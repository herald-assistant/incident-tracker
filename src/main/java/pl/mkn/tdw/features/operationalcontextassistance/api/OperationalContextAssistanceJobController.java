package pl.mkn.tdw.features.operationalcontextassistance.api;

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
import pl.mkn.tdw.features.operationalcontextassistance.job.OperationalContextAssistanceJobService;

@RestController
@RequestMapping("/api/operational-context/assistance/jobs")
@RequiredArgsConstructor
public class OperationalContextAssistanceJobController {

    private final OperationalContextAssistanceJobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperationalContextAssistanceJobSnapshot start(
            @Valid @RequestBody OperationalContextAssistanceJobStartRequest request
    ) {
        return jobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public OperationalContextAssistanceJobSnapshot get(@PathVariable String jobId) {
        return jobService.getJob(jobId);
    }

    @PostMapping("/{jobId}/batch/preview")
    public OperationalContextAssistanceBatchPreview previewBatch(
            @PathVariable String jobId,
            @Valid @RequestBody OperationalContextAssistanceBatchReviewRequest request
    ) {
        return jobService.previewBatch(jobId, request);
    }

    @PostMapping("/{jobId}/batch/decision")
    public OperationalContextAssistanceJobSnapshot applyBatch(
            @PathVariable String jobId,
            @Valid @RequestBody OperationalContextAssistanceBatchReviewRequest request
    ) {
        return jobService.applyBatch(jobId, request);
    }
}
