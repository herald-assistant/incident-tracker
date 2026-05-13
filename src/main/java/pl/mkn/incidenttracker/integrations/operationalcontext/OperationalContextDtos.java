package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;

public final class OperationalContextDtos {

    private static final List<String> SYSTEM_RUNTIME_SIGNAL_KEYS = List.of(
            "serviceNames",
            "applicationNames",
            "containerNames",
            "deploymentNames",
            "namespaceNames",
            "imageNames",
            "artifactNames",
            "artifactIds"
    );

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

        List<OperationalContextResponsibility> responsibilities();

        OperationalContextMatchSignals matchSignals();

        OperationalContextHandoffHints handoffHints();

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
            values.addAll(handoffHints().routeSignals());
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
            OperationalContextReferences references,
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextMatchSignals matchSignals,
            OperationalContextHandoffHints handoffHints,
            List<OperationalContextRelation> relations,
            OperationalContextDeployment deployment,
            OperationalContextCodeSearchScope codeSearchScope,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextSystem {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            participants = participants != null ? participants : OperationalContextSystemParticipants.empty();
            references = defaultReferences(references);
            responsibilities = copyList(responsibilities);
            matchSignals = defaultMatchSignals(matchSignals);
            handoffHints = defaultHandoffHints(handoffHints);
            relations = copyList(relations);
            deployment = deployment != null ? deployment : OperationalContextDeployment.empty();
            codeSearchScope = codeSearchScope != null ? codeSearchScope : OperationalContextCodeSearchScope.empty();
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(aliases);
            values.addAll(useFor);
            values.addAll(runtimeMatchSignals().allValues());
            values.addAll(deployment.allValues());
            values.addAll(handoffHints.routeSignals());
            return copyTextList(values);
        }

        public OperationalContextMatchSignals runtimeMatchSignals() {
            return matchSignals.onlyKeys(SYSTEM_RUNTIME_SIGNAL_KEYS);
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
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextSourceLayout sourceLayout,
            List<OperationalContextRepositoryModule> modules,
            OperationalContextMatchSignals matchSignals,
            OperationalContextHandoffHints handoffHints,
            List<OperationalContextRelation> relations,
            List<String> classHints,
            List<String> packagePrefixes,
            List<String> endpointHints,
            List<String> queueTopicHints,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextRepository {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            git = git != null ? git : OperationalContextGit.empty();
            references = defaultReferences(references);
            responsibilities = copyList(responsibilities);
            sourceLayout = sourceLayout != null ? sourceLayout : OperationalContextSourceLayout.empty();
            modules = copyList(modules);
            matchSignals = defaultMatchSignals(matchSignals);
            handoffHints = defaultHandoffHints(handoffHints);
            relations = copyList(relations);
            classHints = copyList(classHints);
            packagePrefixes = copyList(packagePrefixes);
            endpointHints = copyList(endpointHints);
            queueTopicHints = copyList(queueTopicHints);
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
            values.addAll(sourceLayout.sourceRoots());
            values.addAll(sourceLayout.modulePaths());
            values.addAll(sourceLayout.importantPaths());
            values.addAll(classHints);
            values.addAll(packagePrefixes);
            values.addAll(endpointHints);
            values.addAll(queueTopicHints);
            modules.forEach(module -> values.addAll(module.genericSignals()));
            return copyTextList(values);
        }

        public List<String> packagePrefixSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(packagePrefixes);
            values.addAll(matchSignals.strong().packagePrefixes());
            values.addAll(matchSignals.medium().packagePrefixes());
            modules.forEach(module -> values.addAll(module.packagePrefixSignals()));
            return copyTextList(values);
        }

        public List<String> endpointPrefixSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(endpointHints);
            values.addAll(matchSignals.strong().endpointPrefixes());
            values.addAll(matchSignals.medium().endpointPrefixes());
            modules.forEach(module -> values.addAll(module.endpointPrefixSignals()));
            return copyTextList(values);
        }

        public List<String> modulePathSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(sourceLayout.modulePaths());
            modules.forEach(module -> {
                values.add(module.effectiveId());
                values.addAll(module.source().paths());
                values.addAll(module.sourceRoots());
                values.addAll(module.importantPaths());
            });
            return copyTextList(values);
        }

        public List<String> classHintSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(classHints);
            values.addAll(matchSignals.valuesForKeys("classHints", "entrypoints"));
            modules.forEach(module -> values.addAll(module.classHintSignals()));
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
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextMatchSignals matchSignals,
            OperationalContextHandoffHints handoffHints,
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
            responsibilities = copyList(responsibilities);
            matchSignals = defaultMatchSignals(matchSignals);
            handoffHints = defaultHandoffHints(handoffHints);
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
            OperationalContextTransport transport,
            List<OperationalContextChannel> channels,
            OperationalContextImplementation implementation,
            OperationalContextReferences references,
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextMatchSignals matchSignals,
            OperationalContextHandoffHints handoffHints,
            List<OperationalContextRelation> relations,
            List<String> failureModes,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextIntegration {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            participants = participants != null ? participants : OperationalContextIntegrationParticipants.empty();
            transport = transport != null ? transport : OperationalContextTransport.empty();
            channels = copyList(channels);
            implementation = implementation != null ? implementation : OperationalContextImplementation.empty();
            references = defaultReferences(references);
            responsibilities = copyList(responsibilities);
            matchSignals = defaultMatchSignals(matchSignals);
            handoffHints = defaultHandoffHints(handoffHints);
            relations = copyList(relations);
            failureModes = copyList(failureModes);
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>(OperationalContextEntry.super.genericSignals());
            values.addAll(transport.allSignals());
            channels.forEach(channel -> {
                values.add(channel.type());
                values.add(channel.name());
                values.addAll(channel.signals());
            });
            values.addAll(implementation.classHints());
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
            OperationalContextReferences references,
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextMatchSignals matchSignals,
            OperationalContextHandoffHints handoffHints,
            List<OperationalContextRelation> relations,
            OperationalContextOperationalSignals operationalSignals,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextBoundedContext {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            references = defaultReferences(references);
            responsibilities = copyList(responsibilities);
            matchSignals = defaultMatchSignals(matchSignals);
            handoffHints = defaultHandoffHints(handoffHints);
            relations = copyList(relations);
            operationalSignals = operationalSignals != null ? operationalSignals : OperationalContextOperationalSignals.empty();
            payload = copyMap(payload);
        }

        @Override
        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>(OperationalContextEntry.super.genericSignals());
            values.addAll(operationalSignals.allSignals());
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
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextMatchSignals matchSignals,
            OperationalContextHandoffHints handoffHints,
            List<OperationalContextRelation> relations,
            Map<String, Object> payload
    ) implements OperationalContextEntry {

        public OperationalContextTeam {
            aliases = copyList(aliases);
            useFor = copyList(useFor);
            references = defaultReferences(references);
            responsibilities = copyList(responsibilities);
            matchSignals = defaultMatchSignals(matchSignals);
            handoffHints = defaultHandoffHints(handoffHints);
            relations = copyList(relations);
            payload = copyMap(payload);
        }
    }

    public record OperationalContextReferences(
            List<String> systems,
            List<String> deploymentComponents,
            List<String> repositories,
            List<String> modules,
            List<String> processes,
            List<String> boundedContexts,
            List<String> integrations,
            List<String> terms,
            List<String> teams,
            List<String> externalParties,
            List<String> dataStores,
            List<String> handoffRules
    ) {

        public OperationalContextReferences {
            systems = copyList(systems);
            deploymentComponents = copyList(deploymentComponents);
            repositories = copyList(repositories);
            modules = copyList(modules);
            processes = copyList(processes);
            boundedContexts = copyList(boundedContexts);
            integrations = copyList(integrations);
            terms = copyList(terms);
            teams = copyList(teams);
            externalParties = copyList(externalParties);
            dataStores = copyList(dataStores);
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
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record OperationalContextResponsibility(
            String teamId,
            String actorType,
            String actorId,
            String targetType,
            String targetId,
            String role,
            String scope,
            String status,
            String confidence,
            String evidence,
            String source
    ) {
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

        public List<String> serviceNames() {
            return values("serviceNames");
        }

        public List<String> containerNames() {
            return values("containerNames");
        }

        public List<String> projectNames() {
            return values("projectNames");
        }

        public List<String> projectPaths() {
            return values("projectPaths");
        }

        public List<String> packagePrefixes() {
            return values("packagePrefixes");
        }

        public List<String> endpointPrefixes() {
            return values("endpointPrefixes");
        }

        public List<String> endpointTemplates() {
            return values("endpointTemplates");
        }

        public List<String> operationNames() {
            return values("operationNames");
        }

        public List<String> hosts() {
            return values("hosts");
        }

        public List<String> hostPatterns() {
            return values("hostPatterns");
        }

        public List<String> queues() {
            return values("queues");
        }

        public List<String> topics() {
            return values("topics");
        }

        public List<String> routingKeys() {
            return values("routingKeys");
        }

        public List<String> datasourceNames() {
            return values("datasourceNames");
        }

        public List<String> schemas() {
            return values("schemas");
        }

        public List<String> classHints() {
            return values("classHints");
        }

        public List<String> logMarkers() {
            return values("logMarkers");
        }

        public List<String> moduleIds() {
            return values("moduleIds");
        }

        public List<String> sourceRoots() {
            return values("sourceRoots");
        }

        public List<String> paths() {
            return values("paths");
        }

        public List<String> pathHints() {
            return values("pathHints");
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

    public record OperationalContextSourceLayout(
            String repositoryRoot,
            String buildTool,
            List<String> buildFiles,
            List<String> sourceRoots,
            List<String> testRoots,
            List<String> resourceRoots,
            List<String> modulePaths,
            List<String> generatedSourcePaths,
            List<String> importantPaths,
            List<String> configurationFiles,
            List<String> deploymentFiles,
            List<String> databaseMigrationPaths,
            List<String> workflowDefinitionPaths,
            List<String> documentationPaths
    ) {

        public OperationalContextSourceLayout {
            buildFiles = copyList(buildFiles);
            sourceRoots = copyList(sourceRoots);
            testRoots = copyList(testRoots);
            resourceRoots = copyList(resourceRoots);
            modulePaths = copyList(modulePaths);
            generatedSourcePaths = copyList(generatedSourcePaths);
            importantPaths = copyList(importantPaths);
            configurationFiles = copyList(configurationFiles);
            deploymentFiles = copyList(deploymentFiles);
            databaseMigrationPaths = copyList(databaseMigrationPaths);
            workflowDefinitionPaths = copyList(workflowDefinitionPaths);
            documentationPaths = copyList(documentationPaths);
        }

        public static OperationalContextSourceLayout empty() {
            return new OperationalContextSourceLayout(
                    null,
                    null,
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
                    List.of(),
                    List.of()
            );
        }
    }

    public record OperationalContextRepositoryModule(
            String id,
            String moduleId,
            String name,
            String moduleType,
            String lifecycleStatus,
            OperationalContextReferences references,
            List<OperationalContextResponsibility> responsibilities,
            OperationalContextSource source,
            List<String> sourceRoots,
            List<String> importantPaths,
            OperationalContextMatchSignals matchSignals,
            Map<String, Object> payload
    ) {

        public OperationalContextRepositoryModule {
            references = defaultReferences(references);
            responsibilities = copyList(responsibilities);
            source = source != null ? source : OperationalContextSource.empty();
            sourceRoots = copyList(sourceRoots);
            importantPaths = copyList(importantPaths);
            matchSignals = defaultMatchSignals(matchSignals);
            payload = copyMap(payload);
        }

        public String effectiveId() {
            return firstNonBlank(moduleId, id);
        }

        public List<String> genericSignals() {
            var values = new LinkedHashSet<String>();
            values.add(effectiveId());
            values.add(name);
            values.addAll(source.paths());
            values.addAll(source.packages());
            values.addAll(sourceRoots);
            values.addAll(importantPaths);
            values.addAll(matchSignals.allValues());
            return copyTextList(values);
        }

        public List<String> packagePrefixSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(source.packages());
            values.addAll(matchSignals.strong().packagePrefixes());
            values.addAll(matchSignals.medium().packagePrefixes());
            return copyTextList(values);
        }

        public List<String> endpointPrefixSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(matchSignals.strong().endpointPrefixes());
            values.addAll(matchSignals.medium().endpointPrefixes());
            return copyTextList(values);
        }

        public List<String> classHintSignals() {
            return matchSignals.valuesForKeys("classHints", "entrypoints");
        }
    }

    public record OperationalContextSource(
            List<String> paths,
            List<String> packages,
            boolean generated
    ) {

        public OperationalContextSource {
            paths = copyList(paths);
            packages = copyList(packages);
        }

        public static OperationalContextSource empty() {
            return new OperationalContextSource(List.of(), List.of(), false);
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
            List<String> modules,
            String role,
            String externalOwner,
            List<String> notes
    ) {

        public OperationalContextIntegrationParticipant {
            repositories = copyList(repositories);
            modules = copyList(modules);
            notes = copyList(notes);
        }

        public static OperationalContextIntegrationParticipant empty() {
            return new OperationalContextIntegrationParticipant(
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    List.of()
            );
        }
    }

    public record OperationalContextTransport(
            List<String> protocols,
            OperationalContextHttpTransport http,
            OperationalContextMessagingTransport messaging,
            OperationalContextDatabaseTransport database
    ) {

        public OperationalContextTransport {
            protocols = copyList(protocols);
            http = http != null ? http : OperationalContextHttpTransport.empty();
            messaging = messaging != null ? messaging : OperationalContextMessagingTransport.empty();
            database = database != null ? database : OperationalContextDatabaseTransport.empty();
        }

        public static OperationalContextTransport empty() {
            return new OperationalContextTransport(
                    List.of(),
                    OperationalContextHttpTransport.empty(),
                    OperationalContextMessagingTransport.empty(),
                    OperationalContextDatabaseTransport.empty()
            );
        }

        public List<String> allSignals() {
            var values = new LinkedHashSet<String>();
            values.addAll(protocols);
            values.addAll(http.allSignals());
            values.addAll(messaging.allSignals());
            values.addAll(database.allSignals());
            return copyTextList(values);
        }
    }

    public record OperationalContextHttpTransport(
            List<String> methods,
            List<String> endpointPrefixes,
            List<String> endpointTemplates,
            List<String> operationNames,
            List<String> hosts,
            List<String> hostPatterns,
            List<String> baseUrlConfigKeys,
            List<String> clientNames,
            List<String> gatewayRoutes
    ) {

        public OperationalContextHttpTransport {
            methods = copyList(methods);
            endpointPrefixes = copyList(endpointPrefixes);
            endpointTemplates = copyList(endpointTemplates);
            operationNames = copyList(operationNames);
            hosts = copyList(hosts);
            hostPatterns = copyList(hostPatterns);
            baseUrlConfigKeys = copyList(baseUrlConfigKeys);
            clientNames = copyList(clientNames);
            gatewayRoutes = copyList(gatewayRoutes);
        }

        public static OperationalContextHttpTransport empty() {
            return new OperationalContextHttpTransport(
                    List.of(),
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

        public List<String> allSignals() {
            return copyTextList(List.of(
                    methods,
                    endpointPrefixes,
                    endpointTemplates,
                    operationNames,
                    hosts,
                    hostPatterns,
                    baseUrlConfigKeys,
                    clientNames,
                    gatewayRoutes
            ));
        }
    }

    public record OperationalContextMessagingTransport(
            List<String> brokers,
            List<String> virtualHosts,
            List<String> exchanges,
            List<String> queues,
            List<String> topics,
            List<String> routingKeys,
            List<String> bindings,
            List<String> dlqs,
            List<String> retryQueuesOrTopics,
            List<String> consumerGroups,
            List<String> partitionKeys
    ) {

        public OperationalContextMessagingTransport {
            brokers = copyList(brokers);
            virtualHosts = copyList(virtualHosts);
            exchanges = copyList(exchanges);
            queues = copyList(queues);
            topics = copyList(topics);
            routingKeys = copyList(routingKeys);
            bindings = copyList(bindings);
            dlqs = copyList(dlqs);
            retryQueuesOrTopics = copyList(retryQueuesOrTopics);
            consumerGroups = copyList(consumerGroups);
            partitionKeys = copyList(partitionKeys);
        }

        public static OperationalContextMessagingTransport empty() {
            return new OperationalContextMessagingTransport(
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
                    List.of()
            );
        }

        public List<String> allSignals() {
            return copyTextList(List.of(
                    brokers,
                    virtualHosts,
                    exchanges,
                    queues,
                    topics,
                    routingKeys,
                    bindings,
                    dlqs,
                    retryQueuesOrTopics,
                    consumerGroups,
                    partitionKeys
            ));
        }
    }

    public record OperationalContextDatabaseTransport(
            List<String> datasourceNames,
            List<String> connectionNames,
            List<String> hikariPoolMarkers,
            List<String> schemas,
            List<String> tables,
            List<String> entities,
            List<String> repositories,
            List<String> operations
    ) {

        public OperationalContextDatabaseTransport {
            datasourceNames = copyList(datasourceNames);
            connectionNames = copyList(connectionNames);
            hikariPoolMarkers = copyList(hikariPoolMarkers);
            schemas = copyList(schemas);
            tables = copyList(tables);
            entities = copyList(entities);
            repositories = copyList(repositories);
            operations = copyList(operations);
        }

        public static OperationalContextDatabaseTransport empty() {
            return new OperationalContextDatabaseTransport(
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

        public List<String> allSignals() {
            return copyTextList(List.of(
                    datasourceNames,
                    connectionNames,
                    hikariPoolMarkers,
                    schemas,
                    tables,
                    entities,
                    repositories,
                    operations
            ));
        }
    }

    public record OperationalContextChannel(
            String type,
            String name,
            String direction,
            List<String> signals
    ) {

        public OperationalContextChannel {
            signals = copyList(signals);
        }
    }

    public record OperationalContextImplementation(
            String localSide,
            List<String> clientTypes,
            List<String> packagePrefixes,
            List<String> modulePaths,
            List<String> classHints,
            List<String> clientClasses,
            List<String> controllerClasses,
            List<String> listenerClasses,
            List<String> publisherClasses,
            List<String> schedulerClasses,
            List<String> generatedClientClasses,
            List<String> configClasses
    ) {

        public OperationalContextImplementation {
            clientTypes = copyList(clientTypes);
            packagePrefixes = copyList(packagePrefixes);
            modulePaths = copyList(modulePaths);
            classHints = copyList(classHints);
            clientClasses = copyList(clientClasses);
            controllerClasses = copyList(controllerClasses);
            listenerClasses = copyList(listenerClasses);
            publisherClasses = copyList(publisherClasses);
            schedulerClasses = copyList(schedulerClasses);
            generatedClientClasses = copyList(generatedClientClasses);
            configClasses = copyList(configClasses);
        }

        public static OperationalContextImplementation empty() {
            return new OperationalContextImplementation(
                    null,
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

    public record OperationalContextHandoffHints(
            String defaultRouteLabel,
            List<String> firstResponderTeamIds,
            List<String> escalationTeamIds,
            List<String> partnerTeamIds,
            List<String> platformSupportTeamIds,
            List<String> externalRouteLabels,
            List<String> requiredEvidence,
            List<String> preferredEvidence,
            List<String> expectedFirstActions,
            List<String> whenToRouteHere,
            List<String> whenToInvolveAsPartner,
            List<String> whenNotToRouteHere,
            String fallbackIfAmbiguous,
            List<String> notes
    ) {

        public OperationalContextHandoffHints {
            firstResponderTeamIds = copyList(firstResponderTeamIds);
            escalationTeamIds = copyList(escalationTeamIds);
            partnerTeamIds = copyList(partnerTeamIds);
            platformSupportTeamIds = copyList(platformSupportTeamIds);
            externalRouteLabels = copyList(externalRouteLabels);
            requiredEvidence = copyList(requiredEvidence);
            preferredEvidence = copyList(preferredEvidence);
            expectedFirstActions = copyList(expectedFirstActions);
            whenToRouteHere = copyList(whenToRouteHere);
            whenToInvolveAsPartner = copyList(whenToInvolveAsPartner);
            whenNotToRouteHere = copyList(whenNotToRouteHere);
            notes = copyList(notes);
        }

        public static OperationalContextHandoffHints empty() {
            return new OperationalContextHandoffHints(
                    null,
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
                    List.of(),
                    null,
                    List.of()
            );
        }

        public List<String> routeSignals() {
            var values = new LinkedHashSet<String>();
            values.add(defaultRouteLabel);
            values.addAll(firstResponderTeamIds);
            values.addAll(escalationTeamIds);
            values.addAll(partnerTeamIds);
            values.addAll(platformSupportTeamIds);
            values.addAll(externalRouteLabels);
            values.addAll(requiredEvidence);
            values.addAll(preferredEvidence);
            values.addAll(expectedFirstActions);
            values.addAll(whenToRouteHere);
            values.addAll(whenToInvolveAsPartner);
            values.addAll(whenNotToRouteHere);
            values.add(fallbackIfAmbiguous);
            values.addAll(notes);
            return copyTextList(values);
        }
    }

    public record OperationalContextDeployment(
            List<String> serviceNames,
            List<String> applicationNames,
            List<String> containerNames,
            List<String> deploymentNames,
            List<String> namespaceNames,
            List<String> imageNames,
            List<String> artifactNames
    ) {

        public OperationalContextDeployment {
            serviceNames = copyList(serviceNames);
            applicationNames = copyList(applicationNames);
            containerNames = copyList(containerNames);
            deploymentNames = copyList(deploymentNames);
            namespaceNames = copyList(namespaceNames);
            imageNames = copyList(imageNames);
            artifactNames = copyList(artifactNames);
        }

        public static OperationalContextDeployment empty() {
            return new OperationalContextDeployment(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        public List<String> allValues() {
            return copyTextList(
                    serviceNames,
                    applicationNames,
                    containerNames,
                    deploymentNames,
                    namespaceNames,
                    imageNames,
                    artifactNames
            );
        }
    }

    public record OperationalContextCodeSearchScope(
            List<String> repositories,
            List<String> packagePrefixes,
            List<String> classHints,
            List<String> configPrefixes,
            List<String> generatedClients,
            List<String> sharedLibraries,
            List<String> searchTogetherWithSystems,
            List<String> searchNotes
    ) {

        public OperationalContextCodeSearchScope {
            repositories = copyList(repositories);
            packagePrefixes = copyList(packagePrefixes);
            classHints = copyList(classHints);
            configPrefixes = copyList(configPrefixes);
            generatedClients = copyList(generatedClients);
            sharedLibraries = copyList(sharedLibraries);
            searchTogetherWithSystems = copyList(searchTogetherWithSystems);
            searchNotes = copyList(searchNotes);
        }

        public static OperationalContextCodeSearchScope empty() {
            return new OperationalContextCodeSearchScope(
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

    public record OperationalContextRepositorySearchScope(
            String id,
            String name,
            String lifecycleStatus,
            OperationalContextRepositorySearchTarget target,
            List<String> useFor,
            List<OperationalContextRepositorySearchRepository> repositories,
            List<String> packagePrefixes,
            List<String> classHints,
            List<String> endpointHints,
            List<String> queueTopicHints,
            OperationalContextRepositorySearchDatabaseHints databaseHints,
            OperationalContextRepositorySearchWorkflowHints workflowHints,
            OperationalContextRepositorySearchStrategy searchStrategy,
            List<String> limitations,
            Map<String, Object> payload
    ) {

        public OperationalContextRepositorySearchScope {
            target = target != null ? target : OperationalContextRepositorySearchTarget.empty();
            useFor = copyList(useFor);
            repositories = copyList(repositories);
            packagePrefixes = copyList(packagePrefixes);
            classHints = copyList(classHints);
            endpointHints = copyList(endpointHints);
            queueTopicHints = copyList(queueTopicHints);
            databaseHints = databaseHints != null
                    ? databaseHints
                    : OperationalContextRepositorySearchDatabaseHints.empty();
            workflowHints = workflowHints != null
                    ? workflowHints
                    : OperationalContextRepositorySearchWorkflowHints.empty();
            searchStrategy = searchStrategy != null
                    ? searchStrategy
                    : OperationalContextRepositorySearchStrategy.empty();
            limitations = copyList(limitations);
            payload = copyMap(payload);
        }
    }

    public record OperationalContextRepositorySearchTarget(
            List<String> systems,
            List<String> deploymentComponents,
            List<String> processes,
            List<String> boundedContexts,
            List<String> integrations,
            List<String> terms
    ) {

        public OperationalContextRepositorySearchTarget {
            systems = copyList(systems);
            deploymentComponents = copyList(deploymentComponents);
            processes = copyList(processes);
            boundedContexts = copyList(boundedContexts);
            integrations = copyList(integrations);
            terms = copyList(terms);
        }

        public static OperationalContextRepositorySearchTarget empty() {
            return new OperationalContextRepositorySearchTarget(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record OperationalContextRepositorySearchRepository(
            String repoId,
            String role,
            Integer priority,
            boolean include,
            List<String> moduleIds,
            String reason
    ) {

        public OperationalContextRepositorySearchRepository {
            moduleIds = copyList(moduleIds);
        }
    }

    public record OperationalContextRepositorySearchDatabaseHints(
            List<String> datasourceNames,
            List<String> hikariPools,
            List<String> schemas,
            List<String> tables,
            List<String> entities,
            List<String> migrations
    ) {

        public OperationalContextRepositorySearchDatabaseHints {
            datasourceNames = copyList(datasourceNames);
            hikariPools = copyList(hikariPools);
            schemas = copyList(schemas);
            tables = copyList(tables);
            entities = copyList(entities);
            migrations = copyList(migrations);
        }

        public static OperationalContextRepositorySearchDatabaseHints empty() {
            return new OperationalContextRepositorySearchDatabaseHints(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record OperationalContextRepositorySearchWorkflowHints(
            List<String> jobNames,
            List<String> workflowNames,
            List<String> definitionPaths
    ) {

        public OperationalContextRepositorySearchWorkflowHints {
            jobNames = copyList(jobNames);
            workflowNames = copyList(workflowNames);
            definitionPaths = copyList(definitionPaths);
        }

        public static OperationalContextRepositorySearchWorkflowHints empty() {
            return new OperationalContextRepositorySearchWorkflowHints(List.of(), List.of(), List.of());
        }
    }

    public record OperationalContextRepositorySearchStrategy(
            List<String> priorityOrder,
            boolean includeGeneratedClients,
            boolean includeSharedLibraries,
            boolean includeDeploymentConfig,
            boolean includeDocumentation,
            List<String> notes
    ) {

        public OperationalContextRepositorySearchStrategy {
            priorityOrder = copyList(priorityOrder);
            notes = copyList(notes);
        }

        public static OperationalContextRepositorySearchStrategy empty() {
            return new OperationalContextRepositorySearchStrategy(
                    List.of(),
                    false,
                    false,
                    false,
                    false,
                    List.of()
            );
        }
    }

    public record OperationalContextOperationalSignals(Map<String, List<String>> valuesByKey) {

        public OperationalContextOperationalSignals {
            valuesByKey = copyStringListMap(valuesByKey);
        }

        public static OperationalContextOperationalSignals empty() {
            return new OperationalContextOperationalSignals(Map.of());
        }

        public List<String> values(String key) {
            return valuesByKey.getOrDefault(key, List.of());
        }

        public List<String> serviceNames() {
            return values("serviceNames");
        }

        public List<String> endpointPrefixes() {
            return values("endpointPrefixes");
        }

        public List<String> packagePrefixes() {
            return values("packagePrefixes");
        }

        public List<String> allSignals() {
            return valuesByKey.values().stream()
                    .flatMap(Collection::stream)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
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
            String routeTo,
            List<String> useWhen,
            List<String> doNotUseWhen,
            List<String> requiredEvidence,
            List<String> expectedFirstAction,
            List<String> partnerTeams,
            List<String> notes
    ) {

        public OperationalContextHandoffRule {
            useWhen = copyList(useWhen);
            doNotUseWhen = copyList(doNotUseWhen);
            requiredEvidence = copyList(requiredEvidence);
            expectedFirstAction = copyList(expectedFirstAction);
            partnerTeams = copyList(partnerTeams);
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
                references(source.get("references")),
                responsibilities(source.get("responsibilities")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                handoffHints(source.get("handoffHints")),
                relations(source.get("relations")),
                deployment(source.get("deployment")),
                codeSearchScope(source.get("codeSearchScope")),
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
                responsibilities(source.get("responsibilities")),
                sourceLayout(source.get("sourceLayout")),
                mapList(source, "modules").stream().map(OperationalContextDtos::repositoryModule).toList(),
                matchSignals(firstValue(source, "matchSignals", "match")),
                handoffHints(source.get("handoffHints")),
                relations(source.get("relations")),
                textList(source, "classHints"),
                textList(source, "packagePrefixes"),
                textList(source, "endpointHints"),
                textList(source, "queueTopicHints"),
                source
        );
    }

    public static OperationalContextRepositorySearchScope repositorySearchScope(Map<String, Object> source) {
        return new OperationalContextRepositorySearchScope(
                text(source, "id"),
                text(source, "name"),
                text(source, "lifecycleStatus"),
                repositorySearchTarget(source.get("target")),
                textList(source, "useFor"),
                mapList(source, "repositories").stream()
                        .map(OperationalContextDtos::repositorySearchRepository)
                        .toList(),
                textList(source, "packagePrefixes"),
                textList(source, "classHints"),
                textList(source, "endpointHints"),
                textList(source, "queueTopicHints"),
                repositorySearchDatabaseHints(source.get("databaseHints")),
                repositorySearchWorkflowHints(source.get("workflowHints")),
                repositorySearchStrategy(source.get("searchStrategy")),
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
                responsibilities(source.get("responsibilities")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                handoffHints(source.get("handoffHints")),
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
                transport(source.get("transport")),
                mapList(source, "channels").stream().map(OperationalContextDtos::channel).toList(),
                implementation(source.get("implementation")),
                references(source.get("references")),
                responsibilities(source.get("responsibilities")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                handoffHints(source.get("handoffHints")),
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
                references(source.get("references")),
                responsibilities(source.get("responsibilities")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                handoffHints(source.get("handoffHints")),
                relations(source.get("relations")),
                operationalSignals(source.get("operationalSignals")),
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
                responsibilities(source.get("responsibilities")),
                matchSignals(firstValue(source, "matchSignals", "match")),
                handoffHints(source.get("handoffHints")),
                relations(source.get("relations")),
                source
        );
    }

    private static OperationalContextReferences references(Object value) {
        var source = map(value);
        return new OperationalContextReferences(
                textList(source, "systems"),
                textList(source, "deploymentComponents"),
                textList(source, "repositories"),
                textList(source, "modules"),
                textList(source, "processes"),
                textList(source, "boundedContexts"),
                textList(source, "integrations"),
                textList(source, "terms"),
                textList(source, "teams"),
                textList(source, "externalParties"),
                textList(source, "dataStores"),
                textList(source, "handoffRules")
        );
    }

    private static List<OperationalContextResponsibility> responsibilities(Object value) {
        return mapList(value).stream()
                .map(source -> new OperationalContextResponsibility(
                        text(source, "teamId"),
                        text(source, "actorType"),
                        text(source, "actorId"),
                        text(source, "targetType"),
                        text(source, "targetId"),
                        text(source, "role"),
                        text(source, "scope"),
                        text(source, "status"),
                        text(source, "confidence"),
                        text(source, "evidence"),
                        text(source, "source")
                ))
                .toList();
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

    private static OperationalContextSourceLayout sourceLayout(Object value) {
        var source = map(value);
        return new OperationalContextSourceLayout(
                text(source, "repositoryRoot"),
                text(source, "buildTool"),
                textList(source, "buildFiles"),
                textList(source, "sourceRoots"),
                textList(source, "testRoots"),
                textList(source, "resourceRoots"),
                textList(source, "modulePaths"),
                textList(source, "generatedSourcePaths"),
                textList(source, "importantPaths"),
                textList(source, "configurationFiles"),
                textList(source, "deploymentFiles"),
                textList(source, "databaseMigrationPaths"),
                textList(source, "workflowDefinitionPaths"),
                textList(source, "documentationPaths")
        );
    }

    private static OperationalContextRepositoryModule repositoryModule(Map<String, Object> source) {
        return new OperationalContextRepositoryModule(
                text(source, "id"),
                text(source, "moduleId"),
                text(source, "name"),
                text(source, "moduleType"),
                text(source, "lifecycleStatus"),
                references(source.get("references")),
                responsibilities(source.get("responsibilities")),
                source(source.get("source")),
                textList(source, "sourceRoots"),
                textList(source, "importantPaths"),
                matchSignals(firstValue(source, "matchSignals", "match")),
                source
        );
    }

    private static OperationalContextSource source(Object value) {
        var source = map(value);
        return new OperationalContextSource(
                textList(source, "paths"),
                textList(source, "packages"),
                Boolean.parseBoolean(firstNonBlank(text(source, "generated"), "false"))
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
                textList(source, "modules"),
                text(source, "role"),
                text(source, "externalOwner"),
                textList(source, "notes")
        );
    }

    private static OperationalContextTransport transport(Object value) {
        var source = map(value);
        return new OperationalContextTransport(
                textList(source, "protocols"),
                httpTransport(source.get("http")),
                messagingTransport(source.get("messaging")),
                databaseTransport(source.get("database"))
        );
    }

    private static OperationalContextHttpTransport httpTransport(Object value) {
        var source = map(value);
        return new OperationalContextHttpTransport(
                textList(source, "methods"),
                textList(source, "endpointPrefixes"),
                textList(source, "endpointTemplates"),
                textList(source, "operationNames"),
                textList(source, "hosts"),
                textList(source, "hostPatterns"),
                textList(source, "baseUrlConfigKeys"),
                textList(source, "clientNames"),
                textList(source, "gatewayRoutes")
        );
    }

    private static OperationalContextMessagingTransport messagingTransport(Object value) {
        var source = map(value);
        return new OperationalContextMessagingTransport(
                textList(source, "brokers"),
                textList(source, "virtualHosts"),
                textList(source, "exchanges"),
                textList(source, "queues"),
                textList(source, "topics"),
                textList(source, "routingKeys"),
                textList(source, "bindings"),
                textList(source, "dlqs"),
                textList(source, "retryQueuesOrTopics"),
                textList(source, "consumerGroups"),
                textList(source, "partitionKeys")
        );
    }

    private static OperationalContextDatabaseTransport databaseTransport(Object value) {
        var source = map(value);
        return new OperationalContextDatabaseTransport(
                textList(source, "datasourceNames"),
                textList(source, "connectionNames"),
                textList(source, "hikariPoolMarkers"),
                textList(source, "schemas"),
                textList(source, "tables"),
                textList(source, "entities"),
                textList(source, "repositories"),
                textList(source, "operations")
        );
    }

    private static OperationalContextChannel channel(Map<String, Object> source) {
        return new OperationalContextChannel(
                text(source, "type"),
                text(source, "name"),
                text(source, "direction"),
                textList(source, "signals")
        );
    }

    private static OperationalContextImplementation implementation(Object value) {
        var source = map(value);
        return new OperationalContextImplementation(
                text(source, "localSide"),
                textList(source, "clientTypes"),
                textList(source, "packagePrefixes"),
                textList(source, "modulePaths"),
                textList(source, "classHints"),
                textList(source, "clientClasses"),
                textList(source, "controllerClasses"),
                textList(source, "listenerClasses"),
                textList(source, "publisherClasses"),
                textList(source, "schedulerClasses"),
                textList(source, "generatedClientClasses"),
                textList(source, "configClasses")
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

    private static OperationalContextHandoffHints handoffHints(Object value) {
        var source = map(value);
        return new OperationalContextHandoffHints(
                firstNonBlank(text(source, "defaultRouteLabel"), text(source, "defaultRoute")),
                textList(source, "firstResponderTeamIds"),
                textList(source, "escalationTeamIds"),
                textList(source, "partnerTeamIds"),
                textList(source, "platformSupportTeamIds"),
                textList(source, "externalRouteLabels"),
                textList(source, "requiredEvidence"),
                textList(source, "preferredEvidence"),
                firstList(source, "expectedFirstActions", "firstActions"),
                textList(source, "whenToRouteHere"),
                textList(source, "whenToInvolveAsPartner"),
                textList(source, "whenNotToRouteHere"),
                text(source, "fallbackIfAmbiguous"),
                textList(source, "notes")
        );
    }

    private static OperationalContextDeployment deployment(Object value) {
        var source = map(value);
        return new OperationalContextDeployment(
                textList(source, "serviceNames"),
                textList(source, "applicationNames"),
                textList(source, "containerNames"),
                textList(source, "deploymentNames"),
                textList(source, "namespaceNames"),
                textList(source, "imageNames"),
                textList(source, "artifactNames")
        );
    }

    private static OperationalContextCodeSearchScope codeSearchScope(Object value) {
        var source = map(value);
        return new OperationalContextCodeSearchScope(
                textList(source, "repositories"),
                textList(source, "packagePrefixes"),
                textList(source, "classHints"),
                textList(source, "configPrefixes"),
                textList(source, "generatedClients"),
                textList(source, "sharedLibraries"),
                textList(source, "searchTogetherWithSystems"),
                textList(source, "searchNotes")
        );
    }

    private static OperationalContextRepositorySearchTarget repositorySearchTarget(Object value) {
        var source = map(value);
        return new OperationalContextRepositorySearchTarget(
                textList(source, "systems"),
                textList(source, "deploymentComponents"),
                textList(source, "processes"),
                textList(source, "boundedContexts"),
                textList(source, "integrations"),
                textList(source, "terms")
        );
    }

    private static OperationalContextRepositorySearchRepository repositorySearchRepository(Map<String, Object> source) {
        return new OperationalContextRepositorySearchRepository(
                text(source, "repoId"),
                text(source, "role"),
                integer(source, "priority"),
                bool(source, "include", true),
                textList(source, "moduleIds"),
                text(source, "reason")
        );
    }

    private static OperationalContextRepositorySearchDatabaseHints repositorySearchDatabaseHints(Object value) {
        var source = map(value);
        return new OperationalContextRepositorySearchDatabaseHints(
                textList(source, "datasourceNames"),
                textList(source, "hikariPools"),
                textList(source, "schemas"),
                textList(source, "tables"),
                textList(source, "entities"),
                textList(source, "migrations")
        );
    }

    private static OperationalContextRepositorySearchWorkflowHints repositorySearchWorkflowHints(Object value) {
        var source = map(value);
        return new OperationalContextRepositorySearchWorkflowHints(
                textList(source, "jobNames"),
                textList(source, "workflowNames"),
                textList(source, "definitionPaths")
        );
    }

    private static OperationalContextRepositorySearchStrategy repositorySearchStrategy(Object value) {
        var source = map(value);
        return new OperationalContextRepositorySearchStrategy(
                textList(source, "priorityOrder"),
                bool(source, "includeGeneratedClients", false),
                bool(source, "includeSharedLibraries", false),
                bool(source, "includeDeploymentConfig", false),
                bool(source, "includeDocumentation", false),
                textList(source, "notes")
        );
    }

    private static OperationalContextOperationalSignals operationalSignals(Object value) {
        var source = map(value);
        var signals = new LinkedHashMap<String, List<String>>();
        source.forEach((key, item) -> signals.put(key, textList(item)));
        return new OperationalContextOperationalSignals(signals);
    }

    private static OperationalContextReferences defaultReferences(OperationalContextReferences value) {
        return value != null ? value : OperationalContextReferences.empty();
    }

    private static OperationalContextMatchSignals defaultMatchSignals(OperationalContextMatchSignals value) {
        return value != null ? value : OperationalContextMatchSignals.empty();
    }

    private static OperationalContextHandoffHints defaultHandoffHints(OperationalContextHandoffHints value) {
        return value != null ? value : OperationalContextHandoffHints.empty();
    }

    private static Map<String, Object> map(Object value) {
        var values = mapList(value);
        return values.isEmpty() ? Map.of() : values.get(0);
    }

    private static List<String> firstList(Map<String, Object> source, String... paths) {
        for (var path : paths) {
            var values = textList(source, path);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
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
