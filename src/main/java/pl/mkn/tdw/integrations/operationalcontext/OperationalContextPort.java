package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

public interface OperationalContextPort {

    OperationalContextCatalog loadContext(OperationalContextQuery query);

    default OperationalContextSnapshot currentSnapshot() {
        return OperationalContextSnapshot.local(loadContext(OperationalContextQuery.all()));
    }

    default OperationalContextReadSession capture() {
        return OperationalContextReadSession.legacy(currentSnapshot());
    }

}
