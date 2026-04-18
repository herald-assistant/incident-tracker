package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.UriUtils;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRestClientFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitLabSourceResolveService {

    private static final int TREE_PAGE_SIZE = 100;
    private static final int PREVIEW_CONTENT_LIMIT = 2_000;
    private static final List<String> SOURCE_FILE_SUFFIXES = List.of(".java", ".kt", ".groovy");
    private static final String TREE_CACHE_ATTRIBUTE = GitLabSourceResolveService.class.getName() + ".repositoryTreeCache";

    private final GitLabRestClientFactory gitLabRestClientFactory;

    public GitLabSourceResolveResponse resolve(GitLabSourceResolveRequest request) {
        return resolve(request, requestScopedSession());
    }

    public GitLabSourceResolveResponse resolve(GitLabSourceResolveRequest request, GitLabSourceResolveSession session) {
        return resolveInternal(request, null, effectiveSession(session));
    }

    public GitLabSourceResolveResponse resolvePreview(GitLabSourceResolveRequest request) {
        return resolvePreview(request, requestScopedSession());
    }

    public GitLabSourceResolveResponse resolvePreview(GitLabSourceResolveRequest request, GitLabSourceResolveSession session) {
        return resolveInternal(request, PREVIEW_CONTENT_LIMIT, effectiveSession(session));
    }

    public GitLabSourceResolveMatch resolveMatch(GitLabSourceResolveRequest request) {
        return resolveMatch(request, requestScopedSession());
    }

    public GitLabSourceResolveMatch resolveMatch(
            GitLabSourceResolveRequest request,
            GitLabSourceResolveSession session
    ) {
        var effectiveSession = effectiveSession(session);
        var effectiveRef = request.effectiveRef();
        var projectIdOrPath = encodeProjectIdOrPath(request.groupPath(), request.projectPath());
        var symbol = request.symbol().trim();

        log.info(
                "Resolving GitLab source match symbol={} groupPath={} projectPath={} ref={}",
                symbol,
                request.groupPath(),
                request.projectPath(),
                effectiveRef
        );

        var treeNodes = fetchRepositoryTree(
                request.gitlabBaseUrl(),
                projectIdOrPath,
                effectiveRef,
                request.groupPath(),
                request.projectPath(),
                effectiveSession
        );
        var candidates = findCandidates(treeNodes, symbol);

        if (candidates.isEmpty()) {
            throw failure(
                    HttpStatus.NOT_FOUND,
                    List.of(),
                    "No source file candidates found for symbol: " + symbol
            );
        }

        var bestCandidate = candidates.get(0);
        return new GitLabSourceResolveMatch(
                bestCandidate.path(),
                bestCandidate.score(),
                candidates.stream().map(GitLabSourceFileCandidate::path).toList()
        );
    }

    public GitLabSourceResolveSession openSession() {
        return new GitLabSourceResolveSession();
    }

    private GitLabSourceResolveResponse resolveInternal(
            GitLabSourceResolveRequest request,
            Integer contentLimit,
            GitLabSourceResolveSession session
    ) {
        var effectiveRef = request.effectiveRef();
        var projectIdOrPath = encodeProjectIdOrPath(request.groupPath(), request.projectPath());
        var symbol = request.symbol().trim();
        var match = resolveMatch(request, session);
        var rawContent = fetchRawFile(
                request.gitlabBaseUrl(),
                projectIdOrPath,
                effectiveRef,
                match.matchedPath(),
                request.groupPath(),
                request.projectPath()
        );
        var response = new GitLabSourceResolveResponse(
                match.matchedPath(),
                match.score(),
                match.candidates(),
                limitContent(rawContent, contentLimit),
                "OK"
        );

        log.info(
                "Resolved GitLab source symbol={} matchedPath={} score={} candidateCount={}",
                symbol,
                response.matchedPath(),
                response.score(),
                response.candidates().size()
        );

        return response;
    }

    private List<GitLabRepositoryTreeNode> fetchRepositoryTree(
            String gitlabBaseUrl,
            String projectIdOrPath,
            String ref,
            String groupPath,
            String projectPath,
            GitLabSourceResolveSession session
    ) {
        var gitlabApiBaseUrl = apiBaseUrl(gitlabBaseUrl);

        var cachedNodes = session.findRepositoryTree(gitlabApiBaseUrl, projectIdOrPath, ref);
        if (cachedNodes != null) {
            log.debug(
                    "Using cached GitLab repository tree groupPath={} projectPath={} ref={} nodeCount={}",
                    groupPath,
                    projectPath,
                    ref,
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
                        .uri(repositoryTreeUri(gitlabBaseUrl, projectIdOrPath, ref, page))
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
                        "Fetched GitLab tree page={} groupPath={} projectPath={} blobCount={} nextPage={}",
                        page,
                        groupPath,
                        projectPath,
                        body != null ? body.length : 0,
                        entity.getHeaders().getFirst("X-Next-Page")
                );

                page = entity.getHeaders().getFirst("X-Next-Page");
            } catch (RestClientResponseException exception) {
                throw mapTreeFailure(exception, groupPath, projectPath, ref);
            }
        }

        var nodes = List.copyOf(allNodes);
        session.storeRepositoryTree(gitlabApiBaseUrl, projectIdOrPath, ref, nodes);
        return nodes;
    }

    private String fetchRawFile(
            String gitlabBaseUrl,
            String projectIdOrPath,
            String ref,
            String filePath,
            String groupPath,
            String projectPath
    ) {
        try {
            return gitLabRestClientFactory.create()
                    .get()
                    .uri(rawFileUri(gitlabBaseUrl, projectIdOrPath, filePath, ref))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw mapRawFileFailure(exception, groupPath, projectPath, ref, filePath);
        }
    }

    private List<GitLabSourceFileCandidate> findCandidates(List<GitLabRepositoryTreeNode> treeNodes, String symbol) {
        var symbolParts = splitSymbol(symbol);
        var expectedFileNames = SOURCE_FILE_SUFFIXES.stream()
                .map(suffix -> symbolParts.simpleName() + suffix)
                .toList();
        var candidates = new ArrayList<GitLabSourceFileCandidate>();

        for (var node : treeNodes) {
            if (expectedFileNames.stream().noneMatch(node.path()::endsWith)) {
                continue;
            }

            candidates.add(new GitLabSourceFileCandidate(
                    node.path(),
                    calculateScore(node.path(), symbolParts.packagePath())
            ));
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(GitLabSourceFileCandidate::score).reversed()
                        .thenComparing(GitLabSourceFileCandidate::path))
                .toList();
    }

    private int calculateScore(String path, String packagePath) {
        var score = 0;

        if (StringUtils.hasText(packagePath) && path.contains(packagePath)) {
            score += 100;
        }
        if (containsPathSegment(path, "src/main/java")) {
            score += 30;
        }
        if (containsPathSegment(path, "src/main/kotlin")) {
            score += 20;
        }
        if (containsPathSegment(path, "src/test")) {
            score += 10;
        }
        if (containsPathSegment(path, "generated")) {
            score -= 30;
        }

        return score;
    }

    private boolean containsPathSegment(String path, String fragment) {
        return path.startsWith(fragment + "/") || path.contains("/" + fragment + "/");
    }

    private SymbolParts splitSymbol(String symbol) {
        var normalizedSymbol = symbol.trim();
        var separatorIndex = normalizedSymbol.lastIndexOf('.');
        if (separatorIndex < 0) {
            return new SymbolParts(normalizedSymbol, "");
        }

        var simpleName = normalizedSymbol.substring(separatorIndex + 1);
        var packagePath = normalizedSymbol.substring(0, separatorIndex).replace('.', '/');
        return new SymbolParts(simpleName, packagePath);
    }

    private String limitContent(String content, Integer contentLimit) {
        if (content == null || contentLimit == null || content.length() <= contentLimit) {
            return content;
        }

        return content.substring(0, contentLimit);
    }

    private GitLabSourceResolveException mapTreeFailure(
            RestClientResponseException exception,
            String groupPath,
            String projectPath,
            String ref
    ) {
        if (exception.getStatusCode().value() == 404) {
            return failure(
                    HttpStatus.NOT_FOUND,
                    List.of(),
                    "GitLab project or ref not found: " + groupPath + "/" + projectPath + "@" + ref
            );
        }

        return failure(
                HttpStatus.BAD_GATEWAY,
                List.of(),
                "GitLab repository tree request failed with status " + exception.getStatusCode().value()
        );
    }

    private GitLabSourceResolveException mapRawFileFailure(
            RestClientResponseException exception,
            String groupPath,
            String projectPath,
            String ref,
            String filePath
    ) {
        if (exception.getStatusCode().value() == 404) {
            return failure(
                    HttpStatus.NOT_FOUND,
                    List.of(filePath),
                    "GitLab file not found for " + groupPath + "/" + projectPath + "@" + ref + ": " + filePath
            );
        }

        return failure(
                HttpStatus.BAD_GATEWAY,
                List.of(filePath),
                "GitLab raw file request failed with status " + exception.getStatusCode().value()
        );
    }

    private GitLabSourceResolveException failure(HttpStatus status, List<String> candidates, String message) {
        return new GitLabSourceResolveException(
                status,
                new GitLabSourceResolveResponse(
                        null,
                        null,
                        List.copyOf(new LinkedHashSet<>(candidates)),
                        null,
                        message
                )
        );
    }

    private String encodeProjectIdOrPath(String groupPath, String projectPath) {
        return encodePathSegment(groupPath.trim() + "/" + projectPath.trim());
    }

    private URI repositoryTreeUri(String gitlabBaseUrl, String projectIdOrPath, String ref, String page) {
        return URI.create(apiBaseUrl(gitlabBaseUrl)
                + "/projects/" + projectIdOrPath
                + "/repository/tree?recursive=true"
                + "&per_page=" + TREE_PAGE_SIZE
                + "&ref=" + encodeQueryParam(ref)
                + "&page=" + encodeQueryParam(page));
    }

    private URI rawFileUri(String gitlabBaseUrl, String projectIdOrPath, String filePath, String ref) {
        return URI.create(apiBaseUrl(gitlabBaseUrl)
                + "/projects/" + projectIdOrPath
                + "/repository/files/" + encodePathSegment(filePath)
                + "/raw?ref=" + encodeQueryParam(ref));
    }

    private String apiBaseUrl(String gitlabBaseUrl) {
        var normalized = gitlabBaseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.endsWith("/api/v4") ? normalized : normalized + "/api/v4";
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private GitLabSourceResolveSession requestScopedSession() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return openSession();
        }

        var existingCache = requestAttributes.getAttribute(TREE_CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existingCache instanceof GitLabSourceResolveSession session) {
            return session;
        }

        var newSession = openSession();
        requestAttributes.setAttribute(TREE_CACHE_ATTRIBUTE, newSession, RequestAttributes.SCOPE_REQUEST);
        return newSession;
    }

    private GitLabSourceResolveSession effectiveSession(GitLabSourceResolveSession session) {
        return session != null ? session : openSession();
    }

    private record SymbolParts(
            String simpleName,
            String packagePath
    ) {
    }

}
