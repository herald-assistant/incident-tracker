package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunHttpHistorySnapshot;

public record UxInspectorCaptureSnapshot(
        String captureId,
        UxInspectorCapture capture,
        FormFields formFields,
        Store store,
        LocalAnalysisRunHttpHistorySnapshot httpHistory
) {
    public record FormFields(String status, ArrayNode fields, int omittedSensitiveFields, String reason) {
    }

    public record Store(String status, JsonNode state, int redactions, String reason) {
    }
}
