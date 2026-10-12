package edu.manipal.ccd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.manipal.ccd.local.JobStore;
import edu.manipal.ccd.local.Storage;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes durable database dispatch intents so workers may safely scale from zero. */
@Component
@Profile({"local","cloud"})
class JobDispatcher {
    private static final org.slf4j.Logger log=LoggerFactory.getLogger(JobDispatcher.class);
    private static final Set<String> OFFICE_OPERATIONS=Set.of(
        "convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf");
    private final JobStore jobs;private final Storage storage;private final ObjectMapper json;

    JobDispatcher(JobStore jobs,Storage storage,ObjectMapper json) {
        this.jobs=jobs;this.storage=storage;this.json=json;
    }

    @Scheduled(fixedDelayString="${app.dispatch.interval-ms:2000}")
    void dispatch() {
        try {
            for(var job:jobs.pending()) {
                String message=json.writeValueAsString(Map.of(
                    "jobId",job.id().toString(),"ownerKey",job.owner(),
                    "dispatchId",job.dispatch().toString(),"correlationId",job.correlation().toString(),"schemaVersion",1));
                var queue=OFFICE_OPERATIONS.contains(job.operation())?storage.officeQueue:storage.queue;
                queue.sendMessage(message);jobs.sent(job);
            }
        } catch(Exception failure) {
            log.warn("Job dispatch unavailable type={}",failure.getClass().getSimpleName());
        }
    }
}
