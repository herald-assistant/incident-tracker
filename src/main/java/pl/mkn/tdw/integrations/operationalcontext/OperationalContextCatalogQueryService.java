package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;

import java.util.List;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.normalize;

@Component
final class OperationalContextCatalogQueryService {

    OperationalContextCatalog query(OperationalContextCatalog catalog, OperationalContextQuery query) {
        var effectiveQuery = query != null ? query : OperationalContextQuery.all();
        if (effectiveQuery.isUnfiltered()) {
            return catalog;
        }

        return new OperationalContextCatalog(
                filterEntries(catalog.teams(), effectiveQuery, OperationalContextEntryType.TEAM),
                filterEntries(catalog.processes(), effectiveQuery, OperationalContextEntryType.PROCESS),
                filterEntries(catalog.systems(), effectiveQuery, OperationalContextEntryType.SYSTEM),
                filterEntries(catalog.integrations(), effectiveQuery, OperationalContextEntryType.INTEGRATION),
                filterEntries(catalog.repositories(), effectiveQuery, OperationalContextEntryType.REPOSITORY),
                filterCodeSearchScopes(catalog.codeSearchScopes(), effectiveQuery),
                filterEntries(catalog.boundedContexts(), effectiveQuery, OperationalContextEntryType.BOUNDED_CONTEXT),
                filterGlossaryTerms(catalog.glossaryTerms(), effectiveQuery),
                filterHandoffRules(catalog.handoffRules(), effectiveQuery),
                catalog.openQuestions(),
                effectiveQuery.includeIndexDocument() ? catalog.indexDocument() : ""
        );
    }

    private <T extends OperationalContextEntry> List<T> filterEntries(
            List<T> entries,
            OperationalContextQuery query,
            OperationalContextEntryType entryType
    ) {
        if (!query.includes(entryType)) {
            return List.of();
        }

        var filters = query.filtersFor(entryType);
        if (filters.isEmpty()) {
            return entries;
        }

        return entries.stream()
                .filter(entry -> filters.stream()
                        .allMatch(filter -> matchesAnyValue(entry.values(filter.path()), filter)))
                .toList();
    }

    private List<OperationalContextRepositorySearchScope> filterCodeSearchScopes(
            List<OperationalContextRepositorySearchScope> scopes,
            OperationalContextQuery query
    ) {
        if (!query.includes(OperationalContextEntryType.CODE_SEARCH_SCOPE)) {
            return List.of();
        }

        var filters = query.filtersFor(OperationalContextEntryType.CODE_SEARCH_SCOPE);
        if (filters.isEmpty()) {
            return scopes;
        }

        return scopes.stream()
                .filter(scope -> filters.stream()
                        .allMatch(filter -> matchesAnyValue(codeSearchScopeValues(scope, filter.path()), filter)))
                .toList();
    }

    private List<OperationalContextGlossaryTerm> filterGlossaryTerms(
            List<OperationalContextGlossaryTerm> terms,
            OperationalContextQuery query
    ) {
        if (!query.includes(OperationalContextEntryType.GLOSSARY_TERM)) {
            return List.of();
        }

        var filters = query.filtersFor(OperationalContextEntryType.GLOSSARY_TERM);
        if (filters.isEmpty()) {
            return terms;
        }

        return terms.stream()
                .filter(term -> filters.stream()
                        .allMatch(filter -> matchesAnyValue(glossaryTermValues(term, filter.path()), filter)))
                .toList();
    }

    private List<OperationalContextHandoffRule> filterHandoffRules(
            List<OperationalContextHandoffRule> rules,
            OperationalContextQuery query
    ) {
        if (!query.includes(OperationalContextEntryType.HANDOFF_RULE)) {
            return List.of();
        }

        var filters = query.filtersFor(OperationalContextEntryType.HANDOFF_RULE);
        if (filters.isEmpty()) {
            return rules;
        }

        return rules.stream()
                .filter(rule -> filters.stream()
                        .allMatch(filter -> matchesAnyValue(handoffRuleValues(rule, filter.path()), filter)))
                .toList();
    }

    private boolean matchesAnyValue(List<String> candidateValues, OperationalContextFilter filter) {
        for (var candidateValue : candidateValues) {
            var normalizedCandidate = normalize(candidateValue);
            if (!StringUtils.hasText(normalizedCandidate)) {
                continue;
            }

            for (var filterValue : filter.values()) {
                var normalizedFilterValue = normalize(filterValue);
                if (!StringUtils.hasText(normalizedFilterValue)) {
                    continue;
                }
                if (filter.mode() == OperationalContextFilterMode.EXACT
                        && normalizedCandidate.equals(normalizedFilterValue)) {
                    return true;
                }
                if (filter.mode() == OperationalContextFilterMode.CONTAINS
                        && normalizedCandidate.contains(normalizedFilterValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> glossaryTermValues(OperationalContextGlossaryTerm term, String path) {
        return switch (path) {
            case "id" -> List.of(term.id());
            case "term" -> List.of(term.term());
            case "category" -> List.of(term.category());
            case "definition" -> List.of(term.definition());
            case "useInContext" -> term.useInContext();
            case "doNotConfuseWith" -> term.doNotConfuseWith();
            case "matchSignals" -> term.matchSignals();
            case "canonicalReferences" -> term.canonicalReferences();
            case "synonyms" -> term.synonyms();
            case "notes" -> term.notes();
            default -> List.of();
        };
    }

    private List<String> codeSearchScopeValues(OperationalContextRepositorySearchScope scope, String path) {
        return switch (path) {
            case "id" -> List.of(scope.id());
            case "name" -> List.of(scope.name());
            case "scopeType" -> List.of(scope.scopeType());
            case "target.type" -> List.of(scope.target().type());
            case "target.id" -> List.of(scope.target().id());
            case "repositories.repoId" -> scope.repositories().stream()
                    .map(OperationalContextDtos.OperationalContextRepositorySearchRepository::repoId)
                    .toList();
            default -> List.of();
        };
    }

    private List<String> handoffRuleValues(OperationalContextHandoffRule rule, String path) {
        return switch (path) {
            case "id" -> List.of(rule.id());
            case "title" -> List.of(rule.title());
            case "useWhen" -> rule.useWhen();
            case "doNotUseWhen" -> rule.doNotUseWhen();
            case "requiredEvidence" -> rule.requiredEvidence();
            case "expectedFirstAction" -> rule.expectedFirstAction();
            case "notes" -> rule.notes();
            default -> List.of();
        };
    }
}
