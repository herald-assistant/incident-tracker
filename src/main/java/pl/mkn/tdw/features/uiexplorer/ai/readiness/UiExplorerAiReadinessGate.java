package pl.mkn.tdw.features.uiexplorer.ai.readiness;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSectionContextCoverage;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class UiExplorerAiReadinessGate {

    public UiExplorerAiReadiness evaluate(
            UiExplorerJobStartRequest request,
            UiExplorerSourceContextSnapshot context
    ) {
        var limitations = new ArrayList<String>();
        if (request == null || context == null) {
            return blocked(List.of(), "UI Explorer request and source context are required.");
        }

        var activeAssignments = request.resolvedSectionModes().stream()
                .filter(assignment -> assignment.mode() != UiExplorerSectionMode.OFF)
                .toList();
        var activeSections = activeAssignments.stream().map(assignment -> assignment.sectionId()).toList();
        if (activeSections.isEmpty()) {
            return blocked(List.of(), "At least one UI Explorer section must be active.");
        }
        if (context.sourceScope() == null || context.sourceRevision() == null) {
            return blocked(activeSections, "Resolved source scope and revision are required for AI execution.");
        }
        if (context.status() == UiExplorerCoverageStatus.BLOCKED || context.sourceFiles().isEmpty()) {
            return blocked(activeSections, "Deterministic screen source context is blocked.");
        }

        var coverageBySection = new LinkedHashMap<UiExplorerSectionId, UiExplorerSectionContextCoverage>();
        context.sectionCoverage().forEach(coverage -> coverageBySection.put(coverage.sectionId(), coverage));
        var partial = context.status() == UiExplorerCoverageStatus.PARTIAL
                || context.boundary() != null && (
                context.boundary().inventoryTruncated()
                        || context.boundary().routeCatalogTruncated()
                        || context.boundary().contextTruncated()
        );

        for (var assignment : activeAssignments) {
            var coverage = coverageBySection.get(assignment.sectionId());
            if (coverage == null || coverage.mode() != assignment.mode()) {
                limitations.add("Missing deterministic coverage for active section "
                        + assignment.sectionId().name() + ".");
                return new UiExplorerAiReadiness(
                        UiExplorerAiReadinessStatus.BLOCKED,
                        activeSections,
                        false,
                        limitations
                );
            }
            if (coverage.status() == UiExplorerCoverageStatus.BLOCKED) {
                limitations.add("Deterministic coverage is blocked for active section "
                        + assignment.sectionId().name() + ".");
                return new UiExplorerAiReadiness(
                        UiExplorerAiReadinessStatus.BLOCKED,
                        activeSections,
                        false,
                        limitations
                );
            }
            partial |= coverage.status() == UiExplorerCoverageStatus.PARTIAL;
        }

        limitations.addAll(context.visibilityLimits());
        return new UiExplorerAiReadiness(
                partial ? UiExplorerAiReadinessStatus.PARTIAL : UiExplorerAiReadinessStatus.READY,
                activeSections,
                partial,
                limitations
        );
    }

    private UiExplorerAiReadiness blocked(List<UiExplorerSectionId> activeSections, String reason) {
        return new UiExplorerAiReadiness(
                UiExplorerAiReadinessStatus.BLOCKED,
                activeSections,
                false,
                List.of(reason)
        );
    }
}
