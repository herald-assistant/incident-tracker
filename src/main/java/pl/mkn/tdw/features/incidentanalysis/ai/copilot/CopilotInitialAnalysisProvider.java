package pl.mkn.tdw.features.incidentanalysis.ai.copilot;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotInitialAnalysisPreparation;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.report.CopilotIncidentReportMapper;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.response.CopilotResponseParser;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.response.CopilotResponseDtos.StructuredAnalysisResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotInitialAnalysisProvider implements InitialAnalysisProvider {

    private final CopilotIncidentInitialPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final CopilotResponseParser responseParser;
    private final CopilotIncidentReportMapper reportMapper;

    public CopilotInitialAnalysisProvider(
            CopilotIncidentInitialPreparationService preparationService,
            CopilotSdkExecutionGateway executionGateway,
            CopilotResponseParser responseParser
    ) {
        this(
                preparationService,
                executionGateway,
                responseParser,
                new CopilotIncidentReportMapper()
        );
    }

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
        var reportResponse = reportMapper.tryMap(
                executionResult.report(),
                preparedSession.prompt(),
                executionResult.usage(),
                executionResult.sessionId()
        );
        var reportResponseUsed = reportResponse.isPresent();
        InitialAnalysisResponse response;
        boolean structuredResponse;
        if (reportResponseUsed) {
            response = reportResponse.get();
            structuredResponse = true;
        }
        else {
            var parseResult = responseParser.parse(executionResult.content());
            log.info(
                    "Copilot response parse correlationId={} parsedKeys={} structuredResponse={} fallbackResponseUsed={}",
                    request.correlationId(),
                    parseResult.parsedFields(),
                    parseResult.structuredResponse(),
                    parseResult.fallbackResponseUsed()
            );
            response = toAiResponse(
                    parseResult.response(),
                    preparedSession.prompt(),
                    executionResult.usage(),
                    executionResult.sessionId(),
                    executionResult.report()
            );
            structuredResponse = parseResult.structuredResponse();
        }

        log.info(
                "Copilot report mapping correlationId={} reportPresent={} reportResponseUsed={}",
                request.correlationId(),
                executionResult.report() != null,
                reportResponseUsed
        );

        log.info(
                "Copilot analysis completed correlationId={} durationMs={} detectedProblem={} structuredResponse={}",
                request.correlationId(),
                (System.nanoTime() - analysisStart) / 1_000_000,
                response.detectedProblem(),
                structuredResponse
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
            AnalysisAiUsage usage,
            String copilotSessionId,
            AnalysisReport report
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
                usage,
                copilotSessionId,
                report
        );
    }
}
