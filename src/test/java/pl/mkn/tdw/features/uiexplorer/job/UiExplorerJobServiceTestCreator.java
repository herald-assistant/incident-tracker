package pl.mkn.tdw.features.uiexplorer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAnalysisProvider;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunPersistence;
import pl.mkn.tdw.features.uiexplorer.report.DefaultUiExplorerResultReportAssembler;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class UiExplorerJobServiceTestCreator {

    private UiExplorerJobServiceTestCreator() {
    }

    static UiExplorerJobService create(
            UiExplorerSourceContextService sourceContextService,
            UiExplorerAnalysisProvider analysisProvider,
            TaskExecutor taskExecutor
    ) {
        return create(sourceContextService, analysisProvider, taskExecutor, UiExplorerLocalRunPersistence.NO_OP);
    }

    static UiExplorerJobService create(
            UiExplorerSourceContextService sourceContextService,
            UiExplorerAnalysisProvider analysisProvider,
            TaskExecutor taskExecutor,
            UiExplorerLocalRunPersistence localRunPersistence
    ) {
        var artifactService = new UiExplorerArtifactService(new ObjectMapper());
        return new UiExplorerJobService(
                sourceContextService,
                new UiExplorerSourceContextEvidenceMapper(),
                new UiExplorerPromptPreparationService(
                        artifactService,
                        new CopilotArtifactContentMapper()
                ),
                analysisProvider,
                taskExecutor,
                () -> AnalysisAiAuthRef.localToken(null),
                localRunPersistence
        );
    }

    static UiExplorerSourceContextService sourceContextService(UiExplorerSourceContextSnapshot context) {
        var sourceContextService = mock(UiExplorerSourceContextService.class);
        when(sourceContextService.buildContext(
                eq("crm-agent-portal"),
                eq("main"),
                eq("crm-contact-preferences"),
                eq("crm-commit-abc123"),
                anyList()
        )).thenReturn(context);
        return sourceContextService;
    }

    static AnalysisReport report(String reportId, UiExplorerResultResponse result) {
        return new DefaultUiExplorerResultReportAssembler().assemble(reportId, result).report();
    }
}
