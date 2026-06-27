package pl.mkn.tdw.integrations.gitlab;

public record GitLabRepositoryTreeNode(
        String path,
        String type
) {
}
