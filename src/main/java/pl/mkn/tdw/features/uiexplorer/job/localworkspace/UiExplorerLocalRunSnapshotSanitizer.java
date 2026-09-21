package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.ToolResultActivityDetailsSanitizer;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class UiExplorerLocalRunSnapshotSanitizer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, Set<String>> SAFE_CONTEXT_EVIDENCE_ATTRIBUTES = Map.of(
            "selected-screen", Set.of(
                    "screenId", "component", "navigationContext", "discoveryStatus", "lazyLoaded",
                    "guards", "routeParameters", "branch", "sourceRevision", "reachabilityStatus"
            ),
            "route-chain", Set.of("pathSegment", "outlet", "sourcePath", "sourceSymbol"),
            "component-reachability", Set.of(
                    "breadthFirstOrder", "depth", "discoveryKind", "selector", "sourcePath",
                    "templatePath", "status", "entrySymbols", "dependencyCount", "childCount",
                    "sliceCharacters"
            ),
            "dependency-reachability", Set.of(
                    "discoveryOrder", "kind", "category", "sourcePath", "moduleSpecifier", "status",
                    "methods", "usedBy", "downstreamCount", "sliceCharacters"
            ),
            "section-coverage", Set.of("sectionId", "mode", "status", "sourceCategories", "detail"),
            "ai-artifacts", Set.of("role", "category", "mimeType", "itemCount", "characterCount"),
            "reachability-boundary", Set.of(
                    "routeSegmentCount", "componentCount", "dependencyCount", "edgeCount",
                    "sourceFileCount", "sourceCharacters", "sliceCharacters", "outlineCharacters",
                    "contextLimitReached", "researchGap"
            ),
            "reachability-diagnostics", Set.of("severity", "message", "sourcePath")
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

    private static final Set<String> TOOL_RESULTS_WITH_PUBLIC_CODE_CONTENT = Set.of(
            "gitlab_read_frontend_typescript_symbol_slice",
            "gitlab_read_repository_file"
    );

    private static final Set<String> SAFE_CONTEXT_TIER_ACTIVITY_DETAILS = Set.of(
            "phase",
            "trigger",
            "observationSource",
            "model",
            "preference",
            "requestedTier",
            "estimatedInitialTokens",
            "initialThresholdTokens",
            "runtimeUsageThreshold",
            "runtimeThresholdTokens",
            "defaultWindowTokens",
            "longContextWindowTokens",
            "reason",
            "effectiveTier",
            "effectiveModel",
            "effectiveReasoningEffort",
            "tokenLimit",
            "currentTokens",
            "messagesLength",
            "utilizationPercent",
            "verification",
            "rpcSuccess",
            "runtimeUpgradeConfirmed",
            "failureType",
            "sessionId"
    );

    public UiExplorerJobStateSnapshot sanitize(UiExplorerJobStateSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        var safeResult = sanitize(snapshot.result());
        var safeContextSections = sanitizeContextEvidence(snapshot.contextSections());
        var safeToolEvidenceSections = sanitizeToolEvidence(snapshot.toolEvidenceSections());
        var safeReport = sanitize(
                snapshot.report(),
                safeResult,
                snapshot.jobId(),
                allowedSourcePaths(safeResult, safeContextSections, safeToolEvidenceSections)
        );
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
                safeContextSections,
                safeToolEvidenceSections,
                snapshot.aiActivityEvents().stream().map(this::sanitize).toList(),
                List.of(),
                snapshot.preparedPrompt(),
                safeResult,
                safeReport,
                snapshot.usage(),
                snapshot.sourceRevision(),
                snapshot.outputAvailability(),
                safeResult != null && safeReport != null && snapshot.chatMessages().stream()
                        .noneMatch(message -> "ASSISTANT".equals(message.role())
                                && "IN_PROGRESS".equals(message.status())),
                snapshot.chatMessages().stream().map(this::sanitize).toList(),
                snapshot.chatAvailability()
        );
    }

    private AnalysisChatMessageResponse sanitize(AnalysisChatMessageResponse message) {
        return new AnalysisChatMessageResponse(
                message.id(),
                message.role(),
                message.status(),
                message.content(),
                message.errorCode(),
                message.errorMessage(),
                message.createdAt(),
                message.updatedAt(),
                message.completedAt(),
                sanitizeToolEvidence(message.toolEvidenceSections()),
                message.aiActivityEvents().stream().map(this::sanitize).toList(),
                message.toolFeedback(),
                message.prompt(),
                message.usage()
        );
    }

    private AnalysisReport sanitize(
            AnalysisReport report,
            UiExplorerResultResponse result,
            String jobId,
            Set<String> allowedPaths
    ) {
        if (report == null) {
            return null;
        }
        return new AnalysisReport(
                "ui-explorer-report-" + jobId,
                result != null && result.screen() != null ? result.screen().routePattern() : report.header(),
                result != null && result.screen() != null ? result.screen().label() : report.subHeader(),
                report.markdownSummary(),
                report.sections().stream().map(section -> sanitize(section, allowedPaths)).toList(),
                sanitize(report.meta(), allowedPaths)
        );
    }

    private Set<String> allowedSourcePaths(
            UiExplorerResultResponse result,
            List<AnalysisEvidenceSection> contextSections,
            List<AnalysisEvidenceSection> toolEvidenceSections
    ) {
        var paths = new java.util.LinkedHashSet<String>();
        if (result != null) {
            result.sections().stream()
                    .flatMap(section -> section.sourceReferences().stream())
                    .map(UiExplorerSourceReference::path)
                    .map(this::normalizePath)
                    .filter(java.util.Objects::nonNull)
                    .forEach(paths::add);
        }
        java.util.stream.Stream.concat(contextSections.stream(), toolEvidenceSections.stream())
                .flatMap(section -> section.items().stream())
                .flatMap(item -> item.attributes().stream())
                .filter(attribute -> "sourcePath".equals(attribute.name())
                        || "templatePath".equals(attribute.name())
                        || "filePath".equals(attribute.name()))
                .map(AnalysisEvidenceAttribute::value)
                .map(this::normalizePath)
                .filter(java.util.Objects::nonNull)
                .forEach(paths::add);
        return Set.copyOf(paths);
    }

    private AnalysisReportSection sanitize(AnalysisReportSection section, Set<String> allowedPaths) {
        return new AnalysisReportSection(
                section.id(),
                section.title(),
                section.order(),
                section.markdown(),
                sanitize(section.meta(), allowedPaths)
        );
    }

    private AnalysisReportMeta sanitize(AnalysisReportMeta meta, Set<String> allowedPaths) {
        var source = meta != null ? meta : AnalysisReportMeta.empty();
        return new AnalysisReportMeta(
                source.references().stream()
                        .filter(reference -> allowedReference(reference, allowedPaths))
                        .map(this::sanitize)
                        .toList(),
                source.visibilityLimits(),
                source.openQuestions(),
                source.gaps(),
                source.confidence(),
                source.warnings()
        );
    }

    private AnalysisReportReference sanitize(AnalysisReportReference reference) {
        return new AnalysisReportReference(
                reference.type(),
                reference.label(),
                reference.target(),
                reference.description()
        );
    }

    private boolean allowedReference(AnalysisReportReference reference, Set<String> allowedPaths) {
        if (reference == null || reference.target() == null) {
            return false;
        }
        var target = reference.target().replace('\\', '/');
        var lineMarker = target.indexOf("#L");
        var path = normalizePath(lineMarker >= 0 ? target.substring(0, lineMarker) : target);
        return allowedPaths.contains(path);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
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
                result.sourceRevision(),
                result.functionalOverview(),
                result.sections().stream().map(this::sanitize).toList(),
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
                section.confidence(),
                section.markdown(),
                section.sourceReferences().stream().map(this::sanitize).toList(),
                section.visibilityLimits(),
                section.openQuestions()
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
        var safeAttributes = new ArrayList<>(sanitizeAttributes(item, SAFE_TOOL_EVIDENCE_ATTRIBUTES));
        var toolName = attributeValue(item, "toolName");
        if (TOOL_RESULTS_WITH_PUBLIC_CODE_CONTENT.contains(toolName)) {
            addCopiedAttribute(item, safeAttributes, "content");
        }
        if ("gitlab_search_repository_candidates".equals(toolName)) {
            addCandidateFilePaths(item, safeAttributes);
        }
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

    private void addCandidateFilePaths(
            AnalysisEvidenceItem item,
            List<AnalysisEvidenceAttribute> safeAttributes
    ) {
        var candidates = attributeValue(item, "candidates");
        if (candidates == null) {
            return;
        }
        try {
            var paths = new LinkedHashSet<String>();
            for (JsonNode candidate : JSON.readTree(candidates)) {
                var filePath = candidate.path("filePath").asText(null);
                if (filePath != null && !filePath.isBlank()) {
                    paths.add(filePath.trim());
                }
            }
            if (!paths.isEmpty()) {
                safeAttributes.add(new AnalysisEvidenceAttribute(
                        "filePaths",
                        JSON.writeValueAsString(paths)
                ));
            }
        } catch (JsonProcessingException ignored) {
            // Malformed optional presentation data must not block persistence of the run.
        }
    }

    private void addCopiedAttribute(
            AnalysisEvidenceItem item,
            List<AnalysisEvidenceAttribute> safeAttributes,
            String name
    ) {
        var value = attributeValue(item, name);
        if (value != null) {
            safeAttributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private String attributeValue(AnalysisEvidenceItem item, String name) {
        return item.attributes().stream()
                .filter(attribute -> name.equals(attribute.name()))
                .map(AnalysisEvidenceAttribute::value)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
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
                sanitizeActivityDetails(event)
        );
    }

    private Map<String, Object> sanitizeActivityDetails(AnalysisAiActivityEvent event) {
        if (event.details().isEmpty()) {
            return Map.of();
        }
        if ("tool.execution_complete".equals(event.type())) {
            return ToolResultActivityDetailsSanitizer.sanitize(event.details());
        }
        if (!"platform.context_tier".equals(event.type())) {
            return Map.of();
        }
        var safeDetails = new LinkedHashMap<String, Object>();
        event.details().forEach((name, value) -> {
            if (SAFE_CONTEXT_TIER_ACTIVITY_DETAILS.contains(name) && isSafeActivityDetailValue(value)) {
                safeDetails.put(name, value);
            }
        });
        return safeDetails;
    }

    private boolean isSafeActivityDetailValue(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof String string && string.length() <= 500;
    }
}
