package pl.mkn.incidenttracker.integrations.operationalcontext;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

public interface OperationalContextPort {

    OperationalContextCatalog loadContext(OperationalContextQuery query);

}
