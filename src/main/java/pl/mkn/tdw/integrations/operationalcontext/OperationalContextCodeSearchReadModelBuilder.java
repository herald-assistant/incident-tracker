package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchScopeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.GitView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.RepositoryView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
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

public class OperationalContextCodeSearchReadModelBuilder {

    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String REPOSITORY = "repository";
    private static final String CODE_SEARCH_SCOPES_FILE = "src/main/resources/operational-context/code-search-scopes.yml";
    private static final String REPO_MAP_FILE = "src/main/resources/operational-context/repo-map.yml";

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
                            CODE_SEARCH_SCOPES_FILE,
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

        var result = new LinkedHashSet<String>();
        addScopeIds(result, relationIndex, target);
        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        relationIndex.entityRelations(target.type(), target.id()).neighbors().forEach(neighbor ->
                addScopeIds(result, relationIndex, new EntityKey(neighbor.type(), neighbor.id()))
        );
        return List.copyOf(result);
    }

    private void addScopeIds(
            LinkedHashSet<String> scopeIds,
            OperationalContextRelationIndex relationIndex,
            EntityKey target
    ) {
        relationIndex.entityRelations(target.type(), target.id()).incomingRelations().stream()
                .filter(relation -> CODE_SEARCH_SCOPE.equals(relation.source().type()))
                .filter(relation -> relation.relationType().startsWith("targets-"))
                .map(relation -> relation.source().id())
                .forEach(scopeIds::add);
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

        return new RepositoryView(
                repositoryRef(repository),
                scopeRepository.role(),
                scopeRepository.priority(),
                scopeRepository.reason(),
                scopeRepository.readFor(),
                gitView(repository),
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
                REPOSITORY.equals(entityType) ? REPO_MAP_FILE : CODE_SEARCH_SCOPES_FILE,
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

    private List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<ValidationFinding> distinctFindings(List<ValidationFinding> findings) {
        var result = new LinkedHashMap<String, ValidationFinding>();
        for (var finding : findings) {
            result.putIfAbsent(finding.code() + "|" + finding.message(), finding);
        }
        return List.copyOf(result.values());
    }
}
