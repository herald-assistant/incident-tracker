package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeVerificationCopilotGitLabScopePolicyTest {

    private final ChangeVerificationCopilotGitLabScopePolicy policy =
            new ChangeVerificationCopilotGitLabScopePolicy(new ObjectMapper());

    @Test
    void shouldAllowGitLabReadWhenProjectBranchAndFileAreInChangeScope() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILE,
                Map.of(
                        "projectName", "customer-api",
                        "branchRef", "feature/CRM-123-status",
                        "filePath", "src/main/java/CustomerController.java",
                        "reason", "sprawdzam implementacje story"
                )
        )));
    }

    @Test
    void shouldRejectGitLabReadOutsideRepositoryScope() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILE,
                Map.of(
                        "projectName", "CRM/runtime/other-api",
                        "branchRef", "feature/CRM-123-status",
                        "filePath", "src/main/java/CustomerController.java"
                )
        )));

        var result = result(exception);
        assertEquals("denied_by_change_verification_scope_policy", result.get("status"));
        assertTrue(result.get("reason").toString().contains("outside the Change Verification scope"));
        assertTrue(result.get("instruction").toString().contains("repository-scope.md"));
    }

    @Test
    void shouldRejectGitLabReadOutsideBranchScope() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILE,
                Map.of(
                        "projectName", "CRM/runtime/customer-api",
                        "branchRef", "feature/OTHER",
                        "filePath", "src/main/java/CustomerController.java"
                )
        )));

        assertTrue(result(exception).get("reason").toString().contains("branch/ref outside"));
    }

    @Test
    void shouldRejectGitLabReadOutsideChangedFilesAndInstructions() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                Map.of(
                        "projectName", "CRM/runtime/customer-api",
                        "branchRef", "feature/CRM-123-status",
                        "filePath", "src/main/java/UnrelatedService.java",
                        "startLine", 1,
                        "endLine", 20
                )
        )));

        assertTrue(result(exception).get("instruction").toString().contains("plikow zmienionych"));
    }

    @Test
    void shouldValidateAllFilesInBatchRead() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
                Map.of(
                        "projectName", "CRM/runtime/customer-api",
                        "branchRef", "feature/CRM-123-status",
                        "filePaths", List.of("AGENTS.md", "src/main/java/UnrelatedService.java")
                )
        )));

        assertTrue(result(exception).get("reason").toString().contains("unrelatedservice"));
    }

    @Test
    void shouldValidateChunkProjectNamesAndFiles() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
                Map.of(
                        "branchRef", "feature/CRM-123-status",
                        "chunks", List.of(Map.of(
                                "projectName", "CRM/runtime/customer-api",
                                "filePath", "AGENTS.md",
                                "startLine", 1,
                                "endLine", 20
                        ))
                )
        )));
    }

    @Test
    void shouldRejectBroadProjectListSearchWithoutExplicitProjects() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(request(
                GitLabToolNames.FIND_FLOW_CONTEXT,
                Map.of(
                        "branchRef", "feature/CRM-123-status",
                        "keywords", List.of("customer")
                )
        )));

        assertTrue(result(exception).get("instruction").toString().contains("projectName"));
    }

    @Test
    void shouldAllowProjectListSearchInsideRepositoryScope() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.FIND_FLOW_CONTEXT,
                Map.of(
                        "projectNames", List.of("CRM/runtime/customer-api"),
                        "branchRef", "main",
                        "keywords", List.of("CustomerController")
                )
        )));
    }

    @Test
    void shouldIgnoreNonChangeVerificationAndNonGitLabInvocations() {
        assertDoesNotThrow(() -> policy.beforeInvocation(new CopilotToolInvocationPolicyRequest(
                new CopilotToolSessionContext("run-1", "session-1", Map.of("feature", "flow-explorer")),
                "session-1",
                "tool-call-1",
                GitLabToolNames.READ_REPOSITORY_FILE,
                "{}"
        )));
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                "db_describe_table",
                Map.of("tableName", "CUSTOMER")
        )));
    }

    private static CopilotToolInvocationPolicyRequest request(String toolName, Map<String, Object> arguments) {
        var sessionContext = new CopilotToolSessionContext(
                "cv-123",
                "change-verification-cv-123",
                hiddenContext()
        );
        return new CopilotToolInvocationPolicyRequest(
                sessionContext,
                sessionContext.copilotSessionId(),
                "tool-call-1",
                toolName,
                toJson(arguments)
        );
    }

    private static Map<String, Object> hiddenContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put(ChangeVerificationCopilotToolContextKeys.FEATURE, ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE);
        context.put(
                ChangeVerificationCopilotToolContextKeys.RUN_KIND,
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE
        );
        context.put(ChangeVerificationCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED, true);
        context.put(ChangeVerificationCopilotToolContextKeys.ALLOWED_REPOSITORIES, List.of(Map.of(
                "repositoryKey", "CRM/runtime/customer-api",
                "projectPath", "CRM/runtime/customer-api",
                "projectName", "customer-api",
                "sourceRef", "feature/CRM-123-status",
                "targetRef", "main",
                "changedFiles", List.of(Map.of(
                        "path", "src/main/java/CustomerController.java",
                        "oldPath", "src/main/java/CustomerController.java",
                        "newPath", "src/main/java/CustomerController.java"
                )),
                "instructionSources", List.of(Map.of(
                        "path", "AGENTS.md",
                        "kind", "AGENTS",
                        "ref", "feature/CRM-123-status"
                ))
        )));
        return context;
    }

    private static String toJson(Map<String, Object> arguments) {
        try {
            return new ObjectMapper().writeValueAsString(arguments);
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(CopilotToolInvocationRejectedException exception) {
        return (Map<String, Object>) exception.result();
    }
}
