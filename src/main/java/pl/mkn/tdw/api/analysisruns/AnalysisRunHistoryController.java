package pl.mkn.tdw.api.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis/runs")
@RequiredArgsConstructor
public class AnalysisRunHistoryController {

    private final AnalysisRunHistoryService analysisRunHistoryService;

    @GetMapping
    public LocalAnalysisRunListResponse listRuns() {
        return analysisRunHistoryService.listRuns();
    }

    @GetMapping("/{analysisId}")
    public LocalAnalysisRunDetailResponse getRun(@PathVariable String analysisId) {
        return analysisRunHistoryService.getRun(analysisId);
    }

    @GetMapping("/{analysisId}/export")
    public JsonNode exportRun(@PathVariable String analysisId) {
        return analysisRunHistoryService.exportRun(analysisId);
    }

    @PatchMapping("/{analysisId}/name")
    public LocalAnalysisRunDetailResponse renameRun(
            @PathVariable String analysisId,
            @Valid @RequestBody RenameLocalAnalysisRunRequest request
    ) {
        return analysisRunHistoryService.renameRun(analysisId, request.name());
    }

    @PostMapping("/{analysisId}/chat/messages")
    public LocalAnalysisRunDetailResponse sendChatMessage(
            @PathVariable String analysisId,
            @Valid @RequestBody LocalAnalysisRunChatMessageRequest request
    ) {
        return analysisRunHistoryService.sendChatMessage(analysisId, request);
    }

    @DeleteMapping("/{analysisId}")
    public ResponseEntity<Void> deleteRun(@PathVariable String analysisId) {
        analysisRunHistoryService.deleteRun(analysisId);
        return ResponseEntity.noContent().build();
    }
}
