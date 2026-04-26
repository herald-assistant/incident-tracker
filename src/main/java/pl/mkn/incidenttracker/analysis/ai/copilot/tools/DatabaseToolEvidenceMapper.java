package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseToolEvidenceMapper {

    private final ToolJsonPayloadReader payloadReader;

    public boolean supports(String toolName) {
        return StringUtils.hasText(toolName) && toolName.trim().startsWith("db_");
    }

    AnalysisEvidenceSection capture(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        var resultNode = payloadReader.readJsonNode(rawResult);
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "reason", payloadReader.readTopLevelText(argumentsNode, "reason"));
        addAttribute(attributes, "result", payloadReader.prettyPayload(userFacingResultNode(resultNode), rawResult, ""));

        return accumulator.appendDatabaseItem(
                databaseToolKey(toolCallId, toolName),
                new AnalysisEvidenceItem(
                        databaseToolTitle(toolName),
                        List.copyOf(attributes)
                )
        );
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private com.fasterxml.jackson.databind.JsonNode userFacingResultNode(com.fasterxml.jackson.databind.JsonNode resultNode) {
        if (resultNode == null || !resultNode.isObject() || resultNode.get("reason") == null) {
            return resultNode;
        }

        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) resultNode.deepCopy();
        copy.remove("reason");
        return copy;
    }

    private String databaseToolKey(String toolCallId, String toolName) {
        if (StringUtils.hasText(toolCallId)) {
            return toolCallId.trim();
        }
        return "db-tool::" + databaseToolTitle(toolName);
    }

    private String databaseToolTitle(String toolName) {
        return StringUtils.hasText(toolName) ? toolName.trim() : "database-tool";
    }
}
