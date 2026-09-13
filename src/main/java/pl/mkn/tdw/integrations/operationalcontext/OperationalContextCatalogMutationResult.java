package pl.mkn.tdw.integrations.operationalcontext;

public record OperationalContextCatalogMutationResult(
        OperationalContextEditableEntity entity,
        String contentDigest
) {
    public OperationalContextCatalogMutationResult(OperationalContextEditableEntity entity) {
        this(entity, null);
    }
}
