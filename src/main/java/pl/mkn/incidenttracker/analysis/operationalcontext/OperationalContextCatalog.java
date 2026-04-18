package pl.mkn.incidenttracker.analysis.operationalcontext;

import java.util.List;
import java.util.Map;

public record OperationalContextCatalog(
        List<Map<String, Object>> teams,
        List<Map<String, Object>> processes,
        List<Map<String, Object>> systems,
        List<Map<String, Object>> integrations,
        List<Map<String, Object>> repositories,
        List<Map<String, Object>> boundedContexts,
        List<GlossaryTerm> glossaryTerms,
        List<HandoffRule> handoffRules,
        String indexDocument
) {

    public record GlossaryTerm(
            String id,
            String term,
            String category,
            String definition,
            List<String> useInContext,
            List<String> doNotConfuseWith,
            List<String> typicalEvidenceSignals,
            List<String> canonicalReferences,
            List<String> synonyms,
            List<String> notes
    ) {
    }

    public record HandoffRule(
            String id,
            String title,
            String routeTo,
            List<String> useWhen,
            List<String> doNotUseWhen,
            List<String> requiredEvidence,
            List<String> expectedFirstAction,
            List<String> partnerTeams,
            List<String> notes
    ) {
    }

}
