package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunHttpHistorySnapshot;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStoreSnapshot;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UxInspectorCaptureSnapshotService {
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(password|passwd|token|secret|authorization|bearer|cookie|session|csrf|jwt|api.?key|one[-_ ]?time|otp|cvv|cvc).*");
    private static final Pattern JWT = Pattern.compile("^[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}$");

    private final ObjectMapper mapper;
    private final UxInspectorCaptureJson json;
    private final UxInspectorCaptureNormalizer normalizer;
    private final LocalAnalysisRunStore runStore;
    private final UxInspectorHttpHistoryNormalizer httpHistoryNormalizer;
    private final Map<String, UxInspectorCaptureSnapshot> snapshots = new ConcurrentHashMap<>();

    public String save(JsonNode upload, String requestOrigin) {
        var input = json.read(upload, UxInspectorCaptureUpload.class);
        var captureNode = input.capture();
        if (captureNode.has("captureId")) {
            throw new IllegalArgumentException("Capture must be an object without captureId.");
        }
        var id = UUID.randomUUID().toString();
        var withId = captureNode.deepCopy();
        withId.put("captureId", id);
        final UxInspectorCapture capture;
        try {
            capture = normalizer.normalize(json.read(withId, UxInspectorCapture.class));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid UX Inspector capture: " + exception.getMessage(), exception);
        }
        if (requestOrigin != null && !requestOrigin.equals(capture.page().origin())) {
            throw new IllegalArgumentException("Request origin does not match capture page origin.");
        }
        var fields = formFields(input.formFields(), capture.captureProfile());
        var store = store(input.store());
        LocalAnalysisRunHttpHistorySnapshot httpHistory = httpHistoryNormalizer.normalize(
                input.httpHistory(), capture.page().origin()
        );
        snapshots.put(id, new UxInspectorCaptureSnapshot(id, capture, fields, store, httpHistory));
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
                        snapshot.store().redactions(), snapshot.store().reason()), snapshot.httpHistory());
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

    public java.util.Optional<pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunHttpHistorySnapshot> findRunHttpHistory(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]+")) return java.util.Optional.empty();
        try { return runStore.findById(runId).map(record -> record.httpHistorySnapshot()); }
        catch (RuntimeException exception) { return java.util.Optional.empty(); }
    }

    private UxInspectorCaptureSnapshot.FormFields formFields(JsonNode block, UxInspectorCapture.CaptureProfile profile) {
        if (profile != UxInspectorCapture.CaptureProfile.FORM_DIAGNOSTICS) {
            return new UxInspectorCaptureSnapshot.FormFields("NOT_REQUESTED", mapper.createArrayNode(), 0, null);
        }
        try {
            var input = json.read(block, UxInspectorCaptureUpload.FormFields.class);
            var safe = mapper.createArrayNode();
            int omitted = 0;
            for (var field : input.fields()) {
                if (sensitiveField(field)) omitted++;
                else safe.add(json.tree(field));
            }
            return new UxInspectorCaptureSnapshot.FormFields("AVAILABLE", safe, omitted, null);
        } catch (IllegalArgumentException exception) {
            return unavailableFields();
        }
    }

    private UxInspectorCaptureSnapshot.FormFields unavailableFields() {
        return new UxInspectorCaptureSnapshot.FormFields("UNAVAILABLE", mapper.createArrayNode(), 0,
                "Visible form fields could not be captured or validated.");
    }

    private UxInspectorCaptureSnapshot.Store store(JsonNode block) {
        try {
            var input = json.read(block, UxInspectorCaptureUpload.Store.class);
            var safe = input.state().deepCopy();
            return new UxInspectorCaptureSnapshot.Store("AVAILABLE", safe, redact(safe), null);
        } catch (IllegalArgumentException exception) {
            return unavailableStore();
        }
    }

    private UxInspectorCaptureSnapshot.Store unavailableStore() {
        return new UxInspectorCaptureSnapshot.Store("UNAVAILABLE", null, 0,
                "Browser store could not be captured or validated.");
    }

    private boolean sensitiveField(UxInspectorCaptureUpload.FormField field) {
        for (var value : java.util.List.of(field.type(), field.name(), field.id(), field.testId(), field.label())) {
            if (SENSITIVE.matcher(value).matches()) return true;
        }
        if (Set.of("password", "file", "hidden").contains(field.type().toLowerCase())) return true;
        return secretValue(field.value()) || secretText(field.display()) || secretText(field.nativeValidationMessage())
                || field.errors().stream().anyMatch(this::secretText) || field.descriptions().stream().anyMatch(this::secretText);
    }

    private boolean secretValue(JsonNode value) {
        if (value.isArray()) {
            for (var child : value) if (secretValue(child)) return true;
            return false;
        }
        return secretText(value.asText(""));
    }

    private boolean secretText(String value) {
        var text = value.trim();
        return text.matches("(?i)^Bearer\\s+.*") || JWT.matcher(text).matches();
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

}
