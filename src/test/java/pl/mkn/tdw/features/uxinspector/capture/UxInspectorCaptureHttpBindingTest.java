package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

@WebMvcTest(UxInspectorCaptureSnapshotController.class)
@Import({UxInspectorCaptureSnapshotService.class, UxInspectorCaptureJson.class, UxInspectorCaptureNormalizer.class,
        UxInspectorHttpHistoryNormalizer.class, UxInspectorHttpHistorySanitizer.class})
class UxInspectorCaptureHttpBindingTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean LocalAnalysisRunStore runs;

    @Test
    void rejectsMalformedCoreCaptureWithPublicErrorContract() throws Exception {
        var upload = upload();
        upload.withObject("capture").put("version", "1");
        mvc.perform(post("/api/ux-inspector/captures").contentType("application/json").content(upload.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UX_INSPECTOR_CAPTURE_INVALID"));
        upload.withObject("capture").put("version", 1).putNull("page");
        mvc.perform(post("/api/ux-inspector/captures").contentType("application/json").content(upload.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storesValidOptionalDataWhenOtherBlocksFailTypedBinding() throws Exception {
        var upload = upload();
        upload.withObject("capture").put("captureProfile", "FORM_DIAGNOSTICS");
        upload.set("formFields", mapper.createObjectNode().put("status", "AVAILABLE").put("fields", "invalid"));
        upload.set("httpHistory", mapper.createObjectNode().put("status", "AVAILABLE").put("entries", "invalid"));
        var response = mvc.perform(post("/api/ux-inspector/captures").header("Origin", "https://crm.example.com")
                        .contentType("application/json").content(upload.toString()))
                .andExpect(status().isCreated()).andReturn();
        var id = mapper.readTree(response.getResponse().getContentAsString()).path("captureId").asText();
        mvc.perform(get("/api/ux-inspector/captures/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formFields.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.httpHistory.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.store.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.store.state.token").value("[REDACTED]"));
    }

    private ObjectNode upload() {
        var root = mapper.createObjectNode();
        ObjectNode observation = mapper.valueToTree(capture()); observation.remove("captureId");
        root.set("capture", observation);
        root.set("formFields", mapper.createObjectNode().put("status", "NOT_REQUESTED"));
        root.set("store", mapper.createObjectNode().put("status", "AVAILABLE")
                .set("state", mapper.createObjectNode().put("token", "CRM_SECRET")));
        root.set("httpHistory", mapper.createObjectNode().put("status", "UNAVAILABLE").put("reason", "GETTER_MISSING"));
        return root;
    }
}
