package pl.mkn.tdw.integrations.dynatrace;

import pl.mkn.tdw.integrations.dynatrace.contract.DynatraceIncidentEvidence;
import pl.mkn.tdw.integrations.dynatrace.contract.DynatraceIncidentQuery;

public interface DynatraceIncidentPort {

    boolean isConfigured();

    DynatraceIncidentEvidence loadIncidentEvidence(DynatraceIncidentQuery query);

}
