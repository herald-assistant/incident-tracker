package pl.mkn.incidenttracker.integrations.operationalcontext;

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
        List<OpenQuestion> openQuestions,
        String indexDocument
) {

    public OperationalContextCatalog(
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
        this(
                teams,
                processes,
                systems,
                integrations,
                repositories,
                boundedContexts,
                glossaryTerms,
                handoffRules,
                List.of(),
                indexDocument
        );
    }

    public record GlossaryTerm(
            String id,
            String term,
            String category,
            String definition,
            List<String> useInContext,
            List<String> doNotConfuseWith,
            List<String> matchSignals,
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

    public record OpenQuestion(
            String id,
            String sourceFile,
            String entityType,
            String entityId,
            String question,
            String severity,
            String status
    ) {
    }

}
