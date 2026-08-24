package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

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
import pl.mkn.tdw.features.deliveryscopecomplexity.job.DeliveryScopeComplexityJobService;

@RestController
@RequestMapping("/api/delivery-scope-complexity/jobs")
@RequiredArgsConstructor
public class DeliveryScopeComplexityJobController {

    private final DeliveryScopeComplexityJobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeliveryScopeComplexityJobStateSnapshot start(
            @Valid @RequestBody DeliveryScopeComplexityJobStartRequest request
    ) {
        return jobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public DeliveryScopeComplexityJobStateSnapshot get(@PathVariable String jobId) {
        return jobService.getJob(jobId);
    }
}
