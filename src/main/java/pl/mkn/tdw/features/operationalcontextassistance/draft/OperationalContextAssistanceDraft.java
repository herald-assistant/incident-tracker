package pl.mkn.tdw.features.operationalcontextassistance.draft;

import java.util.List;

public record OperationalContextAssistanceDraft(
        List<Proposal> proposals,
        List<String> visibilityLimits
) {
    public OperationalContextAssistanceDraft {
        proposals = List.copyOf(proposals);
        visibilityLimits = List.copyOf(visibilityLimits);
    }

    public record Proposal(
            Operation operation,
            String entityType,
            String entityId,
            List<FieldChange> changes,
            Confidence confidence,
            boolean requiresConfirmation,
            List<String> visibilityLimits
    ) {
        public Proposal {
            changes = List.copyOf(changes);
            visibilityLimits = List.copyOf(visibilityLimits);
        }
    }

    public record FieldChange(
            String path,
            Object before,
            Object after,
            String reason,
            Basis basis,
            List<String> sourceRefs,
            Confidence confidence,
            boolean requiresConfirmation
    ) {
        public FieldChange {
            sourceRefs = List.copyOf(sourceRefs);
        }
    }

    public enum Operation { CREATE, UPDATE }

    public enum Basis { USER_STATEMENT, SOURCE_OBSERVATION, AI_INTERPRETATION }

    public enum Confidence { LOW, MEDIUM, HIGH }
}
