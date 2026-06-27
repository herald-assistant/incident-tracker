package pl.mkn.tdw.features.incidentanalysis.job.api;

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
import pl.mkn.tdw.features.incidentanalysis.job.AnalysisJobService;

@RestController
@RequestMapping("/analysis/jobs")
@RequiredArgsConstructor
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobStateSnapshot start(@Valid @RequestBody AnalysisJobStartRequest request) {
        return analysisJobService.startAnalysis(request);
    }

    @GetMapping("/{analysisId}")
    public AnalysisJobStateSnapshot get(@PathVariable String analysisId) {
        return analysisJobService.getAnalysis(analysisId);
    }

    @PostMapping("/{analysisId}/chat/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobStateSnapshot chat(
            @PathVariable String analysisId,
            @Valid @RequestBody AnalysisChatMessageRequest request
    ) {
        return analysisJobService.startChatMessage(analysisId, request);
    }

}
