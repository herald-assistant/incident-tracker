package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDocumentSnapshot;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextQuery;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextReadSession;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextSnapshot;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;

public interface OperationalContextPort {

    OperationalContextCatalog loadContext(OperationalContextQuery query);

    default OperationalContextSnapshot currentSnapshot() {
        return OperationalContextSnapshot.local(loadContext(OperationalContextQuery.all()));
    }

    default OperationalContextReadSession capture() {
        return OperationalContextReadSession.fromSnapshot(currentSnapshot());
    }

    /** Captures all logical YAML documents and the catalog from one immutable runtime snapshot. */
    default OperationalContextDocumentSnapshot currentDocumentSnapshot() {
        throw new UnsupportedOperationException("Complete operational context documents are unavailable");
    }

}
