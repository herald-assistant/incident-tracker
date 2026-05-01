package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.database;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseToolEvidenceMapper {

    private static final String DATABASE_PROVIDER = "database";
    private static final String DATABASE_TOOL_CATEGORY = "tool-results";
    private static final String DATABASE_ORDER_NAMESPACE = "database";
    private static final String DATABASE_FALLBACK_KEY = "db-tool";

    private final JsonPayloadReader payloadReader;

    public boolean supports(String toolName) {
        return StringUtils.hasText(toolName) && toolName.trim().startsWith("db_");
    }

    AnalysisEvidenceSection capture(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        var resultNode = payloadReader.readJsonNode(rawResult);
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "reason", payloadReader.readTopLevelText(argumentsNode, "reason"));
        addAttribute(attributes, "result", payloadReader.prettyPayload(userFacingResultNode(resultNode), rawResult, ""));

        return sessionEvidence.appendItem(
                DATABASE_PROVIDER,
                DATABASE_TOOL_CATEGORY,
                databaseToolKey(toolCallId, toolName),
                DATABASE_ORDER_NAMESPACE,
                DATABASE_FALLBACK_KEY,
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
