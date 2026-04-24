package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.List;
import java.util.Map;

record OperationalContextMatchBundle(
        List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches,
        List<OperationalContextMatchedEntry<Map<String, Object>>> integrationMatches,
        List<OperationalContextMatchedEntry<Map<String, Object>>> processMatches,
        List<OperationalContextMatchedEntry<Map<String, Object>>> repositoryMatches,
        List<OperationalContextMatchedEntry<Map<String, Object>>> boundedContextMatches,
        List<OperationalContextMatchedEntry<Map<String, Object>>> teamMatches,
        List<OperationalContextMatchedEntry<GlossaryTerm>> glossaryMatches,
        List<OperationalContextMatchedEntry<HandoffRule>> handoffMatches
) {
}
