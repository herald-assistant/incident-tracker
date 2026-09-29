package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public record UxInspectorCaptureSnapshot(
        String captureId,
        UxInspectorCapture capture,
        FormFields formFields,
        Store store
) {
    public record FormFields(String status, ArrayNode fields, int omittedSensitiveFields, String reason) {}
    public record Store(String status, JsonNode state, int redactions, String reason) {}
}
