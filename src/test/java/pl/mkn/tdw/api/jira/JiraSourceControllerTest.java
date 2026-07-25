package pl.mkn.tdw.api.jira;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JiraSourceController.class)
class JiraSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JiraIssuePort jiraIssuePort;

    @Test
    void shouldExposeJiraIssueMaterialForIssueLink() throws Exception {
        when(jiraIssuePort.getIssueMaterial("CRM-123")).thenReturn(new JiraIssueMaterial(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "Customer status",
                "Show customer status.",
                "Story",
                "Ready for Test",
                List.of("release"),
                List.of("Status is visible."),
                List.of(),
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/jira/issue/material")
                        .contentType("application/json")
                        .content("""
                                {
                                  "issueRef": "https://jira.example.com/browse/CRM-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueKey").value("CRM-123"))
                .andExpect(jsonPath("$.summary").value("Customer status"))
                .andExpect(jsonPath("$.acceptanceCriteria[0]").value("Status is visible."));

        verify(jiraIssuePort).getIssueMaterial("CRM-123");
    }

    @Test
    void shouldRejectIssueReferenceWithoutIssueKey() throws Exception {
        mockMvc.perform(post("/api/jira/issue/material")
                        .contentType("application/json")
                        .content("""
                                {
                                  "issueRef": "not-an-issue"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JIRA_SOURCE_BAD_REQUEST"));
    }

    @Test
    void shouldReturnUnavailableWhenJiraAdapterFails() throws Exception {
        when(jiraIssuePort.getIssueMaterial("CRM-123"))
                .thenThrow(new IllegalStateException("analysis.jira.base-url must be configured for Jira REST mode."));

        mockMvc.perform(post("/api/jira/issue/material")
                        .contentType("application/json")
                        .content("""
                                {
                                  "issueRef": "CRM-123"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("JIRA_SOURCE_UNAVAILABLE"));
    }
}
