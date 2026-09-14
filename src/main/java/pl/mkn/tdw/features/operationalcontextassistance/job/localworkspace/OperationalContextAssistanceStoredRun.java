package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;

import java.util.Set;

public record OperationalContextAssistanceStoredRun(
        OperationalContextAssistanceJobSnapshot snapshot,
        Set<String> requiredRepositoryScopeIds
) {
    public OperationalContextAssistanceStoredRun {
        requiredRepositoryScopeIds = Set.copyOf(requiredRepositoryScopeIds);
    }
}
