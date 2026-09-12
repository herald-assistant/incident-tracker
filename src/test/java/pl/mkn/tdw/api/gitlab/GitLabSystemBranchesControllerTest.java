package pl.mkn.tdw.api.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryBranchService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitLabSystemBranchesController.class)
class GitLabSystemBranchesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationalContextPort operationalContextPort;

    @MockitoBean
    private GitLabProperties gitLabProperties;

    @MockitoBean
    private GitLabRepositoryBranchService branchService;

    @Test
    void listsOnlyScopedPrimaryRepositoriesAndUnionsBranches() throws Exception {
        when(gitLabProperties.getGroup()).thenReturn("crm");
        when(operationalContextPort.loadContext(any())).thenReturn(catalog());
        when(branchService.listBranches("crm/backend", "release"))
                .thenReturn(new GitLabRepositoryBranchService.BranchPage(List.of(
                        new GitLabRepositoryBranchService.Branch("release/one", false)), false));
        when(branchService.listBranches("crm/portal", "release"))
                .thenReturn(new GitLabRepositoryBranchService.BranchPage(List.of(
                        new GitLabRepositoryBranchService.Branch("release/one", true),
                        new GitLabRepositoryBranchService.Branch("release/two", false)), true));

        mockMvc.perform(get("/api/gitlab/systems/crm-system/branches").queryParam("search", "release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches.length()").value(2))
                .andExpect(jsonPath("$.branches[0].name").value("release/one"))
                .andExpect(jsonPath("$.branches[0].isDefault").value(true))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.projectPath").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(branchService).listBranches("crm/backend", "release");
        verify(branchService).listBranches("crm/portal", "release");
    }

    @Test
    void rejectsSystemWithoutPrimaryRepositoryInConfiguredGroup() throws Exception {
        when(gitLabProperties.getGroup()).thenReturn("other-group");
        when(operationalContextPort.loadContext(any())).thenReturn(catalog());

        mockMvc.perform(get("/api/gitlab/systems/crm-system/branches"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsUnknownSystemAndTooLongFilter() throws Exception {
        when(gitLabProperties.getGroup()).thenReturn("crm");
        when(operationalContextPort.loadContext(any())).thenReturn(catalog());

        mockMvc.perform(get("/api/gitlab/systems/unknown/branches"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/gitlab/systems/crm-system/branches")
                        .queryParam("search", "a".repeat(161)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsPartialBranchesWithoutLeakingFailedRepositoryDetails() throws Exception {
        when(gitLabProperties.getGroup()).thenReturn("crm");
        when(operationalContextPort.loadContext(any())).thenReturn(catalog());
        when(branchService.listBranches("crm/backend", ""))
                .thenReturn(new GitLabRepositoryBranchService.BranchPage(List.of(
                        new GitLabRepositoryBranchService.Branch("main", true)), false));
        when(branchService.listBranches("crm/portal", ""))
                .thenThrow(new IllegalStateException("private upstream detail"));

        mockMvc.perform(get("/api/gitlab/systems/crm-system/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[0].name").value("main"))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.warnings[0]").value(
                        "Nie udało się pobrać gałęzi z jednego repozytorium GitLab."));
    }

    @Test
    void reportsGatewayFailureWhenNoRepositoryCanBeRead() throws Exception {
        when(gitLabProperties.getGroup()).thenReturn("crm");
        when(operationalContextPort.loadContext(any())).thenReturn(catalog());
        when(branchService.listBranches(any(), any()))
                .thenThrow(new IllegalStateException("private upstream detail"));

        mockMvc.perform(get("/api/gitlab/systems/crm-system/branches"))
                .andExpect(status().isBadGateway());
    }

    private OperationalContextDtos.OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(), List.of(),
                List.of(Map.of("id", "crm-system", "systemType", "internal-service", "systemSubtype", "backend")),
                List.of(),
                List.of(
                        Map.of("id", "backend", "git", Map.of("provider", "gitlab", "group", "crm",
                                "projectPath", "crm/backend")),
                        Map.of("id", "portal", "git", Map.of("provider", "gitlab", "group", "crm",
                                "projectPath", "crm/portal")),
                        Map.of("id", "outside", "git", Map.of("provider", "gitlab", "group", "outside",
                                "projectPath", "outside/secret"))
                ),
                List.of(Map.of(
                        "id", "crm-code", "target", Map.of("type", "system", "id", "crm-system"),
                        "repositories", List.of(
                                Map.of("repoId", "backend", "role", "primary", "priority", 1),
                                Map.of("repoId", "portal", "role", "primary", "priority", 1),
                                Map.of("repoId", "outside", "role", "primary", "priority", 1)
                        )
                )),
                List.of(), List.of(), List.of(), List.of());
    }
}
