package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegrationParticipant;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OperationalContextReadModelValidator {

    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String INTEGRATION = "integration";
    private static final String SYSTEM = "system";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String REPOSITORY = "repository";

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
        validateTeamReferenceDuplicates(safeCatalog, findings);
        validateBidirectionalReferences(relationIndex.relations(), findings);
        validateCodeSearchScopes(safeCatalog, findings);

        return sortedDistinct(findings);
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

    private void validateTeamReferenceDuplicates(
            OperationalContextCatalog catalog,
            List<ValidationFinding> findings
    ) {
        for (var team : catalog.teams()) {
            var responsibilityTargets = teamResponsibilityTargets(team);
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().systems(),
                    responsibilityTargets.systems(),
                    "references.systems",
                    "references-system"
            );
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().repositories(),
                    responsibilityTargets.repositories(),
                    "references.repositories",
                    "references-repository"
            );
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().processes(),
                    responsibilityTargets.processes(),
                    "references.processes",
                    "references-process"
            );
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().boundedContexts(),
                    responsibilityTargets.boundedContexts(),
                    "references.boundedContexts",
                    "references-bounded-context"
            );
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().integrations(),
                    responsibilityTargets.integrations(),
                    "references.integrations",
                    "references-integration"
            );
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().terms(),
                    responsibilityTargets.terms(),
                    "references.terms",
                    "references-term"
            );
            warnTeamReferenceDuplicate(
                    findings,
                    team.id(),
                    team.references().handoffRules(),
                    responsibilityTargets.handoffRules(),
                    "references.handoffRules",
                    "references-handoff-rule"
            );
        }
    }

    private void warnTeamReferenceDuplicate(
            List<ValidationFinding> findings,
            String teamId,
            List<String> references,
            Set<String> responsibilityTargets,
            String fieldPath,
            String relationRole
    ) {
        for (var targetId : references) {
            if (!responsibilityTargets.contains(targetId)) {
                continue;
            }
            findings.add(new ValidationFinding(
                    "error",
                    "TEAM_REFERENCE_DERIVED_FROM_RESPONSIBILITY",
                    "Team " + teamId + " keeps " + targetId + " in " + fieldPath
                            + ", but the same ownership target is already declared in responsibilities.",
                    List.of(new SourceRef(
                            "src/main/resources/operational-context/teams.yml",
                            "team",
                            teamId,
                            "$.teams[id=" + teamId + "]." + fieldPath,
                            relationRole
                    ))
            ));
        }
    }

    private TeamResponsibilityTargets teamResponsibilityTargets(OperationalContextDtos.OperationalContextTeam team) {
        var systems = new LinkedHashSet<String>();
        var repositories = new LinkedHashSet<String>();
        var processes = new LinkedHashSet<String>();
        var boundedContexts = new LinkedHashSet<String>();
        var integrations = new LinkedHashSet<String>();
        var terms = new LinkedHashSet<String>();
        var handoffRules = new LinkedHashSet<String>();

        for (var responsibility : team.responsibilities()) {
            var targetId = text(responsibility.targetId());
            if (!StringUtils.hasText(targetId)) {
                continue;
            }
            switch (normalizeTargetType(responsibility.targetType())) {
                case SYSTEM -> systems.add(targetId);
                case REPOSITORY -> repositories.add(targetId);
                case "process" -> processes.add(targetId);
                case BOUNDED_CONTEXT -> boundedContexts.add(targetId);
                case INTEGRATION -> integrations.add(targetId);
                case "term" -> terms.add(targetId);
                case "handoff-rule" -> handoffRules.add(targetId);
                default -> {
                }
            }
        }

        return new TeamResponsibilityTargets(
                Set.copyOf(systems),
                Set.copyOf(repositories),
                Set.copyOf(processes),
                Set.copyOf(boundedContexts),
                Set.copyOf(integrations),
                Set.copyOf(terms),
                Set.copyOf(handoffRules)
        );
    }

    private String normalizeTargetType(String targetType) {
        var normalized = text(targetType).replace("_", "-").replace(" ", "-");
        return switch (normalized) {
            case "systems" -> SYSTEM;
            case "repositories", "repo" -> REPOSITORY;
            case "processes" -> "process";
            case "boundedContext", "boundedContexts", "bounded-contexts", "context", "contexts" -> BOUNDED_CONTEXT;
            case "integrations" -> INTEGRATION;
            case "terms", "glossary-term", "glossary-terms" -> "term";
            case "handoffRule", "handoffRules", "handoff-rules" -> "handoff-rule";
            default -> normalized;
        };
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
        for (var scope : catalog.codeSearchScopes()) {
            validateCodeSearchScopeShape(scope, repositoryIds, findings);
            var readModel = codeSearchReadModelBuilder.buildForEntity(catalog, CODE_SEARCH_SCOPE, scope.id());
            findings.addAll(readModel.validationFindings());
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
                .anyMatch(repository -> "primary-implementation".equalsIgnoreCase(text(repository.role()))
                        || "primary".equalsIgnoreCase(text(repository.role()))
                        || Integer.valueOf(1).equals(repository.priority()));
        if (!includedRepositories.isEmpty() && !hasPrimaryRepository) {
            findings.add(new ValidationFinding(
                    "warning",
                    "CODE_SEARCH_SCOPE_WITHOUT_PRIMARY_REPOSITORY",
                    "Code-search scope " + scope.id() + " has repositories but no primary implementation or priority 1 repository.",
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

    private record DerivedBoundedContextReferences(Set<String> systems, Set<String> integrations) {
    }

    private record TeamResponsibilityTargets(
            Set<String> systems,
            Set<String> repositories,
            Set<String> processes,
            Set<String> boundedContexts,
            Set<String> integrations,
            Set<String> terms,
            Set<String> handoffRules
    ) {
    }
}
