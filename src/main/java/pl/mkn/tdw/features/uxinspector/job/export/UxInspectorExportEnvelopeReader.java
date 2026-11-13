package pl.mkn.tdw.features.uxinspector.job.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureJson;

/** Applies capture's strict boundary to saved runs without tightening unrelated shared report DTOs. */
@Component
@RequiredArgsConstructor
public class UxInspectorExportEnvelopeReader {
    private final ObjectMapper objectMapper;
    private final UxInspectorCaptureJson captureJson;

    public UxInspectorExportEnvelope read(JsonNode document) throws JsonProcessingException {
        if (document == null) throw new IllegalArgumentException("Missing UX Inspector envelope");
        captureJson.read(document.at("/payload/job/request/capture"), UxInspectorCapture.class);
        return objectMapper.treeToValue(document, UxInspectorExportEnvelope.class);
    }
}
