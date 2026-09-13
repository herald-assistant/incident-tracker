package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

/** A single decision over an ordered set of catalog mutations. */
public record OperationalContextCatalogConditionalBatchCommand(
        String expectedDigest,
        List<Mutation> mutations
) {
    public OperationalContextCatalogConditionalBatchCommand {
        mutations = mutations == null ? List.of() : List.copyOf(mutations);
    }

    public record Mutation(
            String type,
            String id,
            OperationalContextCatalogConditionalMutationCommand.Operation operation,
            List<OperationalContextCatalogConditionalMutationCommand.FieldChange> changes
    ) {
        public Mutation {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }
}
