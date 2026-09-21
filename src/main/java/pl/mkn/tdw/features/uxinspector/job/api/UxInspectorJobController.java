package pl.mkn.tdw.features.uxinspector.job.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorJobService;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelope;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportService;

@RestController
@RequestMapping("/api/ux-inspector/jobs")
@RequiredArgsConstructor
public class UxInspectorJobController {
    private final UxInspectorJobService jobService;
    private final UxInspectorExportService exportService;
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public UxInspectorJobStateSnapshot start(@Valid @RequestBody UxInspectorJobStartRequest request) { return jobService.startJob(request); }
    @GetMapping("/{jobId}")
    public UxInspectorJobStateSnapshot get(@PathVariable String jobId) { return jobService.getJob(jobId); }
    @PostMapping("/{jobId}/chat/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UxInspectorJobStateSnapshot chat(@PathVariable String jobId,
                                            @Valid @RequestBody UxInspectorChatMessageRequest request) {
        return jobService.startChatMessage(jobId, request);
    }
    @GetMapping("/{jobId}/export")
    public UxInspectorExportEnvelope export(@PathVariable String jobId) { return exportService.export(jobId); }
}
