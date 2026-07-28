package pl.mkn.tdw.api.confluence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.confluence.ConfluencePageContent;
import pl.mkn.tdw.integrations.confluence.ConfluencePagePort;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfluenceSourceController.class)
class ConfluenceSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfluencePagePort confluencePagePort;

    @Test
    void shouldExposeConfluencePageContentForPageUrl() throws Exception {
        var pageUrl = "https://confluence.example.com/pages/viewpage.action?pageId=123";
        when(confluencePagePort.getPageContent(pageUrl)).thenReturn(Optional.of(new ConfluencePageContent(
                "123",
                "CRM customer profile",
                pageUrl,
                "Customer profile description.",
                "7",
                List.of()
        )));

        mockMvc.perform(post("/api/confluence/page/content")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pageUrl": "https://confluence.example.com/pages/viewpage.action?pageId=123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId").value("123"))
                .andExpect(jsonPath("$.title").value("CRM customer profile"))
                .andExpect(jsonPath("$.version").value("7"))
                .andExpect(jsonPath("$.content").value("Customer profile description."));

        verify(confluencePagePort).getPageContent(pageUrl);
    }

    @Test
    void shouldRejectPageUrlOutsideConfiguredPattern() throws Exception {
        var pageUrl = "https://other.example.com/pages/123";
        when(confluencePagePort.getPageContent(pageUrl)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/confluence/page/content")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pageUrl": "https://other.example.com/pages/123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFLUENCE_SOURCE_BAD_REQUEST"));
    }

    @Test
    void shouldReturnUnavailableWhenConfluenceAdapterFails() throws Exception {
        var pageUrl = "https://confluence.example.com/pages/123";
        when(confluencePagePort.getPageContent(pageUrl))
                .thenThrow(new IllegalStateException("Confluence client is not available."));

        mockMvc.perform(post("/api/confluence/page/content")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pageUrl": "https://confluence.example.com/pages/123"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFLUENCE_SOURCE_UNAVAILABLE"));
    }
}
