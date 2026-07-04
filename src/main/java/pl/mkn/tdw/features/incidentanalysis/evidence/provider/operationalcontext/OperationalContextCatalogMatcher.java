package pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextProperties;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.anyOverlap;
import static pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.containsAnyId;
import static pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.genericSignals;
import static pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.matchedIds;

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

    private <T extends OperationalContextEntry> List<OperationalContextMatchedEntry<T>> matchEntries(
            List<T> entries,
            Function<T, OperationalContextMatchScore> scorer,
            int limit
    ) {
        return entries.stream()
                .map(entry -> new OperationalContextMatchedEntry<>(entry, scorer.apply(entry)))
                .filter(match -> match.score().score() > 0)
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<T> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id(), Comparator.nullsLast(String::compareTo)))
                .limit(limit)
                .toList();
    }

    private List<OperationalContextMatchedEntry<OperationalContextGlossaryTerm>> matchGlossaryTerms(
            List<OperationalContextGlossaryTerm> terms,
            OperationalContextIncidentSignals signals
    ) {
        return terms.stream()
                .map(term -> new OperationalContextMatchedEntry<>(term, scoreGlossaryTerm(term, signals)))
                .filter(match -> match.score().score() > 0)
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<OperationalContextGlossaryTerm> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id()))
                .limit(properties.getMaxGlossaryTerms())
                .toList();
    }

    private List<OperationalContextMatchedEntry<OperationalContextHandoffRule>> matchHandoffRules(
            List<OperationalContextHandoffRule> rules,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<OperationalContextIntegration>> integrationMatches,
            List<OperationalContextMatchedEntry<OperationalContextBoundedContext>> boundedContextMatches
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
                        .comparingInt((OperationalContextMatchedEntry<OperationalContextHandoffRule> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id()))
                .limit(properties.getMaxHandoffRules())
                .toList();
    }

    private OperationalContextMatchScore scoreSystem(
            OperationalContextSystem system,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, system, "system");
        addSignalMatches(score, signals, genericSignals(system), 10, 6, "signal");
        addSignalMatches(score, signals, system.references().processes(), 4, 2, "process");
        addSignalMatches(score, signals, system.references().boundedContexts(), 5, 3, "context");
        addSignalMatches(score, signals, system.references().repositories(), 6, 4, "repository");
        return score;
    }

    private OperationalContextMatchScore scoreIntegration(
            OperationalContextIntegration integration,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<OperationalContextSystem>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, integration, "integration");
        addSignalMatches(score, signals, genericSignals(integration), 11, 7, "signal");
        addSignalMatches(score, signals, integration.references().processes(), 4, 2, "process");
        addSignalMatches(score, signals, integration.references().boundedContexts(), 5, 3, "context");
        addSignalMatch(score, signals, integration.category(), 5, 3, "category");
        addSignalMatch(score, signals, integration.integrationStyle(), 5, 3, "integrationStyle");
        addSignalMatch(score, signals, integration.flowDirection(), 4, 2, "flowDirection");

        var matchedSystemIds = matchedIds(systemMatches);
        if (containsAnyId(matchedSystemIds, integration.participants().source().system())
                || anyOverlap(integration.participants().targetSystems(), matchedSystemIds)) {
            score.add(10, "sourceOrTargetSystemMatches");
        }
        if (anyOverlap(integration.participants().intermediarySystems(), matchedSystemIds)) {
            score.add(6, "intermediarySystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreRepository(
            OperationalContextRepository repository,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<OperationalContextSystem>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, repository, "repository");
        addSignalMatches(score, signals, genericSignals(repository), 11, 7, "signal");
        addSignalMatch(score, signals, repository.git().projectPath(), 12, 8, "projectPath");
        addSignalMatch(score, signals, repository.git().project(), 10, 6, "project");
        addSignalMatch(score, signals, repository.git().group(), 5, 2, "group");
        addSignalMatches(score, signals, repository.references().systems(), 6, 3, "system");
        addSignalMatches(score, signals, repository.references().processes(), 5, 3, "process");
        addSignalMatches(score, signals, repository.references().boundedContexts(), 5, 3, "context");

        if (anyOverlap(repository.references().systems(), matchedIds(systemMatches))) {
            score.add(8, "referenceSystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreProcess(
            OperationalContextProcess process,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<OperationalContextSystem>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, process, "process");
        addSignalMatches(score, signals, process.participants().primarySystems(), 6, 3, "system");
        addSignalMatches(score, signals, process.participants().externalSystems(), 6, 3, "externalSystem");
        addSignalMatches(score, signals, process.references().repositories(), 6, 3, "repository");
        addSignalMatches(score, signals, process.references().boundedContexts(), 5, 3, "context");
        addSignalMatches(score, signals, process.processBoundary().endsWhen(), 7, 4, "completionSignal");
        addSignalMatches(score, signals, process.outcomes().successArtifacts(), 5, 3, "successArtifact");

        for (var step : process.steps()) {
            addSignalMatch(score, signals, step.id(), 10, 5, "stepId");
            addSignalMatch(score, signals, step.name(), 7, 3, "stepName");
            addSignalMatches(score, signals, step.genericSignals(), 8, 5, "stepSignal");
            addSignalMatches(score, signals, step.references().systems(), 6, 3, "stepSystem");
        }

        if (anyOverlap(process.participants().primarySystems(), matchedIds(systemMatches))
                || anyOverlap(process.participants().externalSystems(), matchedIds(systemMatches))) {
            score.add(8, "processSystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreBoundedContext(
            OperationalContextBoundedContext boundedContext,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<OperationalContextSystem>> systemMatches,
            List<OperationalContextMatchedEntry<OperationalContextProcess>> processMatches,
            List<OperationalContextMatchedEntry<OperationalContextRepository>> repositoryMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, boundedContext, "boundedContext");
        addSignalMatches(score, signals, genericSignals(boundedContext), 9, 5, "signal");
        addSignalMatches(score, signals, boundedContext.references().systems(), 6, 3, "system");
        addSignalMatches(score, signals, boundedContext.references().repositories(), 6, 3, "repository");
        addSignalMatches(score, signals, boundedContext.references().processes(), 5, 3, "process");
        addSignalMatches(score, signals, boundedContext.references().terms(), 6, 3, "term");
        for (var relation : boundedContext.relations()) {
            addSignalMatch(score, signals, relation.target(), 5, 3, "targetContext");
            addSignalMatches(score, signals, relation.via(), 6, 3, "relationVia");
        }

        if (anyOverlap(boundedContext.references().systems(), matchedIds(systemMatches))) {
            score.add(8, "scopeSystemMatches");
        }
        if (anyOverlap(boundedContext.references().processes(), matchedIds(processMatches))) {
            score.add(8, "scopeProcessMatches");
        }
        if (anyOverlap(boundedContext.references().repositories(), matchedIds(repositoryMatches))) {
            score.add(8, "scopeRepositoryMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreTeam(
            OperationalContextTeam team,
            List<OperationalContextMatchedEntry<OperationalContextSystem>> systemMatches,
            List<OperationalContextMatchedEntry<OperationalContextProcess>> processMatches,
            List<OperationalContextMatchedEntry<OperationalContextRepository>> repositoryMatches,
            List<OperationalContextMatchedEntry<OperationalContextBoundedContext>> boundedContextMatches,
            List<OperationalContextMatchedEntry<OperationalContextIntegration>> integrationMatches
    ) {
        var score = new OperationalContextMatchScore();
        var teamId = team.id();
        if (!StringUtils.hasText(teamId)) {
            return score;
        }

        addTeamOwnershipMatches(score, teamId, systemMatches, "system");
        addTeamOwnershipMatches(score, teamId, processMatches, "process");
        addTeamOwnershipMatches(score, teamId, repositoryMatches, "repository");
        addTeamOwnershipMatches(score, teamId, boundedContextMatches, "boundedContext");
        addTeamOwnershipMatches(score, teamId, integrationMatches, "integration");

        if (anyOverlap(team.references().systems(), matchedIds(systemMatches))) {
            score.add(8, "referencesMatchedSystem");
        }
        if (anyOverlap(team.references().processes(), matchedIds(processMatches))) {
            score.add(8, "referencesMatchedProcess");
        }
        if (anyOverlap(team.references().repositories(), matchedIds(repositoryMatches))) {
            score.add(8, "referencesMatchedRepository");
        }
        if (anyOverlap(team.references().boundedContexts(), matchedIds(boundedContextMatches))) {
            score.add(8, "referencesMatchedBoundedContext");
        }
        if (anyOverlap(team.references().integrations(), matchedIds(integrationMatches))) {
            score.add(8, "referencesMatchedIntegration");
        }

        return score;
    }

    private OperationalContextMatchScore scoreGlossaryTerm(
            OperationalContextGlossaryTerm term,
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
            OperationalContextHandoffRule rule,
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
            OperationalContextEntry entry,
            String label
    ) {
        addSignalMatch(score, signals, entry.id(), 10, 5, label + "Id");
        addSignalMatch(score, signals, entry.name(), 7, 3, label + "Name");
        addSignalMatch(score, signals, entry.shortName(), 6, 3, label + "ShortName");
    }

    private void addTeamOwnershipMatches(
            OperationalContextMatchScore score,
            String teamId,
            List<? extends OperationalContextMatchedEntry<? extends OperationalContextEntry>> matches,
            String relationLabel
    ) {
        for (var match : matches) {
            var entry = match.entry();
            var entryId = entry.id();
            if (containsNormalizedId(entry.references().teams(), teamId)
                    || responsibilityTeamIds(entry).contains(normalize(teamId))) {
                score.add(12, relationLabel + "Owner=" + entryId);
            }
            if (containsNormalizedId(entry.handoffHints().partnerTeamIds(), teamId)) {
                score.add(7, relationLabel + "Partner=" + entryId);
            }
        }
    }

    private boolean containsNormalizedId(List<String> values, String id) {
        var normalizedId = normalize(id);
        return StringUtils.hasText(normalizedId)
                && values.stream()
                .map(value -> normalize(value))
                .anyMatch(normalizedId::equals);
    }

    private List<String> responsibilityTeamIds(OperationalContextEntry entry) {
        return entry.responsibilities().stream()
                .map(responsibility -> responsibility.teamId())
                .filter(StringUtils::hasText)
                .map(value -> normalize(value))
                .toList();
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
