package pl.mkn.tdw.features.configdriftviewer.deep;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerAffectedEntity;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContextStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepCoverage;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflight;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerGroundingConfidence;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerOperationalEntityType;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerPrimarySystem;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModelBuilder;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipRequest;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolver;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConfigDriftViewerDeepContextService {

    private final ConfigDriftViewerDeepPreflightService preflightService;
    private final ConfigDriftViewerCodeUsageSearchService codeUsageSearchService;
    private final OperationalContextPort operationalContextPort;
    private final OperationalContextOwnershipResolver ownershipResolver;
    private final OperationalContextCodeSearchReadModelBuilder codeSearchBuilder =
            new OperationalContextCodeSearchReadModelBuilder();

    public Optional<ConfigDriftViewerDeepContext> build(
            ConfigDriftViewerMode mode,
            String repositoryId,
            String systemId,
            String codeRef,
            ConfigDriftViewerDeterministicContext deterministicContext
    ) {
        return build(
                mode,
                repositoryId,
                systemId,
                codeRef,
                deterministicContext,
                ConfigDriftViewerDeepContextListener.NO_OP
        );
    }

    public Optional<ConfigDriftViewerDeepContext> build(
            ConfigDriftViewerMode mode,
            String repositoryId,
            String systemId,
            String codeRef,
            ConfigDriftViewerDeterministicContext deterministicContext,
            ConfigDriftViewerDeepContextListener listener
    ) {
        if (mode != ConfigDriftViewerMode.DEEP) {
            return Optional.empty();
        }
        var resolvedListener = listener != null ? listener : ConfigDriftViewerDeepContextListener.NO_OP;

        resolvedListener.onOperationalContextStarted();
        var preflight = preflightService.check(repositoryId, systemId, codeRef);
        if (!preflight.ready()) {
            var context = unavailable(preflight);
            resolvedListener.onOperationalContextCompleted();
            resolvedListener.onOwnershipCompleted(context);
            return Optional.of(context);
        }

        OperationalContextCatalog catalog;
        try {
            catalog = operationalContextPort.loadContext(OperationalContextQuery.all());
        } catch (RuntimeException exception) {
            var context = unavailable(
                    preflight,
                    "Operational Context became unavailable after DEEP preflight."
            );
            resolvedListener.onOperationalContextCompleted();
            resolvedListener.onOwnershipCompleted(context);
            return Optional.of(context);
        }
        resolvedListener.onOperationalContextCompleted();

        resolvedListener.onCodeGroundingStarted();
        var codeSearch = codeUsageSearchService.search(preflight, deterministicContext);
        resolvedListener.onCodeGroundingCompleted();
        var visibilityLimits = new LinkedHashSet<String>();
        visibilityLimits.addAll(preflight.visibilityLimits());
        visibilityLimits.addAll(codeSearch.visibilityLimits());
        if (codeSearch.groundings().isEmpty()) {
            visibilityLimits.add("No deterministic code usage was confirmed for changed configuration keys.");
        }

        var entityIndex = new EntityIndex(catalog, deterministicContext, codeSearch);
        entityIndex.collect(systemId);
        var primarySystem = primarySystem(catalog, preflight, systemId);
        resolvedListener.onOwnershipStarted();
        var ownership = ownership(catalog, entityIndex);
        visibilityLimits.addAll(ownership.visibilityLimits());
        var affectedInternalSystemsWithoutScope = systemsWithoutScope(catalog, entityIndex.systemIds());
        for (var affectedSystemId : affectedInternalSystemsWithoutScope) {
            visibilityLimits.add("Affected internal system `" + affectedSystemId
                    + "` has no code-search scope; its code was not read.");
        }

        var unavailableRepositories = preflight.repositories().stream()
                .filter(repository -> !repository.ready())
                .map(repository -> repository.repositoryId())
                .toList();
        var coverage = new ConfigDriftViewerDeepCoverage(
                preflight.repositories().size(),
                codeSearch.repositoriesSearched(),
                codeSearch.keysSearched(),
                codeSearch.filesInspected(),
                codeSearch.groundings().size(),
                unavailableRepositories,
                affectedInternalSystemsWithoutScope
        );
        var status = visibilityLimits.isEmpty()
                ? ConfigDriftViewerDeepContextStatus.COMPLETE
                : ConfigDriftViewerDeepContextStatus.PARTIAL;

        var context = new ConfigDriftViewerDeepContext(
                status,
                preflight,
                primarySystem,
                entityIndex.entities(ConfigDriftViewerOperationalEntityType.SYSTEM),
                entityIndex.entities(ConfigDriftViewerOperationalEntityType.INTEGRATION),
                entityIndex.entities(ConfigDriftViewerOperationalEntityType.PROCESS),
                entityIndex.entities(ConfigDriftViewerOperationalEntityType.BOUNDED_CONTEXT),
                codeSearch.groundings(),
                ownership,
                coverage,
                List.copyOf(visibilityLimits)
        );
        resolvedListener.onOwnershipCompleted(context);
        return Optional.of(context);
    }

    private ConfigDriftViewerDeepContext unavailable(ConfigDriftViewerDeepPreflight preflight) {
        return unavailable(preflight, null);
    }

    private ConfigDriftViewerDeepContext unavailable(
            ConfigDriftViewerDeepPreflight preflight,
            String additionalLimit
    ) {
        var limits = new LinkedHashSet<String>(preflight.visibilityLimits());
        preflight.blockers().forEach(blocker -> limits.add(blocker.message()));
        if (StringUtils.hasText(additionalLimit)) {
            limits.add(additionalLimit);
        }
        return new ConfigDriftViewerDeepContext(
                ConfigDriftViewerDeepContextStatus.UNAVAILABLE,
                preflight,
                preflight.systemId() != null
                        ? new ConfigDriftViewerPrimarySystem(
                        preflight.systemId(),
                        preflight.systemLabel(),
                        "internal-service",
                        preflight.resolvedConfigurationDirectory(),
                        "runtime/deployment signal",
                        List.of()
                )
                        : null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                OperationalContextOwnershipResolution.unknown(List.of(), List.copyOf(limits)),
                new ConfigDriftViewerDeepCoverage(
                        preflight.repositories().size(),
                        0,
                        0,
                        0,
                        0,
                        preflight.repositories().stream()
                                .map(repository -> repository.repositoryId())
                                .toList(),
                        List.of()
                ),
                List.copyOf(limits)
        );
    }

    private ConfigDriftViewerPrimarySystem primarySystem(
            OperationalContextCatalog catalog,
            ConfigDriftViewerDeepPreflight preflight,
            String systemId
    ) {
        var system = catalog.systems().stream()
                .filter(candidate -> equalsId(candidate.id(), systemId))
                .findFirst()
                .orElse(null);
        var scopeIds = codeSearchBuilder.buildForEntity(catalog, "system", systemId)
                .scopes().stream()
                .map(scope -> scope.scope().id())
                .toList();
        return new ConfigDriftViewerPrimarySystem(
                systemId,
                system != null ? system.label() : preflight.systemLabel(),
                system != null ? system.kind() : "internal-service",
                preflight.resolvedConfigurationDirectory(),
                "runtime/deployment signal",
                scopeIds
        );
    }

    private OperationalContextOwnershipResolution ownership(
            OperationalContextCatalog catalog,
            EntityIndex index
    ) {
        return ownershipResolver.resolve(
                catalog,
                new OperationalContextOwnershipRequest(
                        index.hasLowConfidenceCandidates() ? "ambiguous" : null,
                        index.systemIds(),
                        index.boundedContextIds(),
                        index.repositoryIds(),
                        index.scopeIds(),
                        OperationalContextOwnershipRequest.TechnicalTarget.empty()
                )
        );
    }

    private List<String> systemsWithoutScope(
            OperationalContextCatalog catalog,
            List<String> systemIds
    ) {
        var result = new ArrayList<String>();
        for (var systemId : systemIds) {
            var system = catalog.systems().stream()
                    .filter(candidate -> equalsId(candidate.id(), systemId))
                    .findFirst()
                    .orElse(null);
            if (system == null
                    || !"internal-service".equalsIgnoreCase(system.kind())
                    || !codeSearchBuilder.buildForEntity(catalog, "system", systemId).scopes().isEmpty()) {
                continue;
            }
            result.add(systemId);
        }
        return List.copyOf(result);
    }

    private boolean equalsId(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replace('_', '-')
                : "";
    }

    private static final class EntityIndex {

        private final OperationalContextCatalog catalog;
        private final ConfigDriftViewerDeterministicContext deterministicContext;
        private final ConfigDriftViewerCodeSearchResult codeSearch;
        private final Map<String, ConfigDriftViewerAffectedEntity> entities = new LinkedHashMap<>();
        private final LinkedHashSet<String> repositoryIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> scopeIds = new LinkedHashSet<>();

        private EntityIndex(
                OperationalContextCatalog catalog,
                ConfigDriftViewerDeterministicContext deterministicContext,
                ConfigDriftViewerCodeSearchResult codeSearch
        ) {
            this.catalog = catalog;
            this.deterministicContext = deterministicContext;
            this.codeSearch = codeSearch;
        }

        private void collect(String primarySystemId) {
            var primary = catalog.systems().stream()
                    .filter(system -> same(system.id(), primarySystemId))
                    .findFirst()
                    .orElse(null);
            if (primary == null) {
                return;
            }
            add(
                    ConfigDriftViewerOperationalEntityType.SYSTEM,
                    primary,
                    "SELECTED_INTERNAL_SYSTEM",
                    ConfigDriftViewerGroundingConfidence.HIGH,
                    List.of(),
                    List.of()
            );

            catalog.processes().stream()
                    .filter(process -> referencesSystem(process, primarySystemId)
                            || contains(process.participants().primarySystems(), primarySystemId)
                            || contains(process.participants().supportingSystems(), primarySystemId))
                    .forEach(process -> {
                        addRelated(ConfigDriftViewerOperationalEntityType.PROCESS, process);
                        addSystems(process.references().systems(), "RELATED_PROCESS");
                        addSystems(process.participants().primarySystems(), "RELATED_PROCESS");
                        addSystems(process.participants().supportingSystems(), "RELATED_PROCESS");
                    });
            catalog.integrations().stream()
                    .filter(integration -> referencesSystem(integration, primarySystemId)
                            || contains(integration.participants().systems(), primarySystemId))
                    .forEach(integration -> {
                        addRelated(ConfigDriftViewerOperationalEntityType.INTEGRATION, integration);
                        addSystems(integration.references().systems(), "RELATED_INTEGRATION");
                        addSystems(integration.participants().systems(), "RELATED_INTEGRATION");
                    });
            catalog.boundedContexts().stream()
                    .filter(context -> referencesSystem(context, primarySystemId))
                    .forEach(context ->
                            addRelated(ConfigDriftViewerOperationalEntityType.BOUNDED_CONTEXT, context));

            addReferencedEntities(primary);
            addSignalCandidates();
            addCodeConfirmedEntities();
        }

        private void addSignalCandidates() {
            for (var difference : deterministicContext.differences()) {
                addSignalCandidates(
                        ConfigDriftViewerOperationalEntityType.SYSTEM,
                        catalog.systems(),
                        difference.path(),
                        difference.differenceId()
                );
                addSignalCandidates(
                        ConfigDriftViewerOperationalEntityType.INTEGRATION,
                        catalog.integrations(),
                        difference.path(),
                        difference.differenceId()
                );
                addSignalCandidates(
                        ConfigDriftViewerOperationalEntityType.PROCESS,
                        catalog.processes(),
                        difference.path(),
                        difference.differenceId()
                );
                addSignalCandidates(
                        ConfigDriftViewerOperationalEntityType.BOUNDED_CONTEXT,
                        catalog.boundedContexts(),
                        difference.path(),
                        difference.differenceId()
                );
            }
        }

        private <T extends OperationalContextEntry> void addSignalCandidates(
                ConfigDriftViewerOperationalEntityType type,
                List<T> candidates,
                String path,
                String differenceId
        ) {
            for (var candidate : candidates) {
                if (matchesSignal(path, candidate)) {
                    add(
                            type,
                            candidate,
                            "CONFIGURATION_KEY_SIGNAL",
                            ConfigDriftViewerGroundingConfidence.LOW,
                            List.of(differenceId),
                            List.of()
                    );
                }
            }
        }

        private boolean matchesSignal(String path, OperationalContextEntry entry) {
            var pathTokens = tokens(path);
            return entry.genericSignals().stream()
                    .flatMap(signal -> tokens(signal).stream())
                    .anyMatch(pathTokens::contains);
        }

        private Set<String> tokens(String value) {
            if (!StringUtils.hasText(value)) {
                return Set.of();
            }
            var tokens = new LinkedHashSet<String>();
            for (var token : value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                    .toLowerCase(Locale.ROOT)
                    .split("[^a-z0-9]+")) {
                if (token.length() >= 4) {
                    tokens.add(token);
                }
            }
            return Set.copyOf(tokens);
        }

        private void addCodeConfirmedEntities() {
            var groundingsByRepository = codeSearch.groundings().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            grounding -> grounding.repositoryId(),
                            LinkedHashMap::new,
                            java.util.stream.Collectors.toList()
                    ));
            for (var entry : groundingsByRepository.entrySet()) {
                var repository = catalog.repositories().stream()
                        .filter(candidate -> same(candidate.id(), entry.getKey()))
                        .findFirst()
                        .orElse(null);
                if (repository == null) {
                    continue;
                }
                repositoryIds.add(repository.id());
                entry.getValue().forEach(grounding -> scopeIds.add(grounding.scopeId()));
                var codeIds = entry.getValue().stream()
                        .map(grounding -> grounding.groundingId())
                        .toList();
                var differenceIds = entry.getValue().stream()
                        .map(grounding -> grounding.differenceId())
                        .distinct()
                        .toList();
                addReferences(
                        repository.references(),
                        "CODE_CONFIRMED_REPOSITORY",
                        ConfigDriftViewerGroundingConfidence.HIGH,
                        differenceIds,
                        codeIds
                );
            }
        }

        private void addReferencedEntities(OperationalContextEntry entry) {
            addReferences(
                    entry.references(),
                    "DIRECT_CATALOG_REFERENCE",
                    ConfigDriftViewerGroundingConfidence.MEDIUM,
                    List.of(),
                    List.of()
            );
        }

        private void addReferences(
                pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences references,
                String evidenceKind,
                ConfigDriftViewerGroundingConfidence confidence,
                List<String> differenceIds,
                List<String> codeIds
        ) {
            addByIds(ConfigDriftViewerOperationalEntityType.SYSTEM, catalog.systems(), references.systems(), evidenceKind, confidence, differenceIds, codeIds);
            addByIds(ConfigDriftViewerOperationalEntityType.INTEGRATION, catalog.integrations(), references.integrations(), evidenceKind, confidence, differenceIds, codeIds);
            addByIds(ConfigDriftViewerOperationalEntityType.PROCESS, catalog.processes(), references.processes(), evidenceKind, confidence, differenceIds, codeIds);
            addByIds(ConfigDriftViewerOperationalEntityType.BOUNDED_CONTEXT, catalog.boundedContexts(), references.boundedContexts(), evidenceKind, confidence, differenceIds, codeIds);
        }

        private <T extends OperationalContextEntry> void addByIds(
                ConfigDriftViewerOperationalEntityType type,
                List<T> candidates,
                List<String> ids,
                String evidenceKind,
                ConfigDriftViewerGroundingConfidence confidence,
                List<String> differenceIds,
                List<String> codeIds
        ) {
            candidates.stream()
                    .filter(candidate -> contains(ids, candidate.id()))
                    .forEach(candidate ->
                            add(type, candidate, evidenceKind, confidence, differenceIds, codeIds));
        }

        private void addRelated(
                ConfigDriftViewerOperationalEntityType type,
                OperationalContextEntry entry
        ) {
            add(
                    type,
                    entry,
                    "RELATED_TO_PRIMARY_SYSTEM",
                    ConfigDriftViewerGroundingConfidence.MEDIUM,
                    List.of(),
                    List.of()
            );
            addReferencedEntities(entry);
        }

        private void addSystems(List<String> systemIds, String evidenceKind) {
            addByIds(
                    ConfigDriftViewerOperationalEntityType.SYSTEM,
                    catalog.systems(),
                    systemIds,
                    evidenceKind,
                    ConfigDriftViewerGroundingConfidence.MEDIUM,
                    List.of(),
                    List.of()
            );
        }

        private void add(
                ConfigDriftViewerOperationalEntityType type,
                OperationalContextEntry entry,
                String evidenceKind,
                ConfigDriftViewerGroundingConfidence confidence,
                List<String> differenceIds,
                List<String> codeIds
        ) {
            var key = type + ":" + normalize(entry.id());
            var existing = entities.get(key);
            if (existing != null && rank(existing.confidence()) >= rank(confidence)) {
                entities.put(key, merge(existing, differenceIds, codeIds));
                return;
            }
            entities.put(key, new ConfigDriftViewerAffectedEntity(
                    type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ":" + entry.id(),
                    type,
                    entry.id(),
                    entry.label(),
                    entry.summary(),
                    evidenceKind,
                    confidence,
                    distinct(differenceIds),
                    distinct(codeIds)
            ));
        }

        private ConfigDriftViewerAffectedEntity merge(
                ConfigDriftViewerAffectedEntity existing,
                List<String> differenceIds,
                List<String> codeIds
        ) {
            var mergedDifferences = new LinkedHashSet<>(existing.differenceIds());
            mergedDifferences.addAll(differenceIds);
            var mergedCode = new LinkedHashSet<>(existing.codeGroundingIds());
            mergedCode.addAll(codeIds);
            return new ConfigDriftViewerAffectedEntity(
                    existing.contextId(),
                    existing.type(),
                    existing.entityId(),
                    existing.label(),
                    existing.summary(),
                    existing.evidenceKind(),
                    existing.confidence(),
                    List.copyOf(mergedDifferences),
                    List.copyOf(mergedCode)
            );
        }

        private int rank(ConfigDriftViewerGroundingConfidence confidence) {
            return switch (confidence) {
                case HIGH -> 3;
                case MEDIUM -> 2;
                case LOW -> 1;
            };
        }

        private List<ConfigDriftViewerAffectedEntity> entities(
                ConfigDriftViewerOperationalEntityType type
        ) {
            return entities.values().stream()
                    .filter(entity -> entity.type() == type)
                    .toList();
        }

        private List<String> systemIds() {
            return entities(ConfigDriftViewerOperationalEntityType.SYSTEM).stream()
                    .map(ConfigDriftViewerAffectedEntity::entityId)
                    .toList();
        }

        private List<String> boundedContextIds() {
            return entities(ConfigDriftViewerOperationalEntityType.BOUNDED_CONTEXT).stream()
                    .map(ConfigDriftViewerAffectedEntity::entityId)
                    .toList();
        }

        private List<String> repositoryIds() {
            return List.copyOf(repositoryIds);
        }

        private List<String> scopeIds() {
            return scopeIds.stream().filter(StringUtils::hasText).toList();
        }

        private boolean hasLowConfidenceCandidates() {
            return entities.values().stream()
                    .anyMatch(entity ->
                            entity.confidence() == ConfigDriftViewerGroundingConfidence.LOW
                                    && (entity.type() == ConfigDriftViewerOperationalEntityType.SYSTEM
                                    || entity.type()
                                    == ConfigDriftViewerOperationalEntityType.BOUNDED_CONTEXT));
        }

        private boolean referencesSystem(OperationalContextEntry entry, String systemId) {
            return contains(entry.references().systems(), systemId);
        }

        private boolean contains(List<String> ids, String id) {
            return ids != null && ids.stream().anyMatch(candidate -> same(candidate, id));
        }

        private boolean same(String left, String right) {
            return normalize(left).equals(normalize(right));
        }

        private String normalize(String value) {
            return StringUtils.hasText(value)
                    ? value.trim().toLowerCase(Locale.ROOT).replace('_', '-')
                    : "";
        }

        private List<String> distinct(List<String> values) {
            return values != null
                    ? values.stream().filter(StringUtils::hasText).distinct().toList()
                    : List.of();
        }
    }
}
