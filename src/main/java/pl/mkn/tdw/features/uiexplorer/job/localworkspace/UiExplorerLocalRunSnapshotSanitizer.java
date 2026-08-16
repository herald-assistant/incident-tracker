package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerFinding;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerResultReportAssembler;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UiExplorerLocalRunSnapshotSanitizer {

    private static final Map<String, Set<String>> SAFE_CONTEXT_EVIDENCE_ATTRIBUTES = Map.of(
            "selected-screen", Set.of(
                    "screenId", "routePattern", "navigationContext", "discoveryStatus", "lazyLoaded",
                    "guards", "routeParameters", "branch", "sourceRevision", "contextStatus"
            ),
            "source-manifest", Set.of("roles", "returnedCharacters", "truncated"),
            "technical-signals", Set.of(
                    "description", "confidence", "sourcePath", "sourceSymbol", "startLine", "endLine"
            ),
            "section-coverage", Set.of("sectionId", "mode", "status", "sourceCategories", "detail"),
            "source-boundary", Set.of(
                    "visitedRouteNodeCount", "visitedRouteFileCount", "graphSourceReadCount",
                    "aliasResolutionCount", "unresolvedEdgeCount", "returnedContextFileCount",
                    "totalReturnedCharacters", "graphLimitReached", "contextLimitReached",
                    "maxRouteNodes", "maxRouteFiles", "maxSourceReads", "maxAliasResolutions",
                    "maxImportDepth", "maxComponentDepth", "maxContextFiles", "maxFileCharacters",
                    "maxTotalCharacters", "visibilityLimit"
            ),
            "source-diagnostics", Set.of("severity", "message", "sourcePath")
    );

    private static final Set<String> SAFE_TOOL_EVIDENCE_ATTRIBUTES = Set.of(
            "filePath",
            "reason",
            "toolCallId",
            "toolName",
            "startLine",
            "candidateCount",
            "returnedStartLine",
            "returnedEndLine"
    );

    private final UiExplorerResultReportAssembler reportAssembler;

    public UiExplorerJobStateSnapshot sanitize(UiExplorerJobStateSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        var safeResult = sanitize(snapshot.result());
        var safeReport = safeResult != null
                ? reportAssembler.assemble("ui-explorer-report-" + snapshot.jobId(), safeResult).report()
                : null;
        return new UiExplorerJobStateSnapshot(
                snapshot.jobId(),
                snapshot.request(),
                snapshot.status(),
                snapshot.currentStepCode(),
                snapshot.currentStepLabel(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt(),
                snapshot.steps(),
                sanitizeContextEvidence(snapshot.contextSections()),
                sanitizeToolEvidence(snapshot.toolEvidenceSections()),
                snapshot.aiActivityEvents().stream().map(this::sanitize).toList(),
                List.of(),
                null,
                safeResult,
                safeReport,
                snapshot.usage(),
                snapshot.sourceRevision(),
                snapshot.outputAvailability(),
                safeResult != null && safeReport != null
        );
    }

    private List<AnalysisEvidenceSection> sanitizeContextEvidence(List<AnalysisEvidenceSection> sections) {
        return sections.stream()
                .filter(section -> "ui-explorer".equals(section.provider()))
                .filter(section -> SAFE_CONTEXT_EVIDENCE_ATTRIBUTES.containsKey(section.category()))
                .map(section -> new AnalysisEvidenceSection(
                        "ui-explorer",
                        section.category(),
                        section.items().stream()
                                .map(item -> sanitizeEvidenceItem(
                                        item,
                                        SAFE_CONTEXT_EVIDENCE_ATTRIBUTES.get(section.category()),
                                        item.title()
                                ))
                                .toList()
                ))
                .filter(AnalysisEvidenceSection::hasItems)
                .toList();
    }

    private UiExplorerResultResponse sanitize(UiExplorerResultResponse result) {
        if (result == null) {
            return null;
        }
        return new UiExplorerResultResponse(
                result.screen(),
                result.scenarioDescription(),
                result.profile(),
                result.sourceRevision(),
                result.functionalOverview(),
                result.sections().stream().map(this::sanitize).toList(),
                result.crossSectionDependencies(),
                result.changePreparationSummary(),
                result.overallConfidence(),
                result.visibilityLimits(),
                result.unresolvedQuestions(),
                result.usage()
        );
    }

    private UiExplorerResultSection sanitize(UiExplorerResultSection section) {
        return new UiExplorerResultSection(
                section.sectionId(),
                section.mode(),
                section.coverage(),
                section.summary(),
                section.findings().stream().map(this::sanitize).toList(),
                section.dependencies(),
                section.sourceReferences().stream().map(this::sanitize).toList(),
                section.visibilityLimits(),
                section.openQuestions()
        );
    }

    private UiExplorerFinding sanitize(UiExplorerFinding finding) {
        return new UiExplorerFinding(
                finding.title(),
                finding.description(),
                finding.confidence(),
                finding.conditions(),
                finding.impactNotes(),
                finding.sourceReferences().stream().map(this::sanitize).toList()
        );
    }

    private UiExplorerSourceReference sanitize(UiExplorerSourceReference reference) {
        return new UiExplorerSourceReference(
                null,
                reference.path(),
                reference.symbol(),
                reference.startLine(),
                reference.endLine()
        );
    }

    private List<AnalysisEvidenceSection> sanitizeToolEvidence(List<AnalysisEvidenceSection> sections) {
        return sections.stream()
                .map(section -> new AnalysisEvidenceSection(
                        section.provider(),
                        section.category(),
                        section.items().stream().map(this::sanitizeToolEvidence).toList()
                ))
                .filter(AnalysisEvidenceSection::hasItems)
                .toList();
    }

    private AnalysisEvidenceItem sanitizeToolEvidence(AnalysisEvidenceItem item) {
        var safeAttributes = sanitizeAttributes(item, SAFE_TOOL_EVIDENCE_ATTRIBUTES);
        var filePath = safeAttributes.stream()
                .filter(attribute -> "filePath".equals(attribute.name()))
                .map(AnalysisEvidenceAttribute::value)
                .findFirst()
                .orElse(null);
        return new AnalysisEvidenceItem(
                filePath != null ? "Source evidence: " + filePath : "UI Explorer tool evidence",
                safeAttributes
        );
    }

    private AnalysisEvidenceItem sanitizeEvidenceItem(
            AnalysisEvidenceItem item,
            Set<String> allowedAttributes,
            String safeTitle
    ) {
        return new AnalysisEvidenceItem(safeTitle, sanitizeAttributes(item, allowedAttributes));
    }

    private List<AnalysisEvidenceAttribute> sanitizeAttributes(
            AnalysisEvidenceItem item,
            Set<String> allowedAttributes
    ) {
        return item.attributes().stream()
                .filter(attribute -> allowedAttributes.contains(attribute.name()))
                .map(attribute -> new AnalysisEvidenceAttribute(attribute.name(), attribute.value()))
                .toList();
    }

    private AnalysisAiActivityEvent sanitize(AnalysisAiActivityEvent event) {
        return new AnalysisAiActivityEvent(
                event.eventId(),
                event.parentEventId(),
                event.type(),
                event.category(),
                event.status(),
                event.title(),
                event.summary(),
                event.turnId(),
                event.interactionId(),
                event.toolCallId(),
                event.toolName(),
                event.timestamp(),
                Map.of()
        );
    }
}
