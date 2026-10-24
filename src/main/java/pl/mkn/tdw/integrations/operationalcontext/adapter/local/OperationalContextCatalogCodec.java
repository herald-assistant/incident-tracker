package pl.mkn.tdw.integrations.operationalcontext.adapter.local;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogEntityType;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextGapContract;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.ValidationFinding;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextImmutableValues;
import pl.mkn.tdw.integrations.operationalcontext.internal.OperationalContextRawDocuments;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextOpenQuestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextMaps.mapList;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextMaps.text;

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

        var rawTeams = entityMaps(teamsDocument, "teams");
        var rawProcesses = entityMaps(processesDocument, "processes");
        var rawSystems = entityMaps(systemsDocument, "systems");
        var rawIntegrations = entityMaps(integrationsDocument, "integrations");
        var rawRepositories = entityMaps(repositoriesDocument, "repositories");
        var rawCodeSearchScopes = entityMaps(codeSearchScopesDocument, "codeSearchScopes");
        var rawBoundedContexts = entityMaps(boundedContextsDocument, "boundedContexts");
        var rawGlossaryTerms = entityMaps(glossaryDocument, "terms");
        var rawHandoffRules = entityMaps(handoffRulesDocument, "handoffRules");

        var questions = openQuestions(documents);
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
                questions.questions(),
                questions.findings()
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

    private List<Map<String, Object>> entityMaps(Map<String, Object> document, String collection) {
        return mapList(document.get(collection)).stream()
                .filter(entry -> entry.get("id") instanceof String id && StringUtils.hasText(id)).toList();
    }

    private QuestionDecode openQuestions(Map<String, Map<String, Object>> documents) {
        var questions = new ArrayList<OperationalContextOpenQuestion>();
        var findings = new ArrayList<ValidationFinding>();
        for (var type : OperationalContextCatalogEntityType.values()) {
            var document = documents.get(type.logicalDocument());
            addYamlGaps(questions, findings, type, null, false, "/gaps", document.get("gaps"));
            var metadata = new LinkedHashMap<>(document);
            metadata.remove(type.collectionName());
            addSignalFindings(findings, type, null, metadata, "");
            var collection = document.get(type.collectionName());
            if (collection != null && !(collection instanceof List<?>)) {
                findings.add(sourceFinding("INVALID_CATALOG_COLLECTION", type, null,
                        "/" + type.collectionName(), "Entity collection must be a list"));
            }
            var entries = collection instanceof List<?> list ? list : List.of();
            for (int index = 0; index < entries.size(); index++) {
                var path = "/" + type.collectionName() + "/" + index;
                if (!(entries.get(index) instanceof Map<?, ?>)) {
                    findings.add(sourceFinding("INVALID_CATALOG_ENTITY", type, null, path, "Entity must be an object"));
                    continue;
                }
                var entry = mapList(List.of(entries.get(index))).get(0);
                var entityId = entry.get("id") instanceof String id && StringUtils.hasText(id) ? id : null;
                if (entityId == null) {
                    findings.add(sourceFinding("INVALID_CATALOG_ENTITY", type, null,
                            path + "/id", "Entity requires a non-blank text ID"));
                }
                addYamlGaps(questions, findings, type, entityId, true, path + "/gaps", entry.get("gaps"));
                addSignalFindings(findings, type, entityId, entry, path);
            }
        }
        return new QuestionDecode(List.copyOf(questions), List.copyOf(findings));
    }

    private void addYamlGaps(
            List<OperationalContextOpenQuestion> questions,
            List<ValidationFinding> findings,
            OperationalContextCatalogEntityType type,
            String entityId,
            boolean entityScoped,
            String path,
            Object source
    ) {
        if (source == null) {
            return;
        }
        if (entityScoped && !StringUtils.hasText(entityId)) {
            findings.add(sourceFinding("INVALID_GAP_TARGET", type, null, path, "Entity gap requires a valid parent entity ID"));
            return;
        }
        if (!(source instanceof List<?> entries)) {
            findings.add(sourceFinding("INVALID_GAP", type, entityId, path, "Gaps must be a list"));
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var itemPath = path + "/" + index;
            var errors = OperationalContextGapContract.validateGap(entry, entityScoped);
            if (!errors.isEmpty()) {
                for (var error : errors) {
                    findings.add(sourceFinding("INVALID_GAP", type, entityId,
                            itemPath + error.pointer(), error.message()));
                }
                continue;
            }
            var item = mapList(List.of(entry)).get(0);
            questions.add(new OperationalContextOpenQuestion(
                    text(item, "id"),
                    type.logicalDocument(),
                    entityId != null ? type.externalName() : defaultText(text(item, "entityType"), type.externalName()),
                    entityId != null ? entityId : text(item, "entityId"),
                    text(item, "summary"),
                    defaultText(text(item, "severity"), "info"),
                    defaultText(text(item, "status"), "open"),
                    itemPath
            ));
        }
    }

    private void addSignalFindings(
            List<ValidationFinding> findings, OperationalContextCatalogEntityType type,
            String entityId, Object source, String path
    ) {
        for (var error : OperationalContextGapContract.unresolvedSignalErrors(source, path)) {
            findings.add(sourceFinding("UNREGISTERED_GAP_SIGNAL", type, entityId, error.pointer(), error.message()));
        }
    }

    private ValidationFinding sourceFinding(
            String code, OperationalContextCatalogEntityType type, String entityId, String path, String message
    ) {
        return new ValidationFinding("error", code, message, List.of(new SourceRef(
                type.logicalDocument(), entityId != null ? type.externalName() : "catalog",
                entityId != null ? entityId : type.logicalDocument(), path, "catalog-contract"
        )));
    }

    private String defaultText(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private record QuestionDecode(List<OperationalContextOpenQuestion> questions, List<ValidationFinding> findings) {
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
