package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

public record GitLabRepositoryTreeNode(
        String path,
        String type
) {
}
