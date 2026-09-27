package pl.mkn.tdw.integrations.operationalcontext.service;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogSearchPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogSearchMatch;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.*;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Searchable catalog values shared by the operator API and the agent tool. */
@Component
public final class OperationalContextCatalogSearch implements OperationalContextCatalogSearchPort {

    @Override
    public List<OperationalContextCatalogSearchMatch> find(OperationalContextCatalog catalog, String query) {
        return search(catalog, query);
    }

    public static List<OperationalContextCatalogSearchMatch> search(OperationalContextCatalog catalog, String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        var normalizedQuery = normalize(query);
        var tokens = Arrays.stream(normalizedQuery.split("\\s+"))
                .filter(StringUtils::hasText).distinct().toList();
        var matches = new ArrayList<OperationalContextCatalogSearchMatch>();
        for (var document : documents(catalog)) {
            var fields = new LinkedHashSet<String>();
            var signals = new LinkedHashSet<String>();
            var score = 0;
            score = Math.max(score, score("identity", document.identity(), normalizedQuery, tokens, fields, signals, 100, 90, 75));
            score = Math.max(score, score("signals", document.signals(), normalizedQuery, tokens, fields, signals, 70, 65, 55));
            score = Math.max(score, score("summary", document.summaryValues(), normalizedQuery, tokens, fields, signals, 50, 45, 35));
            score = Math.max(score, score("relations", document.relations(), normalizedQuery, tokens, fields, signals, 40, 35, 25));
            if (score > 0) {
                matches.add(new OperationalContextCatalogSearchMatch(document.type(), document.id(), score, List.copyOf(fields),
                        signals.stream().limit(8).toList(),
                        "Matched %s for %s:%s.".formatted(String.join(", ", fields), document.type(), document.id())));
            }
        }
        return List.copyOf(matches);
    }

    private static List<Document> documents(OperationalContextCatalog catalog) {
        var safe = catalog != null ? catalog : OperationalContextCatalog.empty();
        var documents = new ArrayList<Document>();
        addEntries(documents, "system", safe.systems());
        addEntries(documents, "repository", safe.repositories());
        for (var scope : safe.codeSearchScopes()) {
            var signals = new ArrayList<String>();
            signals.addAll(scope.useFor());
            signals.add(scope.scopeType());
            signals.addAll(scope.limitations());
            for (var repository : scope.repositories()) {
                signals.addAll(values(repository.repoId(), repository.role(), repository.reason(), repository.searchMode()));
                signals.addAll(repository.readFor());
                signals.addAll(repository.pathPrefixes());
            }
            documents.add(new Document("codeSearchScope", scope.id(), values(scope.id(), scope.name()),
                    signals, values(scope.summary()), values(scope.target().type(), scope.target().id())));
        }
        addEntries(documents, "process", safe.processes());
        addEntries(documents, "integration", safe.integrations());
        addEntries(documents, "boundedContext", safe.boundedContexts());
        addEntries(documents, "team", safe.teams());
        for (var term : safe.glossaryTerms()) {
            documents.add(new Document("glossaryTerm", term.id(), values(term.id(), term.term()),
                    join(term.synonyms(), term.matchSignals(), term.useInContext()),
                    values(term.definition()), term.canonicalReferences()));
        }
        for (var rule : safe.handoffRules()) {
            documents.add(new Document("handoffRule", rule.id(), values(rule.id(), rule.title()),
                    join(rule.useWhen(), rule.requiredEvidence(), rule.expectedFirstAction()),
                    join(rule.doNotUseWhen(), rule.notes()), references(rule.references())));
        }
        return documents;
    }

    private static void addEntries(List<Document> documents, String type, List<? extends OperationalContextEntry> entries) {
        for (var entry : entries) {
            documents.add(new Document(type, entry.id(), join(values(entry.id(), entry.label()), entry.aliases()),
                    join(entry.useFor(), entry.matchSignals().allValues(), entry.genericSignals()),
                    values(entry.summary(), entry.purpose()),
                    join(references(entry.references()), entry.relations().stream()
                            .flatMap(relation -> join(values(relation.targetType(), relation.target(), relation.evidence()),
                                    relation.via()).stream())
                            .toList())));
        }
    }

    private static List<String> references(OperationalContextReferences references) {
        if (references == null) {
            return List.of();
        }
        return join(references.systems(), references.repositories(), references.processes(),
                references.boundedContexts(), references.integrations(), references.terms(),
                references.teams(), references.handoffRules());
    }

    @SafeVarargs
    private static List<String> join(List<String>... lists) {
        var values = new LinkedHashSet<String>();
        for (var list : lists) {
            if (list != null) {
                list.stream().filter(StringUtils::hasText).forEach(values::add);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> values(String... values) {
        return Arrays.stream(values).filter(StringUtils::hasText).toList();
    }

    private static int score(String field, List<String> values, String query, List<String> tokens,
                             LinkedHashSet<String> fields, LinkedHashSet<String> signals,
                             int exact, int contains, int token) {
        var score = 0;
        for (var value : values) {
            var normalized = normalize(value);
            if (normalized.equals(query)) {
                score = Math.max(score, exact);
            } else if (normalized.contains(query)) {
                score = Math.max(score, contains);
            } else if (!tokens.isEmpty() && tokens.stream().allMatch(normalized::contains)) {
                score = Math.max(score, token);
            } else {
                continue;
            }
            fields.add(field);
            signals.add(value);
        }
        return score;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private record Document(String type, String id, List<String> identity, List<String> signals,
                            List<String> summaryValues, List<String> relations) {
    }

}
