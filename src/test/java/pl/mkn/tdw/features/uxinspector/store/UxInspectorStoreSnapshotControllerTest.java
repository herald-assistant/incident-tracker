package pl.mkn.tdw.features.uxinspector.store;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UxInspectorStoreSnapshotControllerTest {
    @Test
    void acceptsAnUploadAndReturnsOnlyAnOpaqueReference() throws Exception {
        var snapshots = mock(UxInspectorStoreSnapshotService.class);
        var auth = mock(AnalysisAiAuthRefResolver.class);
        when(snapshots.stage(any(), any())).thenReturn(
                new UxInspectorStoreSnapshotService.UploadReceipt("opaque-ref", 42, 1));
        var mvc = MockMvcBuilders.standaloneSetup(new UxInspectorStoreSnapshotController(snapshots, auth)).build();

        mvc.perform(post("/api/ux-inspector/store-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"captureId\":\"cap_crm_contact_save\",\"origin\":\"https://crm.example.com\",\"state\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeSnapshotRef").value("opaque-ref"))
                .andExpect(jsonPath("$.bytes").value(42))
                .andExpect(jsonPath("$.state").doesNotExist());
        verify(snapshots).stage(any(), any());
    }

    @Test
    void mapsInvalidStoreEnvelopeToBadRequest() throws Exception {
        var snapshots = mock(UxInspectorStoreSnapshotService.class);
        when(snapshots.stage(any(), any())).thenThrow(new IllegalArgumentException("Invalid store snapshot envelope."));
        var mvc = MockMvcBuilders.standaloneSetup(new UxInspectorStoreSnapshotController(
                snapshots, mock(AnalysisAiAuthRefResolver.class))).build();

        mvc.perform(post("/api/ux-inspector/store-snapshots").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
