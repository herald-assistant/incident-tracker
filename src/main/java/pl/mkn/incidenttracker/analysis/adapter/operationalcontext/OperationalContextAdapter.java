package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.textList;

@Component
@Slf4j
@RequiredArgsConstructor
public class OperationalContextAdapter implements OperationalContextPort {

    private final OperationalContextProperties properties;
    private final OperationalContextMarkdownParser markdownParser = new OperationalContextMarkdownParser();

    private volatile OperationalContextCatalog cachedCatalog;

    @Override
    public OperationalContextCatalog loadContext(OperationalContextQuery query) {
        var effectiveQuery = query != null ? query : OperationalContextQuery.all();
        var catalog = loadCatalog();
        return effectiveQuery.isUnfiltered() ? catalog : filterCatalog(catalog, effectiveQuery);
    }

    private OperationalContextCatalog loadCatalog() {
        var catalog = cachedCatalog;
        if (catalog != null) {
            return catalog;
        }

        synchronized (this) {
            if (cachedCatalog == null) {
                cachedCatalog = buildCatalog();
            }

            return cachedCatalog;
        }
    }

    private OperationalContextCatalog buildCatalog() {
        var resourceRoot = normalizeRoot(properties.getResourceRoot());

        var teams = loadYamlEntries(resourceRoot, "teams.yml", "teams");
        var processes = loadYamlEntries(resourceRoot, "processes.yml", "processes");
        var systems = loadYamlEntries(resourceRoot, "systems.yml", "systems");
        var integrations = loadYamlEntries(resourceRoot, "integrations.yml", "integrations");
        var repositories = loadYamlEntries(resourceRoot, "repo-map.yml", "repositories");
        var boundedContexts = loadYamlEntries(resourceRoot, "bounded-contexts.yml", "boundedContexts");
        var glossaryDocument = readTextResource(resourceRoot, "glossary.md");
        var handoffRulesDocument = readTextResource(resourceRoot, "handoff-rules.md");
        var indexDocument = readTextResource(resourceRoot, "operational-context-index.md");

        var glossaryTerms = markdownParser.parseGlossary(glossaryDocument);
        var handoffRules = markdownParser.parseHandoffRules(handoffRulesDocument);

        log.info(
                "Operational context catalog loaded resourceRoot={} teams={} processes={} systems={} integrations={} repositories={} boundedContexts={} glossaryTerms={} handoffRules={}",
                resourceRoot,
                teams.size(),
                processes.size(),
                systems.size(),
                integrations.size(),
                repositories.size(),
                boundedContexts.size(),
                glossaryTerms.size(),
                handoffRules.size()
        );

        return new OperationalContextCatalog(
                teams,
                processes,
                systems,
                integrations,
                repositories,
                boundedContexts,
                glossaryTerms,
                handoffRules,
                indexDocument
        );
    }

    private OperationalContextCatalog filterCatalog(
            OperationalContextCatalog catalog,
            OperationalContextQuery query
    ) {
        return new OperationalContextCatalog(
                filterMapEntries(catalog.teams(), query, OperationalContextEntryType.TEAM),
                filterMapEntries(catalog.processes(), query, OperationalContextEntryType.PROCESS),
                filterMapEntries(catalog.systems(), query, OperationalContextEntryType.SYSTEM),
                filterMapEntries(catalog.integrations(), query, OperationalContextEntryType.INTEGRATION),
                filterMapEntries(catalog.repositories(), query, OperationalContextEntryType.REPOSITORY),
                filterMapEntries(catalog.boundedContexts(), query, OperationalContextEntryType.BOUNDED_CONTEXT),
                filterGlossaryTerms(catalog.glossaryTerms(), query),
                filterHandoffRules(catalog.handoffRules(), query),
                query.includeIndexDocument() ? catalog.indexDocument() : ""
        );
    }

    private List<Map<String, Object>> filterMapEntries(
            List<Map<String, Object>> entries,
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
                .filter(entry -> matchesAll(entry, filters))
                .toList();
    }

    private List<GlossaryTerm> filterGlossaryTerms(
            List<GlossaryTerm> terms,
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
                .filter(term -> matchesAll(term, filters))
                .toList();
    }

    private List<HandoffRule> filterHandoffRules(
            List<HandoffRule> rules,
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
                .filter(rule -> matchesAll(rule, filters))
                .toList();
    }

    private boolean matchesAll(Map<String, Object> entry, List<OperationalContextFilter> filters) {
        return filters.stream().allMatch(filter -> matchesAnyValue(textList(entry, filter.path()), filter));
    }

    private boolean matchesAll(GlossaryTerm term, List<OperationalContextFilter> filters) {
        return filters.stream()
                .allMatch(filter -> matchesAnyValue(glossaryTermValues(term, filter.path()), filter));
    }

    private boolean matchesAll(HandoffRule rule, List<OperationalContextFilter> filters) {
        return filters.stream()
                .allMatch(filter -> matchesAnyValue(handoffRuleValues(rule, filter.path()), filter));
    }

    private boolean matchesAnyValue(List<String> candidateValues, OperationalContextFilter filter) {
        if (candidateValues.isEmpty()) {
            return false;
        }

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

    private List<String> glossaryTermValues(GlossaryTerm term, String path) {
        return switch (path) {
            case "id" -> List.of(term.id());
            case "term" -> List.of(term.term());
            case "category" -> List.of(term.category());
            case "definition" -> List.of(term.definition());
            case "useInContext" -> term.useInContext();
            case "doNotConfuseWith" -> term.doNotConfuseWith();
            case "typicalEvidenceSignals" -> term.typicalEvidenceSignals();
            case "canonicalReferences" -> term.canonicalReferences();
            case "synonyms" -> term.synonyms();
            case "notes" -> term.notes();
            default -> List.of();
        };
    }

    private List<String> handoffRuleValues(HandoffRule rule, String path) {
        return switch (path) {
            case "id" -> List.of(rule.id());
            case "title" -> List.of(rule.title());
            case "routeTo" -> List.of(rule.routeTo());
            case "useWhen" -> rule.useWhen();
            case "doNotUseWhen" -> rule.doNotUseWhen();
            case "requiredEvidence" -> rule.requiredEvidence();
            case "expectedFirstAction" -> rule.expectedFirstAction();
            case "partnerTeams" -> rule.partnerTeams();
            case "notes" -> rule.notes();
            default -> List.of();
        };
    }

    private List<Map<String, Object>> loadYamlEntries(String resourceRoot, String fileName, String listKey) {
        var resource = resource(resourceRoot, fileName);
        if (!resource.exists()) {
            log.warn("Operational context resource missing: {}", resource.getDescription());
            return List.of();
        }

        var factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(resource);
        factoryBean.afterPropertiesSet();

        var document = factoryBean.getObject();
        if (document == null) {
            return List.of();
        }

        return mapList(document.get(listKey));
    }

    private String readTextResource(String resourceRoot, String fileName) {
        var resource = resource(resourceRoot, fileName);
        if (!resource.exists()) {
            log.warn("Operational context text resource missing: {}", resource.getDescription());
            return "";
        }

        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read operational context resource: " + resource.getDescription(), exception);
        }
    }

    private Resource resource(String resourceRoot, String fileName) {
        return new ClassPathResource(resourceRoot + "/" + fileName);
    }

    private String normalizeRoot(String resourceRoot) {
        if (!StringUtils.hasText(resourceRoot)) {
            return "operational-context";
        }

        var normalized = resourceRoot.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
