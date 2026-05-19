package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityListener;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;
import pl.mkn.incidenttracker.shared.evidence.AnalysisAiToolEvidenceListener;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response.CopilotResponseDtos.StructuredAnalysisResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotInitialAnalysisProvider implements InitialAnalysisProvider {

    private final CopilotIncidentInitialPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final CopilotResponseParser responseParser;

    @Override
    public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        return preparationService.prepare(request);
    }

    @Override
    public InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyzePrepared(preparedAnalysis, toolEvidenceListener, AnalysisAiActivityListener.NO_OP);
    }

    @Override
    public InitialAnalysisResponse analyze(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        return analyzePrepared(preparedAnalysis, toolEvidenceListener, activityListener);
    }

    private InitialAnalysisResponse analyzePrepared(
            InitialAnalysisPreparation preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
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
        var executionResult = executionGateway.execute(
                withRuntimeSinks(preparedSession, toolEvidenceListener, activityListener)
        );
        var parseResult = responseParser.parse(executionResult.content());
        var response = toAiResponse(
                parseResult.response(),
                preparedSession.prompt(),
                executionResult.usage()
        );

        log.info(
                "Copilot response parse correlationId={} parsedKeys={} structuredResponse={} fallbackResponseUsed={}",
                request.correlationId(),
                parseResult.parsedFields(),
                parseResult.structuredResponse(),
                parseResult.fallbackResponseUsed()
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

    private CopilotPreparedSession withRuntimeSinks(
            CopilotPreparedSession preparedSession,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var session = preparedSession;
        if (toolEvidenceListener != null && toolEvidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
            session = session.withEvidenceSink(toolEvidenceListener::onToolEvidenceUpdated);
        }
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            session = session.withActivitySink(activityListener::onAiActivity);
        }
        return session;
    }

    private InitialAnalysisResponse toAiResponse(
            StructuredAnalysisResponse structuredResponse,
            String prompt,
            AnalysisAiUsage usage
    ) {
        return new InitialAnalysisResponse(
                "copilot-sdk",
                structuredResponse.detectedProblem(),
                structuredResponse.affectedProcess(),
                structuredResponse.affectedBoundedContext(),
                structuredResponse.affectedTeam(),
                structuredResponse.functionalAnalysis(),
                structuredResponse.technicalAnalysis(),
                structuredResponse.confidence(),
                structuredResponse.visibilityLimits(),
                prompt,
                usage
        );
    }
}
