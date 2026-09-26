package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GitLabFrontendBootstrapDiscoveryService {

    private static final List<String> SEARCH_TERMS = List.of(
            "bootstrapApplication", "provideRouter", "bootstrapModule");
    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final AngularBootstrapSourceParser parser = new AngularBootstrapSourceParser();

    public GitLabFrontendBootstrapDiscoveryResult discover(
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendGraphLimits limits
    ) {
        var effectiveLimits = limits != null ? limits : GitLabFrontendGraphLimits.defaults();
        if (!gitLabRepositoryPort.refExists(scope.group(), scope.projectName(), scope.ref())) {
            throw new GitLabFrontendDiscoveryException(
                    "FRONTEND_REF_NOT_FOUND",
                    "The requested GitLab branch/ref does not exist"
            );
        }
        var diagnostics = new ArrayList<GitLabFrontendGraphDiagnostic>();
        var candidates = candidates(scope, effectiveLimits, diagnostics);
        if (candidates == null) {
            return blocked(scope, 0, 0, false, diagnostics);
        }
        var candidateLimitReached = candidates.size() > effectiveLimits.maxRootCandidates();
        if (candidateLimitReached) {
            diagnostics.add(diagnostic(
                    GitLabFrontendGraphDiagnosticCode.ROOT_CANDIDATE_LIMIT_REACHED,
                    "Bootstrap discovery exceeded maxRootCandidates="
                            + effectiveLimits.maxRootCandidates() + ".",
                    null
            ));
            return blocked(scope, candidates.size(), 0, true, diagnostics);
        }

        var parsedSources = new ArrayList<AngularBootstrapSourceParser.ParsedSource>();
        for (var candidate : candidates) {
            var content = read(scope, candidate.filePath(), effectiveLimits, diagnostics);
            if (content == null) {
                continue;
            }
            parsedSources.add(parser.parse(candidate.filePath(), content));
        }
        if (diagnostics.stream().anyMatch(this::blocksRootConfirmation)) {
            return blocked(scope, candidates.size(), parsedSources.size(), false, diagnostics);
        }

        var roots = resolveRoots(scope, parsedSources, effectiveLimits, diagnostics);
        if (roots.size() == 1) {
            return new GitLabFrontendBootstrapDiscoveryResult(
                    scope,
                    GitLabFrontendCoverageStatus.READY,
                    roots.get(0),
                    candidates.size(),
                    parsedSources.size(),
                    false,
                    diagnostics
            );
        }
        if (roots.size() > 1) {
            diagnostics.add(diagnostic(
                    GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_AMBIGUOUS,
                    "More than one production Angular bootstrap root reaches provideRouter.",
                    roots.get(0).bootstrapSource()
            ));
        } else if (parsedSources.stream().anyMatch(source -> !source.bootstrapCalls().isEmpty())) {
            diagnostics.add(diagnostic(
                    GitLabFrontendGraphDiagnosticCode.ROUTER_PROVIDER_NOT_FOUND,
                    "No unique router root is reachable from the production bootstrap configuration.",
                    null
            ));
        } else {
            diagnostics.add(diagnostic(
                    GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_NOT_FOUND,
                    "No production Angular bootstrap root was confirmed.",
                    null
            ));
        }
        return blocked(scope, candidates.size(), parsedSources.size(), false, diagnostics);
    }

    private List<GitLabRepositoryFileCandidate> candidates(
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendGraphLimits limits,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        try {
            var result = gitLabRepositoryPort.searchRepositoryFilesByContent(
                    scope.group(),
                    scope.projectName(),
                    scope.ref(),
                    SEARCH_TERMS,
                    limits.maxRootCandidates() + 1
            );
            var distinct = new LinkedHashMap<String, GitLabRepositoryFileCandidate>();
            for (var candidate : result != null ? result : List.<GitLabRepositoryFileCandidate>of()) {
                if (candidate == null || !isEligible(candidate.filePath(), scope.pathPrefixes())) {
                    continue;
                }
                distinct.putIfAbsent(normalizePath(candidate.filePath()), candidate);
            }
            return distinct.values().stream()
                    .sorted(java.util.Comparator.comparing(candidate -> normalizePath(candidate.filePath())))
                    .toList();
        } catch (RuntimeException exception) {
            diagnostics.add(diagnostic(
                    GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_SEARCH_FAILED,
                    "GitLab content search failed while locating the Angular bootstrap root: "
                            + exception.getClass().getSimpleName() + ".",
                    null
            ));
            return null;
        }
    }

    private String read(
            GitLabFrontendRepositoryScope scope,
            String sourcePath,
            GitLabFrontendGraphLimits limits,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        try {
            var source = gitLabRepositoryPort.readFile(
                    scope.group(),
                    scope.projectName(),
                    scope.ref(),
                    sourcePath,
                    limits.maxFileCharacters()
            );
            if (source == null || !StringUtils.hasText(source.content())) {
                diagnostics.add(diagnostic(
                        GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_CANDIDATE_UNREADABLE,
                        "A bootstrap candidate could not be read.",
                        new GitLabFrontendSourceReference(sourcePath, null, null, null)
                ));
                return null;
            }
            if (source.truncated()) {
                diagnostics.add(diagnostic(
                        GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_CANDIDATE_TRUNCATED,
                        "A bootstrap candidate exceeded maxFileCharacters and cannot be confirmed safely.",
                        new GitLabFrontendSourceReference(sourcePath, null, null, null)
                ));
                return null;
            }
            return source.content();
        } catch (RuntimeException exception) {
            diagnostics.add(diagnostic(
                    GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_CANDIDATE_UNREADABLE,
                    "A bootstrap candidate could not be read: " + exception.getClass().getSimpleName() + ".",
                    new GitLabFrontendSourceReference(sourcePath, null, null, null)
            ));
            return null;
        }
    }

    private List<GitLabFrontendBootstrapRoot> resolveRoots(
            GitLabFrontendRepositoryScope scope,
            List<AngularBootstrapSourceParser.ParsedSource> sources,
            GitLabFrontendGraphLimits limits,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        var roots = new LinkedHashMap<String, GitLabFrontendBootstrapRoot>();
        for (var bootstrapSource : sources) {
            for (var bootstrapCall : bootstrapSource.bootstrapCalls()) {
                if (bootstrapCall.kind() == AngularBootstrapSourceParser.CallKind.MODULE_BOOTSTRAP) {
                    resolveModuleRoots(scope, bootstrapSource, bootstrapCall, limits, roots, diagnostics);
                    continue;
                }
                if (bootstrapCall.arguments().size() < 2) {
                    continue;
                }
                var configuration = bootstrapCall.arguments().get(1);
                var resolvedConfiguration = resolveConfiguration(bootstrapSource, configuration, sources);
                if (resolvedConfiguration == null) {
                    continue;
                }
                var routerCalls = resolvedConfiguration.source().routerCallsWithin(
                        resolvedConfiguration.start(),
                        resolvedConfiguration.end()
                );
                if (routerCalls.size() > 1) {
                    diagnostics.add(diagnostic(
                            GitLabFrontendGraphDiagnosticCode.ROUTER_PROVIDER_AMBIGUOUS,
                            "The bootstrap configuration contains more than one provideRouter call.",
                            resolvedConfiguration.reference()
                    ));
                    continue;
                }
                if (routerCalls.size() != 1) {
                    continue;
                }
                var routerCall = routerCalls.get(0);
                var routeCollectionSymbol = routerCall.arguments().isEmpty()
                        ? null
                        : routerCall.arguments().get(0).identifier();
                var identity = bootstrapSource.sourcePath()
                        + "|" + resolvedConfiguration.source().sourcePath()
                        + "|" + valueOrEmpty(resolvedConfiguration.reference().symbol())
                        + "|" + routerCall.source().path()
                        + "|" + valueOrEmpty(routeCollectionSymbol);
                var root = new GitLabFrontendBootstrapRoot(
                        "bootstrap-" + shortHash(scope.projectName() + "|" + identity),
                        "bootstrapApplication",
                        bootstrapCall.source(),
                        resolvedConfiguration.reference(),
                        "provideRouter",
                        routerCall.source(),
                        routeCollectionSymbol
                );
                roots.putIfAbsent(identity, root);
            }
        }
        return List.copyOf(roots.values());
    }

    private void resolveModuleRoots(
            GitLabFrontendRepositoryScope scope,
            AngularBootstrapSourceParser.ParsedSource bootstrapSource,
            AngularBootstrapSourceParser.CallExpression bootstrapCall,
            GitLabFrontendGraphLimits limits,
            LinkedHashMap<String, GitLabFrontendBootstrapRoot> roots,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        var moduleName = bootstrapCall.arguments().isEmpty()
                ? null : bootstrapCall.arguments().get(0).identifier();
        if (moduleName == null) return;
        var session = new GitLabFrontendTargetedSourceSession(gitLabRepositoryPort, scope, limits);
        var imports = new GitLabFrontendTargetedImportResolver(session, bootstrapSource.sourcePath());
        var queue = new ArrayDeque<ModuleTarget>();
        enqueueModule(bootstrapSource, moduleName, 0, imports, queue);
        var visited = new LinkedHashSet<String>();
        while (!queue.isEmpty()) {
            var target = queue.removeFirst();
            if (!session.withinImportDepth(target.depth(), target.sourcePath())
                    || !visited.add(target.sourcePath() + "#" + target.symbol())) continue;
            var source = target.sourcePath().equals(bootstrapSource.sourcePath())
                    ? bootstrapSource
                    : parseModuleSource(session, target.sourcePath());
            if (source == null) continue;
            var module = source.module(target.symbol());
            if (module == null) continue;
            for (var routerCall : source.routerModuleCalls()) {
                if (routerCall.start() < module.metadataStart()
                        || routerCall.end() > module.metadataEnd()) continue;
                var routeSymbol = routerCall.arguments().isEmpty()
                        ? null : routerCall.arguments().get(0).identifier();
                var identity = bootstrapSource.sourcePath() + "|" + target.sourcePath()
                        + "|" + module.name() + "|" + routerCall.start();
                roots.putIfAbsent(identity, new GitLabFrontendBootstrapRoot(
                        "bootstrap-" + shortHash(scope.projectName() + "|" + identity),
                        "bootstrapModule", bootstrapCall.source(), module.source(),
                        "RouterModule.forRoot", routerCall.source(), routeSymbol
                ));
            }
            for (var importedModule : module.imports()) {
                enqueueModule(source, importedModule, target.depth() + 1, imports, queue);
            }
        }
        diagnostics.addAll(session.diagnostics());
    }

    private AngularBootstrapSourceParser.ParsedSource parseModuleSource(
            GitLabFrontendTargetedSourceSession session, String sourcePath
    ) {
        var content = session.readRequired(sourcePath);
        return content != null ? parser.parse(sourcePath, content) : null;
    }

    private void enqueueModule(
            AngularBootstrapSourceParser.ParsedSource owner,
            String moduleName,
            int depth,
            GitLabFrontendTargetedImportResolver imports,
            ArrayDeque<ModuleTarget> queue
    ) {
        if (owner.module(moduleName) != null) {
            queue.add(new ModuleTarget(owner.sourcePath(), moduleName, depth));
            return;
        }
        var binding = owner.imported(moduleName);
        if (binding == null || binding.moduleSpecifier().startsWith("@angular/")
                || !binding.moduleSpecifier().startsWith(".")
                && !binding.moduleSpecifier().contains("/")) return;
        var paths = imports.resolve(owner.sourcePath(), binding.moduleSpecifier());
        if (paths.size() == 1) {
            queue.add(new ModuleTarget(paths.get(0), binding.exportedName(), depth));
        }
    }

    private ResolvedConfiguration resolveConfiguration(
            AngularBootstrapSourceParser.ParsedSource bootstrapSource,
            AngularBootstrapSourceParser.Expression expression,
            List<AngularBootstrapSourceParser.ParsedSource> sources
    ) {
        if (expression.text().trim().startsWith("{")) {
            return new ResolvedConfiguration(
                    bootstrapSource,
                    expression.start(),
                    expression.end(),
                    new GitLabFrontendSourceReference(
                            bootstrapSource.sourcePath(),
                            null,
                            lineNumber(bootstrapSource, expression.start()),
                            lineNumber(bootstrapSource, expression.start())
                    )
            );
        }
        var identifier = expression.identifier();
        if (identifier == null) {
            return null;
        }
        var local = bootstrapSource.constant(identifier);
        if (local != null) {
            return new ResolvedConfiguration(
                    bootstrapSource,
                    local.expressionStart(),
                    local.expressionEnd(),
                    local.source()
            );
        }
        var imported = bootstrapSource.imported(identifier);
        if (imported == null) {
            return null;
        }
        var matches = sources.stream()
                .flatMap(source -> source.constants().stream()
                        .filter(AngularBootstrapSourceParser.ConstDeclaration::exported)
                        .filter(constant -> constant.name().equals(imported.exportedName()))
                        .map(constant -> new ResolvedConfiguration(
                                source,
                                constant.expressionStart(),
                                constant.expressionEnd(),
                                constant.source()
                        )))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private int lineNumber(AngularBootstrapSourceParser.ParsedSource source, int offset) {
        var line = 1;
        for (var index = 0; index < Math.min(offset, source.source().length()); index++) {
            if (source.source().charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private boolean blocksRootConfirmation(GitLabFrontendGraphDiagnostic diagnostic) {
        return diagnostic.code() == GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_CANDIDATE_UNREADABLE
                || diagnostic.code() == GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_CANDIDATE_TRUNCATED;
    }

    private boolean isEligible(String rawPath, List<String> prefixes) {
        var path = normalizePath(rawPath);
        if (!StringUtils.hasText(path) || !path.toLowerCase(Locale.ROOT).endsWith(".ts")) {
            return false;
        }
        var lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".spec.ts")
                || lower.endsWith(".test.ts")
                || lower.endsWith(".stories.ts")
                || lower.endsWith(".story.ts")
                || lower.contains("/.storybook/")
                || lower.startsWith(".storybook/")
                || lower.contains("/fixtures/")
                || lower.contains("/testing/")) {
            return false;
        }
        return prefixes.isEmpty()
                || prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private GitLabFrontendBootstrapDiscoveryResult blocked(
            GitLabFrontendRepositoryScope scope,
            int candidateCount,
            int inspectedSourceCount,
            boolean candidateLimitReached,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        return new GitLabFrontendBootstrapDiscoveryResult(
                scope,
                GitLabFrontendCoverageStatus.BLOCKED,
                null,
                candidateCount,
                inspectedSourceCount,
                candidateLimitReached,
                diagnostics
        );
    }

    private GitLabFrontendGraphDiagnostic diagnostic(
            GitLabFrontendGraphDiagnosticCode code,
            String message,
            GitLabFrontendSourceReference source
    ) {
        return new GitLabFrontendGraphDiagnostic(
                GitLabFrontendDiagnosticSeverity.ERROR,
                code,
                message,
                null,
                null,
                source
        );
    }

    private String shortHash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder();
            for (var index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private record ResolvedConfiguration(
            AngularBootstrapSourceParser.ParsedSource source,
            int start,
            int end,
            GitLabFrontendSourceReference reference
    ) {
    }

    private record ModuleTarget(String sourcePath, String symbol, int depth) {
    }
}
