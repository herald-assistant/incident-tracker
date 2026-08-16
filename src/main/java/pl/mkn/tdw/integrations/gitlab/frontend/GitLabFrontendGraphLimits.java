package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendGraphLimits(
        int maxRootCandidates,
        int maxRouteNodes,
        int maxRouteFiles,
        int maxSourceReads,
        int maxAliasResolutions,
        int maxImportDepth,
        int maxComponentDepth,
        int maxContextFiles,
        int maxFileCharacters,
        int maxTotalCharacters
) {

    public static GitLabFrontendGraphLimits defaults() {
        return new GitLabFrontendGraphLimits(
                10,
                400,
                80,
                300,
                500,
                12,
                5,
                40,
                200_000,
                2_000_000
        );
    }

    public GitLabFrontendGraphLimits {
        maxRootCandidates = bounded(maxRootCandidates, 50, "maxRootCandidates");
        maxRouteNodes = bounded(maxRouteNodes, 2_000, "maxRouteNodes");
        maxRouteFiles = bounded(maxRouteFiles, 300, "maxRouteFiles");
        maxSourceReads = bounded(maxSourceReads, 5_000, "maxSourceReads");
        maxAliasResolutions = bounded(maxAliasResolutions, 5_000, "maxAliasResolutions");
        maxImportDepth = bounded(maxImportDepth, 32, "maxImportDepth");
        maxComponentDepth = bounded(maxComponentDepth, 16, "maxComponentDepth");
        maxContextFiles = bounded(maxContextFiles, 120, "maxContextFiles");
        maxFileCharacters = bounded(maxFileCharacters, 200_000, "maxFileCharacters");
        maxTotalCharacters = bounded(maxTotalCharacters, 2_000_000, "maxTotalCharacters");
    }

    private static int bounded(int value, int maximum, String field) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(field + " must be between 1 and " + maximum);
        }
        return value;
    }
}
