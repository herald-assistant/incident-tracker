package pl.mkn.incidenttracker.integrations.gitlab.source;

public record GitLabRepositoryTreeNode(
        String path,
        String type
) {
}
