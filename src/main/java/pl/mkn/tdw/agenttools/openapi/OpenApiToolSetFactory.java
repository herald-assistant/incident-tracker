package pl.mkn.tdw.agenttools.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.openapi.OpenApiExecutionPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class OpenApiToolSetFactory {
    private final OpenApiExecutionPort executor;
    private final ObjectMapper mapper;

    public List<ToolCallback> create(List<OpenApiExecution> executions) {
        return create(executions, new AtomicBoolean(false));
    }

    /** The caller owns the lifecycle of a turn or an MCP session's uncertain-write state. */
    public List<ToolCallback> create(List<OpenApiExecution> executions, AtomicBoolean uncertainWrite) {
        return executions.stream().map(execution -> (ToolCallback) new ToolCallback() {
            private final pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation operation = execution.operation();
            @Override
            public ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition(operation.name(), operation.description(), operation.inputSchema().toString());
            }

            @Override
            public String call(String input) {
                if (input == null || input.length() > 65_536)
                    throw new OpenApiException("Argumenty przekraczają limit 65536 znaków.");
                if (operation.mutation() && uncertainWrite.get())
                    return "{\"outcome\":\"WRITE_BLOCKED\",\"message\":\"Poprzedni zapis ma nieustalony wynik. W tej turze użyj odczytu.\"}";
                try {
                    var result = executor.execute(execution, new OpenApiArguments(mapper.readTree(input)));
                    if (operation.mutation() && result.outcome().equals("OUTCOME_UNKNOWN")) uncertainWrite.set(true);
                    return mapper.writeValueAsString(result);
                } catch (java.io.IOException ex) {
                    throw new OpenApiException("Argumenty muszą być poprawnym JSON.");
                }
            }
        }).toList();
    }
}
