package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

public interface OperationalContextPort {

    OperationalContextCatalog loadContext(OperationalContextQuery query);

}
