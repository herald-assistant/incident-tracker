package pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.normalize;

@Component
public class OperationalContextEvidenceMapper {

    public AnalysisEvidenceSection emptySection() {
        return new AnalysisEvidenceSection(
                OperationalContextEvidenceView.EVIDENCE_REFERENCE.provider(),
                OperationalContextEvidenceView.EVIDENCE_REFERENCE.category(),
                List.of()
        );
    }

    public AnalysisEvidenceSection toEvidenceSection(OperationalContextMatchBundle matches) {
        return toEvidenceSection(matches, null);
    }

    public AnalysisEvidenceSection toEvidenceSection(
            OperationalContextMatchBundle matches,
            OperationalContextCatalog catalog
    ) {
        var items = new ArrayList<AnalysisEvidenceItem>();
        addSystemItems(items, matches.systemMatches(), catalog);
        addIntegrationItems(items, matches.integrationMatches());
        addProcessItems(items, matches.processMatches());
        addRepositoryItems(items, matches.repositoryMatches());
        addBoundedContextItems(items, matches.boundedContextMatches());
        addTeamItems(items, matches.teamMatches());
        addGlossaryItems(items, matches.glossaryMatches());
        addHandoffRuleItems(items, matches.handoffMatches());

        return items.isEmpty()
                ? emptySection()
                : new AnalysisEvidenceSection(
                OperationalContextEvidenceView.EVIDENCE_REFERENCE.provider(),
                OperationalContextEvidenceView.EVIDENCE_REFERENCE.category(),
                List.copyOf(items)
        );
    }

    private void addSystemItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextSystem>> matches,
            OperationalContextCatalog catalog
    ) {
        for (var match : matches) {
            var system = match.entry();
            var systemId = system.id();
            var repoIds = systemRepositoryIds(system);
            var codeSearchRepositories = resolveCodeSearchRepositories(catalog, systemId, repoIds);
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_ID, systemId);
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(system.name(), system.shortName()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(system)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(system.handoffHints().partnerTeamIds()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, system.participants().externalOwner());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(system.references().processes()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(system.references().boundedContexts()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_IDS, joined(repoIds));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CODE_SEARCH_REPOSITORY_IDS, joined(repositoryIds(codeSearchRepositories)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CODE_SEARCH_PROJECTS, joined(repositoryProjects(codeSearchRepositories)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PACKAGES, joined(limit(repositoryPackages(codeSearchRepositories), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CLASS_HINTS, joined(limit(repositoryClassHints(codeSearchRepositories), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational system " + firstNonBlank(system.id(), system.name()),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addIntegrationItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextIntegration>> matches
    ) {
        for (var match : matches) {
            var integration = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_INTEGRATION_ID, integration.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(integration.name(), integration.shortName()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_SYSTEM, integration.participants().source().system());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TARGET_SYSTEMS, joined(integrationTargetSystems(integration)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(integration)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(integration.handoffHints().partnerTeamIds()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, integrationExternalOwner(integration));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROTOCOLS, joined(integration.transport().protocols()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_HANDOFF_TARGET, integration.handoffHints().defaultRouteLabel());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational integration " + firstNonBlank(integration.id(), integration.name()),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addProcessItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextProcess>> matches
    ) {
        for (var match : matches) {
            var process = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_ID, process.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(process.name(), process.shortName()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(process)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(process.handoffHints().partnerTeamIds()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(process.participants().primarySystems()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_SYSTEM_IDS, joined(process.participants().externalSystems()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_COMPLETION_SIGNALS, joined(limit(completionSignals(process), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational process " + firstNonBlank(process.id(), process.name()),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addRepositoryItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextRepository>> matches
    ) {
        for (var match : matches) {
            var repository = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_ID, repository.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROJECT_PATH, repository.git().projectPath());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_GROUP, repository.git().group());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(repository)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(repository.references().systems()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(repository.references().processes()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(repository.references().boundedContexts()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MODULE_IDS, joined(moduleIds(repository)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PATHS, joined(limit(repositoryPaths(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PACKAGES, joined(limit(repositoryPackages(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CLASS_HINTS, joined(limit(repositoryClassHints(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational repository " + firstNonBlank(repository.id(), repository.git().projectPath(), repository.name()),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addBoundedContextItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextBoundedContext>> matches
    ) {
        for (var match : matches) {
            var boundedContext = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_BOUNDED_CONTEXT_ID, boundedContext.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(boundedContext.name(), boundedContext.shortName()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(boundedContext)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(boundedContext.references().systems()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_IDS, joined(boundedContext.references().repositories()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(boundedContext.references().processes()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TERMS, joined(limit(boundedContext.references().terms(), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational bounded context " + firstNonBlank(boundedContext.id(), boundedContext.name()),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addTeamItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextTeam>> matches
    ) {
        for (var match : matches) {
            var team = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TEAM_ID, team.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(team.name(), team.shortName()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(team.references().systems()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_IDS, joined(team.references().repositories()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(team.references().processes()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(team.references().boundedContexts()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_INTEGRATION_IDS, joined(team.references().integrations()));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_HANDOFF_TARGET, team.handoffHints().defaultRouteLabel());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational team " + firstNonBlank(team.id(), team.name()),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addGlossaryItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextGlossaryTerm>> matches
    ) {
        for (var match : matches) {
            var term = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TERM_ID, term.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_DEFINITION, term.definition());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCH_SIGNALS, joined(limit(term.matchSignals(), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CANONICAL_REFERENCES, joined(limit(term.canonicalReferences(), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational glossary term " + term.id(),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addHandoffRuleItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<OperationalContextHandoffRule>> matches
    ) {
        for (var match : matches) {
            var rule = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_RULE_ID, rule.id());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_ROUTE_TO, rule.routeTo());
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REQUIRED_EVIDENCE, joined(limit(rule.requiredEvidence(), 5)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXPECTED_FIRST_ACTION, joined(limit(rule.expectedFirstAction(), 3)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAMS, joined(limit(rule.partnerTeams(), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational handoff rule " + rule.id(),
                    List.copyOf(attributes)
            ));
        }
    }

    private List<String> moduleIds(OperationalContextRepository repository) {
        return repository.modules().stream()
                .map(module -> firstNonBlank(module.moduleId(), module.id()))
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> systemRepositoryIds(OperationalContextSystem system) {
        return system.references().repositories();
    }

    private List<OperationalContextRepository> resolveCodeSearchRepositories(
            OperationalContextCatalog catalog,
            String systemId,
            List<String> repoIds
    ) {
        if (catalog == null || catalog.repositories().isEmpty()) {
            return List.of();
        }

        var repositories = catalog.repositories();
        var selected = new ArrayList<OperationalContextRepository>();
        var selectedIds = new LinkedHashSet<String>();

        for (var repoId : repoIds) {
            repositories.stream()
                    .filter(repository -> sameId(repository.id(), repoId))
                    .findFirst()
                    .ifPresent(repository -> addRepository(selected, selectedIds, repository));
        }

        if (StringUtils.hasText(systemId)) {
            for (var repository : repositories) {
                if (containsNormalized(repository.references().systems(), systemId)) {
                    addRepository(selected, selectedIds, repository);
                }
            }
        }

        return List.copyOf(selected);
    }

    private void addRepository(
            List<OperationalContextRepository> repositories,
            LinkedHashSet<String> selectedIds,
            OperationalContextRepository repository
    ) {
        var id = firstNonBlank(repository.id(), repository.git().projectPath());
        var normalizedId = normalize(id);
        if (StringUtils.hasText(normalizedId) && selectedIds.add(normalizedId)) {
            repositories.add(repository);
        }
    }

    private List<String> repositoryIds(List<OperationalContextRepository> repositories) {
        return deduplicate(repositories.stream()
                .map(OperationalContextRepository::id)
                .toList());
    }

    private List<String> repositoryProjects(List<OperationalContextRepository> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.add(repository.git().projectPath());
            values.add(repository.git().project());
            values.add(repository.id());
        }
        return deduplicate(values);
    }

    private List<String> repositoryPaths(List<OperationalContextRepository> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(repository.sourceLayout().sourceRoots());
            values.addAll(repository.sourceLayout().modulePaths());
            values.addAll(repository.sourceLayout().importantPaths());
            for (var module : repository.modules()) {
                values.addAll(module.sourceRoots());
                values.addAll(module.importantPaths());
                values.addAll(module.matchSignals().valuesForKeys("paths", "pathHints"));
            }
        }
        return deduplicate(values);
    }

    private List<String> repositoryPackages(List<OperationalContextRepository> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(repository.packagePrefixSignals());
        }
        return deduplicate(values);
    }

    private List<String> repositoryClassHints(List<OperationalContextRepository> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(repository.classHintSignals());
        }
        return deduplicate(values);
    }

    private List<String> ownerTeamIds(OperationalContextEntry entry) {
        var values = new ArrayList<String>();
        values.addAll(entry.references().teams());
        for (var responsibility : entry.responsibilities()) {
            values.add(responsibility.teamId());
        }
        return deduplicate(values);
    }

    private List<String> integrationTargetSystems(OperationalContextIntegration integration) {
        return integration.participants().targetSystems();
    }

    private List<String> completionSignals(OperationalContextProcess process) {
        var values = new ArrayList<String>();
        values.addAll(process.processBoundary().endsWhen());
        values.addAll(process.outcomes().successArtifacts());
        return deduplicate(values);
    }

    private String integrationExternalOwner(OperationalContextIntegration integration) {
        var owners = new ArrayList<String>();
        owners.add(integration.participants().source().externalOwner());
        for (var target : integration.participants().targets()) {
            owners.add(target.externalOwner());
        }
        return joined(deduplicate(owners));
    }

    private boolean containsNormalized(List<String> values, String expected) {
        var normalizedExpected = normalize(expected);
        return StringUtils.hasText(normalizedExpected)
                && values.stream().map(value -> normalize(value)).anyMatch(normalizedExpected::equals);
    }

    private boolean sameId(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && normalize(left).equals(normalize(right));
    }

    private String joined(List<String> values) {
        var filteredValues = values.stream()
                .filter(StringUtils::hasText)
                .toList();
        return filteredValues.isEmpty() ? null : String.join("; ", filteredValues);
    }

    private List<String> limit(List<String> values, int maxSize) {
        return values.stream()
                .filter(StringUtils::hasText)
                .limit(maxSize)
                .toList();
    }

    private List<String> deduplicate(List<String> values) {
        var deduplicated = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                deduplicated.add(value.trim());
            }
        }
        return List.copyOf(deduplicated);
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

}
