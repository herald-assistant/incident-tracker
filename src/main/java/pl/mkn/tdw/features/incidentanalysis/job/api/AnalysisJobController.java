package pl.mkn.tdw.features.incidentanalysis.job.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.mkn.tdw.features.incidentanalysis.job.AnalysisJobFacade;

@RestController
@RequestMapping({
        "/api/analysis/jobs",
        "/analysis/jobs"
})
@RequiredArgsConstructor
public class AnalysisJobController {

    private final AnalysisJobFacade analysisJobFacade;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobStateSnapshot start(
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "correlationId", required = false) String correlationId,
            @RequestPart(value = "logFile", required = false) MultipartFile logFile,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "reasoningEffort", required = false) String reasoningEffort
    ) {
        return analysisJobFacade.startAnalysis(AnalysisJobStartRequest.fromMultipart(
                source,
                correlationId,
                logFile,
                model,
                reasoningEffort
        ));
    }

    @GetMapping("/input-options")
    public AnalysisJobInputOptionsResponse inputOptions() {
        return analysisJobFacade.inputOptions();
    }

    @GetMapping("/{analysisId}")
    public AnalysisJobStateSnapshot get(@PathVariable String analysisId) {
        return analysisJobFacade.getAnalysis(analysisId);
    }

    @PostMapping("/{analysisId}/chat/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobStateSnapshot chat(
            @PathVariable String analysisId,
            @Valid @RequestBody AnalysisChatMessageRequest request
    ) {
        return analysisJobFacade.startChatMessage(analysisId, request);
    }

}
