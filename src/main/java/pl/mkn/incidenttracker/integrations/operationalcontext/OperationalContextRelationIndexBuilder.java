package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegrationParticipant;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcessStep;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRelation;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositoryModule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class OperationalContextRelationIndexBuilder {

    private static final String SYSTEM = "system";
    private static final String REPOSITORY = "repository";
    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String PROCESS = "process";
    private static final String PROCESS_STEP = "process-step";
    private static final String INTEGRATION = "integration";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String TEAM = "team";
    private static final String TERM = "term";
    private static final String HANDOFF_RULE = "handoff-rule";
    private static final String MODULE = "module";
    private static final String DATASTORE = "datastore";
    private static final String EXTERNAL_PARTY = "external-party";
    private static final String RUNTIME_SIGNAL = "runtime-signal";

    public OperationalContextRelationIndex build(OperationalContextCatalog catalog) {
        var state = new BuildState();
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();

        registerEntities(state, safeCatalog);
        extractSystemRelations(state, safeCatalog.systems());
        extractRepositoryRelations(state, safeCatalog.repositories());
        extractCodeSearchScopeRelations(state, safeCatalog.codeSearchScopes(), safeCatalog.repositories());
        extractProcessRelations(state, safeCatalog.processes());
        extractIntegrationRelations(state, safeCatalog.integrations());
        extractBoundedContextRelations(state, safeCatalog.boundedContexts());
        extractTeamRelations(state, safeCatalog.teams());
        extractGlossaryRelations(state, safeCatalog.glossaryTerms());
        extractHandoffRuleRelations(state, safeCatalog.handoffRules());

        return state.toIndex();
    }

    private void registerEntities(BuildState state, OperationalContextCatalog catalog) {
        catalog.systems().forEach(system -> registerEntry(state, SYSTEM, system));
        catalog.repositories().forEach(repository -> {
            registerEntry(state, REPOSITORY, repository);
            repository.modules().forEach(module -> registerModule(state, repository, module));
        });
        catalog.codeSearchScopes().forEach(scope -> register(state, CODE_SEARCH_SCOPE, scope.id(), scope.name(), scope.lifecycleStatus(), null));
        catalog.processes().forEach(process -> {
            registerEntry(state, PROCESS, process);
            process.steps().forEach(step -> registerProcessStep(state, process, step));
        });
        catalog.integrations().forEach(integration -> registerEntry(state, INTEGRATION, integration));
        catalog.boundedContexts().forEach(context -> registerEntry(state, BOUNDED_CONTEXT, context));
        catalog.teams().forEach(team -> registerEntry(state, TEAM, team));
        catalog.glossaryTerms().forEach(term -> register(state, TERM, term.id(), term.term(), null, term.definition()));
        catalog.handoffRules().forEach(rule -> register(state, HANDOFF_RULE, rule.id(), rule.title(), null, null));
    }

    private void extractSystemRelations(BuildState state, List<OperationalContextSystem> systems) {
        for (var system : systems) {
            var source = new EntityKey(SYSTEM, system.id());
            references(state, source, system.references(), "systems.yml", SYSTEM, system.id(), "$.systems[id=" + system.id() + "].references", true);
            responsibilities(state, source, system.responsibilities(), "systems.yml", SYSTEM, system.id(), "$.systems[id=" + system.id() + "].responsibilities");
            explicitRelations(state, source, system.relations(), "systems.yml", system.id(), "$.systems[id=" + system.id() + "].relations");

            addRuntimeSignals(state, source, system.deployment().serviceNames(), "service-name", "systems.yml", system.id(), "deployment.serviceNames");
            addRuntimeSignals(state, source, system.deployment().applicationNames(), "application-name", "systems.yml", system.id(), "deployment.applicationNames");
            addRuntimeSignals(state, source, system.deployment().containerNames(), "container-name", "systems.yml", system.id(), "deployment.containerNames");
            addRuntimeSignals(state, source, system.deployment().deploymentNames(), "deployment-name", "systems.yml", system.id(), "deployment.deploymentNames");

            dependencyRelations(state, source, system.values("dependencies.upstream"), "depends-on-upstream-system", "systems.yml", system.id(), "dependencies.upstream");
            dependencyRelations(state, source, system.values("dependencies.downstream"), "depends-on-downstream-system", "systems.yml", system.id(), "dependencies.downstream");
            dependencyRelations(state, source, system.values("dependencies.platformServices"), "uses-platform-service", "systems.yml", system.id(), "dependencies.platformServices");
        }
    }

    private void extractRepositoryRelations(BuildState state, List<OperationalContextRepository> repositories) {
        for (var repository : repositories) {
            var source = new EntityKey(REPOSITORY, repository.id());
            references(state, source, repository.references(), "repo-map.yml", REPOSITORY, repository.id(), "$.repositories[id=" + repository.id() + "].references", true);
            responsibilities(state, source, repository.responsibilities(), "repo-map.yml", REPOSITORY, repository.id(), "$.repositories[id=" + repository.id() + "].responsibilities");
            explicitRelations(state, source, repository.relations(), "repo-map.yml", repository.id(), "$.repositories[id=" + repository.id() + "].relations");

            for (var module : repository.modules()) {
                var moduleId = moduleId(repository, module);
                relation(
                        state,
                        source,
                        "contains-module",
                        new EntityKey(MODULE, moduleId),
                        module.moduleType(),
                        source,
                        true,
                        false,
                        "direct-yaml",
                        "high",
                        sourceRef("repo-map.yml", REPOSITORY, repository.id(), "$.repositories[id=" + repository.id() + "].modules[id=" + moduleId + "]", "contains-module")
                );
                moduleReferences(state, repository, module, moduleId);
            }
        }
    }

    private void extractCodeSearchScopeRelations(
            BuildState state,
            List<OperationalContextRepositorySearchScope> scopes,
            List<OperationalContextRepository> repositories
    ) {
        var repositoriesById = repositoriesById(repositories);
        for (var scope : scopes) {
            var source = new EntityKey(CODE_SEARCH_SCOPE, scope.id());
            referenceList(state, source, "targets-system", SYSTEM, scope.target().systems(), "repo-map.yml", scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].target.systems", true);
            referenceList(state, source, "targets-process", PROCESS, scope.target().processes(), "repo-map.yml", scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].target.processes", true);
            referenceList(state, source, "targets-bounded-context", BOUNDED_CONTEXT, scope.target().boundedContexts(), "repo-map.yml", scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].target.boundedContexts", true);
            referenceList(state, source, "targets-integration", INTEGRATION, scope.target().integrations(), "repo-map.yml", scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].target.integrations", true);
            referenceList(state, source, "targets-term", TERM, scope.target().terms(), "repo-map.yml", scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].target.terms", true);

            var derivedTargets = new LinkedHashSet<String>();
            for (var repository : scope.repositories()) {
                codeSearchRepository(state, source, scope, repository);
                codeSearchRepositoryTargets(state, source, scope, repository, repositoriesById, derivedTargets);
            }
        }
    }

    private void extractProcessRelations(BuildState state, List<OperationalContextProcess> processes) {
        for (var process : processes) {
            var source = new EntityKey(PROCESS, process.id());
            referenceList(state, source, "primary-system", SYSTEM, process.participants().primarySystems(), "processes.yml", process.id(), "$.processes[id=" + process.id() + "].participants.primarySystems", true);
            referenceList(state, source, "supporting-system", SYSTEM, process.participants().supportingSystems(), "processes.yml", process.id(), "$.processes[id=" + process.id() + "].participants.supportingSystems", true);
            referenceList(state, source, "external-system", SYSTEM, process.participants().externalSystems(), "processes.yml", process.id(), "$.processes[id=" + process.id() + "].participants.externalSystems", true);
            referenceList(state, source, "platform-component", SYSTEM, process.participants().platformComponents(), "processes.yml", process.id(), "$.processes[id=" + process.id() + "].participants.platformComponents", true);

            references(state, source, process.references(), "processes.yml", PROCESS, process.id(), "$.processes[id=" + process.id() + "].references", false);
            responsibilities(state, source, process.responsibilities(), "processes.yml", PROCESS, process.id(), "$.processes[id=" + process.id() + "].responsibilities");
            explicitRelations(state, source, process.relations(), "processes.yml", process.id(), "$.processes[id=" + process.id() + "].relations");

            for (var step : process.steps()) {
                var stepKey = new EntityKey(PROCESS_STEP, process.id() + "/" + step.id());
                relation(
                        state,
                        source,
                        "has-step",
                        stepKey,
                        step.type(),
                        source,
                        true,
                        false,
                        "direct-yaml",
                        "high",
                        sourceRef("processes.yml", PROCESS, process.id(), "$.processes[id=" + process.id() + "].steps[id=" + step.id() + "]", "has-step")
                );
                references(state, stepKey, step.references(), "processes.yml", PROCESS, process.id(), "$.processes[id=" + process.id() + "].steps[id=" + step.id() + "].references", false);
            }
        }
    }

    private void extractIntegrationRelations(BuildState state, List<OperationalContextIntegration> integrations) {
        for (var integration : integrations) {
            var source = new EntityKey(INTEGRATION, integration.id());
            integrationParticipant(state, source, integration, integration.participants().source(), "source", "source-system");
            integration.participants().targets().forEach(participant -> integrationParticipant(state, source, integration, participant, "targets", "target-system"));
            integration.participants().intermediaries().forEach(participant -> integrationParticipant(state, source, integration, participant, "intermediaries", "intermediary-system"));
            integration.participants().finalTargets().forEach(participant -> integrationParticipant(state, source, integration, participant, "finalTargets", "final-target-system"));

            references(state, source, integration.references(), "integrations.yml", INTEGRATION, integration.id(), "$.integrations[id=" + integration.id() + "].references", false);
            responsibilities(state, source, integration.responsibilities(), "integrations.yml", INTEGRATION, integration.id(), "$.integrations[id=" + integration.id() + "].responsibilities");
            explicitRelations(state, source, integration.relations(), "integrations.yml", integration.id(), "$.integrations[id=" + integration.id() + "].relations");
        }
    }

    private void extractBoundedContextRelations(BuildState state, List<OperationalContextBoundedContext> boundedContexts) {
        for (var context : boundedContexts) {
            var source = new EntityKey(BOUNDED_CONTEXT, context.id());
            references(state, source, context.references(), "bounded-contexts.yml", BOUNDED_CONTEXT, context.id(), "$.boundedContexts[id=" + context.id() + "].references", true);
            responsibilities(state, source, context.responsibilities(), "bounded-contexts.yml", BOUNDED_CONTEXT, context.id(), "$.boundedContexts[id=" + context.id() + "].responsibilities");
            explicitRelations(state, source, context.relations(), "bounded-contexts.yml", context.id(), "$.boundedContexts[id=" + context.id() + "].relations");
        }
    }

    private void extractTeamRelations(BuildState state, List<OperationalContextTeam> teams) {
        for (var team : teams) {
            var source = new EntityKey(TEAM, team.id());
            references(state, source, team.references(), "teams.yml", TEAM, team.id(), "$.teams[id=" + team.id() + "].references", true);
            responsibilities(state, source, team.responsibilities(), "teams.yml", TEAM, team.id(), "$.teams[id=" + team.id() + "].responsibilities");
            explicitRelations(state, source, team.relations(), "teams.yml", team.id(), "$.teams[id=" + team.id() + "].relations");
        }
    }

    private void extractGlossaryRelations(BuildState state, List<OperationalContextGlossaryTerm> terms) {
        for (var term : terms) {
            var source = new EntityKey(TERM, term.id());
            for (var reference : safeTextList(term.canonicalReferences())) {
                glossaryReference(state, source, reference, term.id());
            }
        }
    }

    private void extractHandoffRuleRelations(BuildState state, List<OperationalContextHandoffRule> rules) {
        for (var rule : rules) {
            var source = new EntityKey(HANDOFF_RULE, rule.id());
            markdownReferences(
                    state,
                    source,
                    rule.references(),
                    "handoff-rules.md",
                    HANDOFF_RULE,
                    rule.id(),
                    "$.handoffRules[id=" + rule.id() + "].operationalContextLinks",
                    true
            );
        }
    }

    private void glossaryReference(
            BuildState state,
            EntityKey source,
            String reference,
            String termId
    ) {
        if (isNone(reference)) {
            return;
        }

        var typedReference = typedReference(reference);
        if (typedReference != null) {
            relation(
                    state,
                    source,
                    "canonical-reference",
                    new EntityKey(typedReference.type(), typedReference.id()),
                    null,
                    source,
                    true,
                    false,
                    "direct-markdown",
                    "medium",
                    sourceRef("glossary.md", TERM, termId, "$.terms[id=" + termId + "].canonicalReferences", "canonical-reference")
            );
            return;
        }

        var termReference = new EntityKey(TERM, reference);
        if (!state.entities.containsKey(termReference)) {
            return;
        }

        relation(
                state,
                source,
                "canonical-reference",
                termReference,
                null,
                source,
                true,
                false,
                "direct-markdown",
                "medium",
                sourceRef("glossary.md", TERM, termId, "$.terms[id=" + termId + "].canonicalReferences", "canonical-reference")
        );
    }

    private void registerEntry(BuildState state, String type, OperationalContextEntry entry) {
        register(state, type, entry.id(), entry.label(), lifecycleStatus(entry), entry.summary());
    }

    private void registerModule(BuildState state, OperationalContextRepository repository, OperationalContextRepositoryModule module) {
        var moduleId = moduleId(repository, module);
        register(state, MODULE, moduleId, firstNonBlank(module.name(), module.effectiveId(), moduleId), module.lifecycleStatus(), null);
    }

    private void registerProcessStep(BuildState state, OperationalContextProcess process, OperationalContextProcessStep step) {
        register(state, PROCESS_STEP, process.id() + "/" + step.id(), firstNonBlank(step.name(), step.id()), null, step.summary());
    }

    private void register(BuildState state, String type, String id, String label, String lifecycleStatus, String summary) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        var ref = new EntityRef(type, id, firstNonBlank(label, id), lifecycleStatus, summary);
        state.entities.putIfAbsent(ref.key(), ref);
    }

    private void references(
            BuildState state,
            EntityKey source,
            OperationalContextReferences references,
            String file,
            String sourceRefEntityType,
            String sourceEntityId,
            String basePath,
            boolean canonical
    ) {
        var safeReferences = references != null ? references : OperationalContextReferences.empty();
        referenceList(state, source, "references-system", SYSTEM, safeReferences.systems(), file, sourceRefEntityType, sourceEntityId, basePath + ".systems", canonical);
        referenceList(state, source, "references-repository", REPOSITORY, safeReferences.repositories(), file, sourceRefEntityType, sourceEntityId, basePath + ".repositories", canonical);
        referenceList(state, source, "references-module", MODULE, safeReferences.modules(), file, sourceRefEntityType, sourceEntityId, basePath + ".modules", canonical);
        referenceList(state, source, "references-process", PROCESS, safeReferences.processes(), file, sourceRefEntityType, sourceEntityId, basePath + ".processes", canonical);
        referenceList(state, source, "references-bounded-context", BOUNDED_CONTEXT, safeReferences.boundedContexts(), file, sourceRefEntityType, sourceEntityId, basePath + ".boundedContexts", canonical);
        referenceList(state, source, "references-integration", INTEGRATION, safeReferences.integrations(), file, sourceRefEntityType, sourceEntityId, basePath + ".integrations", canonical);
        referenceList(state, source, "references-term", TERM, safeReferences.terms(), file, sourceRefEntityType, sourceEntityId, basePath + ".terms", canonical);
        referenceList(state, source, "references-team", TEAM, safeReferences.teams(), file, sourceRefEntityType, sourceEntityId, basePath + ".teams", canonical);
        referenceList(state, source, "references-external-party", EXTERNAL_PARTY, safeReferences.externalParties(), file, sourceRefEntityType, sourceEntityId, basePath + ".externalParties", canonical);
        referenceList(state, source, "references-datastore", DATASTORE, safeReferences.dataStores(), file, sourceRefEntityType, sourceEntityId, basePath + ".dataStores", canonical);
        referenceList(state, source, "references-handoff-rule", HANDOFF_RULE, safeReferences.handoffRules(), file, sourceRefEntityType, sourceEntityId, basePath + ".handoffRules", canonical);
    }

    private void markdownReferences(
            BuildState state,
            EntityKey source,
            OperationalContextReferences references,
            String file,
            String sourceRefEntityType,
            String sourceEntityId,
            String basePath,
            boolean canonical
    ) {
        var safeReferences = references != null ? references : OperationalContextReferences.empty();
        markdownReferenceList(state, source, "references-system", SYSTEM, safeReferences.systems(), file, sourceRefEntityType, sourceEntityId, basePath + ".systems", canonical);
        markdownReferenceList(state, source, "references-repository", REPOSITORY, safeReferences.repositories(), file, sourceRefEntityType, sourceEntityId, basePath + ".repositories", canonical);
        markdownReferenceList(state, source, "references-module", MODULE, safeReferences.modules(), file, sourceRefEntityType, sourceEntityId, basePath + ".modules", canonical);
        markdownReferenceList(state, source, "references-process", PROCESS, safeReferences.processes(), file, sourceRefEntityType, sourceEntityId, basePath + ".processes", canonical);
        markdownReferenceList(state, source, "references-bounded-context", BOUNDED_CONTEXT, safeReferences.boundedContexts(), file, sourceRefEntityType, sourceEntityId, basePath + ".boundedContexts", canonical);
        markdownReferenceList(state, source, "references-integration", INTEGRATION, safeReferences.integrations(), file, sourceRefEntityType, sourceEntityId, basePath + ".integrations", canonical);
        markdownReferenceList(state, source, "references-term", TERM, safeReferences.terms(), file, sourceRefEntityType, sourceEntityId, basePath + ".terms", canonical);
        markdownReferenceList(state, source, "references-team", TEAM, safeReferences.teams(), file, sourceRefEntityType, sourceEntityId, basePath + ".teams", canonical);
        markdownReferenceList(state, source, "references-external-party", EXTERNAL_PARTY, safeReferences.externalParties(), file, sourceRefEntityType, sourceEntityId, basePath + ".externalParties", canonical);
        markdownReferenceList(state, source, "references-datastore", DATASTORE, safeReferences.dataStores(), file, sourceRefEntityType, sourceEntityId, basePath + ".dataStores", canonical);
        markdownReferenceList(state, source, "references-handoff-rule", HANDOFF_RULE, safeReferences.handoffRules(), file, sourceRefEntityType, sourceEntityId, basePath + ".handoffRules", canonical);
    }

    private void referenceList(
            BuildState state,
            EntityKey source,
            String relationType,
            String targetType,
            List<String> targetIds,
            String file,
            String sourceEntityId,
            String fieldPath,
            boolean canonical
    ) {
        referenceList(state, source, relationType, targetType, targetIds, file, source.type(), sourceEntityId, fieldPath, canonical);
    }

    private void referenceList(
            BuildState state,
            EntityKey source,
            String relationType,
            String targetType,
            List<String> targetIds,
            String file,
            String sourceRefEntityType,
            String sourceEntityId,
            String fieldPath,
            boolean canonical
    ) {
        for (var targetId : safeTextList(targetIds)) {
            relation(
                    state,
                    source,
                    relationType,
                    new EntityKey(targetType, targetId),
                    null,
                    source,
                    canonical,
                    !canonical,
                    canonical ? "direct-yaml" : "supporting-yaml-reference",
                    canonical ? "high" : "medium",
                    sourceRef(file, sourceRefEntityType, sourceEntityId, fieldPath, relationType)
            );
        }
    }

    private void markdownReferenceList(
            BuildState state,
            EntityKey source,
            String relationType,
            String targetType,
            List<String> targetIds,
            String file,
            String sourceRefEntityType,
            String sourceEntityId,
            String fieldPath,
            boolean canonical
    ) {
        for (var targetId : safeTextList(targetIds)) {
            relation(
                    state,
                    source,
                    relationType,
                    new EntityKey(targetType, targetId),
                    null,
                    source,
                    canonical,
                    !canonical,
                    canonical ? "direct-markdown" : "supporting-markdown-reference",
                    canonical ? "high" : "medium",
                    sourceRef(file, sourceRefEntityType, sourceEntityId, fieldPath, relationType)
            );
        }
    }

    private void responsibilities(
            BuildState state,
            EntityKey source,
            List<OperationalContextDtos.OperationalContextResponsibility> responsibilities,
            String file,
            String sourceEntityId,
            String fieldPath
    ) {
        responsibilities(state, source, responsibilities, file, source.type(), sourceEntityId, fieldPath);
    }

    private void responsibilities(
            BuildState state,
            EntityKey source,
            List<OperationalContextDtos.OperationalContextResponsibility> responsibilities,
            String file,
            String sourceRefEntityType,
            String sourceEntityId,
            String fieldPath
    ) {
        for (var responsibility : responsibilities) {
            if (StringUtils.hasText(responsibility.teamId())) {
                relation(
                        state,
                        source,
                        "responsible-team",
                        new EntityKey(TEAM, responsibility.teamId()),
                        responsibility.role(),
                        source,
                        true,
                        false,
                        "direct-yaml",
                        firstNonBlank(responsibility.confidence(), "medium"),
                        sourceRef(file, sourceRefEntityType, sourceEntityId, fieldPath, "responsible-team")
                );
            }

            if (StringUtils.hasText(responsibility.targetType()) && StringUtils.hasText(responsibility.targetId())) {
                relation(
                        state,
                        source,
                        "responsible-for",
                        new EntityKey(normalizeTargetType(responsibility.targetType()), responsibility.targetId()),
                        responsibility.role(),
                        source,
                        true,
                        false,
                        "direct-yaml",
                        firstNonBlank(responsibility.confidence(), "medium"),
                        sourceRef(file, sourceRefEntityType, sourceEntityId, fieldPath, "responsible-for")
                );
            }
        }
    }

    private void explicitRelations(
            BuildState state,
            EntityKey source,
            List<OperationalContextRelation> relations,
            String file,
            String sourceEntityId,
            String fieldPath
    ) {
        for (var relation : relations) {
            var targetId = firstNonBlank(relation.targetContextId(), relation.target());
            if (!StringUtils.hasText(targetId)) {
                continue;
            }
            var targetType = firstNonBlank(relation.targetType(), relation.targetContextId() != null ? BOUNDED_CONTEXT : null, SYSTEM);
            relation(
                    state,
                    source,
                    firstNonBlank(relation.type(), "related-to"),
                    new EntityKey(normalizeTargetType(targetType), targetId),
                    null,
                    source,
                    true,
                    false,
                    "direct-yaml",
                    "medium",
                    sourceRef(file, source.type(), sourceEntityId, fieldPath, "explicit-relation")
            );
        }
    }

    private void moduleReferences(
            BuildState state,
            OperationalContextRepository repository,
            OperationalContextRepositoryModule module,
            String moduleId
    ) {
        var source = new EntityKey(MODULE, moduleId);
        references(
                state,
                source,
                module.references(),
                "repo-map.yml",
                REPOSITORY,
                repository.id(),
                "$.repositories[id=" + repository.id() + "].modules[id=" + moduleId + "].references",
                true
        );
        responsibilities(
                state,
                source,
                module.responsibilities(),
                "repo-map.yml",
                REPOSITORY,
                repository.id(),
                "$.repositories[id=" + repository.id() + "].modules[id=" + moduleId + "].responsibilities"
        );
    }

    private void codeSearchRepository(
            BuildState state,
            EntityKey source,
            OperationalContextRepositorySearchScope scope,
            OperationalContextRepositorySearchRepository repository
    ) {
        if (!repository.include() || !StringUtils.hasText(repository.repoId())) {
            return;
        }
        relation(
                state,
                source,
                "includes-repository",
                new EntityKey(REPOSITORY, repository.repoId()),
                firstNonBlank(repository.role(), "included"),
                source,
                true,
                false,
                "direct-yaml",
                "high",
                sourceRef("repo-map.yml", CODE_SEARCH_SCOPE, scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].repositories[repoId=" + repository.repoId() + "]", "includes-repository")
        );
        for (var moduleId : safeTextList(repository.moduleIds())) {
            relation(
                    state,
                    source,
                    "includes-module",
                    new EntityKey(MODULE, moduleId),
                    repository.role(),
                    source,
                    true,
                    false,
                    "direct-yaml",
                    "high",
                    sourceRef("repo-map.yml", CODE_SEARCH_SCOPE, scope.id(), "$.codeSearchScopes[id=" + scope.id() + "].repositories[repoId=" + repository.repoId() + "].moduleIds", "includes-module")
            );
        }
    }

    private void codeSearchRepositoryTargets(
            BuildState state,
            EntityKey source,
            OperationalContextRepositorySearchScope scope,
            OperationalContextRepositorySearchRepository scopeRepository,
            Map<String, OperationalContextRepository> repositoriesById,
            LinkedHashSet<String> derivedTargets
    ) {
        if (!scopeRepository.include() || !StringUtils.hasText(scopeRepository.repoId())) {
            return;
        }
        var repository = repositoriesById.get(scopeRepository.repoId());
        if (repository == null) {
            return;
        }
        var includeRef = sourceRef(
                "repo-map.yml",
                CODE_SEARCH_SCOPE,
                scope.id(),
                "$.codeSearchScopes[id=" + scope.id() + "].repositories[repoId=" + scopeRepository.repoId() + "]",
                "includes-repository"
        );
        repositoryReferenceTargets(
                state,
                source,
                repository,
                repository.references().systems(),
                "targets-system",
                SYSTEM,
                "systems",
                "references-system",
                includeRef,
                derivedTargets
        );
        repositoryReferenceTargets(
                state,
                source,
                repository,
                repository.references().processes(),
                "targets-process",
                PROCESS,
                "processes",
                "references-process",
                includeRef,
                derivedTargets
        );
        repositoryReferenceTargets(
                state,
                source,
                repository,
                repository.references().boundedContexts(),
                "targets-bounded-context",
                BOUNDED_CONTEXT,
                "boundedContexts",
                "references-bounded-context",
                includeRef,
                derivedTargets
        );
        repositoryReferenceTargets(
                state,
                source,
                repository,
                repository.references().integrations(),
                "targets-integration",
                INTEGRATION,
                "integrations",
                "references-integration",
                includeRef,
                derivedTargets
        );
        repositoryReferenceTargets(
                state,
                source,
                repository,
                repository.references().terms(),
                "targets-term",
                TERM,
                "terms",
                "references-term",
                includeRef,
                derivedTargets
        );
    }

    private void repositoryReferenceTargets(
            BuildState state,
            EntityKey source,
            OperationalContextRepository repository,
            List<String> targetIds,
            String relationType,
            String targetType,
            String referenceField,
            String referenceRole,
            SourceRef includeRef,
            LinkedHashSet<String> derivedTargets
    ) {
        for (var targetId : safeTextList(targetIds)) {
            if (!derivedTargets.add(relationType + "|" + targetType + "|" + targetId)) {
                continue;
            }
            relation(
                    state,
                    source,
                    relationType,
                    new EntityKey(targetType, targetId),
                    null,
                    source,
                    false,
                    true,
                    "derived-from-included-repository-reference",
                    "medium",
                    List.of(
                            includeRef,
                            sourceRef(
                                    "repo-map.yml",
                                    REPOSITORY,
                                    repository.id(),
                                    "$.repositories[id=" + repository.id() + "].references." + referenceField,
                                    referenceRole
                            )
                    )
            );
        }
    }

    private void integrationParticipant(
            BuildState state,
            EntityKey source,
            OperationalContextIntegration integration,
            OperationalContextIntegrationParticipant participant,
            String participantPath,
            String relationType
    ) {
        if (participant == null || !StringUtils.hasText(participant.system())) {
            return;
        }
        relation(
                state,
                source,
                relationType,
                new EntityKey(SYSTEM, participant.system()),
                firstNonBlank(participant.role(), participantPath),
                source,
                true,
                false,
                "direct-yaml",
                "high",
                sourceRef("integrations.yml", INTEGRATION, integration.id(), "$.integrations[id=" + integration.id() + "].participants." + participantPath, relationType)
        );
        if (StringUtils.hasText(participant.boundedContext())) {
            relation(
                    state,
                    source,
                    "participant-bounded-context",
                    new EntityKey(BOUNDED_CONTEXT, participant.boundedContext()),
                    firstNonBlank(participant.role(), participantPath),
                    source,
                    true,
                    false,
                    "direct-yaml",
                    "high",
                    sourceRef("integrations.yml", INTEGRATION, integration.id(), "$.integrations[id=" + integration.id() + "].participants." + participantPath + ".boundedContext", "participant-bounded-context")
            );
        }
        referenceList(state, source, "participant-repository", REPOSITORY, participant.repositories(), "integrations.yml", integration.id(), "$.integrations[id=" + integration.id() + "].participants." + participantPath + ".repositories", true);
        referenceList(state, source, "participant-module", MODULE, participant.modules(), "integrations.yml", integration.id(), "$.integrations[id=" + integration.id() + "].participants." + participantPath + ".modules", true);
    }

    private void addRuntimeSignals(
            BuildState state,
            EntityKey source,
            List<String> signals,
            String role,
            String file,
            String sourceEntityId,
            String fieldPath
    ) {
        for (var signal : safeTextList(signals)) {
            var signalId = source.id() + "/" + role + "/" + signal;
            register(state, RUNTIME_SIGNAL, signalId, signal, null, role);
            relation(
                    state,
                    source,
                    "has-runtime-signal",
                    new EntityKey(RUNTIME_SIGNAL, signalId),
                    role,
                    source,
                    true,
                    false,
                    "direct-yaml",
                    "high",
                    sourceRef(file, SYSTEM, sourceEntityId, "$.systems[id=" + sourceEntityId + "]." + fieldPath, "has-runtime-signal")
            );
        }
    }

    private void dependencyRelations(
            BuildState state,
            EntityKey source,
            List<String> targetIds,
            String relationType,
            String file,
            String sourceEntityId,
            String fieldPath
    ) {
        for (var target : safeTextList(targetIds)) {
            relation(
                    state,
                    source,
                    relationType,
                    new EntityKey(SYSTEM, target),
                    null,
                    source,
                    true,
                    false,
                    "direct-yaml",
                    "medium",
                    sourceRef(file, SYSTEM, sourceEntityId, "$.systems[id=" + sourceEntityId + "]." + fieldPath, relationType)
            );
        }
    }

    private void relation(
            BuildState state,
            EntityKey source,
            String relationType,
            EntityKey target,
            String role,
            EntityKey canonicalOwner,
            boolean canonical,
            boolean derived,
            String derivation,
            String confidence,
            SourceRef sourceRef
    ) {
        relation(
                state,
                source,
                relationType,
                target,
                role,
                canonicalOwner,
                canonical,
                derived,
                derivation,
                confidence,
                List.of(sourceRef)
        );
    }

    private void relation(
            BuildState state,
            EntityKey source,
            String relationType,
            EntityKey target,
            String role,
            EntityKey canonicalOwner,
            boolean canonical,
            boolean derived,
            String derivation,
            String confidence,
            List<SourceRef> sourceRefs
    ) {
        if (source.equals(target)) {
            state.findings.add(new ValidationFinding(
                    "error",
                    "SELF_REFERENCE",
                    "Relation " + relationType + " points " + source.value() + " to itself.",
                    sourceRefs
            ));
            return;
        }

        if (!state.entities.containsKey(target)) {
            state.findings.add(new ValidationFinding(
                    "error",
                    "UNKNOWN_RELATION_TARGET",
                    "Relation " + relationType + " points to unknown target " + target.value() + ".",
                    sourceRefs
            ));
        }

        var key = relationKey(source, relationType, target, role, canonicalOwner, derived);
        state.drafts.compute(key, (ignored, draft) -> {
            if (draft == null) {
                return new RelationDraft(
                        relationType,
                        source,
                        target,
                        role,
                        canonicalOwner,
                        canonical,
                        derived,
                        derivation,
                        confidence,
                        new ArrayList<>(sourceRefs),
                        new ArrayList<>()
                );
            }
            draft.sourceRefs().addAll(sourceRefs);
            draft.warnings().add("Duplicate relation merged into one read-model edge.");
            return draft;
        });
    }

    private SourceRef sourceRef(String file, String entityType, String entityId, String fieldPath, String role) {
        return new SourceRef("src/main/resources/operational-context/" + file, entityType, entityId, fieldPath, role);
    }

    private Map<String, OperationalContextRepository> repositoriesById(List<OperationalContextRepository> repositories) {
        var result = new LinkedHashMap<String, OperationalContextRepository>();
        for (var repository : repositories) {
            if (StringUtils.hasText(repository.id())) {
                result.putIfAbsent(repository.id(), repository);
            }
        }
        return result;
    }

    private String moduleId(OperationalContextRepository repository, OperationalContextRepositoryModule module) {
        return firstNonBlank(module.effectiveId(), module.id(), module.moduleId(), repository.id() + "/module");
    }

    private String lifecycleStatus(OperationalContextEntry entry) {
        if (entry instanceof OperationalContextSystem system) {
            return system.lifecycleStatus();
        }
        if (entry instanceof OperationalContextRepository repository) {
            return repository.lifecycleStatus();
        }
        if (entry instanceof OperationalContextProcess process) {
            return process.lifecycleStatus();
        }
        if (entry instanceof OperationalContextIntegration integration) {
            return integration.lifecycleStatus();
        }
        if (entry instanceof OperationalContextBoundedContext context) {
            return context.lifecycleStatus();
        }
        if (entry instanceof OperationalContextTeam team) {
            return team.lifecycleStatus();
        }
        return null;
    }

    private String normalizeTargetType(String targetType) {
        var normalized = targetType.trim()
                .replace("_", "-")
                .replace(" ", "-");
        return switch (normalized) {
            case "systems" -> SYSTEM;
            case "repositories" -> REPOSITORY;
            case "code-search-scopes", "codeSearchScope", "codeSearchScopes" -> CODE_SEARCH_SCOPE;
            case "processes" -> PROCESS;
            case "integrations" -> INTEGRATION;
            case "boundedContext", "boundedContexts", "bounded-contexts" -> BOUNDED_CONTEXT;
            case "teams" -> TEAM;
            case "terms", "glossary-term", "glossary-terms" -> TERM;
            case "handoffRules", "handoff-rules" -> HANDOFF_RULE;
            case "modules" -> MODULE;
            case "dataStores", "data-stores" -> DATASTORE;
            case "externalParties", "external-parties" -> EXTERNAL_PARTY;
            default -> normalized;
        };
    }

    private TypedReference typedReference(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var normalized = value.trim()
                .replace("`", "");
        var separator = normalized.indexOf(':');
        if (separator < 1) {
            return null;
        }

        var type = typedReferenceTargetType(normalized.substring(0, separator));
        if (!StringUtils.hasText(type)) {
            return null;
        }

        var id = normalized.substring(separator + 1).trim()
                .replaceFirst("\\s+.*$", "")
                .replaceAll("[,.;]+$", "");
        if (!StringUtils.hasText(id)) {
            return null;
        }

        return new TypedReference(type, id);
    }

    private String typedReferenceTargetType(String targetType) {
        var normalized = targetType.trim()
                .replace("_", "-")
                .replace(" ", "-");
        return switch (normalized) {
            case "system", "systems" -> SYSTEM;
            case "repository", "repositories", "repo", "repos" -> REPOSITORY;
            case "code-search-scope", "code-search-scopes", "codeSearchScope", "codeSearchScopes" -> CODE_SEARCH_SCOPE;
            case "process", "processes" -> PROCESS;
            case "integration", "integrations" -> INTEGRATION;
            case "bounded-context", "bounded-contexts", "boundedContext", "boundedContexts" -> BOUNDED_CONTEXT;
            case "team", "teams" -> TEAM;
            case "term", "terms", "glossary-term", "glossary-terms" -> TERM;
            case "handoff-rule", "handoff-rules", "handoffRule", "handoffRules" -> HANDOFF_RULE;
            case "module", "modules" -> MODULE;
            case "datastore", "datastores", "data-store", "data-stores" -> DATASTORE;
            case "external-party", "external-parties", "externalParty", "externalParties" -> EXTERNAL_PARTY;
            default -> null;
        };
    }

    private boolean isNone(String value) {
        return !StringUtils.hasText(value)
                || "none".equalsIgnoreCase(value.trim())
                || "null".equalsIgnoreCase(value.trim());
    }

    private String relationKey(
            EntityKey source,
            String relationType,
            EntityKey target,
            String role,
            EntityKey canonicalOwner,
            boolean derived
    ) {
        return source.value()
                + "|" + relationType
                + "|" + target.value()
                + "|" + firstNonBlank(role, "")
                + "|" + canonicalOwner.value()
                + "|" + derived;
    }

    private List<String> safeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
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

    private static final class BuildState {

        private final Map<EntityKey, EntityRef> entities = new LinkedHashMap<>();
        private final Map<String, RelationDraft> drafts = new LinkedHashMap<>();
        private final List<ValidationFinding> findings = new ArrayList<>();

        private OperationalContextRelationIndex toIndex() {
            var relations = drafts.values().stream()
                    .map(RelationDraft::toRelation)
                    .toList();
            var outgoing = new LinkedHashMap<EntityKey, List<ReadModelRelation>>();
            var incoming = new LinkedHashMap<EntityKey, List<ReadModelRelation>>();
            var neighborKeys = new LinkedHashMap<EntityKey, LinkedHashSet<EntityKey>>();

            for (var relation : relations) {
                outgoing.computeIfAbsent(relation.source(), ignored -> new ArrayList<>()).add(relation);
                incoming.computeIfAbsent(relation.target(), ignored -> new ArrayList<>()).add(relation);
                neighborKeys.computeIfAbsent(relation.source(), ignored -> new LinkedHashSet<>()).add(relation.target());
                neighborKeys.computeIfAbsent(relation.target(), ignored -> new LinkedHashSet<>()).add(relation.source());
            }

            var neighbors = new LinkedHashMap<EntityKey, List<EntityRef>>();
            neighborKeys.forEach((key, values) -> neighbors.put(
                    key,
                    values.stream()
                            .map(value -> entities.getOrDefault(value, EntityRef.fromKey(value)))
                            .toList()
            ));

            return new OperationalContextRelationIndex(
                    entities,
                    relations,
                    outgoing,
                    incoming,
                    neighbors,
                    findings
            );
        }
    }

    private record RelationDraft(
            String relationType,
            EntityKey source,
            EntityKey target,
            String role,
            EntityKey canonicalOwner,
            boolean canonical,
            boolean derived,
            String derivation,
            String confidence,
            List<SourceRef> sourceRefs,
            List<String> warnings
    ) {

        ReadModelRelation toRelation() {
            return new ReadModelRelation(
                    relationType,
                    "outbound",
                    source,
                    target,
                    role,
                    canonicalOwner,
                    derived,
                    new Provenance(
                            canonical,
                            derivation,
                            confidence,
                            OperationalContextRelationIndex.distinct(sourceRefs),
                            OperationalContextRelationIndex.distinct(warnings)
                    )
            );
        }
    }

    private record TypedReference(
            String type,
            String id
    ) {
    }
}
