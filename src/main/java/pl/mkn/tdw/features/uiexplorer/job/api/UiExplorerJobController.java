package pl.mkn.tdw.features.uiexplorer.job.api;

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
import pl.mkn.tdw.features.uiexplorer.job.UiExplorerJobService;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportEnvelope;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportService;

@RestController
@RequestMapping("/api/ui-explorer/jobs")
@RequiredArgsConstructor
public class UiExplorerJobController {

    private final UiExplorerJobService uiExplorerJobService;
    private final UiExplorerExportService uiExplorerExportService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UiExplorerJobStateSnapshot start(@Valid @RequestBody UiExplorerJobStartRequest request) {
        return uiExplorerJobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public UiExplorerJobStateSnapshot get(@PathVariable String jobId) {
        return uiExplorerJobService.getJob(jobId);
    }

    @GetMapping("/{jobId}/export")
    public UiExplorerExportEnvelope export(@PathVariable String jobId) {
        return uiExplorerExportService.export(jobId);
    }
}
