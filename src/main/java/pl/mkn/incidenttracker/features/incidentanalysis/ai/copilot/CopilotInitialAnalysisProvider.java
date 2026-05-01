package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionTelemetry;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotUsage;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.quality.CopilotResponseQualityGate;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseDtos.StructuredAnalysisResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotInitialAnalysisProvider implements InitialAnalysisProvider {

    private final CopilotIncidentInitialPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final CopilotResponseParser responseParser;
    private final CopilotResponseQualityGate qualityGate;
    private final CopilotSessionTelemetry telemetry;

    @Override
    public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        return preparationService.prepare(request);
    }

    @Override
    public InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyzePrepared(preparedAnalysis, toolEvidenceListener);
    }

    private InitialAnalysisResponse analyzePrepared(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        if (!(preparedAnalysis instanceof CopilotInitialAnalysisPreparation initialPreparation)) {
            throw new IllegalArgumentException(
                    "Copilot initial analysis provider requires CopilotInitialAnalysisPreparation, got %s"
                            .formatted(preparedAnalysis != null ? preparedAnalysis.getClass().getName() : "null")
            );
        }

        var preparedSession = initialPreparation.session();
        var request = initialPreparation.request();
        var analysisStart = System.nanoTime();
        var copilotSessionId = copilotSessionId(preparedSession);
        try {
            var assistantContent = executionGateway.execute(
                    withToolEvidenceSink(preparedSession, toolEvidenceListener)
            );
            var parseResult = responseParser.parse(assistantContent);
            var qualityReport = qualityGate.evaluate(request, parseResult.response());
            var response = toAiResponse(parseResult.response(), preparedSession.prompt(), null);
            telemetry.recordResponse(
                    copilotSessionId,
                    parseResult.structuredResponse(),
                    parseResult.fallbackResponseUsed(),
                    response.detectedProblem(),
                    parseResult.response().confidence()
            );
            telemetry.recordQualityReport(request.correlationId(), copilotSessionId, qualityReport);
            var metrics = telemetry.completeSession(copilotSessionId);
            response = toAiResponse(
                    parseResult.response(),
                    preparedSession.prompt(),
                    metrics.map(snapshot -> toAnalysisAiUsage(snapshot.usage()))
                            .orElse(null)
            );

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
            telemetry.discardSession(copilotSessionId);
            throw exception;
        }
    }

    private String copilotSessionId(CopilotPreparedSession preparedSession) {
        if (preparedSession == null || preparedSession.sessionConfig() == null) {
            return null;
        }

        return preparedSession.sessionConfig().getSessionId();
    }

    private CopilotPreparedSession withToolEvidenceSink(
            CopilotPreparedSession preparedSession,
            AnalysisAiToolEvidenceListener listener
    ) {
        if (listener == null || listener == AnalysisAiToolEvidenceListener.NO_OP) {
            return preparedSession;
        }

        return preparedSession.withEvidenceSink(listener::onToolEvidenceUpdated);
    }

    private InitialAnalysisResponse toAiResponse(
            StructuredAnalysisResponse structuredResponse,
            String prompt,
            AnalysisAiUsage usage
    ) {
        return new InitialAnalysisResponse(
                "copilot-sdk",
                structuredResponse.summary(),
                structuredResponse.detectedProblem(),
                structuredResponse.recommendedAction(),
                structuredResponse.rationale(),
                structuredResponse.affectedFunction(),
                structuredResponse.affectedProcess(),
                structuredResponse.affectedBoundedContext(),
                structuredResponse.affectedTeam(),
                prompt,
                usage
        );
    }

    private AnalysisAiUsage toAnalysisAiUsage(CopilotUsage usage) {
        if (usage == null) {
            return null;
        }

        return new AnalysisAiUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.totalTokens(),
                usage.cost(),
                usage.apiDurationMs(),
                usage.apiCallCount(),
                usage.model(),
                usage.contextTokenLimit(),
                usage.contextCurrentTokens(),
                usage.contextMessages()
        );
    }
}
