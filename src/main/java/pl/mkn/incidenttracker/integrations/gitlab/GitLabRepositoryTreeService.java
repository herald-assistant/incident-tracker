package pl.mkn.incidenttracker.integrations.gitlab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitLabRepositoryTreeService {

    private static final int TREE_PAGE_SIZE = 100;
    private static final String TREE_CACHE_ATTRIBUTE = GitLabRepositoryTreeService.class.getName() + ".repositoryTreeCache";

    private final GitLabRestClientFactory gitLabRestClientFactory;

    public List<GitLabRepositoryTreeNode> fetchRepositoryBlobs(
            String gitlabBaseUrl,
            String projectIdOrPath,
            String ref,
            String pathPrefix,
            GitLabRepositoryTreeSession session
    ) {
        var effectiveSession = session != null ? session : openSession();
        var gitlabApiBaseUrl = apiBaseUrl(gitlabBaseUrl);
        var encodedProjectIdOrPath = encodePathSegment(projectIdOrPath.trim());
        var effectivePathPrefix = normalizePathPrefix(pathPrefix);

        var cachedNodes = effectiveSession.findRepositoryTree(
                gitlabApiBaseUrl,
                encodedProjectIdOrPath,
                ref,
                effectivePathPrefix
        );
        if (cachedNodes != null) {
            log.debug(
                    "Using cached GitLab repository tree projectIdOrPath={} ref={} pathPrefix={} nodeCount={}",
                    projectIdOrPath,
                    ref,
                    effectivePathPrefix,
                    cachedNodes.size()
            );
            return cachedNodes;
        }

        var allNodes = new ArrayList<GitLabRepositoryTreeNode>();
        var page = "1";

        while (StringUtils.hasText(page)) {
            try {
                var entity = gitLabRestClientFactory.create()
                        .get()
                        .uri(repositoryTreeUri(gitlabApiBaseUrl, encodedProjectIdOrPath, ref, effectivePathPrefix, page))
                        .retrieve()
                        .toEntity(GitLabRepositoryTreeNode[].class);

                var body = entity.getBody();
                if (body != null) {
                    for (var node : body) {
                        if ("blob".equals(node.type())) {
                            allNodes.add(node);
                        }
                    }
                }

                log.debug(
                        "Fetched GitLab tree page={} projectIdOrPath={} ref={} pathPrefix={} blobCount={} nextPage={}",
                        page,
                        projectIdOrPath,
                        ref,
                        effectivePathPrefix,
                        body != null ? body.length : 0,
                        entity.getHeaders().getFirst("X-Next-Page")
                );

                page = entity.getHeaders().getFirst("X-Next-Page");
            } catch (RestClientResponseException exception) {
                throw new GitLabRepositoryTreeException(
                        exception.getStatusCode().value(),
                        "GitLab repository tree request failed with status " + exception.getStatusCode().value(),
                        exception
                );
            }
        }

        var nodes = List.copyOf(allNodes);
        effectiveSession.storeRepositoryTree(
                gitlabApiBaseUrl,
                encodedProjectIdOrPath,
                ref,
                effectivePathPrefix,
                nodes
        );
        return nodes;
    }

    public GitLabRepositoryTreeSession openSession() {
        return new GitLabRepositoryTreeSession();
    }

    public GitLabRepositoryTreeSession requestScopedSession() {
        return requestScopedSession(TREE_CACHE_ATTRIBUTE);
    }

    public GitLabRepositoryTreeSession requestScopedSession(String attributeName) {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return openSession();
        }

        var existingCache = requestAttributes.getAttribute(attributeName, RequestAttributes.SCOPE_REQUEST);
        if (existingCache instanceof GitLabRepositoryTreeSession session) {
            return session;
        }

        var newSession = openSession();
        requestAttributes.setAttribute(attributeName, newSession, RequestAttributes.SCOPE_REQUEST);
        return newSession;
    }

    private URI repositoryTreeUri(
            String gitlabApiBaseUrl,
            String projectIdOrPath,
            String ref,
            String pathPrefix,
            String page
    ) {
        var uri = gitlabApiBaseUrl
                + "/projects/" + projectIdOrPath
                + "/repository/tree?recursive=true"
                + "&per_page=" + TREE_PAGE_SIZE
                + "&ref=" + encodeQueryParam(ref)
                + "&page=" + encodeQueryParam(page);

        if (StringUtils.hasText(pathPrefix)) {
            uri += "&path=" + encodeQueryParam(pathPrefix);
        }

        return URI.create(uri);
    }

    private String apiBaseUrl(String gitlabBaseUrl) {
        if (!StringUtils.hasText(gitlabBaseUrl)) {
            throw new IllegalStateException("GitLab base URL must be configured.");
        }

        var normalized = gitlabBaseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.endsWith("/api/v4") ? normalized : normalized + "/api/v4";
    }

    private String normalizePathPrefix(String pathPrefix) {
        if (!StringUtils.hasText(pathPrefix)) {
            return null;
        }
        var normalized = pathPrefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }
}
