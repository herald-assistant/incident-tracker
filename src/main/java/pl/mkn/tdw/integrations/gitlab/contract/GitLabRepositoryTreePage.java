package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.List;

public record GitLabRepositoryTreePage(List<GitLabRepositoryTreeNode> nodes, String nextCursor) {
    public GitLabRepositoryTreePage {
        nodes = List.copyOf(nodes);
    }
}
