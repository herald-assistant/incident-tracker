package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

class UxInspectorCaptureSnapshotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,true,false", "true,false,true", "true,false,false",
            "false,true,true", "false,true,false", "false,false,true", "false,false,false"})
    void isolatesEachOptionalBlock(boolean validFields, boolean validStore, boolean validHttp) {
        var service = service();
        var upload = upload();
        upload.withObject("capture").put("captureProfile", "FORM_DIAGNOSTICS");
        upload.set("formFields", mapper.createObjectNode().put("status", "AVAILABLE")
                .set("fields", mapper.createArrayNode().add(field("contactName", "CRM contact"))));
        upload.set("httpHistory", pl.mkn.tdw.common.HttpHistoryTestFixtures.block(mapper.createArrayNode().add(
                pl.mkn.tdw.common.HttpHistoryTestFixtures.entry(1, mapper.nullNode()))));
        if (!validFields) ((ObjectNode) upload.at("/formFields/fields/0")).put("checked", "false");
        if (!validStore) upload.withObject("store").put("state", "invalid");
        if (!validHttp) ((ObjectNode) upload.at("/httpHistory/entries/0/response")).put("status", "200");
        var snapshot = service.get(service.save(upload, "https://crm.example.com"));
        assertThat(snapshot.capture()).isNotNull();
        assertThat(snapshot.formFields().status()).isEqualTo(validFields ? "AVAILABLE" : "UNAVAILABLE");
        assertThat(snapshot.store().status()).isEqualTo(validStore ? "AVAILABLE" : "UNAVAILABLE");
        assertThat(snapshot.httpHistory().status()).isEqualTo(validHttp ? "AVAILABLE" : "UNAVAILABLE");
    }

    @Test
    void distinguishesMissingOptionalEnvelopePropertiesFromExplicitNullBlocks() {
        var service = service();
        for (var field : java.util.List.of("store", "formFields", "httpHistory")) {
            var upload = upload(); upload.remove(field);
            assertThatThrownBy(() -> service.save(upload, "https://crm.example.com")).isInstanceOf(IllegalArgumentException.class);
            upload.putNull(field);
            assertThat(service.get(service.save(upload, "https://crm.example.com")).capture()).isNotNull();
        }
        var upload = upload(); upload.put("extra", "CRM");
        assertThatThrownBy(() -> service.save(upload, "https://crm.example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void postsAndReadsActualHttpSnapshotWithIndependentOptionalDataAndStrictVersion() throws Exception {
        var service = service();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new UxInspectorCaptureSnapshotController(service)).build();
        var upload = upload();
        upload.set("httpHistory", pl.mkn.tdw.common.HttpHistoryTestFixtures.block(mapper.createArrayNode().add(
                pl.mkn.tdw.common.HttpHistoryTestFixtures.entry(1, mapper.createObjectNode().put("notes", "CRM".repeat(40000))))));
        upload.withObject("store").put("state", "invalid");
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/ux-inspector/captures").contentType("application/json")
                        .header("Origin", "https://crm.example.com").content(mapper.writeValueAsString(upload)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andReturn();
        var id = mapper.readTree(response.getResponse().getContentAsString()).path("captureId").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ux-inspector/captures/" + id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.httpHistory.entries[0].response.body.value.notes").value("CRM".repeat(40000)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.store.status").value("UNAVAILABLE"));
        service.get(id).httpHistory().entries().removeAll();
        assertThat(service.get(id).httpHistory().entries()).hasSize(1);
        upload.withObject("httpHistory").put("entries", "invalid");
        assertThat(service.get(service.save(upload, "https://crm.example.com")).httpHistory().status()).isEqualTo("UNAVAILABLE");
        upload.withObject("capture").put("version", 3);
        assertThatThrownBy(() -> service.save(upload, "https://crm.example.com")).isInstanceOf(IllegalArgumentException.class);
    }

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
        return new UxInspectorCaptureSnapshotService(mapper, UxInspectorCaptureTestSupport.json(mapper), new UxInspectorCaptureNormalizer(),
                mock(LocalAnalysisRunStore.class), UxInspectorCaptureTestSupport.history(mapper));
    }

    private ObjectNode upload() {
        var root = mapper.createObjectNode();
        var observation = (ObjectNode) mapper.valueToTree(capture());
        observation.remove("captureId");
        observation.put("capturedAt", "2026-09-15T10:00:00Z");
        root.set("capture", observation);
        root.set("httpHistory", mapper.createObjectNode().put("status", "UNAVAILABLE").put("reason", "GETTER_MISSING"));
        root.set("formFields", mapper.createObjectNode().put("status", "NOT_REQUESTED"));
        root.set("store", mapper.createObjectNode().put("status", "AVAILABLE")
                .set("state", mapper.createObjectNode()));
        return root;
    }
}
