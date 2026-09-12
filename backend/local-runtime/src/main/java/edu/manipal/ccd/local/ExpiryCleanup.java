package edu.manipal.ccd.local;
import java.util.function.Consumer;

/** A job is expired only after its private blob prefix has been deleted successfully. */
public class ExpiryCleanup {
    private final JobStore jobs;
    private final Consumer<String> deletePrefix;
    public ExpiryCleanup(JobStore jobs,Consumer<String> deletePrefix) { this.jobs=jobs;this.deletePrefix=deletePrefix; }
    public record Result(int removed,int failed) {}
    public Result run() {
        int removed=0,failed=0;
        for(var job:jobs.expired()) {
            try { jobs.deleteSecret(job.id());deletePrefix.accept(job.id()+"/");jobs.expire(job);removed++; }
            catch(RuntimeException error) { failed++; }
        }
        return new Result(removed,failed);
    }
}
