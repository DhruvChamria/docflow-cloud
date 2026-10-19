package edu.manipal.ccd.api;
import edu.manipal.ccd.local.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration
@Profile({"local","cloud"})
class LocalConfiguration {
    @Bean com.fasterxml.jackson.databind.ObjectMapper jobMessageMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
    @Bean JobStore jobs(Environment env) {
        return new JobStore(env.getRequiredProperty("DB_URL"),env.getRequiredProperty("DB_USER"),env.getRequiredProperty("DB_PASSWORD"));
    }
    @Bean Storage storage(Environment env) { return new Storage(env.getRequiredProperty("STORAGE_CONNECTION")); }
    @Bean AccountStore accounts(Environment env) {
        return new AccountStore(env.getRequiredProperty("DB_URL"),env.getRequiredProperty("DB_USER"),env.getRequiredProperty("DB_PASSWORD"));
    }
    @Bean SecretBox documentSecrets(Environment env) {
        String inline=env.getProperty("APP_DOCUMENT_SECRET");
        return inline!=null&&!inline.isBlank()?new SecretBox(inline):new SecretBox(java.nio.file.Path.of(env.getRequiredProperty("APP_DOCUMENT_SECRET_FILE")));
    }
    @Bean org.springframework.boot.health.contributor.HealthIndicator database(JobStore jobs) {
        return ()->{try { return jobs.healthy()?org.springframework.boot.health.contributor.Health.up().build():org.springframework.boot.health.contributor.Health.down().build(); }
            catch(Exception error) { return org.springframework.boot.health.contributor.Health.down().build(); }};
    }
    @Bean org.springframework.boot.health.contributor.HealthIndicator storageDependency(Storage storage) {
        return ()->{try {
            boolean exists=storage.blobs.existsWithResponse(java.time.Duration.ofSeconds(3),com.azure.core.util.Context.NONE).getValue();
            storage.queue.getPropertiesWithResponse(java.time.Duration.ofSeconds(3),com.azure.core.util.Context.NONE);
            storage.officeQueue.getPropertiesWithResponse(java.time.Duration.ofSeconds(3),com.azure.core.util.Context.NONE);
            return exists?org.springframework.boot.health.contributor.Health.up().build():org.springframework.boot.health.contributor.Health.down().build();
        } catch(Exception error) { return org.springframework.boot.health.contributor.Health.down().build(); }};
    }
}
