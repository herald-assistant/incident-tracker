package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendContextMetrics(
        int sourceFileCount,
        int sourceCharactersRead,
        int returnedSliceCount,
        int returnedCharacters,
        int omittedCharacters,
        int omittedFileCount,
        int relationCount,
        int unresolvedFrontierCount
) {
}
