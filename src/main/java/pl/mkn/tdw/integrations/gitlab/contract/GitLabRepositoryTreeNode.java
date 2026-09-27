package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabRepositoryTreeNode(
        String path,
        String type
) {
}
