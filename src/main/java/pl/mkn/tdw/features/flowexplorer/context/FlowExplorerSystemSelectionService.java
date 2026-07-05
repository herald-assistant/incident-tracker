package pl.mkn.tdw.features.flowexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerSystemOptionResponse;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipRequest;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolver;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.REPOSITORY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.SYSTEM;

@Service
@RequiredArgsConstructor
public class FlowExplorerSystemSelectionService {

    private static final Set<OperationalContextEntryType> SYSTEM_SELECTION_ENTRY_TYPES = Set.of(
            SYSTEM,
            REPOSITORY
    );

    private final OperationalContextPort operationalContextPort;
    private final OperationalContextOwnershipResolver ownershipResolver = new OperationalContextOwnershipResolver();

    public List<FlowExplorerSystemOptionResponse> systems() {
        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                SYSTEM_SELECTION_ENTRY_TYPES,
                List.of(),
                false
        ));

        return catalog.systems().stream()
                .filter(this::internalSystem)
                .map(system -> toOption(catalog, system))
                .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private FlowExplorerSystemOptionResponse toOption(
            OperationalContextCatalog catalog,
            OperationalContextSystem system
    ) {
        var codeSearchScopeRepositoryIds = codeSearchScopeRepositoryIds(catalog, system.id());
        var repositoryIds = distinct(combineValues(
                system.references().repositories(),
                codeSearchScopeRepositoryIds
        ));
        var ownerTeamIds = ownerTeamIds(ownershipResolver.resolve(catalog, ownershipRequest(system)));

        return new FlowExplorerSystemOptionResponse(
                system.id(),
                system.name(),
                system.shortName(),
                system.kind(),
                system.lifecycleStatus(),
                system.operationalStatus(),
                system.criticality(),
                firstDefined(system.summary(), system.purpose()),
                system.aliases(),
                repositoryIds.size(),
                codeSearchScopeCount(catalog, system),
                ownerTeamIds
        );
    }

    private boolean internalSystem(OperationalContextSystem system) {
        var kind = normalize(system.kind());
        return kind.equals("internal") || kind.startsWith("internal-") || kind.equals("api-gateway");
    }

    private int codeSearchScopeCount(OperationalContextCatalog catalog, OperationalContextSystem system) {
        var semanticScopeCount = catalog.codeSearchScopes().stream()
                .filter(scope -> targetsSystem(scope, system.id()))
                .count();
        return Math.toIntExact(semanticScopeCount);
    }

    private List<String> codeSearchScopeRepositoryIds(OperationalContextCatalog catalog, String systemId) {
        return catalog.codeSearchScopes().stream()
                .filter(scope -> targetsSystem(scope, systemId))
                .flatMap(scope -> scope.repositories().stream())
                .map(OperationalContextRepositorySearchRepository::repoId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean targetsSystem(OperationalContextRepositorySearchScope scope, String systemId) {
        return StringUtils.hasText(systemId)
                && systemTargetType(scope.target().type())
                && systemId.equals(scope.target().id());
    }

    private boolean systemTargetType(String targetType) {
        var normalized = normalize(targetType);
        return normalized.equals("system") || normalized.equals("systems");
    }

    private OperationalContextOwnershipRequest ownershipRequest(OperationalContextSystem system) {
        return new OperationalContextOwnershipRequest(
                null,
                List.of(system.id()),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private List<String> ownerTeamIds(OperationalContextOwnershipResolution ownership) {
        return ownership.primaryOwners().stream()
                .flatMap(owner -> owner.ownerTeamIds().stream())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @SafeVarargs
    private final List<String> combineValues(List<String>... values) {
        var combined = new ArrayList<String>();
        for (var list : values) {
            if (list != null) {
                combined.addAll(list);
            }
        }
        return combined;
    }

    private List<String> distinct(List<String> values) {
        var distinctValues = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                distinctValues.add(value);
            }
        }
        return List.copyOf(distinctValues);
    }

    private String firstDefined(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String displayName(FlowExplorerSystemOptionResponse option) {
        var displayName = firstDefined(firstDefined(option.name(), option.shortName()), option.systemId());
        return displayName != null ? displayName : "";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replace("_", "-")
                : "";
    }
}
