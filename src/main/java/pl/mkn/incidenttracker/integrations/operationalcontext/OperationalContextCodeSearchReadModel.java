package pl.mkn.incidenttracker.integrations.operationalcontext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalContextCodeSearchReadModel(
        String contract,
        int contractVersion,
        ReadModelProfile profile,
        OperationalContextRelationIndex.EntityRef analysisTarget,
        List<CodeSearchScopeView> scopes,
        List<RepositoryView> repositories,
        CodeSearchHints aggregatedHints,
        List<String> limitations,
        List<OperationalContextRelationIndex.ValidationFinding> validationFindings
) {

    public OperationalContextCodeSearchReadModel {
        contract = contract != null ? contract : "operational-context.code-search";
        contractVersion = contractVersion > 0 ? contractVersion : 1;
        profile = profile != null ? profile : ReadModelProfile.defaultProfile();
        scopes = copyList(scopes);
        repositories = copyList(repositories);
        aggregatedHints = aggregatedHints != null ? aggregatedHints : CodeSearchHints.empty();
        limitations = copyTextList(limitations);
        validationFindings = copyList(validationFindings);
    }

    public static OperationalContextCodeSearchReadModel empty(
            OperationalContextRelationIndex.EntityRef analysisTarget,
            List<OperationalContextRelationIndex.ValidationFinding> findings
    ) {
        return new OperationalContextCodeSearchReadModel(
                "operational-context.code-search",
                1,
                ReadModelProfile.defaultProfile(),
                analysisTarget,
                List.of(),
                List.of(),
                CodeSearchHints.empty(),
                List.of(),
                findings
        );
    }

    public record ReadModelProfile(
            String profile,
            String intendedConsumer,
            int maxTokenBudget,
            boolean truncated,
            String truncationReason,
            List<String> availableExpansions
    ) {

        public ReadModelProfile {
            profile = textOrDefault(profile, "default");
            intendedConsumer = textOrDefault(intendedConsumer, "tool");
            maxTokenBudget = maxTokenBudget > 0 ? maxTokenBudget : 2000;
            availableExpansions = copyTextList(availableExpansions);
        }

        public static ReadModelProfile defaultProfile() {
            return new ReadModelProfile(
                    "default",
                    "tool",
                    2000,
                    false,
                    null,
                    List.of("codeSearch.expanded")
            );
        }
    }

    public record CodeSearchScopeView(
            OperationalContextRelationIndex.EntityRef scope,
            List<OperationalContextRelationIndex.EntityRef> targets,
            List<OperationalContextRelationIndex.EntityRef> repositories,
            CodeSearchHints hints,
            SearchStrategyView searchStrategy,
            List<String> limitations,
            OperationalContextRelationIndex.Provenance provenance
    ) {

        public CodeSearchScopeView {
            targets = copyList(targets);
            repositories = copyList(repositories);
            hints = hints != null ? hints : CodeSearchHints.empty();
            searchStrategy = searchStrategy != null ? searchStrategy : SearchStrategyView.empty();
            limitations = copyTextList(limitations);
        }
    }

    public record RepositoryView(
            OperationalContextRelationIndex.EntityRef repository,
            String role,
            Integer priority,
            boolean include,
            String reason,
            GitView git,
            SourceLayoutView sourceLayout,
            List<ModuleView> modules,
            CodeSearchHints hints,
            OperationalContextRelationIndex.Provenance provenance
    ) {

        public RepositoryView {
            role = textOrDefault(role, "included");
            reason = text(reason);
            git = git != null ? git : GitView.empty();
            sourceLayout = sourceLayout != null ? sourceLayout : SourceLayoutView.empty();
            modules = copyList(modules);
            hints = hints != null ? hints : CodeSearchHints.empty();
        }
    }

    public record GitView(
            String provider,
            String group,
            String project,
            String projectPath,
            String defaultBranch,
            String url
    ) {

        public static GitView empty() {
            return new GitView(null, null, null, null, null, null);
        }
    }

    public record SourceLayoutView(
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

        public SourceLayoutView {
            buildFiles = copyTextList(buildFiles);
            sourceRoots = copyTextList(sourceRoots);
            testRoots = copyTextList(testRoots);
            resourceRoots = copyTextList(resourceRoots);
            modulePaths = copyTextList(modulePaths);
            generatedSourcePaths = copyTextList(generatedSourcePaths);
            importantPaths = copyTextList(importantPaths);
            configurationFiles = copyTextList(configurationFiles);
            deploymentFiles = copyTextList(deploymentFiles);
            databaseMigrationPaths = copyTextList(databaseMigrationPaths);
            workflowDefinitionPaths = copyTextList(workflowDefinitionPaths);
            documentationPaths = copyTextList(documentationPaths);
        }

        public static SourceLayoutView empty() {
            return new SourceLayoutView(
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

    public record ModuleView(
            String id,
            String name,
            String moduleType,
            String lifecycleStatus,
            List<String> paths,
            List<String> packages,
            List<String> sourceRoots,
            List<String> importantPaths,
            CodeSearchHints hints
    ) {

        public ModuleView {
            id = text(id);
            name = text(name);
            moduleType = text(moduleType);
            lifecycleStatus = text(lifecycleStatus);
            paths = copyTextList(paths);
            packages = copyTextList(packages);
            sourceRoots = copyTextList(sourceRoots);
            importantPaths = copyTextList(importantPaths);
            hints = hints != null ? hints : CodeSearchHints.empty();
        }
    }

    public record CodeSearchHints(
            List<String> packagePrefixes,
            List<String> classHints,
            List<String> endpointHints,
            List<String> queueTopicHints,
            DatabaseHints databaseHints,
            WorkflowHints workflowHints
    ) {

        public CodeSearchHints {
            packagePrefixes = copyTextList(packagePrefixes);
            classHints = copyTextList(classHints);
            endpointHints = copyTextList(endpointHints);
            queueTopicHints = copyTextList(queueTopicHints);
            databaseHints = databaseHints != null ? databaseHints : DatabaseHints.empty();
            workflowHints = workflowHints != null ? workflowHints : WorkflowHints.empty();
        }

        public static CodeSearchHints empty() {
            return new CodeSearchHints(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    DatabaseHints.empty(),
                    WorkflowHints.empty()
            );
        }
    }

    public record DatabaseHints(
            List<String> datasourceNames,
            List<String> hikariPools,
            List<String> schemas,
            List<String> tables,
            List<String> entities,
            List<String> migrations
    ) {

        public DatabaseHints {
            datasourceNames = copyTextList(datasourceNames);
            hikariPools = copyTextList(hikariPools);
            schemas = copyTextList(schemas);
            tables = copyTextList(tables);
            entities = copyTextList(entities);
            migrations = copyTextList(migrations);
        }

        public static DatabaseHints empty() {
            return new DatabaseHints(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record WorkflowHints(
            List<String> jobNames,
            List<String> workflowNames,
            List<String> definitionPaths
    ) {

        public WorkflowHints {
            jobNames = copyTextList(jobNames);
            workflowNames = copyTextList(workflowNames);
            definitionPaths = copyTextList(definitionPaths);
        }

        public static WorkflowHints empty() {
            return new WorkflowHints(List.of(), List.of(), List.of());
        }
    }

    public record SearchStrategyView(
            List<String> priorityOrder,
            boolean includeGeneratedClients,
            boolean includeSharedLibraries,
            boolean includeDeploymentConfig,
            boolean includeDocumentation,
            List<String> notes
    ) {

        public SearchStrategyView {
            priorityOrder = copyTextList(priorityOrder);
            notes = copyTextList(notes);
        }

        public static SearchStrategyView empty() {
            return new SearchStrategyView(List.of(), false, false, false, false, List.of());
        }
    }

    static <T> List<T> copyList(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(OperationalContextCodeSearchReadModel::text)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    static <K, V> Map<K, V> copyMap(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String textOrDefault(String value, String defaultValue) {
        var normalized = text(value);
        return normalized != null ? normalized : defaultValue;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
