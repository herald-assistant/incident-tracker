package pl.mkn.tdw.features.uxinspector.job.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorJobService;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

@WebMvcTest(UxInspectorJobController.class)
class UxInspectorJobControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean UxInspectorJobService jobService;
    @MockitoBean UxInspectorExportService exportService;

    @Test
    void shouldAcceptOnlyTypedCaptureV2WithRequiredAiSelection() throws Exception {
        when(jobService.startJob(any())).thenReturn(null);

        mockMvc.perform(post("/api/ux-inspector/jobs").contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(requestDocument())))
                .andExpect(status().isAccepted());

        var request = ArgumentCaptor.forClass(UxInspectorJobStartRequest.class);
        verify(jobService).startJob(request.capture());
        assertThat(request.getValue().capture().version()).isEqualTo(1);
        assertThat(request.getValue().model()).isEqualTo("gpt-crm");
    }

    @Test
    void shouldRejectMissingModelAndUnknownRequestField() throws Exception {
        var missingModel = requestDocument();
        missingModel.remove("model");
        var unknown = requestDocument();
        unknown.put("legacyInspector", true);
        mockMvc.perform(post("/api/ux-inspector/jobs").contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(missingModel)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ux-inspector/jobs").contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(unknown)))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> requestDocument() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("systemId", "crm-agent-portal");
        result.put("branch", "main");
        result.put("viewId", "crm-contact-create");
        result.put("sourceRevision", "abc123crm");
        result.put("question", "Dlaczego przycisk jest zablokowany?");
        result.put("capture", objectMapper.convertValue(capture(), Map.class));
        result.put("model", "gpt-crm");
        result.put("reasoningEffort", "medium");
        return result;
    }
}
