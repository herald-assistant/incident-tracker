package pl.mkn.tdw.features.operationalcontextassistance.ai;

import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;

import java.util.Set;

public record OperationalContextAssistanceCopilotResult(
        CopilotExecutionResult executionResult,
        Set<String> readSourceRefs
) {
    public OperationalContextAssistanceCopilotResult {
        readSourceRefs = readSourceRefs != null ? Set.copyOf(readSourceRefs) : Set.of();
    }
}
