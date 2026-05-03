package pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.HandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextProperties;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.anyOverlap;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.containsAnyId;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.containsId;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.genericSignals;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.matchedIds;
import static pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textListAny;

@Component
@RequiredArgsConstructor
public class OperationalContextCatalogMatcher {

    private final OperationalContextProperties properties;

    public OperationalContextMatchBundle match(
            OperationalContextCatalog catalog,
            OperationalContextIncidentSignals signals
    ) {
        var systemMatches = matchEntries(
                catalog.systems(),
                entry -> scoreSystem(entry, signals),
                properties.getMaxItemsPerType()
        );
        var integrationMatches = matchEntries(
                catalog.integrations(),
                entry -> scoreIntegration(entry, signals, systemMatches),
                properties.getMaxItemsPerType()
        );
        var repositoryMatches = matchEntries(
                catalog.repositories(),
                entry -> scoreRepository(entry, signals, systemMatches),
                properties.getMaxItemsPerType()
        );
        var processMatches = matchEntries(
                catalog.processes(),
                entry -> scoreProcess(entry, signals, systemMatches),
                properties.getMaxItemsPerType()
        );
        var boundedContextMatches = matchEntries(
                catalog.boundedContexts(),
                entry -> scoreBoundedContext(entry, signals, systemMatches, processMatches, repositoryMatches),
                properties.getMaxItemsPerType()
        );
        var teamMatches = matchEntries(
                catalog.teams(),
                entry -> scoreTeam(
                        entry,
                        systemMatches,
                        processMatches,
                        repositoryMatches,
                        boundedContextMatches,
                        integrationMatches
                ),
                properties.getMaxItemsPerType()
        );
        var glossaryMatches = matchGlossaryTerms(catalog.glossaryTerms(), signals);
        var handoffMatches = matchHandoffRules(
                catalog.handoffRules(),
                signals,
                integrationMatches,
                boundedContextMatches
        );

        return new OperationalContextMatchBundle(
                systemMatches,
                integrationMatches,
                processMatches,
                repositoryMatches,
                boundedContextMatches,
                teamMatches,
                glossaryMatches,
                handoffMatches
        );
    }

    private List<OperationalContextMatchedEntry<Map<String, Object>>> matchEntries(
            List<Map<String, Object>> entries,
            Function<Map<String, Object>, OperationalContextMatchScore> scorer,
            int limit
    ) {
        return entries.stream()
                .map(entry -> new OperationalContextMatchedEntry<>(entry, scorer.apply(entry)))
                .filter(match -> match.score().score() > 0)
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<Map<String, Object>> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> text(match.entry(), "id"), Comparator.nullsLast(String::compareTo)))
                .limit(limit)
                .toList();
    }

    private List<OperationalContextMatchedEntry<GlossaryTerm>> matchGlossaryTerms(
            List<GlossaryTerm> terms,
            OperationalContextIncidentSignals signals
    ) {
        return terms.stream()
                .map(term -> new OperationalContextMatchedEntry<>(term, scoreGlossaryTerm(term, signals)))
                .filter(match -> match.score().score() > 0)
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<GlossaryTerm> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id()))
                .limit(properties.getMaxGlossaryTerms())
                .toList();
    }

    private List<OperationalContextMatchedEntry<HandoffRule>> matchHandoffRules(
            List<HandoffRule> rules,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> integrationMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> boundedContextMatches
    ) {
        var selectedRuleIds = new LinkedHashSet<String>();
        if (signals.containsAny("timeout", "soapfault", "connection reset", "read timed out")
                && !integrationMatches.isEmpty()) {
            selectedRuleIds.add("integration-external-sync-failure");
        }
        if (signals.containsAny("queue", "topic", "routing key", "consumer lag", "serialization")
                || signals.matchesAttributeNames("queuename", "topicname")) {
            selectedRuleIds.add("integration-async-messaging-stall");
        }
        if (signals.containsAny("jdbc", "sql", "oracle", "deadlock", "schema", "lock")) {
            selectedRuleIds.add("database-state-or-schema-issue");
        }
        if (signals.containsAny("namespace", "certificate", "host resolution", "deployment", "unavailable")) {
            selectedRuleIds.add("platform-or-environment-issue");
        }
        if (!boundedContextMatches.isEmpty()
                && signals.containsAny("contract", "payload", "state", "mismatch", "semantic")) {
            selectedRuleIds.add("bounded-context-boundary-mismatch");
        }
        if (selectedRuleIds.isEmpty()) {
            selectedRuleIds.add("retain-with-current-owner");
        }

        return rules.stream()
                .filter(rule -> selectedRuleIds.contains(rule.id()))
                .map(rule -> new OperationalContextMatchedEntry<>(rule, scoreHandoffRule(rule, signals)))
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<HandoffRule> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id()))
                .limit(properties.getMaxHandoffRules())
                .toList();
    }

    private OperationalContextMatchScore scoreSystem(
            Map<String, Object> system,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, system, "system");
        addSignalMatches(score, signals, genericSignals(system), 10, 6, "signal");
        addSignalMatches(score, signals, textList(system, "references.processes"), 4, 2, "process");
        addSignalMatches(score, signals, textList(system, "references.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textList(system, "references.repositories"), 6, 4, "repository");
        return score;
    }

    private OperationalContextMatchScore scoreIntegration(
            Map<String, Object> integration,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, integration, "integration");
        addSignalMatches(score, signals, genericSignals(integration), 11, 7, "signal");
        addSignalMatches(score, signals, textList(integration, "references.processes"), 4, 2, "process");
        addSignalMatches(score, signals, textList(integration, "references.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textList(integration, "transport.protocols"), 4, 2, "protocol");

        var matchedSystemIds = matchedIds(systemMatches);
        if (containsAnyId(
                matchedSystemIds,
                text(integration, "participants.source.system"),
                firstOf(textList(integration, "participants.finalTargets"))
        )) {
            score.add(10, "sourceOrTargetSystemMatches");
        }
        if (anyOverlap(textList(integration, "participants.intermediarySystems"), matchedSystemIds)) {
            score.add(6, "intermediarySystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreRepository(
            Map<String, Object> repository,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, repository, "repository");
        addSignalMatches(score, signals, genericSignals(repository), 11, 7, "signal");
        addSignalMatch(score, signals, text(repository, "git.projectPath"), 12, 8, "projectPath");
        addSignalMatch(score, signals, text(repository, "git.project"), 10, 6, "project");
        addSignalMatch(score, signals, text(repository, "git.group"), 5, 2, "group");
        addSignalMatches(score, signals, textList(repository, "references.systems"), 6, 3, "system");
        addSignalMatches(score, signals, textList(repository, "references.processes"), 5, 3, "process");
        addSignalMatches(score, signals, textList(repository, "references.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.sourceRoots"), 10, 6, "sourceRoot");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.modulePaths"), 8, 5, "modulePath");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.importantPaths"), 8, 5, "importantPath");
        addSignalMatches(score, signals, textList(repository, "codeSearch.entrypoints"), 7, 4, "entrypoint");

        for (var module : mapList(repository, "modules")) {
            addIdentityMatches(score, signals, module, "module");
            addSignalMatches(score, signals, textList(module, "sourceRoots"), 8, 5, "moduleSourceRoot");
            addSignalMatches(score, signals, textList(module, "importantPaths"), 8, 5, "moduleImportantPath");
            addSignalMatches(score, signals, genericSignals(module), 8, 5, "moduleSignal");
        }

        if (anyOverlap(textList(repository, "references.systems"), matchedIds(systemMatches))) {
            score.add(8, "referenceSystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreProcess(
            Map<String, Object> process,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, process, "process");
        addSignalMatches(score, signals, textList(process, "participants.primarySystems"), 6, 3, "system");
        addSignalMatches(score, signals, textList(process, "participants.externalSystems"), 6, 3, "externalSystem");
        addSignalMatches(score, signals, textList(process, "references.repositories"), 6, 3, "repository");
        addSignalMatches(score, signals, textList(process, "references.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textList(process, "processBoundary.endsWhen"), 7, 4, "completionSignal");
        addSignalMatches(score, signals, textList(process, "outcomes.successArtifacts"), 5, 3, "successArtifact");

        for (var step : mapList(process, "processSteps")) {
            addIdentityMatches(score, signals, step, "step");
            addSignalMatches(score, signals, genericSignals(step), 8, 5, "stepSignal");
            addSignalMatches(score, signals, textList(step, "participants.systems"), 6, 3, "stepSystem");
        }

        if (anyOverlap(textList(process, "participants.primarySystems"), matchedIds(systemMatches))
                || anyOverlap(textList(process, "participants.externalSystems"), matchedIds(systemMatches))) {
            score.add(8, "processSystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreBoundedContext(
            Map<String, Object> boundedContext,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> processMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> repositoryMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, boundedContext, "boundedContext");
        addSignalMatches(score, signals, genericSignals(boundedContext), 9, 5, "signal");
        addSignalMatches(score, signals, textList(boundedContext, "references.systems"), 6, 3, "system");
        addSignalMatches(score, signals, textList(boundedContext, "references.repositories"), 6, 3, "repository");
        addSignalMatches(score, signals, textList(boundedContext, "references.processes"), 5, 3, "process");
        addSignalMatches(score, signals, textList(boundedContext, "references.terms"), 6, 3, "term");
        addSignalMatches(score, signals, textList(boundedContext, "operationalSignals.serviceNames"), 9, 5, "serviceName");
        addSignalMatches(score, signals, textList(boundedContext, "operationalSignals.endpointPrefixes"), 9, 5, "endpointPrefix");
        addSignalMatches(score, signals, textList(boundedContext, "operationalSignals.packagePrefixes"), 9, 5, "packagePrefix");
        for (var relation : mapList(boundedContext, "relations")) {
            addSignalMatch(score, signals, text(relation, "target"), 5, 3, "targetContext");
            addSignalMatches(score, signals, textList(relation, "via"), 6, 3, "relationVia");
        }

        if (anyOverlap(textList(boundedContext, "references.systems"), matchedIds(systemMatches))) {
            score.add(8, "scopeSystemMatches");
        }
        if (anyOverlap(textList(boundedContext, "references.processes"), matchedIds(processMatches))) {
            score.add(8, "scopeProcessMatches");
        }
        if (anyOverlap(textList(boundedContext, "references.repositories"), matchedIds(repositoryMatches))) {
            score.add(8, "scopeRepositoryMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreTeam(
            Map<String, Object> team,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> processMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> repositoryMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> boundedContextMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> integrationMatches
    ) {
        var score = new OperationalContextMatchScore();
        var teamId = text(team, "id");
        if (!StringUtils.hasText(teamId)) {
            return score;
        }

        addTeamOwnershipMatches(score, teamId, systemMatches, "system");
        addTeamOwnershipMatches(score, teamId, processMatches, "process");
        addTeamOwnershipMatches(score, teamId, repositoryMatches, "repository");
        addTeamOwnershipMatches(score, teamId, boundedContextMatches, "boundedContext");
        addTeamOwnershipMatches(score, teamId, integrationMatches, "integration");

        if (anyOverlap(textList(team, "references.systems"), matchedIds(systemMatches))) {
            score.add(8, "referencesMatchedSystem");
        }
        if (anyOverlap(textList(team, "references.processes"), matchedIds(processMatches))) {
            score.add(8, "referencesMatchedProcess");
        }
        if (anyOverlap(textList(team, "references.repositories"), matchedIds(repositoryMatches))) {
            score.add(8, "referencesMatchedRepository");
        }
        if (anyOverlap(textList(team, "references.boundedContexts"), matchedIds(boundedContextMatches))) {
            score.add(8, "referencesMatchedBoundedContext");
        }
        if (anyOverlap(textList(team, "references.integrations"), matchedIds(integrationMatches))) {
            score.add(8, "referencesMatchedIntegration");
        }

        return score;
    }

    private OperationalContextMatchScore scoreGlossaryTerm(
            GlossaryTerm term,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        addSignalMatch(score, signals, term.id(), 8, 4, "termId");
        addSignalMatch(score, signals, term.term(), 9, 5, "term");
        addSignalMatch(score, signals, term.category(), 3, 1, "termCategory");
        addSignalMatch(score, signals, term.definition(), 3, 1, "definition");
        addSignalMatches(score, signals, term.useInContext(), 4, 2, "useContext");
        addSignalMatches(score, signals, term.doNotConfuseWith(), 4, 2, "doNotConfuse");
        addSignalMatches(score, signals, term.matchSignals(), 8, 5, "matchSignal");
        addSignalMatches(score, signals, term.canonicalReferences(), 6, 3, "canonicalReference");
        addSignalMatches(score, signals, term.synonyms(), 7, 4, "synonym");
        addSignalMatches(score, signals, term.notes(), 3, 1, "note");
        return score;
    }

    private OperationalContextMatchScore scoreHandoffRule(
            HandoffRule rule,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        score.add("retain-with-current-owner".equals(rule.id()) ? 4 : 8, "selectedByIncidentPattern");
        addSignalMatch(score, signals, rule.id(), 6, 3, "ruleId");
        addSignalMatch(score, signals, rule.title(), 5, 2, "ruleTitle");
        addSignalMatch(score, signals, rule.routeTo(), 5, 2, "routeTo");
        addSignalMatches(score, signals, rule.useWhen(), 4, 2, "useWhen");
        addSignalMatches(score, signals, rule.doNotUseWhen(), 3, 1, "doNotUseWhen");
        addSignalMatches(score, signals, rule.requiredEvidence(), 3, 1, "requiredEvidence");
        addSignalMatches(score, signals, rule.expectedFirstAction(), 3, 1, "firstAction");
        addSignalMatches(score, signals, rule.partnerTeams(), 4, 2, "partnerTeam");
        addSignalMatches(score, signals, rule.notes(), 2, 1, "ruleNote");
        return score;
    }

    private void addIdentityMatches(
            OperationalContextMatchScore score,
            OperationalContextIncidentSignals signals,
            Map<String, Object> entry,
            String label
    ) {
        addSignalMatch(score, signals, text(entry, "id"), 10, 5, label + "Id");
        addSignalMatch(score, signals, text(entry, "name"), 7, 3, label + "Name");
        addSignalMatch(score, signals, text(entry, "shortName"), 6, 3, label + "ShortName");
    }

    private void addTeamOwnershipMatches(
            OperationalContextMatchScore score,
            String teamId,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches,
            String relationLabel
    ) {
        for (var match : matches) {
            var entry = match.entry();
            var entryId = text(entry, "id");
            if (containsId(entry, "references.teams", teamId)
                    || responsibilityTeamIds(entry).contains(normalize(teamId))) {
                score.add(12, relationLabel + "Owner=" + entryId);
            }
            if (containsId(entry, "handoffHints.partnerTeamIds", teamId)) {
                score.add(7, relationLabel + "Partner=" + entryId);
            }
        }
    }

    private List<String> responsibilityTeamIds(Map<String, Object> entry) {
        return mapList(entry, "responsibilities").stream()
                .map(responsibility -> text(responsibility, "teamId"))
                .filter(StringUtils::hasText)
                .map(value -> normalize(value))
                .toList();
    }

    private String firstOf(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private void addSignalMatches(
            OperationalContextMatchScore score,
            OperationalContextIncidentSignals signals,
            List<String> candidates,
            int exactPoints,
            int containsPoints,
            String reasonPrefix
    ) {
        for (var candidate : candidates) {
            addSignalMatch(score, signals, candidate, exactPoints, containsPoints, reasonPrefix);
        }
    }

    private void addSignalMatch(
            OperationalContextMatchScore score,
            OperationalContextIncidentSignals signals,
            String candidate,
            int exactPoints,
            int containsPoints,
            String reasonPrefix
    ) {
        var normalizedCandidate = normalize(candidate);
        if (!StringUtils.hasText(normalizedCandidate)
                || !OperationalContextMatchingSupport.isMeaningfulSignal(normalizedCandidate)) {
            return;
        }

        if (signals.containsExact(candidate)) {
            score.add(exactPoints, reasonPrefix + "=" + candidate);
        } else if (signals.contains(candidate)) {
            score.add(containsPoints, reasonPrefix + "=" + candidate);
        }
    }

}
