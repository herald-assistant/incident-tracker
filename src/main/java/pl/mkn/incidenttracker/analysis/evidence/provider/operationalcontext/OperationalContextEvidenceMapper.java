package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.textList;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textAny;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textListAny;

@Component
public class OperationalContextEvidenceMapper {

    public AnalysisEvidenceSection emptySection() {
        return new AnalysisEvidenceSection("operational-context", "matched-context", List.of());
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
                : new AnalysisEvidenceSection("operational-context", "matched-context", List.copyOf(items));
    }

    private void addSystemItems(
            List<AnalysisEvidenceItem> items,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches
    ) {
        for (var match : matches) {
            var system = match.entry();
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, "systemId", text(system, "id"));
            addAttribute(attributes, "name", firstNonBlank(text(system, "name"), text(system, "shortName")));
            addAttribute(attributes, "ownerTeamId", firstNonBlank(text(system, "ownerTeamId"), firstOf(textList(system, "ownership.owningTeamIds"))));
            addAttribute(attributes, "partnerTeamIds", joined(textListAny(system, "partnerTeamIds", "ownership.supportingTeamIds")));
            addAttribute(attributes, "externalOwner", textAny(system, "externalOwner", "ownership.externalOwner"));
            addAttribute(attributes, "processIds", joined(textListAny(system, "processes", "domainContext.processes")));
            addAttribute(attributes, "contextIds", joined(textListAny(system, "contexts", "domainContext.boundedContexts")));
            addAttribute(attributes, "repoIds", joined(textListAny(system, "repos", "repositories.primary")));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "integrationId", text(integration, "id"));
            addAttribute(attributes, "name", firstNonBlank(text(integration, "name"), text(integration, "shortName")));
            addAttribute(attributes, "from", textAny(integration, "from", "topology.sourceSystemId"));
            addAttribute(attributes, "to", textAny(integration, "to", "topology.targetSystemId"));
            addAttribute(attributes, "ownerTeamId", firstNonBlank(text(integration, "ownerTeamId"), firstOf(textList(integration, "ownership.owningTeamIds"))));
            addAttribute(attributes, "partnerTeamIds", joined(textListAny(integration, "partnerTeamIds", "ownership.supportingTeamIds")));
            addAttribute(attributes, "externalOwner", textAny(integration, "externalOwner", "ownership.externalOwner"));
            addAttribute(attributes, "protocol", textAny(integration, "protocol", "classification.protocol"));
            addAttribute(attributes, "handoffTarget", textAny(integration, "handoff.target"));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "processId", text(process, "id"));
            addAttribute(attributes, "name", firstNonBlank(text(process, "name"), text(process, "shortName")));
            addAttribute(attributes, "ownerTeamId", firstNonBlank(text(process, "ownerTeamId"), firstOf(textList(process, "scope.owningTeamIds"))));
            addAttribute(attributes, "partnerTeamIds", joined(textListAny(process, "partnerTeamIds", "scope.supportingTeamIds")));
            addAttribute(attributes, "systemIds", joined(textListAny(process, "systems", "systems.internal")));
            addAttribute(attributes, "externalSystemIds", joined(textListAny(process, "externalSystems", "systems.external")));
            addAttribute(attributes, "completionSignals", joined(limit(textListAny(process, "completionSignals", "outcomes.completionSignals"), 4)));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "repositoryId", text(repository, "id"));
            addAttribute(attributes, "project", textAny(repository, "project", "gitLab.projectPath"));
            addAttribute(attributes, "group", textAny(repository, "group", "gitLab.groupPath"));
            addAttribute(attributes, "ownerTeamId", firstNonBlank(text(repository, "ownerTeamId"), firstOf(textList(repository, "ownership.owningTeamIds"))));
            addAttribute(attributes, "systemIds", joined(textListAny(repository, "systems", "topology.systemIds")));
            addAttribute(attributes, "processIds", joined(textListAny(repository, "processes", "topology.processIds")));
            addAttribute(attributes, "contextIds", joined(textListAny(repository, "contexts", "topology.boundedContexts")));
            addAttribute(attributes, "moduleIds", joined(moduleIds(repository)));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "boundedContextId", text(boundedContext, "id"));
            addAttribute(attributes, "name", firstNonBlank(text(boundedContext, "name"), text(boundedContext, "shortName")));
            addAttribute(attributes, "ownerTeamId", firstNonBlank(text(boundedContext, "ownerTeamId"), firstOf(textList(boundedContext, "ownership.owningTeamIds"))));
            addAttribute(attributes, "systemIds", joined(textListAny(boundedContext, "systems", "scope.systemIds")));
            addAttribute(attributes, "repoIds", joined(textListAny(boundedContext, "repos", "scope.repositoryIds")));
            addAttribute(attributes, "processIds", joined(textListAny(boundedContext, "processes", "scope.processIds")));
            addAttribute(attributes, "terms", joined(limit(textList(boundedContext, "terms"), 4)));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "teamId", text(team, "id"));
            addAttribute(attributes, "name", firstNonBlank(text(team, "name"), text(team, "shortName")));
            addAttribute(attributes, "systemIds", joined(textListAny(team, "owns.systems", "ownership.systems")));
            addAttribute(attributes, "repoIds", joined(textListAny(team, "owns.repos", "ownership.repositories")));
            addAttribute(attributes, "processIds", joined(textListAny(team, "owns.processes", "scope.processes")));
            addAttribute(attributes, "contextIds", joined(textListAny(team, "owns.contexts", "scope.boundedContexts")));
            addAttribute(attributes, "integrationIds", joined(textListAny(team, "owns.integrations", "ownership.integrations")));
            addAttribute(attributes, "handoffTarget", textAny(team, "handoff.target", "handoff.defaultTargetRole"));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "termId", term.id());
            addAttribute(attributes, "definition", term.definition());
            addAttribute(attributes, "typicalEvidenceSignals", joined(limit(term.typicalEvidenceSignals(), 4)));
            addAttribute(attributes, "canonicalReferences", joined(limit(term.canonicalReferences(), 4)));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
            addAttribute(attributes, "ruleId", rule.id());
            addAttribute(attributes, "routeTo", rule.routeTo());
            addAttribute(attributes, "requiredEvidence", joined(limit(rule.requiredEvidence(), 5)));
            addAttribute(attributes, "expectedFirstAction", joined(limit(rule.expectedFirstAction(), 3)));
            addAttribute(attributes, "partnerTeams", joined(limit(rule.partnerTeams(), 4)));
            addAttribute(attributes, "matchedBy", joined(match.score().reasons()));
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
