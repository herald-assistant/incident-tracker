package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOwnership;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.Owner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.AMBIGUOUS;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.BOUNDED_CONTEXT_BOUNDARY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.EXTERNAL_SYSTEM_BOUNDARY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.INSIDE_BOUNDED_CONTEXT;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.INSIDE_SYSTEM;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SOURCE_EXPLICIT_OWNERSHIP;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SOURCE_INFERRED_FROM_TARGET_NAME;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SOURCE_PARENT_SYSTEM_OWNERSHIP;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SYSTEM_BOUNDARY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SYSTEM_INFRASTRUCTURE;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.UNKNOWN;

@Component
public class OperationalContextOwnershipResolver {

    public OperationalContextOwnershipResolution resolve(
            OperationalContextCatalog catalog,
            OperationalContextOwnershipRequest request
    ) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var safeRequest = request != null ? request : OperationalContextOwnershipRequest.empty();
        var lookup = new CatalogLookup(safeCatalog);
        var state = new ResolutionState();
        state.collect(safeRequest, lookup);
        var situationType = situationType(safeRequest, state);

        if (UNKNOWN.equals(situationType)) {
            return OperationalContextOwnershipResolution.unknown(
                    state.resolutionPath(),
                    state.withVisibilityLimit("No system or bounded context matched ownership input.")
            );
        }

        var primaryOwners = new ArrayList<Owner>();
        var partnerOwners = new ArrayList<Owner>();
        resolveOwners(situationType, state, lookup, primaryOwners, partnerOwners);

        if (primaryOwners.isEmpty() && partnerOwners.isEmpty()) {
            return OperationalContextOwnershipResolution.unknown(
                    state.resolutionPath(),
                    state.withVisibilityLimit("No owner candidates could be resolved.")
            );
        }

        return new OperationalContextOwnershipResolution(
                situationType,
                primaryOwners,
                partnerOwners,
                state.resolutionPath(),
                handoffReason(situationType, primaryOwners, partnerOwners),
                state.visibilityLimits()
        );
    }

    private void resolveOwners(
            String situationType,
            ResolutionState state,
            CatalogLookup lookup,
            List<Owner> primaryOwners,
            List<Owner> partnerOwners
    ) {
        if (BOUNDED_CONTEXT_BOUNDARY.equals(situationType)) {
            addBoundaryOwners(state.boundedContextIds(), primaryOwners, partnerOwners, id -> ownerForBoundedContext(id, lookup, state));
            return;
        }

        if (SYSTEM_BOUNDARY.equals(situationType)) {
            addBoundaryOwners(state.systemIds(), primaryOwners, partnerOwners, id -> ownerForSystem(id, lookup, state, SOURCE_EXPLICIT_OWNERSHIP));
            return;
        }

        if (SYSTEM_INFRASTRUCTURE.equals(situationType)) {
            addPrimarySemanticOwner(state, lookup, primaryOwners);
            partnerOwners.add(inferredInfrastructureOwner(state, lookup));
            return;
        }

        if (EXTERNAL_SYSTEM_BOUNDARY.equals(situationType)) {
            addPrimarySemanticOwner(state, lookup, primaryOwners);
            addExternalPartnerOwner(state, lookup, partnerOwners);
            return;
        }

        if (AMBIGUOUS.equals(situationType)) {
            for (var boundedContextId : state.boundedContextIds()) {
                primaryOwners.add(ownerForBoundedContext(boundedContextId, lookup, state));
            }
            for (var systemId : state.systemIds()) {
                primaryOwners.add(ownerForSystem(systemId, lookup, state, SOURCE_EXPLICIT_OWNERSHIP));
            }
            state.addVisibilityLimit("Ownership is ambiguous because multiple catalog targets matched.");
            return;
        }

        if (INSIDE_BOUNDED_CONTEXT.equals(situationType)) {
            primaryOwners.add(ownerForBoundedContext(first(state.boundedContextIds()), lookup, state));
            return;
        }

        primaryOwners.add(ownerForSystem(first(state.systemIds()), lookup, state, SOURCE_EXPLICIT_OWNERSHIP));
    }

    private void addPrimarySemanticOwner(
            ResolutionState state,
            CatalogLookup lookup,
            List<Owner> primaryOwners
    ) {
        if (!state.boundedContextIds().isEmpty()) {
            primaryOwners.add(ownerForBoundedContext(first(state.boundedContextIds()), lookup, state));
            return;
        }
        if (!state.systemIds().isEmpty()) {
            primaryOwners.add(ownerForSystem(first(state.systemIds()), lookup, state, SOURCE_EXPLICIT_OWNERSHIP));
        }
    }

    private void addBoundaryOwners(
            List<String> targetIds,
            List<Owner> primaryOwners,
            List<Owner> partnerOwners,
            OwnerFactory ownerFactory
    ) {
        for (var i = 0; i < targetIds.size(); i++) {
            var owner = ownerFactory.owner(targetIds.get(i));
            if (i == 0) {
                primaryOwners.add(owner);
            } else {
                partnerOwners.add(owner);
            }
        }
    }

    private Owner ownerForBoundedContext(
            String boundedContextId,
            CatalogLookup lookup,
            ResolutionState state
    ) {
        var boundedContext = lookup.boundedContext(boundedContextId);
        if (boundedContext == null) {
            state.addVisibilityLimit("Bounded context `" + boundedContextId + "` is not present in the catalog.");
            return inferredOwner("bounded-context", boundedContextId, labelFromId(boundedContextId), "domeny");
        }

        var ownership = boundedContext.ownership();
        if (ownership.hasOwner()) {
            state.addResolutionPath("bounded-context:" + boundedContext.id() + " -> ownership");
            return explicitOwner("bounded-context", boundedContext.id(), boundedContext.label(), ownership, SOURCE_EXPLICIT_OWNERSHIP);
        }

        for (var systemId : boundedContext.references().systems()) {
            var system = lookup.system(systemId);
            if (system != null && system.ownership().hasOwner()) {
                state.addResolutionPath("bounded-context:" + boundedContext.id() + " -> system:" + system.id() + " -> ownership");
                return explicitOwner("system", system.id(), system.label(), system.ownership(), SOURCE_PARENT_SYSTEM_OWNERSHIP);
            }
        }

        state.addVisibilityLimit("Bounded context `" + boundedContext.id() + "` has no explicit owner.");
        state.addResolutionPath("bounded-context:" + boundedContext.id() + " -> inferred owner");
        return inferredOwner("bounded-context", boundedContext.id(), boundedContext.label(), "domeny");
    }

    private Owner ownerForSystem(
            String systemId,
            CatalogLookup lookup,
            ResolutionState state,
            String explicitSource
    ) {
        var system = lookup.system(systemId);
        if (system == null) {
            state.addVisibilityLimit("System `" + systemId + "` is not present in the catalog.");
            return inferredOwner("system", systemId, labelFromId(systemId), "systemu");
        }

        var ownership = system.ownership();
        if (ownership.hasOwner()) {
            state.addResolutionPath("system:" + system.id() + " -> ownership");
            return explicitOwner("system", system.id(), system.label(), ownership, explicitSource);
        }

        state.addVisibilityLimit("System `" + system.id() + "` has no explicit owner.");
        state.addResolutionPath("system:" + system.id() + " -> inferred owner");
        return inferredOwner("system", system.id(), system.label(), "systemu");
    }

    private Owner explicitOwner(
            String targetType,
            String targetId,
            String targetLabel,
            OperationalContextOwnership ownership,
            String source
    ) {
        return new Owner(
                targetType,
                targetId,
                targetLabel,
                ownership.ownerTeamIds(),
                ownership.ownerLabel(),
                source,
                ownership.confidence()
        );
    }

    private Owner inferredOwner(String targetType, String targetId, String targetLabel, String labelKind) {
        return new Owner(
                targetType,
                targetId,
                targetLabel,
                List.of(),
                "wlasciciel " + labelKind + " " + targetLabel,
                SOURCE_INFERRED_FROM_TARGET_NAME,
                "low"
        );
    }

    private Owner inferredInfrastructureOwner(ResolutionState state, CatalogLookup lookup) {
        var baseLabel = semanticLabel(state, lookup);
        state.addResolutionPath("infrastructure:" + baseLabel + " -> inferred owner");
        state.addVisibilityLimit("Infrastructure owner is inferred because operational context has no infrastructure ownership entity.");
        return new Owner(
                "infrastructure",
                null,
                "infrastruktura " + baseLabel,
                List.of(),
                "wlasciciel infrastruktury " + baseLabel,
                SOURCE_INFERRED_FROM_TARGET_NAME,
                "low"
        );
    }

    private void addExternalPartnerOwner(ResolutionState state, CatalogLookup lookup, List<Owner> partnerOwners) {
        if (state.systemIds().size() > 1) {
            partnerOwners.add(ownerForSystem(state.systemIds().get(1), lookup, state, SOURCE_EXPLICIT_OWNERSHIP));
            return;
        }

        var baseLabel = semanticLabel(state, lookup);
        state.addVisibilityLimit("External system owner is inferred because only one catalog system matched.");
        partnerOwners.add(new Owner(
                "external-system",
                null,
                "zewnetrzny system dla " + baseLabel,
                List.of(),
                "wlasciciel systemu zewnetrznego dla " + baseLabel,
                SOURCE_INFERRED_FROM_TARGET_NAME,
                "low"
        ));
    }

    private String situationType(OperationalContextOwnershipRequest request, ResolutionState state) {
        var requested = normalizeSituationType(request.situationType());
        if (StringUtils.hasText(requested)) {
            return requested;
        }

        if (state.boundedContextIds().size() > 1) {
            return BOUNDED_CONTEXT_BOUNDARY;
        }
        if (state.boundedContextIds().size() == 1) {
            return INSIDE_BOUNDED_CONTEXT;
        }
        if (state.systemIds().size() > 1) {
            return SYSTEM_BOUNDARY;
        }
        if (state.systemIds().size() == 1) {
            return INSIDE_SYSTEM;
        }
        return UNKNOWN;
    }

    private String normalizeSituationType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        return switch (normalized) {
            case INSIDE_BOUNDED_CONTEXT,
                    INSIDE_SYSTEM,
                    BOUNDED_CONTEXT_BOUNDARY,
                    SYSTEM_BOUNDARY,
                    SYSTEM_INFRASTRUCTURE,
                    EXTERNAL_SYSTEM_BOUNDARY,
                    AMBIGUOUS,
                    UNKNOWN -> normalized;
            default -> null;
        };
    }

    private String handoffReason(String situationType, List<Owner> primaryOwners, List<Owner> partnerOwners) {
        var primary = ownerSummary(primaryOwners);
        var partners = ownerSummary(partnerOwners);
        if (!partnerOwners.isEmpty()) {
            return "Problem typu `" + situationType + "` wymaga wlaczenia " + primary + " oraz " + partners + ".";
        }
        return "Problem typu `" + situationType + "` prowadzi do " + primary + ".";
    }

    private String ownerSummary(List<Owner> owners) {
        return owners.stream()
                .map(owner -> firstNonBlank(owner.ownerLabel(), String.join(", ", owner.ownerTeamIds()), owner.targetLabel()))
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("brak jawnego ownera");
    }

    private String semanticLabel(ResolutionState state, CatalogLookup lookup) {
        if (!state.boundedContextIds().isEmpty()) {
            var boundedContext = lookup.boundedContext(first(state.boundedContextIds()));
            return boundedContext != null ? boundedContext.label() : labelFromId(first(state.boundedContextIds()));
        }
        if (!state.systemIds().isEmpty()) {
            var system = lookup.system(first(state.systemIds()));
            return system != null ? system.label() : labelFromId(first(state.systemIds()));
        }
        return "nieznanego targetu";
    }

    private String labelFromId(String id) {
        if (!StringUtils.hasText(id)) {
            return "unknown";
        }
        var normalized = id.trim().replace('-', ' ').replace('_', ' ');
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String first(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9/_]+", "_")
                : null;
    }

    private String normalizeEntityType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "-");
        if ("boundedcontext".equals(normalized) || "bounded-context".equals(normalized)) {
            return "bounded-context";
        }
        return normalized;
    }

    private interface OwnerFactory {
        Owner owner(String id);
    }

    private final class CatalogLookup {

        private final Map<String, OperationalContextSystem> systemsById = new LinkedHashMap<>();
        private final Map<String, OperationalContextBoundedContext> boundedContextsById = new LinkedHashMap<>();
        private final Map<String, OperationalContextRepository> repositoriesById = new LinkedHashMap<>();
        private final Map<String, OperationalContextRepositorySearchScope> codeSearchScopesById = new LinkedHashMap<>();

        private CatalogLookup(OperationalContextCatalog catalog) {
            catalog.systems().forEach(system -> systemsById.putIfAbsent(normalizeComparable(system.id()), system));
            catalog.boundedContexts().forEach(context -> boundedContextsById.putIfAbsent(normalizeComparable(context.id()), context));
            catalog.repositories().forEach(repository -> repositoriesById.putIfAbsent(normalizeComparable(repository.id()), repository));
            catalog.codeSearchScopes().forEach(scope -> codeSearchScopesById.putIfAbsent(normalizeComparable(scope.id()), scope));
        }

        private OperationalContextSystem system(String id) {
            return systemsById.get(normalizeComparable(id));
        }

        private OperationalContextBoundedContext boundedContext(String id) {
            return boundedContextsById.get(normalizeComparable(id));
        }

        private OperationalContextRepository repository(String id) {
            return repositoriesById.get(normalizeComparable(id));
        }

        private OperationalContextRepositorySearchScope codeSearchScope(String id) {
            return codeSearchScopesById.get(normalizeComparable(id));
        }

        private OperationalContextRepository repositoryByProjectPath(String projectPath) {
            var normalizedProjectPath = normalizeComparable(projectPath);
            if (!StringUtils.hasText(normalizedProjectPath)) {
                return null;
            }
            for (var repository : repositoriesById.values()) {
                if (normalizedProjectPath.equals(normalizeComparable(repository.git().projectPath()))
                        || normalizedProjectPath.equals(normalizeComparable(repository.git().project()))
                        || normalizedProjectPath.equals(normalizeComparable(repository.id()))) {
                    return repository;
                }
            }
            return null;
        }
    }

    private final class ResolutionState {

        private final LinkedHashSet<String> systemIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> boundedContextIds = new LinkedHashSet<>();
        private final List<String> resolutionPath = new ArrayList<>();
        private final LinkedHashSet<String> visibilityLimits = new LinkedHashSet<>();

        private void collect(OperationalContextOwnershipRequest request, CatalogLookup lookup) {
            addAll(systemIds, request.systemIds(), "request.systemIds");
            addAll(boundedContextIds, request.boundedContextIds(), "request.boundedContextIds");
            addAll(systemIds, request.technicalTarget().systemIds(), "technicalTarget.systemIds");
            addAll(boundedContextIds, request.technicalTarget().boundedContextIds(), "technicalTarget.boundedContextIds");
            addRepository(request.technicalTarget().repositoryId(), lookup, "technicalTarget.repositoryId");
            addRepositoryByProjectPath(request.technicalTarget().gitProjectPath(), lookup);
            addAllRepositories(request.repositoryIds(), lookup, "request.repositoryIds");
            addAllCodeSearchScopes(request.codeSearchScopeIds(), lookup, "request.codeSearchScopeIds");
            addAllCodeSearchScopes(request.technicalTarget().codeSearchScopeIds(), lookup, "technicalTarget.codeSearchScopeIds");
            if (request.technicalTarget().endpoint().hasAnySignal()) {
                addResolutionPath("technicalTarget.endpoint -> semantic catalog target");
            }
            addParentSystemsForContexts(lookup);
        }

        private List<String> systemIds() {
            return List.copyOf(systemIds);
        }

        private List<String> boundedContextIds() {
            return List.copyOf(boundedContextIds);
        }

        private List<String> resolutionPath() {
            return List.copyOf(resolutionPath);
        }

        private List<String> visibilityLimits() {
            return List.copyOf(visibilityLimits);
        }

        private List<String> withVisibilityLimit(String limit) {
            addVisibilityLimit(limit);
            return visibilityLimits();
        }

        private void addVisibilityLimit(String limit) {
            if (StringUtils.hasText(limit)) {
                visibilityLimits.add(limit);
            }
        }

        private void addResolutionPath(String step) {
            if (StringUtils.hasText(step) && !resolutionPath.contains(step)) {
                resolutionPath.add(step);
            }
        }

        private void addAll(LinkedHashSet<String> target, List<String> values, String source) {
            for (var value : values) {
                add(target, value, source);
            }
        }

        private void add(LinkedHashSet<String> target, String value, String source) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            if (target.add(value.trim())) {
                addResolutionPath(source + " -> " + value.trim());
            }
        }

        private void addAllRepositories(List<String> repositoryIds, CatalogLookup lookup, String source) {
            for (var repositoryId : repositoryIds) {
                addRepository(repositoryId, lookup, source);
            }
        }

        private void addRepository(String repositoryId, CatalogLookup lookup, String source) {
            if (!StringUtils.hasText(repositoryId)) {
                return;
            }
            var repository = lookup.repository(repositoryId);
            if (repository == null) {
                addVisibilityLimit("Repository `" + repositoryId + "` is not present in the catalog.");
                return;
            }
            addResolutionPath(source + " -> repository:" + repository.id());
            addAll(systemIds, repository.references().systems(), "repository:" + repository.id() + ".references.systems");
            addAll(boundedContextIds, repository.references().boundedContexts(), "repository:" + repository.id() + ".references.boundedContexts");
        }

        private void addRepositoryByProjectPath(String gitProjectPath, CatalogLookup lookup) {
            if (!StringUtils.hasText(gitProjectPath)) {
                return;
            }
            var repository = lookup.repositoryByProjectPath(gitProjectPath);
            if (repository == null) {
                addVisibilityLimit("Git project path `" + gitProjectPath + "` does not match a catalog repository.");
                return;
            }
            addRepository(repository.id(), lookup, "technicalTarget.gitProjectPath");
        }

        private void addAllCodeSearchScopes(List<String> scopeIds, CatalogLookup lookup, String source) {
            for (var scopeId : scopeIds) {
                addCodeSearchScope(scopeId, lookup, source);
            }
        }

        private void addCodeSearchScope(String scopeId, CatalogLookup lookup, String source) {
            if (!StringUtils.hasText(scopeId)) {
                return;
            }
            var scope = lookup.codeSearchScope(scopeId);
            if (scope == null) {
                addVisibilityLimit("Code-search scope `" + scopeId + "` is not present in the catalog.");
                return;
            }
            addResolutionPath(source + " -> code-search-scope:" + scope.id());
            var targetType = normalizeEntityType(scope.target().type());
            if ("bounded-context".equals(targetType)) {
                add(boundedContextIds, scope.target().id(), "code-search-scope:" + scope.id() + ".target");
            } else if ("system".equals(targetType)) {
                add(systemIds, scope.target().id(), "code-search-scope:" + scope.id() + ".target");
            }
            for (var repository : scope.repositories()) {
                addRepository(repository.repoId(), lookup, "code-search-scope:" + scope.id() + ".repositories");
            }
        }

        private void addParentSystemsForContexts(CatalogLookup lookup) {
            for (var contextId : new ArrayList<>(boundedContextIds)) {
                var context = lookup.boundedContext(contextId);
                if (context != null) {
                    addAll(systemIds, context.references().systems(), "bounded-context:" + context.id() + ".references.systems");
                }
            }
        }
    }
}
