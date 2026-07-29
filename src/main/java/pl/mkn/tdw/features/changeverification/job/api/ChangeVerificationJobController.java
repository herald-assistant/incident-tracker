package pl.mkn.tdw.features.changeverification.job.api;

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
import pl.mkn.tdw.features.changeverification.job.ChangeVerificationJobService;

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
}
