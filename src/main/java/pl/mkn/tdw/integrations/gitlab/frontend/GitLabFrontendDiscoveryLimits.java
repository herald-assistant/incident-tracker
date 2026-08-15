package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendDiscoveryLimits(
        int maxInventoryFiles,
        int maxRouteFiles,
        int maxRouteEntries,
        int maxContextFiles,
        int maxFileCharacters,
        int maxTotalCharacters,
        int maxTraversalDepth
) {

    public static GitLabFrontendDiscoveryLimits defaults() {
        return new GitLabFrontendDiscoveryLimits(2_000, 80, 400, 40, 50_000, 500_000, 3);
    }

    public GitLabFrontendDiscoveryLimits {
        maxInventoryFiles = bounded(maxInventoryFiles, 10_000, "maxInventoryFiles");
        maxRouteFiles = bounded(maxRouteFiles, 300, "maxRouteFiles");
        maxRouteEntries = bounded(maxRouteEntries, 2_000, "maxRouteEntries");
        maxContextFiles = bounded(maxContextFiles, 120, "maxContextFiles");
        maxFileCharacters = bounded(maxFileCharacters, 200_000, "maxFileCharacters");
        maxTotalCharacters = bounded(maxTotalCharacters, 2_000_000, "maxTotalCharacters");
        maxTraversalDepth = bounded(maxTraversalDepth, 8, "maxTraversalDepth");
    }

    private static int bounded(int value, int maximum, String field) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(field + " must be between 1 and " + maximum);
        }
        return value;
    }
}
