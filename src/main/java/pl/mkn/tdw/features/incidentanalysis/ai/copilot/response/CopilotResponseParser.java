package pl.mkn.tdw.features.incidentanalysis.ai.copilot.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.response.CopilotResponseDtos.ParseResult;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.response.CopilotResponseDtos.StructuredAnalysisResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CopilotResponseParser {

    private static final String DEFAULT_FUNCTIONAL_ANALYSIS =
            "Nie udalo sie sparsowac funkcjonalnego wyniku analizy. Sprawdz surowa odpowiedz Copilota i prompt.";
    private static final String DEFAULT_TECHNICAL_ANALYSIS =
            "Nie udalo sie sparsowac technicznego wyniku analizy. Sprawdz surowa odpowiedz Copilota i prompt.";
    private static final Pattern FENCED_JSON_PATTERN = Pattern.compile(
            "```(?:json|JSON)?\\s*([\\s\\S]*?)\\s*```",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;

    public ParseResult parse(String assistantContent) {
        var fullJson = parseJsonNode(assistantContent);
        if (fullJson != null) {
            return resultFromResponse(
                    responseFromJson(fullJson),
                    assistantContent,
                    parsedJsonFields(fullJson)
            );
        }

        var fencedJson = extractFencedJson(assistantContent);
        if (fencedJson != null) {
            var fencedNode = parseJsonNode(fencedJson);
            if (fencedNode != null) {
                return resultFromResponse(
                        responseFromJson(fencedNode),
                        assistantContent,
                        parsedJsonFields(fencedNode)
                );
            }
        }

        var embeddedJsonResult = resultFromEmbeddedJsonObject(assistantContent);
        if (embeddedJsonResult != null) {
            return embeddedJsonResult;
        }

        return fallbackResult(null, assistantContent, List.of());
    }

    private ParseResult resultFromResponse(
            StructuredAnalysisResponse parsedResponse,
            String assistantContent,
            List<String> parsedFields
    ) {
        if (hasRequiredFields(parsedResponse)) {
            return new ParseResult(
                    normalizeResponse(parsedResponse),
                    true,
                    false,
                    parsedFields
            );
        }

        return fallbackResult(parsedResponse, assistantContent, parsedFields);
    }

    private ParseResult fallbackResult(
            StructuredAnalysisResponse parsedResponse,
            String assistantContent,
            List<String> parsedFields
    ) {
        return new ParseResult(
                fallbackResponse(parsedResponse, assistantContent),
                false,
                true,
                parsedFields
        );
    }

    private StructuredAnalysisResponse fallbackResponse(
            StructuredAnalysisResponse parsedResponse,
            String assistantContent
    ) {
        return new StructuredAnalysisResponse(
                firstText(parsedResponse != null ? parsedResponse.detectedProblem() : null, "AI_UNSTRUCTURED_RESPONSE"),
                firstText(parsedResponse != null ? parsedResponse.affectedProcess() : null, "Nie ustalono"),
                firstText(parsedResponse != null ? parsedResponse.affectedBoundedContext() : null, "Nie ustalono"),
                firstText(parsedResponse != null ? parsedResponse.affectedTeam() : null, "Nie ustalono"),
                firstText(
                        parsedResponse != null ? parsedResponse.functionalAnalysis() : null,
                        firstText(assistantContent, DEFAULT_FUNCTIONAL_ANALYSIS)
                ),
                firstText(parsedResponse != null ? parsedResponse.technicalAnalysis() : null, DEFAULT_TECHNICAL_ANALYSIS),
                firstText(parsedResponse != null ? parsedResponse.confidence() : null, "low"),
                parsedResponse != null && !parsedResponse.visibilityLimits().isEmpty()
                        ? parsedResponse.visibilityLimits()
                        : List.of("Odpowiedz AI nie spelnila pelnego kontraktu JSON nowego wyniku analizy.")
        );
    }

    private StructuredAnalysisResponse normalizeResponse(StructuredAnalysisResponse response) {
        return new StructuredAnalysisResponse(
                response.detectedProblem(),
                firstText(response.affectedProcess(), "Nie ustalono"),
                firstText(response.affectedBoundedContext(), "Nie ustalono"),
                firstText(response.affectedTeam(), "Nie ustalono"),
                response.functionalAnalysis(),
                response.technicalAnalysis(),
                firstText(response.confidence(), "low"),
                response.visibilityLimits()
        );
    }

    private boolean hasRequiredFields(StructuredAnalysisResponse response) {
        return response != null
                && StringUtils.hasText(response.detectedProblem())
                && StringUtils.hasText(response.functionalAnalysis())
                && StringUtils.hasText(response.technicalAnalysis());
    }

    private JsonNode parseJsonNode(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }

        try {
            try (var parser = objectMapper.createParser(candidate.trim())) {
                var node = objectMapper.readValue(parser, JsonNode.class);
                return node != null && node.isObject() && parser.nextToken() == null ? node : null;
            }
        }
        catch (IOException exception) {
            return null;
        }
    }

    private String extractFencedJson(String assistantContent) {
        if (!StringUtils.hasText(assistantContent)) {
            return null;
        }

        var matcher = FENCED_JSON_PATTERN.matcher(assistantContent);
        return matcher.find() ? matcher.group(1) : null;
    }

    private ParseResult resultFromEmbeddedJsonObject(String assistantContent) {
        if (!StringUtils.hasText(assistantContent)) {
            return null;
        }

        StructuredAnalysisResponse firstParsedResponse = null;
        List<String> firstParsedFields = List.of();

        for (var start = assistantContent.indexOf('{'); start >= 0; start = assistantContent.indexOf('{', start + 1)) {
            var end = findJsonObjectEnd(assistantContent, start);
            if (end < 0) {
                continue;
            }

            var candidate = assistantContent.substring(start, end + 1);
            var node = parseJsonNode(candidate);
            if (node == null) {
                continue;
            }

            var response = responseFromJson(node);
            var parsedFields = parsedJsonFields(node);
            if (hasRequiredFields(response)) {
                return resultFromResponse(response, assistantContent, parsedFields);
            }

            if (firstParsedResponse == null) {
                firstParsedResponse = response;
                firstParsedFields = parsedFields;
            }
        }

        return firstParsedResponse != null
                ? resultFromResponse(firstParsedResponse, assistantContent, firstParsedFields)
                : null;
    }

    private int findJsonObjectEnd(String content, int start) {
        var depth = 0;
        var inString = false;
        var escaped = false;

        for (var index = start; index < content.length(); index++) {
            var character = content.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                }
                else if (character == '\\') {
                    escaped = true;
                }
                else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            if (character == '"') {
                inString = true;
            }
            else if (character == '{') {
                depth++;
            }
            else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    private StructuredAnalysisResponse responseFromJson(JsonNode node) {
        return new StructuredAnalysisResponse(
                text(node, "detectedProblem"),
                text(node, "affectedProcess"),
                text(node, "affectedBoundedContext"),
                text(node, "affectedTeam"),
                text(node, "functionalAnalysis"),
                text(node, "technicalAnalysis"),
                text(node, "confidence"),
                visibilityLimits(node.get("visibilityLimits"))
        );
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }

        var field = node.get(fieldName);
        if (field.isValueNode()) {
            var value = field.asText();
            return StringUtils.hasText(value) ? value.trim() : null;
        }

        return field.toString();
    }

    private List<String> visibilityLimits(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        var limits = new ArrayList<String>();
        for (var item : node) {
            if (item.isValueNode() && StringUtils.hasText(item.asText())) {
                limits.add(item.asText().trim());
            }
            else if (item.isObject()) {
                var description = text(item, "description");
                if (description != null) {
                    limits.add(description);
                }
            }
        }

        return List.copyOf(limits);
    }

    private List<String> parsedJsonFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return List.of();
        }

        var fields = new ArrayList<String>();
        node.fieldNames().forEachRemaining(fields::add);
        return List.copyOf(fields);
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
