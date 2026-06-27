package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.textList;

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
        var codeSearchScopesDocument = loadYamlDocument(resourceRoot, "code-search-scopes.yml");
        var boundedContextsDocument = loadYamlDocument(resourceRoot, "bounded-contexts.yml");

        var rawTeams = mapList(teamsDocument.get("teams"));
        var rawProcesses = mapList(processesDocument.get("processes"));
        var rawSystems = mapList(systemsDocument.get("systems"));
        var rawIntegrations = mapList(integrationsDocument.get("integrations"));
        var rawRepositories = mapList(repositoriesDocument.get("repositories"));
        var rawCodeSearchScopes = mapList(codeSearchScopesDocument.get("codeSearchScopes"));
        var rawBoundedContexts = mapList(boundedContextsDocument.get("boundedContexts"));
        var teams = rawTeams.stream().map(OperationalContextDtos::team).toList();
        var processes = rawProcesses.stream().map(OperationalContextDtos::process).toList();
        var systems = rawSystems.stream().map(OperationalContextDtos::system).toList();
        var integrations = rawIntegrations.stream().map(OperationalContextDtos::integration).toList();
        var repositories = rawRepositories.stream().map(OperationalContextDtos::repository).toList();
        var codeSearchScopes = rawCodeSearchScopes.stream()
                .map(OperationalContextDtos::repositorySearchScope)
                .toList();
        var boundedContexts = rawBoundedContexts.stream().map(OperationalContextDtos::boundedContext).toList();
        var glossaryDocument = readTextResource(resourceRoot, "glossary.md");
        var handoffRulesDocument = readTextResource(resourceRoot, "handoff-rules.md");
        var indexDocument = readTextResource(resourceRoot, "operational-context-index.md");

        var glossaryTerms = markdownParser.parseGlossary(glossaryDocument);
        var handoffRules = markdownParser.parseHandoffRules(handoffRulesDocument);
        var openQuestions = openQuestions(
                systemsDocument,
                repositoriesDocument,
                codeSearchScopesDocument,
                processesDocument,
                integrationsDocument,
                boundedContextsDocument,
                teamsDocument,
                glossaryDocument,
                handoffRulesDocument,
                rawSystems,
                rawRepositories,
                rawCodeSearchScopes,
                rawProcesses,
                rawIntegrations,
                rawBoundedContexts,
                rawTeams
        );

        log.info(
                "Operational context catalog loaded resourceRoot={} teams={} processes={} systems={} integrations={} repositories={} codeSearchScopes={} boundedContexts={} glossaryTerms={} handoffRules={} openQuestions={}",
                resourceRoot,
                teams.size(),
                processes.size(),
                systems.size(),
                integrations.size(),
                repositories.size(),
                codeSearchScopes.size(),
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
                codeSearchScopes,
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
                filterEntries(catalog.teams(), query, OperationalContextEntryType.TEAM),
                filterEntries(catalog.processes(), query, OperationalContextEntryType.PROCESS),
                filterEntries(catalog.systems(), query, OperationalContextEntryType.SYSTEM),
                filterEntries(catalog.integrations(), query, OperationalContextEntryType.INTEGRATION),
                filterEntries(catalog.repositories(), query, OperationalContextEntryType.REPOSITORY),
                catalog.codeSearchScopes(),
                filterEntries(catalog.boundedContexts(), query, OperationalContextEntryType.BOUNDED_CONTEXT),
                filterGlossaryTerms(catalog.glossaryTerms(), query),
                filterHandoffRules(catalog.handoffRules(), query),
                catalog.openQuestions(),
                query.includeIndexDocument() ? catalog.indexDocument() : ""
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
                .filter(entry -> matchesAll(entry, filters))
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
                .filter(term -> matchesAll(term, filters))
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
                .filter(rule -> matchesAll(rule, filters))
                .toList();
    }

    private boolean matchesAll(OperationalContextEntry entry, List<OperationalContextFilter> filters) {
        return filters.stream().allMatch(filter -> matchesAnyValue(entry.values(filter.path()), filter));
    }

    private boolean matchesAll(OperationalContextGlossaryTerm term, List<OperationalContextFilter> filters) {
        return filters.stream()
                .allMatch(filter -> matchesAnyValue(glossaryTermValues(term, filter.path()), filter));
    }

    private boolean matchesAll(OperationalContextHandoffRule rule, List<OperationalContextFilter> filters) {
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

    private List<String> handoffRuleValues(OperationalContextHandoffRule rule, String path) {
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

    private List<OperationalContextOpenQuestion> openQuestions(
            Map<String, Object> systemsDocument,
            Map<String, Object> repositoriesDocument,
            Map<String, Object> codeSearchScopesDocument,
            Map<String, Object> processesDocument,
            Map<String, Object> integrationsDocument,
            Map<String, Object> boundedContextsDocument,
            Map<String, Object> teamsDocument,
            String glossaryDocument,
            String handoffRulesDocument,
            List<Map<String, Object>> systems,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> codeSearchScopes,
            List<Map<String, Object>> processes,
            List<Map<String, Object>> integrations,
            List<Map<String, Object>> boundedContexts,
            List<Map<String, Object>> teams
    ) {
        var questions = new ArrayList<OperationalContextOpenQuestion>();
        addYamlGaps(questions, "systems.yml", "system", null, systemsDocument.get("gaps"));
        addEntityGaps(questions, "systems.yml", "system", systems);
        addYamlGaps(questions, "repo-map.yml", "repository", null, repositoriesDocument.get("gaps"));
        addEntityGaps(questions, "repo-map.yml", "repository", repositories);
        addYamlGaps(questions, "code-search-scopes.yml", "code-search-scope", null, codeSearchScopesDocument.get("gaps"));
        addEntityGaps(questions, "code-search-scopes.yml", "code-search-scope", codeSearchScopes);
        addYamlGaps(questions, "processes.yml", "process", null, processesDocument.get("gaps"));
        addEntityGaps(questions, "processes.yml", "process", processes);
        addYamlGaps(questions, "integrations.yml", "integration", null, integrationsDocument.get("gaps"));
        addEntityGaps(questions, "integrations.yml", "integration", integrations);
        addYamlGaps(questions, "bounded-contexts.yml", "bounded-context", null, boundedContextsDocument.get("gaps"));
        addEntityGaps(questions, "bounded-contexts.yml", "bounded-context", boundedContexts);
        addYamlGaps(questions, "teams.yml", "team", null, teamsDocument.get("gaps"));
        addEntityGaps(questions, "teams.yml", "team", teams);
        addMarkdownGaps(questions, "glossary.md", glossaryDocument);
        addMarkdownGaps(questions, "handoff-rules.md", handoffRulesDocument);
        return List.copyOf(questions);
    }

    private void addEntityGaps(
            List<OperationalContextOpenQuestion> questions,
            String sourceFile,
            String entityType,
            List<Map<String, Object>> entries
    ) {
        for (var entry : entries) {
            var entityId = text(entry, "id");
            addYamlGaps(questions, sourceFile, entityType, entityId, entry.get("gaps"));
        }
    }

    private void addYamlGaps(
            List<OperationalContextOpenQuestion> questions,
            String sourceFile,
            String entityType,
            String entityId,
            Object source
    ) {
        var entries = mapList(source);
        var index = 0;
        for (var item : entries) {
            var question = firstNonBlank(
                    text(item, "question"),
                    text(item, "summary"),
                    text(item, "description"),
                    text(item, "impact")
            );
            if (!isActionableGap(question)) {
                index++;
                continue;
            }

            var effectiveEntityType = firstNonBlank(text(item, "entityType"), text(item, "targetType"), entityType);
            var effectiveEntityId = firstNonBlank(text(item, "entityId"), text(item, "targetId"), entityId);
            questions.add(new OperationalContextOpenQuestion(
                    openQuestionId(sourceFile, effectiveEntityType, effectiveEntityId, question, index),
                    sourceFile,
                    effectiveEntityType,
                    effectiveEntityId,
                    question,
                    firstNonBlank(text(item, "severity"), inferSeverity(question)),
                    firstNonBlank(text(item, "status"), "open")
            ));
            index++;
        }
    }

    private void addMarkdownGaps(List<OperationalContextOpenQuestion> questions, String sourceFile, String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return;
        }

        var inGaps = false;
        var currentId = "";
        var currentFields = new java.util.LinkedHashMap<String, String>();
        String currentSection = null;
        var index = 0;
        for (var line : markdown.split("\\R")) {
            var trimmed = line.trim();
            if (trimmed.startsWith("## Gaps")) {
                inGaps = true;
                continue;
            }
            if (inGaps && trimmed.startsWith("## ") && !trimmed.startsWith("## Gaps")) {
                break;
            }
            if (!inGaps) {
                continue;
            }

            if (trimmed.startsWith("### ")) {
                index = addMarkdownGap(questions, sourceFile, currentId, currentFields, index);
                currentId = stripMarkdown(trimmed.substring(4));
                currentFields = new java.util.LinkedHashMap<>();
                currentSection = null;
                continue;
            }

            if (!StringUtils.hasText(currentId)) {
                continue;
            }

            var field = parseMarkdownField(trimmed);
            if (field != null) {
                currentFields.put(field.label(), field.value());
                currentSection = StringUtils.hasText(field.value()) ? null : field.label();
                continue;
            }

            if (currentSection != null && StringUtils.hasText(trimmed) && !trimmed.startsWith("- ")) {
                appendMarkdownField(currentFields, currentSection, stripMarkdown(trimmed));
            }
        }
        addMarkdownGap(questions, sourceFile, currentId, currentFields, index);
    }

    private int addMarkdownGap(
            List<OperationalContextOpenQuestion> questions,
            String sourceFile,
            String currentId,
            Map<String, String> fields,
            int index
    ) {
        if (!StringUtils.hasText(currentId)) {
            return index;
        }

        var question = firstNonBlank(
                fields.get("question"),
                fields.get("summary"),
                fields.get("description"),
                fields.get("impact")
        );
        if (!isActionableGap(question)) {
            return index + 1;
        }

        questions.add(new OperationalContextOpenQuestion(
                openQuestionId(sourceFile, "", null, question, index),
                sourceFile,
                "",
                null,
                question,
                firstNonBlank(fields.get("severity"), inferSeverity(question)),
                firstNonBlank(fields.get("status"), "open")
        ));
        return index + 1;
    }

    private MarkdownField parseMarkdownField(String value) {
        if (!value.startsWith("**")) {
            return null;
        }

        var colonInsideBold = value.indexOf(":**", 2);
        if (colonInsideBold > 1) {
            return new MarkdownField(
                    normalize(value.substring(2, colonInsideBold)),
                    stripMarkdown(value.substring(colonInsideBold + 3))
            );
        }

        var colonOutsideBold = value.indexOf("**:", 2);
        if (colonOutsideBold > 1) {
            return new MarkdownField(
                    normalize(value.substring(2, colonOutsideBold)),
                    stripMarkdown(value.substring(colonOutsideBold + 3))
            );
        }

        if (value.endsWith("**")) {
            return new MarkdownField(normalize(value.substring(2, value.length() - 2)), "");
        }

        return null;
    }

    private void appendMarkdownField(Map<String, String> fields, String field, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        var existing = fields.get(field);
        fields.put(field, StringUtils.hasText(existing) ? existing + " " + value : value);
    }

    private String stripMarkdown(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.trim().replace("`", "");
    }

    private boolean isActionableGap(String question) {
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

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
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

    private record MarkdownField(String label, String value) {
    }
}
