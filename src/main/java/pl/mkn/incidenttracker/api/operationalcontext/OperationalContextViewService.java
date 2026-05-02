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
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog;
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

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;

@Service
@RequiredArgsConstructor
public class OperationalContextViewService {

    private static final String SYSTEM = "system";
    private static final String REPOSITORY = "repository";
    private static final String PROCESS = "process";
    private static final String INTEGRATION = "integration";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String TEAM = "team";
    private static final String GLOSSARY_TERM = "glossary-term";
    private static final String HANDOFF_RULE = "handoff-rule";

    private static final Map<String, String> SOURCE_FILES = Map.of(
            SYSTEM, "systems.yml",
            REPOSITORY, "repo-map.yml",
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
                                "Typical signals",
                                GLOSSARY_TERM,
                                term.id(),
                                term.typicalEvidenceSignals(),
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
            case PROCESS -> mapEntityDetail(view, PROCESS, requireEntity(view.processesById(), PROCESS, id));
            case INTEGRATION -> mapEntityDetail(view, INTEGRATION, requireEntity(view.integrationsById(), INTEGRATION, id));
            case BOUNDED_CONTEXT -> mapEntityDetail(view, BOUNDED_CONTEXT, requireEntity(view.contextsById(), BOUNDED_CONTEXT, id));
            case TEAM -> mapEntityDetail(view, TEAM, requireEntity(view.teamsById(), TEAM, id));
            case GLOSSARY_TERM -> glossaryDetail(view, id);
            case HANDOFF_RULE -> handoffRuleDetail(view, id);
            default -> throw new OperationalContextEntityNotFoundException(type, id);
        };
    }

    private OperationalContextSystemRowDto systemRow(CatalogView view, Map<String, Object> system) {
        var id = id(system);
        var repos = combineLists(system, "repos", "repositories.primary", "repositories.secondary", "repositories.backendModules", "repositories.frontendModules");
        return new OperationalContextSystemRowDto(
                id,
                name(system),
                text(system, "type"),
                ownerValue(view, SYSTEM, system),
                text(system, "purpose"),
                idAggregate("Repos", SYSTEM, id, REPOSITORY, repos, view.repositoriesById(), "System lists these repositories as its code scope."),
                idAggregate("Processes", SYSTEM, id, PROCESS, textList(system, "processes"), view.processesById(), "System is attached to these operational processes."),
                idAggregate("Contexts", SYSTEM, id, BOUNDED_CONTEXT, textList(system, "contexts"), view.contextsById(), "System participates in these semantic contexts."),
                idAggregate("Integrations", SYSTEM, id, INTEGRATION, integrationIdsForSystem(view, id), view.integrationsById(), "Integration contracts reference this system as source or target."),
                signalAggregate(SYSTEM, id, system),
                handoffAggregate(SYSTEM, id, system),
                validationAggregate(SYSTEM, id, view.validationFindings()),
                openQuestionAggregate(SYSTEM, id, view.openQuestions())
        );
    }

    private OperationalContextRepositoryRowDto repositoryRow(CatalogView view, Map<String, Object> repository) {
        var id = id(repository);
        return new OperationalContextRepositoryRowDto(
                id,
                firstDefined(text(repository, "gitLab.projectPath"), text(repository, "project"), id),
                firstDefined(text(repository, "gitLab.groupPath"), text(repository, "group"), ""),
                ownerValue(view, REPOSITORY, repository),
                idAggregate("Systems", REPOSITORY, id, SYSTEM, textList(repository, "systems"), view.systemsById(), "Repository lists these runtime systems."),
                idAggregate("Processes", REPOSITORY, id, PROCESS, textList(repository, "processes"), view.processesById(), "Repository is scoped to these processes."),
                idAggregate("Contexts", REPOSITORY, id, BOUNDED_CONTEXT, textList(repository, "contexts"), view.contextsById(), "Repository contributes code to these contexts."),
                valueAggregate("Package roots", REPOSITORY, id, packageRoots(repository), "Package and source hints define the code search scope.", "package"),
                valueAggregate("Entry classes", REPOSITORY, id, entrypoints(repository), "Entrypoint and class hints help target code search.", "entrypoint"),
                valueAggregate("Runtime mappings", REPOSITORY, id, runtimeMappings(repository), "Runtime signals map deployed services back to this repository.", "runtime-signal"),
                moduleAggregate(REPOSITORY, id, repository),
                handoffAggregate(REPOSITORY, id, repository),
                validationAggregate(REPOSITORY, id, view.validationFindings())
        );
    }

    private OperationalContextProcessRowDto processRow(CatalogView view, Map<String, Object> process) {
        var id = id(process);
        return new OperationalContextProcessRowDto(
                id,
                name(process),
                ownerValue(view, PROCESS, process),
                text(process, "purpose"),
                idAggregate("Systems", PROCESS, id, SYSTEM, textList(process, "systems"), view.systemsById(), "Process runs through these internal systems."),
                idAggregate("External systems", PROCESS, id, SYSTEM, textList(process, "externalSystems"), view.systemsById(), "Process depends on these external systems."),
                idAggregate("Repos", PROCESS, id, REPOSITORY, textList(process, "repos"), view.repositoriesById(), "Process references these code scopes."),
                idAggregate("Contexts", PROCESS, id, BOUNDED_CONTEXT, textList(process, "contexts"), view.contextsById(), "Process belongs to these semantic contexts."),
                stepAggregate(PROCESS, id, process),
                valueAggregate("Completion signals", PROCESS, id, textList(process, "completionSignals"), "Completion signals show when the flow has finished.", "signal"),
                valueAggregate("Handoff hints", PROCESS, id, textList(process, "handoffHints"), "Hints written directly on the process.", "hint"),
                validationAggregate(PROCESS, id, view.validationFindings())
        );
    }

    private OperationalContextIntegrationRowDto integrationRow(CatalogView view, Map<String, Object> integration) {
        var id = id(integration);
        return new OperationalContextIntegrationRowDto(
                id,
                name(integration),
                text(integration, "from"),
                text(integration, "to"),
                ownerValue(view, INTEGRATION, integration),
                idAggregate("Partner teams", INTEGRATION, id, TEAM, textList(integration, "partnerTeamIds"), view.teamsById(), "Partner teams are listed on this integration contract."),
                text(integration, "protocol"),
                text(integration, "type"),
                idAggregate("Processes", INTEGRATION, id, PROCESS, textList(integration, "processes"), view.processesById(), "Integration supports these processes."),
                idAggregate("Contexts", INTEGRATION, id, BOUNDED_CONTEXT, textList(integration, "contexts"), view.contextsById(), "Integration crosses these bounded contexts."),
                signalAggregate(INTEGRATION, id, integration),
                handoffAggregate(INTEGRATION, id, integration),
                validationAggregate(INTEGRATION, id, view.validationFindings())
        );
    }

    private OperationalContextBoundedContextRowDto boundedContextRow(CatalogView view, Map<String, Object> context) {
        var id = id(context);
        return new OperationalContextBoundedContextRowDto(
                id,
                name(context),
                ownerValue(view, BOUNDED_CONTEXT, context),
                text(context, "purpose"),
                idAggregate("Systems", BOUNDED_CONTEXT, id, SYSTEM, textList(context, "systems"), view.systemsById(), "Context is implemented by these systems."),
                idAggregate("Repos", BOUNDED_CONTEXT, id, REPOSITORY, textList(context, "repos"), view.repositoriesById(), "Context is implemented in these repositories."),
                idAggregate("Processes", BOUNDED_CONTEXT, id, PROCESS, textList(context, "processes"), view.processesById(), "Context participates in these processes."),
                valueAggregate("Terms", BOUNDED_CONTEXT, id, textList(context, "terms"), "Terms describe the local domain vocabulary.", GLOSSARY_TERM),
                relationAggregate(BOUNDED_CONTEXT, id, context),
                valueAggregate("Runtime fingerprints", BOUNDED_CONTEXT, id, runtimeFingerprints(context), "Signals that reveal this semantic area in runtime evidence.", "fingerprint"),
                validationAggregate(BOUNDED_CONTEXT, id, view.validationFindings())
        );
    }

    private OperationalContextTeamRowDto teamRow(CatalogView view, Map<String, Object> team) {
        var id = id(team);
        return new OperationalContextTeamRowDto(
                id,
                name(team),
                text(team, "purpose"),
                idAggregate("Systems", TEAM, id, SYSTEM, textList(team, "owns.systems"), view.systemsById(), "Team owns these systems."),
                idAggregate("Repos", TEAM, id, REPOSITORY, textList(team, "owns.repos"), view.repositoriesById(), "Team owns these repositories."),
                idAggregate("Processes", TEAM, id, PROCESS, textList(team, "owns.processes"), view.processesById(), "Team owns these processes."),
                idAggregate("Contexts", TEAM, id, BOUNDED_CONTEXT, textList(team, "owns.contexts"), view.contextsById(), "Team owns these contexts."),
                idAggregate("Integrations", TEAM, id, INTEGRATION, textList(team, "owns.integrations"), view.integrationsById(), "Team owns these integration contracts."),
                signalAggregate(TEAM, id, team),
                handoffAggregate(TEAM, id, team),
                validationAggregate(TEAM, id, view.validationFindings())
        );
    }

    private OperationalContextEntityDetailDto mapEntityDetail(CatalogView view, String type, Map<String, Object> entity) {
        var id = id(entity);
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
                rawPreview(entity)
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
                valueAggregate("Typical evidence signals", GLOSSARY_TERM, term.id(), term.typicalEvidenceSignals(), "Signals listed on the term.", "signal").groups(),
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
        var catalog = operationalContextPort.loadContext(OperationalContextQuery.all());
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
            var id = id(system);
            requireReferences(findings, SYSTEM, id, "repo", REPOSITORY, allRepositoryIds(system), view.repositoriesById(), "System references unknown repository.");
            requireReferences(findings, SYSTEM, id, "process", PROCESS, textList(system, "processes"), view.processesById(), "System references unknown process.");
            requireReferences(findings, SYSTEM, id, "context", BOUNDED_CONTEXT, textList(system, "contexts"), view.contextsById(), "System references unknown bounded context.");
            requireReferences(findings, SYSTEM, id, "ownerTeamId", TEAM, listOf(text(system, "ownerTeamId")), view.teamsById(), "System ownerTeamId points to an unknown team.");
            requireReferences(findings, SYSTEM, id, "partnerTeamIds", TEAM, textList(system, "partnerTeamIds"), view.teamsById(), "System partnerTeamIds points to an unknown team.");
            requireReferences(findings, SYSTEM, id, "dependsOn", SYSTEM, textList(system, "dependsOn"), view.systemsById(), "System dependsOn references unknown system.");
        }
        for (var repository : view.catalog().repositories()) {
            var id = id(repository);
            requireReferences(findings, REPOSITORY, id, "systems", SYSTEM, textList(repository, "systems"), view.systemsById(), "Repository references unknown system.");
            requireReferences(findings, REPOSITORY, id, "processes", PROCESS, textList(repository, "processes"), view.processesById(), "Repository references unknown process.");
            requireReferences(findings, REPOSITORY, id, "contexts", BOUNDED_CONTEXT, textList(repository, "contexts"), view.contextsById(), "Repository references unknown bounded context.");
            requireReferences(findings, REPOSITORY, id, "ownerTeamId", TEAM, listOf(text(repository, "ownerTeamId")), view.teamsById(), "Repository ownerTeamId points to an unknown team.");
        }
        for (var process : view.catalog().processes()) {
            var id = id(process);
            requireReferences(findings, PROCESS, id, "systems", SYSTEM, textList(process, "systems"), view.systemsById(), "Process references unknown system.");
            requireReferences(findings, PROCESS, id, "externalSystems", SYSTEM, textList(process, "externalSystems"), view.systemsById(), "Process references unknown external system.");
            requireReferences(findings, PROCESS, id, "repos", REPOSITORY, textList(process, "repos"), view.repositoriesById(), "Process references unknown repository.");
            requireReferences(findings, PROCESS, id, "contexts", BOUNDED_CONTEXT, textList(process, "contexts"), view.contextsById(), "Process references unknown bounded context.");
            requireReferences(findings, PROCESS, id, "ownerTeamId", TEAM, listOf(text(process, "ownerTeamId")), view.teamsById(), "Process ownerTeamId points to an unknown team.");
            requireReferences(findings, PROCESS, id, "partnerTeamIds", TEAM, textList(process, "partnerTeamIds"), view.teamsById(), "Process partnerTeamIds points to an unknown team.");
        }
        for (var integration : view.catalog().integrations()) {
            var id = id(integration);
            requireReferences(findings, INTEGRATION, id, "from", SYSTEM, listOf(text(integration, "from")), view.systemsById(), "Integration source system is not modeled.");
            requireReferences(findings, INTEGRATION, id, "to", SYSTEM, listOf(text(integration, "to")), view.systemsById(), "Integration target system is not modeled.");
            requireReferences(findings, INTEGRATION, id, "processes", PROCESS, textList(integration, "processes"), view.processesById(), "Integration references unknown process.");
            requireReferences(findings, INTEGRATION, id, "contexts", BOUNDED_CONTEXT, textList(integration, "contexts"), view.contextsById(), "Integration references unknown bounded context.");
            requireReferences(findings, INTEGRATION, id, "ownerTeamId", TEAM, listOf(text(integration, "ownerTeamId")), view.teamsById(), "Integration ownerTeamId points to an unknown team.");
            requireReferences(findings, INTEGRATION, id, "partnerTeamIds", TEAM, textList(integration, "partnerTeamIds"), view.teamsById(), "Integration partnerTeamIds points to an unknown team.");
        }
        for (var context : view.catalog().boundedContexts()) {
            var id = id(context);
            requireReferences(findings, BOUNDED_CONTEXT, id, "systems", SYSTEM, textList(context, "systems"), view.systemsById(), "Bounded context references unknown system.");
            requireReferences(findings, BOUNDED_CONTEXT, id, "repos", REPOSITORY, textList(context, "repos"), view.repositoriesById(), "Bounded context references unknown repository.");
            requireReferences(findings, BOUNDED_CONTEXT, id, "processes", PROCESS, textList(context, "processes"), view.processesById(), "Bounded context references unknown process.");
            requireReferences(findings, BOUNDED_CONTEXT, id, "ownerTeamId", TEAM, listOf(text(context, "ownerTeamId")), view.teamsById(), "Bounded context ownerTeamId points to an unknown team.");
        }
        for (var team : view.catalog().teams()) {
            var id = id(team);
            requireReferences(findings, TEAM, id, "owns.systems", SYSTEM, textList(team, "owns.systems"), view.systemsById(), "Team owns unknown system.");
            requireReferences(findings, TEAM, id, "owns.repos", REPOSITORY, textList(team, "owns.repos"), view.repositoriesById(), "Team owns unknown repository.");
            requireReferences(findings, TEAM, id, "owns.processes", PROCESS, textList(team, "owns.processes"), view.processesById(), "Team owns unknown process.");
            requireReferences(findings, TEAM, id, "owns.contexts", BOUNDED_CONTEXT, textList(team, "owns.contexts"), view.contextsById(), "Team owns unknown bounded context.");
            requireReferences(findings, TEAM, id, "owns.integrations", INTEGRATION, textList(team, "owns.integrations"), view.integrationsById(), "Team owns unknown integration.");
        }
    }

    private void validateOwnership(CatalogView view, List<ValidationFindingDto> findings) {
        validateOwnerBackReference(findings, view, SYSTEM, view.catalog().systems(), "owns.systems");
        validateOwnerBackReference(findings, view, REPOSITORY, view.catalog().repositories(), "owns.repos");
        validateOwnerBackReference(findings, view, PROCESS, view.catalog().processes(), "owns.processes");
        validateOwnerBackReference(findings, view, BOUNDED_CONTEXT, view.catalog().boundedContexts(), "owns.contexts");
        validateOwnerBackReference(findings, view, INTEGRATION, view.catalog().integrations(), "owns.integrations");
        validateTeamOwnership(findings, view, SYSTEM, view.catalog().systems(), "owns.systems");
        validateTeamOwnership(findings, view, REPOSITORY, view.catalog().repositories(), "owns.repos");
        validateTeamOwnership(findings, view, PROCESS, view.catalog().processes(), "owns.processes");
        validateTeamOwnership(findings, view, BOUNDED_CONTEXT, view.catalog().boundedContexts(), "owns.contexts");
        validateTeamOwnership(findings, view, INTEGRATION, view.catalog().integrations(), "owns.integrations");

        for (var system : view.catalog().systems()) {
            if ("internal".equalsIgnoreCase(text(system, "type")) && !StringUtils.hasText(text(system, "ownerTeamId"))) {
                addFinding(findings, "warning", "ownership-consistency", SYSTEM, id(system), "Internal system has no owner", "Internal systems should declare ownerTeamId.", "Add ownerTeamId or mark the system as external.", "Routing and handoff may be ambiguous.");
            }
        }
        for (var repository : view.catalog().repositories()) {
            if (!StringUtils.hasText(text(repository, "ownerTeamId"))) {
                addFinding(findings, "warning", "ownership-consistency", REPOSITORY, id(repository), "Repository has no owner", "Repositories should declare ownerTeamId.", "Add ownerTeamId and align teams.yml owns.repos.", "Code search scope cannot be routed confidently.");
            }
        }
        for (var process : view.catalog().processes()) {
            if (!StringUtils.hasText(text(process, "ownerTeamId"))) {
                addFinding(findings, "warning", "ownership-consistency", PROCESS, id(process), "Process has no owner", "Processes should declare ownerTeamId.", "Add ownerTeamId and align teams.yml owns.processes.", "Business flow ownership is unclear.");
            }
        }
        for (var context : view.catalog().boundedContexts()) {
            if (!StringUtils.hasText(text(context, "ownerTeamId"))) {
                addFinding(findings, "warning", "ownership-consistency", BOUNDED_CONTEXT, id(context), "Bounded context has no owner", "Bounded contexts should declare ownerTeamId.", "Add ownerTeamId and align teams.yml owns.contexts.", "Semantic routing is unclear.");
            }
        }
        for (var integration : view.catalog().integrations()) {
            if (!StringUtils.hasText(text(integration, "ownerTeamId"))) {
                addFinding(findings, "warning", "ownership-consistency", INTEGRATION, id(integration), "Integration has no owner", "Integration contracts should declare ownerTeamId.", "Add ownerTeamId and align teams.yml owns.integrations.", "Contract ownership is unclear.");
            }
        }
    }

    private void validateCompleteness(CatalogView view, List<ValidationFindingDto> findings) {
        for (var system : view.catalog().systems()) {
            if ("internal".equalsIgnoreCase(text(system, "type")) && allRepositoryIds(system).isEmpty()) {
                addFinding(findings, "warning", "completeness", SYSTEM, id(system), "Internal system has no repositories", "No repository/code scope is attached to this internal system.", "Add repos or repositories.* entries.", "AI and operators cannot target code confidently.");
            }
        }
        for (var integration : view.catalog().integrations()) {
            if (!StringUtils.hasText(text(integration, "protocol"))) {
                addFinding(findings, "warning", "completeness", INTEGRATION, id(integration), "Integration protocol is missing", "The integration contract has no protocol.", "Set protocol, e.g. HTTP, SOAP, Kafka or JDBC.", "Operators cannot quickly understand the contract shape.");
            }
            if (!StringUtils.hasText(text(integration, "type"))) {
                addFinding(findings, "warning", "completeness", INTEGRATION, id(integration), "Integration type is missing", "The integration contract has no type.", "Set type, e.g. sync, async, event or batch.", "Operators cannot classify the failure mode.");
            }
            if (!StringUtils.hasText(text(integration, "handoff.target")) || textList(integration, "handoff.requiredEvidence").isEmpty()) {
                addFinding(findings, "warning", "completeness", INTEGRATION, id(integration), "Integration handoff checklist is incomplete", "Missing handoff target or required evidence.", "Fill handoff.target and handoff.requiredEvidence.", "Routing can stop at an unclear checklist.");
            }
        }
        for (var process : view.catalog().processes()) {
            if (textList(process, "systems").isEmpty()) {
                addFinding(findings, "warning", "completeness", PROCESS, id(process), "Process has no systems", "No runtime systems are attached to this process.", "Add systems to the process.", "The flow cannot be linked to runtime evidence.");
            }
            if (textList(process, "completionSignals").isEmpty()) {
                addFinding(findings, "info", "completeness", PROCESS, id(process), "Process has no completion signals", "No completion signals are documented.", "Add completionSignals when known.", "Operators may not know whether the flow finished.");
            }
        }
        for (var repository : view.catalog().repositories()) {
            if (!StringUtils.hasText(text(repository, "gitLab.projectPath")) && !StringUtils.hasText(text(repository, "project"))) {
                addFinding(findings, "warning", "completeness", REPOSITORY, id(repository), "Repository project path is missing", "No GitLab project path or project is defined.", "Fill gitLab.projectPath or project.", "Code search cannot target the repository.");
            }
            if (packageRoots(repository).isEmpty() && entrypoints(repository).isEmpty()) {
                addFinding(findings, "warning", "completeness", REPOSITORY, id(repository), "Repository has no source hints", "No package roots, paths, modules or class hints are documented.", "Add modules, package prefixes, source paths or entrypoint hints.", "Code search has too broad a scope.");
            }
        }
        for (var context : view.catalog().boundedContexts()) {
            if (textList(context, "terms").isEmpty()) {
                addFinding(findings, "info", "completeness", BOUNDED_CONTEXT, id(context), "Bounded context has no terms", "No domain terms are attached to this context.", "Add terms or glossary references.", "Vocabulary explanation may be thin.");
            }
            if (textList(context, "systems").isEmpty() && textList(context, "repos").isEmpty() && textList(context, "processes").isEmpty()) {
                addFinding(findings, "warning", "completeness", BOUNDED_CONTEXT, id(context), "Bounded context has no scope", "No systems, repositories or processes define this context.", "Add systems, repos or processes.", "The semantic boundary cannot be linked to incidents.");
            }
        }
        for (var team : view.catalog().teams()) {
            if (!StringUtils.hasText(text(team, "handoff.target"))) {
                addFinding(findings, "warning", "completeness", TEAM, id(team), "Team has no handoff target", "No team handoff target is documented.", "Fill handoff.target.", "Escalation path is incomplete.");
            }
        }
    }

    private void validateSignalQuality(CatalogView view, List<ValidationFindingDto> findings) {
        duplicateSignalFindings(view, findings, "signals.serviceNames", "serviceName", "Duplicate serviceName across systems");
        duplicateSignalFindings(view, findings, "signals.endpoints", "endpoint", "Duplicate endpoint prefix across systems");
        duplicateSignalFindings(view, findings, "signals.hosts", "host", "Duplicate host across systems");
        for (var system : view.catalog().systems()) {
            var id = id(system);
            if ("internal".equalsIgnoreCase(text(system, "type")) && signalValues(system).isEmpty()) {
                addFinding(findings, "warning", "signal-quality", SYSTEM, id, "Internal system has no recognition signals", "No service, container, endpoint, host, package or marker signals are documented.", "Add signals that appear in logs, traces, code or runtime metadata.", "Signal resolver cannot identify this system.");
            }
            for (var marker : textList(system, "signals.markers")) {
                if (GENERIC_MARKERS.contains(normalize(marker)) || marker.trim().length() < 3) {
                    addFinding(findings, "info", "signal-quality", SYSTEM, id, "Very generic marker", "Marker `" + marker + "` may match too much evidence.", "Replace it with a more specific runtime or code marker.", "Search confidence may be noisy.");
                }
            }
        }
    }

    private void validateModelingQuality(CatalogView view, List<ValidationFindingDto> findings) {
        for (var system : view.catalog().systems()) {
            var id = id(system);
            if (looksLikeEndpointHostOrEnvironment(id) || looksLikeEndpointHostOrEnvironment(name(system))) {
                addFinding(findings, "info", "modeling-quality", SYSTEM, id, "System looks too technical", "The system id/name looks like an endpoint, host, environment, pod or namespace.", "Model a stable runtime system instead of a deployment detail.", "Operational routing can become too brittle.");
            }
        }
        for (var process : view.catalog().processes()) {
            var id = id(process);
            if (looksLikeControllerMethodOrEndpoint(id) || looksLikeControllerMethodOrEndpoint(name(process))) {
                addFinding(findings, "info", "modeling-quality", PROCESS, id, "Process looks too granular", "The process looks like a controller, method or single endpoint.", "Model a business or operational flow instead.", "Flow-level diagnosis may collapse into code-level details.");
            }
        }
        for (var integration : view.catalog().integrations()) {
            var id = id(integration);
            if (looksLikeSingleHttpCall(id) || looksLikeSingleHttpCall(name(integration))) {
                addFinding(findings, "info", "modeling-quality", INTEGRATION, id, "Integration looks like one HTTP call", "The integration id/name looks like a single endpoint call rather than a contract.", "Model the contract and list endpoints as signals.", "Routing may miss related failure shapes.");
            }
        }
        for (var context : view.catalog().boundedContexts()) {
            var id = id(context);
            if (looksLikePackageModuleOrController(id) || looksLikePackageModuleOrController(name(context))) {
                addFinding(findings, "info", "modeling-quality", BOUNDED_CONTEXT, id, "Bounded context looks like a code artifact", "The context id/name looks like a package, module or controller.", "Model a semantic business boundary instead.", "Domain vocabulary can become code-shaped instead of domain-shaped.");
            }
        }
    }

    private void validateHandoffReadiness(CatalogView view, List<ValidationFindingDto> findings) {
        for (var entry : allHandoffEntries(view)) {
            if (!StringUtils.hasText(text(entry.entity(), "handoff.target"))) {
                addFinding(findings, "warning", "handoff-readiness", entry.type(), id(entry.entity()), "Handoff target is missing", "The entity has no handoff.target.", "Fill handoff.target.", "Operators may not know where to route the incident.");
            }
            if (textList(entry.entity(), "handoff.requiredEvidence").isEmpty()) {
                addFinding(findings, "info", "handoff-readiness", entry.type(), id(entry.entity()), "Required evidence checklist is empty", "The entity has no handoff.requiredEvidence.", "List the minimal evidence needed before handoff.", "Handoff quality may be inconsistent.");
            }
        }
    }

    private void requireReferences(
            List<ValidationFindingDto> findings,
            String entityType,
            String entityId,
            String path,
            String targetType,
            List<String> references,
            Map<String, Map<String, Object>> targetsById,
            String title
    ) {
        for (var reference : references) {
            if (!StringUtils.hasText(reference) || targetsById.containsKey(reference)) {
                continue;
            }
            addFinding(
                    findings,
                    "error",
                    "reference-integrity",
                    entityType,
                    entityId,
                    title,
                    "Reference `" + reference + "` at `" + path + "` does not exist as " + targetType + ".",
                    "Create `" + reference + "` or remove/rename the reference.",
                    "Related entity breakdowns and search results will be incomplete."
            );
        }
    }

    private void validateOwnerBackReference(
            List<ValidationFindingDto> findings,
            CatalogView view,
            String entityType,
            List<Map<String, Object>> entries,
            String teamOwnsPath
    ) {
        for (var entry : entries) {
            var ownerTeamId = text(entry, "ownerTeamId");
            if (!StringUtils.hasText(ownerTeamId)) {
                continue;
            }
            var team = view.teamsById().get(ownerTeamId);
            if (team == null) {
                continue;
            }
            if (!textList(team, teamOwnsPath).contains(id(entry))) {
                addFinding(findings, "warning", "ownership-consistency", entityType, id(entry), "Owner team does not list this entity", "ownerTeamId points to `" + ownerTeamId + "`, but teams.yml does not list the entity in `" + teamOwnsPath + "`.", "Add the entity id to the owning team's owns section.", "Ownership matrix and entity owner disagree.");
            }
        }
    }

    private void validateTeamOwnership(
            List<ValidationFindingDto> findings,
            CatalogView view,
            String entityType,
            List<Map<String, Object>> entries,
            String teamOwnsPath
    ) {
        var byId = indexById(entries);
        for (var team : view.catalog().teams()) {
            for (var ownedId : textList(team, teamOwnsPath)) {
                var entity = byId.get(ownedId);
                if (entity == null) {
                    continue;
                }
                var entityOwner = text(entity, "ownerTeamId");
                if (StringUtils.hasText(entityOwner) && !Objects.equals(entityOwner, id(team))) {
                    addFinding(findings, "warning", "ownership-consistency", entityType, ownedId, "Team ownership conflicts with entity owner", "Team `" + id(team) + "` owns this entity, but entity ownerTeamId is `" + entityOwner + "`.", "Choose one owner and align both sides.", "Routing may choose the wrong owner.");
                }
            }
        }
    }

    private void duplicateSignalFindings(
            CatalogView view,
            List<ValidationFindingDto> findings,
            String signalPath,
            String signalLabel,
            String title
    ) {
        var owners = new LinkedHashMap<String, List<String>>();
        for (var system : view.catalog().systems()) {
            for (var value : textList(system, signalPath)) {
                owners.computeIfAbsent(normalize(value), key -> new ArrayList<>()).add(id(system));
            }
        }
        owners.forEach((signal, systemIds) -> {
            if (StringUtils.hasText(signal) && systemIds.size() > 1) {
                for (var systemId : systemIds) {
                    addFinding(findings, "warning", "signal-quality", SYSTEM, systemId, title, "The " + signalLabel + " `" + signal + "` appears on multiple systems: " + String.join(", ", systemIds) + ".", "Make the signal more specific or document why the systems are related.", "Signal resolver confidence can become ambiguous.");
                }
            }
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
                slug(category + "-" + entityType + "-" + entityId + "-" + title + "-" + detail),
                severity,
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
            if (entry instanceof Map<?, ?> map) {
                var stringMap = asStringMap(map);
                items.add(item(id(stringMap), displayLabel(detailsType, id(stringMap), stringMap), detailsType, "Loaded from " + sourceFile(detailsType) + ".", "verified", sourceRef(detailsType, id(stringMap))));
            } else if (entry instanceof OpenQuestionDto question) {
                items.add(item(question.id(), question.question(), "open-question", "Parsed from " + question.sourceFile() + ".", "needs-review", new SourceReferenceDto(question.sourceFile(), "", question.entityId())));
            }
        }
        return aggregate(label, count, count > 0 ? "ok" : "unknown", "high", label + " loaded from the operational context catalog.", List.of(group(label, items)), List.of(reason("Catalog count", "Count is derived from parsed catalog entries.", "strong")), List.of(), sourceRefs(items), detailsType, ids(items));
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

    private ExplainableValueDto<String> ownerValue(CatalogView view, String entityType, Map<String, Object> entry) {
        var entityId = id(entry);
        var ownerTeamId = text(entry, "ownerTeamId");
        var warnings = new ArrayList<String>();
        var reasons = new ArrayList<ExplanationReasonDto>();
        if (StringUtils.hasText(ownerTeamId)) {
            var team = view.teamsById().get(ownerTeamId);
            if (team != null) {
                reasons.add(reason("ownerTeamId", "Entity declares ownerTeamId `" + ownerTeamId + "` and the team exists in teams.yml.", "strong"));
                return new ExplainableValueDto<>(ownerTeamId, name(team), "high", reasons, List.of(), List.of(sourceRef(entityType, entityId), sourceRef(TEAM, ownerTeamId)));
            }
            warnings.add("ownerTeamId points to an unknown team.");
            reasons.add(reason("ownerTeamId", "Entity declares ownerTeamId `" + ownerTeamId + "`, but the team is missing.", "medium"));
            return new ExplainableValueDto<>(ownerTeamId, ownerTeamId, "low", reasons, warnings, List.of(sourceRef(entityType, entityId)));
        }

        var externalOwner = text(entry, "externalOwner");
        if (StringUtils.hasText(externalOwner)) {
            reasons.add(reason("externalOwner", "Entity declares an external owner instead of an internal ownerTeamId.", "medium"));
            return new ExplainableValueDto<>(externalOwner, externalOwner, "medium", reasons, List.of(), List.of(sourceRef(entityType, entityId)));
        }

        var handoffTarget = text(entry, "handoff.target");
        if (StringUtils.hasText(handoffTarget)) {
            reasons.add(reason("handoff.target", "No ownerTeamId is present, so the handoff target is used as the closest owner hint.", "weak"));
            return new ExplainableValueDto<>(handoffTarget, handoffTarget, "low", reasons, List.of("Owner is inferred from handoff target, not explicit ownership."), List.of(sourceRef(entityType, entityId)));
        }

        return new ExplainableValueDto<>("", "Missing owner", "low", List.of(reason("No owner field", "No ownerTeamId, externalOwner or handoff target is present.", "weak")), List.of("Owner is missing."), List.of(sourceRef(entityType, entityId)));
    }

    private ExplainableAggregateDto idAggregate(
            String label,
            String sourceType,
            String sourceId,
            String targetType,
            List<String> ids,
            Map<String, Map<String, Object>> targetsById,
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

    private ExplainableAggregateDto signalAggregate(String sourceType, String sourceId, Map<String, Object> entry) {
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
                List.of(reason("signals.*", "Signals are read from the entity `signals` section.", "strong")),
                count > 0 ? List.of() : List.of("No recognition signals documented."),
                List.of(sourceRef(sourceType, sourceId)),
                "signal",
                idsFromGroups(groups)
        );
    }

    private ExplainableAggregateDto handoffAggregate(String sourceType, String sourceId, Map<String, Object> entry) {
        var items = new ArrayList<ExplainableBreakdownItemDto>();
        var warnings = new ArrayList<String>();
        var target = text(entry, "handoff.target");
        if (StringUtils.hasText(target)) {
            items.add(item("target", target, "handoff-target", "handoff.target is defined.", "verified", sourceRef(sourceType, sourceId)));
        } else {
            warnings.add("handoff.target is missing.");
            items.add(item("target", "Missing target", "handoff-target", "handoff.target is not defined.", "missing", sourceRef(sourceType, sourceId)));
        }
        var requiredEvidence = textList(entry, "handoff.requiredEvidence");
        if (requiredEvidence.isEmpty()) {
            warnings.add("handoff.requiredEvidence is empty.");
            items.add(item("requiredEvidence", "Missing required evidence", "handoff-evidence", "handoff.requiredEvidence is empty.", "missing", sourceRef(sourceType, sourceId)));
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
                List.of(reason("handoff.*", "Handoff readiness is derived from handoff.target and handoff.requiredEvidence.", "strong")),
                warnings,
                List.of(sourceRef(sourceType, sourceId)),
                "handoff",
                ids(items)
        );
    }

    private ExplainableAggregateDto validationAggregate(String entityType, String entityId, List<ValidationFindingDto> findings) {
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

    private ExplainableAggregateDto openQuestionAggregate(String entityType, String entityId, List<OpenQuestionDto> questions) {
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

    private ExplainableAggregateDto moduleAggregate(String sourceType, String sourceId, Map<String, Object> repository) {
        var items = mapList(repository, "modules").stream()
                .map(module -> {
                    var moduleId = firstDefined(text(module, "id"), text(module, "name"), "module");
                    var label = firstDefined(text(module, "name"), moduleId);
                    var reason = "Module paths/packages/classes: " + String.join(", ", combineLists(module, "paths", "packages", "classHints"));
                    return item(moduleId, label, "module", reason, "verified", sourceRef(sourceType, sourceId));
                })
                .toList();
        return aggregate("Modules", items.size(), items.isEmpty() ? "unknown" : "ok", "high", tooltip("Modules", items.size(), "Repository modules describe code search slices."), List.of(group("Modules", items)), List.of(reason("modules", "Modules are declared on the repository.", "strong")), List.of(), List.of(sourceRef(sourceType, sourceId)), "module", ids(items));
    }

    private ExplainableAggregateDto stepAggregate(String sourceType, String sourceId, Map<String, Object> process) {
        var items = mapList(process, "steps").stream()
                .map(step -> item(firstDefined(text(step, "id"), text(step, "name"), "step"), firstDefined(text(step, "name"), text(step, "id"), "Step"), "process-step", "Step is declared in the process definition.", "verified", sourceRef(sourceType, sourceId)))
                .toList();
        return aggregate("Steps", items.size(), items.isEmpty() ? "unknown" : "ok", "high", tooltip("Steps", items.size(), "Process steps describe the operational flow."), List.of(group("Steps", items)), List.of(reason("steps", "Steps are parsed from process.steps.", "strong")), List.of(), List.of(sourceRef(sourceType, sourceId)), "process-step", ids(items));
    }

    private ExplainableAggregateDto relationAggregate(String sourceType, String sourceId, Map<String, Object> context) {
        var items = mapList(context, "relations").stream()
                .map(relation -> item(firstDefined(text(relation, "target"), text(relation, "type"), "relation"), relationLabel(relation), "relation", "Relation is declared on the bounded context.", "verified", sourceRef(sourceType, sourceId)))
                .toList();
        return aggregate("Relations", items.size(), items.isEmpty() ? "unknown" : "ok", "high", tooltip("Relations", items.size(), "Relations explain context dependencies."), List.of(group("Relations", items)), List.of(reason("relations", "Relations are parsed from bounded context relations.", "strong")), List.of(), List.of(sourceRef(sourceType, sourceId)), "relation", ids(items));
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

    private List<ExplainableBreakdownGroupDto> relatedGroups(CatalogView view, String type, Map<String, Object> entity) {
        var id = id(entity);
        return switch (type) {
            case SYSTEM -> List.of(
                    idAggregate("Repositories", type, id, REPOSITORY, allRepositoryIds(entity), view.repositoriesById(), "System lists these repositories.").groups().get(0),
                    idAggregate("Processes", type, id, PROCESS, textList(entity, "processes"), view.processesById(), "System lists these processes.").groups().get(0),
                    idAggregate("Integrations", type, id, INTEGRATION, integrationIdsForSystem(view, id), view.integrationsById(), "Integrations reference this system.").groups().get(0)
            );
            case REPOSITORY -> List.of(
                    idAggregate("Systems", type, id, SYSTEM, textList(entity, "systems"), view.systemsById(), "Repository lists these systems.").groups().get(0),
                    idAggregate("Processes", type, id, PROCESS, textList(entity, "processes"), view.processesById(), "Repository lists these processes.").groups().get(0)
            );
            case PROCESS -> List.of(
                    idAggregate("Systems", type, id, SYSTEM, textList(entity, "systems"), view.systemsById(), "Process lists these systems.").groups().get(0),
                    idAggregate("External systems", type, id, SYSTEM, textList(entity, "externalSystems"), view.systemsById(), "Process lists these external systems.").groups().get(0)
            );
            case INTEGRATION -> List.of(
                    idAggregate("Systems", type, id, SYSTEM, combineValues(text(entity, "from"), text(entity, "to")), view.systemsById(), "Integration connects these systems.").groups().get(0),
                    idAggregate("Processes", type, id, PROCESS, textList(entity, "processes"), view.processesById(), "Integration lists these processes.").groups().get(0)
            );
            case BOUNDED_CONTEXT -> List.of(
                    idAggregate("Systems", type, id, SYSTEM, textList(entity, "systems"), view.systemsById(), "Context lists these systems.").groups().get(0),
                    idAggregate("Repositories", type, id, REPOSITORY, textList(entity, "repos"), view.repositoriesById(), "Context lists these repositories.").groups().get(0)
            );
            case TEAM -> List.of(
                    idAggregate("Owned systems", type, id, SYSTEM, textList(entity, "owns.systems"), view.systemsById(), "Team owns these systems.").groups().get(0),
                    idAggregate("Owned repositories", type, id, REPOSITORY, textList(entity, "owns.repos"), view.repositoriesById(), "Team owns these repositories.").groups().get(0)
            );
            default -> List.of();
        };
    }

    private void addSearchResults(
            List<OperationalContextSearchResultDto> results,
            String type,
            List<Map<String, Object>> entries,
            String normalizedQuery
    ) {
        for (var entry : entries) {
            var fields = searchFields(type, entry);
            var matchedFields = matchingFields(fields, normalizedQuery);
            if (matchedFields.isEmpty()) {
                continue;
            }
            var confidence = searchConfidence(fields, matchedFields, normalizedQuery);
            results.add(new OperationalContextSearchResultDto(
                    type,
                    id(entry),
                    displayLabel(type, id(entry), entry),
                    detailSubtitle(type, entry),
                    confidence,
                    matchedFields,
                    searchWhy(confidence, matchedFields),
                    Map.of("detailsType", type, "detailsId", id(entry), "detailsUrl", "/api/operational-context/entities/" + type + "/" + id(entry))
            ));
        }
    }

    private void addGlossarySearchResults(
            List<OperationalContextSearchResultDto> results,
            List<OperationalContextCatalog.GlossaryTerm> terms,
            String normalizedQuery
    ) {
        for (var term : terms) {
            var fields = orderedMap(
                    "id", term.id(),
                    "term", term.term(),
                    "category", term.category(),
                    "definition", term.definition(),
                    "typicalEvidenceSignals", term.typicalEvidenceSignals(),
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
            List<OperationalContextCatalog.HandoffRule> rules,
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

    private Map<String, Object> searchFields(String type, Map<String, Object> entry) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", id(entry));
        fields.put("name", name(entry));
        fields.put("type", text(entry, "type"));
        fields.put("purpose", text(entry, "purpose"));
        fields.put("projectName", firstDefined(text(entry, "project"), text(entry, "gitLab.projectPath")));
        fields.put("group", firstDefined(text(entry, "group"), text(entry, "gitLab.groupPath")));
        fields.put("ownerTeamId", text(entry, "ownerTeamId"));
        fields.put("handoffTarget", text(entry, "handoff.target"));
        fields.putAll(signalMap(entry));
        fields.put("packagePrefix", packageRoots(entry));
        fields.put("classHint", entrypoints(entry));
        fields.put("module", mapList(entry, "modules").stream().map(module -> firstDefined(text(module, "id"), text(module, "name"))).filter(StringUtils::hasText).toList());
        fields.put("entityType", type);
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
        if (matchedFields.stream().anyMatch(field -> Set.of("serviceNames", "containerNames", "projectNames", "packagePrefixes", "endpoints", "hosts", "queues", "topics", "schemas", "markers", "classHint", "term").contains(field))) {
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
                || catalog.processes().isEmpty()
                || catalog.teams().isEmpty()) {
            return "partial";
        }
        return "ready";
    }

    private Map<String, Map<String, Object>> indexById(List<Map<String, Object>> entries) {
        var index = new LinkedHashMap<String, Map<String, Object>>();
        for (var entry : entries) {
            var id = id(entry);
            if (StringUtils.hasText(id)) {
                index.put(id, entry);
            }
        }
        return Map.copyOf(index);
    }

    private Map<String, Object> requireEntity(Map<String, Map<String, Object>> index, String type, String id) {
        var entity = index.get(id);
        if (entity == null) {
            throw new OperationalContextEntityNotFoundException(type, id);
        }
        return entity;
    }

    private String normalizeType(String type) {
        return normalize(type).replace("_", "-");
    }

    private String id(Map<String, Object> entry) {
        return firstDefined(text(entry, "id"), text(entry, "key"), text(entry, "name"));
    }

    private String name(Map<String, Object> entry) {
        return firstDefined(text(entry, "name"), text(entry, "title"), id(entry));
    }

    private String displayLabel(String type, String id, Map<String, Object> entry) {
        if (REPOSITORY.equals(type)) {
            return firstDefined(text(entry, "gitLab.projectPath"), text(entry, "project"), name(entry), id);
        }
        return firstDefined(name(entry), id);
    }

    private String detailSubtitle(String type, Map<String, Object> entity) {
        return switch (type) {
            case SYSTEM -> firstDefined(text(entity, "type"), text(entity, "purpose"), "");
            case REPOSITORY -> String.join(" / ", distinct(combineValues(text(entity, "group"), text(entity, "gitLab.groupPath"))));
            case PROCESS, BOUNDED_CONTEXT, TEAM -> firstDefined(text(entity, "purpose"), "");
            case INTEGRATION -> String.join(" -> ", distinct(combineValues(text(entity, "from"), text(entity, "to"))));
            default -> "";
        };
    }

    private Map<String, Object> overviewFields(Map<String, Object> entity) {
        var fields = new LinkedHashMap<String, Object>();
        for (var key : List.of("id", "name", "type", "purpose", "project", "group", "ownerTeamId", "externalOwner", "from", "to", "protocol", "processes", "contexts", "repos", "systems", "externalSystems", "terms")) {
            var value = directValue(entity, key);
            if (value != null && !asTextValues(value).isEmpty()) {
                fields.put(key, value);
            }
        }
        return fields;
    }

    private Object directValue(Map<String, Object> entity, String key) {
        if (!key.contains(".")) {
            return entity.get(key);
        }
        var values = textList(entity, key);
        return values.size() == 1 ? values.get(0) : values;
    }

    private List<String> allRepositoryIds(Map<String, Object> system) {
        return combineLists(system, "repos", "repositories.primary", "repositories.secondary", "repositories.backendModules", "repositories.frontendModules");
    }

    private List<String> packageRoots(Map<String, Object> repository) {
        var values = new LinkedHashSet<String>();
        values.addAll(textList(repository, "signals.packagePrefixes"));
        values.addAll(textList(repository, "signals.paths"));
        values.addAll(textList(repository, "packageRoots"));
        values.addAll(textList(repository, "sourceRoots"));
        for (var module : mapList(repository, "modules")) {
            values.addAll(textList(module, "packages"));
            values.addAll(textList(module, "paths"));
        }
        return List.copyOf(values);
    }

    private List<String> entrypoints(Map<String, Object> repository) {
        var values = new LinkedHashSet<String>();
        values.addAll(textList(repository, "entrypoints"));
        values.addAll(textList(repository, "entryClasses"));
        values.addAll(textList(repository, "signals.entrypoints"));
        for (var module : mapList(repository, "modules")) {
            values.addAll(textList(module, "classHints"));
        }
        values.addAll(textList(repository, "signals.endpoints"));
        return List.copyOf(values);
    }

    private List<String> runtimeMappings(Map<String, Object> repository) {
        var values = new LinkedHashSet<String>();
        values.addAll(textList(repository, "runtimeMappings"));
        values.addAll(textList(repository, "signals.serviceNames"));
        values.addAll(textList(repository, "signals.containerNames"));
        values.addAll(textList(repository, "signals.projectNames"));
        return List.copyOf(values);
    }

    private List<String> runtimeFingerprints(Map<String, Object> context) {
        var values = new LinkedHashSet<String>();
        values.addAll(signalValues(context));
        values.addAll(textList(context, "runtimeFingerprints"));
        return List.copyOf(values);
    }

    private List<String> signalValues(Map<String, Object> entry) {
        return signalMap(entry).values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    private Map<String, List<String>> signalMap(Map<String, Object> entry) {
        var signals = new LinkedHashMap<String, List<String>>();
        var rawSignals = directMap(entry.get("signals"));
        rawSignals.forEach((key, value) -> {
            var values = asTextValues(value);
            if (!values.isEmpty()) {
                signals.put(key, values);
            }
        });
        return signals;
    }

    private List<String> integrationIdsForSystem(CatalogView view, String systemId) {
        return view.catalog().integrations().stream()
                .filter(integration -> Objects.equals(text(integration, "from"), systemId)
                        || Objects.equals(text(integration, "to"), systemId)
                        || textList(integration, "systems").contains(systemId))
                .map(this::id)
                .toList();
    }

    private List<OpenQuestionDto> openQuestionsFor(String entityType, String entityId, List<OpenQuestionDto> questions) {
        return questions.stream()
                .filter(question -> Objects.equals(entityType, question.entityType()) && Objects.equals(entityId, question.entityId()))
                .toList();
    }

    private List<ValidationFindingDto> findingsFor(String entityType, String entityId, List<ValidationFindingDto> findings) {
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

    private String tooltip(String label, int count, String reason) {
        return label + ": " + count + ". " + reason;
    }

    private List<String> combineLists(Map<String, Object> source, String... paths) {
        var values = new LinkedHashSet<String>();
        for (var path : paths) {
            values.addAll(textList(source, path));
        }
        return List.copyOf(values);
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

    private Map<String, Object> directMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return asStringMap(map);
    }

    private Map<String, Object> asStringMap(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
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

    private String relationLabel(Map<String, Object> relation) {
        return String.join(" ", distinct(combineValues(text(relation, "type"), text(relation, "target"), text(relation, "via"))));
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

    private boolean looksLikeEndpointHostOrEnvironment(String value) {
        var normalized = normalize(value);
        return normalized.contains("/")
                || normalized.contains("http:")
                || normalized.contains("https:")
                || normalized.contains(".local")
                || normalized.matches(".*\\b(dev|test|prod|uat|sit)\\d*\\b.*")
                || normalized.contains("namespace")
                || normalized.contains("pod");
    }

    private boolean looksLikeControllerMethodOrEndpoint(String value) {
        var normalized = normalize(value);
        return normalized.contains("/")
                || normalized.contains("controller")
                || normalized.contains("method")
                || normalized.matches(".*\\b(get|post|put|delete|patch)\\b.*");
    }

    private boolean looksLikeSingleHttpCall(String value) {
        var normalized = normalize(value);
        return normalized.matches(".*\\b(get|post|put|delete|patch)\\b.*")
                || normalized.contains("/api/")
                || normalized.contains("endpoint");
    }

    private boolean looksLikePackageModuleOrController(String value) {
        var normalized = normalize(value);
        return normalized.contains(".")
                || normalized.contains("controller")
                || normalized.contains("module")
                || normalized.startsWith("com-")
                || normalized.startsWith("pl-");
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

    private List<HandoffEntry> allHandoffEntries(CatalogView view) {
        var entries = new ArrayList<HandoffEntry>();
        view.catalog().systems().forEach(entry -> entries.add(new HandoffEntry(SYSTEM, entry)));
        view.catalog().repositories().forEach(entry -> entries.add(new HandoffEntry(REPOSITORY, entry)));
        view.catalog().integrations().forEach(entry -> entries.add(new HandoffEntry(INTEGRATION, entry)));
        view.catalog().teams().forEach(entry -> entries.add(new HandoffEntry(TEAM, entry)));
        return entries;
    }

    private record CatalogView(
            OperationalContextCatalog catalog,
            Map<String, Map<String, Object>> systemsById,
            Map<String, Map<String, Object>> repositoriesById,
            Map<String, Map<String, Object>> processesById,
            Map<String, Map<String, Object>> integrationsById,
            Map<String, Map<String, Object>> contextsById,
            Map<String, Map<String, Object>> teamsById,
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

    private record HandoffEntry(
            String type,
            Map<String, Object> entity
    ) {
    }
}
