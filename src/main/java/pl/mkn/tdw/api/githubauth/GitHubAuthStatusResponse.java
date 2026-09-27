package pl.mkn.tdw.api.githubauth;

public record GitHubAuthStatusResponse(
        boolean configured,
        String settingsUrl
) {
}
