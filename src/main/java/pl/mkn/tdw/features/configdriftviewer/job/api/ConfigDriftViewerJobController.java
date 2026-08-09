package pl.mkn.tdw.features.configdriftviewer.job.api;

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
import pl.mkn.tdw.features.configdriftviewer.job.ConfigDriftViewerJobService;

@RestController
@RequestMapping("/api/config-drift-viewer/v1/jobs")
@RequiredArgsConstructor
public class ConfigDriftViewerJobController {

    private final ConfigDriftViewerJobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ConfigDriftViewerJobStateSnapshot start(
            @Valid @RequestBody ConfigDriftViewerJobStartRequest request
    ) {
        return jobService.startJob(request);
    }

    @GetMapping("/{jobId}")
    public ConfigDriftViewerJobStateSnapshot get(@PathVariable String jobId) {
        return jobService.getJob(jobId);
    }
}
