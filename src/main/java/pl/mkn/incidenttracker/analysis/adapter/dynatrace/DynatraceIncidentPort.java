package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

public interface DynatraceIncidentPort {

    DynatraceIncidentEvidence loadIncidentEvidence(DynatraceIncidentQuery query);

}
