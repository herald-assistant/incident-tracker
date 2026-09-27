package pl.mkn.tdw.integrations.operationalcontext.contract;

public record OperationalContextCatalogMutationResult(
        OperationalContextEditableEntity entity,
        String contentDigest
) {
    public OperationalContextCatalogMutationResult(OperationalContextEditableEntity entity) {
        this(entity, null);
    }
}
