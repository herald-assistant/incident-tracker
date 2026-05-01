package pl.mkn.incidenttracker.integrations.operationalcontext;

public interface OperationalContextPort {

    OperationalContextCatalog loadContext(OperationalContextQuery query);

}
