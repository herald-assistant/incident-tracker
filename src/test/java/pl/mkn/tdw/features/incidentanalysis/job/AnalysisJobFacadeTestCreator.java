package pl.mkn.tdw.features.incidentanalysis.job;

import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisOrchestrator;
import pl.mkn.tdw.features.incidentanalysis.job.localworkspace.IncidentAnalysisLocalRunPersistence;
import pl.mkn.tdw.features.incidentanalysis.job.validation.AnalysisJobStartValidationService;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogCsvImportService;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

final class AnalysisJobFacadeTestCreator {

    private AnalysisJobFacadeTestCreator() {
    }

    static AnalysisJobFacade create(
            AnalysisOrchestrator analysisOrchestrator,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor applicationTaskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotAccessTokenResolver accessTokenResolver,
            IncidentAnalysisLocalRunPersistence localRunPersistence,
            AnalysisJobInputOptionsService inputOptionsService
    ) {
        return new AnalysisJobFacade(
                analysisOrchestrator,
                analysisAiChatProvider,
                applicationTaskExecutor,
                authRefResolver,
                new CopilotRunAuthMapper(),
                accessTokenResolver,
                localRunPersistence,
                inputOptionsService,
                new AnalysisJobStartValidationService(inputOptionsService, new ElasticLogCsvImportService())
        );
    }
}
