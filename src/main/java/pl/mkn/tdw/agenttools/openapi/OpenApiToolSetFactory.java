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
import pl.mkn.tdw.agenttools.content.ToolContentCallback;
import pl.mkn.tdw.agenttools.content.ToolContentResult;
import pl.mkn.tdw.common.documents.PdfPageRenderer;
import pl.mkn.tdw.common.documents.DocxContentReader;
import org.springframework.ai.chat.model.ToolContext;

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
        return executions.stream().map(execution -> (ToolCallback) new ToolContentCallback() {
            private final pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation operation = execution.operation();
            @Override
            public ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition(operation.name(), operation.description(), operation.inputSchema().toString());
            }

            @Override
            public ToolContentResult callWithContent(String input, ToolContext context) {
                if (input == null || input.length() > 65_536)
                    throw new OpenApiException("Argumenty przekraczają limit 65536 znaków.");
                if (operation.mutation() && uncertainWrite.get())
                    return new ToolContentResult("{\"outcome\":\"WRITE_BLOCKED\",\"message\":\"Poprzedni zapis ma nieustalony wynik. W tej turze użyj odczytu.\"}", List.of());
                try {
                    var result = executor.execute(execution, new OpenApiArguments(mapper.readTree(input)));
                    if (operation.mutation() && result.outcome().equals("OUTCOME_UNKNOWN")) uncertainWrite.set(true);
                    var binaries = new java.util.ArrayList<ToolContentResult.Binary>();
                    var text = mapper.valueToTree(result);
                    if (result.file() != null) {
                        var file = result.file();
                        try {
                            if (file.mimeType().equals("application/pdf")) {
                                for (var page : PdfPageRenderer.render(file.name(), file.content()))
                                    binaries.add(new ToolContentResult.Binary(page.name(), "image/jpeg", page.content()));
                            } else if (file.mimeType().equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                                var document = DocxContentReader.read(file.content());
                                ((com.fasterxml.jackson.databind.node.ObjectNode) text).put("documentText", document.text());
                                for (var image : document.images()) binaries.add(new ToolContentResult.Binary(
                                        file.name() + " — " + image.name(), image.mimeType(), image.content()));
                            } else binaries.add(new ToolContentResult.Binary(file.name(), file.mimeType(), file.content()));
                            ((com.fasterxml.jackson.databind.node.ObjectNode) text).put("fileDelivery", "CURRENT_TURN_ONLY");
                        } catch (IllegalArgumentException failure) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) text).put("fileDelivery", "FAILED")
                                    .put("fileError", "Nie udało się przygotować zawartości pliku. HTTP zostało wykonane; nie ponawiaj mutacji.");
                        }
                    }
                    return new ToolContentResult(mapper.writeValueAsString(text), binaries);
                } catch (java.io.IOException ex) {
                    throw new OpenApiException("Argumenty muszą być poprawnym JSON.");
                }
            }
        }).toList();
    }
}
