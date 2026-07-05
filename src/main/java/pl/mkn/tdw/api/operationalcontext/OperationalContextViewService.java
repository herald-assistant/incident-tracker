package pl.mkn.tdw.api.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownGroupDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownItemDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableValueDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplanationReasonDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OpenQuestionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextBoundedContextRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextCodeSearchScopeRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextDetailSectionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityRelationsReadModelDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextExplainabilitySectionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextGlossaryRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextHandoffRuleRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextIntegrationRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProcessRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextRepositoryRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextResolvedOwnerDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextResolvedOwnershipDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSearchResultDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextTeamRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.SourceReferenceDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ValidationFindingDto;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModelBuilder;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegrationParticipant;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextMatchSignals;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRelation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndexBuilder;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextReadModelValidator;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipRequest;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.Owner;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            CODE_SEARCH_SCOPE, "code-search-scopes.yml",
            PROCESS, "processes.yml",
            INTEGRATION, "integrations.yml",
            BOUNDED_CONTEXT, "bounded-contexts.yml",
            TEAM, "teams.yml",
            GLOSSARY_TERM, "glossary.md",
            HANDOFF_RULE, "handoff-rules.md"
    );

    private final OperationalContextPort operationalContextPort;
    private final OperationalContextRelationIndexBuilder relationIndexBuilder =
            new OperationalContextRelationIndexBuilder();
    private final OperationalContextReadModelValidator readModelValidator =
            new OperationalContextReadModelValidator(relationIndexBuilder);
    private final OperationalContextCodeSearchReadModelBuilder codeSearchReadModelBuilder =
            new OperationalContextCodeSearchReadModelBuilder(relationIndexBuilder);
    private final OperationalContextProfiledReadModelMapper profiledReadModelMapper =
            new OperationalContextProfiledReadModelMapper();
    private final OperationalContextOwnershipResolver ownershipResolver =
            new OperationalContextOwnershipResolver();

    public OperationalContextSummaryDto summary() {
        var view = view();
        var validationCounts = validationCounts(view.validationFindings());
        var healthCards = List.of(
                countCard("Systems", view.catalog().systems().size(), SYSTEM),
                countCard("Repositories", view.catalog().repositories().size(), REPOSITORY),
                countCard("Code search scopes", view.catalog().codeSearchScopes().size(), CODE_SEARCH_SCOPE),
                countCard("Processes", view.catalog().processes().size(), PROCESS),
                countCard("Integrations", view.catalog().integrations().size(), INTEGRATION),
                countCard("Bounded contexts", view.catalog().boundedContexts().size(), BOUNDED_CONTEXT),
                countCard("Teams", view.catalog().teams().size(), TEAM),
                countCard("Open questions", view.openQuestions().size(), "open-question"),
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
                validationCounts,
                catalogStatus(validationCounts),
                healthCards
        );
    }

    public Object summary(String profile) {
        var expanded = summary();
        return profiledReadModelMapper.expandedProfile(profile)
                ? expanded
                : profiledReadModelMapper.summary(expanded, profile);
    }

    public List<OperationalContextSystemRowDto> systems() {
        var view = view();
        return view.catalog().systems().stream().map(system -> systemRow(view, system)).toList();
    }

    public List<OperationalContextRepositoryRowDto> repositories() {
        var view = view();
        return view.catalog().repositories().stream().map(repository -> repositoryRow(view, repository)).toList();
    }

    public List<OperationalContextCodeSearchScopeRowDto> codeSearchScopes() {
        var view = view();
        return view.catalog().codeSearchScopes().stream().map(scope -> codeSearchScopeRow(view, scope)).toList();
    }

    public List<OperationalContextProcessRowDto> processes() {
        var view = view();
        return view.catalog().processes().stream().map(process -> processRow(view, process)).toList();
    }

    public List<OperationalContextIntegrationRowDto> integrations() {
        var view = view();
        return view.catalog().integrations().stream().map(integration -> integrationRow(view, integration)).toList();
    }

    public List<OperationalContextBoundedContextRowDto> boundedContexts() {
        var view = view();
        return view.catalog().boundedContexts().stream().map(context -> boundedContextRow(view, context)).toList();
    }

    public List<OperationalContextTeamRowDto> teams() {
        var view = view();
        return view.catalog().teams().stream().map(team -> teamRow(view, team)).toList();
    }

    public List<OperationalContextGlossaryRowDto> glossary() {
        return view().catalog().glossaryTerms().stream()
                .map(term -> new OperationalContextGlossaryRowDto(
                        term.id(),
                        term.term(),
                        term.category(),
                        term.definition(),
                        valueAggregate("Match signals", GLOSSARY_TERM, term.id(), term.matchSignals(), "Signals listed on glossary term.", "signal"),
                        valueAggregate("Canonical references", GLOSSARY_TERM, term.id(), term.canonicalReferences(), "Canonical references listed on glossary term.", "reference")
                ))
                .toList();
    }

    public List<OperationalContextHandoffRuleRowDto> handoffRules() {
        return view().catalog().handoffRules().stream()
                .map(rule -> new OperationalContextHandoffRuleRowDto(
                        rule.id(),
                        rule.title(),
                        valueAggregate("Use when", HANDOFF_RULE, rule.id(), rule.useWhen(), "Rule conditions.", "condition"),
                        valueAggregate("Required evidence", HANDOFF_RULE, rule.id(), rule.requiredEvidence(), "Evidence needed for handoff.", "evidence"),
                        first(rule.expectedFirstAction())
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
        addEntrySearchResults(results, view, SYSTEM, view.catalog().systems(), normalizedQuery);
        addEntrySearchResults(results, view, REPOSITORY, view.catalog().repositories(), normalizedQuery);
        addCodeSearchScopeSearchResults(results, view, normalizedQuery);
        addEntrySearchResults(results, view, PROCESS, view.catalog().processes(), normalizedQuery);
        addEntrySearchResults(results, view, INTEGRATION, view.catalog().integrations(), normalizedQuery);
        addEntrySearchResults(results, view, BOUNDED_CONTEXT, view.catalog().boundedContexts(), normalizedQuery);
        addEntrySearchResults(results, view, TEAM, view.catalog().teams(), normalizedQuery);
        addGlossarySearchResults(results, view, normalizedQuery);
        addHandoffSearchResults(results, view, normalizedQuery);
        return results.stream()
                .sorted(Comparator.comparingInt((OperationalContextSearchResultDto result) -> confidenceRank(result.confidence()))
                        .thenComparing(OperationalContextSearchResultDto::label))
                .toList();
    }

    public Object search(String query, String profile) {
        var expanded = search(query);
        return profiledReadModelMapper.expandedProfile(profile)
                ? expanded
                : profiledReadModelMapper.search(query, expanded, profile);
    }

    public OperationalContextEntityDetailDto entity(String type, String id) {
        var view = view();
        return switch (normalizeType(type)) {
            case SYSTEM -> entryDetail(view, SYSTEM, require(view.systemsById(), SYSTEM, id));
            case REPOSITORY -> repositoryDetail(view, require(view.repositoriesById(), REPOSITORY, id));
            case CODE_SEARCH_SCOPE -> codeSearchScopeDetail(view, id);
            case PROCESS -> processDetail(view, require(view.processesById(), PROCESS, id));
            case INTEGRATION -> integrationDetail(view, require(view.integrationsById(), INTEGRATION, id));
            case BOUNDED_CONTEXT -> entryDetail(view, BOUNDED_CONTEXT, require(view.contextsById(), BOUNDED_CONTEXT, id));
            case TEAM -> entryDetail(view, TEAM, require(view.teamsById(), TEAM, id));
            case GLOSSARY_TERM -> glossaryDetail(view, id);
            case HANDOFF_RULE -> handoffRuleDetail(view, id);
            default -> throw new OperationalContextEntityNotFoundException(type, id);
        };
    }

    public Object entity(String type, String id, String profile) {
        var expanded = entity(type, id);
        return profiledReadModelMapper.expandedProfile(profile)
                ? expanded
                : profiledReadModelMapper.entity(expanded, profile);
    }

    public OperationalContextEntityRelationsReadModelDto entityRelationsReadModel(String type, String id) {
        var view = view();
        var entityType = normalizeReadModelType(type);
        requireReadModelEntity(view, entityType, id);
        var relations = view.relationIndex().entityRelations(entityType, id);
        return new OperationalContextEntityRelationsReadModelDto(
                "operational-context.entity-relations",
                1,
                relations.entity(),
                relations.outgoingRelations(),
                relations.incomingRelations(),
                relations.neighbors(),
                view.relationIndex().validationFindings()
        );
    }

    public Object entityRelationsReadModel(String type, String id, String profile) {
        var expanded = entityRelationsReadModel(type, id);
        return profiledReadModelMapper.expandedProfile(profile)
                ? expanded
                : profiledReadModelMapper.relations(expanded, profile);
    }

    public OperationalContextCodeSearchReadModel codeSearchReadModel(String type, String id) {
        var view = view();
        var entityType = normalizeReadModelType(type);
        requireReadModelEntity(view, entityType, id);
        return codeSearchReadModelBuilder.buildForEntity(view.catalog(), entityType, id);
    }

    public Object codeSearchReadModel(String type, String id, String profile) {
        var expanded = codeSearchReadModel(type, id);
        return profiledReadModelMapper.expandedProfile(profile)
                ? expanded
                : profiledReadModelMapper.codeSearch(expanded, profile);
    }

    private OperationalContextSystemRowDto systemRow(CatalogView view, OperationalContextSystem system) {
        var resolvedOwnership = resolvedOwnership(view, ownershipRequest(system));
        return new OperationalContextSystemRowDto(
                system.id(),
                system.label(),
                system.kind(),
                ownerValue(resolvedOwnership, SYSTEM, system.id()),
                resolvedOwnership,
                summaryText(system),
                referenceAggregate(view, SYSTEM, system.id(), system.references()),
                signalAggregate(SYSTEM, system.id(), system.matchSignals()),
                resolvedOwnershipAggregate(SYSTEM, system.id(), resolvedOwnership),
                validationAggregate(SYSTEM, system.id(), view.validationFindings()),
                openQuestionAggregate(SYSTEM, system.id(), view.openQuestions())
        );
    }

    private OperationalContextRepositoryRowDto repositoryRow(CatalogView view, OperationalContextRepository repository) {
        var resolvedOwnership = resolvedOwnership(view, ownershipRequest(repository));
        return new OperationalContextRepositoryRowDto(
                repository.id(),
                firstNonBlank(repository.git().projectPath(), repository.git().project()),
                repository.git().group(),
                ownerValue(resolvedOwnership, REPOSITORY, repository.id()),
                resolvedOwnership,
                idAggregate("Systems", REPOSITORY, repository.id(), SYSTEM, repository.references().systems(), view.systemsById()),
                idAggregate("Contexts", REPOSITORY, repository.id(), BOUNDED_CONTEXT, repository.references().boundedContexts(), view.contextsById()),
                idAggregate("Processes", REPOSITORY, repository.id(), PROCESS, repository.references().processes(), view.processesById()),
                idAggregate("Integrations", REPOSITORY, repository.id(), INTEGRATION, repository.references().integrations(), view.integrationsById()),
                repositoryCodeSearchScopesAggregate(view, repository),
                repositoryCodeSearchRolesAggregate(view, repository),
                resolvedOwnershipAggregate(REPOSITORY, repository.id(), resolvedOwnership),
                validationAggregate(REPOSITORY, repository.id(), view.validationFindings())
        );
    }

    private OperationalContextCodeSearchScopeRowDto codeSearchScopeRow(CatalogView view, OperationalContextRepositorySearchScope scope) {
        return new OperationalContextCodeSearchScopeRowDto(
                scope.id(),
                firstNonBlank(scope.name(), scope.id()),
                scope.scopeType(),
                scope.lifecycleStatus(),
                codeSearchScopeTargetAggregate(view, scope),
                codeSearchScopeRepositoriesAggregate(view, scope),
                valueAggregate("Limitations", CODE_SEARCH_SCOPE, scope.id(), scope.limitations(), "Known limitations for this scope.", "limitation"),
                validationAggregate(CODE_SEARCH_SCOPE, scope.id(), view.validationFindings())
        );
    }

    private OperationalContextProcessRowDto processRow(CatalogView view, OperationalContextProcess process) {
        var resolvedOwnership = resolvedOwnership(view, ownershipRequest(process));
        return new OperationalContextProcessRowDto(
                process.id(),
                process.label(),
                ownerValue(resolvedOwnership, PROCESS, process.id()),
                resolvedOwnership,
                summaryText(process),
                idAggregate("Systems", PROCESS, process.id(), SYSTEM, process.references().systems(), view.systemsById()),
                idAggregate("External systems", PROCESS, process.id(), SYSTEM, process.participants().externalSystems(), view.systemsById()),
                idAggregate("Repositories", PROCESS, process.id(), REPOSITORY, process.references().repositories(), view.repositoriesById()),
                idAggregate("Contexts", PROCESS, process.id(), BOUNDED_CONTEXT, process.references().boundedContexts(), view.contextsById()),
                valueAggregate("Steps", PROCESS, process.id(), process.steps().stream().map(step -> firstNonBlank(step.name(), step.id())).toList(), "Process steps listed in catalog.", "step"),
                valueAggregate("Completion signals", PROCESS, process.id(), process.outcomes().successArtifacts(), "Success artifacts listed in catalog.", "outcome"),
                validationAggregate(PROCESS, process.id(), view.validationFindings())
        );
    }

    private OperationalContextIntegrationRowDto integrationRow(CatalogView view, OperationalContextIntegration integration) {
        var resolvedOwnership = resolvedOwnership(view, ownershipRequest(integration));
        return new OperationalContextIntegrationRowDto(
                integration.id(),
                integration.label(),
                integration.participants().source().system(),
                String.join(", ", integration.participants().targetSystems()),
                ownerValue(resolvedOwnership, INTEGRATION, integration.id()),
                resolvedOwnership,
                resolvedPartnerOwnersAggregate(INTEGRATION, integration.id(), resolvedOwnership),
                integration.category(),
                integration.integrationStyle(),
                integration.flowDirection(),
                idAggregate("Processes", INTEGRATION, integration.id(), PROCESS, integration.references().processes(), view.processesById()),
                idAggregate("Contexts", INTEGRATION, integration.id(), BOUNDED_CONTEXT, integration.references().boundedContexts(), view.contextsById()),
                signalAggregate(INTEGRATION, integration.id(), integration.matchSignals()),
                resolvedOwnershipAggregate(INTEGRATION, integration.id(), resolvedOwnership),
                validationAggregate(INTEGRATION, integration.id(), view.validationFindings())
        );
    }

    private OperationalContextBoundedContextRowDto boundedContextRow(CatalogView view, OperationalContextBoundedContext context) {
        var resolvedOwnership = resolvedOwnership(view, ownershipRequest(context));
        return new OperationalContextBoundedContextRowDto(
                context.id(),
                context.label(),
                ownerValue(resolvedOwnership, BOUNDED_CONTEXT, context.id()),
                resolvedOwnership,
                summaryText(context),
                idAggregate("Systems", BOUNDED_CONTEXT, context.id(), SYSTEM, context.references().systems(), view.systemsById()),
                idAggregate("Terms", BOUNDED_CONTEXT, context.id(), GLOSSARY_TERM, context.references().terms(), glossaryMap(view)),
                valueAggregate("Relations", BOUNDED_CONTEXT, context.id(), context.relations().stream().map(this::relationLabel).toList(), "Relations listed on bounded context.", "relation"),
                validationAggregate(BOUNDED_CONTEXT, context.id(), view.validationFindings())
        );
    }

    private OperationalContextTeamRowDto teamRow(CatalogView view, OperationalContextTeam team) {
        return new OperationalContextTeamRowDto(
                team.id(),
                team.label(),
                summaryText(team),
                idAggregate("Owns systems", TEAM, team.id(), SYSTEM, team.references().systems(), view.systemsById()),
                idAggregate("Owns repositories", TEAM, team.id(), REPOSITORY, team.references().repositories(), view.repositoriesById()),
                idAggregate("Owns processes", TEAM, team.id(), PROCESS, team.references().processes(), view.processesById()),
                idAggregate("Owns contexts", TEAM, team.id(), BOUNDED_CONTEXT, team.references().boundedContexts(), view.contextsById()),
                idAggregate("Owns integrations", TEAM, team.id(), INTEGRATION, team.references().integrations(), view.integrationsById()),
                signalAggregate(TEAM, team.id(), team.matchSignals()),
                notOwnershipSourceAggregate(TEAM, team.id()),
                validationAggregate(TEAM, team.id(), view.validationFindings())
        );
    }

    private OperationalContextEntityDetailDto entryDetail(CatalogView view, String type, OperationalContextEntry entry) {
        var sections = new ArrayList<OperationalContextDetailSectionDto>();
        sections.add(section("Overview", map(
                "name", entry.label(),
                "summary", summaryText(entry),
                "aliases", entry.aliases(),
                "useFor", entry.useFor()
        )));
        sections.add(section("References", referencesMap(entry.references())));
        sections.add(section("Resolved ownership", resolvedOwnershipMap(resolvedOwnership(view, ownershipRequest(type, entry)))));
        return new OperationalContextEntityDetailDto(
                type,
                entry.id(),
                entry.label(),
                summaryText(entry),
                sections,
                relatedGroups(view, type, entry.id(), entry.references()),
                signalGroups(entry.matchSignals()),
                explainability(type, entry.id(), "Catalog entry assembled from " + sourceFile(type) + "."),
                validationFor(type, entry.id(), view.validationFindings()),
                openQuestionsFor(type, entry.id(), view.openQuestions()),
                List.of(sourceRef(type, entry.id())),
                rawPreview(entry.payload())
        );
    }

    private OperationalContextEntityDetailDto repositoryDetail(CatalogView view, OperationalContextRepository repository) {
        var detail = entryDetail(view, REPOSITORY, repository);
        var sections = new ArrayList<>(detail.overviewSections());
        sections.add(section("Git", map(
                "provider", repository.git().provider(),
                "group", repository.git().group(),
                "project", repository.git().project(),
                "projectPath", repository.git().projectPath(),
                "defaultBranch", repository.git().defaultBranch(),
                "url", repository.git().url()
        )));
        return replaceSections(detail, sections);
    }

    private OperationalContextEntityDetailDto processDetail(CatalogView view, OperationalContextProcess process) {
        var detail = entryDetail(view, PROCESS, process);
        var sections = new ArrayList<>(detail.overviewSections());
        sections.add(section("Process", map(
                "type", process.type(),
                "participants", map(
                        "actors", process.participants().actors(),
                        "primarySystems", process.participants().primarySystems(),
                        "supportingSystems", process.participants().supportingSystems(),
                        "externalSystems", process.participants().externalSystems()
                ),
                "steps", process.steps().stream()
                        .map(step -> map("id", step.id(), "name", step.name(), "type", step.type(), "summary", step.summary()))
                        .toList(),
                "failureModes", process.failureModes()
        )));
        return replaceSections(detail, sections);
    }

    private OperationalContextEntityDetailDto integrationDetail(CatalogView view, OperationalContextIntegration integration) {
        var detail = entryDetail(view, INTEGRATION, integration);
        var sections = new ArrayList<>(detail.overviewSections());
        sections.add(section("Integration", map(
                "category", integration.category(),
                "integrationStyle", integration.integrationStyle(),
                "flowDirection", integration.flowDirection(),
                "source", participantMap(integration.participants().source()),
                "targets", integration.participants().targets().stream().map(this::participantMap).toList(),
                "intermediaries", integration.participants().intermediaries().stream().map(this::participantMap).toList(),
                "finalTargets", integration.participants().finalTargets().stream().map(this::participantMap).toList(),
                "failureModes", integration.failureModes()
        )));
        return replaceSections(detail, sections);
    }

    private OperationalContextEntityDetailDto codeSearchScopeDetail(CatalogView view, String id) {
        var scope = requireScope(view, id);
        var repositories = scope.repositories().stream()
                .map(repository -> map(
                        "repoId", repository.repoId(),
                        "role", repository.role(),
                        "priority", repository.priority(),
                        "reason", repository.reason(),
                        "readFor", repository.readFor(),
                        "projectPath", view.repositoriesById().containsKey(repository.repoId())
                                ? view.repositoriesById().get(repository.repoId()).git().projectPath()
                                : null
                ))
                .toList();
        return new OperationalContextEntityDetailDto(
                CODE_SEARCH_SCOPE,
                scope.id(),
                firstNonBlank(scope.name(), scope.id()),
                scope.summary(),
                List.of(
                        section("Overview", map(
                                "scopeType", scope.scopeType(),
                                "lifecycleStatus", scope.lifecycleStatus(),
                                "summary", scope.summary(),
                                "target", map("type", scope.target().type(), "id", scope.target().id()),
                                "useFor", scope.useFor(),
                                "limitations", scope.limitations()
                        )),
                        section("Repositories", map("repositories", repositories))
                ),
                relatedGroups(view, CODE_SEARCH_SCOPE, scope.id(), scopeReferences(scope)),
                List.of(),
                explainability(CODE_SEARCH_SCOPE, scope.id(), "Code-search scope maps semantic target to repositories."),
                validationFor(CODE_SEARCH_SCOPE, scope.id(), view.validationFindings()),
                openQuestionsFor(CODE_SEARCH_SCOPE, scope.id(), view.openQuestions()),
                List.of(sourceRef(CODE_SEARCH_SCOPE, scope.id())),
                rawPreview(scope.payload())
        );
    }

    private OperationalContextEntityDetailDto glossaryDetail(CatalogView view, String id) {
        var term = view.catalog().glossaryTerms().stream()
                .filter(candidate -> normalize(candidate.id()).equals(normalize(id)))
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(GLOSSARY_TERM, id));
        return new OperationalContextEntityDetailDto(
                GLOSSARY_TERM,
                term.id(),
                term.term(),
                term.definition(),
                List.of(section("Overview", map(
                        "category", term.category(),
                        "definition", term.definition(),
                        "useInContext", term.useInContext(),
                        "doNotConfuseWith", term.doNotConfuseWith(),
                        "synonyms", term.synonyms(),
                        "notes", term.notes()
                ))),
                List.of(group("Canonical references", GLOSSARY_TERM, term.canonicalReferences(), "Glossary canonical reference.")),
                List.of(group("Match signals", "signal", term.matchSignals(), "Glossary match signal.")),
                explainability(GLOSSARY_TERM, term.id(), "Glossary term from " + sourceFile(GLOSSARY_TERM) + "."),
                validationFor(GLOSSARY_TERM, term.id(), view.validationFindings()),
                openQuestionsFor(GLOSSARY_TERM, term.id(), view.openQuestions()),
                List.of(sourceRef(GLOSSARY_TERM, term.id())),
                rawPreview(map("id", term.id(), "term", term.term()))
        );
    }

    private OperationalContextEntityDetailDto handoffRuleDetail(CatalogView view, String id) {
        var rule = view.catalog().handoffRules().stream()
                .filter(candidate -> normalize(candidate.id()).equals(normalize(id)))
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(HANDOFF_RULE, id));
        return new OperationalContextEntityDetailDto(
                HANDOFF_RULE,
                rule.id(),
                rule.title(),
                firstNonBlank(first(rule.useWhen()), first(rule.expectedFirstAction()), first(rule.notes())),
                List.of(section("Overview", map(
                        "useWhen", rule.useWhen(),
                        "doNotUseWhen", rule.doNotUseWhen(),
                        "requiredEvidence", rule.requiredEvidence(),
                        "expectedFirstAction", rule.expectedFirstAction(),
                        "notes", rule.notes()
                ))),
                relatedGroups(view, HANDOFF_RULE, rule.id(), rule.references()),
                List.of(),
                explainability(HANDOFF_RULE, rule.id(), "Handoff rule from " + sourceFile(HANDOFF_RULE) + "."),
                validationFor(HANDOFF_RULE, rule.id(), view.validationFindings()),
                openQuestionsFor(HANDOFF_RULE, rule.id(), view.openQuestions()),
                List.of(sourceRef(HANDOFF_RULE, rule.id())),
                rawPreview(map("id", rule.id(), "title", rule.title()))
        );
    }

    private CatalogView view() {
        var loaded = operationalContextPort.loadContext(OperationalContextQuery.all());
        var catalog = loaded != null ? loaded : OperationalContextCatalog.empty();
        var relationIndex = relationIndexBuilder.build(catalog);
        var validation = readModelValidator.validateCatalogContract(catalog).stream()
                .map(this::validationFinding)
                .toList();
        var openQuestions = catalog.openQuestions().stream()
                .map(this::openQuestion)
                .toList();
        return new CatalogView(
                catalog,
                indexById(catalog.systems()),
                indexById(catalog.repositories()),
                indexById(catalog.processes()),
                indexById(catalog.integrations()),
                indexById(catalog.boundedContexts()),
                indexById(catalog.teams()),
                relationIndex,
                openQuestions,
                validation
        );
    }

    private ValidationFindingDto validationFinding(ValidationFinding finding) {
        var firstRef = finding.sourceRefs().stream().findFirst().orElse(null);
        return new ValidationFindingDto(
                finding.code() + ":" + (firstRef != null ? firstRef.entityId() : "catalog"),
                normalizeSeverity(finding.severity()),
                finding.code(),
                firstRef != null ? firstRef.entityType() : null,
                firstRef != null ? firstRef.entityId() : null,
                finding.code(),
                finding.message(),
                finding.sourceRefs().stream().map(this::sourceReference).toList(),
                null,
                null
        );
    }

    private OpenQuestionDto openQuestion(OperationalContextOpenQuestion question) {
        return new OpenQuestionDto(
                question.id(),
                question.sourceFile(),
                normalizeType(question.entityType()),
                question.entityId(),
                question.question(),
                normalizeSeverity(question.severity()),
                StringUtils.hasText(question.status()) ? question.status() : "open"
        );
    }

    private void addEntrySearchResults(
            List<OperationalContextSearchResultDto> results,
            CatalogView view,
            String type,
            List<? extends OperationalContextEntry> entries,
            String normalizedQuery
    ) {
        for (var entry : entries) {
            var match = matchEntry(entry, normalizedQuery);
            if (match != null) {
                results.add(searchResult(type, entry.id(), entry.label(), summaryText(entry), match));
            }
        }
    }

    private SearchMatch matchEntry(OperationalContextEntry entry, String normalizedQuery) {
        if (normalize(entry.id()).equals(normalizedQuery) || normalize(entry.label()).equals(normalizedQuery)) {
            return new SearchMatch("high", List.of("identity"), "Exact catalog identity match.");
        }
        if (containsAny(normalizedQuery, entry.aliases(), entry.useFor(), entry.matchSignals().allValues())) {
            return new SearchMatch("high", List.of("signals"), "Matched aliases, usage or catalog signals.");
        }
        if (containsAny(normalizedQuery, textValues(entry.summary(), entry.purpose()), entry.genericSignals())) {
            return new SearchMatch("medium", List.of("summary"), "Matched catalog summary or related signals.");
        }
        if (containsAny(normalizedQuery, referencesValues(entry.references()))) {
            return new SearchMatch("medium", List.of("relations"), "Matched semantic relation ids.");
        }
        return null;
    }

    private void addCodeSearchScopeSearchResults(
            List<OperationalContextSearchResultDto> results,
            CatalogView view,
            String normalizedQuery
    ) {
        for (var scope : view.catalog().codeSearchScopes()) {
            var values = new ArrayList<String>();
            values.add(scope.id());
            values.add(scope.name());
            values.add(scope.summary());
            values.add(scope.scopeType());
            values.add(scope.target().type());
            values.add(scope.target().id());
            values.addAll(scope.useFor());
            values.addAll(scope.limitations());
            scope.repositories().forEach(repository -> {
                values.add(repository.repoId());
                values.add(repository.role());
                values.add(repository.reason());
                values.addAll(repository.readFor());
            });
            if (containsAny(normalizedQuery, values)) {
                results.add(searchResult(
                        CODE_SEARCH_SCOPE,
                        scope.id(),
                        firstNonBlank(scope.name(), scope.id()),
                        scope.summary(),
                        new SearchMatch("high", List.of("codeSearchScope"), "Matched code-search target or repository scope.")
                ));
            }
        }
    }

    private void addGlossarySearchResults(List<OperationalContextSearchResultDto> results, CatalogView view, String normalizedQuery) {
        for (var term : view.catalog().glossaryTerms()) {
            if (containsAny(normalizedQuery, textValues(term.id(), term.term(), term.definition()), term.matchSignals(), term.synonyms())) {
                results.add(searchResult(
                        GLOSSARY_TERM,
                        term.id(),
                        term.term(),
                        term.definition(),
                        new SearchMatch("medium", List.of("glossary"), "Matched glossary term.")
                ));
            }
        }
    }

    private void addHandoffSearchResults(List<OperationalContextSearchResultDto> results, CatalogView view, String normalizedQuery) {
        for (var rule : view.catalog().handoffRules()) {
            if (containsAny(
                    normalizedQuery,
                    textValues(rule.id(), rule.title()),
                    rule.useWhen(),
                    rule.requiredEvidence(),
                    rule.expectedFirstAction()
            )) {
                results.add(searchResult(
                        HANDOFF_RULE,
                        rule.id(),
                        rule.title(),
                        firstNonBlank(first(rule.useWhen()), first(rule.expectedFirstAction()), first(rule.notes())),
                        new SearchMatch("medium", List.of("handoff"), "Matched handoff rule.")
                ));
            }
        }
    }

    private OperationalContextSearchResultDto searchResult(String type, String id, String label, String subtitle, SearchMatch match) {
        return new OperationalContextSearchResultDto(
                type,
                id,
                label,
                subtitle,
                match.confidence(),
                match.fields(),
                match.why(),
                Map.of(
                        "entity", "/api/operational-context/entities/" + type + "/" + id,
                        "relations", "/api/operational-context/read-model/entities/" + type + "/" + id + "/relations",
                        "codeSearch", "/api/operational-context/read-model/entities/" + type + "/" + id + "/code-search"
                )
        );
    }

    private ExplainableAggregateDto referenceAggregate(
            CatalogView view,
            String entityType,
            String entityId,
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences references
    ) {
        var values = referencesValues(references);
        return aggregate(
                "Relations",
                values.size(),
                "info",
                "high",
                "Semantic references from catalog.",
                relatedGroups(view, entityType, entityId, references),
                List.of(reason("catalog", "References are maintained directly on the entity.", "high")),
                List.of(sourceRef(entityType, entityId)),
                "relations",
                values
        );
    }

    private List<ExplainableBreakdownGroupDto> relatedGroups(
            CatalogView view,
            String entityType,
            String entityId,
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences references
    ) {
        return nonEmptyGroups(
                groupFromIds("Systems", SYSTEM, references.systems(), view.systemsById()),
                groupFromIds("Repositories", REPOSITORY, references.repositories(), view.repositoriesById()),
                groupFromIds("Processes", PROCESS, references.processes(), view.processesById()),
                groupFromIds("Bounded contexts", BOUNDED_CONTEXT, references.boundedContexts(), view.contextsById()),
                groupFromIds("Integrations", INTEGRATION, references.integrations(), view.integrationsById()),
                groupFromIds("Terms", GLOSSARY_TERM, references.terms(), glossaryMap(view)),
                groupFromIds("Teams", TEAM, references.teams(), view.teamsById()),
                groupFromIds("Handoff rules", HANDOFF_RULE, references.handoffRules(), handoffRuleMap(view))
        );
    }

    private ExplainableAggregateDto signalAggregate(String entityType, String entityId, OperationalContextMatchSignals signals) {
        var values = signals.allValues();
        return aggregate(
                "Match signals",
                values.size(),
                values.isEmpty() ? "warning" : "info",
                values.isEmpty() ? "low" : "high",
                "Signals used to recognize this catalog entity.",
                signalGroups(signals),
                List.of(reason("matchSignals", "Catalog recognition signals.", values.isEmpty() ? "low" : "high")),
                List.of(sourceRef(entityType, entityId)),
                "signal",
                values
        );
    }

    private List<ExplainableBreakdownGroupDto> signalGroups(OperationalContextMatchSignals signals) {
        return nonEmptyGroups(
                group("Exact", "signal", signals.exact().allValues(), "Exact match signal."),
                group("Strong", "signal", signals.strong().allValues(), "Strong match signal."),
                group("Medium", "signal", signals.medium().allValues(), "Medium match signal."),
                group("Weak", "signal", signals.weak().allValues(), "Weak match signal.")
        );
    }

    private ExplainableAggregateDto notOwnershipSourceAggregate(String entityType, String entityId) {
        return aggregate(
                "Resolved handoff",
                0,
                "unknown",
                "low",
                "Team entries are not an ownership source. Resolve ownership from bounded context or system.",
                List.of(),
                List.of(reason("resolvedOwnership", "Team entries are not used as ownership input.", "low")),
                List.of(sourceRef(entityType, entityId)),
                "resolved-ownership",
                List.of()
        );
    }

    private ExplainableAggregateDto validationAggregate(String entityType, String entityId, List<ValidationFindingDto> findings) {
        var scoped = validationFor(entityType, entityId, findings);
        return aggregate(
                "Validation",
                scoped.size(),
                scoped.stream().anyMatch(finding -> "error".equals(finding.severity())) ? "error" : scoped.isEmpty() ? "info" : "warning",
                scoped.isEmpty() ? "high" : "medium",
                "Validation findings for this entity.",
                List.of(),
                List.of(reason("validation", "Relation-index validation.", scoped.isEmpty() ? "high" : "medium")),
                scoped.stream().flatMap(finding -> finding.sourceRefs().stream()).toList(),
                "validation",
                scoped.stream().map(ValidationFindingDto::id).toList()
        );
    }

    private ExplainableAggregateDto openQuestionAggregate(String entityType, String entityId, List<OpenQuestionDto> questions) {
        var scoped = openQuestionsFor(entityType, entityId, questions);
        return aggregate(
                "Open questions",
                scoped.size(),
                scoped.isEmpty() ? "info" : "warning",
                scoped.isEmpty() ? "high" : "medium",
                "Open questions for this entity.",
                List.of(),
                List.of(reason("openQuestions", "Questions from catalog maintenance.", scoped.isEmpty() ? "high" : "medium")),
                List.of(sourceRef(entityType, entityId)),
                "open-question",
                scoped.stream().map(OpenQuestionDto::id).toList()
        );
    }

    private ExplainableAggregateDto idAggregate(
            String label,
            String entityType,
            String entityId,
            String relatedType,
            List<String> ids,
            Map<String, ? extends Object> known
    ) {
        var normalized = distinct(ids);
        var missing = normalized.stream().filter(id -> !known.containsKey(id)).toList();
        return aggregate(
                label,
                normalized.size(),
                missing.isEmpty() ? "info" : "warning",
                missing.isEmpty() ? "high" : "medium",
                label + " referenced by this catalog entity.",
                List.of(group(label, relatedType, normalized, "Catalog reference.")),
                List.of(reason("references", "Semantic reference ids.", missing.isEmpty() ? "high" : "medium")),
                List.of(sourceRef(entityType, entityId)),
                relatedType,
                normalized
        );
    }

    private ExplainableAggregateDto valueAggregate(
            String label,
            String entityType,
            String entityId,
            List<String> values,
            String tooltip,
            String detailsType
    ) {
        var normalized = distinct(values);
        return aggregate(
                label,
                normalized.size(),
                normalized.isEmpty() ? "warning" : "info",
                normalized.isEmpty() ? "low" : "high",
                tooltip,
                List.of(group(label, detailsType, normalized, "Catalog value.")),
                List.of(reason("catalog", tooltip, normalized.isEmpty() ? "low" : "high")),
                List.of(sourceRef(entityType, entityId)),
                detailsType,
                normalized
        );
    }

    private ExplainableAggregateDto repositoryCodeSearchScopesAggregate(CatalogView view, OperationalContextRepository repository) {
        var scopeIds = view.catalog().codeSearchScopes().stream()
                .filter(scope -> scope.repositories().stream().anyMatch(scopeRepository -> repository.id().equals(scopeRepository.repoId())))
                .map(OperationalContextRepositorySearchScope::id)
                .toList();
        return valueAggregate("Code-search scopes", REPOSITORY, repository.id(), scopeIds, "Code-search scopes including this repository.", CODE_SEARCH_SCOPE);
    }

    private ExplainableAggregateDto repositoryCodeSearchRolesAggregate(CatalogView view, OperationalContextRepository repository) {
        var roles = view.catalog().codeSearchScopes().stream()
                .flatMap(scope -> scope.repositories().stream())
                .filter(scopeRepository -> repository.id().equals(scopeRepository.repoId()))
                .map(this::codeSearchRepositoryRoleLabel)
                .toList();
        return valueAggregate("Code-search roles", REPOSITORY, repository.id(), roles, "Repository roles in code-search scopes.", "code-search-role");
    }

    private ExplainableAggregateDto codeSearchScopeTargetAggregate(CatalogView view, OperationalContextRepositorySearchScope scope) {
        var target = scope.target().value();
        return valueAggregate("Target", CODE_SEARCH_SCOPE, scope.id(), textValues(target), "Semantic target of this code-search scope.", scope.target().type());
    }

    private ExplainableAggregateDto codeSearchScopeRepositoriesAggregate(CatalogView view, OperationalContextRepositorySearchScope scope) {
        var ids = scope.repositories().stream().map(OperationalContextRepositorySearchRepository::repoId).toList();
        return idAggregate("Repositories", CODE_SEARCH_SCOPE, scope.id(), REPOSITORY, ids, view.repositoriesById());
    }

    private ExplainableAggregateDto countCard(String label, int count, String detailsType) {
        return aggregate(
                label,
                count,
                count == 0 ? "warning" : "info",
                count == 0 ? "low" : "high",
                label + " in operational context catalog.",
                List.of(),
                List.of(reason("catalog", "Catalog entity count.", count == 0 ? "low" : "high")),
                List.of(),
                detailsType,
                List.of()
        );
    }

    private ExplainableAggregateDto validationCard(List<ValidationFindingDto> findings) {
        return aggregate(
                "Validation",
                findings.size(),
                findings.stream().anyMatch(finding -> "error".equals(finding.severity())) ? "error" : findings.isEmpty() ? "info" : "warning",
                findings.isEmpty() ? "high" : "medium",
                "Relation-index validation findings.",
                List.of(),
                List.of(reason("validation", "Relation-index validation.", findings.isEmpty() ? "high" : "medium")),
                findings.stream().flatMap(finding -> finding.sourceRefs().stream()).toList(),
                "validation",
                findings.stream().map(ValidationFindingDto::id).toList()
        );
    }

    private ExplainableAggregateDto aggregate(
            String label,
            int count,
            String severity,
            String confidence,
            String tooltip,
            List<ExplainableBreakdownGroupDto> groups,
            List<ExplanationReasonDto> reasons,
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
                List.of(),
                sourceRefs,
                detailsType,
                detailsIds
        );
    }

    private OperationalContextResolvedOwnershipDto resolvedOwnership(
            CatalogView view,
            OperationalContextOwnershipRequest request
    ) {
        return resolvedOwnership(ownershipResolver.resolve(view.catalog(), request));
    }

    private OperationalContextResolvedOwnershipDto resolvedOwnership(OperationalContextOwnershipResolution resolution) {
        return new OperationalContextResolvedOwnershipDto(
                resolution.situationType(),
                owners(resolution.primaryOwners()),
                owners(resolution.partnerOwners()),
                resolution.resolutionPath(),
                resolution.handoffReason(),
                resolution.visibilityLimits()
        );
    }

    private List<OperationalContextResolvedOwnerDto> owners(List<Owner> owners) {
        return owners.stream()
                .map(owner -> new OperationalContextResolvedOwnerDto(
                        owner.targetType(),
                        owner.targetId(),
                        owner.targetLabel(),
                        owner.ownerTeamIds(),
                        owner.ownerLabel(),
                        owner.source(),
                        owner.confidence()
                ))
                .toList();
    }

    private ExplainableValueDto<String> ownerValue(
            OperationalContextResolvedOwnershipDto resolvedOwnership,
            String entityType,
            String entityId
    ) {
        var owner = resolvedOwnership.primaryOwners().stream()
                .findFirst()
                .map(this::ownerLabel)
                .orElse(null);
        var confidence = resolvedOwnership.primaryOwners().stream()
                .findFirst()
                .map(OperationalContextResolvedOwnerDto::confidence)
                .filter(StringUtils::hasText)
                .orElse("low");
        return new ExplainableValueDto<>(
                owner,
                StringUtils.hasText(owner) ? "Resolved owner" : "Missing owner",
                confidence,
                List.of(reason(
                        "resolvedOwnership",
                        firstNonBlank(resolvedOwnership.handoffReason(), "Owner resolved from system/bounded context ownership."),
                        confidence
                )),
                resolvedOwnership.visibilityLimits(),
                List.of(sourceRef(entityType, entityId))
        );
    }

    private ExplainableAggregateDto resolvedOwnershipAggregate(
            String entityType,
            String entityId,
            OperationalContextResolvedOwnershipDto resolvedOwnership
    ) {
        var owners = new ArrayList<OperationalContextResolvedOwnerDto>();
        owners.addAll(resolvedOwnership.primaryOwners());
        owners.addAll(resolvedOwnership.partnerOwners());
        var count = owners.size();
        var hasVisibilityLimits = !resolvedOwnership.visibilityLimits().isEmpty();
        return aggregate(
                "Resolved handoff",
                count,
                hasVisibilityLimits ? "warning" : count == 0 ? "warning" : "info",
                owners.stream().map(OperationalContextResolvedOwnerDto::confidence).filter(StringUtils::hasText).findFirst().orElse("low"),
                "Ownership and handoff resolved from bounded context/system ownership.",
                nonEmptyGroups(
                        ownerGroup("Primary owners", resolvedOwnership.primaryOwners(), "Resolved primary owner."),
                        ownerGroup("Partner owners", resolvedOwnership.partnerOwners(), "Resolved partner owner."),
                        group("Resolution path", "resolution-step", resolvedOwnership.resolutionPath(), "Resolver decision path."),
                        group("Visibility limits", "visibility-limit", resolvedOwnership.visibilityLimits(), "Resolver visibility limit.")
                ),
                List.of(reason("resolvedOwnership", firstNonBlank(resolvedOwnership.handoffReason(), "Resolved ownership."), "high")),
                List.of(sourceRef(entityType, entityId)),
                "resolved-ownership",
                owners.stream()
                        .map(owner -> firstNonBlank(owner.targetId(), owner.targetLabel(), ownerLabel(owner)))
                        .toList()
        );
    }

    private ExplainableAggregateDto resolvedPartnerOwnersAggregate(
            String entityType,
            String entityId,
            OperationalContextResolvedOwnershipDto resolvedOwnership
    ) {
        return aggregate(
                "Partner owners",
                resolvedOwnership.partnerOwners().size(),
                resolvedOwnership.partnerOwners().isEmpty() ? "info" : "warning",
                resolvedOwnership.partnerOwners().isEmpty() ? "high" : "medium",
                "Partner owners resolved for a boundary handoff.",
                nonEmptyGroups(ownerGroup("Partner owners", resolvedOwnership.partnerOwners(), "Resolved partner owner.")),
                List.of(reason("resolvedOwnership", "Partner owners come from the ownership resolver.", "high")),
                List.of(sourceRef(entityType, entityId)),
                "resolved-ownership",
                resolvedOwnership.partnerOwners().stream()
                        .map(owner -> firstNonBlank(owner.targetId(), owner.targetLabel(), ownerLabel(owner)))
                        .toList()
        );
    }

    private ExplainableBreakdownGroupDto ownerGroup(
            String label,
            List<OperationalContextResolvedOwnerDto> owners,
            String reason
    ) {
        var items = owners.stream()
                .map(owner -> new ExplainableBreakdownItemDto(
                        firstNonBlank(owner.targetId(), owner.targetLabel(), ownerLabel(owner)),
                        ownerLabel(owner),
                        firstNonBlank(owner.targetType(), "owner"),
                        reason + " Source: " + firstNonBlank(owner.source(), "unknown") + ".",
                        "resolved",
                        List.of()
                ))
                .toList();
        return new ExplainableBreakdownGroupDto(label, items.size(), items);
    }

    private String ownerLabel(OperationalContextResolvedOwnerDto owner) {
        return firstNonBlank(
                owner.ownerLabel(),
                String.join(", ", owner.ownerTeamIds()),
                owner.targetLabel(),
                owner.targetId()
        );
    }

    private ExplainableBreakdownGroupDto groupFromIds(
            String label,
            String kind,
            List<String> ids,
            Map<String, ? extends Object> known
    ) {
        var items = distinct(ids).stream()
                .map(id -> new ExplainableBreakdownItemDto(
                        id,
                        labelFor(kind, id, known.get(id)),
                        kind,
                        known.containsKey(id) ? "Known catalog reference." : "Reference not found in loaded catalog.",
                        known.containsKey(id) ? "known" : "missing",
                        List.of(sourceRef(kind, id))
                ))
                .toList();
        return new ExplainableBreakdownGroupDto(label, items.size(), items);
    }

    private ExplainableBreakdownGroupDto group(String label, String kind, List<String> values, String reason) {
        var items = distinct(values).stream()
                .map(value -> new ExplainableBreakdownItemDto(
                        value,
                        value,
                        kind,
                        reason,
                        "catalog",
                        List.of()
                ))
                .toList();
        return new ExplainableBreakdownGroupDto(label, items.size(), items);
    }

    private List<ExplainableBreakdownGroupDto> nonEmptyGroups(ExplainableBreakdownGroupDto... groups) {
        var result = new ArrayList<ExplainableBreakdownGroupDto>();
        for (var group : groups) {
            if (group != null && group.count() > 0) {
                result.add(group);
            }
        }
        return List.copyOf(result);
    }

    private List<OperationalContextExplainabilitySectionDto> explainability(String type, String id, String summary) {
        return List.of(new OperationalContextExplainabilitySectionDto(
                "Source",
                summary,
                "high",
                List.of(reason("source", "Catalog source " + sourceFile(type) + ".", "high")),
                List.of(),
                List.of(sourceRef(type, id))
        ));
    }

    private OperationalContextDetailSectionDto section(String title, Map<String, Object> fields) {
        return new OperationalContextDetailSectionDto(title, fields);
    }

    private OperationalContextEntityDetailDto replaceSections(
            OperationalContextEntityDetailDto detail,
            List<OperationalContextDetailSectionDto> sections
    ) {
        return new OperationalContextEntityDetailDto(
                detail.type(),
                detail.id(),
                detail.title(),
                detail.subtitle(),
                List.copyOf(sections),
                detail.relatedEntities(),
                detail.recognitionSignals(),
                detail.explainabilitySections(),
                detail.validationFindings(),
                detail.openQuestions(),
                detail.sourceReferences(),
                detail.rawSourcePreview()
        );
    }

    private Map<String, Object> referencesMap(pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences references) {
        return map(
                "systems", references.systems(),
                "repositories", references.repositories(),
                "processes", references.processes(),
                "boundedContexts", references.boundedContexts(),
                "integrations", references.integrations(),
                "terms", references.terms(),
                "teams", references.teams(),
                "handoffRules", references.handoffRules()
        );
    }

    private Map<String, Object> resolvedOwnershipMap(OperationalContextResolvedOwnershipDto resolvedOwnership) {
        return map(
                "situationType", resolvedOwnership.situationType(),
                "primaryOwners", resolvedOwnership.primaryOwners().stream().map(this::resolvedOwnerMap).toList(),
                "partnerOwners", resolvedOwnership.partnerOwners().stream().map(this::resolvedOwnerMap).toList(),
                "handoffReason", resolvedOwnership.handoffReason(),
                "resolutionPath", resolvedOwnership.resolutionPath(),
                "visibilityLimits", resolvedOwnership.visibilityLimits()
        );
    }

    private Map<String, Object> resolvedOwnerMap(OperationalContextResolvedOwnerDto owner) {
        return map(
                "targetType", owner.targetType(),
                "targetId", owner.targetId(),
                "targetLabel", owner.targetLabel(),
                "ownerTeamIds", owner.ownerTeamIds(),
                "ownerLabel", owner.ownerLabel(),
                "source", owner.source(),
                "confidence", owner.confidence()
        );
    }

    private Map<String, Object> participantMap(OperationalContextIntegrationParticipant participant) {
        return map(
                "system", participant.system(),
                "boundedContext", participant.boundedContext(),
                "repositories", participant.repositories(),
                "role", participant.role(),
                "externalOwner", participant.externalOwner(),
                "notes", participant.notes()
        );
    }

    private pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences scopeReferences(
            OperationalContextRepositorySearchScope scope
    ) {
        var systems = "system".equals(normalizeType(scope.target().type())) ? textValues(scope.target().id()) : List.<String>of();
        var processes = "process".equals(normalizeType(scope.target().type())) ? textValues(scope.target().id()) : List.<String>of();
        var contexts = BOUNDED_CONTEXT.equals(normalizeType(scope.target().type())) ? textValues(scope.target().id()) : List.<String>of();
        return new pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences(
                systems,
                scope.repositories().stream().map(OperationalContextRepositorySearchRepository::repoId).toList(),
                processes,
                contexts,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private OperationalContextOwnershipRequest ownershipRequest(OperationalContextSystem system) {
        return new OperationalContextOwnershipRequest(
                null,
                textValues(system.id()),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private OperationalContextOwnershipRequest ownershipRequest(OperationalContextRepository repository) {
        return new OperationalContextOwnershipRequest(
                null,
                repository.references().systems(),
                repository.references().boundedContexts(),
                textValues(repository.id()),
                List.of(),
                new OperationalContextOwnershipRequest.TechnicalTarget(
                        repository.id(),
                        repository.git().projectPath(),
                        List.of(),
                        repository.references().systems(),
                        repository.references().boundedContexts(),
                        null,
                        "operational-context-api"
                )
        );
    }

    private OperationalContextOwnershipRequest ownershipRequest(OperationalContextProcess process) {
        return new OperationalContextOwnershipRequest(
                null,
                distinct(combineValues(process.references().systems(), process.participants().primarySystems())),
                process.references().boundedContexts(),
                process.references().repositories(),
                List.of(),
                null
        );
    }

    private OperationalContextOwnershipRequest ownershipRequest(OperationalContextIntegration integration) {
        var contextIds = distinct(combineValues(
                integration.references().boundedContexts(),
                integration.participants().source().boundedContext(),
                integration.participants().targets().stream().map(OperationalContextIntegrationParticipant::boundedContext).toList()
        ));
        var systemIds = distinct(combineValues(
                integration.references().systems(),
                integration.participants().source().system(),
                integration.participants().targetSystems(),
                integration.participants().intermediarySystems(),
                integration.participants().finalTargetSystems()
        ));
        var situationType = contextIds.size() > 1
                ? OperationalContextOwnershipResolution.BOUNDED_CONTEXT_BOUNDARY
                : systemIds.size() > 1
                ? OperationalContextOwnershipResolution.SYSTEM_BOUNDARY
                : null;
        return new OperationalContextOwnershipRequest(
                situationType,
                systemIds,
                contextIds,
                integration.references().repositories(),
                List.of(),
                null
        );
    }

    private OperationalContextOwnershipRequest ownershipRequest(OperationalContextBoundedContext context) {
        return new OperationalContextOwnershipRequest(
                null,
                context.references().systems(),
                textValues(context.id()),
                context.references().repositories(),
                List.of(),
                null
        );
    }

    private OperationalContextOwnershipRequest ownershipRequest(String type, OperationalContextEntry entry) {
        var normalizedType = normalizeType(type);
        if (SYSTEM.equals(normalizedType) && entry instanceof OperationalContextSystem system) {
            return ownershipRequest(system);
        }
        if (REPOSITORY.equals(normalizedType) && entry instanceof OperationalContextRepository repository) {
            return ownershipRequest(repository);
        }
        if (PROCESS.equals(normalizedType) && entry instanceof OperationalContextProcess process) {
            return ownershipRequest(process);
        }
        if (INTEGRATION.equals(normalizedType) && entry instanceof OperationalContextIntegration integration) {
            return ownershipRequest(integration);
        }
        if (BOUNDED_CONTEXT.equals(normalizedType) && entry instanceof OperationalContextBoundedContext context) {
            return ownershipRequest(context);
        }
        return new OperationalContextOwnershipRequest(
                null,
                entry.references().systems(),
                entry.references().boundedContexts(),
                entry.references().repositories(),
                List.of(),
                null
        );
    }

    private OperationalContextRepositorySearchScope requireScope(CatalogView view, String id) {
        return view.catalog().codeSearchScopes().stream()
                .filter(scope -> normalize(scope.id()).equals(normalize(id)))
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(CODE_SEARCH_SCOPE, id));
    }

    private <T extends OperationalContextEntry> T require(Map<String, T> values, String type, String id) {
        var value = values.get(id);
        if (value != null) {
            return value;
        }
        return values.entrySet().stream()
                .filter(entry -> normalize(entry.getKey()).equals(normalize(id)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new OperationalContextEntityNotFoundException(type, id));
    }

    private void requireReadModelEntity(CatalogView view, String type, String id) {
        switch (normalizeReadModelType(type)) {
            case SYSTEM -> require(view.systemsById(), SYSTEM, id);
            case REPOSITORY -> require(view.repositoriesById(), REPOSITORY, id);
            case CODE_SEARCH_SCOPE -> requireScope(view, id);
            case PROCESS -> require(view.processesById(), PROCESS, id);
            case INTEGRATION -> require(view.integrationsById(), INTEGRATION, id);
            case BOUNDED_CONTEXT -> require(view.contextsById(), BOUNDED_CONTEXT, id);
            case TEAM -> require(view.teamsById(), TEAM, id);
            default -> throw new OperationalContextEntityNotFoundException(type, id);
        }
    }

    private String normalizeReadModelType(String type) {
        return normalizeType(type);
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "";
        }
        var normalized = type.trim()
                .replace("_", "-")
                .replace(" ", "-")
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "systems", "operationalcontextsystem" -> SYSTEM;
            case "repositories", "operationalcontextrepository" -> REPOSITORY;
            case "codesearchscope", "codesearchscopes", "code-search-scopes", "operationalcontextrepositorysearchscope" -> CODE_SEARCH_SCOPE;
            case "processes", "operationalcontextprocess" -> PROCESS;
            case "integrations", "operationalcontextintegration" -> INTEGRATION;
            case "boundedcontext", "boundedcontexts", "bounded-contexts", "operationalcontextboundedcontext" -> BOUNDED_CONTEXT;
            case "teams", "operationalcontextteam" -> TEAM;
            case "term", "terms", "glossaryterm", "glossaryterms", "glossary-terms" -> GLOSSARY_TERM;
            case "handoffrule", "handoffrules", "handoff-rules" -> HANDOFF_RULE;
            default -> normalized;
        };
    }

    private Map<String, OperationalContextGlossaryTerm> glossaryMap(CatalogView view) {
        var result = new LinkedHashMap<String, OperationalContextGlossaryTerm>();
        view.catalog().glossaryTerms().forEach(term -> result.put(term.id(), term));
        return Map.copyOf(result);
    }

    private Map<String, OperationalContextHandoffRule> handoffRuleMap(CatalogView view) {
        var result = new LinkedHashMap<String, OperationalContextHandoffRule>();
        view.catalog().handoffRules().forEach(rule -> result.put(rule.id(), rule));
        return Map.copyOf(result);
    }

    private <T extends OperationalContextEntry> Map<String, T> indexById(List<T> entries) {
        var result = new LinkedHashMap<String, T>();
        for (var entry : entries) {
            if (StringUtils.hasText(entry.id())) {
                result.put(entry.id(), entry);
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, Integer> validationCounts(List<ValidationFindingDto> findings) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var finding : findings) {
            counts.merge(normalizeSeverity(finding.severity()), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private String catalogStatus(Map<String, Integer> validationCounts) {
        if (validationCounts.getOrDefault("error", 0) > 0) {
            return "error";
        }
        if (validationCounts.getOrDefault("warning", 0) > 0) {
            return "warning";
        }
        return "ok";
    }

    private List<ValidationFindingDto> validationFor(String type, String id, List<ValidationFindingDto> findings) {
        var normalizedType = normalizeType(type);
        var normalizedId = normalize(id);
        return findings.stream()
                .filter(finding -> normalizeType(finding.entityType()).equals(normalizedType))
                .filter(finding -> normalize(finding.entityId()).equals(normalizedId))
                .toList();
    }

    private List<OpenQuestionDto> openQuestionsFor(String type, String id, List<OpenQuestionDto> questions) {
        var normalizedType = normalizeType(type);
        var normalizedId = normalize(id);
        return questions.stream()
                .filter(question -> normalizeType(question.entityType()).equals(normalizedType))
                .filter(question -> !StringUtils.hasText(question.entityId()) || normalize(question.entityId()).equals(normalizedId))
                .toList();
    }

    private SourceReferenceDto sourceReference(SourceRef ref) {
        return new SourceReferenceDto(ref.file(), ref.fieldPath(), ref.entityId());
    }

    private SourceReferenceDto sourceRef(String type, String id) {
        return new SourceReferenceDto(sourceFile(type), "$." + normalizeType(type) + "[id=" + id + "]", id);
    }

    private String sourceFile(String type) {
        return SOURCE_FILES.getOrDefault(normalizeType(type), "operational-context");
    }

    private ExplanationReasonDto reason(String label, String detail, String strength) {
        return new ExplanationReasonDto(label, detail, strength);
    }

    private String labelFor(String kind, String id, Object known) {
        if (known instanceof OperationalContextEntry entry) {
            return entry.label();
        }
        if (known instanceof OperationalContextGlossaryTerm term) {
            return term.term();
        }
        if (known instanceof OperationalContextHandoffRule rule) {
            return rule.title();
        }
        return id;
    }

    private String summaryText(OperationalContextEntry entry) {
        return firstNonBlank(entry.summary(), entry.purpose());
    }

    private String relationLabel(OperationalContextRelation relation) {
        return firstNonBlank(relation.type(), relation.targetType()) + " -> " + firstNonBlank(relation.target(), relation.targetContextId());
    }

    private String codeSearchRepositoryRoleLabel(OperationalContextRepositorySearchRepository repository) {
        return firstNonBlank(repository.role(), "referenced") + " in " + repository.repoId();
    }

    private List<String> referencesValues(pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences references) {
        var values = new ArrayList<String>();
        values.addAll(references.systems());
        values.addAll(references.repositories());
        values.addAll(references.processes());
        values.addAll(references.boundedContexts());
        values.addAll(references.integrations());
        values.addAll(references.terms());
        values.addAll(references.teams());
        values.addAll(references.handoffRules());
        return distinct(values);
    }

    private List<String> combineValues(Object... values) {
        var result = new ArrayList<String>();
        for (var value : values) {
            if (value instanceof List<?> list) {
                list.stream()
                        .map(item -> item != null ? String.valueOf(item) : null)
                        .filter(StringUtils::hasText)
                        .forEach(result::add);
            } else if (value instanceof String text && StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private boolean containsAny(String normalizedQuery, List<String>... groups) {
        for (var group : groups) {
            for (var value : group != null ? group : List.<String>of()) {
                var normalized = normalize(value);
                if (StringUtils.hasText(normalized) && normalized.contains(normalizedQuery)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private List<String> textValues(String... values) {
        var result = new ArrayList<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private int confidenceRank(String confidence) {
        return switch (StringUtils.hasText(confidence) ? confidence.toLowerCase(Locale.ROOT) : "low") {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return "info";
        }
        var normalized = severity.trim().toLowerCase(Locale.ROOT);
        return Set.of("error", "warning", "info").contains(normalized) ? normalized : "info";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String first(List<String> values) {
        return values != null ? values.stream().filter(StringUtils::hasText).findFirst().orElse(null) : null;
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Map<String, Object> map(Object... keysAndValues) {
        var values = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < keysAndValues.length; index += 2) {
            var key = (String) keysAndValues[index];
            var value = keysAndValues[index + 1];
            if (value != null) {
                values.put(key, value);
            }
        }
        return Map.copyOf(values);
    }

    private String rawPreview(Object value) {
        return value != null ? value.toString() : null;
    }

    private record SearchMatch(String confidence, List<String> fields, String why) {
    }

    private record CatalogView(
            OperationalContextCatalog catalog,
            Map<String, OperationalContextSystem> systemsById,
            Map<String, OperationalContextRepository> repositoriesById,
            Map<String, OperationalContextProcess> processesById,
            Map<String, OperationalContextIntegration> integrationsById,
            Map<String, OperationalContextBoundedContext> contextsById,
            Map<String, OperationalContextTeam> teamsById,
            OperationalContextRelationIndex relationIndex,
            List<OpenQuestionDto> openQuestions,
            List<ValidationFindingDto> validationFindings
    ) {
    }
}
