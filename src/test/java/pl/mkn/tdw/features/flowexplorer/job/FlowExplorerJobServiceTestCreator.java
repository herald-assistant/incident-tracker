package pl.mkn.tdw.features.flowexplorer.job;

import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportMapper;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextService;
import pl.mkn.tdw.features.flowexplorer.job.localworkspace.FlowExplorerLocalRunPersistence;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;

final class FlowExplorerJobServiceTestCreator {

    private FlowExplorerJobServiceTestCreator() {
    }

    static FlowExplorerJobService create(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            TaskExecutor applicationTaskExecutor
    ) {
        return create(
                flowExplorerContextService,
                promptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                applicationTaskExecutor,
                FlowExplorerLocalRunPersistence.NO_OP
        );
    }

    static FlowExplorerJobService create(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            TaskExecutor applicationTaskExecutor,
            FlowExplorerLocalRunPersistence localRunPersistence
    ) {
        return create(
                flowExplorerContextService,
                promptPreparationService,
                new FlowExplorerFollowUpPromptPreparationService(),
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                new FlowExplorerReportMapper(),
                applicationTaskExecutor,
                () -> AnalysisAiAuthRef.localToken(null),
                new CopilotRunAuthMapper(),
                auth -> new CopilotAccessToken("test-token", null, null, false),
                localRunPersistence
        );
    }

    static FlowExplorerJobService create(
            FlowExplorerContextService flowExplorerContextService,
            FlowExplorerPromptPreparationService promptPreparationService,
            FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService,
            FlowExplorerCopilotRunRequestAssembler runRequestAssembler,
            CopilotRunPreparationService runPreparationService,
            CopilotSdkExecutionGateway executionGateway,
            FlowExplorerAiResponseParser responseParser,
            FlowExplorerReportMapper reportMapper,
            TaskExecutor applicationTaskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotRunAuthMapper runAuthMapper,
            CopilotAccessTokenResolver accessTokenResolver,
            FlowExplorerLocalRunPersistence localRunPersistence
    ) {
        return new FlowExplorerJobService(
                flowExplorerContextService,
                promptPreparationService,
                followUpPromptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                reportMapper,
                applicationTaskExecutor,
                authRefResolver,
                runAuthMapper,
                accessTokenResolver,
                localRunPersistence
        );
    }
}
