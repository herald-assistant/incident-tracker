package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

public record OperationalContextCatalogBatchMutationResult(
        List<OperationalContextEditableEntity> entities,
        String contentDigest
) {
    public OperationalContextCatalogBatchMutationResult {
        entities = entities == null ? List.of() : List.copyOf(entities);
    }
}
