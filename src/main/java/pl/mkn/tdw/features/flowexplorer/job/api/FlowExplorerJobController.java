package pl.mkn.tdw.features.flowexplorer.job.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.flowexplorer.job.FlowExplorerJobService;
import pl.mkn.tdw.shared.ai.report.AnalysisReportEditRequest;

@RestController
@RequestMapping("/api/flow-explorer/jobs")
@RequiredArgsConstructor
public class FlowExplorerJobController {

    private final FlowExplorerJobService flowExplorerJobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FlowExplorerJobStateSnapshot start(@Valid @RequestBody FlowExplorerJobStartRequest request) {
        return flowExplorerJobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public FlowExplorerJobStateSnapshot get(@PathVariable String jobId) {
        return flowExplorerJobService.getJob(jobId);
    }

    @PatchMapping("/{jobId}/report")
    public FlowExplorerJobStateSnapshot editReport(@PathVariable String jobId,
                                                   @RequestBody AnalysisReportEditRequest request) {
        return flowExplorerJobService.editReport(jobId, request);
    }

    @PostMapping("/{jobId}/chat/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FlowExplorerJobStateSnapshot chat(
            @PathVariable String jobId,
            @Valid @RequestBody FlowExplorerChatMessageRequest request
    ) {
        return flowExplorerJobService.startChatMessage(jobId, request);
    }

}
