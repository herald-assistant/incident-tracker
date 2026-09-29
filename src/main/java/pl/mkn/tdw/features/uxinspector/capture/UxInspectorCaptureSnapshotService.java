package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStoreSnapshot;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UxInspectorCaptureSnapshotService {
    private static final Set<String> FIELD_KEYS = Set.of("tag", "type", "name", "id", "testId", "label",
            "disabled", "value", "display", "source", "checked", "indeterminate", "invalid", "errors",
            "descriptions", "nativeInvalid", "nativeValidationMessage");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(password|passwd|token|secret|authorization|bearer|cookie|session|csrf|jwt|api.?key|one[-_ ]?time|otp|cvv|cvc).*");
    private static final Pattern JWT = Pattern.compile("^[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}$");

    private final ObjectMapper mapper;
    private final UxInspectorCaptureNormalizer normalizer;
    private final LocalAnalysisRunStore runStore;
    private final Map<String, UxInspectorCaptureSnapshot> snapshots = new ConcurrentHashMap<>();

    public String save(JsonNode upload, String requestOrigin) {
        if (upload == null || !upload.isObject() || !keys(upload).equals(Set.of("capture", "formFields", "store"))) {
            throw new IllegalArgumentException("Invalid UX Inspector capture upload envelope.");
        }
        var rawCapture = upload.get("capture");
        if (!(rawCapture instanceof ObjectNode captureNode) || captureNode.has("captureId")) {
            throw new IllegalArgumentException("Capture must be an object without captureId.");
        }
        var id = UUID.randomUUID().toString();
        var withId = captureNode.deepCopy();
        withId.put("captureId", id);
        final UxInspectorCapture capture;
        try {
            capture = normalizer.normalize(mapper.treeToValue(withId, UxInspectorCapture.class));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid UX Inspector capture: " + exception.getMessage(), exception);
        }
        if (requestOrigin != null && !requestOrigin.equals(capture.page().origin())) {
            throw new IllegalArgumentException("Request origin does not match capture page origin.");
        }
        var fields = formFields(upload.get("formFields"), capture.captureProfile());
        var store = store(upload.get("store"));
        snapshots.put(id, new UxInspectorCaptureSnapshot(id, capture, fields, store));
        return id;
    }

    public UxInspectorCaptureSnapshot get(String id) {
        var snapshot = snapshots.get(id);
        if (snapshot == null) throw new UxInspectorJobException("UX_INSPECTOR_CAPTURE_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND, "UX Inspector capture was not found. Select the element again.");
        // JsonNode is mutable; return copies so GET and job consumers cannot alter the pending observation.
        return new UxInspectorCaptureSnapshot(snapshot.captureId(), snapshot.capture(),
                new UxInspectorCaptureSnapshot.FormFields(snapshot.formFields().status(),
                        snapshot.formFields().fields().deepCopy(), snapshot.formFields().omittedSensitiveFields(),
                        snapshot.formFields().reason()),
                new UxInspectorCaptureSnapshot.Store(snapshot.store().status(),
                        snapshot.store().state() != null ? snapshot.store().state().deepCopy() : null,
                        snapshot.store().redactions(), snapshot.store().reason()));
    }

    public LocalAnalysisRunStoreSnapshot runStoreSnapshot(UxInspectorCaptureSnapshot snapshot) {
        if (!"AVAILABLE".equals(snapshot.store().status())) return null;
        return new LocalAnalysisRunStoreSnapshot(snapshot.store().state().deepCopy(),
                snapshot.capture().capturedAt(), snapshot.capture().page().origin(), snapshot.store().redactions());
    }

    public java.util.Optional<LocalAnalysisRunStoreSnapshot> findRunStore(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]+")) return java.util.Optional.empty();
        try { return runStore.findById(runId).map(record -> record.storeSnapshot()); }
        catch (RuntimeException exception) { return java.util.Optional.empty(); }
    }

    private UxInspectorCaptureSnapshot.FormFields formFields(JsonNode block, UxInspectorCapture.CaptureProfile profile) {
        if (profile != UxInspectorCapture.CaptureProfile.FORM_DIAGNOSTICS) {
            return new UxInspectorCaptureSnapshot.FormFields("NOT_REQUESTED", mapper.createArrayNode(), 0, null);
        }
        if (block == null || !block.isObject() || !"AVAILABLE".equals(block.path("status").asText())) {
            return unavailableFields();
        }
        var values = block.get("fields");
        if (values == null || !values.isArray()) return unavailableFields();
        var safe = mapper.createArrayNode();
        int omitted = 0;
        for (var value : values) {
            if (!validField(value)) return unavailableFields();
            if (sensitiveField(value)) omitted++;
            else safe.add(value.deepCopy());
        }
        return new UxInspectorCaptureSnapshot.FormFields("AVAILABLE", safe, omitted, null);
    }

    private UxInspectorCaptureSnapshot.FormFields unavailableFields() {
        return new UxInspectorCaptureSnapshot.FormFields("UNAVAILABLE", mapper.createArrayNode(), 0,
                "Visible form fields could not be captured or validated.");
    }

    private UxInspectorCaptureSnapshot.Store store(JsonNode block) {
        if (block == null || !block.isObject() || !"AVAILABLE".equals(block.path("status").asText())) {
            return unavailableStore();
        }
        var state = block.get("state");
        if (state == null || (!state.isObject() && !state.isArray())) return unavailableStore();
        var safe = state.deepCopy();
        return new UxInspectorCaptureSnapshot.Store("AVAILABLE", safe, redact(safe), null);
    }

    private UxInspectorCaptureSnapshot.Store unavailableStore() {
        return new UxInspectorCaptureSnapshot.Store("UNAVAILABLE", null, 0,
                "Browser store could not be captured or validated.");
    }

    private boolean validField(JsonNode field) {
        if (field == null || !field.isObject() || !keys(field).equals(FIELD_KEYS)) return false;
        for (var key : Set.of("tag", "type", "name", "id", "testId", "label", "display", "source", "nativeValidationMessage")) {
            if (!field.path(key).isTextual()) return false;
        }
        if (!Set.of("dom", "display-text").contains(field.path("source").asText())) return false;
        var value = field.path("value");
        if (!value.isTextual() && !(value.isArray() && strings(value))) return false;
        for (var key : Set.of("disabled", "checked", "indeterminate", "invalid", "nativeInvalid")) {
            if (!field.path(key).isBoolean() && !field.path(key).isNull()) return false;
        }
        return field.path("errors").isArray() && strings(field.path("errors"))
                && field.path("descriptions").isArray() && strings(field.path("descriptions"));
    }

    private boolean sensitiveField(JsonNode field) {
        for (var key : Set.of("type", "name", "id", "testId", "label")) {
            if (SENSITIVE.matcher(field.path(key).asText()).matches()) return true;
        }
        if (Set.of("password", "file", "hidden").contains(field.path("type").asText().toLowerCase())) return true;
        if (secretValue(field.path("value")) || secretValue(field.path("display"))) return true;
        for (var key : Set.of("errors", "descriptions")) {
            for (var message : field.path(key)) if (secretValue(message)) return true;
        }
        return secretValue(field.path("nativeValidationMessage"));
    }

    private boolean secretValue(JsonNode value) {
        if (value.isArray()) {
            for (var child : value) if (secretValue(child)) return true;
            return false;
        }
        var text = value.asText("").trim();
        return text.matches("(?i)^Bearer\\s+.*") || JWT.matcher(text).matches();
    }

    private boolean strings(JsonNode values) {
        for (var value : values) if (!value.isTextual()) return false;
        return true;
    }

    private int redact(JsonNode node) {
        int count = 0;
        if (node instanceof ObjectNode object) {
            var fields = object.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (SENSITIVE.matcher(entry.getKey()).matches()) {
                    object.put(entry.getKey(), "[REDACTED]");
                    count++;
                } else count += redact(entry.getValue());
            }
        } else if (node instanceof ArrayNode array) {
            for (var child : array) count += redact(child);
        }
        return count;
    }

    private Set<String> keys(JsonNode value) {
        var keys = new HashSet<String>();
        value.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
