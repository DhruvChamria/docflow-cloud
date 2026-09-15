package edu.manipal.ccd.api;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@Profile("!local")
class UnavailableJobsController {
    @PostMapping("/api/v1/jobs") void submit() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,"Start the local profile with its database, storage and worker.");
    }
}

