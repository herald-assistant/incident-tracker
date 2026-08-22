package pl.mkn.tdw.features.uiexplorer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAnalysisProvider;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContextService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunPersistence;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class UiExplorerJobServiceTestCreator {

    private UiExplorerJobServiceTestCreator() {
    }

    static UiExplorerJobService create(
            UiExplorerScreenReachabilityContextService reachabilityContextService,
            UiExplorerAnalysisProvider analysisProvider,
            TaskExecutor taskExecutor
    ) {
        return create(reachabilityContextService, analysisProvider, taskExecutor, UiExplorerLocalRunPersistence.NO_OP);
    }

    static UiExplorerJobService create(
            UiExplorerScreenReachabilityContextService reachabilityContextService,
            UiExplorerAnalysisProvider analysisProvider,
            TaskExecutor taskExecutor,
            UiExplorerLocalRunPersistence localRunPersistence
    ) {
        var artifactService = new UiExplorerArtifactService(new ObjectMapper());
        return new UiExplorerJobService(
                reachabilityContextService,
                new UiExplorerScreenReachabilityEvidenceMapper(),
                new UiExplorerPromptPreparationService(
                        artifactService,
                        new CopilotArtifactContentMapper()
                ),
                new UiExplorerPromptPreparationEvidenceMapper(),
                analysisProvider,
                taskExecutor,
                () -> AnalysisAiAuthRef.localToken(null),
                localRunPersistence
        );
    }

    static UiExplorerScreenReachabilityContextService reachabilityContextService(
            UiExplorerScreenReachabilityContext context
    ) {
        var reachabilityContextService = mock(UiExplorerScreenReachabilityContextService.class);
        when(reachabilityContextService.buildContext(
                eq("crm-agent-portal"),
                eq("main"),
                eq("crm-contact-preferences"),
                eq("crm-commit-abc123"),
                anyList()
        )).thenReturn(context);
        return reachabilityContextService;
    }

    static AnalysisReport report(String reportId, UiExplorerResultResponse result) {
        var sections = result.sections().stream().map(section -> new AnalysisReportSection(
                section.sectionId().name(),
                section.sectionId().label(),
                section.sectionId().ordinal(),
                section.markdown(),
                new AnalysisReportMeta(
                        section.sourceReferences().stream().map(reference -> new AnalysisReportReference(
                                "source",
                                reference.symbol(),
                                reference.path() + "#L" + reference.startLine() + "-L" + reference.endLine(),
                                "Synthetic CRM UI source"
                        )).toList(),
                        section.visibilityLimits(),
                        section.openQuestions(),
                        List.of(),
                        "high",
                        List.of()
                )
        )).toList();
        return new AnalysisReport(
                reportId,
                result.screen().routePattern(),
                result.screen().label(),
                result.functionalOverview(),
                sections,
                new AnalysisReportMeta(
                        sections.stream().flatMap(section -> section.meta().references().stream()).toList(),
                        result.visibilityLimits(),
                        result.unresolvedQuestions(),
                        List.of(),
                        "high",
                        List.of()
                )
        );
    }
}
