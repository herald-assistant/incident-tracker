package pl.mkn.incidenttracker.agenttools.operationalcontext.mcp;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.*;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pl.mkn.incidenttracker.agenttools.operationalcontext.mcp.OperationalContextToolDtos.*;

@Component
public class OperationalContextToolMapper {

    static final String TYPE_SYSTEM = "system";
    static final String TYPE_REPOSITORY = "repository";
    static final String TYPE_CODE_SEARCH_SCOPE = "codeSearchScope";
    static final String TYPE_PROCESS = "process";
    static final String TYPE_INTEGRATION = "integration";
    static final String TYPE_BOUNDED_CONTEXT = "boundedContext";
    static final String TYPE_TEAM = "team";
    static final String TYPE_GLOSSARY_TERM = "glossaryTerm";
    static final String TYPE_HANDOFF_RULE = "handoffRule";

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_SEARCH_LIMIT = 8;
    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int DEFAULT_SECTION_LIST_LIMIT = 8;
    private static final int DEFAULT_SECTION_MAP_LIMIT = 12;
    private static final int DEFAULT_SOURCE_REF_LIMIT = 8;
    private static final int DEFAULT_OPEN_QUESTION_LIMIT = 5;
    private static final List<String> DETAIL_SECTION_ORDER = List.of(
            "overview",
            "relations",
            "signals",
            "codeSearch",
            "handoff",
            "sourceCoverage",
            "openQuestions"
    );
    private static final Set<String> DEFAULT_DETAIL_INCLUDES = Set.of(
            "overview",
            "relations",
            "signals",
            "codeSearch",
            "handoff",
            "sourceCoverage",
            "openQuestions"
    );

    public OpctxScopeResult getScope(OperationalContextCatalog catalog) {
        var index = index(catalog);
        return new OpctxScopeResult(
                true,
                typeOrder().stream()
                        .map(type -> new OpctxEntityTypeSummary(
                                type,
                                labelForType(type),
                                index.entitiesByType().getOrDefault(type, List.of()).size(),
                                true,
                                true,
                                true
                        ))
                        .toList(),
                scopeAffordances()
        );
    }

    public OpctxListEntitiesResult listEntities(
            OperationalContextCatalog catalog,
            String type,
            Integer page,
            Integer pageSize,
            String filter
    ) {
        var normalizedType = normalizeType(type);
        var requestedPage = normalizePositive(page, DEFAULT_PAGE);
        var effectivePageSize = Math.min(MAX_PAGE_SIZE, normalizePositive(pageSize, DEFAULT_PAGE_SIZE));
        var entities = index(catalog).entitiesByType().getOrDefault(normalizedType, List.of()).stream()
                .filter(entity -> matchesFilter(entity, filter))
                .sorted(Comparator.comparing(OpctxCatalogEntity::label, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(OpctxCatalogEntity::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
        var totalItems = entities.size();
        var totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / effectivePageSize);
        var fromIndex = Math.min((requestedPage - 1) * effectivePageSize, totalItems);
        var toIndex = Math.min(fromIndex + effectivePageSize, totalItems);

        return new OpctxListEntitiesResult(
                normalizedType,
                requestedPage,
                effectivePageSize,
                totalItems,
                totalPages,
                toIndex < totalItems,
                entities.subList(fromIndex, toIndex).stream()
                        .map(this::toIndexItem)
                        .toList(),
                listAffordances(normalizedType, requestedPage, effectivePageSize, totalItems, toIndex < totalItems, entities)
        );
    }

    public OpctxSearchResult search(
            OperationalContextCatalog catalog,
            String query,
            List<String> types,
            Integer limit
    ) {
        var effectiveLimit = Math.min(MAX_SEARCH_LIMIT, normalizePositive(limit, DEFAULT_SEARCH_LIMIT));
        var normalizedTypes = normalizeTypes(types);
        if (!StringUtils.hasText(query)) {
            return new OpctxSearchResult(query, normalizedTypes, effectiveLimit, false, List.of(), searchAffordances(query, List.of(), false));
        }

        var queryText = query.trim();
        var tokens = tokens(queryText);
        var results = index(catalog).entities().stream()
                .filter(entity -> normalizedTypes.isEmpty() || normalizedTypes.contains(entity.type()))
                .map(entity -> score(entity, queryText, tokens))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(OpctxScoredEntity::score).reversed()
                        .thenComparing(result -> result.entity().type())
                        .thenComparing(result -> result.entity().label(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        var items = results.stream()
                .limit(effectiveLimit)
                .map(this::toSearchItem)
                .toList();
        return new OpctxSearchResult(
                queryText,
                normalizedTypes,
                effectiveLimit,
                results.size() > effectiveLimit,
                items,
                searchAffordances(queryText, items, results.size() > effectiveLimit)
        );
    }

    public OpctxEntityDetailResult getEntity(
            OperationalContextCatalog catalog,
            String type,
            String id,
            List<String> include
    ) {
        var normalizedType = normalizeType(type);
        var normalizedId = normalize(id);
        if (!StringUtils.hasText(normalizedId)) {
            throw new IllegalArgumentException("Operational context entity id is required.");
        }

        var entity = index(catalog).entitiesByType().getOrDefault(normalizedType, List.of()).stream()
                .filter(candidate -> normalize(candidate.id()).equals(normalizedId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                "Unknown operational context entity %s:%s.".formatted(normalizedType, id)
                ));
        var includes = normalizeIncludes(include);
        var overview = included(includes, "overview", entity.overview());
        var relations = included(includes, "relations", entity.relations());
        var signals = included(includes, "signals", entity.signals());
        var codeSearch = included(includes, "codeSearch", entity.codeSearch());
        var handoff = included(includes, "handoff", entity.handoff());
        var sourceCoverage = included(includes, "sourceCoverage", entity.sourceCoverage());
        var openQuestions = includes.contains("openQuestions")
                ? limitList(entity.openQuestions(), DEFAULT_OPEN_QUESTION_LIMIT)
                : List.<OpctxOpenQuestion>of();
        var sourceRefs = limitText(entity.sourceRefs(), DEFAULT_SOURCE_REF_LIMIT);
        var truncation = entityTruncation(entity, overview, relations, signals, codeSearch, handoff, sourceCoverage, openQuestions, sourceRefs);

        return new OpctxEntityDetailResult(
                entity.type(),
                entity.id(),
                entity.label(),
                entity.summary(),
                entity.purpose(),
                overview,
                relations,
                signals,
                codeSearch,
                handoff,
                sourceCoverage,
                openQuestions,
                sourceRefs,
                entityAffordances(entity, includes, truncation)
        );
    }

    private OpctxCatalogIndex index(OperationalContextCatalog catalog) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var repositoriesById = safeCatalog.repositories().stream()
                .collect(Collectors.toMap(
                        OperationalContextRepository::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        var openQuestions = safeCatalog.openQuestions();
        var entities = new ArrayList<OpctxCatalogEntity>();

        safeCatalog.systems().forEach(system -> entities.add(systemEntity(system, safeCatalog, openQuestions)));
        safeCatalog.repositories().forEach(repository -> entities.add(repositoryEntity(repository, safeCatalog, openQuestions)));
        safeCatalog.codeSearchScopes().forEach(scope -> entities.add(codeSearchScopeEntity(scope, repositoriesById, openQuestions)));
        safeCatalog.processes().forEach(process -> entities.add(processEntity(process, openQuestions)));
        safeCatalog.integrations().forEach(integration -> entities.add(integrationEntity(integration, openQuestions)));
        safeCatalog.boundedContexts().forEach(context -> entities.add(boundedContextEntity(context, openQuestions)));
        safeCatalog.teams().forEach(team -> entities.add(teamEntity(team, openQuestions)));
        safeCatalog.glossaryTerms().forEach(term -> entities.add(glossaryTermEntity(term, openQuestions)));
        safeCatalog.handoffRules().forEach(rule -> entities.add(handoffRuleEntity(rule, openQuestions)));

        var entitiesByType = entities.stream()
                .collect(Collectors.groupingBy(
                        OpctxCatalogEntity::type,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return new OpctxCatalogIndex(List.copyOf(entities), entitiesByType);
    }

    private OpctxCatalogEntity systemEntity(
            OperationalContextSystem system,
            OperationalContextCatalog catalog,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var codeSearchScopes = catalog.codeSearchScopes().stream()
                .filter(scope -> semanticTargetMatches(scope.target(), TYPE_SYSTEM, system.id()))
                .toList();
        var facets = facets(
                "ownerTeamIds", teamIds(system.responsibilities(), system.references()),
                "repositoryIds", system.references().repositories(),
                "processIds", system.references().processes(),
                "boundedContextIds", system.references().boundedContexts(),
                "integrationIds", system.references().integrations(),
                "codeSearchScopeIds", codeSearchScopes.stream().map(OperationalContextRepositorySearchScope::id).toList()
        );
        var overview = values(
                "kind", system.kind(),
                "lifecycleStatus", system.lifecycleStatus(),
                "operationalStatus", system.operationalStatus(),
                "criticality", system.criticality(),
                "aliases", system.aliases(),
                "useFor", system.useFor()
        );
        var relations = values(
                "references", references(system.references()),
                "responsibilities", responsibilities(system.responsibilities()),
                "relations", relations(system.relations()),
                "externalOwner", system.participants().externalOwner()
        );
        var signals = values(
                "matchSignals", matchSignals(system.runtimeMatchSignals()),
                "deployment", deployment(system.deployment())
        );
        var codeSearch = values(
                "codeSearchScopes", codeSearchScopes.stream()
                        .map(scope -> codeSearchScopeSummary(scope, catalog.repositories()))
                        .toList()
        );
        var handoff = handoff(system.handoffHints());

        return entity(
                TYPE_SYSTEM,
                system.id(),
                system.label(),
                firstNonBlank(system.summary(), system.purpose()),
                system.purpose(),
                system.aliases(),
                system.useFor(),
                facets,
                overview,
                relations,
                signals,
                codeSearch,
                handoff,
                values("sourceRefs", List.of("systems.yml#" + system.id())),
                openQuestionsFor(openQuestions, TYPE_SYSTEM, system.id()),
                List.of("systems.yml#" + system.id()),
                join(system.genericSignals(), flattenFacets(facets))
        );
    }

    private OpctxCatalogEntity repositoryEntity(
            OperationalContextRepository repository,
            OperationalContextCatalog catalog,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var codeSearchScopes = catalog.codeSearchScopes().stream()
                .filter(scope -> scope.repositories().stream()
                        .anyMatch(candidate -> normalize(candidate.repoId()).equals(normalize(repository.id()))))
                .toList();
        var facets = facets(
                "projectName", List.of(repository.git().project()),
                "gitLabPath", List.of(repository.git().projectPath()),
                "systems", repository.references().systems(),
                "boundedContexts", repository.references().boundedContexts(),
                "processes", repository.references().processes(),
                "integrations", repository.references().integrations(),
                "packagePrefixes", repository.packagePrefixSignals(),
                "classHints", repository.classHintSignals(),
                "endpointHints", repository.endpointPrefixSignals(),
                "codeSearchScopeIds", codeSearchScopes.stream().map(OperationalContextRepositorySearchScope::id).toList()
        );
        var overview = values(
                "repositoryType", repository.repositoryType(),
                "lifecycleStatus", repository.lifecycleStatus(),
                "criticality", repository.criticality(),
                "git", git(repository.git()),
                "aliases", repository.aliases(),
                "useFor", repository.useFor()
        );
        var relations = values(
                "references", references(repository.references()),
                "responsibilities", responsibilities(repository.responsibilities()),
                "relations", relations(repository.relations()),
                "modules", repository.modules().stream().map(this::repositoryModule).toList()
        );
        var signals = values(
                "matchSignals", matchSignals(repository.matchSignals()),
                "packagePrefixes", repository.packagePrefixSignals(),
                "classHints", repository.classHintSignals(),
                "endpointHints", repository.endpointPrefixSignals(),
                "queueTopicHints", repository.queueTopicHints(),
                "sourceLayout", sourceLayout(repository.sourceLayout())
        );
        var codeSearch = values(
                "git", git(repository.git()),
                "codeSearchScopes", codeSearchScopes.stream()
                        .map(scope -> codeSearchScopeSummary(scope, catalog.repositories()))
                        .toList()
        );

        return entity(
                TYPE_REPOSITORY,
                repository.id(),
                repository.label(),
                firstNonBlank(repository.summary(), repository.purpose()),
                repository.purpose(),
                join(repository.aliases(), repository.git().aliases()),
                repository.useFor(),
                facets,
                overview,
                relations,
                signals,
                codeSearch,
                handoff(repository.handoffHints()),
                values("sourceRefs", List.of("repo-map.yml#repositories/" + repository.id())),
                openQuestionsFor(openQuestions, TYPE_REPOSITORY, repository.id()),
                List.of("repo-map.yml#repositories/" + repository.id()),
                join(repository.genericSignals(), flattenFacets(facets))
        );
    }

    private OpctxCatalogEntity codeSearchScopeEntity(
            OperationalContextRepositorySearchScope scope,
            Map<String, OperationalContextRepository> repositoriesById,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var includedRepositories = scope.repositories().stream()
                .map(repository -> scopeRepository(repository, repositoriesById))
                .toList();
        var facets = facets(
                "scopeType", scope.scopeType(),
                "targetType", scope.target().type(),
                "targetId", scope.target().id(),
                "repositories", scope.repositories().stream().map(OperationalContextRepositorySearchRepository::repoId).toList(),
                "packagePrefixes", scope.packagePrefixes(),
                "classHints", scope.classHints(),
                "endpointHints", scope.endpointHints(),
                "queueTopicHints", scope.queueTopicHints()
        );
        var overview = values(
                "scopeType", scope.scopeType(),
                "lifecycleStatus", scope.lifecycleStatus(),
                "summary", scope.summary(),
                "useFor", scope.useFor()
        );
        var relations = values(
                "target", repositorySearchTarget(scope.target()),
                "repositories", includedRepositories
        );
        var codeSearch = values(
                "repositories", includedRepositories,
                "packagePrefixes", scope.packagePrefixes(),
                "classHints", scope.classHints(),
                "endpointHints", scope.endpointHints(),
                "queueTopicHints", scope.queueTopicHints(),
                "databaseHints", databaseHints(scope.databaseHints()),
                "workflowHints", workflowHints(scope.workflowHints()),
                "traversal", traversal(scope.traversal())
        );
        var sourceCoverage = values(
                "limitations", scope.limitations(),
                "sourceRefs", List.of("repo-map.yml#codeSearchScopes/" + scope.id())
        );

        return entity(
                TYPE_CODE_SEARCH_SCOPE,
                scope.id(),
                firstNonBlank(scope.name(), scope.id()),
                codeSearchScopeSummary(scope),
                null,
                List.of(),
                scope.useFor(),
                facets,
                overview,
                relations,
                values("target", repositorySearchTarget(scope.target())),
                codeSearch,
                Map.of(),
                sourceCoverage,
                openQuestionsFor(openQuestions, TYPE_CODE_SEARCH_SCOPE, scope.id()),
                List.of("repo-map.yml#codeSearchScopes/" + scope.id()),
                flattenFacets(facets)
        );
    }

    private OpctxCatalogEntity processEntity(
            OperationalContextProcess process,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var facets = facets(
                "primarySystems", process.participants().primarySystems(),
                "supportingSystems", process.participants().supportingSystems(),
                "externalSystems", process.participants().externalSystems(),
                "boundedContexts", process.references().boundedContexts(),
                "repositories", process.references().repositories(),
                "integrations", process.references().integrations(),
                "failureModes", process.failureModes()
        );
        var overview = values(
                "type", process.type(),
                "lifecycleStatus", process.lifecycleStatus(),
                "criticality", process.criticality(),
                "aliases", process.aliases(),
                "useFor", process.useFor(),
                "endsWhen", process.processBoundary().endsWhen(),
                "successArtifacts", process.outcomes().successArtifacts()
        );
        var relations = values(
                "participants", values(
                        "actors", process.participants().actors(),
                        "primarySystems", process.participants().primarySystems(),
                        "supportingSystems", process.participants().supportingSystems(),
                        "externalSystems", process.participants().externalSystems(),
                        "platformComponents", process.participants().platformComponents()
                ),
                "references", references(process.references()),
                "responsibilities", responsibilities(process.responsibilities()),
                "relations", relations(process.relations()),
                "steps", process.steps().stream().map(this::processStep).toList()
        );

        return entity(
                TYPE_PROCESS,
                process.id(),
                process.label(),
                firstNonBlank(process.summary(), process.purpose()),
                process.purpose(),
                process.aliases(),
                process.useFor(),
                facets,
                overview,
                relations,
                values("matchSignals", matchSignals(process.matchSignals()), "failureModes", process.failureModes()),
                Map.of(),
                handoff(process.handoffHints()),
                values("sourceRefs", List.of("processes.yml#" + process.id())),
                openQuestionsFor(openQuestions, TYPE_PROCESS, process.id()),
                List.of("processes.yml#" + process.id()),
                join(process.genericSignals(), flattenFacets(facets))
        );
    }

    private OpctxCatalogEntity integrationEntity(
            OperationalContextIntegration integration,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var facets = facets(
                "sourceSystem", List.of(integration.participants().source().system()),
                "targetSystems", integration.participants().targetSystems(),
                "intermediarySystems", integration.participants().intermediarySystems(),
                "finalTargets", integration.participants().finalTargetSystems(),
                "protocols", integration.transport().protocols(),
                "endpointPrefixes", integration.transport().http().endpointPrefixes(),
                "queues", integration.transport().messaging().queues(),
                "topics", integration.transport().messaging().topics(),
                "classHints", integration.implementation().classHints()
        );
        var overview = values(
                "category", integration.category(),
                "integrationStyle", integration.integrationStyle(),
                "flowDirection", integration.flowDirection(),
                "lifecycleStatus", integration.lifecycleStatus(),
                "criticality", integration.criticality(),
                "aliases", integration.aliases(),
                "useFor", integration.useFor()
        );
        var relations = values(
                "participants", values(
                        "source", integrationParticipant(integration.participants().source()),
                        "targets", integration.participants().targets().stream().map(this::integrationParticipant).toList(),
                        "intermediaries", integration.participants().intermediaries().stream().map(this::integrationParticipant).toList(),
                        "finalTargets", integration.participants().finalTargets().stream().map(this::integrationParticipant).toList()
                ),
                "references", references(integration.references()),
                "responsibilities", responsibilities(integration.responsibilities()),
                "relations", relations(integration.relations())
        );
        var signals = values(
                "matchSignals", matchSignals(integration.matchSignals()),
                "transport", transport(integration.transport()),
                "channels", integration.channels().stream().map(this::channel).toList(),
                "implementation", implementation(integration.implementation()),
                "failureModes", integration.failureModes()
        );

        return entity(
                TYPE_INTEGRATION,
                integration.id(),
                integration.label(),
                firstNonBlank(integration.summary(), integration.purpose()),
                integration.purpose(),
                integration.aliases(),
                integration.useFor(),
                facets,
                overview,
                relations,
                signals,
                Map.of(),
                handoff(integration.handoffHints()),
                values("sourceRefs", List.of("integrations.yml#" + integration.id())),
                openQuestionsFor(openQuestions, TYPE_INTEGRATION, integration.id()),
                List.of("integrations.yml#" + integration.id()),
                join(integration.genericSignals(), flattenFacets(facets))
        );
    }

    private OpctxCatalogEntity boundedContextEntity(
            OperationalContextBoundedContext context,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var facets = facets(
                "systems", context.references().systems(),
                "repositories", context.references().repositories(),
                "processes", context.references().processes(),
                "integrations", context.references().integrations(),
                "terms", context.references().terms(),
                "serviceNames", context.operationalSignals().serviceNames(),
                "endpointPrefixes", context.operationalSignals().endpointPrefixes(),
                "packagePrefixes", context.operationalSignals().packagePrefixes()
        );

        return entity(
                TYPE_BOUNDED_CONTEXT,
                context.id(),
                context.label(),
                firstNonBlank(context.summary(), context.purpose()),
                context.purpose(),
                context.aliases(),
                context.useFor(),
                facets,
                values(
                        "lifecycleStatus", context.lifecycleStatus(),
                        "aliases", context.aliases(),
                        "useFor", context.useFor()
                ),
                values(
                        "references", references(context.references()),
                        "responsibilities", responsibilities(context.responsibilities()),
                        "relations", relations(context.relations())
                ),
                values(
                        "matchSignals", matchSignals(context.matchSignals()),
                        "operationalSignals", context.operationalSignals().valuesByKey()
                ),
                Map.of(),
                handoff(context.handoffHints()),
                values("sourceRefs", List.of("bounded-contexts.yml#" + context.id())),
                openQuestionsFor(openQuestions, TYPE_BOUNDED_CONTEXT, context.id()),
                List.of("bounded-contexts.yml#" + context.id()),
                join(context.genericSignals(), flattenFacets(facets))
        );
    }

    private OpctxCatalogEntity teamEntity(
            OperationalContextTeam team,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var facets = facets(
                "systems", team.references().systems(),
                "repositories", team.references().repositories(),
                "processes", team.references().processes(),
                "boundedContexts", team.references().boundedContexts(),
                "integrations", team.references().integrations(),
                "handoffRules", team.references().handoffRules()
        );

        return entity(
                TYPE_TEAM,
                team.id(),
                team.label(),
                firstNonBlank(team.summary(), team.purpose()),
                team.purpose(),
                team.aliases(),
                team.useFor(),
                facets,
                values(
                        "lifecycleStatus", team.lifecycleStatus(),
                        "aliases", team.aliases(),
                        "useFor", team.useFor()
                ),
                values(
                        "references", references(team.references()),
                        "responsibilities", responsibilities(team.responsibilities()),
                        "relations", relations(team.relations())
                ),
                values("matchSignals", matchSignals(team.matchSignals())),
                Map.of(),
                handoff(team.handoffHints()),
                values("sourceRefs", List.of("teams.yml#" + team.id())),
                openQuestionsFor(openQuestions, TYPE_TEAM, team.id()),
                List.of("teams.yml#" + team.id()),
                join(team.genericSignals(), flattenFacets(facets))
        );
    }

    private OpctxCatalogEntity glossaryTermEntity(
            OperationalContextGlossaryTerm term,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var facets = facets(
                "category", List.of(term.category()),
                "canonicalReferences", term.canonicalReferences(),
                "synonyms", term.synonyms(),
                "matchSignals", term.matchSignals()
        );

        return entity(
                TYPE_GLOSSARY_TERM,
                term.id(),
                firstNonBlank(term.term(), term.id()),
                term.definition(),
                null,
                term.synonyms(),
                term.useInContext(),
                facets,
                values(
                        "term", term.term(),
                        "category", term.category(),
                        "definition", term.definition(),
                        "useInContext", term.useInContext(),
                        "doNotConfuseWith", term.doNotConfuseWith(),
                        "notes", term.notes()
                ),
                values("canonicalReferences", term.canonicalReferences()),
                values("matchSignals", term.matchSignals(), "synonyms", term.synonyms()),
                Map.of(),
                Map.of(),
                values("sourceRefs", List.of("glossary.md#" + term.id())),
                openQuestionsFor(openQuestions, TYPE_GLOSSARY_TERM, term.id()),
                List.of("glossary.md#" + term.id()),
                join(term.matchSignals(), join(term.canonicalReferences(), term.synonyms()))
        );
    }

    private OpctxCatalogEntity handoffRuleEntity(
            OperationalContextHandoffRule rule,
            List<OperationalContextOpenQuestion> openQuestions
    ) {
        var facets = facets(
                "routeTo", List.of(rule.routeTo()),
                "partnerTeams", rule.partnerTeams(),
                "requiredEvidence", rule.requiredEvidence(),
                "useWhen", rule.useWhen()
        );

        return entity(
                TYPE_HANDOFF_RULE,
                rule.id(),
                firstNonBlank(rule.title(), rule.id()),
                firstNonBlank(first(rule.useWhen()), first(rule.expectedFirstAction()), first(rule.notes())),
                null,
                List.of(),
                rule.useWhen(),
                facets,
                values("title", rule.title(), "routeTo", rule.routeTo()),
                values("partnerTeams", rule.partnerTeams()),
                values("useWhen", rule.useWhen(), "doNotUseWhen", rule.doNotUseWhen()),
                Map.of(),
                values(
                        "routeTo", rule.routeTo(),
                        "requiredEvidence", rule.requiredEvidence(),
                        "expectedFirstAction", rule.expectedFirstAction(),
                        "partnerTeams", rule.partnerTeams(),
                        "notes", rule.notes()
                ),
                values("sourceRefs", List.of("handoff-rules.md#" + rule.id())),
                openQuestionsFor(openQuestions, TYPE_HANDOFF_RULE, rule.id()),
                List.of("handoff-rules.md#" + rule.id()),
                join(rule.useWhen(), join(rule.requiredEvidence(), join(rule.expectedFirstAction(), rule.partnerTeams())))
        );
    }

    private OpctxCatalogEntity entity(
            String type,
            String id,
            String label,
            String summary,
            String purpose,
            List<String> aliases,
            List<String> useFor,
            Map<String, List<String>> facets,
            Map<String, Object> overview,
            Map<String, Object> relations,
            Map<String, Object> signals,
            Map<String, Object> codeSearch,
            Map<String, Object> handoff,
            Map<String, Object> sourceCoverage,
            List<OpctxOpenQuestion> openQuestions,
            List<String> sourceRefs,
            List<String> extraSearchValues
    ) {
        var identityValues = textValues(id, label, aliases);
        var summaryValues = textValues(summary, purpose, useFor);
        var signalValues = textValues(extraSearchValues, signals.values());
        var relationValues = textValues(flattenFacets(facets), relations.values(), codeSearch.values(), handoff.values());

        return new OpctxCatalogEntity(
                type,
                id,
                firstNonBlank(label, id),
                summary,
                purpose,
                identityValues,
                summaryValues,
                signalValues,
                relationValues,
                facets,
                overview,
                relations,
                signals,
                codeSearch,
                handoff,
                sourceCoverage,
                openQuestions,
                sourceRefs
        );
    }

    private OpctxEntityIndexItem toIndexItem(OpctxCatalogEntity entity) {
        return new OpctxEntityIndexItem(
                entity.type(),
                entity.id(),
                entity.label(),
                entity.summary(),
                compactFacets(entity.facets()),
                limitText(entity.sourceRefs(), DEFAULT_SOURCE_REF_LIMIT)
        );
    }

    private OpctxSearchItem toSearchItem(OpctxScoredEntity scored) {
        var confidence = Math.min(1.0d, scored.score() / 100.0d);
        return new OpctxSearchItem(
                scored.entity().type(),
                scored.entity().id(),
                scored.entity().label(),
                scored.entity().summary(),
                Math.round(confidence * 100.0d) / 100.0d,
                scored.matchedFields(),
                scored.matchedSignals(),
                scored.why(),
                scored.entity().sourceRefs()
        );
    }

    private OpctxScoredEntity score(OpctxCatalogEntity entity, String query, List<String> tokens) {
        var matchedFields = new LinkedHashSet<String>();
        var matchedSignals = new LinkedHashSet<String>();
        var score = 0;

        score = Math.max(score, scoreValues("identity", entity.identityValues(), query, tokens, matchedFields, matchedSignals, 100, 90, 75));
        score = Math.max(score, scoreValues("signals", entity.signalValues(), query, tokens, matchedFields, matchedSignals, 70, 65, 55));
        score = Math.max(score, scoreValues("summary", entity.summaryValues(), query, tokens, matchedFields, matchedSignals, 50, 45, 35));
        score = Math.max(score, scoreValues("relations", entity.relationValues(), query, tokens, matchedFields, matchedSignals, 40, 35, 25));

        var why = matchedFields.isEmpty()
                ? null
                : "Matched %s for %s:%s.".formatted(String.join(", ", matchedFields), entity.type(), entity.id());
        return new OpctxScoredEntity(
                entity,
                score,
                List.copyOf(matchedFields),
                List.copyOf(limit(matchedSignals, 8)),
                why
        );
    }

    private int scoreValues(
            String field,
            List<String> values,
            String query,
            List<String> tokens,
            Set<String> matchedFields,
            Set<String> matchedSignals,
            int exactScore,
            int containsScore,
            int tokenScore
    ) {
        var normalizedQuery = normalize(query);
        var normalizedTokens = tokens.stream().map(this::normalize).filter(StringUtils::hasText).toList();
        var fieldScore = 0;

        for (var value : values) {
            var normalizedValue = normalize(value);
            if (!StringUtils.hasText(normalizedValue)) {
                continue;
            }

            if (normalizedValue.equals(normalizedQuery)) {
                fieldScore = Math.max(fieldScore, exactScore);
                matchedFields.add(field);
                matchedSignals.add(value);
                continue;
            }
            if (normalizedValue.contains(normalizedQuery)) {
                fieldScore = Math.max(fieldScore, containsScore);
                matchedFields.add(field);
                matchedSignals.add(value);
                continue;
            }
            if (!normalizedTokens.isEmpty() && normalizedTokens.stream().allMatch(normalizedValue::contains)) {
                fieldScore = Math.max(fieldScore, tokenScore);
                matchedFields.add(field);
                matchedSignals.add(value);
            }
        }

        return fieldScore;
    }

    private boolean matchesFilter(OpctxCatalogEntity entity, String filter) {
        if (!StringUtils.hasText(filter)) {
            return true;
        }
        var normalizedFilter = normalize(filter);
        return textValues(
                entity.identityValues(),
                entity.summaryValues(),
                entity.signalValues(),
                entity.relationValues()
        ).stream().map(this::normalize).anyMatch(value -> value.contains(normalizedFilter));
    }

    private String normalizeType(String type) {
        var normalized = normalize(type);
        return switch (normalized) {
            case "system", "systems" -> TYPE_SYSTEM;
            case "repository", "repositories", "repo" -> TYPE_REPOSITORY;
            case "codesearchscope", "code-search-scope", "code_search_scope", "scope" -> TYPE_CODE_SEARCH_SCOPE;
            case "process", "processes" -> TYPE_PROCESS;
            case "integration", "integrations" -> TYPE_INTEGRATION;
            case "boundedcontext", "bounded-context", "bounded_context", "context" -> TYPE_BOUNDED_CONTEXT;
            case "team", "teams" -> TYPE_TEAM;
            case "glossaryterm", "glossary-term", "glossary_term", "term", "terms", "glossary" -> TYPE_GLOSSARY_TERM;
            case "handoffrule", "handoff-rule", "handoff_rule", "handoff" -> TYPE_HANDOFF_RULE;
            default -> throw new IllegalArgumentException("Unsupported operational context entity type: " + type);
        };
    }

    private List<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return types.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeType)
                .distinct()
                .toList();
    }

    private Set<String> normalizeIncludes(List<String> include) {
        if (include == null || include.isEmpty()) {
            return DEFAULT_DETAIL_INCLUDES;
        }
        var normalized = include.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(DEFAULT_DETAIL_INCLUDES::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return normalized.isEmpty() ? DEFAULT_DETAIL_INCLUDES : Set.copyOf(normalized);
    }

    private Map<String, Object> included(Set<String> includes, String include, Map<String, Object> value) {
        return includes.contains(include) ? compactMap(value) : Map.of();
    }

    private OpctxToolAffordances scopeAffordances() {
        return new OpctxToolAffordances(
                "default",
                List.of(
                        toolLink("list-systems", "opctx_list_entities", values("type", TYPE_SYSTEM), "Browse canonical systems first."),
                        toolLink("search", "opctx_search", values("query", "<term-or-signal>"), "Search by endpoint, class, service, repository or domain term.")
                ),
                List.of("opctx_list_entities(type=<type>)", "opctx_search(query=<term-or-signal>)"),
                List.of(
                        "Use opctx_search when you have a concrete signal.",
                        "Use opctx_list_entities when you need a table-of-contents style browse."
                ),
                List.of("opctx_search", "opctx_list_entities", "opctx_get_entity"),
                "Scope has no expanded payload; follow list/search links to read specific entities.",
                List.of("Entity details are intentionally omitted from scope."),
                OpctxTruncation.none(),
                List.of()
        );
    }

    private OpctxToolAffordances listAffordances(
            String type,
            int page,
            int pageSize,
            int totalItems,
            boolean truncated,
            List<OpctxCatalogEntity> entities
    ) {
        var pageItems = entities.stream()
                .skip((long) Math.max(0, page - 1) * pageSize)
                .limit(pageSize)
                .toList();
        var links = new ArrayList<OpctxToolLink>();
        if (!pageItems.isEmpty()) {
            var first = pageItems.get(0);
            links.add(toolLink(
                    "first-entity",
                    "opctx_get_entity",
                    values("type", first.type(), "id", first.id()),
                    "Open the first listed entity before following related repositories or flows."
            ));
        }
        if (truncated) {
            links.add(toolLink(
                    "next-page",
                    "opctx_list_entities",
                    values("type", type, "page", page + 1, "pageSize", pageSize),
                    "Continue browsing this entity type."
            ));
        }
        return new OpctxToolAffordances(
                "default",
                links,
                List.of("opctx_get_entity(type=<type>, id=<id>)", "opctx_search(query=<term-or-signal>)"),
                pageItems.isEmpty()
                        ? List.of("Try opctx_search with a concrete signal if this list is empty.")
                        : List.of("Open the most relevant listed entity with opctx_get_entity before reading code."),
                List.of("opctx_get_entity", "opctx_search"),
                "Use opctx_get_entity with a focused include list when a listed item is relevant.",
                List.of("List output keeps only index cards and compact facets."),
                truncation(
                        "entity list paginated",
                        counts("items", pageItems.size()),
                        counts("items", totalItems)
                ),
                List.of()
        );
    }

    private OpctxToolAffordances searchAffordances(
            String query,
            List<OpctxSearchItem> items,
            boolean truncated
    ) {
        var links = new ArrayList<OpctxToolLink>();
        if (!items.isEmpty()) {
            var top = items.get(0);
            links.add(toolLink(
                    "top-result",
                    "opctx_get_entity",
                    values("type", top.type(), "id", top.id()),
                    "Read compact entity detail for the top ranked match."
            ));
        }
        return new OpctxToolAffordances(
                "default",
                links,
                List.of("opctx_get_entity(type=<type>, id=<id>)", "opctx_search(query=<more-specific-signal>)"),
                items.isEmpty()
                        ? List.of("Search again with a system, endpoint, class, queue, table, repository or local-language term.")
                        : List.of("Read top result details before choosing repositories or handoff route."),
                List.of("opctx_get_entity", "opctx_search"),
                "Increase limit only when recall matters more than a short ranked candidate set.",
                List.of("Search returns compact ranked candidates, not full entity details."),
                truncation(
                        "search results limited",
                        counts("results", items.size()),
                        counts("results", truncated ? Math.max(items.size() + 1, items.size()) : items.size())
                ),
                List.of()
        );
    }

    private OpctxToolAffordances entityAffordances(
            OpctxCatalogEntity entity,
            Set<String> includes,
            OpctxTruncation truncation
    ) {
        var links = new ArrayList<OpctxToolLink>();
        links.add(toolLink(
                "self",
                "opctx_get_entity",
                values("type", entity.type(), "id", entity.id()),
                "Read compact default entity detail."
        ));
        for (var section : DETAIL_SECTION_ORDER) {
            links.add(toolLink(
                    "include-" + section,
                    "opctx_get_entity",
                    values("type", entity.type(), "id", entity.id(), "include", List.of(section)),
                    "Read only the " + section + " section for this entity."
            ));
        }

        var omitted = new ArrayList<String>();
        for (var section : DETAIL_SECTION_ORDER) {
            if (!includes.contains(section)) {
                omitted.add(section + " omitted by include filter.");
            }
        }
        if (truncation.truncated()) {
            omitted.add("Large maps/lists were compacted for the default tool profile.");
        }

        var suggestedReads = new ArrayList<String>();
        if (!entity.codeSearch().isEmpty()) {
            suggestedReads.add("opctx_get_entity(type=%s, id=%s, include=[codeSearch]) to narrow repositories/modules.".formatted(entity.type(), entity.id()));
        }
        if (!entity.relations().isEmpty()) {
            suggestedReads.add("opctx_get_entity(type=%s, id=%s, include=[relations]) to inspect upstream/downstream references.".formatted(entity.type(), entity.id()));
        }
        if (!entity.signals().isEmpty()) {
            suggestedReads.add("opctx_get_entity(type=%s, id=%s, include=[signals]) to verify runtime/code matching signals.".formatted(entity.type(), entity.id()));
        }
        suggestedReads.add("opctx_search(query=<more-specific-signal>) if this entity is too broad.");

        return new OpctxToolAffordances(
                "default",
                links,
                DETAIL_SECTION_ORDER.stream()
                        .map(section -> "include=" + section)
                        .toList(),
                suggestedReads,
                List.of("opctx_get_entity", "opctx_search"),
                "Call opctx_get_entity again with a focused include section when omitted/truncated data is needed.",
                omitted,
                truncation,
                limitationsFromSourceCoverage(entity.sourceCoverage())
        );
    }

    private OpctxTruncation entityTruncation(
            OpctxCatalogEntity entity,
            Map<String, Object> overview,
            Map<String, Object> relations,
            Map<String, Object> signals,
            Map<String, Object> codeSearch,
            Map<String, Object> handoff,
            Map<String, Object> sourceCoverage,
            List<OpctxOpenQuestion> openQuestions,
            List<String> sourceRefs
    ) {
        var returned = counts(
                "overviewValues", leafCount(overview),
                "relationValues", leafCount(relations),
                "signalValues", leafCount(signals),
                "codeSearchValues", leafCount(codeSearch),
                "handoffValues", leafCount(handoff),
                "sourceCoverageValues", leafCount(sourceCoverage),
                "openQuestions", openQuestions.size(),
                "sourceRefs", sourceRefs.size()
        );
        var total = counts(
                "overviewValues", leafCount(entity.overview()),
                "relationValues", leafCount(entity.relations()),
                "signalValues", leafCount(entity.signals()),
                "codeSearchValues", leafCount(entity.codeSearch()),
                "handoffValues", leafCount(entity.handoff()),
                "sourceCoverageValues", leafCount(entity.sourceCoverage()),
                "openQuestions", entity.openQuestions().size(),
                "sourceRefs", entity.sourceRefs().size()
        );
        return truncation("entity detail compacted for default tool profile", returned, total);
    }

    private OpctxTruncation truncation(
            String reason,
            Map<String, Integer> returnedCounts,
            Map<String, Integer> totalCounts
    ) {
        var omitted = new LinkedHashMap<String, Integer>();
        totalCounts.forEach((key, total) -> {
            var returned = returnedCounts.getOrDefault(key, 0);
            var omittedCount = Math.max(0, total - returned);
            if (omittedCount > 0) {
                omitted.put(key, omittedCount);
            }
        });
        return new OpctxTruncation(!omitted.isEmpty(), omitted.isEmpty() ? null : reason, returnedCounts, omitted);
    }

    private OpctxToolLink toolLink(String rel, String tool, Map<String, Object> arguments, String reason) {
        return new OpctxToolLink(rel, tool, arguments, reason);
    }

    private Map<String, Integer> counts(Object... keysAndValues) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var index = 0; index + 1 < keysAndValues.length; index += 2) {
            var key = keysAndValues[index];
            var value = keysAndValues[index + 1];
            if (key != null && value instanceof Number number) {
                counts.put(key.toString(), number.intValue());
            }
        }
        return Map.copyOf(counts);
    }

    private Map<String, List<String>> compactFacets(Map<String, List<String>> facets) {
        if (facets == null || facets.isEmpty()) {
            return Map.of();
        }
        var compacted = new LinkedHashMap<String, List<String>>();
        facets.entrySet().stream()
                .limit(DEFAULT_SECTION_MAP_LIMIT)
                .forEach(entry -> compacted.put(entry.getKey(), limitText(entry.getValue(), DEFAULT_SECTION_LIST_LIMIT)));
        return Map.copyOf(compacted);
    }

    private Map<String, Object> compactMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return compactMap(value, 0);
    }

    private Map<String, Object> compactMap(Map<String, Object> value, int depth) {
        var compacted = new LinkedHashMap<String, Object>();
        value.entrySet().stream()
                .limit(DEFAULT_SECTION_MAP_LIMIT)
                .forEach(entry -> {
                    var compactValue = compactValue(entry.getValue(), depth + 1);
                    if (compactValue != null) {
                        compacted.put(entry.getKey(), compactValue);
                    }
                });
        return Map.copyOf(compacted);
    }

    @SuppressWarnings("unchecked")
    private Object compactValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            if (depth > 4) {
                return Map.of("omittedBecause", "nested data compacted");
            }
            var typed = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> {
                if (key != null) {
                    typed.put(key.toString(), item);
                }
            });
            return compactMap(typed, depth);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .limit(DEFAULT_SECTION_LIST_LIMIT)
                    .map(item -> compactValue(item, depth + 1))
                    .filter(item -> item != null)
                    .toList();
        }
        return value;
    }

    private int leafCount(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().mapToInt(this::leafCount).sum();
        }
        if (value instanceof List<?> list) {
            return list.stream().mapToInt(this::leafCount).sum();
        }
        return 1;
    }

    private List<String> limitationsFromSourceCoverage(Map<String, Object> sourceCoverage) {
        var limitations = sourceCoverage.get("limitations");
        return limitText(textValues(limitations), DEFAULT_SECTION_LIST_LIMIT);
    }

    private String labelForType(String type) {
        return switch (type) {
            case TYPE_SYSTEM -> "Systems";
            case TYPE_REPOSITORY -> "Repositories";
            case TYPE_CODE_SEARCH_SCOPE -> "Code search scopes";
            case TYPE_PROCESS -> "Processes";
            case TYPE_INTEGRATION -> "Integrations";
            case TYPE_BOUNDED_CONTEXT -> "Bounded contexts";
            case TYPE_TEAM -> "Teams";
            case TYPE_GLOSSARY_TERM -> "Glossary terms";
            case TYPE_HANDOFF_RULE -> "Handoff rules";
            default -> type;
        };
    }

    private List<String> typeOrder() {
        return List.of(
                TYPE_SYSTEM,
                TYPE_REPOSITORY,
                TYPE_CODE_SEARCH_SCOPE,
                TYPE_PROCESS,
                TYPE_INTEGRATION,
                TYPE_BOUNDED_CONTEXT,
                TYPE_TEAM,
                TYPE_GLOSSARY_TERM,
                TYPE_HANDOFF_RULE
        );
    }

    private List<OpctxOpenQuestion> openQuestionsFor(
            List<OperationalContextOpenQuestion> openQuestions,
            String type,
            String id
    ) {
        var normalizedType = normalize(type);
        var normalizedId = normalize(id);
        return safeList(openQuestions).stream()
                .filter(question -> matchesOpenQuestionType(question.entityType(), normalizedType))
                .filter(question -> !StringUtils.hasText(question.entityId()) || normalize(question.entityId()).equals(normalizedId))
                .map(question -> new OpctxOpenQuestion(
                        question.id(),
                        question.sourceFile(),
                        question.question(),
                        question.severity(),
                        question.status()
                ))
                .toList();
    }

    private boolean matchesOpenQuestionType(String candidateType, String normalizedType) {
        if (!StringUtils.hasText(candidateType)) {
            return false;
        }
        try {
            return normalize(normalizeType(candidateType)).equals(normalizedType);
        } catch (IllegalArgumentException exception) {
            return normalize(candidateType).equals(normalizedType);
        }
    }

    private List<String> teamIds(
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextReferences references
    ) {
        var values = new LinkedHashSet<String>();
        values.addAll(safeList(references.teams()));
        safeList(responsibilities).forEach(responsibility -> values.add(responsibility.teamId()));
        return textValues(values);
    }

    private Map<String, Object> references(OperationalContextReferences references) {
        return values(
                "systems", references.systems(),
                "deploymentComponents", references.deploymentComponents(),
                "repositories", references.repositories(),
                "modules", references.modules(),
                "processes", references.processes(),
                "boundedContexts", references.boundedContexts(),
                "integrations", references.integrations(),
                "terms", references.terms(),
                "teams", references.teams(),
                "externalParties", references.externalParties(),
                "dataStores", references.dataStores(),
                "handoffRules", references.handoffRules()
        );
    }

    private List<Map<String, Object>> responsibilities(List<OperationalContextResponsibility> responsibilities) {
        return safeList(responsibilities).stream()
                .map(responsibility -> values(
                        "teamId", responsibility.teamId(),
                        "actorType", responsibility.actorType(),
                        "actorId", responsibility.actorId(),
                        "targetType", responsibility.targetType(),
                        "targetId", responsibility.targetId(),
                        "role", responsibility.role(),
                        "scope", responsibility.scope(),
                        "status", responsibility.status(),
                        "confidence", responsibility.confidence(),
                        "evidence", responsibility.evidence(),
                        "source", responsibility.source()
                ))
                .toList();
    }

    private Map<String, Object> matchSignals(OperationalContextMatchSignals matchSignals) {
        return values(
                "exact", matchSignals.exact().valuesByKey(),
                "strong", matchSignals.strong().valuesByKey(),
                "medium", matchSignals.medium().valuesByKey(),
                "weak", matchSignals.weak().valuesByKey()
        );
    }

    private List<Map<String, Object>> relations(List<OperationalContextRelation> relations) {
        return safeList(relations).stream()
                .map(relation -> values(
                        "type", relation.type(),
                        "targetType", relation.targetType(),
                        "targetContextId", relation.targetContextId(),
                        "target", relation.target(),
                        "via", relation.via(),
                        "evidence", relation.evidence()
                ))
                .toList();
    }

    private Map<String, Object> handoff(OperationalContextHandoffHints handoffHints) {
        return values(
                "defaultRouteLabel", handoffHints.defaultRouteLabel(),
                "firstResponderTeamIds", handoffHints.firstResponderTeamIds(),
                "escalationTeamIds", handoffHints.escalationTeamIds(),
                "partnerTeamIds", handoffHints.partnerTeamIds(),
                "platformSupportTeamIds", handoffHints.platformSupportTeamIds(),
                "externalRouteLabels", handoffHints.externalRouteLabels(),
                "requiredEvidence", handoffHints.requiredEvidence(),
                "preferredEvidence", handoffHints.preferredEvidence(),
                "expectedFirstActions", handoffHints.expectedFirstActions(),
                "whenToRouteHere", handoffHints.whenToRouteHere(),
                "whenToInvolveAsPartner", handoffHints.whenToInvolveAsPartner(),
                "whenNotToRouteHere", handoffHints.whenNotToRouteHere(),
                "fallbackIfAmbiguous", handoffHints.fallbackIfAmbiguous(),
                "notes", handoffHints.notes()
        );
    }

    private Map<String, Object> deployment(OperationalContextDeployment deployment) {
        return values(
                "serviceNames", deployment.serviceNames(),
                "applicationNames", deployment.applicationNames(),
                "containerNames", deployment.containerNames(),
                "deploymentNames", deployment.deploymentNames(),
                "namespaceNames", deployment.namespaceNames(),
                "imageNames", deployment.imageNames(),
                "artifactNames", deployment.artifactNames()
        );
    }

    private Map<String, Object> git(OperationalContextGit git) {
        return values(
                "provider", git.provider(),
                "group", git.group(),
                "project", git.project(),
                "projectPath", git.projectPath(),
                "defaultBranch", git.defaultBranch(),
                "url", git.url(),
                "aliases", git.aliases(),
                "inferred", git.inferred()
        );
    }

    private Map<String, Object> sourceLayout(OperationalContextSourceLayout sourceLayout) {
        return values(
                "repositoryRoot", sourceLayout.repositoryRoot(),
                "buildTool", sourceLayout.buildTool(),
                "buildFiles", sourceLayout.buildFiles(),
                "sourceRoots", sourceLayout.sourceRoots(),
                "testRoots", sourceLayout.testRoots(),
                "resourceRoots", sourceLayout.resourceRoots(),
                "modulePaths", sourceLayout.modulePaths(),
                "generatedSourcePaths", sourceLayout.generatedSourcePaths(),
                "importantPaths", sourceLayout.importantPaths(),
                "configurationFiles", sourceLayout.configurationFiles(),
                "deploymentFiles", sourceLayout.deploymentFiles(),
                "databaseMigrationPaths", sourceLayout.databaseMigrationPaths(),
                "workflowDefinitionPaths", sourceLayout.workflowDefinitionPaths(),
                "documentationPaths", sourceLayout.documentationPaths()
        );
    }

    private Map<String, Object> repositoryModule(OperationalContextRepositoryModule module) {
        return values(
                "id", module.effectiveId(),
                "name", module.name(),
                "moduleType", module.moduleType(),
                "lifecycleStatus", module.lifecycleStatus(),
                "sourceRoots", module.sourceRoots(),
                "importantPaths", module.importantPaths(),
                "packagePrefixes", module.packagePrefixSignals(),
                "classHints", module.classHintSignals()
        );
    }

    private Map<String, Object> processStep(OperationalContextProcessStep step) {
        return values(
                "id", step.id(),
                "name", step.name(),
                "type", step.type(),
                "summary", step.summary(),
                "references", references(step.references()),
                "matchSignals", matchSignals(step.matchSignals())
        );
    }

    private Map<String, Object> integrationParticipant(OperationalContextIntegrationParticipant participant) {
        return values(
                "system", participant.system(),
                "boundedContext", participant.boundedContext(),
                "repositories", participant.repositories(),
                "modules", participant.modules(),
                "role", participant.role(),
                "externalOwner", participant.externalOwner(),
                "notes", participant.notes()
        );
    }

    private Map<String, Object> transport(OperationalContextTransport transport) {
        return values(
                "protocols", transport.protocols(),
                "http", values(
                        "methods", transport.http().methods(),
                        "endpointPrefixes", transport.http().endpointPrefixes(),
                        "endpointTemplates", transport.http().endpointTemplates(),
                        "operationNames", transport.http().operationNames(),
                        "hosts", transport.http().hosts(),
                        "hostPatterns", transport.http().hostPatterns(),
                        "clientNames", transport.http().clientNames(),
                        "gatewayRoutes", transport.http().gatewayRoutes()
                ),
                "messaging", values(
                        "brokers", transport.messaging().brokers(),
                        "exchanges", transport.messaging().exchanges(),
                        "queues", transport.messaging().queues(),
                        "topics", transport.messaging().topics(),
                        "routingKeys", transport.messaging().routingKeys(),
                        "consumerGroups", transport.messaging().consumerGroups()
                ),
                "database", values(
                        "datasourceNames", transport.database().datasourceNames(),
                        "schemas", transport.database().schemas(),
                        "tables", transport.database().tables(),
                        "entities", transport.database().entities(),
                        "repositories", transport.database().repositories()
                )
        );
    }

    private Map<String, Object> channel(OperationalContextChannel channel) {
        return values(
                "type", channel.type(),
                "name", channel.name(),
                "direction", channel.direction(),
                "signals", channel.signals()
        );
    }

    private Map<String, Object> implementation(OperationalContextImplementation implementation) {
        return values(
                "localSide", implementation.localSide(),
                "clientTypes", implementation.clientTypes(),
                "packagePrefixes", implementation.packagePrefixes(),
                "modulePaths", implementation.modulePaths(),
                "classHints", implementation.classHints(),
                "clientClasses", implementation.clientClasses(),
                "controllerClasses", implementation.controllerClasses(),
                "listenerClasses", implementation.listenerClasses(),
                "publisherClasses", implementation.publisherClasses(),
                "schedulerClasses", implementation.schedulerClasses(),
                "generatedClientClasses", implementation.generatedClientClasses(),
                "configClasses", implementation.configClasses()
        );
    }

    private Map<String, Object> codeSearchScopeSummary(
            OperationalContextRepositorySearchScope scope,
            List<OperationalContextRepository> repositories
    ) {
        var repositoriesById = repositories.stream()
                .collect(Collectors.toMap(
                        OperationalContextRepository::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return values(
                "id", scope.id(),
                "name", scope.name(),
                "target", repositorySearchTarget(scope.target()),
                "repositories", scope.repositories().stream()
                        .map(repository -> scopeRepository(repository, repositoriesById))
                        .toList(),
                "packagePrefixes", scope.packagePrefixes(),
                "classHints", scope.classHints(),
                "endpointHints", scope.endpointHints(),
                "queueTopicHints", scope.queueTopicHints(),
                "traversal", traversal(scope.traversal()),
                "limitations", scope.limitations()
        );
    }

    private String codeSearchScopeSummary(OperationalContextRepositorySearchScope scope) {
        if (!StringUtils.hasText(scope.target().value())) {
            return "Code search scope for repositories: " + String.join(", ", scope.repositories().stream()
                    .map(OperationalContextRepositorySearchRepository::repoId)
                    .toList());
        }
        return "Code search scope for " + scope.target().value();
    }

    private Map<String, Object> repositorySearchTarget(OperationalContextRepositorySearchTarget target) {
        return values(
                "type", target.type(),
                "id", target.id()
        );
    }

    private boolean semanticTargetMatches(
            OperationalContextRepositorySearchTarget target,
            String expectedType,
            String expectedId
    ) {
        return normalize(target.type()).equals(normalize(expectedType))
                && normalize(target.id()).equals(normalize(expectedId));
    }

    private Map<String, Object> scopeRepository(
            OperationalContextRepositorySearchRepository scopeRepository,
            Map<String, OperationalContextRepository> repositoriesById
    ) {
        var repository = repositoriesById.get(scopeRepository.repoId());
        return values(
                "repoId", scopeRepository.repoId(),
                "role", scopeRepository.role(),
                "priority", scopeRepository.priority(),
                "moduleIds", scopeRepository.moduleIds(),
                "reason", scopeRepository.reason(),
                "readFor", scopeRepository.readFor(),
                "projectName", repository != null ? repository.git().project() : null,
                "gitLabPath", repository != null ? repository.git().projectPath() : null,
                "packagePrefixes", repository != null ? repository.packagePrefixSignals() : List.of()
        );
    }

    private Map<String, Object> databaseHints(OperationalContextRepositorySearchDatabaseHints hints) {
        return values(
                "datasourceNames", hints.datasourceNames(),
                "hikariPools", hints.hikariPools(),
                "schemas", hints.schemas(),
                "tables", hints.tables(),
                "entities", hints.entities(),
                "migrations", hints.migrations()
        );
    }

    private Map<String, Object> workflowHints(OperationalContextRepositorySearchWorkflowHints hints) {
        return values(
                "jobNames", hints.jobNames(),
                "workflowNames", hints.workflowNames(),
                "definitionPaths", hints.definitionPaths()
        );
    }

    private Map<String, Object> traversal(OperationalContextRepositorySearchTraversal traversal) {
        return values(
                "rules", traversal.rules(),
                "expandWhen", traversal.expandWhen()
        );
    }

    private Map<String, List<String>> facets(Object... keysAndValues) {
        var values = new LinkedHashMap<String, List<String>>();
        for (var index = 0; index + 1 < keysAndValues.length; index += 2) {
            var key = (String) keysAndValues[index];
            var list = textValues(keysAndValues[index + 1]);
            if (!list.isEmpty()) {
                values.put(key, list);
            }
        }
        return Map.copyOf(values);
    }

    private Map<String, Object> values(Object... keysAndValues) {
        var values = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < keysAndValues.length; index += 2) {
            var key = (String) keysAndValues[index];
            var value = cleanValue(keysAndValues[index + 1]);
            if (value != null) {
                values.put(key, value);
            }
        }
        return Map.copyOf(values);
    }

    private Object cleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text.trim() : null;
        }
        if (value instanceof List<?> list) {
            var cleaned = list.stream()
                    .map(this::cleanValue)
                    .filter(item -> item != null)
                    .toList();
            return cleaned.isEmpty() ? null : cleaned;
        }
        if (value instanceof Map<?, ?> map) {
            var cleaned = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> {
                if (key == null) {
                    return;
                }
                var cleanedValue = cleanValue(item);
                if (cleanedValue != null) {
                    cleaned.put(key.toString(), cleanedValue);
                }
            });
            return cleaned.isEmpty() ? null : Map.copyOf(cleaned);
        }
        return value;
    }

    private List<String> flattenFacets(Map<String, List<String>> facets) {
        return facets.values().stream()
                .flatMap(List::stream)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> textValues(Object... values) {
        var result = new LinkedHashSet<String>();
        for (var value : values) {
            appendTextValues(result, value);
        }
        return List.copyOf(result);
    }

    private void appendTextValues(Set<String> result, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            if (StringUtils.hasText(text)) {
                result.add(text.trim());
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> appendTextValues(result, item));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> appendTextValues(result, item));
            return;
        }
        if (value instanceof Boolean || value instanceof Number) {
            return;
        }
        result.add(value.toString());
    }

    private List<String> join(List<String> left, List<String> right) {
        return textValues(left, right);
    }

    private List<String> limit(Set<String> values, int maxItems) {
        return values.stream().limit(maxItems).toList();
    }

    private List<String> limitText(List<String> values, int maxItems) {
        if (values == null || values.isEmpty() || maxItems <= 0) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(maxItems)
                .toList();
    }

    private <T> List<T> limitList(List<T> values, int maxItems) {
        if (values == null || values.isEmpty() || maxItems <= 0) {
            return List.of();
        }
        return values.stream().limit(maxItems).toList();
    }

    private String first(List<String> values) {
        return safeList(values).stream().filter(StringUtils::hasText).findFirst().orElse(null);
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private int normalizePositive(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private boolean containsNormalized(List<String> values, String expected) {
        var normalizedExpected = normalize(expected);
        return safeList(values).stream().map(this::normalize).anyMatch(normalizedExpected::equals);
    }

    private List<String> tokens(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(normalize(value).split("\\s+"))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private record OpctxCatalogIndex(
            List<OpctxCatalogEntity> entities,
            Map<String, List<OpctxCatalogEntity>> entitiesByType
    ) {
    }

    private record OpctxCatalogEntity(
            String type,
            String id,
            String label,
            String summary,
            String purpose,
            List<String> identityValues,
            List<String> summaryValues,
            List<String> signalValues,
            List<String> relationValues,
            Map<String, List<String>> facets,
            Map<String, Object> overview,
            Map<String, Object> relations,
            Map<String, Object> signals,
            Map<String, Object> codeSearch,
            Map<String, Object> handoff,
            Map<String, Object> sourceCoverage,
            List<OpctxOpenQuestion> openQuestions,
            List<String> sourceRefs
    ) {
    }

    private record OpctxScoredEntity(
            OpctxCatalogEntity entity,
            int score,
            List<String> matchedFields,
            List<String> matchedSignals,
            String why
    ) {
    }
}
