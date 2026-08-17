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
import pl.mkn.tdw.features.uiexplorer.ai.response.UiExplorerAiParseStatus;
import pl.mkn.tdw.features.uiexplorer.ai.response.UiExplorerAiResponseParser;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerResultReportAssembler;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
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
    private final UiExplorerAiResponseParser responseParser;
    private final UiExplorerResultReportAssembler reportAssembler;

    @Override
    public UiExplorerAiAnalysis analyze(
            String runReference,
            UiExplorerJobStartRequest request,
            UiExplorerSourceContextSnapshot context,
            UiExplorerPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var readiness = readinessGate.evaluate(request, context);
        if (!readiness.executable()) {
            var fallback = responseParser.malformed(
                    request,
                    context,
                    readiness.limitations().isEmpty()
                            ? "UI Explorer AI readiness is blocked."
                            : readiness.limitations().get(0)
            );
            return new UiExplorerAiAnalysis(
                    UiExplorerAiAnalysisStatus.BLOCKED,
                    fallback.result(),
                    null,
                    null,
                    null,
                    reportAssembler.assemble(reportId(runReference), fallback.result()).report(),
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
        var parsed = responseParser.parse(execution.content(), request, context, Set.copyOf(fetchedSourcePaths));
        if (assembly.toolAccessPolicy().fallbackAvailable()
                && !gitLabFallbackAttempted.get()
                && reportsPreventableRepositoryGap(parsed.result())) {
            parsed = responseParser.malformed(
                    request,
                    context,
                    "AI reported a missing in-scope UI source without attempting the required scoped GitLab fallback."
            );
        }
        var result = withUsageAndLimit(
                parsed.result(),
                execution.usage(),
                missingFallback ? "Scoped GitLab fallback tools were unavailable for this run." : null
        );
        var report = reportAssembler.assemble(reportId(runReference), result).report();
        return new UiExplorerAiAnalysis(
                missingFallback && parsed.status() == UiExplorerAiParseStatus.COMPLETED
                        ? UiExplorerAiAnalysisStatus.PARTIAL
                        : status(parsed.status()),
                result,
                execution.usage(),
                preparation.prompt(),
                execution.sessionId(),
                report,
                mergeLimitations(parsed.limitations(), missingFallback)
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

    private UiExplorerResultResponse withUsageAndLimit(
            UiExplorerResultResponse result,
            AnalysisAiUsage usage,
            String limitation
    ) {
        var limits = new LinkedHashSet<>(result.visibilityLimits());
        if (StringUtils.hasText(limitation)) {
            limits.add(limitation);
        }
        return new UiExplorerResultResponse(
                result.screen(),
                result.scenarioDescription(),
                result.sourceRevision(),
                result.functionalOverview(),
                result.sections(),
                result.overallConfidence(),
                java.util.List.copyOf(limits),
                result.unresolvedQuestions(),
                usage
        );
    }

    private java.util.List<String> mergeLimitations(java.util.List<String> limitations, boolean missingFallback) {
        var merged = new LinkedHashSet<>(limitations);
        if (missingFallback) {
            merged.add("Scoped GitLab fallback tools were unavailable for this run.");
        }
        return java.util.List.copyOf(merged);
    }

    private UiExplorerAiAnalysisStatus status(UiExplorerAiParseStatus status) {
        return switch (status) {
            case COMPLETED -> UiExplorerAiAnalysisStatus.COMPLETED;
            case PARTIAL -> UiExplorerAiAnalysisStatus.PARTIAL;
            case MALFORMED -> UiExplorerAiAnalysisStatus.MALFORMED;
        };
    }

    private String reportId(String runReference) {
        var value = StringUtils.hasText(runReference) ? runReference.trim() : "unassigned";
        return "ui-explorer-report-" + value;
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
