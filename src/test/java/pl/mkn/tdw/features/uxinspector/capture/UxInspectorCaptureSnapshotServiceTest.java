package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

class UxInspectorCaptureSnapshotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void storesOneRedactedSnapshotWithoutConsumingIt() {
        var service = service();
        var upload = upload();
        upload.withObject("store").set("state", mapper.createObjectNode()
                .put("token", "CRM secret").put("contactName", "CRM contact"));
        var id = service.save(upload, "https://crm.example.com");

        var first = service.get(id);
        assertThat(first.captureId()).isEqualTo(id);
        assertThat(first.capture().captureId()).isEqualTo(id);
        assertThat(first.store().state().path("token").asText()).isEqualTo("[REDACTED]");
        assertThat(first.store().redactions()).isEqualTo(1);
        assertThat(first.formFields().status()).isEqualTo("NOT_REQUESTED");
        ((ObjectNode) first.store().state()).put("contactName", "mutated");
        assertThat(service.get(id).store().state().path("contactName").asText()).isEqualTo("CRM contact");
        assertThat(service.runStoreSnapshot(service.get(id)).state().path("token").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void acceptsMoreThanEightPendingAndLargerThanFormerStoreLimit() {
        var service = service();
        String first = null;
        for (var i = 0; i < 10; i++) {
            var id = service.save(upload(), "https://crm.example.com");
            if (i == 0) first = id;
        }
        assertThat(service.get(first).capture()).isNotNull();
        var large = upload();
        large.withObject("store").set("state", mapper.createObjectNode().put("crmMemo", "x".repeat(17 * 1024 * 1024)));
        var id = service.save(large, "https://crm.example.com");
        assertThat(service.get(id).store().state().path("crmMemo").asText()).hasSize(17 * 1024 * 1024);
    }

    @Test
    void isolatesMalformedOptionalDataAndLosesPendingDataWithNewInstance() {
        var service = service();
        var upload = upload();
        upload.withObject("store").put("state", "invalid");
        var id = service.save(upload, "https://crm.example.com");
        assertThat(service.get(id).store().status()).isEqualTo("UNAVAILABLE");
        assertThatThrownBy(() -> service().get(id)).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.save(upload, "https://other.example.com"))
                .hasMessageContaining("origin");
        upload.withObject("capture").put("unexpected", true);
        assertThatThrownBy(() -> service.save(upload, "https://crm.example.com"))
                .hasMessageContaining("Invalid UX Inspector capture");
    }

    @Test
    void redactsSensitiveVisibleFieldsWithoutDroppingTheWholeFormSnapshot() {
        var service = service();
        var upload = upload();
        upload.withObject("capture").put("captureProfile", "FORM_DIAGNOSTICS");
        var fields = mapper.createArrayNode();
        fields.add(field("contactName", "CRM contact"));
        fields.add(field("password", "CRM private"));
        upload.set("formFields", mapper.createObjectNode().put("status", "AVAILABLE").set("fields", fields));

        var snapshot = service.get(service.save(upload, "https://crm.example.com"));

        assertThat(snapshot.formFields().status()).isEqualTo("AVAILABLE");
        assertThat(snapshot.formFields().fields()).hasSize(1);
        assertThat(snapshot.formFields().fields().get(0).path("name").asText()).isEqualTo("contactName");
        assertThat(snapshot.formFields().omittedSensitiveFields()).isEqualTo(1);
    }

    private ObjectNode field(String name, String value) {
        var field = mapper.createObjectNode();
        for (var key : java.util.List.of("tag", "type", "name", "id", "testId", "label", "display", "source",
                "nativeValidationMessage")) field.put(key, "");
        field.put("tag", "input").put("type", "text").put("name", name).put("source", "dom")
                .put("value", value).put("display", value);
        for (var key : java.util.List.of("disabled", "checked", "indeterminate", "invalid", "nativeInvalid"))
            field.putNull(key);
        field.set("errors", mapper.createArrayNode());
        field.set("descriptions", mapper.createArrayNode());
        return field;
    }

    private UxInspectorCaptureSnapshotService service() {
        return new UxInspectorCaptureSnapshotService(mapper, new UxInspectorCaptureNormalizer(),
                mock(LocalAnalysisRunStore.class));
    }

    private ObjectNode upload() {
        var root = mapper.createObjectNode();
        var observation = (ObjectNode) mapper.valueToTree(capture());
        observation.remove("captureId");
        observation.put("capturedAt", "2026-09-15T10:00:00Z");
        root.set("capture", observation);
        root.set("formFields", mapper.createObjectNode().put("status", "NOT_REQUESTED"));
        root.set("store", mapper.createObjectNode().put("status", "AVAILABLE")
                .set("state", mapper.createObjectNode()));
        return root;
    }
}
