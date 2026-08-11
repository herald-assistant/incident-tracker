package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.Objects;

public final class OperationalContextReadSession {

    private final OperationalContextSnapshot snapshot;
    private final OperationalContextCatalogQueryService queryService;

    OperationalContextReadSession(
            OperationalContextSnapshot snapshot,
            OperationalContextCatalogQueryService queryService
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    static OperationalContextReadSession legacy(OperationalContextSnapshot snapshot) {
        return new OperationalContextReadSession(snapshot, new OperationalContextCatalogQueryService());
    }

    public OperationalContextSnapshot snapshot() {
        return snapshot;
    }

    public String contentDigest() {
        return snapshot.contentDigest();
    }

    public OperationalContextCatalog query(OperationalContextQuery query) {
        return queryService.query(snapshot.catalog(), query);
    }
}
