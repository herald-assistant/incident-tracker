package pl.mkn.tdw.features.flowexplorer.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerResultUpdateApplicator;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextRequest;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextService;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerChatMessageRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultUpdateDecisionRequest;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobResultUpdateUnavailableException;
import pl.mkn.tdw.features.flowexplorer.job.localworkspace.FlowExplorerLocalRunPersistence;
import pl.mkn.tdw.features.flowexplorer.job.state.FlowExplorerJobState;
import pl.mkn.tdw.features.flowexplorer.job.state.FlowExplorerResultUpdateDecision;
import pl.mkn.tdw.features.flowexplorer.job.state.FlowExplorerResultUpdateDecisionContext;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FlowExplorerJobService {

    private final Map<String, FlowExplorerJobState> jobs = new ConcurrentHashMap<>();
    private final FlowExplorerContextService flowExplorerContextService;
    private final FlowExplorerPromptPreparationService promptPreparationService;
    private final FlowExplorerCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final FlowExplorerAiResponseParser responseParser;
    private final FlowExplorerFollowUpChatResponseParser followUpResponseParser;
    private final FlowExplorerResultUpdateApplicator resultUpdateApplicator;
    private final ObjectMapper objectMapper;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final FlowExplorerLocalRunPersistence localRunPersistence;

    public FlowExplorerJobService(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            TaskExecutor applicationTaskExecutor
    ) {
        this(
                flowExplorerContextService,
                promptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                new FlowExplorerFollowUpChatResponseParser(new ObjectMapper()),
                new FlowExplorerResultUpdateApplicator(),
                new ObjectMapper(),
                applicationTaskExecutor,
                () -> AnalysisAiAuthRef.localToken(null),
                new CopilotRunAuthMapper(),
                auth -> new pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken(
                        "test-token",
                        null,
                        null,
                        false
                ),
                FlowExplorerLocalRunPersistence.NO_OP
        );
    }

    public FlowExplorerJobService(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            TaskExecutor applicationTaskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotRunAuthMapper runAuthMapper,
            CopilotAccessTokenResolver accessTokenResolver
    ) {
        this(
                flowExplorerContextService,
                promptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                new FlowExplorerFollowUpChatResponseParser(new ObjectMapper()),
                new FlowExplorerResultUpdateApplicator(),
                new ObjectMapper(),
                applicationTaskExecutor,
                authRefResolver,
                runAuthMapper,
                accessTokenResolver,
                FlowExplorerLocalRunPersistence.NO_OP
        );
    }

    @Autowired
    public FlowExplorerJobService(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            FlowExplorerFollowUpChatResponseParser followUpResponseParser,
            FlowExplorerResultUpdateApplicator resultUpdateApplicator,
            ObjectMapper objectMapper,
            TaskExecutor applicationTaskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotRunAuthMapper runAuthMapper,
            CopilotAccessTokenResolver accessTokenResolver,
            FlowExplorerLocalRunPersistence localRunPersistence
    ) {
        this.flowExplorerContextService = flowExplorerContextService;
        this.promptPreparationService = promptPreparationService;
        this.runRequestAssembler = runRequestAssembler;
        this.runPreparationService = runPreparationService;
        this.executionGateway = executionGateway;
        this.responseParser = responseParser;
        this.followUpResponseParser = followUpResponseParser;
        this.resultUpdateApplicator = resultUpdateApplicator;
        this.objectMapper = objectMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.authRefResolver = authRefResolver;
        this.runAuthMapper = runAuthMapper;
        this.accessTokenResolver = accessTokenResolver;
        this.localRunPersistence = localRunPersistence;
    }

    public FlowExplorerJobStateSnapshot startJob(FlowExplorerJobStartRequest request) {
        var authRef = authRefResolver.resolveForCurrentRequest();
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));
        var jobId = UUID.randomUUID().toString();
        var job = new FlowExplorerJobState(jobId, request, authRef);
        job.markContextStarted();
        jobs.put(jobId, job);
        applicationTaskExecutor.execute(() -> runJob(jobId, job, request, authRef));

        return job.snapshot();
    }

    private void runJob(
            String jobId,
            FlowExplorerJobState job,
            FlowExplorerJobStartRequest request,
            AnalysisAiAuthRef authRef
    ) {
        try {
            var contextSnapshot = flowExplorerContextService.buildContext(new FlowExplorerContextRequest(
                    request.systemId(),
                    request.endpointId(),
                    request.httpMethod(),
                    request.endpointPath(),
                    request.branch(),
                    request.goal(),
                    request.focusAreas()
            ));
            var promptPreparation = promptPreparationService.prepare(request, contextSnapshot);
            job.markAiStarted(contextSnapshot, promptPreparation.prompt());

            var runAssembly = runRequestAssembler.assemble(
                    jobId,
                    request,
                    contextSnapshot,
                    promptPreparation,
                    authRef
            );
            var preparedSession = runPreparationService.prepare(runAssembly.runRequest())
                    .withEvidenceSink(job::markAiToolEvidenceUpdated)
                    .withActivitySink(job::markAiActivity);
            var executionResult = executionGateway.execute(preparedSession);
            var aiResponse = responseParser.parse(
                    executionResult.content(),
                    request.goal(),
                    request.resolvedSectionModes()
            );

            job.markAiCompleted(aiResponse, executionResult.usage(), promptPreparation.prompt(), executionResult.sessionId());
            persistCompletedInitialRun(job, authRef, executionResult.sessionId());
        } catch (RuntimeException exception) {
            log.error(
                    "Flow Explorer job failed jobId={} systemId={} endpointId={} message={}",
                    jobId,
                    request.systemId(),
                    request.endpointId(),
                    exception.getMessage(),
                    exception
            );
            job.markFailed(
                    "FLOW_EXPLORER_AI_FAILED",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Unexpected Flow Explorer analysis failure."
            );
        }
    }

    public FlowExplorerJobStateSnapshot getJob(String jobId) {
        return jobOrThrow(jobId).snapshot();
    }

    public FlowExplorerJobStateSnapshot startChatMessage(String jobId, FlowExplorerChatMessageRequest request) {
        var job = jobOrThrow(jobId);
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(job.authRefForChat()));
        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        var chatRequest = job.startChatMessage(userMessageId, assistantMessageId, request.message());

        applicationTaskExecutor.execute(() -> runChat(job, assistantMessageId, chatRequest));

        return job.snapshot();
    }

    private void runChat(
            FlowExplorerJobState job,
            String assistantMessageId,
            FlowExplorerFollowUpChatRequest chatRequest
    ) {
        try {
            var visiblePrompt = chatRequest.message() != null ? chatRequest.message().trim() : "";
            var promptPreparation = promptPreparationService.prepareFollowUp(
                    chatRequest.initialRequest(),
                    visiblePrompt
            );
            var runAssembly = runRequestAssembler.assembleFollowUp(
                    "flow-explorer-follow-up-" + assistantMessageId,
                    chatRequest.initialRequest(),
                    chatRequest.contextSnapshot(),
                    promptPreparation,
                    chatRequest.copilotSessionId(),
                    chatRequest.authRef()
            );
            var preparedSession = runPreparationService.prepare(runAssembly.runRequest())
                    .withEvidenceSink(section -> job.markChatToolEvidenceUpdated(assistantMessageId, section))
                    .withActivitySink(event -> job.markChatAiActivity(assistantMessageId, event));
            var executionResult = executionGateway.execute(preparedSession);
            var parsedResponse = followUpResponseParser.parse(executionResult.content());
            var resultUpdate = parsedResponse.hasResultUpdate()
                    ? objectMapper.valueToTree(resultUpdateApplicator.apply(
                            job.currentAiResponse(),
                            parsedResponse.resultUpdate()
                    ))
                    : null;
            job.markChatCompleted(
                    assistantMessageId,
                    parsedResponse.message(),
                    visiblePrompt,
                    executionResult.sessionId(),
                    resultUpdate
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Flow Explorer follow-up chat failed systemId={} endpointId={} message={}",
                    chatRequest.initialRequest().systemId(),
                    chatRequest.initialRequest().endpointId(),
                    exception.getMessage(),
                    exception
            );
            job.markChatFailed(
                    assistantMessageId,
                    "FLOW_EXPLORER_CHAT_FAILED",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Unexpected Flow Explorer follow-up chat failure."
            );
        }
    }

    public FlowExplorerJobStateSnapshot applyResultUpdate(
            String jobId,
            String messageId,
            FlowExplorerResultUpdateDecisionRequest request
    ) {
        return decideResultUpdate(jobId, messageId, request, FlowExplorerResultUpdateDecision.APPLY);
    }

    public FlowExplorerJobStateSnapshot rejectResultUpdate(
            String jobId,
            String messageId,
            FlowExplorerResultUpdateDecisionRequest request
    ) {
        return decideResultUpdate(jobId, messageId, request, FlowExplorerResultUpdateDecision.REJECT);
    }

    private FlowExplorerJobStateSnapshot decideResultUpdate(
            String jobId,
            String messageId,
            FlowExplorerResultUpdateDecisionRequest request,
            FlowExplorerResultUpdateDecision decision
    ) {
        var job = jobOrThrow(jobId);
        var aiResponse = request != null ? request.aiResponse() : null;
        var decisionContext = job.startResultUpdateDecision(messageId, decision, aiResponse);
        try {
            accessTokenResolver.resolve(runAuthMapper.toRunAuth(decisionContext.authRef()));
            var syncResult = executeResultUpdateSync(decisionContext);
            job.markResultUpdateDecisionCompleted(
                    messageId,
                    decisionContext.authoritativeResult(),
                    syncResult.sessionId()
            );
            return job.snapshot();
        } catch (CopilotLocalTokenMissingException
                 | GitHubCopilotAuthRequiredException
                 | GitHubCopilotReauthRequiredException
                 | UserFacingApplicationException exception) {
            job.markResultUpdateDecisionFailed(messageId);
            throw exception;
        } catch (RuntimeException exception) {
            job.markResultUpdateDecisionFailed(messageId);
            throw new FlowExplorerJobResultUpdateUnavailableException(
                    "FLOW_EXPLORER_RESULT_UPDATE_SYNC_FAILED",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Flow Explorer result update session sync failed."
            );
        }
    }

    private pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult executeResultUpdateSync(
            FlowExplorerResultUpdateDecisionContext decisionContext
    ) {
        var promptPreparation = new FlowExplorerPromptPreparation(
                resultUpdateSyncPrompt(decisionContext),
                List.of(),
                Map.of()
        );
        var runAssembly = runRequestAssembler.assembleFollowUp(
                "flow-explorer-result-update-" + decisionContext.decision().name().toLowerCase() + "-"
                        + decisionContext.assistantMessageId(),
                decisionContext.initialRequest(),
                decisionContext.contextSnapshot(),
                promptPreparation,
                decisionContext.copilotSessionId(),
                decisionContext.authRef()
        );
        var preparedSession = runPreparationService.prepare(runAssembly.runRequest());
        var executionResult = executionGateway.execute(preparedSession);
        if (!"OK".equalsIgnoreCase(executionResult.content() != null ? executionResult.content().trim() : "")) {
            throw new IllegalStateException("Flow Explorer result update session sync did not return OK.");
        }
        return executionResult;
    }

    private String resultUpdateSyncPrompt(FlowExplorerResultUpdateDecisionContext decisionContext) {
        var decisionText = switch (decisionContext.decision()) {
            case APPLY -> "operator zaakceptowal resultUpdate";
            case REJECT -> "operator odrzucil resultUpdate";
        };
        return """
                Techniczna wiadomosc synchronizacyjna Flow Explorer. Nie pokazuj jej uzytkownikowi jako tresci merytorycznej.

                Decyzja operatora: %s z assistant message id `%s`.
                Ponizszy `FlowExplorerAiResponse` jest teraz authoritative state aplikacji.

                Authoritative result JSON:
                %s

                Odpowiedz dokladnie jednym slowem:
                OK
                """.formatted(
                decisionText,
                decisionContext.assistantMessageId(),
                resultUpdateJson(decisionContext)
        );
    }

    private String resultUpdateJson(FlowExplorerResultUpdateDecisionContext decisionContext) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(decisionContext.authoritativeResult());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Flow Explorer result update cannot be serialized.", exception);
        }
    }

    private void persistCompletedInitialRun(
            FlowExplorerJobState job,
            AnalysisAiAuthRef authRef,
            String copilotSessionId
    ) {
        var snapshot = job.snapshot();
        try {
            localRunPersistence.persistCompletedInitialRun(snapshot, authRef, copilotSessionId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist completed local Flow Explorer run jobId={} systemId={} endpointId={} reason={}",
                    snapshot.jobId(),
                    snapshot.systemId(),
                    snapshot.endpointId(),
                    exception.getMessage()
            );
        }
    }

    private FlowExplorerJobState jobOrThrow(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new FlowExplorerJobNotFoundException(jobId);
        }
        return job;
    }
}
