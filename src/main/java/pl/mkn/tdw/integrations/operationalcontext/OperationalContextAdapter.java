package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

@Component
@RequiredArgsConstructor
public class OperationalContextAdapter implements OperationalContextPort {

    private final OperationalContextSnapshotStore snapshotStore;
    private final OperationalContextCatalogQueryService queryService;

    @Override
    public OperationalContextCatalog loadContext(OperationalContextQuery query) {
        return capture().query(query);
    }

    @Override
    public OperationalContextSnapshot currentSnapshot() {
        return snapshotStore.currentStoredSnapshot().readSnapshot();
    }

    @Override
    public OperationalContextDocumentSnapshot currentDocumentSnapshot() {
        var stored = snapshotStore.currentStoredSnapshot();
        var snapshot = stored.readSnapshot();
        return new OperationalContextDocumentSnapshot(
                snapshot.contentDigest(), snapshot.source(), snapshot.catalog(), stored.decodedDocuments()
        );
    }

    @Override
    public OperationalContextReadSession capture() {
        var snapshot = snapshotStore.currentStoredSnapshot().readSnapshot();
        return new OperationalContextReadSession(snapshot, queryService);
    }
}
