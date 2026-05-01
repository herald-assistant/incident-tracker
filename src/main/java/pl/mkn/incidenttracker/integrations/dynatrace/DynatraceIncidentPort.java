package pl.mkn.incidenttracker.integrations.dynatrace;

public interface DynatraceIncidentPort {

    DynatraceIncidentEvidence loadIncidentEvidence(DynatraceIncidentQuery query);

}
