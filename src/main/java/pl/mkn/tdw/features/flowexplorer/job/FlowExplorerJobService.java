package pl.mkn.tdw.features.flowexplorer.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextRequest;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextService;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerChatMessageRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
import pl.mkn.tdw.features.flowexplorer.job.localworkspace.FlowExplorerLocalRunPersistence;
import pl.mkn.tdw.features.flowexplorer.job.state.FlowExplorerJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

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
    private final FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService;
    private final FlowExplorerCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final FlowExplorerAiResponseParser responseParser;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final FlowExplorerLocalRunPersistence localRunPersistence;

    public FlowExplorerJobService(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            TaskExecutor applicationTaskExecutor
    ) {
        this(
                flowExplorerContextService,
                promptPreparationService,
                followUpPromptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
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
            FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService,
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
                followUpPromptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
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
            FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            TaskExecutor applicationTaskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotRunAuthMapper runAuthMapper,
            CopilotAccessTokenResolver accessTokenResolver,
            FlowExplorerLocalRunPersistence localRunPersistence
    ) {
        this.flowExplorerContextService = flowExplorerContextService;
        this.promptPreparationService = promptPreparationService;
        this.followUpPromptPreparationService = followUpPromptPreparationService;
        this.runRequestAssembler = runRequestAssembler;
        this.runPreparationService = runPreparationService;
        this.executionGateway = executionGateway;
        this.responseParser = responseParser;
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
            var promptPreparation = new FlowExplorerPromptPreparation(
                    chatRequest.message() != null ? chatRequest.message().trim() : "",
                    List.of(),
                    Map.of()
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
            job.markChatCompleted(
                    assistantMessageId,
                    executionResult.content(),
                    promptPreparation.prompt(),
                    executionResult.sessionId()
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
