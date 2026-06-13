package pl.mkn.incidenttracker.integrations.gitlab.usecase;

record GitLabEndpointUseCaseSourceFile(
        String path,
        String content,
        int characterCount,
        boolean truncated
) {
    GitLabEndpointUseCaseSourceFile {
        content = content != null ? content : "";
    }
}
