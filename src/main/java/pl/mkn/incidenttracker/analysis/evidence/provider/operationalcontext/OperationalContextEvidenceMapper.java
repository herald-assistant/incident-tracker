package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.mapList;
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
        var items = new ArrayList<AnalysisEvidenceItem>();
        addSystemItems(items, matches.systemMatches());
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
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var system = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_SYSTEM_ID, text(system, "id"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_NAME, firstNonBlank(text(system, "name"), text(system, "shortName")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_OWNER_TEAM_ID, firstNonBlank(text(system, "ownerTeamId"), firstOf(textList(system, "ownership.owningTeamIds"))));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PARTNER_TEAM_IDS, joined(textListAny(system, "partnerTeamIds", "ownership.supportingTeamIds")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_EXTERNAL_OWNER, textAny(system, "externalOwner", "ownership.externalOwner"));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_PROCESS_IDS, joined(textListAny(system, "processes", "domainContext.processes")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_CONTEXT_IDS, joined(textListAny(system, "contexts", "domainContext.boundedContexts")));
            addAttribute(attributes, OperationalContextEvidenceView.ATTRIBUTE_REPO_IDS, joined(textListAny(system, "repos", "repositories.primary")));
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
