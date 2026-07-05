package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.textList;

public final class OperationalContextDtos {

    public static final String CODE_SEARCH_MODE_WHOLE_REPOSITORY = "whole-repository";
    public static final String CODE_SEARCH_MODE_PATH_PREFIXES = "path-prefixes";

    private OperationalContextDtos() {
    }

    public interface OperationalContextEntry {

        String id();

        String name();

        String shortName();

        String summary();

        String purpose();

        List<String> aliases();

        List<String> useFor();

        OperationalContextReferences references();

        OperationalContextMatchSignals matchSignals();

        List<OperationalContextRelation> relations();

        Map<String, Object> payload();

        default String label() {
            return firstNonBlank(name(), shortName(), id());
        }

        default List<String> genericSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(aliases());
            values.addAll(useFor());
            values.addAll(matchSignals().allValues());
            return List.copyOf(values);
        }

        default List<String> values(String path) {
            return textList(payload(), path);
        }

        default String value(String path) {
            return text(payload(), path);
        }
    }

    public record OperationalContextCatalog(
            List<OperationalContextTeam> teams,
            List<OperationalContextProcess> processes,
            List<OperationalContextSystem> systems,
            List<OperationalContextIntegration> integrations,
            List<OperationalContextRepository> repositories,
            List<OperationalContextRepositorySearchScope> codeSearchScopes,
            List<OperationalContextBoundedContext> boundedContexts,
            List<OperationalContextGlossaryTerm> glossaryTerms,
            List<OperationalContextHandoffRule> handoffRules,
            List<OperationalContextOpenQuestion> openQuestions,
            String indexDocument
    ) {

        public OperationalContextCatalog {
            teams = copyList(teams);
            processes = copyList(processes);
            systems = copyList(systems);
            integrations = copyList(integrations);
            repositories = copyList(repositories);
            codeSearchScopes = copyList(codeSearchScopes);
            boundedContexts = copyList(boundedContexts);
            glossaryTerms = copyList(glossaryTerms);
            handoffRules = copyList(handoffRules);
            openQuestions = copyList(openQuestions);
            indexDocument = indexDocument != null ? indexDocument : "";
        }

        public OperationalContextCatalog(
                List<OperationalContextTeam> teams,
                List<OperationalContextProcess> processes,
                List<OperationalContextSystem> systems,
                List<OperationalContextIntegration> integrations,
                List<OperationalContextRepository> repositories,
                List<OperationalContextBoundedContext> boundedContexts,
                List<OperationalContextGlossaryTerm> glossaryTerms,
                List<OperationalContextHandoffRule> handoffRules,
                List<OperationalContextOpenQuestion> openQuestions,
                String indexDocument
        ) {
            this(
                    teams,
                    processes,
                    systems,
                    integrations,
                    repositories,
                    List.of(),
                    boundedContexts,
                    glossaryTerms,
                    handoffRules,
                    openQuestions,
                    indexDocument
            );
        }

        public OperationalContextCatalog(
                List<OperationalContextTeam> teams,
                List<OperationalContextProcess> processes,
                List<OperationalContextSystem> systems,
                List<OperationalContextIntegration> integrations,
                List<OperationalContextRepository> repositories,
                List<OperationalContextBoundedContext> boundedContexts,
                List<OperationalContextGlossaryTerm> glossaryTerms,
                List<OperationalContextHandoffRule> handoffRules,
                String indexDocument
        ) {
            this(
                    teams,
                    processes,
                    systems,
                    integrations,
                    repositories,
                    List.of(),
                    boundedContexts,
                    glossaryTerms,
                    handoffRules,
                    List.of(),
                    indexDocument
            );
        }

        public static OperationalContextCatalog empty() {
            return new OperationalContextCatalog(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ""
            );
        }
    }

    public record OperationalContextSystem(
            String id,
            String name,
            String shortName,
            String kind,
            String lifecycleStatus,
            String operationalStatus,
            String criticality,
            String summary,
            String purpose,
            List<String> aliases,
            List<String> useFor,
            OperationalContextSystemParticipants participants,
            OperationalContextOwnership ownership,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            List<OperationalContextRelation> relations,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextSystem {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            participants = participants != null ? participants : OperationalContextSystemParticipants.empty();
            ownership = defaultOwnership(ownership);
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            relations = copyList(relations);
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(aliases);
            values.addAll(useFor);
            values.addAll(matchSignals.allValues());
            return copyTextList(values);
        }
    }

    public record OperationalContextRepository(
            String id,
            String name,
            String shortName,
            String repositoryType,
            String lifecycleStatus,
            String criticality,
            String summary,
            String purpose,
            List<String> aliases,
            List<String> useFor,
            OperationalContextGit git,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            List<OperationalContextRelation> relations,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextRepository {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            git = git != null ? git : OperationalContextGit.empty();
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            relations = copyList(relations);
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>(OperationalContextEntry.super.genericSignals());
            values.add(git.provider());
            values.add(git.group());
            values.add(git.project());
            values.add(git.projectPath());
            values.addAll(git.aliases());
            return copyTextList(values);
        }
    }

    public record OperationalContextProcess(
            String id,
            String name,
            String shortName,
            String type,
            String lifecycleStatus,
            String criticality,
            String summary,
            String purpose,
            List<String> aliases,
            List<String> useFor,
            OperationalContextProcessParticipants participants,
            OperationalContextProcessBoundary processBoundary,
            OperationalContextProcessOutcomes outcomes,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            List<OperationalContextRelation> relations,
            List<OperationalContextProcessStep> steps,
            List<String> failureModes,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextProcess {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            participants = participants != null ? participants : OperationalContextProcessParticipants.empty();
            processBoundary = processBoundary != null ? processBoundary : OperationalContextProcessBoundary.empty();
            outcomes = outcomes != null ? outcomes : OperationalContextProcessOutcomes.empty();
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            relations = copyList(relations);
            steps = copyList(steps);
            failureModes = copyList(failureModes);
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>(OperationalContextEntry.super.genericSignals());
            values.addAll(participants.primarySystems());
            values.addAll(participants.externalSystems());
            values.addAll(processBoundary.endsWhen());
            values.addAll(outcomes.successArtifacts());
            values.addAll(failureModes);
            steps.forEach(step -> values.addAll(step.genericSignals()));
            return copyTextList(values);
        }
    }

    public record OperationalContextIntegration(
            String id,
            String name,
            String shortName,
            String category,
            String lifecycleStatus,
            String summary,
            String purpose,
            String integrationStyle,
            String flowDirection,
            String criticality,
            List<String> aliases,
            List<String> useFor,
            OperationalContextIntegrationParticipants participants,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            List<OperationalContextRelation> relations,
            List<String> failureModes,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextIntegration {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            participants = participants != null ? participants : OperationalContextIntegrationParticipants.empty();
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            relations = copyList(relations);
            failureModes = copyList(failureModes);
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>(OperationalContextEntry.super.genericSignals());
            values.addAll(failureModes);
            return copyTextList(values);
        }
    }

    public record OperationalContextBoundedContext(
            String id,
            String name,
            String shortName,
            String lifecycleStatus,
            String summary,
            String purpose,
            List<String> aliases,
            List<String> useFor,
            OperationalContextOwnership ownership,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            List<OperationalContextRelation> relations,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextBoundedContext {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            ownership = defaultOwnership(ownership);
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            relations = copyList(relations);
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>(OperationalContextEntry.super.genericSignals());
            relations.forEach(relation -> {
                values.add(relation.target());
                values.addAll(relation.via());
            });
            return copyTextList(values);
        }
    }

    public record OperationalContextTeam(
            String id,
            String name,
            String shortName,
            String lifecycleStatus,
            String summary,
            String purpose,
            List<String> aliases,
            List<String> useFor,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            List<OperationalContextRelation> relations,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextTeam {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            relations = copyList(relations);
            payload = copyMap(payload);
        }
    }

    public record OperationalContextReferences(
            List<String> systems,
            List<String> repositories,
            List<String> processes,
            List<String> boundedContexts,
            List<String> integrations,
            List<String> terms,
            List<String> teams,
            List<String> handoffRules
    ) {

        public OperationalContextReferences {
            systems = copyList(systems);
            repositories = copyList(repositories);
            processes = copyList(processes);
            boundedContexts = copyList(boundedContexts);
            integrations = copyList(integrations);
            terms = copyList(terms);
            teams = copyList(teams);
            handoffRules = copyList(handoffRules);
        }

        public static OperationalContextReferences empty() {
            return new OperationalContextReferences(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record OperationalContextOwnership(
            List<String> ownerTeamIds,
            String ownerLabel,
            String ownershipStatus,
            String confidence,
            String source,
            List<String> notes
    ) {

        public OperationalContextOwnership {
            ownerTeamIds = copyList(ownerTeamIds);
            ownershipStatus = firstNonBlank(ownershipStatus, "unknown");
            confidence = firstNonBlank(confidence, "low");
            notes = copyList(notes);
        }

        public static OperationalContextOwnership empty() {
            return new OperationalContextOwnership(
                    List.of(),
                    null,
                    "unknown",
                    "low",
                    null,
                    List.of()
            );
        }

        public boolean hasOwner() {
            return !ownerTeamIds.isEmpty() || StringUtils.hasText(ownerLabel);
        }
    }

    public record OperationalContextSystemParticipants(String externalOwner) {

        public static OperationalContextSystemParticipants empty() {
            return new OperationalContextSystemParticipants(null);
        }
    }

    public record OperationalContextMatchSignals(
            OperationalContextSignalSet exact,
            OperationalContextSignalSet strong,
            OperationalContextSignalSet medium,
            OperationalContextSignalSet weak
    ) {

        public OperationalContextMatchSignals {
            exact = exact != null ? exact : OperationalContextSignalSet.empty();
            strong = strong != null ? strong : OperationalContextSignalSet.empty();
            medium = medium != null ? medium : OperationalContextSignalSet.empty();
            weak = weak != null ? weak : OperationalContextSignalSet.empty();
        }

        public static OperationalContextMatchSignals empty() {
            return new OperationalContextMatchSignals(
                    OperationalContextSignalSet.empty(),
                    OperationalContextSignalSet.empty(),
                    OperationalContextSignalSet.empty(),
                    OperationalContextSignalSet.empty()
            );
        }

        public List<String> allValues() {
            var values = new LinkedHashSet<String>();
            values.addAll(exact.allValues());
            values.addAll(strong.allValues());
            values.addAll(medium.allValues());
            values.addAll(weak.allValues());
            return copyTextList(values);
        }

        public List<String> valuesForKeys(String... keys) {
            var values = new LinkedHashSet<String>();
            for (var key : keys) {
                values.addAll(exact.values(key));
                values.addAll(strong.values(key));
                values.addAll(medium.values(key));
                values.addAll(weak.values(key));
            }
            return copyTextList(values);
        }

        public OperationalContextMatchSignals onlyKeys(Collection<String> keys) {
            return new OperationalContextMatchSignals(
                    exact.onlyKeys(keys),
                    strong.onlyKeys(keys),
                    medium.onlyKeys(keys),
                    weak.onlyKeys(keys)
            );
        }
    }

    public record OperationalContextSignalSet(Map<String, List<String>> valuesByKey) {

        public OperationalContextSignalSet {
            valuesByKey = copyStringListMap(valuesByKey);
        }

        public static OperationalContextSignalSet empty() {
            return new OperationalContextSignalSet(Map.of());
        }

        public List<String> values(String key) {
            return valuesByKey.getOrDefault(key, List.of());
        }

        public List<String> allValues() {
            return valuesByKey.values().stream()
                    .flatMap(Collection::stream)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }

        public OperationalContextSignalSet onlyKeys(Collection<String> keys) {
            if (keys == null || keys.isEmpty()) {
                return empty();
            }
            var allowed = new LinkedHashSet<>(keys);
            var filtered = new LinkedHashMap<String, List<String>>();
            valuesByKey.forEach((key, values) -> {
                if (allowed.contains(key)) {
                    filtered.put(key, values);
                }
            });
            return new OperationalContextSignalSet(filtered);
        }

        public List<String> projectNames() {
            return values("projectNames");
        }

        public List<String> projectPaths() {
            return values("projectPaths");
        }
    }

    public record OperationalContextGit(
            String provider,
            String group,
            String project,
            String projectPath,
            String defaultBranch,
            String url,
            List<String> aliases,
            boolean inferred
    ) {

        public OperationalContextGit {
            aliases = copyList(aliases);
        }

        public static OperationalContextGit empty() {
            return new OperationalContextGit(null, null, null, null, null, null, List.of(), false);
        }
    }

    public record OperationalContextProcessParticipants(
            List<String> actors,
            List<String> primarySystems,
            List<String> supportingSystems,
            List<String> externalSystems,
            List<String> platformComponents
    ) {

        public OperationalContextProcessParticipants {
            actors = copyList(actors);
            primarySystems = copyList(primarySystems);
            supportingSystems = copyList(supportingSystems);
            externalSystems = copyList(externalSystems);
            platformComponents = copyList(platformComponents);
        }

        public static OperationalContextProcessParticipants empty() {
            return new OperationalContextProcessParticipants(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record OperationalContextProcessBoundary(List<String> endsWhen) {

        public OperationalContextProcessBoundary {
            endsWhen = copyList(endsWhen);
        }

        public static OperationalContextProcessBoundary empty() {
            return new OperationalContextProcessBoundary(List.of());
        }
    }

    public record OperationalContextProcessOutcomes(List<String> successArtifacts) {

        public OperationalContextProcessOutcomes {
            successArtifacts = copyList(successArtifacts);
        }

        public static OperationalContextProcessOutcomes empty() {
            return new OperationalContextProcessOutcomes(List.of());
        }
    }

    public record OperationalContextProcessStep(
            String id,
            String name,
            String type,
            String summary,
            OperationalContextReferences references,
            OperationalContextMatchSignals matchSignals,
            Map<String, Object> payload
    ) {

        public OperationalContextProcessStep {
            references = defaultReferences(references);
            matchSignals = defaultMatchSignals(matchSignals);
            payload = copyMap(payload);
        }

        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>();
            values.add(id);
            values.add(name);
            values.add(type);
            values.add(summary);
            values.addAll(matchSignals.allValues());
            return copyTextList(values);
        }
    }

    public record OperationalContextIntegrationParticipants(
            OperationalContextIntegrationParticipant source,
            List<OperationalContextIntegrationParticipant> targets,
            List<OperationalContextIntegrationParticipant> intermediaries,
            List<OperationalContextIntegrationParticipant> finalTargets
    ) {

        public OperationalContextIntegrationParticipants {
            source = source != null ? source : OperationalContextIntegrationParticipant.empty();
            targets = copyList(targets);
            intermediaries = copyList(intermediaries);
            finalTargets = copyList(finalTargets);
        }

        public static OperationalContextIntegrationParticipants empty() {
            return new OperationalContextIntegrationParticipants(
                    OperationalContextIntegrationParticipant.empty(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        public List<String> systems() {
            var values = new LinkedHashSet<String>();
            values.add(source.system());
            targets.forEach(target -> values.add(target.system()));
            intermediaries.forEach(intermediary -> values.add(intermediary.system()));
            finalTargets.forEach(target -> values.add(target.system()));
            return copyTextList(values);
        }

        public List<String> targetSystems() {
            var values = new LinkedHashSet<String>();
            targets.forEach(target -> values.add(target.system()));
            values.addAll(finalTargetSystems());
            return copyTextList(values);
        }

        public List<String> finalTargetSystems() {
            return finalTargets.stream()
                    .map(OperationalContextIntegrationParticipant::system)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        public List<String> intermediarySystems() {
            return intermediaries.stream()
                    .map(OperationalContextIntegrationParticipant::system)
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }

    public record OperationalContextIntegrationParticipant(
            String system,
            String boundedContext,
            List<String> repositories,
            String role,
            String externalOwner,
            List<String> notes
    ) {

        public OperationalContextIntegrationParticipant {
            repositories = copyList(repositories);
            notes = copyList(notes);
        }

        public static OperationalContextIntegrationParticipant empty() {
            return new OperationalContextIntegrationParticipant(
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    List.of()
            );
        }
    }

    public record OperationalContextRelation(
            String type,
            String targetType,
            String targetContextId,
            String target,
            List<String> via,
            String evidence
    ) {

        public OperationalContextRelation {
            via = copyList(via);
        }
    }

    public record OperationalContextRepositorySearchScope(
            String id,
            String name,
            String scopeType,
            String lifecycleStatus,
            String summary,
            OperationalContextRepositorySearchTarget target,
            List<String> useFor,
            List<OperationalContextRepositorySearchRepository> repositories,
            List<String> limitations,
            Map<String, Object> payload
    ) {

        public OperationalContextRepositorySearchScope {
            target = target != null ? target : OperationalContextRepositorySearchTarget.empty();
            useFor = copyList(useFor);
            repositories = copyList(repositories);
            limitations = copyList(limitations);
            payload = copyMap(payload);
        }
    }

    public record OperationalContextRepositorySearchTarget(
            String type,
            String id
    ) {

        public String value() {
            return StringUtils.hasText(type) && StringUtils.hasText(id)
                    ? type + ":" + id
                    : null;
        }

        public static OperationalContextRepositorySearchTarget empty() {
            return new OperationalContextRepositorySearchTarget(null, null);
        }
    }

    public record OperationalContextRepositorySearchRepository(
            String repoId,
            String role,
            Integer priority,
            String reason,
            List<String> readFor,
            String searchMode,
            List<String> pathPrefixes
    ) {

        public OperationalContextRepositorySearchRepository {
            readFor = copyList(readFor);
            pathPrefixes = copyList(pathPrefixes);
        }
    }

    public record OperationalContextGlossaryTerm(
            String id,
            String term,
            String category,
            String definition,
            List<String> useInContext,
            List<String> doNotConfuseWith,
            List<String> matchSignals,
            List<String> canonicalReferences,
            List<String> synonyms,
            List<String> notes
    ) {

        public OperationalContextGlossaryTerm {
            useInContext = copyList(useInContext);
            doNotConfuseWith = copyList(doNotConfuseWith);
            matchSignals = copyList(matchSignals);
            canonicalReferences = copyList(canonicalReferences);
            synonyms = copyList(synonyms);
            notes = copyList(notes);
        }
    }

    public record OperationalContextHandoffRule(
            String id,
            String title,
            List<String> useWhen,
            List<String> doNotUseWhen,
            List<String> requiredEvidence,
            List<String> expectedFirstAction,
            OperationalContextReferences references,
            List<String> notes
    ) {

        public OperationalContextHandoffRule(
                String id,
                String title,
                List<String> useWhen,
                List<String> doNotUseWhen,
                List<String> requiredEvidence,
                List<String> expectedFirstAction,
                List<String> notes
        ) {
            this(
                    id,
                    title,
                    useWhen,
                    doNotUseWhen,
                    requiredEvidence,
                    expectedFirstAction,
                    OperationalContextReferences.empty(),
                    notes
            );
        }

        public OperationalContextHandoffRule {
            useWhen = copyList(useWhen);
            doNotUseWhen = copyList(doNotUseWhen);
            requiredEvidence = copyList(requiredEvidence);
            expectedFirstAction = copyList(expectedFirstAction);
            references = defaultReferences(references);
            notes = copyList(notes);
        }
    }

    public record OperationalContextOpenQuestion(
            String id,
            String sourceFile,
            String entityType,
            String entityId,
            String question,
            String severity,
            String status
    ) {
    }

    public static OperationalContextCatalog catalogFromRaw(
            List<Map<String, Object>> teams,
            List<Map<String, Object>> processes,
            List<Map<String, Object>> systems,
            List<Map<String, Object>> integrations,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> codeSearchScopes,
            List<Map<String, Object>> boundedContexts,
            List<OperationalContextGlossaryTerm> glossaryTerms,
            List<OperationalContextHandoffRule> handoffRules,
            List<OperationalContextOpenQuestion> openQuestions,
            String indexDocument
    ) {
        return new OperationalContextCatalog(
                copyList(teams).stream().map(OperationalContextDtos::team).toList(),
                copyList(processes).stream().map(OperationalContextDtos::process).toList(),
                copyList(systems).stream().map(OperationalContextDtos::system).toList(),
                copyList(integrations).stream().map(OperationalContextDtos::integration).toList(),
                copyList(repositories).stream().map(OperationalContextDtos::repository).toList(),
                copyList(codeSearchScopes).stream().map(OperationalContextDtos::repositorySearchScope).toList(),
                copyList(boundedContexts).stream().map(OperationalContextDtos::boundedContext).toList(),
                glossaryTerms,
                handoffRules,
                openQuestions,
                indexDocument
        );
    }

    public static OperationalContextCatalog catalogFromRaw(
            List<Map<String, Object>> teams,
            List<Map<String, Object>> processes,
            List<Map<String, Object>> systems,
            List<Map<String, Object>> integrations,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> boundedContexts,
            List<OperationalContextGlossaryTerm> glossaryTerms,
            List<OperationalContextHandoffRule> handoffRules,
            List<OperationalContextOpenQuestion> openQuestions,
            String indexDocument
    ) {
        return catalogFromRaw(
                teams,
                processes,
                systems,
                integrations,
                repositories,
                List.of(),
                boundedContexts,
                glossaryTerms,
                handoffRules,
                openQuestions,
                indexDocument
        );
    }

    public static OperationalContextCatalog catalogFromRaw(
            List<Map<String, Object>> teams,
            List<Map<String, Object>> processes,
            List<Map<String, Object>> systems,
            List<Map<String, Object>> integrations,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> boundedContexts,
            List<OperationalContextGlossaryTerm> glossaryTerms,
            List<OperationalContextHandoffRule> handoffRules,
            String indexDocument
    ) {
        return catalogFromRaw(
                teams,
                processes,
                systems,
                integrations,
                repositories,
                boundedContexts,
                glossaryTerms,
                handoffRules,
                List.of(),
                indexDocument
        );
    }

    public static OperationalContextSystem system(Map<String, Object> source) {
        return new OperationalContextSystem(
                text(source, "id"),
                text(source, "name"),
                text(source, "shortName"),
                firstNonBlank(text(source, "kind"), text(source, "type"), text(source, "systemType")),
                text(source, "lifecycleStatus"),
                text(source, "operationalStatus"),
                text(source, "criticality"),
                text(source, "summary"),
                text(source, "purpose"),
                textList(source, "aliases"),
                textList(source, "useFor"),
                systemParticipants(source.get("participants")),
                ownership(source.get("ownership")),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                relations(source.get("relations")),
                source
        );
    }

    public static OperationalContextRepository repository(Map<String, Object> source) {
        return new OperationalContextRepository(
                text(source, "id"),
                text(source, "name"),
                text(source, "shortName"),
                text(source, "repositoryType"),
                text(source, "lifecycleStatus"),
                text(source, "criticality"),
                text(source, "summary"),
                text(source, "purpose"),
                textList(source, "aliases"),
                textList(source, "useFor"),
                git(source.get("git")),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                relations(source.get("relations")),
                source
        );
    }

    public static OperationalContextRepositorySearchScope repositorySearchScope(Map<String, Object> source) {
        return new OperationalContextRepositorySearchScope(
                text(source, "id"),
                text(source, "name"),
                text(source, "scopeType"),
                text(source, "lifecycleStatus"),
                text(source, "summary"),
                repositorySearchTarget(source.get("target")),
                textList(source, "useFor"),
                mapList(source, "repositories").stream()
                        .map(OperationalContextDtos::repositorySearchRepository)
                        .toList(),
                textList(source, "limitations"),
                source
        );
    }

    public static OperationalContextProcess process(Map<String, Object> source) {
        var steps = new ArrayList<OperationalContextProcessStep>();
        steps.addAll(mapList(source, "steps").stream().map(OperationalContextDtos::processStep).toList());
        steps.addAll(mapList(source, "processSteps").stream().map(OperationalContextDtos::processStep).toList());
        return new OperationalContextProcess(
                text(source, "id"),
                text(source, "name"),
                text(source, "shortName"),
                text(source, "type"),
                text(source, "lifecycleStatus"),
                text(source, "criticality"),
                text(source, "summary"),
                text(source, "purpose"),
                textList(source, "aliases"),
                textList(source, "useFor"),
                processParticipants(source.get("participants")),
                processBoundary(source.get("processBoundary")),
                processOutcomes(source.get("outcomes")),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                relations(source.get("relations")),
                steps,
                textList(source, "failureModes"),
                source
        );
    }

    public static OperationalContextIntegration integration(Map<String, Object> source) {
        return new OperationalContextIntegration(
                text(source, "id"),
                text(source, "name"),
                text(source, "shortName"),
                text(source, "category"),
                text(source, "lifecycleStatus"),
                text(source, "summary"),
                text(source, "purpose"),
                text(source, "integrationStyle"),
                text(source, "flowDirection"),
                text(source, "criticality"),
                textList(source, "aliases"),
                textList(source, "useFor"),
                integrationParticipants(source.get("participants")),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                relations(source.get("relations")),
                textList(source, "failureModes"),
                source
        );
    }

    public static OperationalContextBoundedContext boundedContext(Map<String, Object> source) {
        return new OperationalContextBoundedContext(
                text(source, "id"),
                text(source, "name"),
                text(source, "shortName"),
                text(source, "lifecycleStatus"),
                text(source, "summary"),
                text(source, "purpose"),
                textList(source, "aliases"),
                textList(source, "useFor"),
                ownership(source.get("ownership")),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                relations(source.get("relations")),
                source
        );
    }

    public static OperationalContextTeam team(Map<String, Object> source) {
        return new OperationalContextTeam(
                text(source, "id"),
                text(source, "name"),
                text(source, "shortName"),
                text(source, "lifecycleStatus"),
                text(source, "summary"),
                text(source, "purpose"),
                textList(source, "aliases"),
                textList(source, "useFor"),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                relations(source.get("relations")),
                source
        );
    }

    private static OperationalContextReferences references(Object value) {
        var source = map(value);
        return new OperationalContextReferences(
                textList(source, "systems"),
                textList(source, "repositories"),
                textList(source, "processes"),
                textList(source, "boundedContexts"),
                textList(source, "integrations"),
                textList(source, "terms"),
                textList(source, "teams"),
                textList(source, "handoffRules")
        );
    }

    private static OperationalContextOwnership ownership(Object value) {
        var source = map(value);
        return new OperationalContextOwnership(
                textList(source, "ownerTeamIds"),
                text(source, "ownerLabel"),
                text(source, "ownershipStatus"),
                text(source, "confidence"),
                text(source, "source"),
                textList(source, "notes")
        );
    }

    private static OperationalContextSystemParticipants systemParticipants(Object value) {
        var source = map(value);
        return new OperationalContextSystemParticipants(
                text(source, "externalOwner")
        );
    }

    private static OperationalContextMatchSignals matchSignals(Object value) {
        var source = map(value);
        if (!source.containsKey("exact")
                && !source.containsKey("strong")
                && !source.containsKey("medium")
                && !source.containsKey("weak")) {
            return new OperationalContextMatchSignals(
                    OperationalContextSignalSet.empty(),
                    signalSet(source),
                    OperationalContextSignalSet.empty(),
                    OperationalContextSignalSet.empty()
            );
        }
        return new OperationalContextMatchSignals(
                signalSet(source.get("exact")),
                signalSet(source.get("strong")),
                signalSet(source.get("medium")),
                signalSet(source.get("weak"))
        );
    }

    private static OperationalContextSignalSet signalSet(Object value) {
        var source = map(value);
        var signals = new LinkedHashMap<String, List<String>>();
        source.forEach((key, item) -> signals.put(key, textList(item)));
        return new OperationalContextSignalSet(signals);
    }

    private static OperationalContextGit git(Object value) {
        var source = map(value);
        return new OperationalContextGit(
                firstText(source, "provider"),
                firstText(source, "group"),
                firstText(source, "project"),
                firstText(source, "projectPath"),
                firstText(source, "defaultBranch"),
                firstText(source, "url"),
                textList(source, "aliases"),
                Boolean.parseBoolean(firstNonBlank(firstText(source, "inferred"), "false"))
        );
    }

    private static OperationalContextProcessParticipants processParticipants(Object value) {
        var source = map(value);
        return new OperationalContextProcessParticipants(
                textList(source, "actors"),
                textList(source, "primarySystems"),
                textList(source, "supportingSystems"),
                textList(source, "externalSystems"),
                textList(source, "platformComponents")
        );
    }

    private static OperationalContextProcessBoundary processBoundary(Object value) {
        var source = map(value);
        return new OperationalContextProcessBoundary(
                textList(source, "endsWhen")
        );
    }

    private static OperationalContextProcessOutcomes processOutcomes(Object value) {
        var source = map(value);
        return new OperationalContextProcessOutcomes(
                textList(source, "successArtifacts")
        );
    }

    private static OperationalContextProcessStep processStep(Map<String, Object> source) {
        return new OperationalContextProcessStep(
                text(source, "id"),
                text(source, "name"),
                text(source, "type"),
                text(source, "summary"),
                references(source.get("references")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                source
        );
    }

    private static OperationalContextIntegrationParticipants integrationParticipants(Object value) {
        var source = map(value);
        return new OperationalContextIntegrationParticipants(
                integrationParticipant(source.get("source")),
                integrationParticipantList(source.get("targets")),
                integrationParticipantList(source.get("intermediaries")),
                integrationParticipantList(source.get("finalTargets"))
        );
    }

    private static List<OperationalContextIntegrationParticipant> integrationParticipantList(Object value) {
        if (value == null) {
            return List.of();
        }

        var participants = new ArrayList<OperationalContextIntegrationParticipant>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addIntegrationParticipant(participants, item));
        } else {
            addIntegrationParticipant(participants, value);
        }
        return List.copyOf(participants);
    }

    private static void addIntegrationParticipant(
            List<OperationalContextIntegrationParticipant> participants,
            Object value
    ) {
        var participant = integrationParticipant(value);
        if (StringUtils.hasText(participant.system())) {
            participants.add(participant);
        }
    }

    private static OperationalContextIntegrationParticipant integrationParticipant(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return new OperationalContextIntegrationParticipant(
                    text(value),
                    null,
                    List.of(),
                    null,
                    null,
                    List.of()
            );
        }
        var source = map(value);
        return new OperationalContextIntegrationParticipant(
                text(source, "system"),
                text(source, "boundedContext"),
                textList(source, "repositories"),
                text(source, "role"),
                text(source, "externalOwner"),
                textList(source, "notes")
        );
    }

    private static List<OperationalContextRelation> relations(Object value) {
        return mapList(value).stream()
                .map(source -> new OperationalContextRelation(
                        text(source, "type"),
                        text(source, "targetType"),
                        text(source, "targetContextId"),
                        text(source, "target"),
                        textList(source, "via"),
                        text(source, "evidence")
                ))
                .toList();
    }

    private static OperationalContextRepositorySearchTarget repositorySearchTarget(Object value) {
        var source = map(value);
        return new OperationalContextRepositorySearchTarget(
                text(source, "type"),
                text(source, "id")
        );
    }

    private static OperationalContextRepositorySearchRepository repositorySearchRepository(Map<String, Object> source) {
        return new OperationalContextRepositorySearchRepository(
                text(source, "repoId"),
                text(source, "role"),
                integer(source, "priority"),
                text(source, "reason"),
                textList(source, "readFor"),
                text(source, "searchMode"),
                textList(source, "pathPrefixes")
        );
    }

    private static OperationalContextReferences defaultReferences(OperationalContextReferences value) {
        return value != null ? value : OperationalContextReferences.empty();
    }

    private static OperationalContextOwnership defaultOwnership(OperationalContextOwnership value) {
        return value != null ? value : OperationalContextOwnership.empty();
    }

    private static OperationalContextMatchSignals defaultMatchSignals(OperationalContextMatchSignals value) {
        return value != null ? value : OperationalContextMatchSignals.empty();
    }

    private static Map<String, Object> map(Object value) {
        var values = mapList(value);
        return values.isEmpty() ? Map.of() : values.get(0);
    }

    private static Object firstValue(Map<String, Object> source, String... paths) {
        for (var path : paths) {
            var value = source.get(path);
            if (value instanceof Collection<?> collection && collection.isEmpty()) {
                continue;
            }
            if (value instanceof Map<?, ?> map && map.isEmpty()) {
                continue;
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> source, String path) {
        var values = textList(source, path);
        return values.isEmpty() ? null : values.get(0);
    }

    private static Integer integer(Map<String, Object> source, String path) {
        var value = firstText(source, path);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean bool(Map<String, Object> source, String path, boolean defaultValue) {
        var value = firstText(source, path);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    @SafeVarargs
    private static List<String> copyTextList(Collection<String>... values) {
        var result = new LinkedHashSet<String>();
        for (var collection : values) {
            result.addAll(collection);
        }
        return copyTextList(result);
    }

    private static List<String> copyTextList(Collection<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<String> copyTextList(List<List<String>> values) {
        var result = new LinkedHashSet<String>();
        values.forEach(result::addAll);
        return copyTextList(result);
    }

    private static Map<String, List<String>> copyStringListMap(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, List<String>>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key)) {
                result.put(key, copyList(value));
            }
        });
        return Map.copyOf(result);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static <T> List<T> copyList(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
