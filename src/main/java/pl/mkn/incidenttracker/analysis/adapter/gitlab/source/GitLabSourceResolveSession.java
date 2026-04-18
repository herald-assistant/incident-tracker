package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GitLabSourceResolveSession {

    private final Map<String, List<GitLabRepositoryTreeNode>> repositoryTrees = new HashMap<>();

    List<GitLabRepositoryTreeNode> findRepositoryTree(String gitlabApiBaseUrl, String projectIdOrPath, String ref) {
        return repositoryTrees.get(cacheKey(gitlabApiBaseUrl, projectIdOrPath, ref));
    }

    void storeRepositoryTree(
            String gitlabApiBaseUrl,
            String projectIdOrPath,
            String ref,
            List<GitLabRepositoryTreeNode> nodes
    ) {
        repositoryTrees.put(cacheKey(gitlabApiBaseUrl, projectIdOrPath, ref), nodes);
    }

    private String cacheKey(String gitlabApiBaseUrl, String projectIdOrPath, String ref) {
        return gitlabApiBaseUrl + "|" + projectIdOrPath + "|" + ref;
    }
}
