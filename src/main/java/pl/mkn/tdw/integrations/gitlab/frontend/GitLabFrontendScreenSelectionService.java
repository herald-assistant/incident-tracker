package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
class GitLabFrontendScreenSelectionService {

    private static final Pattern TEMPLATE_URL = Pattern.compile("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]");

    private final GitLabFrontendRouteGraphDiscoveryService graphDiscoveryService;
    private final GitLabRepositoryPort repositoryPort;

    GitLabFrontendScreenReachabilitySeed select(GitLabFrontendScreenSelectionRequest request) {
        var graph = graphDiscoveryService.discover(request.scope(), request.limits());
        var screenNode = graph.nodes().stream()
                .filter(node -> node.screen() != null)
                .filter(node -> request.screenId().equals(node.screen().screenId()))
                .findFirst()
                .orElseThrow(() -> failure(
                        "FRONTEND_SCREEN_NOT_FOUND",
                        "Selected screen is not present in the route graph."
                ));
        var chain = graph.effectiveRouteChains().stream()
                .filter(candidate -> candidate.screen().equals(screenNode.screen()))
                .findFirst()
                .orElseThrow(() -> failure(
                        "FRONTEND_SCREEN_SOURCE_UNRESOLVED",
                        "Selected screen route chain is unavailable."
                ));
        verifyRevision(request.expectedRevision(), graph.sourceRevision());

        var session = new GitLabFrontendTargetedSourceSession(repositoryPort, request.scope(), request.limits());
        var files = new LinkedHashMap<String, GitLabFrontendSourceFile>();
        for (var segment : chain.segments()) {
            addFile(segment.source().path(), GitLabFrontendSourceRole.ROUTE_CONFIGURATION, session, files);
        }
        var viewPath = screenNode.viewTarget() != null ? screenNode.viewTarget().sourcePath() : null;
        if (!StringUtils.hasText(viewPath)) {
            throw failure("FRONTEND_SCREEN_SOURCE_UNRESOLVED", "Selected screen component source is unavailable.");
        }
        var source = addFile(viewPath, GitLabFrontendSourceRole.VIEW_COMPONENT, session, files);
        if (source != null) {
            var matcher = TEMPLATE_URL.matcher(source);
            while (matcher.find()) {
                addFile(relative(viewPath, matcher.group(1)), GitLabFrontendSourceRole.TEMPLATE, session, files);
            }
        }

        var diagnostics = new ArrayList<>(graph.diagnostics());
        diagnostics.addAll(session.diagnostics());
        return new GitLabFrontendScreenReachabilitySeed(
                request.scope(),
                graph.sourceRevision(),
                screenNode,
                chain,
                graph.coverage(),
                List.copyOf(files.values()),
                diagnostics
        );
    }

    private void verifyRevision(String expected, GitLabFrontendSourceRevision revision) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (!StringUtils.hasText(revision.commitId())) {
            throw failure("FRONTEND_SOURCE_REVISION_UNRESOLVED", "Exact source revision could not be confirmed.");
        }
        if (!expected.equals(revision.commitId())) {
            throw failure("FRONTEND_SOURCE_REVISION_CHANGED", "Source revision changed after screen selection.");
        }
    }

    private String addFile(
            String path,
            GitLabFrontendSourceRole requestedRole,
            GitLabFrontendTargetedSourceSession session,
            LinkedHashMap<String, GitLabFrontendSourceFile> files
    ) {
        var normalized = GitLabFrontendTargetedSourceSession.normalize(path);
        if (files.containsKey(normalized)) {
            var current = files.get(normalized);
            var roles = new LinkedHashSet<>(current.roles());
            roles.add(requestedRole);
            files.put(normalized, new GitLabFrontendSourceFile(
                    current.path(), List.copyOf(roles), current.content(), current.returnedCharacters(), current.truncated()
            ));
            return current.content();
        }
        if (!session.markContextFile(normalized)) {
            return null;
        }
        var source = session.readRequired(normalized);
        if (source == null) {
            return null;
        }
        files.put(normalized, new GitLabFrontendSourceFile(
                normalized, List.of(requestedRole), source, source.length(), false
        ));
        return source;
    }

    private String relative(String ownerPath, String relative) {
        var separator = ownerPath.lastIndexOf('/');
        var parent = separator >= 0 ? ownerPath.substring(0, separator) : "";
        return GitLabFrontendTargetedSourceSession.normalize(parent + "/" + relative);
    }

    private GitLabFrontendDiscoveryException failure(String code, String message) {
        return new GitLabFrontendDiscoveryException(code, message);
    }
}
