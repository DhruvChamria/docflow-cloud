package edu.manipal.ccd.api;

import edu.manipal.ccd.local.JobStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-cardinality, database-backed metrics shared by API and worker state. */
@Component
@Profile({"local","cloud"})
class JobMetrics {
    private static final List<String> OPERATIONS=List.of(
        "pdf.merge","pdf.split","pdf.extract","pdf.delete","pdf.reorder","pdf.rotate","pdf.duplicate_pages","pdf.insert_blank","pdf.page_numbers",
        "pdf.watermark_text","pdf.watermark_image","pdf.render_images","pdf.extract_text","pdf.metadata_read","pdf.metadata_update","pdf.metadata_remove",
        "pdf.protect","pdf.unlock","pdf.optimize","pdf.compress","convert.image_to_pdf","convert.text_to_pdf","convert.markdown_to_pdf","convert.html_to_pdf",
        "convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf");
    private final JobStore jobs;
    private final Map<String,AtomicLong> status=new HashMap<>(),submitted=new HashMap<>(),completed=new HashMap<>(),failed=new HashMap<>(),retriesByOperation=new HashMap<>();
    private final AtomicLong queueDepth=new AtomicLong(),retries=new AtomicLong(),poison=new AtomicLong(),workerActive=new AtomicLong();
    private volatile double oldestQueuedSeconds;

    JobMetrics(JobStore jobs,MeterRegistry registry) {
        this.jobs=jobs;
        for(String value:List.of("QUEUED","PROCESSING","RETRYING","COMPLETED","FAILED","CANCELLED")) {
            var counter=new AtomicLong();status.put(value,counter);
            Gauge.builder("docflow_jobs_status",counter,AtomicLong::get).tag("status",value).register(registry);
        }
        for(String operation:OPERATIONS) {
            submitted.put(operation,gauge(registry,"docflow_jobs_submitted_total",operation));
            completed.put(operation,gauge(registry,"docflow_jobs_completed_total",operation));
            failed.put(operation,gauge(registry,"docflow_jobs_failed_total",operation));
            retriesByOperation.put(operation,gauge(registry,"docflow_operation_retries",operation));
        }
        Gauge.builder("docflow_queue_depth",queueDepth,AtomicLong::get).tag("worker_type","pdf").register(registry);
        Gauge.builder("docflow_job_retries",retries,AtomicLong::get).register(registry);
        Gauge.builder("docflow_poison_jobs",poison,AtomicLong::get).register(registry);
        Gauge.builder("docflow_worker_active",workerActive,AtomicLong::get).tag("worker_type","pdf").register(registry);
        Gauge.builder("docflow_oldest_queued_seconds",this,value->value.oldestQueuedSeconds).register(registry);
        refresh();
    }
    private AtomicLong gauge(MeterRegistry registry,String name,String operation) {
        var value=new AtomicLong();Gauge.builder(name,value,AtomicLong::get).tag("operation",operation).register(registry);return value;
    }
    @Scheduled(fixedDelay=10_000)
    void refresh() {
        try {
            var snapshot=jobs.metrics();
            status.get("QUEUED").set(snapshot.queued());status.get("PROCESSING").set(snapshot.processing());status.get("RETRYING").set(snapshot.retrying());
            status.get("COMPLETED").set(snapshot.completed());status.get("FAILED").set(snapshot.failed());status.get("CANCELLED").set(snapshot.cancelled());
            queueDepth.set(snapshot.queued()+snapshot.retrying());retries.set(snapshot.retries());poison.set(snapshot.poison());
            workerActive.set(snapshot.workerActive()?1:0);oldestQueuedSeconds=snapshot.oldestQueuedSeconds();
            var operations=jobs.operationMetrics();
            for(String operation:OPERATIONS) {
                var values=operations.getOrDefault(operation,new JobStore.OperationMetrics(0,0,0,0));
                submitted.get(operation).set(values.submitted());completed.get(operation).set(values.completed());
                failed.get(operation).set(values.failed());retriesByOperation.get(operation).set(values.retries());
            }
        } catch(RuntimeException ignored) { /* health endpoint reports dependency outages */ }
    }
}
