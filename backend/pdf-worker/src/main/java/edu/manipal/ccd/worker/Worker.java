package edu.manipal.ccd.worker;
import edu.manipal.ccd.local.*;
import java.util.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;

public class Worker {
    private static final org.slf4j.Logger log=LoggerFactory.getLogger(Worker.class);
    private static final Set<String> OFFICE_OPERATIONS=Set.of("convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf");
    private static String env(String name) { return Objects.requireNonNull(System.getenv(name),name+" is required"); }
    private static boolean demoFailureEnabled() { return Boolean.parseBoolean(System.getenv().getOrDefault("DEMO_FAIL_FIRST_ATTEMPT","false")); }
    private static void clearContext() { org.slf4j.MDC.remove("correlationId");org.slf4j.MDC.remove("jobId");org.slf4j.MDC.remove("operation"); }
    static boolean isOfficeOperation(String operation) { return OFFICE_OPERATIONS.contains(operation); }
    public static void main(String[] args) throws Exception {
        var jobs=new JobStore(env("DB_URL"),env("DB_USER"),env("DB_PASSWORD"));
        var storage=new Storage(env("STORAGE_CONNECTION"));
        String inlineSecret=System.getenv("APP_DOCUMENT_SECRET");
        var secrets=inlineSecret!=null&&!inlineSecret.isBlank()?new SecretBox(inlineSecret):new SecretBox(Path.of(env("APP_DOCUMENT_SECRET_FILE")));
        var json=new ObjectMapper();
        var cleanup=new ExpiryCleanup(jobs,storage::deletePrefix);
        while (true) {
            try {
                jobs.recover();
                jobs.heartbeat();
                var cleaned=cleanup.run();
                if(cleaned.failed()>0) log.warn("Expiry cleanup will retry {} jobs",cleaned.failed());
                for (var job:jobs.pending()) {
                    var target=isOfficeOperation(job.operation())?storage.officeQueue:storage.queue;
                    target.sendMessage(json.writeValueAsString(Map.of("jobId",job.id().toString(),
                        "ownerKey",job.owner(),"dispatchId",job.dispatch().toString(),"correlationId",job.correlation().toString(),"schemaVersion",1)));
                    jobs.sent(job);
                }
                var message=storage.receive();
                if(message==null) { Thread.sleep(1000); continue; }
                JobStore.Job job;
                try {
                    var data=json.readTree(message.getMessageText());
                    if(data.path("schemaVersion").asInt()!=1) throw new IllegalArgumentException();
                    var claim=jobs.claim(UUID.fromString(data.path("jobId").asText()),data.path("ownerKey").asText(),
                        UUID.fromString(data.path("dispatchId").asText()));
                    if(claim.isEmpty()) { storage.queue.deleteMessage(message.getMessageId(),message.getPopReceipt()); continue; }
                    job=claim.get();
                } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException bad) {
                    log.warn("Discarding invalid internal queue message");
                    storage.queue.deleteMessage(message.getMessageId(),message.getPopReceipt()); continue;
                }
                org.slf4j.MDC.put("correlationId",job.correlation().toString());
                org.slf4j.MDC.put("jobId",job.id().toString());
                org.slf4j.MDC.put("operation",job.operation());
                if(demoFailureEnabled()&&job.attempts()==1) {
                    jobs.fail(job,true,"DEMO_INJECTED_FIRST_ATTEMPT");
                    storage.queue.deleteMessage(message.getMessageId(),message.getPopReceipt());
                    log.warn("Injected demo failure; job will retry");clearContext();continue;
                }
                var work=Files.createTempDirectory("docflow-");
                String suffix=switch(job.operation()) { case "pdf.split","pdf.render_images" -> ".zip";case "pdf.extract_text" -> ".txt";case "pdf.metadata_read" -> ".json";default -> ".pdf"; };
                String output=job.id()+"/outputs/"+job.lease()+suffix;
                var leaseLost=new AtomicBoolean(false);
                var heartbeat=Executors.newScheduledThreadPool(2);
                // Separate thread: a stalled lease-renewal call must not block the deadline.
                heartbeat.schedule(()->{ log.error("Worker deadline exceeded; exiting for lease recovery");System.exit(2); },90,TimeUnit.SECONDS);
                heartbeat.scheduleAtFixedRate(()->{
                    try { if(!jobs.renew(job)) leaseLost.set(true); }
                    catch(Exception e) { leaseLost.set(true); }
                },10,10,TimeUnit.SECONDS);
                try {
                    char[] documentPassword=null;
                    var inputs=new ArrayList<Path>();
                    for(int i=0;i<job.files();i++) {
                        var file=work.resolve("input-"+i+".bin");
                        storage.download(job.id()+"/inputs/"+i+".bin",file); inputs.add(file);
                    }
                    var result=work.resolve("result"+suffix);
                    try {
                        if(job.operation().equals("pdf.protect")||job.operation().equals("pdf.unlock"))
                            documentPassword=secrets.decrypt(jobs.secret(job.id()).orElseThrow(()->new java.io.IOException("Document secret unavailable")));
                        PdfOperations.run(new edu.manipal.ccd.core.PdfOptions(job.operation(),job.pages(),job.angle(),job.copies(),job.insertAt(),
                            job.pageSize(),job.text(),job.position(),job.startNumber(),job.fontSize(),job.opacity()),inputs,result,documentPassword);
                    }
                    catch(IllegalArgumentException invalid) {
                        jobs.fail(job,false,"INVALID_PAGE_OPTIONS");
                        jobs.deleteSecret(job.id());
                        storage.queue.deleteMessage(message.getMessageId(),message.getPopReceipt());
                        continue;
                    }
                    catch(java.io.IOException invalid) {
                        jobs.fail(job,false,"INVALID_OR_UNSUPPORTED_PDF");
                        jobs.deleteSecret(job.id());
                        storage.queue.deleteMessage(message.getMessageId(),message.getPopReceipt());
                        continue;
                    }
                    finally { if(documentPassword!=null)java.util.Arrays.fill(documentPassword,'\0'); }
                    if(leaseLost.get()) throw new IllegalStateException("Lease lost");
                    var stored=storage.upload(output,result);
                    if(!jobs.complete(job,output,stored.size(),stored.sha256())) storage.delete(output);else jobs.deleteSecret(job.id());
                    storage.queue.deleteMessage(message.getMessageId(),message.getPopReceipt());
                    log.info("PDF operation completed jobId={} operation={}",job.id(),job.operation());
                } catch(Exception failure) {
                    log.warn("PDF attempt failed jobId={} type={}",job.id(),failure.getClass().getSimpleName());
                    jobs.fail(job,true,"PROCESSING_UNAVAILABLE");
                    if(job.attempts()>=3)jobs.deleteSecret(job.id());
                    // Leave the delivery unacknowledged if acknowledgement cannot be safely completed.
                } finally {
                    clearContext();
                    heartbeat.shutdownNow();
                    try(var paths=Files.walk(work)) {
                        for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                    }
                }
            } catch(Exception failure) {
                log.warn("Worker poll failed type={}",failure.getClass().getSimpleName()); Thread.sleep(3000);
            }
        }
    }
}
