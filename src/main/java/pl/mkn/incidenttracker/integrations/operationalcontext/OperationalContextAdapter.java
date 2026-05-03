package pl.mkn.incidenttracker.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.HandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.OpenQuestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;

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

        var teamsDocument = loadYamlDocument(resourceRoot, "teams.yml");
        var processesDocument = loadYamlDocument(resourceRoot, "processes.yml");
        var systemsDocument = loadYamlDocument(resourceRoot, "systems.yml");
        var integrationsDocument = loadYamlDocument(resourceRoot, "integrations.yml");
        var repositoriesDocument = loadYamlDocument(resourceRoot, "repo-map.yml");
        var boundedContextsDocument = loadYamlDocument(resourceRoot, "bounded-contexts.yml");

        var teams = mapList(teamsDocument.get("teams"));
        var processes = mapList(processesDocument.get("processes"));
        var systems = mapList(systemsDocument.get("systems"));
        var integrations = mapList(integrationsDocument.get("integrations"));
        var repositories = mapList(repositoriesDocument.get("repositories"));
        var boundedContexts = mapList(boundedContextsDocument.get("boundedContexts"));
        var glossaryDocument = readTextResource(resourceRoot, "glossary.md");
        var handoffRulesDocument = readTextResource(resourceRoot, "handoff-rules.md");
        var indexDocument = readTextResource(resourceRoot, "operational-context-index.md");

        var glossaryTerms = markdownParser.parseGlossary(glossaryDocument);
        var handoffRules = markdownParser.parseHandoffRules(handoffRulesDocument);
        var openQuestions = openQuestions(
                systemsDocument,
                repositoriesDocument,
                processesDocument,
                integrationsDocument,
                boundedContextsDocument,
                teamsDocument,
                glossaryDocument,
                handoffRulesDocument,
                systems,
                repositories,
                processes,
                integrations,
                boundedContexts,
                teams
        );

        log.info(
                "Operational context catalog loaded resourceRoot={} teams={} processes={} systems={} integrations={} repositories={} boundedContexts={} glossaryTerms={} handoffRules={} openQuestions={}",
                resourceRoot,
                teams.size(),
                processes.size(),
                systems.size(),
                integrations.size(),
                repositories.size(),
                boundedContexts.size(),
                glossaryTerms.size(),
                handoffRules.size(),
                openQuestions.size()
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
                openQuestions,
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
                catalog.openQuestions(),
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
            case "matchSignals" -> term.matchSignals();
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

    private Map<String, Object> loadYamlDocument(String resourceRoot, String fileName) {
        var resource = resource(resourceRoot, fileName);
        if (!resource.exists()) {
            log.warn("Operational context resource missing: {}", resource.getDescription());
            return Map.of();
        }

        var factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(resource);
        factoryBean.afterPropertiesSet();

        var document = factoryBean.getObject();
        if (document == null) {
            return Map.of();
        }

        return Map.copyOf(document);
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

    private List<OpenQuestion> openQuestions(
            Map<String, Object> systemsDocument,
            Map<String, Object> repositoriesDocument,
            Map<String, Object> processesDocument,
            Map<String, Object> integrationsDocument,
            Map<String, Object> boundedContextsDocument,
            Map<String, Object> teamsDocument,
            String glossaryDocument,
            String handoffRulesDocument,
            List<Map<String, Object>> systems,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> processes,
            List<Map<String, Object>> integrations,
            List<Map<String, Object>> boundedContexts,
            List<Map<String, Object>> teams
    ) {
        var questions = new ArrayList<OpenQuestion>();
        addYamlOpenQuestions(questions, "systems.yml", "system", null, systemsDocument.get("openQuestions"));
        addEntityOpenQuestions(questions, "systems.yml", "system", systems);
        addYamlOpenQuestions(questions, "repo-map.yml", "repository", null, repositoriesDocument.get("openQuestions"));
        addEntityOpenQuestions(questions, "repo-map.yml", "repository", repositories);
        addYamlOpenQuestions(questions, "processes.yml", "process", null, processesDocument.get("openQuestions"));
        addEntityOpenQuestions(questions, "processes.yml", "process", processes);
        addYamlOpenQuestions(questions, "integrations.yml", "integration", null, integrationsDocument.get("openQuestions"));
        addEntityOpenQuestions(questions, "integrations.yml", "integration", integrations);
        addYamlOpenQuestions(questions, "bounded-contexts.yml", "bounded-context", null, boundedContextsDocument.get("openQuestions"));
        addEntityOpenQuestions(questions, "bounded-contexts.yml", "bounded-context", boundedContexts);
        addYamlOpenQuestions(questions, "teams.yml", "team", null, teamsDocument.get("openQuestions"));
        addEntityOpenQuestions(questions, "teams.yml", "team", teams);
        addMarkdownOpenQuestions(questions, "glossary.md", glossaryDocument);
        addMarkdownOpenQuestions(questions, "handoff-rules.md", handoffRulesDocument);
        return List.copyOf(questions);
    }

    private void addEntityOpenQuestions(
            List<OpenQuestion> questions,
            String sourceFile,
            String entityType,
            List<Map<String, Object>> entries
    ) {
        for (var entry : entries) {
            var entityId = text(entry, "id");
            addYamlOpenQuestions(questions, sourceFile, entityType, entityId, entry.get("openQuestions"));
        }
    }

    private void addYamlOpenQuestions(
            List<OpenQuestion> questions,
            String sourceFile,
            String entityType,
            String entityId,
            Object source
    ) {
        var entries = source instanceof Iterable<?> iterable ? iterable : List.of();
        var index = 0;
        for (var item : entries) {
            var question = "";
            var severity = "";
            var status = "open";
            if (item instanceof Map<?, ?> map) {
                question = text(map.get("question"));
                severity = text(map.get("severity"));
                status = text(map.get("status"));
            } else {
                question = text(item);
            }

            if (!isActionableOpenQuestion(question)) {
                index++;
                continue;
            }

            questions.add(new OpenQuestion(
                    openQuestionId(sourceFile, entityType, entityId, question, index),
                    sourceFile,
                    entityType,
                    entityId,
                    question,
                    StringUtils.hasText(severity) ? severity : inferSeverity(question),
                    StringUtils.hasText(status) ? status : "open"
            ));
            index++;
        }
    }

    private void addMarkdownOpenQuestions(List<OpenQuestion> questions, String sourceFile, String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return;
        }

        var inOpenQuestions = false;
        var index = 0;
        for (var line : markdown.split("\\R")) {
            var trimmed = line.trim();
            if (trimmed.startsWith("## Open Questions")) {
                inOpenQuestions = true;
                continue;
            }
            if (inOpenQuestions && trimmed.startsWith("## ") && !trimmed.startsWith("## Open Questions")) {
                break;
            }
            if (!inOpenQuestions || !trimmed.startsWith("- ")) {
                continue;
            }

            var question = trimmed.substring(2).trim();
            if (!isActionableOpenQuestion(question)) {
                index++;
                continue;
            }

            questions.add(new OpenQuestion(
                    openQuestionId(sourceFile, "", null, question, index),
                    sourceFile,
                    "",
                    null,
                    question.replace("`", ""),
                    inferSeverity(question),
                    "open"
            ));
            index++;
        }
    }

    private boolean isActionableOpenQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }

        var normalized = question.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("none")
                && !normalized.equals("n/a")
                && !normalized.equals("todo")
                && !normalized.equals("-");
    }

    private String inferSeverity(String question) {
        var normalized = question != null ? question.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("block") || normalized.contains("critical") || normalized.contains("error")) {
            return "error";
        }
        if (normalized.contains("owner") || normalized.contains("handoff") || normalized.contains("missing")) {
            return "warning";
        }
        return "info";
    }

    private String openQuestionId(
            String sourceFile,
            String entityType,
            String entityId,
            String question,
            int index
    ) {
        var seed = String.join(":",
                sourceFile,
                entityType != null ? entityType : "",
                entityId != null ? entityId : "",
                Integer.toString(index),
                question != null ? question : ""
        );
        return "open-question-" + slug(seed);
    }

    private String slug(String value) {
        var normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.length() > 72) {
            return normalized.substring(0, 72).replaceAll("-$", "");
        }
        return normalized;
    }
}
