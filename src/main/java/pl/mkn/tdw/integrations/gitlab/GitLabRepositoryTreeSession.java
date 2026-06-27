package pl.mkn.tdw.integrations.gitlab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GitLabRepositoryTreeSession {

    private final Map<String, List<GitLabRepositoryTreeNode>> repositoryTrees = new HashMap<>();

    public List<GitLabRepositoryTreeNode> findRepositoryTree(
            String gitlabApiBaseUrl,
            String projectIdOrPath,
            String ref,
            String pathPrefix
    ) {
        return repositoryTrees.get(cacheKey(gitlabApiBaseUrl, projectIdOrPath, ref, pathPrefix));
    }

    public void storeRepositoryTree(
            String gitlabApiBaseUrl,
            String projectIdOrPath,
            String ref,
            String pathPrefix,
            List<GitLabRepositoryTreeNode> nodes
    ) {
        repositoryTrees.put(cacheKey(gitlabApiBaseUrl, projectIdOrPath, ref, pathPrefix), nodes);
    }

    private String cacheKey(String gitlabApiBaseUrl, String projectIdOrPath, String ref, String pathPrefix) {
        return gitlabApiBaseUrl + "|" + projectIdOrPath + "|" + ref + "|" + (pathPrefix != null ? pathPrefix : "");
    }
}
