package pl.mkn.tdw.features.flowexplorer.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextRequest;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextService;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerChatMessageRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
import pl.mkn.tdw.features.flowexplorer.job.state.FlowExplorerJobState;

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
        this.flowExplorerContextService = flowExplorerContextService;
        this.promptPreparationService = promptPreparationService;
        this.followUpPromptPreparationService = followUpPromptPreparationService;
        this.runRequestAssembler = runRequestAssembler;
        this.runPreparationService = runPreparationService;
        this.executionGateway = executionGateway;
        this.responseParser = responseParser;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    public FlowExplorerJobStateSnapshot startJob(FlowExplorerJobStartRequest request) {
        var jobId = UUID.randomUUID().toString();
        var job = new FlowExplorerJobState(jobId, request);
        job.markContextStarted();
        jobs.put(jobId, job);
        applicationTaskExecutor.execute(() -> runJob(jobId, job, request));

        return job.snapshot();
    }

    private void runJob(String jobId, FlowExplorerJobState job, FlowExplorerJobStartRequest request) {
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
                    promptPreparation
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

            job.markAiCompleted(aiResponse, executionResult.usage(), promptPreparation.prompt());
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
            var promptPreparation = followUpPromptPreparationService.prepare(chatRequest);
            var runAssembly = runRequestAssembler.assembleFollowUp(
                    "flow-explorer-follow-up-" + assistantMessageId,
                    chatRequest.initialRequest(),
                    chatRequest.contextSnapshot(),
                    promptPreparation
            );
            var preparedSession = runPreparationService.prepare(runAssembly.runRequest())
                    .withEvidenceSink(section -> job.markChatToolEvidenceUpdated(assistantMessageId, section))
                    .withActivitySink(event -> job.markChatAiActivity(assistantMessageId, event));
            var executionResult = executionGateway.execute(preparedSession);
            job.markChatCompleted(assistantMessageId, executionResult.content(), promptPreparation.prompt());
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

    private FlowExplorerJobState jobOrThrow(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new FlowExplorerJobNotFoundException(jobId);
        }
        return job;
    }
}
