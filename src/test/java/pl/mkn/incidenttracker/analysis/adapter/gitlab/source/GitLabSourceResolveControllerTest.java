package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitLabSourceResolveController.class)
class GitLabSourceResolveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitLabSourceResolveService gitLabSourceResolveService;

    @Test
    void shouldResolveSourceForValidRequest() throws Exception {
        when(gitLabSourceResolveService.resolve(any(GitLabSourceResolveRequest.class)))
                .thenReturn(new GitLabSourceResolveResponse(
                        "src/main/java/c/e/synthetic/response/ResponsePathSelector.java",
                        130,
                        java.util.List.of(
                                "src/main/java/c/e/synthetic/response/ResponsePathSelector.java",
                                "src/test/java/c/e/synthetic/response/ResponsePathSelectorTest.java"
                        ),
                        "package c.e.synthetic.response; public class ResponsePathSelector {}",
                        "OK"
                ));

        mockMvc.perform(post("/api/gitlab/source/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gitlabBaseUrl": "https://gitlab.example.com",
                                  "groupPath": "my-group/subgroup",
                                  "projectPath": "my-service",
                                  "ref": "HEAD",
                                  "symbol": "c.e.synthetic.response.ResponsePathSelector"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedPath").value("src/main/java/c/e/synthetic/response/ResponsePathSelector.java"))
                .andExpect(jsonPath("$.score").value(130))
                .andExpect(jsonPath("$.candidates[0]").value("src/main/java/c/e/synthetic/response/ResponsePathSelector.java"))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(gitLabSourceResolveService).resolve(new GitLabSourceResolveRequest(
                "https://gitlab.example.com",
                "my-group/subgroup",
                "my-service",
                "HEAD",
                "c.e.synthetic.response.ResponsePathSelector"
        ));
    }

    @Test
    void shouldReturnBadRequestForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/source/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gitlabBaseUrl": " ",
                                  "groupPath": "",
                                  "projectPath": "my-service",
                                  "ref": "HEAD",
                                  "symbol": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field").isArray());

        verifyNoInteractions(gitLabSourceResolveService);
    }

}


