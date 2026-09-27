package pl.mkn.tdw.integrations.operationalcontext.contract;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;

import java.util.Objects;

public final class OperationalContextReadSession {

    private final OperationalContextSnapshot snapshot;
    private final OperationalContextCatalogQueryService queryService;

    public OperationalContextReadSession(
            OperationalContextSnapshot snapshot,
            OperationalContextCatalogQueryService queryService
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    public static OperationalContextReadSession fromSnapshot(OperationalContextSnapshot snapshot) {
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
