package pl.mkn.tdw.integrations.dynatrace;

public interface DynatraceIncidentPort {

    DynatraceIncidentEvidence loadIncidentEvidence(DynatraceIncidentQuery query);

}
