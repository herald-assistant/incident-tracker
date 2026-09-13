package pl.mkn.tdw.integrations.gitlab;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitLabRepositoryTreeService {

    private static final int TREE_PAGE_SIZE = 100;
    private static final int MAX_WINDOW_BLOBS = 200;
    private static final int MAX_WINDOW_REQUESTS = 5;
    private static final int MAX_PAGE_NUMBER = 100_000;
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

    /** Fetches at most five GitLab pages and never caches or assembles a complete tree. */
    public RepositoryTreePage fetchRepositoryBlobsPage(
            String gitlabBaseUrl,
            String projectIdOrPath,
            String ref,
            String pathPrefix,
            String cursor,
            int maxBlobs
    ) {
        if (maxBlobs < 1 || maxBlobs > MAX_WINDOW_BLOBS) {
            throw new IllegalArgumentException("maxBlobs must be between 1 and 200.");
        }
        var gitlabApiBaseUrl = apiBaseUrl(gitlabBaseUrl);
        var encodedProjectIdOrPath = encodePathSegment(projectIdOrPath.trim());
        var effectivePathPrefix = normalizePathPrefix(pathPrefix);
        var cursorScope = cursorScope(gitlabApiBaseUrl, encodedProjectIdOrPath, ref, effectivePathPrefix);
        var position = decodeCursor(cursor, cursorScope);
        var blobs = new ArrayList<GitLabRepositoryTreeNode>(maxBlobs);

        for (var request = 0; request < MAX_WINDOW_REQUESTS; request++) {
            var entity = fetchTreePage(gitlabApiBaseUrl, encodedProjectIdOrPath, ref,
                    effectivePathPrefix, position.page());
            var body = entity.getBody() != null ? entity.getBody() : new GitLabRepositoryTreeNode[0];
            if (body.length > TREE_PAGE_SIZE || position.offset() > body.length) {
                throw new IllegalStateException("GitLab repository tree page exceeded the bounded page contract.");
            }
            var nextPage = nextPage(entity.getHeaders().getFirst("X-Next-Page"), position.page());
            for (var index = position.offset(); index < body.length; index++) {
                if (body[index] != null && "blob".equals(body[index].type())) {
                    blobs.add(body[index]);
                }
                if (blobs.size() == maxBlobs) {
                    var continuation = index + 1 < body.length
                            ? encodeCursor(cursorScope, position.page(), index + 1)
                            : nextPage != null ? encodeCursor(cursorScope, nextPage, 0) : null;
                    return new RepositoryTreePage(List.copyOf(blobs), continuation);
                }
            }
            if (nextPage == null) {
                return new RepositoryTreePage(List.copyOf(blobs), null);
            }
            if (request == MAX_WINDOW_REQUESTS - 1) {
                return new RepositoryTreePage(List.copyOf(blobs), encodeCursor(cursorScope, nextPage, 0));
            }
            position = new CursorPosition(nextPage, 0);
        }
        throw new IllegalStateException("Unreachable GitLab tree pagination state.");
    }

    /** One bounded, non-recursive page including both directories and files. */
    public RepositoryTreePage fetchRepositoryChildrenPage(
            String gitlabBaseUrl,
            String projectIdOrPath,
            String ref,
            String directory,
            String cursor,
            int maxEntries
    ) {
        if (maxEntries < 1 || maxEntries > TREE_PAGE_SIZE) {
            throw new IllegalArgumentException("maxEntries must be between 1 and 100.");
        }
        var gitlabApiBaseUrl = apiBaseUrl(gitlabBaseUrl);
        var encodedProject = encodePathSegment(projectIdOrPath.trim());
        var path = normalizePathPrefix(directory);
        var cursorScope = cursorScope(gitlabApiBaseUrl, encodedProject, ref, "children:" + path);
        var position = decodeCursor(cursor, cursorScope);
        try {
            var uri = gitlabApiBaseUrl + "/projects/" + encodedProject
                    + "/repository/tree?recursive=false&per_page=" + TREE_PAGE_SIZE
                    + "&ref=" + encodeQueryParam(ref)
                    + "&page=" + position.page()
                    + (StringUtils.hasText(path) ? "&path=" + encodeQueryParam(path) : "");
            var entity = gitLabRestClientFactory.create().get().uri(URI.create(uri))
                    .retrieve().toEntity(GitLabRepositoryTreeNode[].class);
            var body = entity.getBody() != null ? entity.getBody() : new GitLabRepositoryTreeNode[0];
            if (body.length > TREE_PAGE_SIZE || position.offset() > body.length) {
                throw new IllegalStateException("GitLab directory page exceeded the bounded page contract.");
            }
            var end = Math.min(body.length, position.offset() + maxEntries);
            var nodes = List.copyOf(java.util.Arrays.asList(body).subList(position.offset(), end));
            var nextPage = nextPage(entity.getHeaders().getFirst("X-Next-Page"), position.page());
            var nextCursor = end < body.length
                    ? encodeCursor(cursorScope, position.page(), end)
                    : nextPage != null ? encodeCursor(cursorScope, nextPage, 0) : null;
            return new RepositoryTreePage(nodes, nextCursor);
        } catch (RestClientResponseException exception) {
            throw new GitLabRepositoryTreeException(exception.getStatusCode().value(),
                    "GitLab repository directory request failed with status " + exception.getStatusCode().value(),
                    exception);
        }
    }

    private org.springframework.http.ResponseEntity<GitLabRepositoryTreeNode[]> fetchTreePage(
            String gitlabApiBaseUrl,
            String encodedProjectIdOrPath,
            String ref,
            String pathPrefix,
            int page
    ) {
        try {
            return gitLabRestClientFactory.create().get()
                    .uri(repositoryTreeUri(gitlabApiBaseUrl, encodedProjectIdOrPath, ref,
                            pathPrefix, Integer.toString(page)))
                    .retrieve()
                    .toEntity(GitLabRepositoryTreeNode[].class);
        } catch (RestClientResponseException exception) {
            throw new GitLabRepositoryTreeException(exception.getStatusCode().value(),
                    "GitLab repository tree request failed with status " + exception.getStatusCode().value(),
                    exception);
        }
    }

    private Integer nextPage(String raw, int currentPage) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            var value = Integer.parseInt(raw);
            if (value <= currentPage || value > MAX_PAGE_NUMBER) {
                throw new IllegalArgumentException("Invalid GitLab next-page header.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid GitLab next-page header.", exception);
        }
    }

    private CursorPosition decodeCursor(String raw, String cursorScope) {
        if (!StringUtils.hasText(raw)) {
            return new CursorPosition(1, 0);
        }
        if (raw.length() > 2_048) {
            throw new IllegalArgumentException("Invalid GitLab tree cursor.");
        }
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            var parts = decoded.split("\\n", -1);
            if (parts.length != 4 || !"v1".equals(parts[0])
                    || !cursorScope.equals(parts[1])) {
                throw new IllegalArgumentException("GitLab tree cursor does not match repository scope.");
            }
            var page = Integer.parseInt(parts[2]);
            var offset = Integer.parseInt(parts[3]);
            if (page < 1 || page > MAX_PAGE_NUMBER || offset < 0 || offset >= TREE_PAGE_SIZE) {
                throw new IllegalArgumentException("Invalid GitLab tree cursor position.");
            }
            return new CursorPosition(page, offset);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid GitLab tree cursor.", exception);
        }
    }

    private String encodeCursor(String cursorScope, int page, int offset) {
        var payload = "v1\n" + cursorScope + "\n" + page + "\n" + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String cursorScope(String baseUrl, String project, String ref, String pathPrefix) {
        try {
            var value = baseUrl + "\n" + project + "\n" + ref + "\n" + (pathPrefix != null ? pathPrefix : "");
            var hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record RepositoryTreePage(List<GitLabRepositoryTreeNode> nodes, String nextCursor) {
    }

    private record CursorPosition(int page, int offset) {
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
