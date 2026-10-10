package edu.manipal.ccd.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.manipal.ccd.local.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;

public final class OfficeWorker {
    private static final org.slf4j.Logger log=LoggerFactory.getLogger(OfficeWorker.class);
    private static String env(String name) { return Objects.requireNonNull(System.getenv(name),name+" is required"); }
    public static void main(String[] args) throws Exception {
        var jobs=new JobStore(env("DB_URL"),env("DB_USER"),env("DB_PASSWORD"));var storage=new Storage(env("STORAGE_CONNECTION"));var json=new ObjectMapper();
        while(true) try {
            jobs.heartbeat("OFFICE");var message=storage.receiveOffice();if(message==null){Thread.sleep(1000);continue;}
            JobStore.Job job;
            try {
                var data=json.readTree(message.getMessageText());if(data.path("schemaVersion").asInt()!=1)throw new IllegalArgumentException();
                var claim=jobs.claim(UUID.fromString(data.path("jobId").asText()),data.path("ownerKey").asText(),UUID.fromString(data.path("dispatchId").asText()));
                if(claim.isEmpty()){storage.officeQueue.deleteMessage(message.getMessageId(),message.getPopReceipt());continue;}job=claim.get();
            } catch(IllegalArgumentException|com.fasterxml.jackson.core.JsonProcessingException bad) {
                log.warn("Discarding invalid office queue message");storage.officeQueue.deleteMessage(message.getMessageId(),message.getPopReceipt());continue;
            }
            org.slf4j.MDC.put("correlationId",job.correlation().toString());org.slf4j.MDC.put("jobId",job.id().toString());org.slf4j.MDC.put("operation",job.operation());
            var work=Files.createTempDirectory("docflow-office-");var leaseLost=new AtomicBoolean(false);var heartbeat=Executors.newScheduledThreadPool(2);
            heartbeat.schedule(()->{log.error("Office worker deadline exceeded; exiting for lease recovery");System.exit(2);},90,TimeUnit.SECONDS);
            heartbeat.scheduleAtFixedRate(()->{try{if(!jobs.renew(job))leaseLost.set(true);}catch(Exception error){leaseLost.set(true);}},10,10,TimeUnit.SECONDS);
            try {
                if(!Set.of("convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf").contains(job.operation())||job.files()!=1)throw new java.io.IOException("Unsupported office operation");
                String extension=switch(job.operation()){case "convert.pptx_to_pdf"->"pptx";case "convert.odt_to_pdf"->"odt";case "convert.odp_to_pdf"->"odp";case "convert.ods_to_pdf"->"ods";default->"docx";};
                Path input=work.resolve("source."+extension);storage.download(job.id()+"/inputs/0.bin",input);
                Path result=OfficeOperations.convert(input,work.resolve("output"),work.resolve("profile"));if(leaseLost.get())throw new IllegalStateException("Lease lost");
                String key=job.id()+"/outputs/"+job.lease()+".pdf";var stored=storage.upload(key,result);if(!jobs.complete(job,key,stored.size(),stored.sha256()))storage.delete(key);
                storage.officeQueue.deleteMessage(message.getMessageId(),message.getPopReceipt());log.info("Office conversion completed jobId={}",job.id());
            } catch(java.io.IOException invalid) {
                jobs.fail(job,false,"INVALID_OR_UNSUPPORTED_DOCUMENT");storage.officeQueue.deleteMessage(message.getMessageId(),message.getPopReceipt());
            } catch(Exception failure) {
                log.warn("Office attempt failed jobId={} type={}",job.id(),failure.getClass().getSimpleName());jobs.fail(job,true,"OFFICE_PROCESSING_UNAVAILABLE");
            } finally {
                org.slf4j.MDC.clear();heartbeat.shutdownNow();try(var paths=Files.walk(work)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
            }
        } catch(Exception failure) { log.warn("Office worker poll failed type={}",failure.getClass().getSimpleName());Thread.sleep(3000); }
    }
}
