package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.text;

@Component
final class OperationalContextCatalogCodec {

    private static final String TEAMS = "teams.yml";
    private static final String PROCESSES = "processes.yml";
    private static final String SYSTEMS = "systems.yml";
    private static final String INTEGRATIONS = "integrations.yml";
    private static final String REPOSITORIES = "repo-map.yml";
    private static final String CODE_SEARCH_SCOPES = "code-search-scopes.yml";
    private static final String BOUNDED_CONTEXTS = "bounded-contexts.yml";
    private static final String GLOSSARY = "glossary.yml";
    private static final String HANDOFF_RULES = "handoff-rules.yml";
    private static final String INDEX = "operational-context-index.md";

    OperationalContextCatalogDecodeResult decode(OperationalContextRawDocuments rawDocuments) {
        var documents = new LinkedHashMap<String, Map<String, Object>>();
        documents.put(TEAMS, yamlDocument(rawDocuments.content(TEAMS), TEAMS));
        documents.put(PROCESSES, yamlDocument(rawDocuments.content(PROCESSES), PROCESSES));
        documents.put(SYSTEMS, yamlDocument(rawDocuments.content(SYSTEMS), SYSTEMS));
        documents.put(INTEGRATIONS, yamlDocument(rawDocuments.content(INTEGRATIONS), INTEGRATIONS));
        documents.put(REPOSITORIES, yamlDocument(rawDocuments.content(REPOSITORIES), REPOSITORIES));
        documents.put(CODE_SEARCH_SCOPES, yamlDocument(rawDocuments.content(CODE_SEARCH_SCOPES), CODE_SEARCH_SCOPES));
        documents.put(BOUNDED_CONTEXTS, yamlDocument(rawDocuments.content(BOUNDED_CONTEXTS), BOUNDED_CONTEXTS));
        documents.put(GLOSSARY, yamlDocument(rawDocuments.content(GLOSSARY), GLOSSARY));
        documents.put(HANDOFF_RULES, yamlDocument(rawDocuments.content(HANDOFF_RULES), HANDOFF_RULES));

        var teamsDocument = documents.get(TEAMS);
        var processesDocument = documents.get(PROCESSES);
        var systemsDocument = documents.get(SYSTEMS);
        var integrationsDocument = documents.get(INTEGRATIONS);
        var repositoriesDocument = documents.get(REPOSITORIES);
        var codeSearchScopesDocument = documents.get(CODE_SEARCH_SCOPES);
        var boundedContextsDocument = documents.get(BOUNDED_CONTEXTS);
        var glossaryDocument = documents.get(GLOSSARY);
        var handoffRulesDocument = documents.get(HANDOFF_RULES);

        var rawTeams = mapList(teamsDocument.get("teams"));
        var rawProcesses = mapList(processesDocument.get("processes"));
        var rawSystems = mapList(systemsDocument.get("systems"));
        var rawIntegrations = mapList(integrationsDocument.get("integrations"));
        var rawRepositories = mapList(repositoriesDocument.get("repositories"));
        var rawCodeSearchScopes = mapList(codeSearchScopesDocument.get("codeSearchScopes"));
        var rawBoundedContexts = mapList(boundedContextsDocument.get("boundedContexts"));
        var rawGlossaryTerms = mapList(glossaryDocument.get("terms"));
        var rawHandoffRules = mapList(handoffRulesDocument.get("handoffRules"));

        var catalog = new OperationalContextCatalog(
                rawTeams.stream().map(OperationalContextDtos::team).toList(),
                rawProcesses.stream().map(OperationalContextDtos::process).toList(),
                rawSystems.stream().map(OperationalContextDtos::system).toList(),
                rawIntegrations.stream().map(OperationalContextDtos::integration).toList(),
                rawRepositories.stream().map(OperationalContextDtos::repository).toList(),
                rawCodeSearchScopes.stream().map(OperationalContextDtos::repositorySearchScope).toList(),
                rawBoundedContexts.stream().map(OperationalContextDtos::boundedContext).toList(),
                rawGlossaryTerms.stream().map(OperationalContextDtos::glossaryTerm).toList(),
                rawHandoffRules.stream().map(OperationalContextDtos::handoffRule).toList(),
                openQuestions(
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
                        rawTeams,
                        rawGlossaryTerms,
                        rawHandoffRules
                ),
                rawDocuments.content(INDEX)
        );
        return new OperationalContextCatalogDecodeResult(catalog, documents);
    }

    private Map<String, Object> yamlDocument(String content, String logicalDocument) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }

        var resource = new ByteArrayResource(
                content.getBytes(StandardCharsets.UTF_8),
                "operational-context:" + logicalDocument
        );
        var factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(resource);
        factoryBean.afterPropertiesSet();
        var document = factoryBean.getObject();
        return OperationalContextImmutableValues.copyMap(document);
    }

    private List<OperationalContextOpenQuestion> openQuestions(
            Map<String, Object> systemsDocument,
            Map<String, Object> repositoriesDocument,
            Map<String, Object> codeSearchScopesDocument,
            Map<String, Object> processesDocument,
            Map<String, Object> integrationsDocument,
            Map<String, Object> boundedContextsDocument,
            Map<String, Object> teamsDocument,
            Map<String, Object> glossaryDocument,
            Map<String, Object> handoffRulesDocument,
            List<Map<String, Object>> systems,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> codeSearchScopes,
            List<Map<String, Object>> processes,
            List<Map<String, Object>> integrations,
            List<Map<String, Object>> boundedContexts,
            List<Map<String, Object>> teams,
            List<Map<String, Object>> glossaryTerms,
            List<Map<String, Object>> handoffRules
    ) {
        var questions = new ArrayList<OperationalContextOpenQuestion>();
        addYamlGaps(questions, SYSTEMS, "system", null, systemsDocument.get("gaps"));
        addEntityGaps(questions, SYSTEMS, "system", systems);
        addYamlGaps(questions, REPOSITORIES, "repository", null, repositoriesDocument.get("gaps"));
        addEntityGaps(questions, REPOSITORIES, "repository", repositories);
        addYamlGaps(questions, CODE_SEARCH_SCOPES, "code-search-scope", null, codeSearchScopesDocument.get("gaps"));
        addEntityGaps(questions, CODE_SEARCH_SCOPES, "code-search-scope", codeSearchScopes);
        addYamlGaps(questions, PROCESSES, "process", null, processesDocument.get("gaps"));
        addEntityGaps(questions, PROCESSES, "process", processes);
        addYamlGaps(questions, INTEGRATIONS, "integration", null, integrationsDocument.get("gaps"));
        addEntityGaps(questions, INTEGRATIONS, "integration", integrations);
        addYamlGaps(questions, BOUNDED_CONTEXTS, "bounded-context", null, boundedContextsDocument.get("gaps"));
        addEntityGaps(questions, BOUNDED_CONTEXTS, "bounded-context", boundedContexts);
        addYamlGaps(questions, TEAMS, "team", null, teamsDocument.get("gaps"));
        addEntityGaps(questions, TEAMS, "team", teams);
        addYamlGaps(questions, GLOSSARY, "glossary-term", null, glossaryDocument.get("gaps"));
        addEntityGaps(questions, GLOSSARY, "glossary-term", glossaryTerms);
        addYamlGaps(questions, HANDOFF_RULES, "handoff-rule", null, handoffRulesDocument.get("gaps"));
        addEntityGaps(questions, HANDOFF_RULES, "handoff-rule", handoffRules);
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

}

record OperationalContextCatalogDecodeResult(
        OperationalContextCatalog catalog,
        Map<String, Map<String, Object>> decodedDocuments
) {

    OperationalContextCatalogDecodeResult {
        decodedDocuments = OperationalContextImmutableValues.copyDocuments(decodedDocuments);
    }
}
