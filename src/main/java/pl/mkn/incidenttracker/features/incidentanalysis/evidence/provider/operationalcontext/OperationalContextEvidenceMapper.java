package pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textListAny;

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
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches,
            OperationalContextCatalog catalog
    ) {
        for (var match : matches) {
            var system = match.entry();
            var systemId = text(system, "id");
            var repoIds = systemRepositoryIds(system);
            var codeSearchRepositories = resolveCodeSearchRepositories(catalog, systemId, repoIds);
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_ID, systemId);
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(text(system, "name"), text(system, "shortName")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(system)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textList(system, "handoffHints.partnerTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, text(system, "participants.externalOwner"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textList(system, "references.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textList(system, "references.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_IDS, joined(repoIds));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CODE_SEARCH_REPOSITORY_IDS, joined(repositoryIds(codeSearchRepositories)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CODE_SEARCH_PROJECTS, joined(repositoryProjects(codeSearchRepositories)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PACKAGES, joined(limit(repositoryPackages(codeSearchRepositories), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CLASS_HINTS, joined(limit(repositoryClassHints(codeSearchRepositories), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational system " + firstNonBlank(text(system, "id"), text(system, "name")),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addIntegrationItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var integration = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_INTEGRATION_ID, text(integration, "id"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(text(integration, "name"), text(integration, "shortName")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_SYSTEM, text(integration, "participants.source.system"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TARGET_SYSTEMS, joined(integrationTargetSystems(integration)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(integration)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textList(integration, "handoffHints.partnerTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, integrationExternalOwner(integration));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROTOCOLS, joined(textList(integration, "transport.protocols")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_HANDOFF_TARGET, text(integration, "handoffHints.defaultRouteLabel"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational integration " + firstNonBlank(text(integration, "id"), text(integration, "name")),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addProcessItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var process = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_ID, text(process, "id"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(text(process, "name"), text(process, "shortName")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(process)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textList(process, "handoffHints.partnerTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textList(process, "participants.primarySystems")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_SYSTEM_IDS, joined(textList(process, "participants.externalSystems")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_COMPLETION_SIGNALS, joined(limit(textListAny(process, "processBoundary.endsWhen", "outcomes.successArtifacts"), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational process " + firstNonBlank(text(process, "id"), text(process, "name")),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addRepositoryItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var repository = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_ID, text(repository, "id"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROJECT_PATH, text(repository, "git.projectPath"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_GROUP, text(repository, "git.group"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(repository)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textList(repository, "references.systems")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textList(repository, "references.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textList(repository, "references.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MODULE_IDS, joined(moduleIds(repository)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PATHS, joined(limit(repositoryPaths(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PACKAGES, joined(limit(repositoryPackages(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CLASS_HINTS, joined(limit(repositoryClassHints(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational repository " + firstNonBlank(text(repository, "id"), text(repository, "git.projectPath"), text(repository, "name")),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addBoundedContextItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var boundedContext = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_BOUNDED_CONTEXT_ID, text(boundedContext, "id"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(text(boundedContext, "name"), text(boundedContext, "shortName")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_IDS, joined(ownerTeamIds(boundedContext)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textList(boundedContext, "references.systems")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_IDS, joined(textList(boundedContext, "references.repositories")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textList(boundedContext, "references.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TERMS, joined(limit(textList(boundedContext, "references.terms"), 4)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational bounded context " + firstNonBlank(text(boundedContext, "id"), text(boundedContext, "name")),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addTeamItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var team = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TEAM_ID, text(team, "id"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(text(team, "name"), text(team, "shortName")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textList(team, "references.systems")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPOSITORY_IDS, joined(textList(team, "references.repositories")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textList(team, "references.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textList(team, "references.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_INTEGRATION_IDS, joined(textList(team, "references.integrations")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_HANDOFF_TARGET, text(team, "handoffHints.defaultRouteLabel"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational team " + firstNonBlank(text(team, "id"), text(team, "name")),
                    List.copyOf(attributes)
            ));
        }
    }

    private void addGlossaryItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<GlossaryTerm>> matches
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
            List<OperationalContextMatchedEntry<HandoffRule>> matches
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

    private List<String> moduleIds(Map<String, Object> repository) {
        return mapList(repository, "modules").stream()
                .map(module -> firstNonBlank(text(module, "moduleId"), text(module, "id")))
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> systemRepositoryIds(Map<String, Object> system) {
        return textList(system, "references.repositories");
    }

    private List<Map<String, Object>> resolveCodeSearchRepositories(
            OperationalContextCatalog catalog,
            String systemId,
            List<String> repoIds
    ) {
        if (catalog == null || catalog.repositories().isEmpty()) {
            return List.of();
        }

        var repositories = catalog.repositories();
        var selected = new ArrayList<Map<String, Object>>();
        var selectedIds = new LinkedHashSet<String>();

        for (var repoId : repoIds) {
            repositories.stream()
                    .filter(repository -> sameId(text(repository, "id"), repoId))
                    .findFirst()
                    .ifPresent(repository -> addRepository(selected, selectedIds, repository));
        }

        if (StringUtils.hasText(systemId)) {
            for (var repository : repositories) {
                if (containsNormalized(textList(repository, "references.systems"), systemId)) {
                    addRepository(selected, selectedIds, repository);
                }
            }
        }

        return List.copyOf(selected);
    }

    private void addRepository(
            List<Map<String, Object>> repositories,
            LinkedHashSet<String> selectedIds,
            Map<String, Object> repository
    ) {
        var id = firstNonBlank(text(repository, "id"), text(repository, "git.projectPath"));
        var normalizedId = normalize(id);
        if (StringUtils.hasText(normalizedId) && selectedIds.add(normalizedId)) {
            repositories.add(repository);
        }
    }

    private List<String> repositoryIds(List<Map<String, Object>> repositories) {
        return deduplicate(repositories.stream()
                .map(repository -> text(repository, "id"))
                .toList());
    }

    private List<String> repositoryProjects(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.add(text(repository, "git.projectPath"));
            values.add(text(repository, "git.project"));
            values.add(text(repository, "id"));
        }
        return deduplicate(values);
    }

    private List<String> repositoryPaths(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(textList(repository, "sourceLayout.sourceRoots"));
            values.addAll(textList(repository, "sourceLayout.modulePaths"));
            values.addAll(textList(repository, "sourceLayout.importantPaths"));
            for (var module : mapList(repository, "modules")) {
                values.addAll(textList(module, "sourceRoots"));
                values.addAll(textList(module, "importantPaths"));
                values.addAll(matchSignalValues(module, "paths", "pathHints"));
            }
        }
        return deduplicate(values);
    }

    private List<String> repositoryPackages(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(matchSignalValues(repository, "packagePrefixes"));
            for (var module : mapList(repository, "modules")) {
                values.addAll(matchSignalValues(module, "packagePrefixes"));
            }
        }
        return deduplicate(values);
    }

    private List<String> repositoryClassHints(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(textList(repository, "codeSearch.entrypoints"));
            values.addAll(matchSignalValues(repository, "classHints", "entrypoints"));
            for (var module : mapList(repository, "modules")) {
                values.addAll(matchSignalValues(module, "classHints", "entrypoints"));
            }
        }
        return deduplicate(values);
    }

    private List<String> ownerTeamIds(Map<String, Object> entry) {
        var values = new ArrayList<String>();
        values.addAll(textList(entry, "references.teams"));
        for (var responsibility : mapList(entry, "responsibilities")) {
            values.add(text(responsibility, "teamId"));
        }
        return deduplicate(values);
    }

    private List<String> integrationTargetSystems(Map<String, Object> integration) {
        var values = new ArrayList<String>();
        for (var target : mapList(integration, "participants.targets")) {
            values.add(text(target, "system"));
        }
        values.addAll(textList(integration, "participants.finalTargets"));
        return deduplicate(values);
    }

    private String integrationExternalOwner(Map<String, Object> integration) {
        var owners = new ArrayList<String>();
        owners.add(text(integration, "participants.source.externalOwner"));
        for (var target : mapList(integration, "participants.targets")) {
            owners.add(text(target, "externalOwner"));
        }
        return joined(deduplicate(owners));
    }

    private List<String> matchSignalValues(Map<String, Object> entry, String... keys) {
        var values = new ArrayList<String>();
        for (var confidence : List.of("exact", "strong", "medium", "weak")) {
            for (var key : keys) {
                values.addAll(textList(entry, "matchSignals." + confidence + "." + key));
            }
        }
        return deduplicate(values);
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
