package pl.mkn.incidenttracker.analysis.ai.copilot;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkAnalysisAiProvider implements AnalysisAiProvider {

    private static final List<String> EXPECTED_FIELD_LABELS = List.of(
            "detectedProblem",
            "summary",
            "recommendedAction",
            "rationale",
            "affectedFunction",
            "affectedProcess",
            "affectedBoundedContext",
            "affectedTeam"
    );

    private final CopilotSdkPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final CopilotSessionMetricsRegistry metricsRegistry;
    private final CopilotMetricsLogger metricsLogger;

    @Override
    public String preparePrompt(AnalysisAiAnalysisRequest request) {
        return preparationService.preparePrompt(request);
    }

    @Override
    public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
        return analyzeWithExecution(request, executionGateway::execute);
    }

    @Override
    public AnalysisAiAnalysisResponse analyze(
            AnalysisAiAnalysisRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyzeWithExecution(
                request,
                preparedRequest -> executionGateway.execute(preparedRequest, toolEvidenceListener)
        );
    }

    private AnalysisAiAnalysisResponse analyzeWithExecution(
            AnalysisAiAnalysisRequest request,
            Function<CopilotSdkPreparedRequest, String> execution
    ) {
        var analysisStart = System.nanoTime();
        var preparedRequest = preparationService.prepare(request);
        var copilotSessionId = copilotSessionId(preparedRequest);
        try {
            var assistantContent = execution.apply(preparedRequest);
            var mappedContent = mapAssistantContent(
                    request.correlationId(),
                    assistantContent,
                    preparedRequest.prompt()
            );
            var response = mappedContent.response();
            metricsRegistry.recordResponse(
                    copilotSessionId,
                    mappedContent.structuredResponse(),
                    mappedContent.legacyParserUsed(),
                    mappedContent.fallbackResponseUsed(),
                    response.detectedProblem(),
                    null
            );
            metricsRegistry.remove(copilotSessionId).ifPresent(metricsLogger::logSummary);

            log.info(
                    "Copilot analysis completed correlationId={} durationMs={} detectedProblem={} structuredResponse={}",
                    request.correlationId(),
                    (System.nanoTime() - analysisStart) / 1_000_000,
                    response.detectedProblem(),
                    mappedContent.structuredResponse()
            );

            return response;
        }
        catch (RuntimeException exception) {
            metricsRegistry.remove(copilotSessionId).ifPresent(metricsLogger::logSummary);
            throw exception;
        }
    }

    private String copilotSessionId(CopilotSdkPreparedRequest preparedRequest) {
        if (preparedRequest == null || preparedRequest.sessionConfig() == null) {
            return null;
        }

        return preparedRequest.sessionConfig().getSessionId();
    }

    private MappedAssistantContent mapAssistantContent(
            String correlationId,
            String assistantContent,
            String prompt
    ) {
        var fields = parseLabeledFields(assistantContent);
        var detectedProblem = fields.get("detectedproblem");
        var summary = fields.get("summary");
        var recommendedAction = fields.get("recommendedaction");
        var rationale = fields.get("rationale");
        var affectedFunction = fields.get("affectedfunction");
        var affectedProcess = fields.get("affectedprocess");
        var affectedBoundedContext = fields.get("affectedboundedcontext");
        var affectedTeam = fields.get("affectedteam");

        log.info(
                "Copilot response parse correlationId={} parsedKeys={} structuredResponse={} affectedFunctionPresent={} affectedProcessPresent={} affectedBoundedContextPresent={} affectedTeamPresent={}",
                correlationId,
                fields.keySet(),
                detectedProblem != null && summary != null && recommendedAction != null && affectedFunction != null,
                affectedFunction != null,
                affectedProcess != null,
                affectedBoundedContext != null,
                affectedTeam != null
        );

        var structuredResponse = detectedProblem != null
                && summary != null
                && recommendedAction != null
                && affectedFunction != null;

        if (!structuredResponse) {
            return new MappedAssistantContent(new AnalysisAiAnalysisResponse(
                    "copilot-sdk",
                    assistantContent,
                    "AI_UNSTRUCTURED_RESPONSE",
                    "Review the raw Copilot response and improve response formatting in the prompt.",
                    "Generated by GitHub Copilot SDK from the prepared diagnostic evidence.",
                    "",
                    "",
                    "",
                    "",
                    prompt
            ), false, true, true);
        }

        return new MappedAssistantContent(new AnalysisAiAnalysisResponse(
                "copilot-sdk",
                summary,
                detectedProblem,
                recommendedAction,
                rationale != null
                        ? rationale
                        : "Generated by GitHub Copilot SDK from the prepared diagnostic evidence.",
                affectedFunction,
                affectedProcess != null ? affectedProcess : "",
                affectedBoundedContext != null ? affectedBoundedContext : "",
                affectedTeam != null ? affectedTeam : "",
                prompt
        ), true, true, false);
    }

    private Map<String, String> parseLabeledFields(String assistantContent) {
        var fields = new HashMap<String, String>();
        String currentKey = null;
        StringBuilder currentValue = null;

        for (var line : assistantContent.split("\\R")) {
            var fieldDeclaration = parseFieldDeclaration(line);
            if (fieldDeclaration != null) {
                storeCurrentField(fields, currentKey, currentValue);
                currentKey = fieldDeclaration.key();
                currentValue = new StringBuilder(fieldDeclaration.initialValue());
                continue;
            }

            if (currentKey == null || currentValue == null) {
                continue;
            }

            var trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                if (currentValue.length() > 0 && !endsWithNewline(currentValue)) {
                    currentValue.append("\n\n");
                }
                continue;
            }

            if (currentValue.length() > 0 && !endsWithNewline(currentValue)) {
                currentValue.append('\n');
            }
            currentValue.append(trimmedLine);
        }

        storeCurrentField(fields, currentKey, currentValue);
        return fields;
    }

    private void storeCurrentField(Map<String, String> fields, String key, StringBuilder value) {
        if (key == null || value == null) {
            return;
        }

        var normalizedValue = value.toString().trim();
        if (!normalizedValue.isEmpty()) {
            fields.put(key, normalizeStructuredFieldValue(key, normalizedValue));
        }
    }

    private FieldDeclaration parseFieldDeclaration(String rawLine) {
        var trimmedLine = rawLine.trim();
        if (trimmedLine.isEmpty()) {
            return null;
        }

        var normalizedLine = trimmedLine
                .replaceFirst("^[-+*]\\s+", "")
                .replaceFirst("^>\\s+", "")
                .replaceFirst("^\\d+[.)]\\s+", "");
        for (var label : EXPECTED_FIELD_LABELS) {
            var initialValue = extractFieldValue(normalizedLine, label);
            if (initialValue != null) {
                return new FieldDeclaration(normalizeFieldKey(label), initialValue.trim());
            }
        }

        return null;
    }

    private String extractFieldValue(String line, String fieldLabel) {
        var lowerLine = line.toLowerCase(Locale.ROOT);
        var lowerFieldLabel = fieldLabel.toLowerCase(Locale.ROOT);

        if (lowerLine.startsWith(lowerFieldLabel + ":")) {
            return line.substring(fieldLabel.length() + 1);
        }

        for (var wrapper : List.of("**", "__", "`")) {
            var wrappedColonInside = wrapper + lowerFieldLabel + ":" + wrapper;
            if (lowerLine.startsWith(wrappedColonInside)) {
                return line.substring(wrapper.length() + fieldLabel.length() + 1 + wrapper.length());
            }

            var wrappedColonOutside = wrapper + lowerFieldLabel + wrapper + ":";
            if (lowerLine.startsWith(wrappedColonOutside)) {
                return line.substring(wrapper.length() + fieldLabel.length() + wrapper.length() + 1);
            }
        }

        return null;
    }

    private String normalizeFieldKey(String rawKey) {
        return rawKey.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[\\-*+>#\\d.()\\s`_]+", "")
                .replaceAll("[*_`\\s-]+", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private String normalizeStructuredFieldValue(String key, String rawValue) {
        var normalized = rawValue.trim();

        if ("summary".equals(key)
                || "affectedfunction".equals(key)
                || "affectedprocess".equals(key)
                || "affectedboundedcontext".equals(key)
                || "affectedteam".equals(key)) {
            return normalizeLegacyPipeBullets(normalized, false);
        }

        if ("recommendedaction".equals(key) || "rationale".equals(key)) {
            return normalizeLegacyPipeBullets(normalized, true);
        }

        return normalized;
    }

    private String normalizeLegacyPipeBullets(String value, boolean bulletizeFirstSegment) {
        if (!value.contains("| -")) {
            return value;
        }

        var normalized = value.replaceAll("\\s*\\|\\s+-\\s+", "\n- ");
        if (bulletizeFirstSegment && !normalized.startsWith("- ")) {
            return "- " + normalized;
        }

        return normalized;
    }

    private boolean endsWithNewline(StringBuilder value) {
        var current = value.toString();
        return current.endsWith("\n") || current.endsWith("\n\n");
    }

    private record FieldDeclaration(String key, String initialValue) {
    }

    private record MappedAssistantContent(
            AnalysisAiAnalysisResponse response,
            boolean structuredResponse,
            boolean legacyParserUsed,
            boolean fallbackResponseUsed
    ) {
    }

}
