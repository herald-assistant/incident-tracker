package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchHints;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchScopeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.DatabaseHints;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.ModuleView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.RepositoryView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.WorkflowHints;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextImplementationReadModel.ImplementationView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextImplementationReadModel.ModuleImplementationView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class OperationalContextImplementationReadModelBuilder {

    private static final String SYSTEM = "system";
    private static final String PROCESS = "process";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String CODE_SEARCH_SCOPE = "code-search-scope";

    private final OperationalContextRelationIndexBuilder relationIndexBuilder;
    private final OperationalContextCodeSearchReadModelBuilder codeSearchReadModelBuilder;

    public OperationalContextImplementationReadModelBuilder() {
        this(new OperationalContextRelationIndexBuilder());
    }

    public OperationalContextImplementationReadModelBuilder(
            OperationalContextRelationIndexBuilder relationIndexBuilder
    ) {
        this.relationIndexBuilder = relationIndexBuilder;
        this.codeSearchReadModelBuilder = new OperationalContextCodeSearchReadModelBuilder(relationIndexBuilder);
    }

    public OperationalContextImplementationReadModel buildForEntity(
            OperationalContextCatalog catalog,
            String entityType,
            String entityId
    ) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var target = new EntityKey(normalizeEntityType(entityType), entityId);
        var relationIndex = relationIndexBuilder.build(safeCatalog);
        var analysisTarget = relationIndex.entities().getOrDefault(target, EntityRef.fromKey(target));
        var codeSearch = codeSearchReadModelBuilder.buildForEntity(safeCatalog, target.type(), target.id());
        var findings = new ArrayList<>(codeSearch.validationFindings());

        if (codeSearch.scopes().isEmpty()) {
            return OperationalContextImplementationReadModel.empty(analysisTarget, findings);
        }

        var repositoriesById = repositoriesById(codeSearch.repositories());
        var scopeRepositoriesByScopeId = scopeRepositoriesByScopeId(safeCatalog.codeSearchScopes());
        var implementations = new LinkedHashMap<String, ImplementationView>();
        var limitations = new ArrayList<>(codeSearch.limitations());

        for (var scope : codeSearch.scopes()) {
            var implementationTargets = implementationTargets(relationIndex, scope, target, analysisTarget);
            for (var repositoryRef : scope.repositories()) {
                var repository = repositoriesById.get(repositoryRef.id());
                if (repository == null) {
                    continue;
                }
                var scopedRepository = scopeRepositoriesByScopeId
                        .getOrDefault(scope.scope().id(), Map.of())
                        .get(repository.repository().id());
                if (repository.modules().isEmpty()) {
                    putImplementation(
                            implementations,
                            findings,
                            implementationView(scope, implementationTargets, repository, scopedRepository, null)
                    );
                    continue;
                }
                for (var module : repository.modules()) {
                    putImplementation(
                            implementations,
                            findings,
                            implementationView(scope, implementationTargets, repository, scopedRepository, module)
                    );
                }
            }
        }

        if (implementations.isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "NO_IMPLEMENTATION_REPOSITORY",
                    "No repository implementation could be projected for " + target.value() + ".",
                    codeSearch.scopes().stream()
                            .map(scope -> scope.provenance().sourceRefs())
                            .flatMap(List::stream)
                            .toList()
            ));
        }

        return new OperationalContextImplementationReadModel(
                "operational-context.implementation-map",
                1,
                codeSearch.profile(),
                analysisTarget,
                sortedImplementations(implementations),
                limitations,
                distinctFindings(findings)
        );
    }

    private ImplementationView implementationView(
            CodeSearchScopeView scope,
            ImplementationTargets targets,
            RepositoryView repository,
            OperationalContextRepositorySearchRepository scopedRepository,
            ModuleView module
    ) {
        var implementationRole = implementationRole(scopedRepository, repository);
        var priority = implementationPriority(scopedRepository, repository);
        var kind = implementationKind(implementationRole);
        var lifecycleRole = lifecycleRole(kind, implementationRole, priority, repository, module);
        var hints = mergeHints(scope.hints(), repository.hints(), module != null ? module.hints() : CodeSearchHints.empty());
        var packagePrefixes = distinct(
                scope.hints().packagePrefixes(),
                repository.hints().packagePrefixes(),
                module != null ? module.packages() : List.of(),
                module != null ? module.hints().packagePrefixes() : List.of()
        );
        var sourceRoots = distinct(
                repository.sourceLayout().sourceRoots(),
                module != null ? module.sourceRoots() : List.of()
        );
        var importantPaths = distinct(
                repository.sourceLayout().importantPaths(),
                module != null ? module.importantPaths() : List.of(),
                module != null ? module.paths() : List.of()
        );

        return new ImplementationView(
                implementationId(scope.scope().id(), repository.repository().id(), module),
                kind,
                lifecycleRole,
                migrationStatus(lifecycleRole, repository, module),
                implementationRole,
                priority,
                scope.scope(),
                targets.boundedContexts(),
                targets.systems(),
                targets.processes(),
                repository.repository(),
                module != null ? moduleView(module) : null,
                packagePrefixes,
                sourceRoots,
                importantPaths,
                hints,
                metadata(repository, scopedRepository, module),
                provenance(scope, repository, kind, targets)
        );
    }

    private ImplementationTargets implementationTargets(
            OperationalContextRelationIndex relationIndex,
            CodeSearchScopeView scope,
            EntityKey target,
            EntityRef analysisTarget
    ) {
        var systems = new LinkedHashMap<String, EntityRef>();
        var boundedContexts = new LinkedHashMap<String, EntityRef>();
        var processes = new LinkedHashMap<String, EntityRef>();

        addByType(systems, scopeTargetRefs(scope), SYSTEM);
        addByType(boundedContexts, scopeTargetRefs(scope), BOUNDED_CONTEXT);
        addByType(processes, scopeTargetRefs(scope), PROCESS);

        if (SYSTEM.equals(target.type())) {
            systems.putIfAbsent(analysisTarget.id(), analysisTarget);
        }
        if (BOUNDED_CONTEXT.equals(target.type())) {
            boundedContexts.putIfAbsent(analysisTarget.id(), analysisTarget);
        }
        if (PROCESS.equals(target.type())) {
            processes.putIfAbsent(analysisTarget.id(), analysisTarget);
        }

        if (systems.isEmpty()) {
            addByType(systems, relationIndex.entityRelations(target.type(), target.id()).neighbors(), SYSTEM);
        }
        if (boundedContexts.isEmpty()) {
            addByType(boundedContexts, relationIndex.entityRelations(target.type(), target.id()).neighbors(), BOUNDED_CONTEXT);
        }
        if (processes.isEmpty()) {
            addByType(processes, relationIndex.entityRelations(target.type(), target.id()).neighbors(), PROCESS);
        }

        return new ImplementationTargets(
                List.copyOf(boundedContexts.values()),
                List.copyOf(systems.values()),
                List.copyOf(processes.values())
        );
    }

    private List<EntityRef> scopeTargetRefs(CodeSearchScopeView scope) {
        return scope.target() != null ? List.of(scope.target()) : List.of();
    }

    private void putImplementation(
            Map<String, ImplementationView> implementations,
            List<ValidationFinding> findings,
            ImplementationView implementation
    ) {
        implementations.putIfAbsent(implementation.id(), implementation);
        validateImplementationShape(implementation, findings);
    }

    private void validateImplementationShape(
            ImplementationView implementation,
            List<ValidationFinding> findings
    ) {
        if (implementation.systems().isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "IMPLEMENTATION_WITHOUT_SYSTEM",
                    "Implementation " + implementation.id() + " has no system target in the current read model.",
                    implementation.provenance().sourceRefs()
            ));
        }
        if ("implementation".equals(implementation.implementationKind()) && implementation.boundedContexts().isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "IMPLEMENTATION_WITHOUT_BOUNDED_CONTEXT",
                    "Implementation " + implementation.id() + " has no bounded context target in the current read model.",
                    implementation.provenance().sourceRefs()
            ));
        }
        if (implementation.codeSearchScope() == null) {
            findings.add(new ValidationFinding(
                    "warning",
                    "IMPLEMENTATION_WITHOUT_CODE_SEARCH_SCOPE",
                    "Implementation " + implementation.id() + " has no code-search scope.",
                    implementation.provenance().sourceRefs()
            ));
        }
        if (implementation.repository() == null) {
            findings.add(new ValidationFinding(
                    "warning",
                    "IMPLEMENTATION_WITHOUT_REPOSITORY",
                    "Implementation " + implementation.id() + " has no repository.",
                    implementation.provenance().sourceRefs()
            ));
        }
    }

    private ModuleImplementationView moduleView(ModuleView module) {
        return new ModuleImplementationView(
                module.id(),
                module.name(),
                module.moduleType(),
                module.lifecycleStatus(),
                module.paths(),
                module.packages(),
                module.sourceRoots(),
                module.importantPaths()
        );
    }

    private CodeSearchHints mergeHints(
            CodeSearchHints scopeHints,
            CodeSearchHints repositoryHints,
            CodeSearchHints moduleHints
    ) {
        return new CodeSearchHints(
                distinct(scopeHints.packagePrefixes(), repositoryHints.packagePrefixes(), moduleHints.packagePrefixes()),
                distinct(scopeHints.classHints(), repositoryHints.classHints(), moduleHints.classHints()),
                distinct(scopeHints.endpointHints(), repositoryHints.endpointHints(), moduleHints.endpointHints()),
                distinct(scopeHints.queueTopicHints(), repositoryHints.queueTopicHints(), moduleHints.queueTopicHints()),
                new DatabaseHints(
                        distinct(
                                scopeHints.databaseHints().datasourceNames(),
                                repositoryHints.databaseHints().datasourceNames(),
                                moduleHints.databaseHints().datasourceNames()
                        ),
                        distinct(
                                scopeHints.databaseHints().hikariPools(),
                                repositoryHints.databaseHints().hikariPools(),
                                moduleHints.databaseHints().hikariPools()
                        ),
                        distinct(
                                scopeHints.databaseHints().schemas(),
                                repositoryHints.databaseHints().schemas(),
                                moduleHints.databaseHints().schemas()
                        ),
                        distinct(
                                scopeHints.databaseHints().tables(),
                                repositoryHints.databaseHints().tables(),
                                moduleHints.databaseHints().tables()
                        ),
                        distinct(
                                scopeHints.databaseHints().entities(),
                                repositoryHints.databaseHints().entities(),
                                moduleHints.databaseHints().entities()
                        ),
                        distinct(
                                scopeHints.databaseHints().migrations(),
                                repositoryHints.databaseHints().migrations(),
                                moduleHints.databaseHints().migrations()
                        )
                ),
                new WorkflowHints(
                        distinct(
                                scopeHints.workflowHints().jobNames(),
                                repositoryHints.workflowHints().jobNames(),
                                moduleHints.workflowHints().jobNames()
                        ),
                        distinct(
                                scopeHints.workflowHints().workflowNames(),
                                repositoryHints.workflowHints().workflowNames(),
                                moduleHints.workflowHints().workflowNames()
                        ),
                        distinct(
                                scopeHints.workflowHints().definitionPaths(),
                                repositoryHints.workflowHints().definitionPaths(),
                                moduleHints.workflowHints().definitionPaths()
                        )
                )
        );
    }

    private Provenance provenance(
            CodeSearchScopeView scope,
            RepositoryView repository,
            String implementationKind,
            ImplementationTargets targets
    ) {
        var refs = new LinkedHashMap<String, SourceRef>();
        addRefs(refs, scope.provenance().sourceRefs());
        addRefs(refs, repository.provenance().sourceRefs());

        var warnings = new ArrayList<String>();
        if (targets.systems().isEmpty()) {
            warnings.add("No system target could be projected for this implementation.");
        }
        if ("supporting-code".equals(implementationKind)) {
            warnings.add("Repository is referenced as supporting code, not canonical bounded-context ownership.");
        }

        return new Provenance(
                false,
                "derived-from-code-search-read-model",
                "high",
                List.copyOf(refs.values()),
                warnings
        );
    }

    private void addRefs(Map<String, SourceRef> refs, List<SourceRef> sourceRefs) {
        for (var sourceRef : sourceRefs) {
            refs.putIfAbsent(
                    sourceRef.file()
                            + "|" + sourceRef.entityType()
                            + "|" + sourceRef.entityId()
                            + "|" + sourceRef.fieldPath()
                            + "|" + sourceRef.relationRole(),
                    sourceRef
            );
        }
    }

    private Map<String, Object> metadata(
            RepositoryView repository,
            OperationalContextRepositorySearchRepository scopedRepository,
            ModuleView module
    ) {
        var result = new LinkedHashMap<String, Object>();
        putIfPresent(result, "repositoryReason", firstNonBlank(
                scopedRepository != null ? scopedRepository.reason() : null,
                repository.reason()
        ));
        putIfPresent(result, "repositoryProjectPath", repository.git().projectPath());
        putIfPresent(result, "repositoryDefaultBranch", repository.git().defaultBranch());
        putIfPresent(result, "buildTool", repository.sourceLayout().buildTool());
        if (module != null) {
            putIfPresent(result, "moduleType", module.moduleType());
        }
        return result;
    }

    private void putIfPresent(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value.trim());
        }
    }

    private String implementationId(
            String scopeId,
            String repositoryId,
            ModuleView module
    ) {
        if (module == null || !StringUtils.hasText(module.id())) {
            return scopeId + "::" + repositoryId;
        }
        return scopeId + "::" + repositoryId + "::" + module.id();
    }

    private String implementationKind(String implementationRole) {
        var role = lower(implementationRole);
        if (role.contains("library") || role.contains("shared") || role.contains("client")) {
            return "supporting-code";
        }
        return "implementation";
    }

    private String lifecycleRole(
            String implementationKind,
            String implementationRole,
            Integer priority,
            RepositoryView repository,
            ModuleView module
    ) {
        var value = lower(implementationRole)
                + " "
                + lower(repository.repository().lifecycleStatus())
                + " "
                + lower(module != null ? module.lifecycleStatus() : null);

        if (value.contains("deprecated")) {
            return "deprecated";
        }
        if (value.contains("source") || value.contains("legacy")) {
            return "source-implementation";
        }
        if (value.contains("target") || value.contains("new-implementation")) {
            return "target-implementation";
        }
        if (value.contains("being-replaced") || value.contains("being replaced") || value.contains("replaced")) {
            return "being-replaced";
        }
        if (value.contains("parallel")) {
            return "parallel";
        }
        if (value.contains("fallback")) {
            return "fallback";
        }
        if ("supporting-code".equals(implementationKind)) {
            return "supporting-library";
        }
        if (value.contains("primary") || Integer.valueOf(1).equals(priority)) {
            return "primary";
        }
        return "primary";
    }

    private String migrationStatus(
            String lifecycleRole,
            RepositoryView repository,
            ModuleView module
    ) {
        if ("deprecated".equals(lifecycleRole) || "being-replaced".equals(lifecycleRole)) {
            return lifecycleRole;
        }
        if ("source-implementation".equals(lifecycleRole)) {
            return "being-replaced";
        }
        if ("target-implementation".equals(lifecycleRole)) {
            return "target";
        }
        var status = firstNonBlank(
                module != null ? module.lifecycleStatus() : null,
                repository.repository().lifecycleStatus()
        );
        if (status != null) {
            return status;
        }
        if ("primary".equals(lifecycleRole)) {
            return "active";
        }
        return "unknown";
    }

    private Map<String, RepositoryView> repositoriesById(List<RepositoryView> repositories) {
        var result = new LinkedHashMap<String, RepositoryView>();
        for (var repository : repositories) {
            result.putIfAbsent(repository.repository().id(), repository);
        }
        return result;
    }

    private Map<String, Map<String, OperationalContextRepositorySearchRepository>> scopeRepositoriesByScopeId(
            List<OperationalContextRepositorySearchScope> scopes
    ) {
        var result = new LinkedHashMap<String, Map<String, OperationalContextRepositorySearchRepository>>();
        for (var scope : scopes) {
            var repositories = new LinkedHashMap<String, OperationalContextRepositorySearchRepository>();
            for (var repository : scope.repositories()) {
                if (StringUtils.hasText(repository.repoId())) {
                    repositories.putIfAbsent(repository.repoId(), repository);
                }
            }
            result.putIfAbsent(scope.id(), repositories);
        }
        return result;
    }

    private String implementationRole(
            OperationalContextRepositorySearchRepository scopedRepository,
            RepositoryView repository
    ) {
        return firstNonBlank(scopedRepository != null ? scopedRepository.role() : null, repository.role(), "referenced");
    }

    private Integer implementationPriority(
            OperationalContextRepositorySearchRepository scopedRepository,
            RepositoryView repository
    ) {
        if (scopedRepository != null && scopedRepository.priority() != null) {
            return scopedRepository.priority();
        }
        return repository.priority();
    }

    private void addByType(
            Map<String, EntityRef> refs,
            List<EntityRef> source,
            String type
    ) {
        for (var ref : source) {
            if (type.equals(ref.type())) {
                refs.putIfAbsent(ref.id(), ref);
            }
        }
    }

    @SafeVarargs
    private final List<String> distinct(List<String>... values) {
        var result = new LinkedHashSet<String>();
        for (var current : values) {
            if (current == null) {
                continue;
            }
            for (var value : current) {
                if (StringUtils.hasText(value)) {
                    result.add(value.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    private List<ImplementationView> sortedImplementations(Map<String, ImplementationView> implementations) {
        return implementations.values().stream()
                .sorted(Comparator
                        .comparing((ImplementationView implementation) -> implementation.priority() != null
                                ? implementation.priority()
                                : Integer.MAX_VALUE)
                        .thenComparing(implementation -> "supporting-code".equals(implementation.implementationKind()) ? 1 : 0)
                        .thenComparing(ImplementationView::id))
                .toList();
    }

    private List<ValidationFinding> distinctFindings(List<ValidationFinding> findings) {
        var result = new LinkedHashMap<String, ValidationFinding>();
        for (var finding : findings) {
            result.putIfAbsent(finding.code() + "|" + finding.message(), finding);
        }
        return List.copyOf(result.values());
    }

    private String normalizeEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        var normalized = entityType.trim().replace("_", "-").replace(" ", "-");
        return switch (normalized) {
            case "codeSearchScope", "codeSearchScopes", "code-search-scopes" -> CODE_SEARCH_SCOPE;
            case "boundedContext", "boundedContexts", "bounded-contexts" -> BOUNDED_CONTEXT;
            case "systems" -> SYSTEM;
            case "processes" -> PROCESS;
            case "integrations" -> "integration";
            case "repositories" -> "repository";
            case "terms", "glossary-term", "glossary-terms" -> "term";
            default -> normalized;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String lower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }

    private record ImplementationTargets(
            List<EntityRef> boundedContexts,
            List<EntityRef> systems,
            List<EntityRef> processes
    ) {
    }
}
