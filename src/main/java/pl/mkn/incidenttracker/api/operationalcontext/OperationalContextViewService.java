package pl.mkn.incidenttracker.api.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownGroupDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownItemDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplainableValueDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplanationReasonDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OpenQuestionDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextBoundedContextRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextCodeSearchScopeRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextDetailSectionDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextExplainabilitySectionDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextGlossaryRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextHandoffRuleRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextIntegrationRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProcessRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextRepositoryRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSearchResultDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextTeamRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.SourceReferenceDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ValidationFindingDto;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextMatchSignals;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcessStep;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRelation;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositoryModule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchDatabaseHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchStrategy;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchWorkflowHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSignalSet;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperationalContextViewService {

    private static final String SYSTEM = "system";
    private static final String REPOSITORY = "repository";
    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String PROCESS = "process";
    private static final String INTEGRATION = "integration";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String TEAM = "team";
    private static final String GLOSSARY_TERM = "glossary-term";
    private static final String HANDOFF_RULE = "handoff-rule";

    private static final Map<String, String> SOURCE_FILES = Map.of(
            SYSTEM, "systems.yml",
            REPOSITORY, "repo-map.yml",
            CODE_SEARCH_SCOPE, "repo-map.yml",
            PROCESS, "processes.yml",
            INTEGRATION, "integrations.yml",
            BOUNDED_CONTEXT, "bounded-contexts.yml",
            TEAM, "teams.yml",
            GLOSSARY_TERM, "glossary.md",
            HANDOFF_RULE, "handoff-rules.md"
    );

    private static final Set<String> GENERIC_MARKERS = Set.of(
            "error", "warn", "warning", "info", "debug", "todo", "unknown", "service", "api"
    );

    private final OperationalContextPort operationalContextPort;

    public OperationalContextSummaryDto summary() {
        var view = view();
        var counts = validationCounts(view.validationFindings());
        var status = catalogStatus(view.catalog(), view.validationFindings());
        var healthCards = List.of(
                countCard("Systems", view.catalog().systems().size(), SYSTEM, view.catalog().systems()),
                countCard("Repositories", view.catalog().repositories().size(), REPOSITORY, view.catalog().repositories()),
                codeSearchScopeCountCard(view.catalog().codeSearchScopes()),
                countCard("Processes", view.catalog().processes().size(), PROCESS, view.catalog().processes()),
                countCard("Integrations", view.catalog().integrations().size(), INTEGRATION, view.catalog().integrations()),
                countCard("Bounded Contexts", view.catalog().boundedContexts().size(), BOUNDED_CONTEXT, view.catalog().boundedContexts()),
                countCard("Teams", view.catalog().teams().size(), TEAM, view.catalog().teams()),
                countCard("Open Questions", view.openQuestions().size(), "open-question", view.openQuestions()),
                validationCard(view.validationFindings())
        );

        return new OperationalContextSummaryDto(
                view.catalog().systems().size(),
                view.catalog().repositories().size(),
                view.catalog().codeSearchScopes().size(),
                view.catalog().processes().size(),
                view.catalog().integrations().size(),
                view.catalog().boundedContexts().size(),
                view.catalog().teams().size(),
                view.catalog().glossaryTerms().size(),
                view.catalog().handoffRules().size(),
                view.openQuestions().size(),
                counts,
                status,
                healthCards
        );
    }

    public List<OperationalContextSystemRowDto> systems() {
        var view = view();
        return view.catalog().systems().stream()
                .map(system -> systemRow(view, system))
                .toList();
    }

    public List<OperationalContextRepositoryRowDto> repositories() {
        var view = view();
        return view.catalog().repositories().stream()
                .map(repository -> repositoryRow(view, repository))
                .toList();
    }

    public List<OperationalContextCodeSearchScopeRowDto> codeSearchScopes() {
        var view = view();
        return view.catalog().codeSearchScopes().stream()
                .map(scope -> codeSearchScopeRow(view, scope))
                .toList();
    }

    public List<OperationalContextProcessRowDto> processes() {
        var view = view();
        return view.catalog().processes().stream()
                .map(process -> processRow(view, process))
                .toList();
    }

    public List<OperationalContextIntegrationRowDto> integrations() {
        var view = view();
        return view.catalog().integrations().stream()
                .map(integration -> integrationRow(view, integration))
                .toList();
    }

    public List<OperationalContextBoundedContextRowDto> boundedContexts() {
        var view = view();
        return view.catalog().boundedContexts().stream()
                .map(context -> boundedContextRow(view, context))
                .toList();
    }

    public List<OperationalContextTeamRowDto> teams() {
        var view = view();
        return view.catalog().teams().stream()
                .map(team -> teamRow(view, team))
                .toList();
    }

    public List<OperationalContextGlossaryRowDto> glossary() {
        var view = view();
        return view.catalog().glossaryTerms().stream()
                .map(term -> new OperationalContextGlossaryRowDto(
                        term.id(),
                        term.term(),
                        term.category(),
                        term.definition(),
                        valueAggregate(
                                "Match signals",
                                GLOSSARY_TERM,
                                term.id(),
                                term.matchSignals(),
                                "Signals listed directly on the glossary term.",
                                "signal"
                        ),
                        valueAggregate(
                                "Canonical references",
                                GLOSSARY_TERM,
                                term.id(),
                                term.canonicalReferences(),
                                "References listed directly on the glossary term.",
                                "reference"
                        )
                ))
                .toList();
    }

    public List<OperationalContextHandoffRuleRowDto> handoffRules() {
        var view = view();
        return view.catalog().handoffRules().stream()
                .map(rule -> new OperationalContextHandoffRuleRowDto(
                        rule.id(),
                        rule.title(),
                        rule.routeTo(),
                        valueAggregate("Use when", HANDOFF_RULE, rule.id(), rule.useWhen(), "Rule conditions from handoff-rules.md.", "condition"),
                        valueAggregate("Required evidence", HANDOFF_RULE, rule.id(), rule.requiredEvidence(), "Evidence checklist from handoff-rules.md.", "evidence"),
                        first(rule.expectedFirstAction()),
                        valueAggregate("Partner teams", HANDOFF_RULE, rule.id(), rule.partnerTeams(), "Partner teams listed by the rule.", TEAM)
                ))
                .toList();
    }

    public List<OpenQuestionDto> openQuestions() {
        return view().openQuestions();
    }

    public List<ValidationFindingDto> validation() {
        return view().validationFindings();
    }

    public List<OperationalContextSearchResultDto> search(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        var view = view();
        var normalizedQuery = normalize(query);
        var results = new ArrayList<OperationalContextSearchResultDto>();
        addSearchResults(results, SYSTEM, view.catalog().systems(), normalizedQuery);
        addSearchResults(results, REPOSITORY, view.catalog().repositories(), normalizedQuery);
        addCodeSearchScopeSearchResults(results, view.catalog().codeSearchScopes(), normalizedQuery);
        addSearchResults(results, PROCESS, view.catalog().processes(), normalizedQuery);
        addSearchResults(results, INTEGRATION, view.catalog().integrations(), normalizedQuery);
        addSearchResults(results, BOUNDED_CONTEXT, view.catalog().boundedContexts(), normalizedQuery);
        addSearchResults(results, TEAM, view.catalog().teams(), normalizedQuery);
        addGlossarySearchResults(results, view.catalog().glossaryTerms(), normalizedQuery);
        addHandoffSearchResults(results, view.catalog().handoffRules(), normalizedQuery);

        return results.stream()
                .sorted(Comparator
                        .comparingInt((OperationalContextSearchResultDto result) -> confidenceRank(result.confidence()))
                        .thenComparing(OperationalContextSearchResultDto::label))
                .toList();
    }

    public OperationalContextEntityDetailDto entity(String type, String id) {
        var view = view();
        return switch (normalizeType(type)) {
            case SYSTEM -> mapEntityDetail(view, SYSTEM, requireEntity(view.systemsById(), SYSTEM, id));
            case REPOSITORY -> mapEntityDetail(view, REPOSITORY, requireEntity(view.repositoriesById(), REPOSITORY, id));
            case CODE_SEARCH_SCOPE -> codeSearchScopeDetail(view, id);
            case PROCESS -> mapEntityDetail(view, PROCESS, requireEntity(view.processesById(), PROCESS, id));
            case INTEGRATION -> mapEntityDetail(view, INTEGRATION, requireEntity(view.integrationsById(), INTEGRATION, id));
            case BOUNDED_CONTEXT -> mapEntityDetail(view, BOUNDED_CONTEXT, requireEntity(view.contextsById(), BOUNDED_CONTEXT, id));
            case TEAM -> mapEntityDetail(view, TEAM, requireEntity(view.teamsById(), TEAM, id));
            case GLOSSARY_TERM -> glossaryDetail(view, id);
            case HANDOFF_RULE -> handoffRuleDetail(view, id);
            default -> throw new OperationalContextEntityNotFoundException(type, id);
        };
    }

    private OperationalContextSystemRowDto systemRow(CatalogView view, OperationalContextSystem system) {
        var id = system.id();
        return new OperationalContextSystemRowDto(
                id,
                name(system),
                systemKind(system),
                ownerValue(view, SYSTEM, system),
                summaryText(system),
                idAggregate("Repositories", SYSTEM, id, REPOSITORY, repositoryIds(system), view.repositoriesById(), "System lists these repositories as its code scope."),
                idAggregate("Processes", SYSTEM, id, PROCESS, processIds(system), view.processesById(), "System is attached to these operational processes."),
                idAggregate("Contexts", SYSTEM, id, BOUNDED_CONTEXT, boundedContextIds(system), view.contextsById(), "System participates in these semantic contexts."),
                idAggregate("Integrations", SYSTEM, id, INTEGRATION, integrationIdsForSystem(view, id), view.integrationsById(), "Integration contracts reference this system as source or target."),
                signalAggregate(SYSTEM, id, system),
                handoffAggregate(SYSTEM, id, system),
                validationAggregate(SYSTEM, id, view.validationFindings()),
                openQuestionAggregate(SYSTEM, id, view.openQuestions())
        );
    }

    private OperationalContextRepositoryRowDto repositoryRow(CatalogView view, OperationalContextRepository repository) {
        var id = repository.id();
        return new OperationalContextRepositoryRowDto(
                id,
                repositoryProjectPath(repository),
                repositoryGroup(repository),
                ownerValue(view, REPOSITORY, repository),
                idAggregate("Systems", REPOSITORY, id, SYSTEM, systemIds(repository), view.systemsById(), "Repository lists these runtime systems."),
                idAggregate("Processes", REPOSITORY, id, PROCESS, processIds(repository), view.processesById(), "Repository is scoped to these processes."),
                idAggregate("Contexts", REPOSITORY, id, BOUNDED_CONTEXT, boundedContextIds(repository), view.contextsById(), "Repository contributes code to these contexts."),
                valueAggregate("Package roots", REPOSITORY, id, packageRoots(repository), "Package and source hints define the code search scope.", "package"),
                valueAggregate("Entry classes", REPOSITORY, id, entrypoints(repository), "Entrypoint and class hints help target code search.", "entrypoint"),
                valueAggregate("Runtime mappings", REPOSITORY, id, runtimeMappings(repository), "Runtime signals map deployed services back to this repository.", "runtime-signal"),
                moduleAggregate(REPOSITORY, id, repository),
                repositoryCodeSearchScopesAggregate(view, repository),
                repositoryCodeSearchRolesAggregate(view, repository),
                handoffAggregate(REPOSITORY, id, repository),
                validationAggregate(REPOSITORY, id, view.validationFindings())
        );
    }

    private OperationalContextCodeSearchScopeRowDto codeSearchScopeRow(
            CatalogView view,
            OperationalContextRepositorySearchScope scope
    ) {
        var id = scope.id();
        return new OperationalContextCodeSearchScopeRowDto(
                id,
                codeSearchScopeName(scope),
                scope.lifecycleStatus(),
                codeSearchScopeTargetsAggregate(view, scope),
                codeSearchScopeRepositoriesAggregate(view, scope),
                valueAggregate("Package hints", CODE_SEARCH_SCOPE, id, scope.packagePrefixes(), "Package prefixes narrow the first code search inside this scope.", "package"),
                valueAggregate("Entry hints", CODE_SEARCH_SCOPE, id, codeSearchEntryHints(scope), "Class, endpoint, queue and topic hints identify likely entry points.", "entrypoint"),
                codeSearchDataHintsAggregate(id, scope.databaseHints()),
                codeSearchWorkflowHintsAggregate(id, scope.workflowHints()),
                codeSearchStrategyAggregate(id, scope.searchStrategy(), scope.limitations()),
                validationAggregate(CODE_SEARCH_SCOPE, id, view.validationFindings())
        );
    }

    private OperationalContextProcessRowDto processRow(CatalogView view, OperationalContextProcess process) {
        var id = process.id();
        return new OperationalContextProcessRowDto(
                id,
                name(process),
                ownerValue(view, PROCESS, process),
                summaryText(process),
                idAggregate("Systems", PROCESS, id, SYSTEM, processSystemIds(process), view.systemsById(), "Process runs through these internal systems."),
                idAggregate("External systems", PROCESS, id, SYSTEM, processExternalSystemIds(process), view.systemsById(), "Process depends on these external systems."),
                idAggregate("Repositories", PROCESS, id, REPOSITORY, repositoryIds(process), view.repositoriesById(), "Process references these code scopes."),
                idAggregate("Contexts", PROCESS, id, BOUNDED_CONTEXT, boundedContextIds(process), view.contextsById(), "Process belongs to these semantic contexts."),
                stepAggregate(PROCESS, id, process),
                valueAggregate("Completion signals", PROCESS, id, completionSignals(process), "Completion signals show when the flow has finished.", "signal"),
                valueAggregate("Handoff hints", PROCESS, id, handoffHintValues(process), "Hints written directly on the process.", "hint"),
                validationAggregate(PROCESS, id, view.validationFindings())
        );
    }

    private OperationalContextIntegrationRowDto integrationRow(CatalogView view, OperationalContextIntegration integration) {
        var id = integration.id();
        return new OperationalContextIntegrationRowDto(
                id,
                name(integration),
                integrationSourceSystem(integration),
                String.join(", ", integrationTargetSystems(integration)),
                ownerValue(view, INTEGRATION, integration),
                idAggregate("Partner teams", INTEGRATION, id, TEAM, integrationPartnerTeamIds(integration), view.teamsById(), "Partner teams are listed on this integration contract."),
                String.join(", ", integrationProtocols(integration)),
                integrationType(integration),
                idAggregate("Processes", INTEGRATION, id, PROCESS, processIds(integration), view.processesById(), "Integration supports these processes."),
                idAggregate("Contexts", INTEGRATION, id, BOUNDED_CONTEXT, boundedContextIds(integration), view.contextsById(), "Integration crosses these bounded contexts."),
                signalAggregate(INTEGRATION, id, integration),
                handoffAggregate(INTEGRATION, id, integration),
                validationAggregate(INTEGRATION, id, view.validationFindings())
        );
    }

    private OperationalContextBoundedContextRowDto boundedContextRow(CatalogView view, OperationalContextBoundedContext context) {
        var id = context.id();
        return new OperationalContextBoundedContextRowDto(
                id,
                name(context),
                ownerValue(view, BOUNDED_CONTEXT, context),
                summaryText(context),
                idAggregate("Systems", BOUNDED_CONTEXT, id, SYSTEM, systemIds(context), view.systemsById(), "Context is implemented by these systems."),
                idAggregate("Repositories", BOUNDED_CONTEXT, id, REPOSITORY, repositoryIds(context), view.repositoriesById(), "Context is implemented in these repositories."),
                idAggregate("Processes", BOUNDED_CONTEXT, id, PROCESS, processIds(context), view.processesById(), "Context participates in these processes."),
                valueAggregate("Terms", BOUNDED_CONTEXT, id, termIds(context), "Terms describe the local domain vocabulary.", GLOSSARY_TERM),
                relationAggregate(BOUNDED_CONTEXT, id, context),
                valueAggregate("Runtime signals", BOUNDED_CONTEXT, id, runtimeSignals(context), "Signals that reveal this semantic area in runtime evidence.", "signal"),
                validationAggregate(BOUNDED_CONTEXT, id, view.validationFindings())
        );
    }

    private OperationalContextTeamRowDto teamRow(CatalogView view, OperationalContextTeam team) {
        var id = team.id();
        return new OperationalContextTeamRowDto(
                id,
                name(team),
                summaryText(team),
                idAggregate("Systems", TEAM, id, SYSTEM, teamOwnedIds(team, SYSTEM), view.systemsById(), "Team owns these systems."),
                idAggregate("Repositories", TEAM, id, REPOSITORY, teamOwnedIds(team, REPOSITORY), view.repositoriesById(), "Team owns these repositories."),
                idAggregate("Processes", TEAM, id, PROCESS, teamOwnedIds(team, PROCESS), view.processesById(), "Team owns these processes."),
                idAggregate("Contexts", TEAM, id, BOUNDED_CONTEXT, teamOwnedIds(team, BOUNDED_CONTEXT), view.contextsById(), "Team owns these contexts."),
                idAggregate("Integrations", TEAM, id, INTEGRATION, teamOwnedIds(team, INTEGRATION), view.integrationsById(), "Team owns these integration contracts."),
                signalAggregate(TEAM, id, team),
                handoffAggregate(TEAM, id, team),
                validationAggregate(TEAM, id, view.validationFindings())
        );
    }

    private OperationalContextEntityDetailDto mapEntityDetail(
            CatalogView view,
            String type,
            OperationalContextEntry entity
    ) {
        var id = entity.id();
        var validationFindings = findingsFor(type, id, view.validationFindings());
        var openQuestions = openQuestionsFor(type, id, view.openQuestions());
        var sourceRef = sourceRef(type, id);
        var owner = ownerValue(view, type, entity);
        var handoff = handoffAggregate(type, id, entity);
        var signals = signalAggregate(type, id, entity);

        return new OperationalContextEntityDetailDto(
                type,
                id,
                displayLabel(type, id, entity),
                detailSubtitle(type, entity),
                List.of(new OperationalContextDetailSectionDto("Overview", overviewFields(entity))),
                relatedGroups(view, type, entity),
                signals.groups(),
                List.of(
                        new OperationalContextExplainabilitySectionDto(
                                "Ownership",
                                owner.label(),
                                owner.confidence(),
                                owner.reasons(),
                                owner.warnings(),
                                owner.sourceRefs()
                        ),
                        new OperationalContextExplainabilitySectionDto(
                                "Handoff readiness",
                                handoff.tooltip(),
                                handoff.confidence(),
                                handoff.reasons(),
                                handoff.warnings(),
                                handoff.sourceRefs()
                        )
                ),
                validationFindings,
                openQuestions,
                List.of(sourceRef),
                rawPreview(entity.payload())
        );
    }

    private OperationalContextEntityDetailDto codeSearchScopeDetail(CatalogView view, String id) {
        var scope = view.catalog().codeSearchScopes().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(CODE_SEARCH_SCOPE, id));
        var sourceRef = sourceRef(CODE_SEARCH_SCOPE, scope.id());
        var targets = codeSearchScopeTargetsAggregate(view, scope);
        var repositories = codeSearchScopeRepositoriesAggregate(view, scope);
        var packageHints = valueAggregate("Package hints", CODE_SEARCH_SCOPE, id, scope.packagePrefixes(), "Package prefixes narrow the first code search inside this scope.", "package");
        var entryHints = valueAggregate("Entry hints", CODE_SEARCH_SCOPE, id, codeSearchEntryHints(scope), "Class, endpoint, queue and topic hints identify likely entry points.", "entrypoint");
        var dataHints = codeSearchDataHintsAggregate(id, scope.databaseHints());
        var workflowHints = codeSearchWorkflowHintsAggregate(id, scope.workflowHints());
        var strategy = codeSearchStrategyAggregate(id, scope.searchStrategy(), scope.limitations());

        return new OperationalContextEntityDetailDto(
                CODE_SEARCH_SCOPE,
                scope.id(),
                codeSearchScopeName(scope),
                scope.lifecycleStatus(),
                List.of(
                        new OperationalContextDetailSectionDto("Overview", orderedMap(
                                "id", scope.id(),
                                "name", scope.name(),
                                "lifecycleStatus", scope.lifecycleStatus(),
                                "useFor", scope.useFor(),
                                "limitations", scope.limitations()
                        )),
                        new OperationalContextDetailSectionDto("Targets", orderedMap(
                                "systems", scope.target().systems(),
                                "runtimeComponents", scope.target().runtimeComponents(),
                                "deploymentComponents", scope.target().deploymentComponents(),
                                "processes", scope.target().processes(),
                                "boundedContexts", scope.target().boundedContexts(),
                                "integrations", scope.target().integrations(),
                                "terms", scope.target().terms()
                        )),
                        new OperationalContextDetailSectionDto("Search hints", orderedMap(
                                "packagePrefixes", scope.packagePrefixes(),
                                "classHints", scope.classHints(),
                                "endpointHints", scope.endpointHints(),
                                "queueTopicHints", scope.queueTopicHints(),
                                "databaseHints", scope.databaseHints(),
                                "workflowHints", scope.workflowHints(),
                                "priorityOrder", scope.searchStrategy().priorityOrder()
                        ))
                ),
                combineGroups(targets.groups(), repositories.groups()),
                combineGroups(packageHints.groups(), entryHints.groups(), dataHints.groups(), workflowHints.groups(), strategy.groups()),
                List.of(new OperationalContextExplainabilitySectionDto(
                        "Multi-repository search scope",
                        "This scope tells operators and AI which repositories must be searched together for a runtime/process/domain target.",
                        "high",
                        List.of(reason("repo-map.yml codeSearchScopes", "The scope is parsed from the top-level codeSearchScopes section in repo-map.yml.", "strong")),
                        scope.limitations().isEmpty() ? List.of() : scope.limitations(),
                        List.of(sourceRef)
                )),
                findingsFor(CODE_SEARCH_SCOPE, scope.id(), view.validationFindings()),
                openQuestionsFor(CODE_SEARCH_SCOPE, scope.id(), view.openQuestions()),
                List.of(sourceRef),
                rawPreview(scope.payload())
        );
    }

    private OperationalContextEntityDetailDto glossaryDetail(CatalogView view, String id) {
        var term = view.catalog().glossaryTerms().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(GLOSSARY_TERM, id));
        var sourceRef = sourceRef(GLOSSARY_TERM, term.id());
        return new OperationalContextEntityDetailDto(
                GLOSSARY_TERM,
                term.id(),
                term.term(),
                term.category(),
                List.of(new OperationalContextDetailSectionDto("Overview", orderedMap(
                        "definition", term.definition(),
                        "useInContext", term.useInContext(),
                        "doNotConfuseWith", term.doNotConfuseWith(),
                        "synonyms", term.synonyms(),
                        "notes", term.notes()
                ))),
                List.of(valueAggregate("Canonical references", GLOSSARY_TERM, term.id(), term.canonicalReferences(), "Glossary term references these catalog ids.", "reference").groups().get(0)),
                valueAggregate("Match signals", GLOSSARY_TERM, term.id(), term.matchSignals(), "Signals listed on the term.", "signal").groups(),
                List.of(new OperationalContextExplainabilitySectionDto(
                        "Vocabulary",
                        "This term is parsed from glossary.md and helps explain incident evidence.",
                        "medium",
                        List.of(reason("Parsed glossary entry", "The term is present as a markdown entry in glossary.md.", "strong")),
                        List.of(),
                        List.of(sourceRef)
                )),
                findingsFor(GLOSSARY_TERM, term.id(), view.validationFindings()),
                openQuestionsFor(GLOSSARY_TERM, term.id(), view.openQuestions()),
                List.of(sourceRef),
                rawPreview(orderedMap("id", term.id(), "term", term.term(), "definition", term.definition()))
        );
    }

    private OperationalContextEntityDetailDto handoffRuleDetail(CatalogView view, String id) {
        var rule = view.catalog().handoffRules().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(HANDOFF_RULE, id));
        var sourceRef = sourceRef(HANDOFF_RULE, rule.id());
        return new OperationalContextEntityDetailDto(
                HANDOFF_RULE,
                rule.id(),
                rule.title(),
                rule.routeTo(),
                List.of(new OperationalContextDetailSectionDto("Checklist", orderedMap(
                        "routeTo", rule.routeTo(),
                        "useWhen", rule.useWhen(),
                        "doNotUseWhen", rule.doNotUseWhen(),
                        "requiredEvidence", rule.requiredEvidence(),
                        "expectedFirstAction", rule.expectedFirstAction(),
                        "partnerTeams", rule.partnerTeams(),
                        "notes", rule.notes()
                ))),
                List.of(),
                valueAggregate("Required evidence", HANDOFF_RULE, rule.id(), rule.requiredEvidence(), "Checklist evidence for this rule.", "evidence").groups(),
                List.of(new OperationalContextExplainabilitySectionDto(
                        "Routing",
                        "This rule is parsed from handoff-rules.md and should be used as a routing checklist.",
                        "medium",
                        List.of(reason("Parsed handoff rule", "The route and checklist are explicit in handoff-rules.md.", "strong")),
                        warnings(rule.routeTo(), rule.requiredEvidence()),
                        List.of(sourceRef)
                )),
                findingsFor(HANDOFF_RULE, rule.id(), view.validationFindings()),
                openQuestionsFor(HANDOFF_RULE, rule.id(), view.openQuestions()),
                List.of(sourceRef),
                rawPreview(orderedMap("id", rule.id(), "title", rule.title(), "routeTo", rule.routeTo()))
        );
    }

    private CatalogView view() {
        var loadedCatalog = operationalContextPort.loadContext(OperationalContextQuery.all());
        var catalog = loadedCatalog != null ? loadedCatalog : OperationalContextCatalog.empty();
        var openQuestions = catalog.openQuestions().stream()
                .map(question -> new OpenQuestionDto(
                        question.id(),
                        question.sourceFile(),
                        question.entityType(),
                        question.entityId(),
                        question.question(),
                        normalizeSeverity(question.severity()),
                        StringUtils.hasText(question.status()) ? question.status() : "open"
                ))
                .toList();
        var view = new CatalogView(
                catalog,
                indexById(catalog.systems()),
                indexById(catalog.repositories()),
                indexById(catalog.processes()),
                indexById(catalog.integrations()),
                indexById(catalog.boundedContexts()),
                indexById(catalog.teams()),
                openQuestions,
                List.of()
        );
        return view.withValidation(validate(view));
    }

    private List<ValidationFindingDto> validate(CatalogView view) {
        var findings = new ArrayList<ValidationFindingDto>();
        validateReferences(view, findings);
        validateOwnership(view, findings);
        validateCompleteness(view, findings);
        validateSignalQuality(view, findings);
        validateModelingQuality(view, findings);
        validateHandoffReadiness(view, findings);
        return findings.stream()
                .sorted(Comparator
                        .comparingInt((ValidationFindingDto finding) -> severityRank(finding.severity()))
                        .thenComparing(ValidationFindingDto::category)
                        .thenComparing(ValidationFindingDto::entityType)
                        .thenComparing(ValidationFindingDto::entityId, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private void validateReferences(CatalogView view, List<ValidationFindingDto> findings) {
        for (var system : view.catalog().systems()) {
            var id = system.id();
            requireReferences(findings, SYSTEM, id, "repositories", REPOSITORY, repositoryIds(system), view.repositoriesById(), "System references unknown repository.");
            requireReferences(findings, SYSTEM, id, "processes", PROCESS, processIds(system), view.processesById(), "System references unknown process.");
            requireReferences(findings, SYSTEM, id, "boundedContexts", BOUNDED_CONTEXT, boundedContextIds(system), view.contextsById(), "System references unknown bounded context.");
            requireReferences(findings, SYSTEM, id, "teams", TEAM, ownerTeamIds(system), view.teamsById(), "System team reference points to an unknown team.");
            requireReferences(findings, SYSTEM, id, "partnerTeamIds", TEAM, system.handoffHints().partnerTeamIds(), view.teamsById(), "System partner team reference points to an unknown team.");
        }
        for (var repository : view.catalog().repositories()) {
            var id = repository.id();
            requireReferences(findings, REPOSITORY, id, "systems", SYSTEM, systemIds(repository), view.systemsById(), "Repository references unknown system.");
            requireReferences(findings, REPOSITORY, id, "processes", PROCESS, processIds(repository), view.processesById(), "Repository references unknown process.");
            requireReferences(findings, REPOSITORY, id, "contexts", BOUNDED_CONTEXT, boundedContextIds(repository), view.contextsById(), "Repository references unknown bounded context.");
            requireReferences(findings, REPOSITORY, id, "teams", TEAM, ownerTeamIds(repository), view.teamsById(), "Repository team reference points to an unknown team.");
        }
        for (var scope : view.catalog().codeSearchScopes()) {
            var id = scope.id();
            requireReferences(findings, CODE_SEARCH_SCOPE, id, "repositories", REPOSITORY, codeSearchRepositoryIds(scope), view.repositoriesById(), "Code search scope references unknown repository.");
            requireReferences(findings, CODE_SEARCH_SCOPE, id, "target.systems", SYSTEM, scope.target().systems(), view.systemsById(), "Code search scope references unknown system.");
            requireReferences(findings, CODE_SEARCH_SCOPE, id, "target.processes", PROCESS, scope.target().processes(), view.processesById(), "Code search scope references unknown process.");
            requireReferences(findings, CODE_SEARCH_SCOPE, id, "target.boundedContexts", BOUNDED_CONTEXT, scope.target().boundedContexts(), view.contextsById(), "Code search scope references unknown bounded context.");
            requireReferences(findings, CODE_SEARCH_SCOPE, id, "target.integrations", INTEGRATION, scope.target().integrations(), view.integrationsById(), "Code search scope references unknown integration.");
            requireKnownIds(findings, CODE_SEARCH_SCOPE, id, "target.terms", GLOSSARY_TERM, scope.target().terms(), glossaryIds(view), "Code search scope references unknown glossary term.");
        }
        for (var process : view.catalog().processes()) {
            var id = process.id();
            requireReferences(findings, PROCESS, id, "systems", SYSTEM, processSystemIds(process), view.systemsById(), "Process references unknown system.");
            requireReferences(findings, PROCESS, id, "externalSystems", SYSTEM, processExternalSystemIds(process), view.systemsById(), "Process references unknown external system.");
            requireReferences(findings, PROCESS, id, "repositories", REPOSITORY, repositoryIds(process), view.repositoriesById(), "Process references unknown repository.");
            requireReferences(findings, PROCESS, id, "contexts", BOUNDED_CONTEXT, boundedContextIds(process), view.contextsById(), "Process references unknown bounded context.");
            requireReferences(findings, PROCESS, id, "teams", TEAM, ownerTeamIds(process), view.teamsById(), "Process team reference points to an unknown team.");
            requireReferences(findings, PROCESS, id, "partnerTeamIds", TEAM, process.handoffHints().partnerTeamIds(), view.teamsById(), "Process partner team reference points to an unknown team.");
        }
        for (var integration : view.catalog().integrations()) {
            var id = integration.id();
            requireReferences(findings, INTEGRATION, id, "participants", SYSTEM, integrationSystems(integration), view.systemsById(), "Integration source or target system is not modeled.");
            requireReferences(findings, INTEGRATION, id, "processes", PROCESS, processIds(integration), view.processesById(), "Integration references unknown process.");
            requireReferences(findings, INTEGRATION, id, "contexts", BOUNDED_CONTEXT, boundedContextIds(integration), view.contextsById(), "Integration references unknown bounded context.");
            requireReferences(findings, INTEGRATION, id, "teams", TEAM, ownerTeamIds(integration), view.teamsById(), "Integration team reference points to an unknown team.");
            requireReferences(findings, INTEGRATION, id, "partnerTeamIds", TEAM, integrationPartnerTeamIds(integration), view.teamsById(), "Integration partner team reference points to an unknown team.");
        }
        for (var context : view.catalog().boundedContexts()) {
            var id = context.id();
            requireReferences(findings, BOUNDED_CONTEXT, id, "systems", SYSTEM, systemIds(context), view.systemsById(), "Bounded context references unknown system.");
            requireReferences(findings, BOUNDED_CONTEXT, id, "repositories", REPOSITORY, repositoryIds(context), view.repositoriesById(), "Bounded context references unknown repository.");
            requireReferences(findings, BOUNDED_CONTEXT, id, "processes", PROCESS, processIds(context), view.processesById(), "Bounded context references unknown process.");
            requireKnownIds(findings, BOUNDED_CONTEXT, id, "terms", GLOSSARY_TERM, termIds(context), glossaryIds(view), "Bounded context references unknown glossary term.");
            requireReferences(findings, BOUNDED_CONTEXT, id, "teams", TEAM, ownerTeamIds(context), view.teamsById(), "Bounded context team reference points to an unknown team.");
        }
        for (var team : view.catalog().teams()) {
            var id = team.id();
            requireReferences(findings, TEAM, id, "systems", SYSTEM, teamOwnedIds(team, SYSTEM), view.systemsById(), "Team references unknown system.");
            requireReferences(findings, TEAM, id, "repositories", REPOSITORY, teamOwnedIds(team, REPOSITORY), view.repositoriesById(), "Team references unknown repository.");
            requireReferences(findings, TEAM, id, "processes", PROCESS, teamOwnedIds(team, PROCESS), view.processesById(), "Team references unknown process.");
            requireReferences(findings, TEAM, id, "contexts", BOUNDED_CONTEXT, teamOwnedIds(team, BOUNDED_CONTEXT), view.contextsById(), "Team references unknown bounded context.");
            requireReferences(findings, TEAM, id, "integrations", INTEGRATION, teamOwnedIds(team, INTEGRATION), view.integrationsById(), "Team references unknown integration.");
        }
    }

    private void validateOwnership(CatalogView view, List<ValidationFindingDto> findings) {
        view.catalog().systems().forEach(entry -> validateOwnerBackReference(view, findings, SYSTEM, entry));
        view.catalog().repositories().forEach(entry -> validateOwnerBackReference(view, findings, REPOSITORY, entry));
        view.catalog().processes().forEach(entry -> validateOwnerBackReference(view, findings, PROCESS, entry));
        view.catalog().integrations().forEach(entry -> validateOwnerBackReference(view, findings, INTEGRATION, entry));
        view.catalog().boundedContexts().forEach(entry -> validateOwnerBackReference(view, findings, BOUNDED_CONTEXT, entry));
    }

    private void validateCompleteness(CatalogView view, List<ValidationFindingDto> findings) {
        for (var system : view.catalog().systems()) {
            if (!internalSystem(system)) {
                continue;
            }
            if (repositoryIds(system).isEmpty()) {
                addFinding(findings, "warning", "completeness", SYSTEM, system.id(), "Internal system has no repository scope.", "Internal systems should list at least one repository.", "Add references.repositories.", "AI code lookup may be weaker.");
            }
            if (ownerTeamIds(system).isEmpty() && owningTeamIds(view, SYSTEM, system.id()).isEmpty()) {
                addFinding(findings, "warning", "completeness", SYSTEM, system.id(), "System owner is missing.", "No owner team is declared or referencing this system.", "Add responsibilities.teamId or team references.", "Handoff recommendation may be weaker.");
            }
        }
        for (var repository : view.catalog().repositories()) {
            if (!StringUtils.hasText(repositoryProjectPath(repository))) {
                addFinding(findings, "error", "completeness", REPOSITORY, repository.id(), "Repository GitLab path is missing.", "Repository cannot be used by GitLab tools without git.projectPath or git.project.", "Add git.projectPath.", "AI cannot reliably read this repository.");
            }
        }
        for (var scope : view.catalog().codeSearchScopes()) {
            if (scope.repositories().stream().noneMatch(OperationalContextRepositorySearchRepository::include)) {
                addFinding(findings, "error", "completeness", CODE_SEARCH_SCOPE, scope.id(), "Code search scope has no included repositories.", "AI cannot perform a multi-repository lookup without at least one included repository.", "Add codeSearchScopes.repositories with include=true.", "GitLab tool guidance may be incomplete.");
            }
            if (codeSearchScopeTargetValues(scope).isEmpty()) {
                addFinding(findings, "warning", "completeness", CODE_SEARCH_SCOPE, scope.id(), "Code search scope has no operational target.", "Scope is not anchored to systems, runtime components, processes, bounded contexts or integrations.", "Fill codeSearchScopes.target.", "Operators may not know when to use this scope.");
            }
        }
    }

    private void validateSignalQuality(CatalogView view, List<ValidationFindingDto> findings) {
        duplicateSignalFindings(findings, allCatalogEntries(view), "serviceNames", "serviceName");
        duplicateSignalFindings(findings, allCatalogEntries(view), "endpointPrefixes", "endpointPrefix");
        for (var entry : allCatalogEntries(view)) {
            for (var signal : signalValues(entry.entry())) {
                var normalized = normalize(signal);
                if (GENERIC_MARKERS.contains(normalized)) {
                    addFinding(findings, "info", "signal-quality", entry.type(), entry.entry().id(), "Generic recognition signal.", "Signal `" + signal + "` is too generic to explain a confident match.", "Replace it with a runtime-specific marker.", "Low-confidence matches may appear.");
                }
            }
        }
    }

    private void validateModelingQuality(CatalogView view, List<ValidationFindingDto> findings) {
        for (var integration : view.catalog().integrations()) {
            if (integrationSystems(integration).size() < 2) {
                addFinding(findings, "warning", "modeling-quality", INTEGRATION, integration.id(), "Integration participants are incomplete.", "Integration should identify at least source and target systems.", "Fill participants.source and participants.targets.", "Routing and dependency exploration may be weaker.");
            }
        }
    }

    private void validateHandoffReadiness(CatalogView view, List<ValidationFindingDto> findings) {
        for (var entry : allHandoffEntries(view)) {
            if (!StringUtils.hasText(handoffTarget(entry.entry()))) {
                addFinding(findings, "warning", "handoff-readiness", entry.type(), entry.entry().id(), "Handoff target is missing.", "No default route or owner hint is present.", "Add handoffHints.defaultRouteLabel or ownership.", "The UI cannot show a clear handoff target.");
            }
            if (handoffRequiredEvidence(entry.entry()).isEmpty()) {
                addFinding(findings, "info", "handoff-readiness", entry.type(), entry.entry().id(), "Handoff evidence checklist is empty.", "No required evidence is listed for handoff.", "Add handoffHints.requiredEvidence.", "Operators may lack a handoff checklist.");
            }
        }
    }

    private void requireReferences(
            List<ValidationFindingDto> findings,
            String sourceType,
            String sourceId,
            String field,
            String targetType,
            List<String> targetIds,
            Map<String, OperationalContextEntry> targetIndex,
            String detail
    ) {
        var missing = distinct(targetIds).stream()
                .filter(targetId -> !targetIndex.containsKey(targetId))
                .toList();
        for (var targetId : missing) {
            addFinding(findings, "error", "reference-integrity", sourceType, sourceId, "Unknown " + targetType + " reference in " + field + ".", detail + " Missing id: " + targetId, "Create the target entry or remove the reference.", "Invalid links reduce explainability.");
        }
    }

    private void requireKnownIds(
            List<ValidationFindingDto> findings,
            String sourceType,
            String sourceId,
            String field,
            String targetType,
            List<String> targetIds,
            Set<String> knownIds,
            String detail
    ) {
        var missing = distinct(targetIds).stream()
                .filter(targetId -> !knownIds.contains(targetId))
                .toList();
        for (var targetId : missing) {
            addFinding(findings, "warning", "reference-integrity", sourceType, sourceId, "Unknown " + targetType + " reference in " + field + ".", detail + " Missing id: " + targetId, "Create the target entry or remove the reference.", "Invalid links reduce explainability.");
        }
    }

    private void validateOwnerBackReference(
            CatalogView view,
            List<ValidationFindingDto> findings,
            String entityType,
            OperationalContextEntry entry
    ) {
        for (var teamId : ownerTeamIds(entry)) {
            var team = view.teamsById().get(teamId);
            if (!(team instanceof OperationalContextTeam operationalContextTeam)) {
                continue;
            }
            if (!teamOwnedIds(operationalContextTeam, entityType).contains(entry.id())) {
                addFinding(findings, "warning", "ownership-consistency", entityType, entry.id(), "Owner team does not list this entity.", "Owner `" + teamId + "` is declared on the entity, but the team does not point back to `" + entry.id() + "`.", "Add the back-reference on the team or remove the owner hint.", "Ownership explainability becomes asymmetric.");
            }
        }
    }

    private void duplicateSignalFindings(
            List<ValidationFindingDto> findings,
            List<EntryPointer> entries,
            String signalKey,
            String label
    ) {
        var bySignal = new LinkedHashMap<String, List<EntryPointer>>();
        for (var pointer : entries) {
            for (var signal : signalMap(pointer.entry()).getOrDefault(signalKey, List.of())) {
                if (!StringUtils.hasText(signal)) {
                    continue;
                }
                bySignal.computeIfAbsent(normalize(signal), ignored -> new ArrayList<>())
                        .add(pointer);
            }
        }

        bySignal.forEach((signal, owners) -> {
            var distinctOwners = owners.stream()
                    .filter(owner -> StringUtils.hasText(owner.entry().id()))
                    .distinct()
                    .toList();
            if (distinctOwners.size() < 2) {
                return;
            }
            var first = distinctOwners.get(0);
            var ownerLabels = distinctOwners.stream()
                    .map(owner -> owner.type() + " " + owner.entry().id())
                    .toList();
            addFinding(findings, "warning", "signal-quality", first.type(), first.entry().id(), "Duplicate " + label + " signal.", "Signal `" + signal + "` appears on: " + String.join(", ", ownerLabels) + ".", "Make the signal unique or move it to a shared parent concept.", "Duplicate signals can produce ambiguous matches.");
        });
    }

    private void addFinding(
            List<ValidationFindingDto> findings,
            String severity,
            String category,
            String entityType,
            String entityId,
            String title,
            String detail,
            String suggestedFix,
            String impact
    ) {
        findings.add(new ValidationFindingDto(
                slug(String.join(":", category, entityType, entityId != null ? entityId : "", title, detail)),
                normalizeSeverity(severity),
                category,
                entityType,
                entityId,
                title,
                detail,
                List.of(sourceRef(entityType, entityId)),
                suggestedFix,
                impact
        ));
    }

    private ExplainableAggregateDto countCard(String label, int count, String detailsType, List<?> entries) {
        var items = new ArrayList<ExplainableBreakdownItemDto>();
        for (var entry : entries) {
            if (entry instanceof OperationalContextEntry contextEntry) {
                items.add(item(contextEntry.id(), displayLabel(detailsType, contextEntry.id(), contextEntry), detailsType, "Loaded from " + sourceFile(detailsType) + ".", "verified", sourceRef(detailsType, contextEntry.id())));
            } else if (entry instanceof OpenQuestionDto question) {
                items.add(item(question.id(), question.question(), "open-question", "Parsed from " + question.sourceFile() + ".", "needs-review", new SourceReferenceDto(question.sourceFile(), "", question.entityId())));
            }
        }
        return aggregate(label, count, count > 0 ? "ok" : "unknown", "high", label + " loaded from the operational context catalog.", List.of(group(label, items)), List.of(reason("Catalog count", "Count is derived from parsed catalog entries.", "strong")), List.of(), sourceRefs(items), detailsType, ids(items));
    }

    private ExplainableAggregateDto codeSearchScopeCountCard(
            List<OperationalContextRepositorySearchScope> scopes
    ) {
        var items = scopes.stream()
                .map(scope -> item(scope.id(), codeSearchScopeName(scope), CODE_SEARCH_SCOPE, "Loaded from repo-map.yml codeSearchScopes.", "verified", sourceRef(CODE_SEARCH_SCOPE, scope.id())))
                .toList();
        return aggregate(
                "Code Search Scopes",
                items.size(),
                items.isEmpty() ? "unknown" : "ok",
                "high",
                "Top-level multi-repository search scopes loaded from repo-map.yml.",
                List.of(group("Code Search Scopes", items)),
                List.of(reason("Catalog count", "Count is derived from repo-map.yml codeSearchScopes.", "strong")),
                List.of(),
                sourceRefs(items),
                CODE_SEARCH_SCOPE,
                ids(items)
        );
    }

    private ExplainableAggregateDto validationCard(List<ValidationFindingDto> findings) {
        var items = findings.stream()
                .map(finding -> item(finding.id(), finding.title(), "validation", finding.detail(), finding.severity().equals("error") ? "conflicting" : "needs-review", finding.sourceRefs()))
                .toList();
        return aggregate(
                "Validation Findings",
                items.size(),
                aggregateSeverity(findings),
                "high",
                "Quality findings returned by backend validation.",
                List.of(group("Findings", items)),
                List.of(reason("Backend validation", "Findings are generated from reference, ownership, completeness, signal, modeling and handoff checks.", "strong")),
                List.of(),
                sourceRefs(items),
                "validation",
                ids(items)
        );
    }

    private ExplainableValueDto<String> ownerValue(
            CatalogView view,
            String entityType,
            OperationalContextEntry entry
    ) {
        var entityId = entry.id();
        var warnings = new ArrayList<String>();
        var reasons = new ArrayList<ExplanationReasonDto>();
        var ownerTeamIds = ownerTeamIds(entry);
        var knownTeams = ownerTeamIds.stream()
                .filter(teamId -> view.teamsById().containsKey(teamId))
                .toList();
        if (!knownTeams.isEmpty()) {
            var labels = knownTeams.stream()
                    .map(teamId -> name(view.teamsById().get(teamId)))
                    .distinct()
                    .toList();
            reasons.add(reason("responsibilities/references", "Owner hint is derived from responsibilities.teamId or references.teams in the current operational-context contract.", "medium"));
            var refs = new ArrayList<SourceReferenceDto>();
            refs.add(sourceRef(entityType, entityId));
            knownTeams.forEach(teamId -> refs.add(sourceRef(TEAM, teamId)));
            return new ExplainableValueDto<>(String.join(", ", knownTeams), String.join(", ", labels), "medium", reasons, List.of(), refs);
        }
        var owningTeamIds = owningTeamIds(view, entityType, entityId);
        if (!owningTeamIds.isEmpty()) {
            var labels = owningTeamIds.stream()
                    .map(teamId -> name(view.teamsById().get(teamId)))
                    .distinct()
                    .toList();
            reasons.add(reason("teams.yml references", "Owner hint is derived from teams.yml references or responsibilities pointing back to this entity.", "medium"));
            var refs = new ArrayList<SourceReferenceDto>();
            refs.add(sourceRef(entityType, entityId));
            owningTeamIds.forEach(teamId -> refs.add(sourceRef(TEAM, teamId)));
            return new ExplainableValueDto<>(String.join(", ", owningTeamIds), String.join(", ", labels), "medium", reasons, List.of(), refs);
        }
        if (!ownerTeamIds.isEmpty()) {
            warnings.add("Declared team references do not point to known teams.");
            reasons.add(reason("responsibilities/references", "The entity declares team ids, but none exists in teams.yml.", "weak"));
            return new ExplainableValueDto<>(String.join(", ", ownerTeamIds), String.join(", ", ownerTeamIds), "low", reasons, warnings, List.of(sourceRef(entityType, entityId)));
        }

        var handoffTarget = handoffTarget(entry);
        if (StringUtils.hasText(handoffTarget)) {
            reasons.add(reason("handoff target", "No explicit owner is present, so handoff target hints are used as the closest owner hint.", "weak"));
            return new ExplainableValueDto<>(handoffTarget, handoffTarget, "low", reasons, List.of("Owner is inferred from handoff target, not explicit ownership."), List.of(sourceRef(entityType, entityId)));
        }

        return new ExplainableValueDto<>("", "Missing owner", "low", List.of(reason("No owner hint", "No responsibilities, references.teams or handoffHints owner hint is present.", "weak")), List.of("Owner is missing."), List.of(sourceRef(entityType, entityId)));
    }

    private ExplainableAggregateDto idAggregate(
            String label,
            String sourceType,
            String sourceId,
            String targetType,
            List<String> ids,
            Map<String, OperationalContextEntry> targetsById,
            String reason
    ) {
        var items = distinct(ids).stream()
                .map(targetId -> {
                    var target = targetsById.get(targetId);
                    var status = target == null ? "missing" : "verified";
                    var itemLabel = target == null ? targetId : displayLabel(targetType, targetId, target);
                    return item(targetId, itemLabel, targetType, reason, status, sourceRef(targetType, targetId));
                })
                .toList();
        var missing = items.stream().anyMatch(item -> item.status().equals("missing"));
        return aggregate(
                label,
                items.size(),
                missing ? "error" : items.isEmpty() ? "unknown" : "ok",
                missing ? "medium" : "high",
                tooltip(label, items.size(), reason),
                List.of(group(label, items)),
                List.of(reason("Explicit reference", reason, "strong")),
                missing ? List.of("Some referenced ids are missing from the catalog.") : List.of(),
                List.of(sourceRef(sourceType, sourceId)),
                targetType,
                ids(items)
        );
    }

    private ExplainableAggregateDto valueAggregate(
            String label,
            String sourceType,
            String sourceId,
            List<String> values,
            String reason,
            String itemType
    ) {
        var items = distinct(values).stream()
                .map(value -> item(value, value, itemType, reason, "verified", sourceRef(sourceType, sourceId)))
                .toList();
        return aggregate(
                label,
                items.size(),
                items.isEmpty() ? "unknown" : "ok",
                "high",
                tooltip(label, items.size(), reason),
                List.of(group(label, items)),
                List.of(reason("Catalog values", reason, "strong")),
                List.of(),
                List.of(sourceRef(sourceType, sourceId)),
                itemType,
                ids(items)
        );
    }

    private ExplainableAggregateDto repositoryCodeSearchScopesAggregate(
            CatalogView view,
            OperationalContextRepository repository
    ) {
        var items = scopesForRepository(view, repository.id()).stream()
                .map(scope -> item(
                        scope.id(),
                        codeSearchScopeName(scope),
                        CODE_SEARCH_SCOPE,
                        "Repository is part of this top-level codeSearchScopes entry.",
                        "verified",
                        List.of(sourceRef(REPOSITORY, repository.id()), sourceRef(CODE_SEARCH_SCOPE, scope.id()))
                ))
                .toList();
        return aggregate(
                "Search scopes",
                items.size(),
                items.isEmpty() ? "unknown" : "ok",
                items.isEmpty() ? "low" : "high",
                items.isEmpty()
                        ? "This repository is not assigned to any codeSearchScopes entry."
                        : "Top-level codeSearchScopes entries that include this repository.",
                List.of(group("Search scopes", items)),
                List.of(reason("repo-map.yml codeSearchScopes", "Scopes are matched by repository id in repo-map.yml.", "strong")),
                items.isEmpty() ? List.of("AI may only see this repository as an isolated project in the UI catalogue.") : List.of(),
                List.of(sourceRef(REPOSITORY, repository.id())),
                CODE_SEARCH_SCOPE,
                ids(items)
        );
    }

    private ExplainableAggregateDto repositoryCodeSearchRolesAggregate(
            CatalogView view,
            OperationalContextRepository repository
    ) {
        var items = scopesForRepository(view, repository.id()).stream()
                .flatMap(scope -> scope.repositories().stream()
                        .filter(scopeRepository -> scopeRepository.include())
                        .filter(scopeRepository -> repository.id().equals(scopeRepository.repoId()))
                        .map(scopeRepository -> item(
                                scope.id(),
                                codeSearchScopeName(scope) + " / " + codeSearchRepositoryRoleLabel(scopeRepository),
                                CODE_SEARCH_SCOPE,
                                codeSearchRepositoryReason(scopeRepository),
                                "verified",
                                List.of(sourceRef(REPOSITORY, repository.id()), sourceRef(CODE_SEARCH_SCOPE, scope.id()))
                        )))
                .toList();
        return aggregate(
                "Scope roles",
                items.size(),
                items.isEmpty() ? "unknown" : "ok",
                items.isEmpty() ? "low" : "high",
                items.isEmpty()
                        ? "No role/priority is assigned to this repository in codeSearchScopes."
                        : "Repository roles and priorities in multi-repository code search scopes.",
                List.of(group("Scope roles", items)),
                List.of(reason("repo-map.yml codeSearchScopes.repositories", "Roles and priorities come from top-level codeSearchScopes repositories entries.", "strong")),
                items.isEmpty() ? List.of("Missing scope roles make shared libraries and generated clients harder to understand from the UI.") : List.of(),
                List.of(sourceRef(REPOSITORY, repository.id())),
                CODE_SEARCH_SCOPE,
                ids(items)
        );
    }

    private ExplainableAggregateDto codeSearchScopeTargetsAggregate(
            CatalogView view,
            OperationalContextRepositorySearchScope scope
    ) {
        var groups = new ArrayList<ExplainableBreakdownGroupDto>();
        addTargetGroup(groups, "Systems", SYSTEM, scope.target().systems(), view.systemsById(), "Scope targets these runtime systems.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        addValueGroup(groups, "Runtime components", scope.target().runtimeComponents(), "runtime-component", "Scope targets these runtime component names.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        addValueGroup(groups, "Deployment components", scope.target().deploymentComponents(), "deployment-component", "Scope targets these deployment component names.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        addTargetGroup(groups, "Processes", PROCESS, scope.target().processes(), view.processesById(), "Scope targets these operational processes.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        addTargetGroup(groups, "Bounded contexts", BOUNDED_CONTEXT, scope.target().boundedContexts(), view.contextsById(), "Scope targets these bounded contexts.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        addTargetGroup(groups, "Integrations", INTEGRATION, scope.target().integrations(), view.integrationsById(), "Scope targets these integration contracts.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        addValueGroup(groups, "Terms", scope.target().terms(), GLOSSARY_TERM, "Scope targets these glossary terms.", sourceRef(CODE_SEARCH_SCOPE, scope.id()));
        var count = groups.stream().mapToInt(ExplainableBreakdownGroupDto::count).sum();
        return aggregate(
                "Targets",
                count,
                count == 0 ? "unknown" : "ok",
                count == 0 ? "low" : "high",
                count == 0 ? "No target systems/processes/contexts are declared." : "Runtime, process and domain targets for this multi-repo search scope.",
                groups,
                List.of(reason("codeSearchScopes.target", "Targets are parsed from repo-map.yml codeSearchScopes.target.", "strong")),
                count == 0 ? List.of("Scope is not anchored to operational entities.") : List.of(),
                List.of(sourceRef(CODE_SEARCH_SCOPE, scope.id())),
                "",
                idsFromGroups(groups)
        );
    }

    private ExplainableAggregateDto codeSearchScopeRepositoriesAggregate(
            CatalogView view,
            OperationalContextRepositorySearchScope scope
    ) {
        var items = scope.repositories().stream()
                .sorted(Comparator
                        .comparing((OperationalContextRepositorySearchRepository repository) -> repository.priority(), Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(OperationalContextRepositorySearchRepository::repoId, Comparator.nullsLast(String::compareTo)))
                .map(scopeRepository -> {
                    var repository = view.repositoriesById().get(scopeRepository.repoId());
                    var status = repository == null ? "missing" : scopeRepository.include() ? "verified" : "needs-review";
                    var label = repository == null
                            ? firstDefined(scopeRepository.repoId(), "Missing repository")
                            : displayLabel(REPOSITORY, scopeRepository.repoId(), repository);
                    return item(
                            scopeRepository.repoId(),
                            label + " / " + codeSearchRepositoryRoleLabel(scopeRepository),
                            REPOSITORY,
                            codeSearchRepositoryReason(scopeRepository),
                            status,
                            List.of(sourceRef(CODE_SEARCH_SCOPE, scope.id()), sourceRef(REPOSITORY, scopeRepository.repoId()))
                    );
                })
                .toList();
        var hasMissing = items.stream().anyMatch(item -> item.status().equals("missing"));
        return aggregate(
                "Repositories",
                items.size(),
                hasMissing ? "error" : items.isEmpty() ? "unknown" : "ok",
                hasMissing ? "medium" : "high",
                items.isEmpty()
                        ? "Scope has no repositories, so GitLab search cannot be planned."
                        : "Ordered repository set for this code search scope, including shared libraries and generated clients.",
                List.of(group("Repositories", items)),
                List.of(reason("codeSearchScopes.repositories", "Repository roles, priorities and module ids are explicit in repo-map.yml.", "strong")),
                hasMissing ? List.of("Some repositories referenced by this scope are missing from repositories[].") : List.of(),
                List.of(sourceRef(CODE_SEARCH_SCOPE, scope.id())),
                REPOSITORY,
                ids(items)
        );
    }

    private ExplainableAggregateDto codeSearchDataHintsAggregate(
            String scopeId,
            OperationalContextRepositorySearchDatabaseHints hints
    ) {
        var groups = new ArrayList<ExplainableBreakdownGroupDto>();
        var sourceRef = sourceRef(CODE_SEARCH_SCOPE, scopeId);
        addValueGroup(groups, "Datasources", hints.datasourceNames(), "datasource", "Datasource names narrow DB-related code lookup.", sourceRef);
        addValueGroup(groups, "Hikari pools", hints.hikariPools(), "datasource", "Hikari pool names narrow DB-related code lookup.", sourceRef);
        addValueGroup(groups, "Schemas", hints.schemas(), "schema", "Schema names narrow persistence lookup.", sourceRef);
        addValueGroup(groups, "Tables", hints.tables(), "table", "Table names narrow persistence lookup.", sourceRef);
        addValueGroup(groups, "Entities", hints.entities(), "entity", "Entity names narrow persistence lookup.", sourceRef);
        addValueGroup(groups, "Migrations", hints.migrations(), "migration", "Migration paths narrow persistence lookup.", sourceRef);
        var count = groups.stream().mapToInt(ExplainableBreakdownGroupDto::count).sum();
        return aggregate(
                "Data hints",
                count,
                count == 0 ? "unknown" : "ok",
                count == 0 ? "low" : "high",
                count == 0 ? "No DB/persistence hints are declared." : "DB and persistence hints from the code search scope.",
                groups,
                List.of(reason("codeSearchScopes.databaseHints", "Hints come from repo-map.yml databaseHints.", "strong")),
                List.of(),
                List.of(sourceRef),
                "",
                idsFromGroups(groups)
        );
    }

    private ExplainableAggregateDto codeSearchWorkflowHintsAggregate(
            String scopeId,
            OperationalContextRepositorySearchWorkflowHints hints
    ) {
        var groups = new ArrayList<ExplainableBreakdownGroupDto>();
        var sourceRef = sourceRef(CODE_SEARCH_SCOPE, scopeId);
        addValueGroup(groups, "Jobs", hints.jobNames(), "job", "Job names narrow batch/workflow lookup.", sourceRef);
        addValueGroup(groups, "Workflows", hints.workflowNames(), "workflow", "Workflow names narrow orchestration lookup.", sourceRef);
        addValueGroup(groups, "Definitions", hints.definitionPaths(), "path", "Definition paths narrow workflow lookup.", sourceRef);
        var count = groups.stream().mapToInt(ExplainableBreakdownGroupDto::count).sum();
        return aggregate(
                "Workflow hints",
                count,
                count == 0 ? "unknown" : "ok",
                count == 0 ? "low" : "high",
                count == 0 ? "No workflow hints are declared." : "Workflow and batch lookup hints from the code search scope.",
                groups,
                List.of(reason("codeSearchScopes.workflowHints", "Hints come from repo-map.yml workflowHints.", "strong")),
                List.of(),
                List.of(sourceRef),
                "",
                idsFromGroups(groups)
        );
    }

    private ExplainableAggregateDto codeSearchStrategyAggregate(
            String scopeId,
            OperationalContextRepositorySearchStrategy strategy,
            List<String> limitations
    ) {
        var groups = new ArrayList<ExplainableBreakdownGroupDto>();
        var sourceRef = sourceRef(CODE_SEARCH_SCOPE, scopeId);
        addValueGroup(groups, "Priority order", strategy.priorityOrder(), "code-search-role", "Search should inspect repositories in this role order.", sourceRef);
        var includes = new ArrayList<String>();
        if (strategy.includeGeneratedClients()) {
            includes.add("generated clients");
        }
        if (strategy.includeSharedLibraries()) {
            includes.add("shared libraries");
        }
        if (strategy.includeDeploymentConfig()) {
            includes.add("deployment config");
        }
        if (strategy.includeDocumentation()) {
            includes.add("documentation");
        }
        addValueGroup(groups, "Include", includes, "strategy-include", "Search strategy explicitly includes these repository types.", sourceRef);
        addValueGroup(groups, "Notes", strategy.notes(), "note", "Strategy notes explain how AI should traverse this scope.", sourceRef);
        addValueGroup(groups, "Limitations", limitations, "limitation", "Known limitations should be visible before trusting the scope.", sourceRef);
        var count = groups.stream().mapToInt(ExplainableBreakdownGroupDto::count).sum();
        return aggregate(
                "Strategy",
                count,
                limitations.isEmpty() ? count == 0 ? "unknown" : "ok" : "warning",
                count == 0 ? "low" : "high",
                count == 0 ? "No explicit search strategy is declared." : "How AI should traverse repositories in this code search scope.",
                groups,
                List.of(reason("codeSearchScopes.searchStrategy", "Strategy comes from repo-map.yml searchStrategy and limitations.", "strong")),
                limitations.isEmpty() ? List.of() : List.of("Scope declares limitations that should be considered during analysis."),
                List.of(sourceRef),
                "",
                idsFromGroups(groups)
        );
    }

    private void addTargetGroup(
            List<ExplainableBreakdownGroupDto> groups,
            String label,
            String targetType,
            List<String> ids,
            Map<String, OperationalContextEntry> targetIndex,
            String reason,
            SourceReferenceDto scopeSourceRef
    ) {
        var items = distinct(ids).stream()
                .map(targetId -> {
                    var target = targetIndex.get(targetId);
                    var status = target == null ? "missing" : "verified";
                    var itemLabel = target == null ? targetId : displayLabel(targetType, targetId, target);
                    return item(targetId, itemLabel, targetType, reason, status, List.of(scopeSourceRef, sourceRef(targetType, targetId)));
                })
                .toList();
        if (!items.isEmpty()) {
            groups.add(group(label, items));
        }
    }

    private void addValueGroup(
            List<ExplainableBreakdownGroupDto> groups,
            String label,
            List<String> values,
            String itemType,
            String reason,
            SourceReferenceDto scopeSourceRef
    ) {
        var items = distinct(values).stream()
                .map(value -> item(label + ":" + value, value, itemType, reason, "verified", scopeSourceRef))
                .toList();
        if (!items.isEmpty()) {
            groups.add(group(label, items));
        }
    }

    private List<OperationalContextRepositorySearchScope> scopesForRepository(
            CatalogView view,
            String repositoryId
    ) {
        return view.catalog().codeSearchScopes().stream()
                .filter(scope -> scope.repositories().stream()
                        .anyMatch(scopeRepository -> scopeRepository.include()
                                && repositoryId.equals(scopeRepository.repoId())))
                .toList();
    }

    private List<String> codeSearchEntryHints(OperationalContextRepositorySearchScope scope) {
        return distinct(combineValues(scope.classHints(), scope.endpointHints(), scope.queueTopicHints()));
    }

    private String codeSearchScopeName(OperationalContextRepositorySearchScope scope) {
        return firstDefined(scope.name(), scope.id());
    }

    private String codeSearchRepositoryRoleLabel(OperationalContextRepositorySearchRepository repository) {
        return String.join(" ", combineValues(
                firstDefined(repository.role(), "role unknown"),
                repository.priority() != null ? "priority " + repository.priority() : "",
                repository.include() ? "" : "excluded"
        ));
    }

    private String codeSearchRepositoryReason(OperationalContextRepositorySearchRepository repository) {
        var details = new ArrayList<String>();
        if (StringUtils.hasText(repository.role())) {
            details.add("role=" + repository.role());
        }
        if (repository.priority() != null) {
            details.add("priority=" + repository.priority());
        }
        if (!repository.moduleIds().isEmpty()) {
            details.add("modules=" + String.join(", ", repository.moduleIds()));
        }
        if (!repository.include()) {
            details.add("include=false");
        }
        if (StringUtils.hasText(repository.reason())) {
            details.add(repository.reason());
        }
        return details.isEmpty()
                ? "Repository is listed in this code search scope."
                : String.join("; ", details);
    }

    private ExplainableAggregateDto signalAggregate(
            String sourceType,
            String sourceId,
            OperationalContextEntry entry
    ) {
        var groups = new ArrayList<ExplainableBreakdownGroupDto>();
        var signals = signalMap(entry);
        for (var signalGroup : signals.entrySet()) {
            var items = signalGroup.getValue().stream()
                    .map(value -> item(signalGroup.getKey() + ":" + value, value, "signal", "Signal is declared in `" + signalGroup.getKey() + "`.", "verified", sourceRef(sourceType, sourceId)))
                    .toList();
            groups.add(group(signalGroup.getKey(), items));
        }
        var count = groups.stream().mapToInt(ExplainableBreakdownGroupDto::count).sum();
        return aggregate(
                "Signals",
                count,
                count > 0 ? "ok" : "unknown",
                count > 0 ? "high" : "low",
                count > 0 ? "Recognition signals available for search and matching." : "No recognition signals are documented.",
                groups,
                List.of(reason("matchSignals/transport", "Signals are read from typed matchSignals, transport and channel sections.", "strong")),
                count > 0 ? List.of() : List.of("No recognition signals documented."),
                List.of(sourceRef(sourceType, sourceId)),
                "signal",
                idsFromGroups(groups)
        );
    }

    private ExplainableAggregateDto handoffAggregate(
            String sourceType,
            String sourceId,
            OperationalContextEntry entry
    ) {
        var items = new ArrayList<ExplainableBreakdownItemDto>();
        var warnings = new ArrayList<String>();
        var target = handoffTarget(entry);
        if (StringUtils.hasText(target)) {
            items.add(item("target", target, "handoff-target", "Handoff target is defined.", "verified", sourceRef(sourceType, sourceId)));
        } else {
            warnings.add("Handoff target is missing.");
            items.add(item("target", "Missing target", "handoff-target", "No handoff target or handoff hint is defined.", "missing", sourceRef(sourceType, sourceId)));
        }
        var requiredEvidence = handoffRequiredEvidence(entry);
        if (requiredEvidence.isEmpty()) {
            warnings.add("Handoff required evidence is empty.");
            items.add(item("requiredEvidence", "Missing required evidence", "handoff-evidence", "No required evidence checklist is defined.", "missing", sourceRef(sourceType, sourceId)));
        } else {
            requiredEvidence.forEach(evidence -> items.add(item("requiredEvidence:" + evidence, evidence, "handoff-evidence", "Evidence required before handoff.", "verified", sourceRef(sourceType, sourceId))));
        }

        return aggregate(
                "Handoff",
                items.size(),
                warnings.isEmpty() ? "ok" : "warning",
                warnings.isEmpty() ? "high" : "medium",
                warnings.isEmpty() ? "Handoff target and checklist are available." : "Handoff information needs review.",
                List.of(group("Handoff checklist", items)),
                List.of(reason("handoffHints", "Handoff readiness is derived from typed handoffHints.", "strong")),
                warnings,
                List.of(sourceRef(sourceType, sourceId)),
                "handoff",
                ids(items)
        );
    }

    private ExplainableAggregateDto validationAggregate(
            String entityType,
            String entityId,
            List<ValidationFindingDto> findings
    ) {
        var entityFindings = findingsFor(entityType, entityId, findings);
        var items = entityFindings.stream()
                .map(finding -> item(finding.id(), finding.title(), "validation", finding.detail(), finding.severity().equals("error") ? "conflicting" : "needs-review", finding.sourceRefs()))
                .toList();
        return aggregate(
                "Validation",
                items.size(),
                aggregateSeverity(entityFindings),
                "high",
                items.isEmpty() ? "No validation findings for this entity." : "Validation findings need review.",
                List.of(group("Findings", items)),
                List.of(reason("Backend validation", "Validation findings are generated by the operational context API.", "strong")),
                List.of(),
                List.of(sourceRef(entityType, entityId)),
                "validation",
                ids(items)
        );
    }

    private ExplainableAggregateDto openQuestionAggregate(
            String entityType,
            String entityId,
            List<OpenQuestionDto> questions
    ) {
        var entityQuestions = openQuestionsFor(entityType, entityId, questions);
        var items = entityQuestions.stream()
                .map(question -> item(question.id(), question.question(), "open-question", "Question is listed in " + question.sourceFile() + ".", "needs-review", new SourceReferenceDto(question.sourceFile(), "", question.entityId())))
                .toList();
        return aggregate(
                "Open questions",
                items.size(),
                items.isEmpty() ? "ok" : "warning",
                "high",
                items.isEmpty() ? "No open questions for this entity." : "Open questions need catalog owner review.",
                List.of(group("Questions", items)),
                List.of(reason("Catalog questions", "Open questions are parsed from catalog files.", "strong")),
                List.of(),
                List.of(sourceRef(entityType, entityId)),
                "open-question",
                ids(items)
        );
    }

    private ExplainableAggregateDto moduleAggregate(
            String sourceType,
            String sourceId,
            OperationalContextRepository repository
    ) {
        var items = repository.modules().stream()
                .map(module -> {
                    var moduleId = firstDefined(module.id(), module.moduleId(), module.name(), "module");
                    var label = firstDefined(module.name(), moduleId);
                    var reason = "Module paths/packages/classes: " + String.join(", ", distinct(combineValues(
                            module.sourceRoots(),
                            module.importantPaths(),
                            module.source().paths(),
                            module.source().packages(),
                            module.matchSignals().valuesForKeys("packagePrefixes", "paths", "pathHints", "classHints")
                    )));
                    return item(moduleId, label, "module", reason, "verified", sourceRef(sourceType, sourceId));
                })
                .toList();
        return aggregate("Modules", items.size(), items.isEmpty() ? "unknown" : "ok", "high", tooltip("Modules", items.size(), "Repository modules describe code search slices."), List.of(group("Modules", items)), List.of(reason("modules", "Modules are declared on the repository.", "strong")), List.of(), List.of(sourceRef(sourceType, sourceId)), "module", ids(items));
    }

    private ExplainableAggregateDto stepAggregate(
            String sourceType,
            String sourceId,
            OperationalContextProcess process
    ) {
        var items = process.steps().stream()
                .map(step -> item(stepId(step), firstDefined(step.name(), step.id(), "Step"), "process-step", "Step is declared in the process definition.", "verified", sourceRef(sourceType, sourceId)))
                .toList();
        return aggregate("Steps", items.size(), items.isEmpty() ? "unknown" : "ok", "high", tooltip("Steps", items.size(), "Process steps describe the operational flow."), List.of(group("Steps", items)), List.of(reason("processSteps", "Steps are parsed from typed process steps.", "strong")), List.of(), List.of(sourceRef(sourceType, sourceId)), "process-step", ids(items));
    }

    private ExplainableAggregateDto relationAggregate(
            String sourceType,
            String sourceId,
            OperationalContextBoundedContext context
    ) {
        var items = context.relations().stream()
                .map(relation -> item(relationId(relation), relationLabel(relation), "relation", "Relation is declared on the bounded context.", "verified", sourceRef(sourceType, sourceId)))
                .toList();
        return aggregate("Relations", items.size(), items.isEmpty() ? "unknown" : "ok", "high", tooltip("Relations", items.size(), "Relations explain context dependencies."), List.of(group("Relations", items)), List.of(reason("relations", "Relations are parsed from typed bounded context relations.", "strong")), List.of(), List.of(sourceRef(sourceType, sourceId)), "relation", ids(items));
    }

    private ExplainableAggregateDto aggregate(
            String label,
            int count,
            String severity,
            String confidence,
            String tooltip,
            List<ExplainableBreakdownGroupDto> groups,
            List<ExplanationReasonDto> reasons,
            List<String> warnings,
            List<SourceReferenceDto> sourceRefs,
            String detailsType,
            List<String> detailsIds
    ) {
        return new ExplainableAggregateDto(
                label,
                count,
                severity,
                confidence,
                tooltip,
                groups,
                reasons,
                warnings,
                sourceRefs,
                detailsType,
                detailsIds
        );
    }

    private List<ExplainableBreakdownGroupDto> relatedGroups(
            CatalogView view,
            String type,
            OperationalContextEntry entity
    ) {
        var id = entity.id();
        return switch (type) {
            case SYSTEM -> List.of(
                    idAggregate("Repositories", type, id, REPOSITORY, repositoryIds(entity), view.repositoriesById(), "System lists these repositories.").groups().get(0),
                    idAggregate("Processes", type, id, PROCESS, processIds(entity), view.processesById(), "System lists these processes.").groups().get(0),
                    idAggregate("Integrations", type, id, INTEGRATION, integrationIdsForSystem(view, id), view.integrationsById(), "Integrations reference this system.").groups().get(0)
            );
            case REPOSITORY -> List.of(
                    idAggregate("Systems", type, id, SYSTEM, systemIds(entity), view.systemsById(), "Repository lists these systems.").groups().get(0),
                    idAggregate("Processes", type, id, PROCESS, processIds(entity), view.processesById(), "Repository lists these processes.").groups().get(0),
                    repositoryCodeSearchScopesAggregate(view, (OperationalContextRepository) entity).groups().get(0)
            );
            case PROCESS -> {
                var process = (OperationalContextProcess) entity;
                yield List.of(
                        idAggregate("Systems", type, id, SYSTEM, processSystemIds(process), view.systemsById(), "Process lists these systems.").groups().get(0),
                        idAggregate("External systems", type, id, SYSTEM, processExternalSystemIds(process), view.systemsById(), "Process lists these external systems.").groups().get(0)
                );
            }
            case INTEGRATION -> {
                var integration = (OperationalContextIntegration) entity;
                yield List.of(
                        idAggregate("Systems", type, id, SYSTEM, integrationSystems(integration), view.systemsById(), "Integration connects these systems.").groups().get(0),
                        idAggregate("Processes", type, id, PROCESS, processIds(entity), view.processesById(), "Integration lists these processes.").groups().get(0)
                );
            }
            case BOUNDED_CONTEXT -> List.of(
                    idAggregate("Systems", type, id, SYSTEM, systemIds(entity), view.systemsById(), "Context lists these systems.").groups().get(0),
                    idAggregate("Repositories", type, id, REPOSITORY, repositoryIds(entity), view.repositoriesById(), "Context lists these repositories.").groups().get(0)
            );
            case TEAM -> {
                var team = (OperationalContextTeam) entity;
                yield List.of(
                        idAggregate("Owned systems", type, id, SYSTEM, teamOwnedIds(team, SYSTEM), view.systemsById(), "Team owns these systems.").groups().get(0),
                        idAggregate("Owned repositories", type, id, REPOSITORY, teamOwnedIds(team, REPOSITORY), view.repositoriesById(), "Team owns these repositories.").groups().get(0)
                );
            }
            default -> List.of();
        };
    }

    private void addSearchResults(
            List<OperationalContextSearchResultDto> results,
            String type,
            List<? extends OperationalContextEntry> entries,
            String normalizedQuery
    ) {
        for (var entry : entries) {
            var fields = searchFields(entry);
            var matchedFields = matchingFields(fields, normalizedQuery);
            if (matchedFields.isEmpty()) {
                continue;
            }
            var confidence = searchConfidence(fields, matchedFields, normalizedQuery);
            results.add(new OperationalContextSearchResultDto(
                    type,
                    entry.id(),
                    displayLabel(type, entry.id(), entry),
                    detailSubtitle(type, entry),
                    confidence,
                    matchedFields,
                    searchWhy(confidence, matchedFields),
                    Map.of("detailsType", type, "detailsId", entry.id(), "detailsUrl", "/api/operational-context/entities/" + type + "/" + entry.id())
            ));
        }
    }

    private void addCodeSearchScopeSearchResults(
            List<OperationalContextSearchResultDto> results,
            List<OperationalContextRepositorySearchScope> scopes,
            String normalizedQuery
    ) {
        for (var scope : scopes) {
            var fields = orderedMap(
                    "id", scope.id(),
                    "name", scope.name(),
                    "lifecycleStatus", scope.lifecycleStatus(),
                    "systems", scope.target().systems(),
                    "runtimeComponents", scope.target().runtimeComponents(),
                    "deploymentComponents", scope.target().deploymentComponents(),
                    "processes", scope.target().processes(),
                    "boundedContexts", scope.target().boundedContexts(),
                    "integrations", scope.target().integrations(),
                    "repositories", scope.repositories().stream().map(OperationalContextRepositorySearchRepository::repoId).toList(),
                    "roles", scope.repositories().stream().map(OperationalContextRepositorySearchRepository::role).toList(),
                    "packagePrefix", scope.packagePrefixes(),
                    "classHint", scope.classHints(),
                    "endpointPrefixes", scope.endpointHints(),
                    "queues", scope.queueTopicHints(),
                    "schemas", scope.databaseHints().schemas(),
                    "tables", scope.databaseHints().tables(),
                    "workflows", scope.workflowHints().workflowNames()
            );
            var matchedFields = matchingFields(fields, normalizedQuery);
            if (matchedFields.isEmpty()) {
                continue;
            }
            var confidence = searchConfidence(fields, matchedFields, normalizedQuery);
            results.add(new OperationalContextSearchResultDto(
                    CODE_SEARCH_SCOPE,
                    scope.id(),
                    codeSearchScopeName(scope),
                    "repositories: " + String.join(", ", scope.repositories().stream()
                            .filter(OperationalContextRepositorySearchRepository::include)
                            .map(OperationalContextRepositorySearchRepository::repoId)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .toList()),
                    confidence,
                    matchedFields,
                    searchWhy(confidence, matchedFields),
                    Map.of("detailsType", CODE_SEARCH_SCOPE, "detailsId", scope.id(), "detailsUrl", "/api/operational-context/entities/" + CODE_SEARCH_SCOPE + "/" + scope.id())
            ));
        }
    }

    private void addGlossarySearchResults(
            List<OperationalContextSearchResultDto> results,
            List<OperationalContextGlossaryTerm> terms,
            String normalizedQuery
    ) {
        for (var term : terms) {
            var fields = orderedMap(
                    "id", term.id(),
                    "term", term.term(),
                    "category", term.category(),
                    "definition", term.definition(),
                    "matchSignals", term.matchSignals(),
                    "canonicalReferences", term.canonicalReferences(),
                    "synonyms", term.synonyms()
            );
            var matchedFields = matchingFields(fields, normalizedQuery);
            if (matchedFields.isEmpty()) {
                continue;
            }
            var confidence = searchConfidence(fields, matchedFields, normalizedQuery);
            results.add(new OperationalContextSearchResultDto(
                    GLOSSARY_TERM,
                    term.id(),
                    term.term(),
                    term.category(),
                    confidence,
                    matchedFields,
                    searchWhy(confidence, matchedFields),
                    Map.of("detailsType", GLOSSARY_TERM, "detailsId", term.id(), "detailsUrl", "/api/operational-context/entities/" + GLOSSARY_TERM + "/" + term.id())
            ));
        }
    }

    private void addHandoffSearchResults(
            List<OperationalContextSearchResultDto> results,
            List<OperationalContextHandoffRule> rules,
            String normalizedQuery
    ) {
        for (var rule : rules) {
            var fields = orderedMap(
                    "id", rule.id(),
                    "title", rule.title(),
                    "routeTo", rule.routeTo(),
                    "useWhen", rule.useWhen(),
                    "requiredEvidence", rule.requiredEvidence(),
                    "expectedFirstAction", rule.expectedFirstAction(),
                    "partnerTeams", rule.partnerTeams()
            );
            var matchedFields = matchingFields(fields, normalizedQuery);
            if (matchedFields.isEmpty()) {
                continue;
            }
            var confidence = searchConfidence(fields, matchedFields, normalizedQuery);
            results.add(new OperationalContextSearchResultDto(
                    HANDOFF_RULE,
                    rule.id(),
                    rule.title(),
                    rule.routeTo(),
                    confidence,
                    matchedFields,
                    searchWhy(confidence, matchedFields),
                    Map.of("detailsType", HANDOFF_RULE, "detailsId", rule.id(), "detailsUrl", "/api/operational-context/entities/" + HANDOFF_RULE + "/" + rule.id())
            ));
        }
    }

    private Map<String, Object> searchFields(OperationalContextEntry entry) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", entry.id());
        fields.put("name", name(entry));
        fields.put("kind", entry instanceof OperationalContextSystem system ? systemKind(system) : null);
        fields.put("integrationStyle", entry instanceof OperationalContextIntegration integration ? integrationType(integration) : null);
        fields.put("purpose", summaryText(entry));
        fields.put("projectName", entry instanceof OperationalContextRepository repository ? repositoryProjectPath(repository) : null);
        fields.put("group", entry instanceof OperationalContextRepository repository ? repositoryGroup(repository) : null);
        fields.put("ownerTeamIds", ownerTeamIds(entry));
        fields.put("handoffTarget", handoffTarget(entry));
        fields.put("systems", systemIds(entry));
        fields.put("repositories", repositoryIds(entry));
        fields.put("processes", processIds(entry));
        fields.put("contexts", boundedContextIds(entry));
        fields.put("integrationSystems", entry instanceof OperationalContextIntegration integration ? integrationSystems(integration) : List.of());
        fields.put("protocols", entry instanceof OperationalContextIntegration integration ? integrationProtocols(integration) : List.of());
        fields.putAll(signalMap(entry));
        fields.put("packagePrefix", entry instanceof OperationalContextRepository repository ? packageRoots(repository) : List.of());
        fields.put("classHint", entry instanceof OperationalContextRepository repository ? entrypoints(repository) : List.of());
        fields.put("module", entry instanceof OperationalContextRepository repository ? repository.modules().stream().map(this::moduleId).toList() : List.of());
        fields.put("entityType", entryType(entry));
        return fields;
    }

    private List<String> matchingFields(Map<String, Object> fields, String normalizedQuery) {
        var matched = new ArrayList<String>();
        fields.forEach((field, value) -> {
            for (var candidate : asTextValues(value)) {
                var normalizedCandidate = normalize(candidate);
                if (StringUtils.hasText(normalizedCandidate) && normalizedCandidate.contains(normalizedQuery)) {
                    matched.add(field);
                    break;
                }
            }
        });
        return List.copyOf(new LinkedHashSet<>(matched));
    }

    private String searchConfidence(Map<String, Object> fields, List<String> matchedFields, String normalizedQuery) {
        for (var field : List.of("id", "name", "term", "title", "projectName", "handoffTarget")) {
            for (var candidate : asTextValues(fields.get(field))) {
                if (normalize(candidate).equals(normalizedQuery)) {
                    return "high";
                }
            }
        }
        if (matchedFields.stream().anyMatch(field -> Set.of("serviceNames", "containerNames", "projectNames", "repositories", "roles", "packagePrefix", "classHint", "endpointPrefixes", "endpoints", "hosts", "queues", "topics", "schemas", "tables", "term").contains(field))) {
            return "medium";
        }
        return "low";
    }

    private String searchWhy(String confidence, List<String> matchedFields) {
        return "Matched " + String.join(", ", matchedFields) + " with " + confidence + " confidence.";
    }

    private Map<String, Integer> validationCounts(List<ValidationFindingDto> findings) {
        var counts = new LinkedHashMap<String, Integer>();
        counts.put("info", 0);
        counts.put("warning", 0);
        counts.put("error", 0);
        for (var finding : findings) {
            counts.computeIfPresent(finding.severity(), (key, value) -> value + 1);
        }
        return counts;
    }

    private String catalogStatus(OperationalContextCatalog catalog, List<ValidationFindingDto> findings) {
        var total = catalog.systems().size()
                + catalog.repositories().size()
                + catalog.codeSearchScopes().size()
                + catalog.processes().size()
                + catalog.integrations().size()
                + catalog.boundedContexts().size()
                + catalog.teams().size()
                + catalog.glossaryTerms().size()
                + catalog.handoffRules().size();
        if (total == 0) {
            return "empty";
        }
        if (findings.stream().anyMatch(finding -> finding.severity().equals("error"))) {
            return "hasIssues";
        }
        if (catalog.systems().isEmpty()
                || catalog.repositories().isEmpty()
                || catalog.codeSearchScopes().isEmpty()
                || catalog.processes().isEmpty()
                || catalog.teams().isEmpty()) {
            return "partial";
        }
        return "ready";
    }

    private Map<String, OperationalContextEntry> indexById(List<? extends OperationalContextEntry> entries) {
        var index = new LinkedHashMap<String, OperationalContextEntry>();
        for (var entry : entries) {
            if (StringUtils.hasText(entry.id())) {
                index.put(entry.id(), entry);
            }
        }
        return Map.copyOf(index);
    }

    private OperationalContextEntry requireEntity(
            Map<String, OperationalContextEntry> index,
            String type,
            String id
    ) {
        var entity = index.get(id);
        if (entity == null) {
            throw new OperationalContextEntityNotFoundException(type, id);
        }
        return entity;
    }

    private String normalizeType(String type) {
        return normalize(type).replace("_", "-");
    }

    private String name(OperationalContextEntry entry) {
        return firstDefined(entry.name(), entry.shortName(), entry.id());
    }

    private String displayLabel(String type, String id, OperationalContextEntry entry) {
        if (REPOSITORY.equals(type) && entry instanceof OperationalContextRepository repository) {
            return firstDefined(repositoryProjectPath(repository), name(repository), id);
        }
        return firstDefined(name(entry), id);
    }

    private String detailSubtitle(String type, OperationalContextEntry entity) {
        return switch (type) {
            case SYSTEM -> entity instanceof OperationalContextSystem system ? firstDefined(systemKind(system), summaryText(system), "") : summaryText(entity);
            case REPOSITORY -> entity instanceof OperationalContextRepository repository ? String.join(" / ", distinct(combineValues(repositoryGroup(repository), repositoryProjectPath(repository)))) : "";
            case PROCESS, BOUNDED_CONTEXT, TEAM -> summaryText(entity);
            case INTEGRATION -> entity instanceof OperationalContextIntegration integration ? String.join(" -> ", distinct(combineValues(integrationSourceSystem(integration), String.join(", ", integrationTargetSystems(integration))))) : "";
            default -> "";
        };
    }

    private Map<String, Object> overviewFields(OperationalContextEntry entity) {
        var fields = orderedMap(
                "id", entity.id(),
                "name", entity.name(),
                "shortName", entity.shortName(),
                "purpose", entity.purpose(),
                "summary", entity.summary(),
                "systems", systemIds(entity),
                "repositories", repositoryIds(entity),
                "processes", processIds(entity),
                "boundedContexts", boundedContextIds(entity),
                "terms", termIds(entity),
                "teams", ownerTeamIds(entity),
                "handoffTarget", handoffTarget(entity),
                "requiredEvidence", handoffRequiredEvidence(entity)
        );
        if (entity instanceof OperationalContextSystem system) {
            fields.putAll(orderedMap("kind", systemKind(system), "criticality", system.criticality(), "externalOwner", system.participants().externalOwner()));
        }
        if (entity instanceof OperationalContextRepository repository) {
            fields.putAll(orderedMap("gitProjectPath", repositoryProjectPath(repository), "gitProject", repository.git().project(), "gitGroup", repositoryGroup(repository), "repositoryType", repository.repositoryType()));
        }
        if (entity instanceof OperationalContextProcess process) {
            fields.putAll(orderedMap("primarySystems", process.participants().primarySystems(), "externalSystems", process.participants().externalSystems(), "completionSignals", completionSignals(process)));
        }
        if (entity instanceof OperationalContextIntegration integration) {
            fields.putAll(orderedMap("sourceSystem", integrationSourceSystem(integration), "targetSystems", integrationTargetSystems(integration), "protocols", integrationProtocols(integration), "integrationStyle", integrationType(integration)));
        }
        return fields;
    }

    private String systemKind(OperationalContextSystem system) {
        return system.kind();
    }

    private boolean internalSystem(OperationalContextSystem system) {
        var kind = normalize(systemKind(system));
        return kind.equals("internal") || kind.startsWith("internal-") || kind.equals("api-gateway");
    }

    private String summaryText(OperationalContextEntry entry) {
        return firstDefined(entry.purpose(), entry.summary());
    }

    private String repositoryProjectPath(OperationalContextRepository repository) {
        return firstDefined(repository.git().projectPath(), repository.git().project(), repository.id());
    }

    private String repositoryGroup(OperationalContextRepository repository) {
        return repository.git().group();
    }

    private List<String> systemIds(OperationalContextEntry entry) {
        return entry.references().systems();
    }

    private List<String> repositoryIds(OperationalContextEntry entry) {
        return entry.references().repositories();
    }

    private List<String> codeSearchRepositoryIds(OperationalContextRepositorySearchScope scope) {
        return scope.repositories().stream()
                .map(OperationalContextRepositorySearchRepository::repoId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> codeSearchScopeTargetValues(OperationalContextRepositorySearchScope scope) {
        return distinct(combineValues(
                scope.target().systems(),
                scope.target().runtimeComponents(),
                scope.target().deploymentComponents(),
                scope.target().processes(),
                scope.target().boundedContexts(),
                scope.target().integrations(),
                scope.target().terms()
        ));
    }

    private List<String> processIds(OperationalContextEntry entry) {
        return entry.references().processes();
    }

    private List<String> boundedContextIds(OperationalContextEntry entry) {
        return entry.references().boundedContexts();
    }

    private List<String> termIds(OperationalContextEntry entry) {
        return entry.references().terms();
    }

    private List<String> processSystemIds(OperationalContextProcess process) {
        return distinct(combineValues(systemIds(process), process.participants().primarySystems()));
    }

    private List<String> processExternalSystemIds(OperationalContextProcess process) {
        return process.participants().externalSystems();
    }

    private List<String> completionSignals(OperationalContextProcess process) {
        return distinct(combineValues(process.processBoundary().endsWhen(), process.outcomes().successArtifacts()));
    }

    private List<String> handoffHintValues(OperationalContextEntry entry) {
        return distinct(combineValues(
                listOf(entry.handoffHints().defaultRouteLabel()),
                entry.handoffHints().firstResponderTeamIds(),
                entry.handoffHints().partnerTeamIds(),
                entry.handoffHints().requiredEvidence(),
                entry.handoffHints().expectedFirstActions(),
                entry.handoffHints().whenToRouteHere(),
                entry.handoffHints().whenToInvolveAsPartner(),
                entry.handoffHints().whenNotToRouteHere()
        ));
    }

    private String integrationSourceSystem(OperationalContextIntegration integration) {
        return integration.participants().source().system();
    }

    private List<String> integrationTargetSystems(OperationalContextIntegration integration) {
        return integration.participants().targetSystems();
    }

    private List<String> integrationSystems(OperationalContextIntegration integration) {
        return distinct(combineValues(
                listOf(integrationSourceSystem(integration)),
                integrationTargetSystems(integration),
                integration.participants().intermediarySystems(),
                systemIds(integration)
        ));
    }

    private List<String> integrationProtocols(OperationalContextIntegration integration) {
        return integration.transport().protocols();
    }

    private String integrationType(OperationalContextIntegration integration) {
        return firstDefined(integration.integrationStyle(), integration.category());
    }

    private List<String> integrationPartnerTeamIds(OperationalContextIntegration integration) {
        return distinct(combineValues(
                integration.references().teams(),
                integration.handoffHints().partnerTeamIds(),
                responsibilityTeamIds(integration)
        ));
    }

    private String handoffTarget(OperationalContextEntry entry) {
        return firstDefined(
                entry.handoffHints().defaultRouteLabel(),
                first(entry.handoffHints().firstResponderTeamIds()),
                first(entry.references().teams())
        );
    }

    private List<String> handoffRequiredEvidence(OperationalContextEntry entry) {
        return entry.handoffHints().requiredEvidence();
    }

    private List<String> ownerTeamIds(OperationalContextEntry entry) {
        return distinct(combineValues(
                entry.references().teams(),
                responsibilityTeamIds(entry)
        ));
    }

    private List<String> knownOwnerTeamIds(
            CatalogView view,
            String entityType,
            OperationalContextEntry entry
    ) {
        return distinct(combineValues(
                ownerTeamIds(entry),
                owningTeamIds(view, entityType, entry.id())
        )).stream()
                .filter(teamId -> view.teamsById().containsKey(teamId))
                .toList();
    }

    private List<String> owningTeamIds(CatalogView view, String entityType, String entityId) {
        return view.catalog().teams().stream()
                .filter(team -> teamOwnedIds(team, entityType).contains(entityId))
                .map(OperationalContextTeam::id)
                .filter(teamId -> view.teamsById().containsKey(teamId))
                .toList();
    }

    private List<String> responsibilityTeamIds(OperationalContextEntry entry) {
        var values = new LinkedHashSet<String>();
        for (var responsibility : entry.responsibilities()) {
            if (StringUtils.hasText(responsibility.teamId())) {
                values.add(responsibility.teamId());
            }
            if ("team".equals(normalize(responsibility.actorType())) && StringUtils.hasText(responsibility.actorId())) {
                values.add(responsibility.actorId());
            }
        }
        return List.copyOf(values);
    }

    private List<String> responsibilityTargetIds(OperationalContextEntry entry, String targetType) {
        var values = new LinkedHashSet<String>();
        for (var responsibility : entry.responsibilities()) {
            if (targetTypeMatches(responsibility.targetType(), targetType) && StringUtils.hasText(responsibility.targetId())) {
                values.add(responsibility.targetId());
            }
        }
        return List.copyOf(values);
    }

    private boolean targetTypeMatches(String actual, String expected) {
        var normalized = normalize(actual).replace("_", "-");
        return switch (expected) {
            case SYSTEM -> normalized.equals("system");
            case REPOSITORY -> normalized.equals("repository") || normalized.equals("repo");
            case PROCESS -> normalized.equals("process");
            case BOUNDED_CONTEXT -> normalized.equals("bounded-context") || normalized.equals("boundedcontext") || normalized.equals("context");
            case INTEGRATION -> normalized.equals("integration");
            default -> normalized.equals(expected);
        };
    }

    private List<String> teamOwnedIds(OperationalContextTeam team, String entityType) {
        return switch (entityType) {
            case SYSTEM -> distinct(combineValues(team.references().systems(), responsibilityTargetIds(team, SYSTEM)));
            case REPOSITORY -> distinct(combineValues(team.references().repositories(), responsibilityTargetIds(team, REPOSITORY)));
            case PROCESS -> distinct(combineValues(team.references().processes(), responsibilityTargetIds(team, PROCESS)));
            case BOUNDED_CONTEXT -> distinct(combineValues(team.references().boundedContexts(), responsibilityTargetIds(team, BOUNDED_CONTEXT)));
            case INTEGRATION -> distinct(combineValues(team.references().integrations(), responsibilityTargetIds(team, INTEGRATION)));
            default -> List.of();
        };
    }

    private List<String> packageRoots(OperationalContextRepository repository) {
        var values = new LinkedHashSet<String>();
        values.addAll(repository.sourceLayout().sourceRoots());
        values.addAll(repository.sourceLayout().modulePaths());
        values.addAll(repository.sourceLayout().importantPaths());
        values.addAll(repository.packagePrefixSignals());
        for (var module : repository.modules()) {
            values.addAll(module.source().paths());
            values.addAll(module.source().packages());
            values.addAll(module.sourceRoots());
            values.addAll(module.importantPaths());
            values.addAll(module.matchSignals().valuesForKeys("packagePrefixes", "paths", "pathHints"));
        }
        return List.copyOf(values);
    }

    private List<String> entrypoints(OperationalContextRepository repository) {
        var values = new LinkedHashSet<String>();
        values.addAll(repository.classHintSignals());
        values.addAll(repository.endpointPrefixSignals());
        for (var module : repository.modules()) {
            values.addAll(module.classHintSignals());
            values.addAll(module.endpointPrefixSignals());
        }
        return List.copyOf(values);
    }

    private List<String> runtimeMappings(OperationalContextRepository repository) {
        var values = new LinkedHashSet<String>();
        values.addAll(repository.references().runtimeComponents());
        values.addAll(repository.matchSignals().valuesForKeys("serviceNames", "containerNames", "projectNames", "hosts", "applicationNames"));
        return List.copyOf(values);
    }

    private List<String> runtimeSignals(OperationalContextBoundedContext context) {
        var values = new LinkedHashSet<String>();
        values.addAll(signalValues(context));
        values.addAll(context.operationalSignals().allSignals());
        return List.copyOf(values);
    }

    private List<String> signalValues(OperationalContextEntry entry) {
        return signalMap(entry).values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    private Map<String, List<String>> signalMap(OperationalContextEntry entry) {
        var signals = new LinkedHashMap<String, List<String>>();
        addSignalSection(signals, entry.matchSignals().exact());
        addSignalSection(signals, entry.matchSignals().strong());
        addSignalSection(signals, entry.matchSignals().medium());
        addSignalSection(signals, entry.matchSignals().weak());

        if (entry instanceof OperationalContextSystem system) {
            addSignalValues(signals, "serviceNames", system.deployment().serviceNames());
            addSignalValues(signals, "applicationNames", system.deployment().applicationNames());
            addSignalValues(signals, "containerNames", system.deployment().containerNames());
        }
        if (entry instanceof OperationalContextRepository repository) {
            addSignalValues(signals, "packagePrefixes", repository.packagePrefixSignals());
            addSignalValues(signals, "classHints", repository.classHintSignals());
            addSignalValues(signals, "endpointPrefixes", repository.endpointPrefixSignals());
        }
        if (entry instanceof OperationalContextIntegration integration) {
            addSignalValues(signals, "endpointPrefixes", integration.transport().http().endpointPrefixes());
            addSignalValues(signals, "endpointTemplates", integration.transport().http().endpointTemplates());
            addSignalValues(signals, "operationNames", integration.transport().http().operationNames());
            addSignalValues(signals, "hosts", integration.transport().http().hosts());
            addSignalValues(signals, "hostPatterns", integration.transport().http().hostPatterns());
            addSignalValues(signals, "clientNames", integration.transport().http().clientNames());
            addSignalValues(signals, "queues", integration.transport().messaging().queues());
            addSignalValues(signals, "topics", integration.transport().messaging().topics());
            addSignalValues(signals, "routingKeys", integration.transport().messaging().routingKeys());
            addSignalValues(signals, "datasourceNames", integration.transport().database().datasourceNames());
            for (var channel : integration.channels()) {
                addSignalValues(signals, "channels", combineValues(channel.type(), channel.name()));
                addSignalValues(signals, "channelSignals", channel.signals());
            }
        }
        if (entry instanceof OperationalContextBoundedContext context) {
            context.operationalSignals().valuesByKey().forEach((key, values) -> addSignalValues(signals, key, values));
        }
        return signals;
    }

    private void addSignalSection(Map<String, List<String>> signals, OperationalContextSignalSet section) {
        section.valuesByKey().forEach((key, values) -> addSignalValues(signals, key, values));
    }

    private void addSignalValues(Map<String, List<String>> signals, String key, Collection<String> values) {
        var cleaned = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (cleaned.isEmpty()) {
            return;
        }
        var current = new LinkedHashSet<>(signals.getOrDefault(key, List.of()));
        current.addAll(cleaned);
        signals.put(key, List.copyOf(current));
    }

    private List<String> integrationIdsForSystem(CatalogView view, String systemId) {
        return view.catalog().integrations().stream()
                .filter(integration -> integrationSystems(integration).contains(systemId))
                .map(OperationalContextIntegration::id)
                .toList();
    }

    private Set<String> glossaryIds(CatalogView view) {
        return view.catalog().glossaryTerms().stream()
                .map(OperationalContextGlossaryTerm::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<OpenQuestionDto> openQuestionsFor(
            String entityType,
            String entityId,
            List<OpenQuestionDto> questions
    ) {
        return questions.stream()
                .filter(question -> Objects.equals(entityType, question.entityType()) && Objects.equals(entityId, question.entityId()))
                .toList();
    }

    private List<ValidationFindingDto> findingsFor(
            String entityType,
            String entityId,
            List<ValidationFindingDto> findings
    ) {
        return findings.stream()
                .filter(finding -> Objects.equals(entityType, finding.entityType()) && Objects.equals(entityId, finding.entityId()))
                .toList();
    }

    private String aggregateSeverity(List<ValidationFindingDto> findings) {
        if (findings.stream().anyMatch(finding -> finding.severity().equals("error"))) {
            return "error";
        }
        if (findings.stream().anyMatch(finding -> finding.severity().equals("warning"))) {
            return "warning";
        }
        return findings.isEmpty() ? "ok" : "warning";
    }

    private String aggregateSeverity(Collection<ValidationFindingDto> findings) {
        return aggregateSeverity(List.copyOf(findings));
    }

    private SourceReferenceDto sourceRef(String entityType, String entityId) {
        return new SourceReferenceDto(sourceFile(entityType), entityIdPath(entityType, entityId), entityId);
    }

    private String sourceFile(String entityType) {
        return SOURCE_FILES.getOrDefault(entityType, "operational-context");
    }

    private String entityIdPath(String entityType, String entityId) {
        if (!StringUtils.hasText(entityId)) {
            return "";
        }
        return switch (entityType) {
            case SYSTEM -> "systems[id=" + entityId + "]";
            case REPOSITORY -> "repositories[id=" + entityId + "]";
            case CODE_SEARCH_SCOPE -> "codeSearchScopes[id=" + entityId + "]";
            case PROCESS -> "processes[id=" + entityId + "]";
            case INTEGRATION -> "integrations[id=" + entityId + "]";
            case BOUNDED_CONTEXT -> "boundedContexts[id=" + entityId + "]";
            case TEAM -> "teams[id=" + entityId + "]";
            default -> entityId;
        };
    }

    private ExplainableBreakdownItemDto item(
            String id,
            String label,
            String type,
            String reason,
            String status,
            SourceReferenceDto sourceRef
    ) {
        return item(id, label, type, reason, status, List.of(sourceRef));
    }

    private ExplainableBreakdownItemDto item(
            String id,
            String label,
            String type,
            String reason,
            String status,
            List<SourceReferenceDto> sourceRefs
    ) {
        return new ExplainableBreakdownItemDto(id, label, type, reason, status, sourceRefs);
    }

    private ExplainableBreakdownGroupDto group(String label, List<ExplainableBreakdownItemDto> items) {
        return new ExplainableBreakdownGroupDto(label, items.size(), items);
    }

    private ExplanationReasonDto reason(String label, String detail, String strength) {
        return new ExplanationReasonDto(label, detail, strength);
    }

    private List<SourceReferenceDto> sourceRefs(List<ExplainableBreakdownItemDto> items) {
        return items.stream()
                .flatMap(item -> item.sourceRefs().stream())
                .distinct()
                .toList();
    }

    private List<String> ids(List<ExplainableBreakdownItemDto> items) {
        return items.stream()
                .map(ExplainableBreakdownItemDto::id)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> idsFromGroups(List<ExplainableBreakdownGroupDto> groups) {
        return groups.stream()
                .flatMap(group -> group.items().stream())
                .map(ExplainableBreakdownItemDto::id)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @SafeVarargs
    private final List<ExplainableBreakdownGroupDto> combineGroups(
            List<ExplainableBreakdownGroupDto>... groupLists
    ) {
        var groups = new ArrayList<ExplainableBreakdownGroupDto>();
        for (var groupList : groupLists) {
            groups.addAll(groupList);
        }
        return List.copyOf(groups);
    }

    private String tooltip(String label, int count, String reason) {
        return label + ": " + count + ". " + reason;
    }

    private List<String> combineValues(String... values) {
        var combined = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                combined.add(value);
            }
        }
        return List.copyOf(combined);
    }

    @SafeVarargs
    private final List<String> combineValues(Collection<String>... valueLists) {
        var combined = new LinkedHashSet<String>();
        for (var values : valueLists) {
            for (var value : values) {
                if (StringUtils.hasText(value)) {
                    combined.add(value);
                }
            }
        }
        return List.copyOf(combined);
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList()));
    }

    private List<String> listOf(String value) {
        return StringUtils.hasText(value) ? List.of(value) : List.of();
    }

    private String first(List<String> values) {
        return values.stream().findFirst().orElse("");
    }

    private String firstDefined(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private Map<String, Object> orderedMap(Object... values) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            if (values[index] == null) {
                continue;
            }
            var key = String.valueOf(values[index]);
            var value = values[index + 1];
            if (value != null && !asTextValues(value).isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    private List<String> asTextValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            var values = new ArrayList<String>();
            for (var item : iterable) {
                values.addAll(asTextValues(item));
            }
            return values.stream().filter(StringUtils::hasText).map(String::trim).toList();
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream()
                    .flatMap(item -> asTextValues(item).stream())
                    .toList();
        }
        var rendered = String.valueOf(value).trim();
        return StringUtils.hasText(rendered) ? List.of(rendered) : List.of();
    }

    private String rawPreview(Object value) {
        var rendered = String.valueOf(value);
        return rendered.length() > 4000 ? rendered.substring(0, 4000) + "\n..." : rendered;
    }

    private String relationLabel(OperationalContextRelation relation) {
        return String.join(" ", distinct(combineValues(relation.type(), relation.targetContextId(), relation.target(), String.join(", ", relation.via()))));
    }

    private String relationId(OperationalContextRelation relation) {
        return firstDefined(relation.targetContextId(), relation.target(), relation.type(), "relation");
    }

    private String moduleId(OperationalContextRepositoryModule module) {
        return firstDefined(module.id(), module.moduleId(), module.name(), "module");
    }

    private String stepId(OperationalContextProcessStep step) {
        return firstDefined(step.id(), step.name(), "step");
    }

    private String entryType(OperationalContextEntry entry) {
        if (entry instanceof OperationalContextSystem) {
            return SYSTEM;
        }
        if (entry instanceof OperationalContextRepository) {
            return REPOSITORY;
        }
        if (entry instanceof OperationalContextProcess) {
            return PROCESS;
        }
        if (entry instanceof OperationalContextIntegration) {
            return INTEGRATION;
        }
        if (entry instanceof OperationalContextBoundedContext) {
            return BOUNDED_CONTEXT;
        }
        if (entry instanceof OperationalContextTeam) {
            return TEAM;
        }
        return "entry";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeSeverity(String severity) {
        var normalized = normalize(severity);
        if (Set.of("info", "warning", "error").contains(normalized)) {
            return normalized;
        }
        return "info";
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "error" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private int confidenceRank(String confidence) {
        return switch (confidence) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private String slug(String value) {
        var slug = normalize(value)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 96) {
            return slug.substring(0, 96).replaceAll("-$", "");
        }
        return slug;
    }

    private List<String> warnings(String routeTo, List<String> requiredEvidence) {
        var warnings = new ArrayList<String>();
        if (!StringUtils.hasText(routeTo)) {
            warnings.add("Route target is missing.");
        }
        if (requiredEvidence.isEmpty()) {
            warnings.add("Required evidence checklist is empty.");
        }
        return warnings;
    }

    private List<EntryPointer> allHandoffEntries(CatalogView view) {
        var entries = new ArrayList<EntryPointer>();
        view.catalog().systems().forEach(entry -> entries.add(new EntryPointer(SYSTEM, entry)));
        view.catalog().repositories().forEach(entry -> entries.add(new EntryPointer(REPOSITORY, entry)));
        view.catalog().integrations().forEach(entry -> entries.add(new EntryPointer(INTEGRATION, entry)));
        view.catalog().teams().forEach(entry -> entries.add(new EntryPointer(TEAM, entry)));
        return entries;
    }

    private List<EntryPointer> allCatalogEntries(CatalogView view) {
        var entries = new ArrayList<EntryPointer>();
        view.catalog().systems().forEach(entry -> entries.add(new EntryPointer(SYSTEM, entry)));
        view.catalog().repositories().forEach(entry -> entries.add(new EntryPointer(REPOSITORY, entry)));
        view.catalog().processes().forEach(entry -> entries.add(new EntryPointer(PROCESS, entry)));
        view.catalog().integrations().forEach(entry -> entries.add(new EntryPointer(INTEGRATION, entry)));
        view.catalog().boundedContexts().forEach(entry -> entries.add(new EntryPointer(BOUNDED_CONTEXT, entry)));
        view.catalog().teams().forEach(entry -> entries.add(new EntryPointer(TEAM, entry)));
        return entries;
    }

    private record CatalogView(
            OperationalContextCatalog catalog,
            Map<String, OperationalContextEntry> systemsById,
            Map<String, OperationalContextEntry> repositoriesById,
            Map<String, OperationalContextEntry> processesById,
            Map<String, OperationalContextEntry> integrationsById,
            Map<String, OperationalContextEntry> contextsById,
            Map<String, OperationalContextEntry> teamsById,
            List<OpenQuestionDto> openQuestions,
            List<ValidationFindingDto> validationFindings
    ) {

        CatalogView withValidation(List<ValidationFindingDto> validationFindings) {
            return new CatalogView(
                    catalog,
                    systemsById,
                    repositoriesById,
                    processesById,
                    integrationsById,
                    contextsById,
                    teamsById,
                    openQuestions,
                    validationFindings
            );
        }
    }

    private record EntryPointer(
            String type,
            OperationalContextEntry entry
    ) {
    }
}
