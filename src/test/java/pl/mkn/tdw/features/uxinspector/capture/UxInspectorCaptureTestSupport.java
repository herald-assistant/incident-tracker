package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelopeReader;

/** Real boundary dependencies for non-Spring tests. */
public final class UxInspectorCaptureTestSupport {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private UxInspectorCaptureTestSupport() {}

    public static UxInspectorCaptureJson json(ObjectMapper mapper) {
        return new UxInspectorCaptureJson(mapper, VALIDATORS.getValidator());
    }

    public static UxInspectorHttpHistoryNormalizer history(ObjectMapper mapper) {
        var json = json(mapper);
        return new UxInspectorHttpHistoryNormalizer(json, new UxInspectorHttpHistorySanitizer(json));
    }

    public static UxInspectorExportEnvelopeReader envelopes(ObjectMapper mapper) {
        return new UxInspectorExportEnvelopeReader(mapper, json(mapper));
    }
}
