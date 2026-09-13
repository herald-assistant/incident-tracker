package pl.mkn.tdw.features.operationalcontextassistance.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceCollector;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryBranchService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalContextAssistanceBranchOptionsController.class)
class OperationalContextAssistanceBranchOptionsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalContextGitLabSourceCollector sourceCollector;
    @MockitoBean GitLabRepositoryBranchService branchService;

    @Test
    void listsFilteredBranchesForSelectedNestedProject() throws Exception {
        when(sourceCollector.projectPathForBranchOptions("PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS", null))
                .thenReturn("CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS");
        when(branchService.listBranches("CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS", "release"))
                .thenReturn(new GitLabRepositoryBranchService.BranchPage(
                        List.of(new GitLabRepositoryBranchService.Branch("release/2026.09", false)), true));

        mockMvc.perform(get("/api/operational-context/assistance/source-options/branches")
                        .param("project", "PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS")
                        .param("search", " release "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[0].name").value("release/2026.09"))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.warnings").isEmpty());
        verify(sourceCollector).projectPathForBranchOptions("PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS", null);
    }

    @Test
    void acceptsAFullProjectUrlWithoutSplittingSubgroupsInTheBrowser() throws Exception {
        var url = "https://gitlab.example.com/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS";
        when(sourceCollector.projectPathForBranchOptions(null, url))
                .thenReturn("CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS");
        when(branchService.listBranches("CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS", ""))
                .thenReturn(new GitLabRepositoryBranchService.BranchPage(
                        List.of(new GitLabRepositoryBranchService.Branch("main", true)), false));

        mockMvc.perform(get("/api/operational-context/assistance/source-options/branches")
                        .param("projectUrl", url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[0].name").value("main"))
                .andExpect(jsonPath("$.branches[0].isDefault").value(true));
    }

    @Test
    void rejectsOversizeFilterBeforeGitLabLookup() throws Exception {
        mockMvc.perform(get("/api/operational-context/assistance/source-options/branches")
                        .param("project", "project")
                        .param("search", "x".repeat(161)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sourceCollector, branchService);
    }
}
