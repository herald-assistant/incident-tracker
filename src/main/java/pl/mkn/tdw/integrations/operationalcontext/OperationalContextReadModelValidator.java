package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegrationParticipant;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOwnership;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.CODE_SEARCH_MODE_PATH_PREFIXES;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.CODE_SEARCH_MODE_WHOLE_REPOSITORY;

public class OperationalContextReadModelValidator {

    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String INTEGRATION = "integration";
    private static final String SYSTEM = "system";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String REPOSITORY = "repository";
    private static final String PROCESS = "process";
    private static final String TEAM = "team";

    private static final Map<String, String> SOURCE_FILES = Map.of(
            SYSTEM, "systems.yml",
            REPOSITORY, "repo-map.yml",
            CODE_SEARCH_SCOPE, "code-search-scopes.yml",
            PROCESS, "processes.yml",
            INTEGRATION, "integrations.yml",
            BOUNDED_CONTEXT, "bounded-contexts.yml",
            TEAM, "teams.yml"
    );

    private static final Map<String, String> SOURCE_COLLECTIONS = Map.of(
            SYSTEM, "systems",
            REPOSITORY, "repositories",
            CODE_SEARCH_SCOPE, "codeSearchScopes",
            PROCESS, "processes",
            INTEGRATION, "integrations",
            BOUNDED_CONTEXT, "boundedContexts",
            TEAM, "teams"
    );

    private static final Set<String> PARTICIPANT_SYSTEM_RELATIONS = Set.of(
            "source-system",
            "target-system",
            "intermediary-system",
            "final-target-system"
    );

    private final OperationalContextRelationIndexBuilder relationIndexBuilder;
    private final OperationalContextCodeSearchReadModelBuilder codeSearchReadModelBuilder;

    public OperationalContextReadModelValidator() {
        this(new OperationalContextRelationIndexBuilder());
    }

    public OperationalContextReadModelValidator(
            OperationalContextRelationIndexBuilder relationIndexBuilder
    ) {
        this.relationIndexBuilder = relationIndexBuilder;
        this.codeSearchReadModelBuilder = new OperationalContextCodeSearchReadModelBuilder(relationIndexBuilder);
    }

    public List<ValidationFinding> validate(OperationalContextCatalog catalog) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var relationIndex = relationIndexBuilder.build(safeCatalog);
        var findings = new ArrayList<ValidationFinding>();

        findings.addAll(relationIndex.validationFindings());
        validateRelationProvenance(relationIndex.relations(), findings);
        validateMergedDuplicateRelations(relationIndex.relations(), findings);
        validateIntegrationParticipantReferenceDuplicates(relationIndex, findings);
        validateSystemDependenciesDerivedFromIntegrations(safeCatalog, findings);
        validateBoundedContextReferencesDerivedFromReadModel(safeCatalog, findings);
        validateProcessParticipantReferenceDuplicates(safeCatalog, findings);
        validateBidirectionalReferences(relationIndex.relations(), findings);
        validateSystemsDoNotReferenceRepositories(safeCatalog, findings);
        validateCodeSearchScopes(safeCatalog, findings);
        validateOwnershipModel(safeCatalog, findings);

        return sortedDistinct(findings);
    }

    public List<ValidationFinding> validateCatalogContract(OperationalContextCatalog catalog) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var relationIndex = relationIndexBuilder.build(safeCatalog);
        var findings = new ArrayList<ValidationFinding>();

        findings.addAll(relationIndex.validationFindings());
        validateOwnershipModel(safeCatalog, findings);

        return sortedDistinct(findings);
    }

    private void validateOwnershipModel(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        for (var system : catalog.systems()) {
            validateEntryPayload(system, SYSTEM, true, findings);
            warnInferredOwnership(system.ownership(), SYSTEM, system.id(), findings);
        }
        for (var boundedContext : catalog.boundedContexts()) {
            validateEntryPayload(boundedContext, BOUNDED_CONTEXT, true, findings);
            warnInferredOwnership(boundedContext.ownership(), BOUNDED_CONTEXT, boundedContext.id(), findings);
        }
        for (var repository : catalog.repositories()) {
            validateEntryPayload(repository, REPOSITORY, false, findings);
        }
        for (var process : catalog.processes()) {
            validateEntryPayload(process, PROCESS, false, findings);
        }
        for (var integration : catalog.integrations()) {
            validateEntryPayload(integration, INTEGRATION, false, findings);
        }
        for (var team : catalog.teams()) {
            validateEntryPayload(team, TEAM, false, findings);
        }
        for (var scope : catalog.codeSearchScopes()) {
            validatePayload(
                    CODE_SEARCH_SCOPE,
                    scope.id(),
                    rootPath(CODE_SEARCH_SCOPE, scope.id()),
                    scope.payload(),
                    false,
                    findings
            );
        }
    }

    private void validateEntryPayload(
            OperationalContextEntry entry,
            String entityType,
            boolean ownershipAllowed,
            List<ValidationFinding> findings
    ) {
        validatePayload(
                entityType,
                entry.id(),
                rootPath(entityType, entry.id()),
                entry.payload(),
                ownershipAllowed,
                findings
        );
    }

    private void validatePayload(
            String entityType,
            String entityId,
            String fieldPath,
            Object value,
            boolean ownershipAllowed,
            List<ValidationFinding> findings
    ) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                var childPath = fieldPath + "." + key;
                if (!ownershipAllowed && "ownership".equals(key)) {
                    findings.add(new ValidationFinding(
                            "error",
                            "OWNERSHIP_OUTSIDE_SYSTEM_OR_BOUNDED_CONTEXT",
                            "Ownership for " + entityType + " " + entityId
                                    + " is not allowed; ownership can be declared only on system or bounded-context.",
                            List.of(sourceRef(entityType, entityId, childPath, "ownership"))
                    ));
                }
                validatePayload(entityType, entityId, childPath, entry.getValue(), ownershipAllowed, findings);
            }
        } else if (value instanceof List<?> list) {
            for (var index = 0; index < list.size(); index++) {
                validatePayload(
                        entityType,
                        entityId,
                        fieldPath + "[" + index + "]",
                        list.get(index),
                        ownershipAllowed,
                        findings
                );
            }
        }
    }

    private void warnInferredOwnership(
            OperationalContextOwnership ownership,
            String entityType,
            String entityId,
            List<ValidationFinding> findings
    ) {
        if (ownership == null || !ownership.hasOwner()) {
            return;
        }
        if ("explicit".equalsIgnoreCase(text(ownership.ownershipStatus()))) {
            return;
        }

        findings.add(new ValidationFinding(
                "warning",
                "INFERRED_OWNERSHIP_IN_CATALOG",
                "Owner for " + entityType + " " + entityId
                        + " is not explicit; inferred owners should stay in resolver output, not in catalog data.",
                List.of(sourceRef(entityType, entityId, rootPath(entityType, entityId) + ".ownership", "ownership"))
        ));
    }

    private String rootPath(String entityType, String entityId) {
        return "$." + SOURCE_COLLECTIONS.getOrDefault(entityType, entityType) + "[id=" + entityId + "]";
    }

    private SourceRef sourceRef(
            String entityType,
            String entityId,
            String fieldPath,
            String relationRole
    ) {
        return new SourceRef(
                "src/main/resources/operational-context/"
                        + SOURCE_FILES.getOrDefault(entityType, "operational-context"),
                entityType,
                entityId,
                fieldPath,
                relationRole
        );
    }

    private void validateRelationProvenance(
            List<ReadModelRelation> relations,
            List<ValidationFinding> findings
    ) {
        for (var relation : relations) {
            if (relation.canonicalOwner() == null) {
                findings.add(new ValidationFinding(
                        "warning",
                        "RELATION_WITHOUT_CANONICAL_OWNER",
                        "Relation " + label(relation) + " has no canonical owner.",
                        sourceRefs(relation)
                ));
            }

            if (relation.provenance() == null || relation.provenance().sourceRefs().isEmpty()) {
                findings.add(new ValidationFinding(
                        "warning",
                        "RELATION_WITHOUT_SOURCE_REF",
                        "Relation " + label(relation) + " has no source refs.",
                        sourceRefs(relation)
                ));
            }
        }
    }

    private void validateMergedDuplicateRelations(
            List<ReadModelRelation> relations,
            List<ValidationFinding> findings
    ) {
        for (var relation : relations) {
            if (relation.provenance().warnings().isEmpty()) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "warning",
                    "DUPLICATE_RELATION_MERGED",
                    "Relation " + label(relation) + " was declared more than once and merged into one read-model edge.",
                    sourceRefs(relation)
            ));
        }
    }

    private void validateIntegrationParticipantReferenceDuplicates(
            OperationalContextRelationIndex relationIndex,
            List<ValidationFinding> findings
    ) {
        for (var entry : relationIndex.outgoingRelations().entrySet()) {
            var source = entry.getKey();
            if (!INTEGRATION.equals(source.type())) {
                continue;
            }

            warnParticipantReferenceDuplicate(
                    findings,
                    entry.getValue(),
                    source,
                    SYSTEM,
                    PARTICIPANT_SYSTEM_RELATIONS,
                    "references-system",
                    "DUPLICATED_PARTICIPANT_REFERENCE_SYSTEM"
            );
            warnParticipantReferenceDuplicate(
                    findings,
                    entry.getValue(),
                    source,
                    BOUNDED_CONTEXT,
                    Set.of("participant-bounded-context"),
                    "references-bounded-context",
                    "DUPLICATED_PARTICIPANT_REFERENCE_BOUNDED_CONTEXT"
            );
        }
    }

    private void warnParticipantReferenceDuplicate(
            List<ValidationFinding> findings,
            List<ReadModelRelation> relations,
            EntityKey source,
            String targetType,
            Set<String> participantRelationTypes,
            String referenceRelationType,
            String code
    ) {
        var participantsByTarget = relationsByTarget(relations, targetType, participantRelationTypes);
        var referencesByTarget = relationsByTarget(relations, targetType, Set.of(referenceRelationType));

        for (var entry : participantsByTarget.entrySet()) {
            var referenceRelation = referencesByTarget.get(entry.getKey());
            if (referenceRelation == null) {
                continue;
            }
            var participantRelation = entry.getValue();
            findings.add(new ValidationFinding(
                    "error",
                    code,
                    "Integration " + source.value() + " keeps " + entry.getKey().value()
                            + " both in participants and references.",
                    concatSourceRefs(participantRelation, referenceRelation)
            ));
        }
    }

    private Map<EntityKey, ReadModelRelation> relationsByTarget(
            List<ReadModelRelation> relations,
            String targetType,
            Set<String> relationTypes
    ) {
        var result = new LinkedHashMap<EntityKey, ReadModelRelation>();
        for (var relation : relations) {
            if (targetType.equals(relation.target().type()) && relationTypes.contains(relation.relationType())) {
                result.putIfAbsent(relation.target(), relation);
            }
        }
        return result;
    }

    private void validateSystemDependenciesDerivedFromIntegrations(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        var integrationPairs = integrationSystemPairs(catalog);
        for (var system : catalog.systems()) {
            warnSystemDependencyDerivedFromIntegrations(
                    findings,
                    system,
                    system.values("dependencies.downstream"),
                    "dependencies.downstream",
                    "depends-on-downstream-system",
                    true,
                    integrationPairs
            );
            warnSystemDependencyDerivedFromIntegrations(
                    findings,
                    system,
                    system.values("dependencies.upstream"),
                    "dependencies.upstream",
                    "depends-on-upstream-system",
                    false,
                    integrationPairs
            );
        }
    }

    private void warnSystemDependencyDerivedFromIntegrations(
            List<ValidationFinding> findings,
            OperationalContextSystem system,
            List<String> targets,
            String fieldPath,
            String relationRole,
            boolean downstream,
            Set<String> integrationPairs
    ) {
        for (var target : targets) {
            var pair = downstream ? pair(system.id(), target) : pair(target, system.id());
            if (!integrationPairs.contains(pair)) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "error",
                    "SYSTEM_DEPENDENCY_DERIVED_FROM_INTEGRATION",
                    "System " + system.id() + " keeps dependency " + target + " in " + fieldPath
                            + ", but the same directed dependency is derivable from integrations.yml participants.",
                    List.of(new SourceRef(
                            "src/main/resources/operational-context/systems.yml",
                            SYSTEM,
                            system.id(),
                            "$.systems[id=" + system.id() + "]." + fieldPath,
                            relationRole
                    ))
            ));
        }
    }

    private Set<String> integrationSystemPairs(OperationalContextCatalog catalog) {
        var result = new LinkedHashSet<String>();
        for (var integration : catalog.integrations()) {
            var source = text(integration.participants().source().system());
            if (!StringUtils.hasText(source)) {
                continue;
            }
            var bidirectional = isBidirectional(integration.flowDirection());
            addIntegrationSystemPairs(result, source, integration.participants().targets(), bidirectional);
            addIntegrationSystemPairs(result, source, integration.participants().intermediaries(), bidirectional);
            addIntegrationSystemPairs(result, source, integration.participants().finalTargets(), bidirectional);
        }
        return Set.copyOf(result);
    }

    private void addIntegrationSystemPairs(
            Set<String> pairs,
            String source,
            List<OperationalContextIntegrationParticipant> targets,
            boolean bidirectional
    ) {
        for (var target : targets) {
            var targetSystem = text(target.system());
            if (!StringUtils.hasText(targetSystem) || source.equals(targetSystem)) {
                continue;
            }
            pairs.add(pair(source, targetSystem));
            if (bidirectional) {
                pairs.add(pair(targetSystem, source));
            }
        }
    }

    private boolean isBidirectional(String flowDirection) {
        var normalized = text(flowDirection).toLowerCase(Locale.ROOT);
        return normalized.contains("bidirectional")
                || normalized.contains("two-way")
                || normalized.contains("duplex");
    }

    private void validateBoundedContextReferencesDerivedFromReadModel(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        var derivedReferences = boundedContextDerivedReferences(catalog);
        for (var context : catalog.boundedContexts()) {
            warnBoundedContextDerivedReference(
                    findings,
                    context.id(),
                    context.references().systems(),
                    "references.systems",
                    "references-system",
                    derivedReferences.systems(),
                    "BOUNDED_CONTEXT_SYSTEM_REFERENCE_DERIVED"
            );
            warnBoundedContextDerivedReference(
                    findings,
                    context.id(),
                    context.references().integrations(),
                    "references.integrations",
                    "references-integration",
                    derivedReferences.integrations(),
                    "BOUNDED_CONTEXT_INTEGRATION_REFERENCE_DERIVED"
            );
        }
    }

    private void warnBoundedContextDerivedReference(
            List<ValidationFinding> findings,
            String boundedContextId,
            List<String> targetIds,
            String fieldPath,
            String relationRole,
            Set<String> derivedPairs,
            String code
    ) {
        for (var targetId : targetIds) {
            if (!derivedPairs.contains(pair(boundedContextId, targetId))) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "error",
                    code,
                    "Bounded context " + boundedContextId + " keeps " + targetId + " in " + fieldPath
                            + ", but the same relation is derivable from integration participants/references or code-search targets.",
                    List.of(new SourceRef(
                            "src/main/resources/operational-context/bounded-contexts.yml",
                            BOUNDED_CONTEXT,
                            boundedContextId,
                            "$.boundedContexts[id=" + boundedContextId + "]." + fieldPath,
                            relationRole
                    ))
            ));
        }
    }

    private DerivedBoundedContextReferences boundedContextDerivedReferences(OperationalContextCatalog catalog) {
        var systems = new LinkedHashSet<String>();
        var integrations = new LinkedHashSet<String>();

        for (var integration : catalog.integrations()) {
            addBoundedContextDerivedReferences(
                    systems,
                    integrations,
                    integration.id(),
                    integration.participants().source()
            );
            integration.participants().targets().forEach(participant ->
                    addBoundedContextDerivedReferences(systems, integrations, integration.id(), participant));
            integration.participants().intermediaries().forEach(participant ->
                    addBoundedContextDerivedReferences(systems, integrations, integration.id(), participant));
            integration.participants().finalTargets().forEach(participant ->
                    addBoundedContextDerivedReferences(systems, integrations, integration.id(), participant));
            integration.references().boundedContexts().forEach(boundedContextId ->
                    integrations.add(pair(boundedContextId, integration.id())));
        }

        return new DerivedBoundedContextReferences(Set.copyOf(systems), Set.copyOf(integrations));
    }

    private void addBoundedContextDerivedReferences(
            Set<String> systems,
            Set<String> integrations,
            String integrationId,
            OperationalContextIntegrationParticipant participant
    ) {
        var boundedContextId = text(participant.boundedContext());
        if (!StringUtils.hasText(boundedContextId)) {
            return;
        }
        integrations.add(pair(boundedContextId, integrationId));

        var systemId = text(participant.system());
        if (StringUtils.hasText(systemId)) {
            systems.add(pair(boundedContextId, systemId));
        }
    }

    private void validateProcessParticipantReferenceDuplicates(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        for (var process : catalog.processes()) {
            var participantSystems = processParticipantSystems(process);
            for (var systemId : process.references().systems()) {
                if (!participantSystems.contains(systemId)) {
                    continue;
                }
                findings.add(new ValidationFinding(
                        "error",
                        "PROCESS_PARTICIPANT_REFERENCE_SYSTEM",
                        "Process " + process.id() + " keeps " + systemId
                                + " both in participants and references.systems.",
                        List.of(new SourceRef(
                                "src/main/resources/operational-context/processes.yml",
                                "process",
                                process.id(),
                                "$.processes[id=" + process.id() + "].references.systems",
                                "references-system"
                        ))
                ));
            }
        }
    }

    private Set<String> processParticipantSystems(OperationalContextProcess process) {
        var result = new LinkedHashSet<String>();
        result.addAll(process.participants().primarySystems());
        result.addAll(process.participants().supportingSystems());
        result.addAll(process.participants().externalSystems());
        result.addAll(process.participants().platformComponents());
        return Set.copyOf(result);
    }

    private void validateBidirectionalReferences(
            List<ReadModelRelation> relations,
            List<ValidationFinding> findings
    ) {
        var references = relations.stream()
                .filter(relation -> relation.relationType().startsWith("references-"))
                .toList();
        var byDirection = new LinkedHashMap<String, ReadModelRelation>();
        for (var relation : references) {
            byDirection.putIfAbsent(relation.source().value() + "->" + relation.target().value(), relation);
        }

        var reported = new LinkedHashSet<String>();
        for (var relation : references) {
            var reverse = byDirection.get(relation.target().value() + "->" + relation.source().value());
            if (reverse == null) {
                continue;
            }
            var pairKey = unorderedPairKey(relation.source(), relation.target());
            if (!reported.add(pairKey)) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "error",
                    "BIDIRECTIONAL_REFERENCE",
                    "Reference relation is kept in both directions for " + pairKey + ".",
                    concatSourceRefs(relation, reverse)
            ));
        }
    }

    private void validateCodeSearchScopes(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        var repositoryIds = repositoryIds(catalog);
        var scopedSystemIds = new LinkedHashSet<String>();
        for (var scope : catalog.codeSearchScopes()) {
            validateCodeSearchScopeShape(scope, repositoryIds, findings);
            if (isSystemTarget(scope)) {
                scopedSystemIds.add(text(scope.target().id()));
            }
            var readModel = codeSearchReadModelBuilder.buildForEntity(catalog, CODE_SEARCH_SCOPE, scope.id());
            findings.addAll(readModel.validationFindings());
        }
        validateInternalSystemsHaveCodeSearchScope(catalog.systems(), scopedSystemIds, findings);
    }

    private void validateSystemsDoNotReferenceRepositories(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        for (var system : catalog.systems()) {
            if (system.references().repositories().isEmpty()) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "error",
                    "SYSTEM_REPOSITORY_REFERENCE_NOT_ALLOWED",
                    "System " + system.id()
                            + " must not declare references.repositories; define a code-search scope targeting system:"
                            + system.id() + " and keep repository ownership/context on repo-map.yml.",
                    List.of(sourceRef(
                            SYSTEM,
                            system.id(),
                            rootPath(SYSTEM, system.id()) + ".references.repositories",
                            "references-repository"
                    ))
            ));
        }
    }

    private void validateInternalSystemsHaveCodeSearchScope(
            List<OperationalContextSystem> systems,
            Set<String> scopedSystemIds,
            List<ValidationFinding> findings
    ) {
        for (var system : systems) {
            if (!needsCodeSearchScope(system) || scopedSystemIds.contains(system.id())) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "warning",
                    "INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE",
                    "Internal system " + system.id()
                            + " has no code-search scope; code discovery will not infer repositories from system references.",
                    List.of(sourceRef(SYSTEM, system.id(), rootPath(SYSTEM, system.id()), "code-search-scope"))
            ));
        }
    }

    private void validateCodeSearchScopeShape(
            OperationalContextRepositorySearchScope scope,
            Set<String> repositoryIds,
            List<ValidationFinding> findings
    ) {
        var includedRepositories = scope.repositories();
        if (includedRepositories.isEmpty()) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_SCOPE_WITHOUT_INCLUDED_REPOSITORY",
                    "Code-search scope " + scope.id() + " has no repositories.",
                    List.of(scopeRef(scope, "repositories", "repositories"))
            ));
        }

        var hasPrimaryRepository = includedRepositories.stream()
                .anyMatch(repository -> "primary".equalsIgnoreCase(text(repository.role()))
                        || Integer.valueOf(1).equals(repository.priority()));
        if (!includedRepositories.isEmpty() && !hasPrimaryRepository) {
            findings.add(new ValidationFinding(
                    "warning",
                    "CODE_SEARCH_SCOPE_WITHOUT_PRIMARY_REPOSITORY",
                    "Code-search scope " + scope.id() + " has repositories but no primary repository or priority 1 repository.",
                    List.of(scopeRef(scope, "repositories", "primary-repository"))
            ));
        }

        if (scopeTargetValue(scope) == null) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_SCOPE_WITHOUT_TARGET",
                    "Code-search scope " + scope.id() + " has no operational target.",
                    List.of(scopeRef(scope, "target", "scope-target"))
            ));
        } else if (!isSystemTarget(scope)) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_SCOPE_TARGET_NOT_SYSTEM",
                    "Code-search scope " + scope.id()
                            + " must target a system; repository discovery uses only system code-search scopes.",
                    List.of(scopeRef(scope, "target", "scope-target"))
            ));
        }

        for (var repository : includedRepositories) {
            if (StringUtils.hasText(repository.repoId()) && !repositoryIds.contains(repository.repoId())) {
                findings.add(new ValidationFinding(
                        "error",
                        "UNKNOWN_CODE_SEARCH_REPOSITORY",
                        "Code-search scope " + scope.id() + " references unknown repository " + repository.repoId() + ".",
                        List.of(scopeRef(scope, "repositories[repoId=" + repository.repoId() + "]", "references-repository"))
                ));
            }
            validateCodeSearchRepositorySearchBoundary(scope, repository, findings);
        }
    }

    private void validateCodeSearchRepositorySearchBoundary(
            OperationalContextRepositorySearchScope scope,
            OperationalContextDtos.OperationalContextRepositorySearchRepository repository,
            List<ValidationFinding> findings
    ) {
        var searchMode = text(repository.searchMode());
        var fieldPrefix = "repositories[repoId=" + repository.repoId() + "]";
        if (!StringUtils.hasText(searchMode)) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_REPOSITORY_WITHOUT_SEARCH_MODE",
                    "Code-search repository " + repository.repoId() + " in scope " + scope.id()
                            + " must declare searchMode whole-repository or path-prefixes.",
                    List.of(scopeRef(scope, fieldPrefix + ".searchMode", "search-boundary"))
            ));
            return;
        }

        if (!Set.of(CODE_SEARCH_MODE_WHOLE_REPOSITORY, CODE_SEARCH_MODE_PATH_PREFIXES).contains(searchMode)) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_REPOSITORY_UNKNOWN_SEARCH_MODE",
                    "Code-search repository " + repository.repoId() + " in scope " + scope.id()
                            + " uses unsupported searchMode " + searchMode + ".",
                    List.of(scopeRef(scope, fieldPrefix + ".searchMode", "search-boundary"))
            ));
            return;
        }

        if (CODE_SEARCH_MODE_PATH_PREFIXES.equals(searchMode) && repository.pathPrefixes().isEmpty()) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_REPOSITORY_PATH_PREFIXES_EMPTY",
                    "Code-search repository " + repository.repoId() + " in scope " + scope.id()
                            + " uses path-prefixes searchMode but has no pathPrefixes.",
                    List.of(scopeRef(scope, fieldPrefix + ".pathPrefixes", "search-boundary"))
            ));
        }

        if (CODE_SEARCH_MODE_WHOLE_REPOSITORY.equals(searchMode) && !repository.pathPrefixes().isEmpty()) {
            findings.add(new ValidationFinding(
                    "error",
                    "CODE_SEARCH_REPOSITORY_WHOLE_REPOSITORY_WITH_PATH_PREFIXES",
                    "Code-search repository " + repository.repoId() + " in scope " + scope.id()
                            + " uses whole-repository searchMode and must not declare pathPrefixes.",
                    List.of(scopeRef(scope, fieldPrefix + ".pathPrefixes", "search-boundary"))
            ));
        }

        for (var pathPrefix : repository.pathPrefixes()) {
            if (!StringUtils.hasText(pathPrefix) || pathPrefix.startsWith("/") || pathPrefix.contains("\\")) {
                findings.add(new ValidationFinding(
                        "error",
                        "CODE_SEARCH_REPOSITORY_INVALID_PATH_PREFIX",
                        "Code-search repository " + repository.repoId() + " in scope " + scope.id()
                                + " has invalid pathPrefix " + pathPrefix + ". Use relative GitLab paths with / separators.",
                        List.of(scopeRef(scope, fieldPrefix + ".pathPrefixes", "search-boundary"))
                ));
            }
        }
    }

    private Set<String> repositoryIds(OperationalContextCatalog catalog) {
        var result = new LinkedHashSet<String>();
        catalog.repositories().forEach(repository -> {
            if (StringUtils.hasText(repository.id())) {
                result.add(repository.id());
            }
        });
        return Set.copyOf(result);
    }

    private String scopeTargetValue(OperationalContextRepositorySearchScope scope) {
        if (!StringUtils.hasText(scope.target().type()) || !StringUtils.hasText(scope.target().id())) {
            return null;
        }
        return scope.target().value();
    }

    private boolean isSystemTarget(OperationalContextRepositorySearchScope scope) {
        return SYSTEM.equals(normalize(scope.target().type())) && StringUtils.hasText(scope.target().id());
    }

    private boolean needsCodeSearchScope(OperationalContextSystem system) {
        var kind = normalize(system.kind());
        return "internal".equals(kind) || kind.startsWith("internal-") || "api-gateway".equals(kind);
    }

    private SourceRef scopeRef(
            OperationalContextRepositorySearchScope scope,
            String fieldPath,
            String relationRole
    ) {
        return new SourceRef(
                "src/main/resources/operational-context/code-search-scopes.yml",
                CODE_SEARCH_SCOPE,
                scope.id(),
                "$.codeSearchScopes[id=" + scope.id() + "]." + fieldPath,
                relationRole
        );
    }

    private String label(ReadModelRelation relation) {
        return relation.source().value() + " -" + relation.relationType() + "-> " + relation.target().value();
    }

    private String unorderedPairKey(EntityKey first, EntityKey second) {
        return first.value().compareTo(second.value()) <= 0
                ? first.value() + "<->" + second.value()
                : second.value() + "<->" + first.value();
    }

    private String pair(String source, String target) {
        return text(source) + "->" + text(target);
    }

    private List<SourceRef> sourceRefs(ReadModelRelation relation) {
        if (relation.provenance() == null) {
            return List.of();
        }
        return relation.provenance().sourceRefs();
    }

    private List<SourceRef> concatSourceRefs(ReadModelRelation first, ReadModelRelation second) {
        var refs = new LinkedHashMap<String, SourceRef>();
        addRefs(refs, sourceRefs(first));
        addRefs(refs, sourceRefs(second));
        return List.copyOf(refs.values());
    }

    private void addRefs(Map<String, SourceRef> refs, Collection<SourceRef> sourceRefs) {
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

    private List<ValidationFinding> sortedDistinct(List<ValidationFinding> findings) {
        var result = new LinkedHashMap<String, ValidationFinding>();
        for (var finding : findings) {
            result.putIfAbsent(finding.code() + "|" + finding.message(), finding);
        }
        return result.values().stream()
                .sorted(Comparator
                        .comparing(ValidationFinding::severity)
                        .thenComparing(ValidationFinding::code)
                        .thenComparing(ValidationFinding::message))
                .toList();
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalize(String value) {
        return text(value)
                .replace("_", "-")
                .replace(" ", "-")
                .toLowerCase(Locale.ROOT);
    }

    private record DerivedBoundedContextReferences(Set<String> systems, Set<String> integrations) {
    }

}
