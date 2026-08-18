package pl.mkn.tdw.features.deliverycomplexityassessment.job.api;

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
import pl.mkn.tdw.features.deliverycomplexityassessment.job.DeliveryComplexityAssessmentJobService;

@RestController
@RequestMapping("/api/delivery-complexity-assessment/jobs")
@RequiredArgsConstructor
public class DeliveryComplexityAssessmentJobController {

    private final DeliveryComplexityAssessmentJobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeliveryComplexityAssessmentJobStateSnapshot start(
            @Valid @RequestBody DeliveryComplexityAssessmentJobStartRequest request
    ) {
        return jobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public DeliveryComplexityAssessmentJobStateSnapshot get(@PathVariable String jobId) {
        return jobService.getJob(jobId);
    }
}
