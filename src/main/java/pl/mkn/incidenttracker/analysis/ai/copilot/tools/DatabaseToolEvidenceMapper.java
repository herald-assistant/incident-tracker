package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.JsonNode;
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
        var resultNode = payloadReader.readJsonNode(rawResult);
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "diagnosticQuestion", databaseDiagnosticQuestion(toolName, argumentsNode));
        addAttribute(attributes, "environment", payloadReader.readTopLevelText(resultNode, "environment"));
        addAttribute(attributes, "databaseAlias", payloadReader.readTopLevelText(resultNode, "databaseAlias"));
        addAttribute(attributes, "parameters", payloadReader.prettyPayload(argumentsNode, rawArguments, "{}"));
        addAttribute(attributes, "resultSummary", databaseResultSummary(resultNode));
        addAttribute(attributes, "result", payloadReader.prettyPayload(resultNode, rawResult, ""));

        return accumulator.appendDatabaseItem(
                databaseToolKey(toolCallId, toolName),
                new AnalysisEvidenceItem(
                        databaseToolTitle(toolName),
                        List.copyOf(attributes)
                )
        );
    }

    private String databaseDiagnosticQuestion(String toolName, JsonNode argumentsNode) {
        var reason = payloadReader.readTopLevelText(argumentsNode, "reason");
        if (StringUtils.hasText(reason)) {
            return reason;
        }

        var sql = payloadReader.readTopLevelText(argumentsNode, "sql");
        if (StringUtils.hasText(sql)) {
            return "Validate data hypothesis with readonly SQL: " + abbreviate(sql, 160);
        }

        var table = tableRef(payloadReader.field(argumentsNode, "table"));
        if (StringUtils.hasText(table)) {
            return "Inspect " + table + " with " + safeKeyPart(toolName);
        }

        var tables = tableRefs(payloadReader.field(argumentsNode, "tables"));
        if (StringUtils.hasText(tables)) {
            return "Inspect joined tables " + tables + " with " + safeKeyPart(toolName);
        }

        var discoverySeed = payloadReader.joinNonBlank(
                "application=" + payloadReader.readTopLevelText(argumentsNode, "applicationNamePattern"),
                "table=" + payloadReader.readTopLevelText(argumentsNode, "tableNamePattern"),
                "column=" + payloadReader.readTopLevelText(argumentsNode, "columnNamePattern"),
                "hint=" + payloadReader.readTopLevelText(argumentsNode, "entityOrKeywordHint")
        );
        if (StringUtils.hasText(discoverySeed)) {
            return "Find database objects for " + discoverySeed;
        }

        return "Run " + safeKeyPart(toolName) + " for current incident database scope.";
    }

    private String databaseResultSummary(JsonNode resultNode) {
        if (resultNode == null || !resultNode.isObject()) {
            return null;
        }

        var parts = new ArrayList<String>();
        addSummaryPart(parts, "table", tableRef(payloadReader.field(resultNode, "table")));
        addSummaryPart(parts, "count", payloadReader.readTopLevelText(resultNode, "count"));
        addSummaryPart(parts, "exists", payloadReader.readTopLevelText(resultNode, "exists"));
        addSummaryPart(parts, "candidateCount", payloadReader.arraySizeText(payloadReader.field(resultNode, "candidates")));
        addSummaryPart(parts, "rowCount", payloadReader.arraySizeText(payloadReader.field(resultNode, "rows")));
        addSummaryPart(parts, "groupCount", payloadReader.arraySizeText(payloadReader.field(resultNode, "groups")));
        addSummaryPart(parts, "relationshipCount", payloadReader.arraySizeText(payloadReader.field(resultNode, "relationships")));
        addSummaryPart(parts, "columnCount", payloadReader.arraySizeText(payloadReader.field(resultNode, "columns")));
        addSummaryPart(parts, "truncated", payloadReader.readTopLevelText(resultNode, "truncated"));
        addSummaryPart(parts, "warningCount", payloadReader.arraySizeText(payloadReader.field(resultNode, "warnings")));

        if (parts.isEmpty()) {
            return "resultFields=" + payloadReader.summarizeValues(payloadReader.fieldNames(resultNode));
        }

        return String.join(" ", parts);
    }

    private String tableRefs(JsonNode tablesNode) {
        var values = new ArrayList<String>();
        for (var tableNode : payloadReader.iterableArray(tablesNode)) {
            addDistinct(values, tableRef(tableNode));
        }
        return payloadReader.summarizeValues(values);
    }

    private String tableRef(JsonNode tableNode) {
        if (tableNode == null || !tableNode.isObject()) {
            return null;
        }

        var schema = payloadReader.readTopLevelText(tableNode, "schema");
        var tableName = payloadReader.readTopLevelText(tableNode, "tableName");
        if (StringUtils.hasText(schema) && StringUtils.hasText(tableName)) {
            return schema.trim() + "." + tableName.trim();
        }
        if (StringUtils.hasText(tableName)) {
            return tableName.trim();
        }
        return payloadReader.readTopLevelText(tableNode, "name");
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private void addSummaryPart(List<String> parts, String name, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(name + "=" + value.trim());
        }
    }

    private void addDistinct(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value.trim())) {
            values.add(value.trim());
        }
    }

    private String databaseToolKey(String toolCallId, String toolName) {
        if (StringUtils.hasText(toolCallId)) {
            return toolCallId.trim();
        }
        return "db-tool::" + safeKeyPart(toolName);
    }

    private String databaseToolTitle(String toolName) {
        return StringUtils.hasText(toolName) ? toolName.trim() : "database-tool";
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > maxLength
                ? normalized.substring(0, maxLength) + "...(" + normalized.length() + " chars)"
                : normalized;
    }

    private String safeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
