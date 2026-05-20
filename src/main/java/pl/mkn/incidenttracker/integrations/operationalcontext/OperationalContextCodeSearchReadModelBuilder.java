package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchScopeView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.DatabaseHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.GitView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.ModuleView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.RepositoryView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.SourceLayoutView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.TraversalView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.WorkflowHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositoryModule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class OperationalContextCodeSearchReadModelBuilder {

    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String REPOSITORY = "repository";
    private static final String MODULE = "module";

    private final OperationalContextRelationIndexBuilder relationIndexBuilder;

    public OperationalContextCodeSearchReadModelBuilder() {
        this(new OperationalContextRelationIndexBuilder());
    }

    public OperationalContextCodeSearchReadModelBuilder(
            OperationalContextRelationIndexBuilder relationIndexBuilder
    ) {
        this.relationIndexBuilder = relationIndexBuilder;
    }

    public OperationalContextCodeSearchReadModel buildForEntity(
            OperationalContextCatalog catalog,
            String entityType,
            String entityId
    ) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var relationIndex = relationIndexBuilder.build(safeCatalog);
        var target = new EntityKey(normalizeEntityType(entityType), entityId);
        var analysisTarget = relationIndex.entities().getOrDefault(target, EntityRef.fromKey(target));
        var scopesById = scopesById(safeCatalog.codeSearchScopes());
        var repositoriesById = repositoriesById(safeCatalog.repositories());
        var scopeIds = matchingScopeIds(relationIndex, target);
        var findings = new ArrayList<ValidationFinding>();

        if (scopeIds.isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "NO_CODE_SEARCH_SCOPE",
                    "No code-search scope targets " + target.value() + ".",
                    List.of(new SourceRef(
                            "src/main/resources/operational-context/repo-map.yml",
                            target.type(),
                            target.id(),
                            "$.codeSearchScopes",
                            "code-search-scope-lookup"
                    ))
            ));
            return OperationalContextCodeSearchReadModel.empty(analysisTarget, findings);
        }

        var scopeViews = new ArrayList<CodeSearchScopeView>();
        var repositoryViews = new LinkedHashMap<String, RepositoryView>();
        var limitations = new ArrayList<String>();

        for (var scopeId : scopeIds) {
            var scope = scopesById.get(scopeId);
            if (scope == null) {
                findings.add(new ValidationFinding(
                        "warning",
                        "UNKNOWN_CODE_SEARCH_SCOPE",
                        "Relation index points to unknown code-search scope " + scopeId + ".",
                        List.of()
                ));
                continue;
            }

            scopeViews.add(scopeView(relationIndex, scope, repositoriesById));
            limitations.addAll(scope.limitations());
            for (var scopeRepository : scope.repositories()) {
                var repositoryView = repositoryView(scope, scopeRepository, repositoriesById, findings);
                if (repositoryView != null) {
                    repositoryViews.putIfAbsent(repositoryView.repository().id(), repositoryView);
                }
            }
        }

        var repositories = repositoryViews.values().stream()
                .sorted(Comparator
                        .comparing((RepositoryView repository) -> repository.priority() != null
                                ? repository.priority()
                                : Integer.MAX_VALUE)
                        .thenComparing(repository -> repository.repository().id()))
                .toList();

        return new OperationalContextCodeSearchReadModel(
                "operational-context.code-search",
                1,
                OperationalContextCodeSearchReadModel.ReadModelProfile.defaultProfile(),
                analysisTarget,
                scopeViews,
                repositories,
                aggregateHints(scopeViews, repositories),
                distinct(limitations),
                distinctFindings(findings)
        );
    }

    private List<String> matchingScopeIds(
            OperationalContextRelationIndex relationIndex,
            EntityKey target
    ) {
        if (CODE_SEARCH_SCOPE.equals(target.type())) {
            return relationIndex.entities().containsKey(target) ? List.of(target.id()) : List.of();
        }

        return relationIndex.entityRelations(target.type(), target.id()).incomingRelations().stream()
                .filter(relation -> CODE_SEARCH_SCOPE.equals(relation.source().type()))
                .filter(relation -> relation.relationType().startsWith("targets-"))
                .map(relation -> relation.source().id())
                .distinct()
                .toList();
    }

    private CodeSearchScopeView scopeView(
            OperationalContextRelationIndex relationIndex,
            OperationalContextRepositorySearchScope scope,
            Map<String, OperationalContextRepository> repositoriesById
    ) {
        var scopeKey = new EntityKey(CODE_SEARCH_SCOPE, scope.id());
        var scopeRelations = relationIndex.entityRelations(CODE_SEARCH_SCOPE, scope.id());
        var targetRefs = scopeRelations.outgoingRelations().stream()
                .filter(relation -> relation.relationType().startsWith("targets-"))
                .map(relation -> ref(relationIndex, relation.target()))
                .distinct()
                .toList();
        var repositoryRefs = scope.repositories().stream()
                .map(OperationalContextRepositorySearchRepository::repoId)
                .filter(StringUtils::hasText)
                .map(repoId -> repositoriesById.containsKey(repoId)
                        ? repositoryRef(repositoriesById.get(repoId))
                        : EntityRef.fromKey(new EntityKey(REPOSITORY, repoId)))
                .distinct()
                .toList();

        return new CodeSearchScopeView(
                ref(relationIndex, scopeKey),
                scope.scopeType(),
                targetRefs.stream().findFirst().orElse(null),
                repositoryRefs,
                scopeHints(scope),
                traversal(scope),
                scope.limitations(),
                new Provenance(
                        true,
                        "direct-yaml",
                        "high",
                        List.of(sourceRef(CODE_SEARCH_SCOPE, scope.id(), "$.codeSearchScopes[id=" + scope.id() + "]", "code-search-scope")),
                        List.of()
                )
        );
    }

    private RepositoryView repositoryView(
            OperationalContextRepositorySearchScope scope,
            OperationalContextRepositorySearchRepository scopeRepository,
            Map<String, OperationalContextRepository> repositoriesById,
            List<ValidationFinding> findings
    ) {
        if (!StringUtils.hasText(scopeRepository.repoId())) {
            return null;
        }

        var repository = repositoriesById.get(scopeRepository.repoId());
        if (repository == null) {
            findings.add(new ValidationFinding(
                    "error",
                    "UNKNOWN_CODE_SEARCH_REPOSITORY",
                    "Code-search scope " + scope.id() + " references unknown repository " + scopeRepository.repoId() + ".",
                    List.of(sourceRef(CODE_SEARCH_SCOPE, scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].repositories[repoId=" + scopeRepository.repoId() + "]", "references-repository"))
            ));
            return null;
        }

        var modules = selectedModules(repository, scopeRepository.moduleIds()).stream()
                .map(this::moduleView)
                .toList();
        var hints = repositoryHints(scope, repository, modules);

        return new RepositoryView(
                repositoryRef(repository),
                scopeRepository.role(),
                scopeRepository.priority(),
                scopeRepository.reason(),
                scopeRepository.readFor(),
                gitView(repository),
                sourceLayoutView(repository),
                modules,
                hints,
                new Provenance(
                        true,
                        "direct-yaml",
                        "high",
                        List.of(
                                sourceRef(CODE_SEARCH_SCOPE, scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].repositories[repoId=" + repository.id() + "]", "references-repository"),
                                sourceRef(REPOSITORY, repository.id(), "$.repositories[id=" + repository.id() + "]", "repository-details")
                        ),
                        List.of()
                )
        );
    }

    private List<OperationalContextRepositoryModule> selectedModules(
            OperationalContextRepository repository,
            List<String> moduleIds
    ) {
        var requested = new LinkedHashSet<>(textList(moduleIds));
        if (requested.isEmpty()) {
            return repository.modules();
        }
        return repository.modules().stream()
                .filter(module -> requested.contains(module.effectiveId()) || requested.contains(module.id()))
                .toList();
    }

    private ModuleView moduleView(OperationalContextRepositoryModule module) {
        return new ModuleView(
                module.effectiveId(),
                module.name(),
                module.moduleType(),
                module.lifecycleStatus(),
                module.source().paths(),
                module.source().packages(),
                module.sourceRoots(),
                module.importantPaths(),
                new CodeSearchHints(
                        module.packagePrefixSignals(),
                        module.classHintSignals(),
                        module.endpointPrefixSignals(),
                        List.of(),
                        DatabaseHints.empty(),
                        WorkflowHints.empty()
                )
        );
    }

    private CodeSearchHints scopeHints(OperationalContextRepositorySearchScope scope) {
        return new CodeSearchHints(
                scope.packagePrefixes(),
                scope.classHints(),
                scope.endpointHints(),
                scope.queueTopicHints(),
                databaseHints(scope.databaseHints(), List.of()),
                workflowHints(scope.workflowHints(), List.of())
        );
    }

    private CodeSearchHints repositoryHints(
            OperationalContextRepositorySearchScope scope,
            OperationalContextRepository repository,
            List<ModuleView> modules
    ) {
        return new CodeSearchHints(
                distinct(scope.packagePrefixes(), repository.packagePrefixSignals(), modulePackagePrefixes(modules)),
                distinct(scope.classHints(), repository.classHintSignals(), moduleClassHints(modules)),
                distinct(scope.endpointHints(), repository.endpointPrefixSignals()),
                distinct(scope.queueTopicHints(), repository.queueTopicHints()),
                databaseHints(scope.databaseHints(), repository.sourceLayout().databaseMigrationPaths()),
                workflowHints(scope.workflowHints(), repository.sourceLayout().workflowDefinitionPaths())
        );
    }

    private CodeSearchHints aggregateHints(
            List<CodeSearchScopeView> scopes,
            List<RepositoryView> repositories
    ) {
        return new CodeSearchHints(
                distinctGrouped(
                        scopes.stream().map(scope -> scope.hints().packagePrefixes()).toList(),
                        repositories.stream().map(repository -> repository.hints().packagePrefixes()).toList()
                ),
                distinctGrouped(
                        scopes.stream().map(scope -> scope.hints().classHints()).toList(),
                        repositories.stream().map(repository -> repository.hints().classHints()).toList()
                ),
                distinctGrouped(
                        scopes.stream().map(scope -> scope.hints().endpointHints()).toList(),
                        repositories.stream().map(repository -> repository.hints().endpointHints()).toList()
                ),
                distinctGrouped(
                        scopes.stream().map(scope -> scope.hints().queueTopicHints()).toList(),
                        repositories.stream().map(repository -> repository.hints().queueTopicHints()).toList()
                ),
                new DatabaseHints(
                        distinctDatabases(scopes, repositories, DatabaseField.DATASOURCE_NAMES),
                        distinctDatabases(scopes, repositories, DatabaseField.HIKARI_POOLS),
                        distinctDatabases(scopes, repositories, DatabaseField.SCHEMAS),
                        distinctDatabases(scopes, repositories, DatabaseField.TABLES),
                        distinctDatabases(scopes, repositories, DatabaseField.ENTITIES),
                        distinctDatabases(scopes, repositories, DatabaseField.MIGRATIONS)
                ),
                new WorkflowHints(
                        distinctWorkflows(scopes, repositories, WorkflowField.JOB_NAMES),
                        distinctWorkflows(scopes, repositories, WorkflowField.WORKFLOW_NAMES),
                        distinctWorkflows(scopes, repositories, WorkflowField.DEFINITION_PATHS)
                )
        );
    }

    private DatabaseHints databaseHints(
            OperationalContextDtos.OperationalContextRepositorySearchDatabaseHints hints,
            List<String> repositoryMigrations
    ) {
        return new DatabaseHints(
                hints.datasourceNames(),
                hints.hikariPools(),
                hints.schemas(),
                hints.tables(),
                hints.entities(),
                distinct(hints.migrations(), repositoryMigrations)
        );
    }

    private WorkflowHints workflowHints(
            OperationalContextDtos.OperationalContextRepositorySearchWorkflowHints hints,
            List<String> repositoryWorkflowDefinitionPaths
    ) {
        return new WorkflowHints(
                hints.jobNames(),
                hints.workflowNames(),
                distinct(hints.definitionPaths(), repositoryWorkflowDefinitionPaths)
        );
    }

    private TraversalView traversal(OperationalContextRepositorySearchScope scope) {
        var traversal = scope.traversal();
        return new TraversalView(
                traversal.rules(),
                traversal.expandWhen()
        );
    }

    private SourceLayoutView sourceLayoutView(OperationalContextRepository repository) {
        var layout = repository.sourceLayout();
        return new SourceLayoutView(
                layout.repositoryRoot(),
                layout.buildTool(),
                layout.buildFiles(),
                layout.sourceRoots(),
                layout.testRoots(),
                layout.resourceRoots(),
                layout.modulePaths(),
                layout.generatedSourcePaths(),
                layout.importantPaths(),
                layout.configurationFiles(),
                layout.deploymentFiles(),
                layout.databaseMigrationPaths(),
                layout.workflowDefinitionPaths(),
                layout.documentationPaths()
        );
    }

    private GitView gitView(OperationalContextRepository repository) {
        var git = repository.git();
        return new GitView(
                git.provider(),
                git.group(),
                git.project(),
                git.projectPath(),
                git.defaultBranch(),
                git.url()
        );
    }

    private EntityRef ref(
            OperationalContextRelationIndex relationIndex,
            EntityKey key
    ) {
        return relationIndex.entities().getOrDefault(key, EntityRef.fromKey(key));
    }

    private EntityRef repositoryRef(OperationalContextRepository repository) {
        return new EntityRef(
                REPOSITORY,
                repository.id(),
                repository.label(),
                repository.lifecycleStatus(),
                repository.summary()
        );
    }

    private Map<String, OperationalContextRepositorySearchScope> scopesById(
            List<OperationalContextRepositorySearchScope> scopes
    ) {
        var result = new LinkedHashMap<String, OperationalContextRepositorySearchScope>();
        for (var scope : scopes) {
            if (StringUtils.hasText(scope.id())) {
                result.putIfAbsent(scope.id(), scope);
            }
        }
        return result;
    }

    private Map<String, OperationalContextRepository> repositoriesById(
            List<OperationalContextRepository> repositories
    ) {
        var result = new LinkedHashMap<String, OperationalContextRepository>();
        for (var repository : repositories) {
            if (StringUtils.hasText(repository.id())) {
                result.putIfAbsent(repository.id(), repository);
            }
        }
        return result;
    }

    private SourceRef sourceRef(
            String entityType,
            String entityId,
            String fieldPath,
            String relationRole
    ) {
        return new SourceRef(
                "src/main/resources/operational-context/repo-map.yml",
                entityType,
                entityId,
                fieldPath,
                relationRole
        );
    }

    private String normalizeEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        var normalized = entityType.trim().replace("_", "-").replace(" ", "-");
        return switch (normalized) {
            case "codeSearchScope", "codeSearchScopes", "code-search-scopes" -> CODE_SEARCH_SCOPE;
            case "boundedContext", "boundedContexts", "bounded-contexts" -> "bounded-context";
            case "systems" -> "system";
            case "processes" -> "process";
            case "integrations" -> "integration";
            case "repositories" -> REPOSITORY;
            case "terms", "glossary-term", "glossary-terms" -> "term";
            default -> normalized;
        };
    }

    private List<String> modulePackagePrefixes(List<ModuleView> modules) {
        return modules.stream()
                .map(module -> module.hints().packagePrefixes())
                .flatMap(Collection::stream)
                .toList();
    }

    private List<String> moduleClassHints(List<ModuleView> modules) {
        return modules.stream()
                .map(module -> module.hints().classHints())
                .flatMap(Collection::stream)
                .toList();
    }

    private List<String> distinctDatabases(
            List<CodeSearchScopeView> scopes,
            List<RepositoryView> repositories,
            DatabaseField field
    ) {
        return distinctGrouped(
                scopes.stream().map(scope -> databaseValues(scope.hints().databaseHints(), field)).toList(),
                repositories.stream().map(repository -> databaseValues(repository.hints().databaseHints(), field)).toList()
        );
    }

    private List<String> databaseValues(DatabaseHints hints, DatabaseField field) {
        return switch (field) {
            case DATASOURCE_NAMES -> hints.datasourceNames();
            case HIKARI_POOLS -> hints.hikariPools();
            case SCHEMAS -> hints.schemas();
            case TABLES -> hints.tables();
            case ENTITIES -> hints.entities();
            case MIGRATIONS -> hints.migrations();
        };
    }

    private List<String> distinctWorkflows(
            List<CodeSearchScopeView> scopes,
            List<RepositoryView> repositories,
            WorkflowField field
    ) {
        return distinctGrouped(
                scopes.stream().map(scope -> workflowValues(scope.hints().workflowHints(), field)).toList(),
                repositories.stream().map(repository -> workflowValues(repository.hints().workflowHints(), field)).toList()
        );
    }

    private List<String> workflowValues(WorkflowHints hints, WorkflowField field) {
        return switch (field) {
            case JOB_NAMES -> hints.jobNames();
            case WORKFLOW_NAMES -> hints.workflowNames();
            case DEFINITION_PATHS -> hints.definitionPaths();
        };
    }

    @SafeVarargs
    private final List<String> distinct(List<String>... values) {
        var result = new LinkedHashSet<String>();
        for (var current : values) {
            result.addAll(textList(current));
        }
        return List.copyOf(result);
    }

    @SafeVarargs
    private final List<String> distinctGrouped(List<List<String>>... groupedValues) {
        var result = new LinkedHashSet<String>();
        for (var group : groupedValues) {
            for (var values : group) {
                result.addAll(textList(values));
            }
        }
        return List.copyOf(result);
    }

    private List<String> textList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private List<ValidationFinding> distinctFindings(List<ValidationFinding> findings) {
        var result = new LinkedHashMap<String, ValidationFinding>();
        for (var finding : findings) {
            result.putIfAbsent(finding.code() + "|" + finding.message(), finding);
        }
        return List.copyOf(result.values());
    }

    private enum DatabaseField {
        DATASOURCE_NAMES,
        HIKARI_POOLS,
        SCHEMAS,
        TABLES,
        ENTITIES,
        MIGRATIONS
    }

    private enum WorkflowField {
        JOB_NAMES,
        WORKFLOW_NAMES,
        DEFINITION_PATHS
    }
}
