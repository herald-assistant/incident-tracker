package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.database.DatabaseToolNames;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeVerificationCopilotDatabaseReadonlyPolicyTest {

    private final ChangeVerificationCopilotDatabaseReadonlyPolicy policy =
            new ChangeVerificationCopilotDatabaseReadonlyPolicy();

    @Test
    void shouldRejectRawSqlForChangeVerification() {
        var exception = assertThrows(CopilotToolInvocationRejectedException.class, () -> policy.beforeInvocation(
                request(DatabaseToolNames.EXECUTE_READONLY_SQL, changeVerificationContext())
        ));

        var result = result(exception);
        assertEquals("denied_by_change_verification_database_policy", result.get("status"));
        assertEquals(DatabaseToolNames.EXECUTE_READONLY_SQL, result.get("toolName"));
        assertTrue(result.get("instruction").toString().contains("typed readonly DB tools"));
    }

    @Test
    void shouldAllowTypedReadonlyDbToolsForChangeVerification() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                DatabaseToolNames.FIND_TABLES,
                changeVerificationContext()
        )));
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                DatabaseToolNames.COUNT_ROWS,
                changeVerificationContext()
        )));
    }

    @Test
    void shouldIgnoreOtherFeaturesAndNonDatabaseTools() {
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                DatabaseToolNames.EXECUTE_READONLY_SQL,
                Map.of("feature", "incident-analysis")
        )));
        assertDoesNotThrow(() -> policy.beforeInvocation(request(
                GitLabToolNames.READ_REPOSITORY_FILE,
                changeVerificationContext()
        )));
    }

    private static CopilotToolInvocationPolicyRequest request(String toolName, Map<String, Object> hiddenContext) {
        var sessionContext = new CopilotToolSessionContext("cv-123", "change-verification-cv-123", hiddenContext);
        return new CopilotToolInvocationPolicyRequest(
                sessionContext,
                sessionContext.copilotSessionId(),
                "tool-call-1",
                toolName,
                "{}"
        );
    }

    private static Map<String, Object> changeVerificationContext() {
        return Map.of(
                ChangeVerificationCopilotToolContextKeys.FEATURE,
                ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE,
                ChangeVerificationCopilotToolContextKeys.DATABASE_READONLY_ONLY,
                true
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(CopilotToolInvocationRejectedException exception) {
        return (Map<String, Object>) exception.result();
    }
}
