package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

public record OperationalContextCodeSearchReadModel(
        String contract,
        int contractVersion,
        ReadModelProfile profile,
        OperationalContextRelationIndex.EntityRef analysisTarget,
        List<CodeSearchScopeView> scopes,
        List<RepositoryView> repositories,
        List<String> limitations,
        List<OperationalContextRelationIndex.ValidationFinding> validationFindings
) {

    public OperationalContextCodeSearchReadModel {
        contract = contract != null ? contract : "operational-context.code-search";
        contractVersion = contractVersion > 0 ? contractVersion : 1;
        profile = profile != null ? profile : ReadModelProfile.defaultProfile();
        scopes = copyList(scopes);
        repositories = copyList(repositories);
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
            String scopeType,
            OperationalContextRelationIndex.EntityRef target,
            List<OperationalContextRelationIndex.EntityRef> repositories,
            List<String> limitations,
            OperationalContextRelationIndex.Provenance provenance
    ) {

        public CodeSearchScopeView {
            repositories = copyList(repositories);
            limitations = copyTextList(limitations);
        }
    }

    public record RepositoryView(
            OperationalContextRelationIndex.EntityRef repository,
            String role,
            Integer priority,
            String reason,
            List<String> readFor,
            String searchMode,
            List<String> pathPrefixes,
            GitView git,
            OperationalContextRelationIndex.Provenance provenance
    ) {

        public RepositoryView {
            role = textOrDefault(role, "referenced");
            reason = text(reason);
            readFor = copyTextList(readFor);
            searchMode = text(searchMode);
            pathPrefixes = copyTextList(pathPrefixes);
            git = git != null ? git : GitView.empty();
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
