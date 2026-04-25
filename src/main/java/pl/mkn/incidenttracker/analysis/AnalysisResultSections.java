package pl.mkn.incidenttracker.analysis;

import java.util.List;
import java.util.Objects;

public final class AnalysisResultSections {

    private AnalysisResultSections() {
    }

    public static Summary emptySummary() {
        return new Summary("", List.of(), "", "", List.of());
    }

    public static RecommendedAction emptyRecommendedAction() {
        return new RecommendedAction(List.of());
    }

    public static Rationale emptyRationale() {
        return new Rationale(List.of(), List.of(), List.of(), List.of());
    }

    public static AffectedFunction emptyAffectedFunction() {
        return new AffectedFunction("", "", "", List.of(), "");
    }

    public record Summary(
            String overview,
            List<String> keyEvidence,
            String failurePoint,
            String failureDomain,
            List<String> visibilityGaps
    ) {

        public Summary {
            overview = defaultText(overview);
            keyEvidence = copyOfTextList(keyEvidence);
            failurePoint = defaultText(failurePoint);
            failureDomain = defaultText(failureDomain);
            visibilityGaps = copyOfTextList(visibilityGaps);
        }
    }

    public record RecommendedAction(List<ActionItem> items) {

        public RecommendedAction {
            items = copyOfActionItems(items);
        }
    }

    public record ActionItem(String owner, String action) {

        public ActionItem {
            owner = defaultText(owner);
            action = defaultText(action);
        }
    }

    public record Rationale(
            List<String> confirmedEvidence,
            List<String> supportingHypotheses,
            List<String> surroundingFlow,
            List<String> visibilityLimits
    ) {

        public Rationale {
            confirmedEvidence = copyOfTextList(confirmedEvidence);
            supportingHypotheses = copyOfTextList(supportingHypotheses);
            surroundingFlow = copyOfTextList(surroundingFlow);
            visibilityLimits = copyOfTextList(visibilityLimits);
        }
    }

    public record AffectedFunction(
            String name,
            String description,
            String roleInFlow,
            List<String> keyCollaborators,
            String interruptionPoint
    ) {

        public AffectedFunction {
            name = defaultText(name);
            description = defaultText(description);
            roleInFlow = defaultText(roleInFlow);
            keyCollaborators = copyOfTextList(keyCollaborators);
            interruptionPoint = defaultText(interruptionPoint);
        }
    }

    private static String defaultText(String value) {
        return value != null ? value : "";
    }

    private static List<String> copyOfTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<ActionItem> copyOfActionItems(List<ActionItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
