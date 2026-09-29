package pl.mkn.tdw.features.uxinspector.capture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UxInspectorCaptureSnapshotController.class)
class UxInspectorCaptureSnapshotControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UxInspectorCaptureSnapshotService snapshots;

    @Test
    void allowsCrossOriginPostByDefaultAndReturnsCaptureId() throws Exception {
        when(snapshots.save(any(), eq("https://crm.example.com"))).thenReturn("cap-crm-1");
        mvc.perform(options("/api/ux-inspector/captures")
                        .header("Origin", "https://crm.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
        mvc.perform(post("/api/ux-inspector/captures").contentType("application/json")
                        .header("Origin", "https://crm.example.com")
                        .content("{\"capture\":{},\"formFields\":{},\"store\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.captureId").value("cap-crm-1"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void keepsGetSameOriginAndDisablesCaching() throws Exception {
        when(snapshots.get("cap-crm-1")).thenReturn(new UxInspectorCaptureSnapshot("cap-crm-1", null,
                new UxInspectorCaptureSnapshot.FormFields("NOT_REQUESTED", new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode(), 0, null),
                new UxInspectorCaptureSnapshot.Store("UNAVAILABLE", null, 0, null)));
        mvc.perform(get("/api/ux-inspector/captures/cap-crm-1").header("Origin", "https://crm.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(jsonPath("$.captureId").value("cap-crm-1"));
    }

    @Test
    void reportsUnknownCaptureWithFeatureError() throws Exception {
        when(snapshots.get("missing")).thenThrow(new UxInspectorJobException(
                "UX_INSPECTOR_CAPTURE_NOT_FOUND", UserFacingErrorType.NOT_FOUND, "Capture not found."));
        mvc.perform(get("/api/ux-inspector/captures/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UX_INSPECTOR_CAPTURE_NOT_FOUND"));
    }
}
