package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.database.DatabaseToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 95)
public class ChangeVerificationCopilotDatabaseReadonlyPolicy implements CopilotToolInvocationPolicy {

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!changeVerificationDatabaseInvocation(request)) {
            return;
        }

        if (DatabaseToolNames.EXECUTE_READONLY_SQL.equals(request.toolName())) {
            reject(
                    request,
                    "Raw SQL is not available in Change Verification AI analysis.",
                    "Uzyj typed readonly DB tools, np. db_find_tables, db_find_columns, db_describe_table, db_count_rows albo db_exists_by_key. Jezeli potrzebna jest manualna SQL-ka, zaproponuj ja operatorowi jako rekomendacje, ale jej nie wykonuj."
            );
        }
    }

    private boolean changeVerificationDatabaseInvocation(CopilotToolInvocationPolicyRequest request) {
        var context = hiddenContext(request);
        return request != null
                && request.toolName() != null
                && request.toolName().startsWith(DatabaseToolNames.PREFIX)
                && ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE.equals(
                context.get(ChangeVerificationCopilotToolContextKeys.FEATURE)
        );
    }

    private Map<String, Object> hiddenContext(CopilotToolInvocationPolicyRequest request) {
        return request != null && request.sessionContext() != null
                ? request.sessionContext().hiddenContext()
                : Map.of();
    }

    private void reject(CopilotToolInvocationPolicyRequest request, String reason, String instruction) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "denied_by_change_verification_database_policy");
        result.put("toolName", request.toolName());
        result.put("toolCallId", request.toolCallId());
        result.put("reason", reason);
        result.put("instruction", instruction);
        result.put("retryableWithChangedArguments", true);
        throw new CopilotToolInvocationRejectedException(reason, result);
    }
}
