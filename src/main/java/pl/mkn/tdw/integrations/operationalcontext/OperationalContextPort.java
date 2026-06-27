package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

public interface OperationalContextPort {

    OperationalContextCatalog loadContext(OperationalContextQuery query);

}
