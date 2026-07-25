package pl.mkn.tdw.features.changeverification.job.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.changeverification.job.ChangeVerificationJobService;

import java.util.Map;

@RestController
@RequestMapping("/api/change-verification/jobs")
@RequiredArgsConstructor
public class ChangeVerificationJobController {

    private final ChangeVerificationJobService changeVerificationJobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChangeVerificationJobStateSnapshot start(@Valid @RequestBody ChangeVerificationJobStartRequest request) {
        return changeVerificationJobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public ChangeVerificationJobStateSnapshot get(@PathVariable String jobId) {
        return changeVerificationJobService.getJob(jobId);
    }

    @GetMapping("/{jobId}/smoke-pack")
    public ChangeVerificationSmokePackResponse smokePack(@PathVariable String jobId) {
        return changeVerificationJobService.getSmokePack(jobId);
    }

    @PutMapping("/{jobId}/smoke-pack")
    public ChangeVerificationSmokePackResponse updateSmokePack(
            @PathVariable String jobId,
            @RequestBody ChangeVerificationSmokePackResponse smokePack
    ) {
        return changeVerificationJobService.updateSmokePack(jobId, smokePack);
    }

    @GetMapping("/{jobId}/postman/collection")
    public Map<String, Object> postmanCollection(@PathVariable String jobId) {
        return changeVerificationJobService.postmanCollection(jobId);
    }

    @PostMapping("/{jobId}/smoke-executions")
    public ChangeVerificationExecutionResponse executeSmokePack(
            @PathVariable String jobId,
            @Valid @RequestBody ChangeVerificationSmokeExecutionRequest request
    ) {
        return changeVerificationJobService.executeSmokePack(jobId, request);
    }
}
