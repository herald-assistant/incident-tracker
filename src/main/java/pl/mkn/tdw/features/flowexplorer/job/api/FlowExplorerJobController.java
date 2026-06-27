package pl.mkn.tdw.features.flowexplorer.job.api;

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
import pl.mkn.tdw.features.flowexplorer.job.FlowExplorerJobService;

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

    @PostMapping("/{jobId}/chat/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FlowExplorerJobStateSnapshot chat(
            @PathVariable String jobId,
            @Valid @RequestBody FlowExplorerChatMessageRequest request
    ) {
        return flowExplorerJobService.startChatMessage(jobId, request);
    }

    @PostMapping("/{jobId}/sections/{sectionId}/refine")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FlowExplorerJobStateSnapshot refineSection(
            @PathVariable String jobId,
            @PathVariable FlowExplorerResultSectionId sectionId,
            @Valid @RequestBody FlowExplorerSectionRefineRequest request
    ) {
        return flowExplorerJobService.startSectionRefine(jobId, sectionId, request);
    }
}
