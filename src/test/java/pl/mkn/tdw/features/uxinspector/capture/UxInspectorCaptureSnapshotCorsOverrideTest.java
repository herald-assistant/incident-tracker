package pl.mkn.tdw.features.uxinspector.capture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UxInspectorCaptureSnapshotController.class)
@TestPropertySource(properties = "ux-inspector.capture.allowed-origins=https://crm.example.com,https://portal.example.com")
class UxInspectorCaptureSnapshotCorsOverrideTest {
    @Autowired MockMvc mvc;
    @MockitoBean UxInspectorCaptureSnapshotService snapshots;

    @Test
    void permitsConfiguredOriginsAndRejectsOthers() throws Exception {
        mvc.perform(options("/api/ux-inspector/captures").header("Origin", "https://portal.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://portal.example.com"));
        mvc.perform(options("/api/ux-inspector/captures").header("Origin", "https://other.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
