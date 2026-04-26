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
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityGate;
import pl.mkn.incidenttracker.analysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.analysis.ai.copilot.response.CopilotStructuredAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkAnalysisAiProvider implements AnalysisAiProvider {

    private final CopilotSdkPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final CopilotResponseParser responseParser;
    private final CopilotResponseQualityGate qualityGate;
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
            var parseResult = responseParser.parse(assistantContent);
            var qualityReport = qualityGate.evaluate(request, parseResult.response());
            var response = toAiResponse(parseResult.response(), preparedRequest.prompt());
            metricsRegistry.recordResponse(
                    copilotSessionId,
                    parseResult.structuredResponse(),
                    parseResult.fallbackResponseUsed(),
                    response.detectedProblem(),
                    parseResult.response().confidence()
            );
            metricsRegistry.recordQualityReport(copilotSessionId, qualityReport);
            metricsLogger.logQualityReport(request.correlationId(), qualityReport);
            metricsRegistry.remove(copilotSessionId).ifPresent(metricsLogger::logSummary);

            log.info(
                    "Copilot response parse correlationId={} parsedKeys={} structuredResponse={} fallbackResponseUsed={} qualityPassed={} qualityFindingCount={}",
                    request.correlationId(),
                    parseResult.parsedFields(),
                    parseResult.structuredResponse(),
                    parseResult.fallbackResponseUsed(),
                    qualityReport.passed(),
                    qualityReport.findingCount()
            );

            log.info(
                    "Copilot analysis completed correlationId={} durationMs={} detectedProblem={} structuredResponse={}",
                    request.correlationId(),
                    (System.nanoTime() - analysisStart) / 1_000_000,
                    response.detectedProblem(),
                    parseResult.structuredResponse()
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

    private AnalysisAiAnalysisResponse toAiResponse(
            CopilotStructuredAnalysisResponse structuredResponse,
            String prompt
    ) {
        return new AnalysisAiAnalysisResponse(
                "copilot-sdk",
                structuredResponse.summary(),
                structuredResponse.detectedProblem(),
                structuredResponse.recommendedAction(),
                structuredResponse.rationale(),
                structuredResponse.affectedFunction(),
                structuredResponse.affectedProcess(),
                structuredResponse.affectedBoundedContext(),
                structuredResponse.affectedTeam(),
                prompt
        );
    }

}
