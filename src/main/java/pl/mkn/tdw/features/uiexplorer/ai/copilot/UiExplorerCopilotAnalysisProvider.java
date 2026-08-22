package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysis;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysisStatus;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAnalysisProvider;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;
import pl.mkn.tdw.features.uiexplorer.ai.readiness.UiExplorerAiReadinessGate;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerReportMapper;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class UiExplorerCopilotAnalysisProvider implements UiExplorerAnalysisProvider {

    private final UiExplorerAiReadinessGate readinessGate;
    private final UiExplorerCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final UiExplorerReportMapper reportMapper;

    @Override
    public UiExplorerAiAnalysis analyze(
            String runReference,
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context,
            UiExplorerPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var readiness = readinessGate.evaluate(request, context);
        if (!readiness.executable()) {
            return new UiExplorerAiAnalysis(
                    UiExplorerAiAnalysisStatus.BLOCKED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    readiness.limitations()
            );
        }

        var assembly = runRequestAssembler.assemble(
                runReference,
                request,
                context,
                preparation,
                readiness,
                authRef
        );
        if (!assembly.toolAccessPolicy().reportToolsAvailable()) {
            return new UiExplorerAiAnalysis(
                    UiExplorerAiAnalysisStatus.BLOCKED,
                    null,
                    null,
                    preparation.prompt(),
                    null,
                    null,
                    java.util.List.of("UI Explorer report tools are unavailable for this run.")
            );
        }
        var fetchedSourcePaths = new LinkedHashSet<String>();
        var gitLabFallbackAttempted = new AtomicBoolean();
        var preparedSession = runPreparationService.prepare(assembly.runRequest())
                .withEvidenceSink(section -> {
                    if (section != null && "gitlab".equals(section.provider())) {
                        gitLabFallbackAttempted.set(true);
                    }
                    collectFetchedSourcePaths(section, fetchedSourcePaths);
                    if (toolEvidenceListener != null && toolEvidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
                        toolEvidenceListener.onToolEvidenceUpdated(section);
                    }
                });
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            preparedSession = preparedSession.withActivitySink(activityListener::onAiActivity);
        }

        var missingFallback = readiness.fallbackToolsRequired() && !assembly.toolAccessPolicy().fallbackAvailable();
        var execution = executionGateway.execute(preparedSession);
        var mapping = reportMapper.map(
                execution.report(),
                request,
                context,
                Set.copyOf(fetchedSourcePaths),
                execution.usage()
        );
        if (mapping.result() == null || mapping.report() == null) {
            return new UiExplorerAiAnalysis(
                    UiExplorerAiAnalysisStatus.FAILED,
                    null,
                    execution.usage(),
                    preparation.prompt(),
                    execution.sessionId(),
                    null,
                    mapping.limitations()
            );
        }
        var preventableRepositoryGap = assembly.toolAccessPolicy().fallbackAvailable()
                && !gitLabFallbackAttempted.get()
                && reportsPreventableRepositoryGap(mapping.result());
        var limitations = new LinkedHashSet<>(mapping.limitations());
        var result = mapping.result();
        var report = mapping.report();
        if (preventableRepositoryGap) {
            var limitation = "AI reported a missing in-scope UI source without attempting the required scoped GitLab fallback.";
            limitations.add(limitation);
            result = withLimit(result, limitation);
            report = withLimit(report, limitation);
        }
        if (missingFallback) {
            var limitation = "Scoped GitLab fallback tools were unavailable for this run.";
            limitations.add(limitation);
            result = withLimit(result, limitation);
            report = withLimit(report, limitation);
        }
        return new UiExplorerAiAnalysis(
                mapping.complete() && !missingFallback && !preventableRepositoryGap
                        ? UiExplorerAiAnalysisStatus.COMPLETED
                        : UiExplorerAiAnalysisStatus.PARTIAL,
                result,
                execution.usage(),
                preparation.prompt(),
                execution.sessionId(),
                report,
                java.util.List.copyOf(limitations)
        );
    }

    private void collectFetchedSourcePaths(AnalysisEvidenceSection section, Set<String> target) {
        if (section == null || !"gitlab".equals(section.provider())
                || !"tool-fetched-code".equals(section.category())) {
            return;
        }
        section.items().stream()
                .flatMap(item -> item.attributes().stream())
                .filter(attribute -> "filePath".equals(attribute.name()))
                .map(attribute -> normalizePath(attribute.value()))
                .filter(StringUtils::hasText)
                .forEach(target::add);
    }

    private UiExplorerResultResponse withLimit(UiExplorerResultResponse result, String limitation) {
        var limits = new LinkedHashSet<>(result.visibilityLimits());
        limits.add(limitation);
        return new UiExplorerResultResponse(
                result.screen(),
                result.scenarioDescription(),
                result.sourceRevision(),
                result.functionalOverview(),
                result.sections(),
                result.overallConfidence(),
                java.util.List.copyOf(limits),
                result.unresolvedQuestions(),
                result.usage()
        );
    }

    private AnalysisReport withLimit(AnalysisReport report, String limitation) {
        var limits = new LinkedHashSet<>(report.meta().visibilityLimits());
        limits.add(limitation);
        var meta = new AnalysisReportMeta(
                report.meta().references(),
                java.util.List.copyOf(limits),
                report.meta().openQuestions(),
                report.meta().gaps(),
                report.meta().confidence(),
                report.meta().warnings()
        );
        return new AnalysisReport(
                report.reportId(), report.header(), report.subHeader(), report.markdownSummary(), report.sections(), meta
        );
    }

    private String normalizePath(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "")
                : null;
    }

    private boolean reportsPreventableRepositoryGap(UiExplorerResultResponse result) {
        if (result == null) {
            return false;
        }
        var limits = new LinkedHashSet<String>(result.visibilityLimits());
        result.sections().forEach(section -> limits.addAll(section.visibilityLimits()));
        return limits.stream()
                .filter(StringUtils::hasText)
                .map(this::searchableText)
                .anyMatch(limit -> limit.contains("snapshot")
                        || limit.contains("child route")
                        || limit.contains("child view")
                        || limit.contains("podwidok")
                        || limit.contains("komponent potom")
                        || limit.contains("brak kodu")
                        || limit.contains("missing code")
                        || limit.contains("brakujac") && limit.contains("modal"));
    }

    private String searchableText(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
