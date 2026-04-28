package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.textList;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textAny;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textListAny;

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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_ID, firstNonBlank(text(system, "ownerTeamId"), firstOf(textList(system, "ownership.owningTeamIds"))));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textListAny(system, "partnerTeamIds", "ownership.supportingTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, textAny(system, "externalOwner", "ownership.externalOwner"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textListAny(system, "processes", "domainContext.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textListAny(system, "contexts", "domainContext.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPO_IDS, joined(repoIds));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CODE_SEARCH_REPO_IDS, joined(repositoryIds(codeSearchRepositories)));
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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_FROM, textAny(integration, "from", "topology.sourceSystemId"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TO, textAny(integration, "to", "topology.targetSystemId"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_ID, firstNonBlank(text(integration, "ownerTeamId"), firstOf(textList(integration, "ownership.owningTeamIds"))));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textListAny(integration, "partnerTeamIds", "ownership.supportingTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, textAny(integration, "externalOwner", "ownership.externalOwner"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROTOCOL, textAny(integration, "protocol", "classification.protocol"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_HANDOFF_TARGET, textAny(integration, "handoff.target"));
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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_ID, firstNonBlank(text(process, "ownerTeamId"), firstOf(textList(process, "scope.owningTeamIds"))));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textListAny(process, "partnerTeamIds", "scope.supportingTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textListAny(process, "systems", "systems.internal")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_SYSTEM_IDS, joined(textListAny(process, "externalSystems", "systems.external")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_COMPLETION_SIGNALS, joined(limit(textListAny(process, "completionSignals", "outcomes.completionSignals"), 4)));
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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROJECT, textAny(repository, "project", "gitLab.projectPath"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_GROUP, textAny(repository, "group", "gitLab.groupPath"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_ID, firstNonBlank(text(repository, "ownerTeamId"), firstOf(textList(repository, "ownership.owningTeamIds"))));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textListAny(repository, "systems", "topology.systemIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textListAny(repository, "processes", "topology.processIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textListAny(repository, "contexts", "topology.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MODULE_IDS, joined(moduleIds(repository)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PATHS, joined(limit(repositoryPaths(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SOURCE_PACKAGES, joined(limit(repositoryPackages(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CLASS_HINTS, joined(limit(repositoryClassHints(List.of(repository)), 10)));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_MATCHED_BY, joined(match.score().reasons()));
            items.add(new AnalysisEvidenceItem(
                    "Operational repository " + firstNonBlank(text(repository, "id"), textAny(repository, "project", "name")),
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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_ID, firstNonBlank(text(boundedContext, "ownerTeamId"), firstOf(textList(boundedContext, "ownership.owningTeamIds"))));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textListAny(boundedContext, "systems", "scope.systemIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPO_IDS, joined(textListAny(boundedContext, "repos", "scope.repositoryIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textListAny(boundedContext, "processes", "scope.processIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TERMS, joined(limit(textList(boundedContext, "terms"), 4)));
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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_IDS, joined(textListAny(team, "owns.systems", "ownership.systems")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPO_IDS, joined(textListAny(team, "owns.repos", "ownership.repositories")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textListAny(team, "owns.processes", "scope.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textListAny(team, "owns.contexts", "scope.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_INTEGRATION_IDS, joined(textListAny(team, "owns.integrations", "ownership.integrations")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_HANDOFF_TARGET, textAny(team, "handoff.target", "handoff.defaultTargetRole"));
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
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_TYPICAL_EVIDENCE_SIGNALS, joined(limit(term.typicalEvidenceSignals(), 4)));
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
                .map(module -> text(module, "id"))
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> systemRepositoryIds(Map<String, Object> system) {
        return deduplicate(textListAny(
                system,
                "repos",
                "repositories.primary",
                "repositories.secondary"
        ));
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
                if (containsNormalized(textListAny(repository, "systems", "topology.systemIds"), systemId)) {
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
        var id = firstNonBlank(text(repository, "id"), textAny(repository, "project", "gitLab.projectPath"));
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
        return deduplicate(repositories.stream()
                .map(repository -> firstNonBlank(textAny(repository, "project", "gitLab.projectPath"), text(repository, "id")))
                .toList());
    }

    private List<String> repositoryPaths(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(textList(repository, "sourceLayout.importantPaths"));
            for (var module : mapList(repository, "modules")) {
                values.addAll(textListAny(module, "paths", "pathPrefixes"));
            }
        }
        return deduplicate(values);
    }

    private List<String> repositoryPackages(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(textList(repository, "sourceLayout.packageRoots"));
            values.addAll(textList(repository, "signals.packagePrefixes"));
            for (var module : mapList(repository, "modules")) {
                values.addAll(textListAny(module, "packages", "packageRoots"));
            }
        }
        return deduplicate(values);
    }

    private List<String> repositoryClassHints(List<Map<String, Object>> repositories) {
        var values = new ArrayList<String>();
        for (var repository : repositories) {
            values.addAll(textList(repository, "sourceLookupHints.stacktraceHotspots"));
            values.addAll(textList(repository, "sourceLookupHints.likelyEntryClasses"));
            for (var module : mapList(repository, "modules")) {
                values.addAll(textListAny(module, "classHints", "runtimeFingerprints.classNameHints"));
                values.addAll(textList(module, "sourceLookupHints.likelyEntryClasses"));
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

    private String firstOf(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
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
