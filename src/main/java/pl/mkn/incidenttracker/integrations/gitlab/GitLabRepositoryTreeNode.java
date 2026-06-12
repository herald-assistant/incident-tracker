package pl.mkn.incidenttracker.integrations.gitlab;

public record GitLabRepositoryTreeNode(
        String path,
        String type
) {
}
