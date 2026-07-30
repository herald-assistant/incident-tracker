package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.RuntimeConfigurationVerificationJobService;

@RestController
@RequestMapping("/api/runtime-configuration-verification/jobs")
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationJobController {

    private final RuntimeConfigurationVerificationJobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RuntimeConfigurationVerificationJobStateSnapshot start(
            @Valid @RequestBody RuntimeConfigurationVerificationJobStartRequest request
    ) {
        return jobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public RuntimeConfigurationVerificationJobStateSnapshot get(@PathVariable String jobId) {
        return jobService.getJob(jobId);
    }
}
