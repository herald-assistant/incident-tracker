package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabFrontendSourceDiscoveryService {

    private static final int MAX_WORKSPACE_CONFIGURATION_FILES = 20;
    private static final Pattern IMPORT = Pattern.compile(
            "(?s)import\\s*\\{([^}]+)}\\s*from\\s*['\"]([^'\"]+)['\"]"
    );
    private static final Pattern DEFAULT_IMPORT = Pattern.compile(
            "import\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+from\\s*['\"]([^'\"]+)['\"]"
    );
    private static final Pattern TEMPLATE_URL = Pattern.compile("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern STYLE_URL = Pattern.compile("styleUrl\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern STYLE_URLS = Pattern.compile("(?s)styleUrls\\s*:\\s*\\[([^]]*)]");
    private static final Pattern QUOTED_VALUE = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern ROUTE_PARAMETER = Pattern.compile(":([A-Za-z0-9_]+)");
    private static final AngularRouteSourceParser ROUTE_PARSER = new AngularRouteSourceParser();

    private final GitLabRepositoryPort gitLabRepositoryPort;

    public GitLabFrontendRouteCatalog discoverCatalog(GitLabFrontendRouteCatalogRequest request) {
        var diagnostics = new ArrayList<GitLabFrontendDiagnostic>();
        var inventory = inventory(request.scope(), request.limits(), diagnostics);
        var session = new RepositorySession(request.scope(), request.limits(), diagnostics);
        var staticRouteResolver = new TypeScriptStaticRouteResolver(
                inventory.paths(),
                path -> sourceContent(session.read(path))
        );
        var revision = sourceRevision(request.scope(), inventory.paths(), session, diagnostics);
        var workspaceSignals = workspaceSignals(inventory.paths(), session, diagnostics);
        var routeCandidates = routeCandidates(inventory.paths());
        var rootRouteCandidates = rootRouteCandidates(routeCandidates);

        if (routeCandidates.isEmpty()) {
            diagnostics.add(diagnostic(
                    GitLabFrontendDiagnosticSeverity.ERROR,
                    "ANGULAR_ROUTE_SOURCE_NOT_FOUND",
                    "No statically recognizable Angular route source was found within the repository scope.",
                    null
            ));
        }
        if (rootRouteCandidates.size() > request.limits().maxRouteFiles()) {
            diagnostics.add(limitDiagnostic(
                    "ROUTE_FILE_LIMIT_REACHED",
                    "Route source candidates exceeded maxRouteFiles=" + request.limits().maxRouteFiles() + "."
            ));
            rootRouteCandidates = rootRouteCandidates.subList(0, request.limits().maxRouteFiles());
        }

        var discovered = new ArrayList<GitLabFrontendRouteEntry>();
        var work = new ArrayDeque<RouteWork>();
        rootRouteCandidates.forEach(path -> work.add(new RouteWork(path, "", false, 0, List.of())));
        var visited = new LinkedHashSet<String>();
        var scannedRouteFiles = new LinkedHashSet<String>();

        while (!work.isEmpty() && scannedRouteFiles.size() < request.limits().maxRouteFiles()) {
            var current = work.removeFirst();
            var visitKey = current.sourcePath() + "|" + current.parentRoute();
            if (!visited.add(visitKey)) {
                continue;
            }
            if (current.depth() > request.limits().maxTraversalDepth()) {
                diagnostics.add(limitDiagnostic(
                        "ROUTE_TRAVERSAL_DEPTH_REACHED",
                        "Lazy route traversal exceeded maxTraversalDepth="
                                + request.limits().maxTraversalDepth() + "."
                ));
                continue;
            }
            var content = session.read(current.sourcePath());
            if (content == null) {
                continue;
            }
            scannedRouteFiles.add(current.sourcePath());
            var parseResult = ROUTE_PARSER.parse(
                    current.sourcePath(),
                    content.content(),
                    staticRouteResolver
            );
            parseResult.limitations().forEach(message -> diagnostics.add(diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    "ANGULAR_ROUTE_DEFINITION_PARTIAL",
                    message,
                    current.sourcePath()
            )));
            var imports = imports(content.content());

            for (var parsed : parseResult.routes()) {
                if (discovered.size() >= request.limits().maxRouteEntries()) {
                    break;
                }
                var fullRoute = rebaseRoute(current.parentRoute(), parsed.fullPath());
                var parentRoute = rebaseRoute(current.parentRoute(), parsed.parentPath());
                var effectiveGuards = new LinkedHashSet<>(current.inheritedGuards());
                effectiveGuards.addAll(parsed.guards());
                var effectiveLazy = current.lazy() || parsed.lazy();
                var viewResolution = resolveView(
                        parsed,
                        current.sourcePath(),
                        imports,
                        staticRouteResolver
                );
                var limitations = new ArrayList<String>();
                if (viewResolution.ambiguous()) {
                    limitations.add("The view source matched more than one repository file.");
                }
                if (parsed.redirectTo() != null) {
                    discovered.add(routeEntry(
                            request.scope(),
                            parsed,
                            fullRoute,
                            parentRoute,
                            GitLabFrontendRouteEntryKind.REDIRECT,
                            GitLabFrontendDiscoveryStatus.RESOLVED,
                            null,
                            null,
                            effectiveLazy,
                            List.copyOf(effectiveGuards),
                            limitations
                    ));
                } else if (viewResolution.path() != null) {
                    discovered.add(routeEntry(
                            request.scope(),
                            parsed,
                            fullRoute,
                            parentRoute,
                            GitLabFrontendRouteEntryKind.SCREEN,
                            viewResolution.ambiguous()
                                    ? GitLabFrontendDiscoveryStatus.AMBIGUOUS
                                    : GitLabFrontendDiscoveryStatus.RESOLVED,
                            viewResolution.symbol(),
                            viewResolution.path(),
                            effectiveLazy,
                            List.copyOf(effectiveGuards),
                            limitations
                    ));
                } else if (parsed.componentSymbol() != null || parsed.loadComponentDeclared()) {
                    limitations.add("The route view source could not be resolved within the bounded inventory.");
                    discovered.add(routeEntry(
                            request.scope(),
                            parsed,
                            fullRoute,
                            parentRoute,
                            GitLabFrontendRouteEntryKind.UNRESOLVED,
                            GitLabFrontendDiscoveryStatus.PARTIAL,
                            parsed.componentSymbol() != null
                                    ? parsed.componentSymbol()
                                    : parsed.loadComponentSymbol(),
                            null,
                            effectiveLazy,
                            List.copyOf(effectiveGuards),
                            limitations
                    ));
                }

                if (parsed.loadChildrenDeclared()) {
                    var lazyTargets = parsed.loadChildrenImportPath() != null
                            ? staticRouteResolver.resolveImportPaths(
                                    current.sourcePath(),
                                    parsed.loadChildrenImportPath()
                            )
                            : List.<String>of();
                    if (lazyTargets.size() > 1) {
                        diagnostics.add(diagnostic(
                                GitLabFrontendDiagnosticSeverity.WARNING,
                                "TYPESCRIPT_IMPORT_ALIAS_AMBIGUOUS",
                                "A loadChildren import matched more than one bounded repository source.",
                                current.sourcePath()
                        ));
                    }
                    var lazyTarget = lazyTargets.size() == 1 ? lazyTargets.get(0) : null;
                    var lazyRouteSource = resolveLazyRouteSource(
                            lazyTarget,
                            inventory.paths(),
                            session,
                            staticRouteResolver
                    );
                    if (lazyRouteSource != null) {
                        work.addLast(new RouteWork(
                                lazyRouteSource,
                                fullRoute,
                                true,
                                current.depth() + 1,
                                List.copyOf(effectiveGuards)
                        ));
                    } else {
                        var lazyLimitations = List.of(
                                "The lazy route target is dynamic or could not be resolved within the bounded inventory."
                        );
                        discovered.add(routeEntry(
                                request.scope(),
                                parsed,
                                fullRoute,
                                parentRoute,
                                GitLabFrontendRouteEntryKind.UNRESOLVED,
                                GitLabFrontendDiscoveryStatus.UNSUPPORTED,
                                parsed.loadChildrenSymbol(),
                                null,
                                true,
                                List.copyOf(effectiveGuards),
                                lazyLimitations
                        ));
                        diagnostics.add(diagnostic(
                                GitLabFrontendDiagnosticSeverity.WARNING,
                                "LAZY_ROUTE_SOURCE_UNRESOLVED",
                                "A loadChildren target could not be mapped to a static route source.",
                                current.sourcePath()
                        ));
                    }
                }
            }
        }

        diagnostics.add(diagnostic(
                GitLabFrontendDiagnosticSeverity.INFO,
                "HEURISTIC_TYPESCRIPT_DISCOVERY",
                "Angular route relationships were recognized statically without executing or compiling TypeScript.",
                null
        ));
        var routeCatalogTruncated = discovered.size() >= request.limits().maxRouteEntries()
                || scannedRouteFiles.size() >= request.limits().maxRouteFiles() && !work.isEmpty();
        if (routeCatalogTruncated) {
            diagnostics.add(limitDiagnostic(
                    "ROUTE_CATALOG_LIMIT_REACHED",
                    "The route catalog reached an explicit route entry or route file limit."
            ));
        }

        var entries = distinctEntries(discovered).stream()
                .sorted(Comparator.comparing(GitLabFrontendRouteEntry::routePattern)
                        .thenComparing(entry -> entry.kind().name())
                        .thenComparing(entry -> valueOrEmpty(entry.viewSourcePath())))
                .toList();
        return new GitLabFrontendRouteCatalog(
                request.scope(),
                revision,
                workspaceSignals,
                entries,
                sortedDiagnostics(diagnostics),
                inventory.totalCount(),
                scannedRouteFiles.size(),
                inventory.truncated(),
                routeCatalogTruncated
        );
    }

    public GitLabFrontendScreenSourceContext buildScreenContext(GitLabFrontendScreenContextRequest request) {
        var catalog = discoverCatalog(new GitLabFrontendRouteCatalogRequest(request.scope(), request.limits()));
        validateExpectedRevision(request.expectedCommitId(), catalog.sourceRevision());
        var screen = catalog.entries().stream()
                .filter(entry -> entry.kind() == GitLabFrontendRouteEntryKind.SCREEN)
                .filter(entry -> request.screenId().equals(entry.screenId()))
                .findFirst()
                .orElseThrow(() -> new GitLabFrontendDiscoveryException(
                        "FRONTEND_SCREEN_NOT_FOUND",
                        "screenId does not belong to the current repository/ref catalog"
                ));
        if (!StringUtils.hasText(screen.viewSourcePath())) {
            throw new GitLabFrontendDiscoveryException(
                    "FRONTEND_SCREEN_SOURCE_UNRESOLVED",
                    "The selected screen has no resolved view source"
            );
        }

        var diagnostics = new ArrayList<>(catalog.diagnostics());
        var inventory = inventory(request.scope(), request.limits(), diagnostics);
        var session = new RepositorySession(request.scope(), request.limits(), diagnostics);
        var staticRouteResolver = new TypeScriptStaticRouteResolver(
                inventory.paths(),
                path -> sourceContent(session.read(path))
        );
        var files = new LinkedHashMap<String, SourceAccumulator>();
        addSourceFile(screen.routeSource().path(), GitLabFrontendSourceRole.ROUTE_CONFIGURATION, files, session);
        addSourceFile(screen.viewSourcePath(), GitLabFrontendSourceRole.VIEW_COMPONENT, files, session);

        var component = files.get(screen.viewSourcePath());
        if (component != null) {
            collectTemplateAndStyles(component, inventory.paths(), files, session, diagnostics);
            collectRelatedSources(
                    screen.viewSourcePath(),
                    inventory.paths(),
                    files,
                    session,
                    request.limits(),
                    diagnostics,
                    staticRouteResolver
            );
        }

        var signals = technicalSignals(screen, files);
        applySignalRoles(signals, files);
        var bounded = boundedSourceFiles(files, request.limits(), diagnostics);
        var coverage = coverage(screen, bounded.files(), signals);
        return new GitLabFrontendScreenSourceContext(
                request.scope(),
                catalog.sourceRevision(),
                screen,
                catalog.workspaceSignals(),
                bounded.files(),
                signals,
                coverage,
                sortedDiagnostics(diagnostics),
                catalog.repositoryFileCount(),
                catalog.scannedRouteFileCount(),
                catalog.inventoryTruncated(),
                catalog.routeCatalogTruncated(),
                bounded.totalCharacters(),
                bounded.truncated() || catalog.inventoryTruncated() || catalog.routeCatalogTruncated()
        );
    }

    private void validateExpectedRevision(
            String expectedCommitId,
            GitLabFrontendSourceRevision sourceRevision
    ) {
        if (!StringUtils.hasText(expectedCommitId)) {
            return;
        }
        if (sourceRevision == null || !StringUtils.hasText(sourceRevision.commitId())) {
            throw new GitLabFrontendDiscoveryException(
                    "FRONTEND_SOURCE_REVISION_UNRESOLVED",
                    "The exact source revision could not be resolved before building screen context"
            );
        }
        if (!expectedCommitId.equals(sourceRevision.commitId())) {
            throw new GitLabFrontendDiscoveryException(
                    "FRONTEND_SOURCE_REVISION_CHANGED",
                    "The repository ref no longer resolves to the expected source revision"
            );
        }
    }

    private Inventory inventory(
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendDiscoveryLimits limits,
            List<GitLabFrontendDiagnostic> diagnostics
    ) {
        if (!gitLabRepositoryPort.branchExists(scope.group(), scope.projectName(), scope.ref())) {
            throw new GitLabFrontendDiscoveryException(
                    "FRONTEND_REF_NOT_FOUND",
                    "The requested GitLab branch/ref does not exist"
            );
        }
        var paths = new LinkedHashSet<String>();
        var prefixes = scope.pathPrefixes().isEmpty()
                ? java.util.Collections.<String>singletonList(null)
                : scope.pathPrefixes();
        for (var prefix : prefixes) {
            var files = gitLabRepositoryPort.listRepositoryFiles(
                    scope.group(),
                    scope.projectName(),
                    scope.ref(),
                    prefix
            );
            if (files == null) {
                continue;
            }
            files.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(file -> normalizePath(file.filePath()))
                    .filter(StringUtils::hasText)
                    .filter(path -> withinScope(path, scope.pathPrefixes()))
                    .forEach(paths::add);
        }
        var sorted = paths.stream().sorted().toList();
        var total = sorted.size();
        var truncated = total > limits.maxInventoryFiles();
        if (truncated) {
            diagnostics.add(limitDiagnostic(
                    "REPOSITORY_INVENTORY_LIMIT_REACHED",
                    "Repository inventory exceeded maxInventoryFiles=" + limits.maxInventoryFiles() + "."
            ));
            sorted = sorted.subList(0, limits.maxInventoryFiles());
        }
        return new Inventory(sorted, total, truncated);
    }

    private GitLabFrontendSourceRevision sourceRevision(
            GitLabFrontendRepositoryScope scope,
            List<String> inventory,
            RepositorySession session,
            List<GitLabFrontendDiagnostic> diagnostics
    ) {
        var anchor = inventory.stream().filter(path -> path.endsWith("package.json")).findFirst()
                .or(() -> routeCandidates(inventory).stream().findFirst())
                .orElse(null);
        if (anchor != null) {
            try {
                var metadata = gitLabRepositoryPort.readFileMetadata(
                        scope.group(), scope.projectName(), scope.ref(), anchor
                );
                if (metadata != null && StringUtils.hasText(metadata.commitId())) {
                    return new GitLabFrontendSourceRevision(scope.ref(), metadata.commitId().trim());
                }
            } catch (RuntimeException exception) {
                diagnostics.add(diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        "SOURCE_REVISION_LOOKUP_FAILED",
                        "GitLab source revision metadata could not be read: "
                                + exception.getClass().getSimpleName(),
                        anchor
                ));
            }
        }
        diagnostics.add(diagnostic(
                GitLabFrontendDiagnosticSeverity.WARNING,
                "SOURCE_REVISION_UNRESOLVED",
                "The branch/ref is known, but its commit revision could not be resolved from file metadata.",
                anchor
        ));
        return new GitLabFrontendSourceRevision(scope.ref(), null);
    }

    private List<GitLabFrontendWorkspaceSignal> workspaceSignals(
            List<String> inventory,
            RepositorySession session,
            List<GitLabFrontendDiagnostic> diagnostics
    ) {
        var signals = new LinkedHashSet<GitLabFrontendWorkspaceSignal>();
        var configurationPaths = inventory.stream()
                .filter(path -> List.of("angular.json", "nx.json", "project.json", "package.json")
                        .contains(fileName(path)))
                .toList();
        if (configurationPaths.size() > MAX_WORKSPACE_CONFIGURATION_FILES) {
            diagnostics.add(limitDiagnostic(
                    "WORKSPACE_CONFIGURATION_LIMIT_REACHED",
                    "Workspace signal discovery inspected at most "
                            + MAX_WORKSPACE_CONFIGURATION_FILES + " configuration files."
            ));
            configurationPaths = configurationPaths.subList(0, MAX_WORKSPACE_CONFIGURATION_FILES);
        }
        for (var path : configurationPaths) {
            var fileName = fileName(path);
            if ("angular.json".equals(fileName)) {
                signals.add(new GitLabFrontendWorkspaceSignal("WORKSPACE", "ANGULAR_CLI", path));
            } else if ("nx.json".equals(fileName)) {
                signals.add(new GitLabFrontendWorkspaceSignal("WORKSPACE", "NX", path));
            } else if ("project.json".equals(fileName)) {
                signals.add(new GitLabFrontendWorkspaceSignal("PROJECT_CONFIGURATION", "NX_PROJECT", path));
            } else if ("package.json".equals(fileName)) {
                var content = session.read(path);
                if (content != null) {
                    addPackageSignal(signals, content.content(), path, "@angular/core", "FRAMEWORK", "ANGULAR");
                    addPackageSignal(signals, content.content(), path, "@angular/material", "LIBRARY", "ANGULAR_MATERIAL");
                    addPackageSignal(signals, content.content(), path, "@ngrx/store", "STATE", "NGRX");
                    addPackageSignal(signals, content.content(), path, "rxjs", "STREAMS", "RXJS");
                    addPackageSignal(signals, content.content(), path, "keycloak", "AUTHENTICATION", "KEYCLOAK");
                    addPackageSignal(signals, content.content(), path, "nx", "WORKSPACE", "NX");
                }
            }
        }
        return signals.stream()
                .sorted(Comparator.comparing(GitLabFrontendWorkspaceSignal::kind)
                        .thenComparing(GitLabFrontendWorkspaceSignal::value)
                        .thenComparing(GitLabFrontendWorkspaceSignal::sourcePath))
                .toList();
    }

    private void addPackageSignal(
            Set<GitLabFrontendWorkspaceSignal> signals,
            String source,
            String path,
            String token,
            String kind,
            String value
    ) {
        if (source.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
            signals.add(new GitLabFrontendWorkspaceSignal(kind, value, path));
        }
    }

    private List<String> routeCandidates(List<String> paths) {
        return paths.stream()
                .filter(path -> path.endsWith(".ts"))
                .filter(path -> {
                    var name = fileName(path);
                    return name.equals("app.routes.ts")
                            || name.equals("app-routing.module.ts")
                            || name.endsWith(".routes.ts")
                            || name.endsWith("-routing.module.ts")
                            || name.equals("routes.ts");
                })
                .sorted()
                .toList();
    }

    private List<String> rootRouteCandidates(List<String> candidates) {
        var roots = candidates.stream()
                .filter(path -> {
                    var name = fileName(path);
                    return name.equals("app.routes.ts") || name.equals("app-routing.module.ts");
                })
                .toList();
        return roots.isEmpty() ? candidates : roots;
    }

    private GitLabFrontendRouteEntry routeEntry(
            GitLabFrontendRepositoryScope scope,
            AngularRouteSourceParser.ParsedRoute route,
            String fullRoute,
            String parentRoute,
            GitLabFrontendRouteEntryKind kind,
            GitLabFrontendDiscoveryStatus status,
            String viewSymbol,
            String viewPath,
            boolean lazy,
            List<String> guards,
            List<String> limitations
    ) {
        var identity = kind + "|" + fullRoute + "|" + valueOrEmpty(viewPath)
                + "|" + valueOrEmpty(route.redirectTo());
        return new GitLabFrontendRouteEntry(
                (kind == GitLabFrontendRouteEntryKind.SCREEN ? "screen-" : "route-")
                        + shortHash(scope.projectName() + "|" + identity),
                label(fullRoute, viewSymbol, kind),
                fullRoute,
                parentRoute,
                kind,
                status,
                lazy,
                guards,
                routeParameters(fullRoute),
                route.redirectTo(),
                viewSymbol,
                viewPath,
                new GitLabFrontendSourceReference(
                        route.sourcePath(),
                        viewSymbol,
                        route.sourceLine(),
                        route.sourceLine()
                ),
                limitations
        );
    }

    private ViewResolution resolveView(
            AngularRouteSourceParser.ParsedRoute route,
            String routeFile,
            Map<String, String> imports,
            TypeScriptStaticRouteResolver staticRouteResolver
    ) {
        var symbol = route.loadComponentSymbol() != null
                ? route.loadComponentSymbol()
                : route.componentSymbol();
        var importPath = route.loadComponentImportPath();
        if (importPath == null && symbol != null) {
            importPath = imports.get(symbol);
        }
        var matches = staticRouteResolver.resolveImportPaths(routeFile, importPath);
        return new ViewResolution(symbol, matches.isEmpty() ? null : matches.get(0), matches.size() > 1);
    }

    private String resolveLazyRouteSource(
            String lazyTarget,
            List<String> inventory,
            RepositorySession session,
            TypeScriptStaticRouteResolver staticRouteResolver
    ) {
        if (!StringUtils.hasText(lazyTarget)) {
            return null;
        }
        if (lazyTarget.endsWith(".routes.ts") || lazyTarget.endsWith("-routing.module.ts")
                || fileName(lazyTarget).equals("routes.ts")) {
            return lazyTarget;
        }
        var module = session.read(lazyTarget);
        if (module != null) {
            var moduleImports = imports(module.content());
            var routing = moduleImports.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("RoutingModule"))
                    .flatMap(entry -> staticRouteResolver
                            .resolveImportPaths(lazyTarget, entry.getValue()).stream())
                    .findFirst()
                    .orElse(null);
            if (routing != null) {
                return routing;
            }
        }
        var directory = parentPath(lazyTarget);
        return inventory.stream()
                .filter(path -> parentPath(path).equals(directory))
                .filter(path -> path.endsWith("-routing.module.ts") || path.endsWith(".routes.ts"))
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> imports(String source) {
        var result = new LinkedHashMap<String, String>();
        var matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            for (var imported : matcher.group(1).split(",")) {
                var normalized = imported.trim();
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                var aliasParts = normalized.split("\\s+as\\s+");
                result.put(aliasParts[aliasParts.length - 1].trim(), matcher.group(2));
            }
        }
        var defaultMatcher = DEFAULT_IMPORT.matcher(source);
        while (defaultMatcher.find()) {
            result.putIfAbsent(defaultMatcher.group(1), defaultMatcher.group(2));
        }
        return result;
    }

    private void collectTemplateAndStyles(
            SourceAccumulator component,
            List<String> inventory,
            Map<String, SourceAccumulator> files,
            RepositorySession session,
            List<GitLabFrontendDiagnostic> diagnostics
    ) {
        var source = component.content();
        var templateMatcher = TEMPLATE_URL.matcher(source);
        if (templateMatcher.find()) {
            var templatePath = resolveAssetPath(component.path(), templateMatcher.group(1), inventory);
            if (templatePath != null) {
                addSourceFile(templatePath, GitLabFrontendSourceRole.TEMPLATE, files, session);
            } else {
                diagnostics.add(diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        "EXTERNAL_TEMPLATE_UNRESOLVED",
                        "The component templateUrl could not be resolved within the repository scope.",
                        component.path()
                ));
            }
        } else if (Pattern.compile("\\btemplate\\s*:").matcher(source).find()) {
            component.roles().add(GitLabFrontendSourceRole.INLINE_TEMPLATE);
        } else {
            diagnostics.add(diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    "COMPONENT_TEMPLATE_NOT_DETECTED",
                    "No static inline template or templateUrl was detected for the selected view.",
                    component.path()
            ));
        }

        var styleMatcher = STYLE_URL.matcher(source);
        while (styleMatcher.find()) {
            addResolvedStyle(component.path(), styleMatcher.group(1), inventory, files, session, diagnostics);
        }
        var stylesMatcher = STYLE_URLS.matcher(source);
        while (stylesMatcher.find()) {
            var valueMatcher = QUOTED_VALUE.matcher(stylesMatcher.group(1));
            while (valueMatcher.find()) {
                addResolvedStyle(component.path(), valueMatcher.group(1), inventory, files, session, diagnostics);
            }
        }
        if (Pattern.compile("\\bstyles\\s*:").matcher(source).find()) {
            component.roles().add(GitLabFrontendSourceRole.INLINE_STYLE);
        }
    }

    private void addResolvedStyle(
            String componentPath,
            String styleExpression,
            List<String> inventory,
            Map<String, SourceAccumulator> files,
            RepositorySession session,
            List<GitLabFrontendDiagnostic> diagnostics
    ) {
        var stylePath = resolveAssetPath(componentPath, styleExpression, inventory);
        if (stylePath != null) {
            addSourceFile(stylePath, GitLabFrontendSourceRole.STYLE, files, session);
        } else {
            diagnostics.add(diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    "EXTERNAL_STYLE_UNRESOLVED",
                    "A component style reference could not be resolved within the repository scope.",
                    componentPath
            ));
        }
    }

    private String resolveAssetPath(String sourcePath, String assetPath, List<String> inventory) {
        if (!StringUtils.hasText(assetPath) || !assetPath.startsWith(".")) {
            return null;
        }
        var resolved = normalizeRelativePath(parentPath(sourcePath), assetPath);
        return inventory.contains(resolved) ? resolved : null;
    }

    private void collectRelatedSources(
            String rootPath,
            List<String> inventory,
            Map<String, SourceAccumulator> files,
            RepositorySession session,
            GitLabFrontendDiscoveryLimits limits,
            List<GitLabFrontendDiagnostic> diagnostics,
            TypeScriptStaticRouteResolver staticRouteResolver
    ) {
        var queue = new ArrayDeque<SourceWork>();
        queue.add(new SourceWork(rootPath, 0));
        var visited = new LinkedHashSet<String>();
        while (!queue.isEmpty() && files.size() < limits.maxContextFiles()) {
            var current = queue.removeFirst();
            if (!visited.add(current.path())) {
                continue;
            }
            var source = files.containsKey(current.path())
                    ? files.get(current.path())
                    : addSourceFile(current.path(), GitLabFrontendSourceRole.RELATED_SOURCE, files, session);
            if (source == null) {
                continue;
            }
            if (current.depth() >= limits.maxTraversalDepth()) {
                if (imports(source.content()).values().stream().anyMatch(value -> value.startsWith("."))) {
                    diagnostics.add(limitDiagnostic(
                            "SOURCE_TRAVERSAL_DEPTH_REACHED",
                            "Related source traversal reached maxTraversalDepth=" + limits.maxTraversalDepth() + "."
                    ));
                }
                continue;
            }
            for (var importPath : imports(source.content()).values()) {
                for (var resolved : staticRouteResolver.resolveImportPaths(current.path(), importPath)) {
                    if (files.size() >= limits.maxContextFiles()) {
                        diagnostics.add(limitDiagnostic(
                                "SOURCE_FILE_LIMIT_REACHED",
                                "Screen context reached maxContextFiles=" + limits.maxContextFiles() + "."
                        ));
                        break;
                    }
                    if (resolved.endsWith(".ts") && !resolved.endsWith(".spec.ts")) {
                        var role = resolved.endsWith(".component.ts")
                                ? GitLabFrontendSourceRole.CHILD_COMPONENT
                                : GitLabFrontendSourceRole.RELATED_SOURCE;
                        addSourceFile(resolved, role, files, session);
                        queue.addLast(new SourceWork(resolved, current.depth() + 1));
                    }
                }
            }
        }
        if (!queue.isEmpty()) {
            diagnostics.add(limitDiagnostic(
                    "SOURCE_FILE_LIMIT_REACHED",
                    "Screen context reached maxContextFiles=" + limits.maxContextFiles() + "."
            ));
        }
    }

    private SourceAccumulator addSourceFile(
            String path,
            GitLabFrontendSourceRole role,
            Map<String, SourceAccumulator> files,
            RepositorySession session
    ) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        var existing = files.get(path);
        if (existing != null) {
            existing.roles().add(role);
            return existing;
        }
        var content = session.read(path);
        if (content == null) {
            return null;
        }
        var accumulator = new SourceAccumulator(
                path,
                EnumSet.of(role),
                content.content(),
                content.truncated()
        );
        files.put(path, accumulator);
        return accumulator;
    }

    private List<GitLabFrontendTechnicalSignal> technicalSignals(
            GitLabFrontendRouteEntry screen,
            Map<String, SourceAccumulator> files
    ) {
        var signals = new ArrayList<GitLabFrontendTechnicalSignal>();
        for (var guard : screen.guards()) {
            signals.add(new GitLabFrontendTechnicalSignal(
                    GitLabFrontendTechnicalSignalKind.AUTH_GUARD,
                    "Route guard " + guard + " is attached to the selected route.",
                    GitLabFrontendSignalConfidence.HIGH,
                    screen.routeSource()
            ));
        }
        files.values().forEach(file -> detectSignals(file, signals));
        return signals.stream()
                .collect(java.util.stream.Collectors.toMap(
                        signal -> signal.kind() + "|" + signal.source().path(),
                        signal -> signal,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing((GitLabFrontendTechnicalSignal signal) -> signal.kind().name())
                        .thenComparing(signal -> signal.source().path()))
                .toList();
    }

    private void detectSignals(SourceAccumulator file, List<GitLabFrontendTechnicalSignal> signals) {
        var source = file.content();
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                List.of("FormGroup", "FormControl", "FormBuilder", "formControlName"),
                "Reactive Forms declaration or binding is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.CUSTOM_FORM_CONTROL,
                List.of("ControlValueAccessor", "NG_VALUE_ACCESSOR"),
                "A custom Angular form control contract is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.DYNAMIC_FORM_DEFINITION,
                List.of("formDefinition", "fieldConfig", "DynamicForm", "FormSchema"),
                "A custom or runtime form-definition signal is present; concrete runtime fields are not inferred.",
                GitLabFrontendSignalConfidence.MEDIUM);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.NGRX_STORE,
                List.of("Store<", "store.dispatch", "store.select"),
                "NgRx store interaction is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.NGRX_ACTION,
                List.of("createAction", ".dispatch("),
                "NgRx action declaration or dispatch is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.NGRX_SELECTOR,
                List.of("createSelector", ".select("),
                "NgRx selector declaration or selection is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.NGRX_EFFECT,
                List.of("createEffect"), "NgRx effect declaration is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.NGRX_REDUCER,
                List.of("createReducer"), "NgRx reducer declaration is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.HTTP_CLIENT,
                List.of("HttpClient"), "Direct Angular HttpClient usage is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.REST_CLIENT,
                List.of("ApiClient", "RestClient", "GeneratedClient", "ContactApi"),
                "A generated or organizational REST client signal is present.", GitLabFrontendSignalConfidence.MEDIUM);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.WEBSOCKET,
                List.of("WebSocketSubject", "webSocket(", "webSocket<", "WebSocket("),
                "A WebSocket source is present.", GitLabFrontendSignalConfidence.HIGH);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.RXJS_STREAM,
                List.of("Observable<", "Subject<", "combineLatest", "switchMap("),
                "An RxJS stream or transformation is present.", GitLabFrontendSignalConfidence.MEDIUM);
        detect(file, source, signals, GitLabFrontendTechnicalSignalKind.ROLE_OR_PERMISSION_CHECK,
                List.of("hasRole", "hasPermission", "isUserInRole", "Keycloak", "permission"),
                "A client-side role, permission or authentication check is present; this is not proof of backend authorization.",
                GitLabFrontendSignalConfidence.MEDIUM);
    }

    private void detect(
            SourceAccumulator file,
            String source,
            List<GitLabFrontendTechnicalSignal> signals,
            GitLabFrontendTechnicalSignalKind kind,
            List<String> tokens,
            String description,
            GitLabFrontendSignalConfidence confidence
    ) {
        var match = tokens.stream().filter(source::contains).findFirst().orElse(null);
        if (match == null) {
            return;
        }
        var index = source.indexOf(match);
        var line = lineNumber(source, index);
        signals.add(new GitLabFrontendTechnicalSignal(
                kind,
                description,
                confidence,
                new GitLabFrontendSourceReference(file.path(), match, line, line)
        ));
    }

    private void applySignalRoles(
            List<GitLabFrontendTechnicalSignal> signals,
            Map<String, SourceAccumulator> files
    ) {
        for (var signal : signals) {
            var file = files.get(signal.source().path());
            if (file == null) {
                continue;
            }
            switch (signal.kind()) {
                case REACTIVE_FORM, CUSTOM_FORM_CONTROL, DYNAMIC_FORM_DEFINITION ->
                        file.roles().add(GitLabFrontendSourceRole.FORM_LOGIC);
                case NGRX_STORE, NGRX_ACTION, NGRX_SELECTOR, NGRX_EFFECT, NGRX_REDUCER ->
                        file.roles().add(GitLabFrontendSourceRole.STATE_MANAGEMENT);
                case REST_CLIENT, HTTP_CLIENT -> file.roles().add(GitLabFrontendSourceRole.BACKEND_CLIENT);
                case WEBSOCKET, RXJS_STREAM -> file.roles().add(GitLabFrontendSourceRole.WEBSOCKET_STREAM);
                case AUTH_GUARD, ROLE_OR_PERMISSION_CHECK -> file.roles().add(GitLabFrontendSourceRole.AUTHORIZATION);
            }
        }
    }

    private BoundedFiles boundedSourceFiles(
            Map<String, SourceAccumulator> sources,
            GitLabFrontendDiscoveryLimits limits,
            List<GitLabFrontendDiagnostic> diagnostics
    ) {
        var files = new ArrayList<GitLabFrontendSourceFile>();
        var total = 0;
        var truncated = false;
        for (var source : sources.values()) {
            if (files.size() >= limits.maxContextFiles() || total >= limits.maxTotalCharacters()) {
                truncated = true;
                break;
            }
            var remaining = limits.maxTotalCharacters() - total;
            var content = source.content();
            var limited = content.length() > remaining ? content.substring(0, remaining) : content;
            var fileTruncated = source.truncated() || limited.length() != content.length();
            files.add(new GitLabFrontendSourceFile(
                    source.path(),
                    source.roles().stream().sorted(Comparator.comparing(Enum::name)).toList(),
                    limited,
                    limited.length(),
                    fileTruncated
            ));
            total += limited.length();
            truncated |= fileTruncated;
        }
        if (truncated) {
            diagnostics.add(limitDiagnostic(
                    "SCREEN_CONTEXT_CONTENT_LIMIT_REACHED",
                    "Screen context reached an explicit file or character limit."
            ));
        }
        return new BoundedFiles(files, total, truncated);
    }

    private List<GitLabFrontendContextCoverage> coverage(
            GitLabFrontendRouteEntry screen,
            List<GitLabFrontendSourceFile> files,
            List<GitLabFrontendTechnicalSignal> signals
    ) {
        var roles = files.stream().flatMap(file -> file.roles().stream()).collect(java.util.stream.Collectors.toSet());
        return List.of(
                new GitLabFrontendContextCoverage(
                        "ROUTING",
                        screen.routeSource() != null ? GitLabFrontendCoverageStatus.READY : GitLabFrontendCoverageStatus.BLOCKED,
                        screen.routeSource() != null ? "Route source and entry were resolved." : "Route source is unavailable."
                ),
                new GitLabFrontendContextCoverage(
                        "VIEW",
                        roles.contains(GitLabFrontendSourceRole.VIEW_COMPONENT)
                                ? GitLabFrontendCoverageStatus.READY : GitLabFrontendCoverageStatus.BLOCKED,
                        roles.contains(GitLabFrontendSourceRole.VIEW_COMPONENT)
                                ? "View component source was collected." : "View component source is unavailable."
                ),
                optionalCoverage("TEMPLATE", roles.contains(GitLabFrontendSourceRole.TEMPLATE)
                        || roles.contains(GitLabFrontendSourceRole.INLINE_TEMPLATE)),
                optionalCoverage("FORMS", hasSignal(signals,
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        GitLabFrontendTechnicalSignalKind.CUSTOM_FORM_CONTROL,
                        GitLabFrontendTechnicalSignalKind.DYNAMIC_FORM_DEFINITION)),
                optionalCoverage("STATE", hasSignal(signals,
                        GitLabFrontendTechnicalSignalKind.NGRX_STORE,
                        GitLabFrontendTechnicalSignalKind.NGRX_ACTION,
                        GitLabFrontendTechnicalSignalKind.NGRX_SELECTOR,
                        GitLabFrontendTechnicalSignalKind.NGRX_EFFECT,
                        GitLabFrontendTechnicalSignalKind.NGRX_REDUCER)),
                optionalCoverage("BACKEND_SERVICES", hasSignal(signals,
                        GitLabFrontendTechnicalSignalKind.REST_CLIENT,
                        GitLabFrontendTechnicalSignalKind.HTTP_CLIENT,
                        GitLabFrontendTechnicalSignalKind.WEBSOCKET)),
                optionalCoverage("AUTHORIZATION", hasSignal(signals,
                        GitLabFrontendTechnicalSignalKind.AUTH_GUARD,
                        GitLabFrontendTechnicalSignalKind.ROLE_OR_PERMISSION_CHECK))
        );
    }

    private GitLabFrontendContextCoverage optionalCoverage(String category, boolean detected) {
        return new GitLabFrontendContextCoverage(
                category,
                detected ? GitLabFrontendCoverageStatus.READY : GitLabFrontendCoverageStatus.PARTIAL,
                detected
                        ? "A static source signal was collected."
                        : "No static signal was detected; absence of runtime behavior is not proven."
        );
    }

    private boolean hasSignal(
            List<GitLabFrontendTechnicalSignal> signals,
            GitLabFrontendTechnicalSignalKind... kinds
    ) {
        var expected = Set.of(kinds);
        return signals.stream().map(GitLabFrontendTechnicalSignal::kind).anyMatch(expected::contains);
    }

    private List<GitLabFrontendRouteEntry> distinctEntries(List<GitLabFrontendRouteEntry> entries) {
        return entries.stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.kind() + "|" + entry.routePattern() + "|" + valueOrEmpty(entry.viewSourcePath())
                        + "|" + valueOrEmpty(entry.redirectTarget()),
                entry -> entry,
                (left, right) -> left,
                LinkedHashMap::new
        )).values().stream().toList();
    }

    private List<GitLabFrontendDiagnostic> sortedDiagnostics(List<GitLabFrontendDiagnostic> diagnostics) {
        return diagnostics.stream().distinct()
                .sorted(Comparator.comparing((GitLabFrontendDiagnostic value) -> value.severity().ordinal()).reversed()
                        .thenComparing(GitLabFrontendDiagnostic::code)
                        .thenComparing(value -> valueOrEmpty(value.sourcePath())))
                .toList();
    }

    private GitLabFrontendDiagnostic diagnostic(
            GitLabFrontendDiagnosticSeverity severity,
            String code,
            String message,
            String sourcePath
    ) {
        return new GitLabFrontendDiagnostic(severity, code, message, sourcePath);
    }

    private GitLabFrontendDiagnostic limitDiagnostic(String code, String message) {
        return diagnostic(GitLabFrontendDiagnosticSeverity.WARNING, code, message, null);
    }

    private String label(String route, String symbol, GitLabFrontendRouteEntryKind kind) {
        if (kind == GitLabFrontendRouteEntryKind.REDIRECT) {
            return "Redirect " + route;
        }
        var segments = normalizeRoute(route).split("/");
        for (var index = segments.length - 1; index >= 0; index--) {
            var segment = segments[index];
            if (StringUtils.hasText(segment) && !segment.startsWith(":")) {
                return humanize(segment);
            }
        }
        if (StringUtils.hasText(symbol)) {
            var normalized = symbol.replaceFirst("^Crm", "").replaceFirst("(?:Component|Page)$", "");
            return humanize(normalized.replaceAll("([a-z0-9])([A-Z])", "$1-$2"));
        }
        return "Root screen";
    }

    private String humanize(String value) {
        var words = value.replace('_', '-').split("-");
        var result = new ArrayList<String>();
        for (var word : words) {
            if (StringUtils.hasText(word)) {
                result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", result);
    }

    private List<String> routeParameters(String route) {
        var parameters = new LinkedHashSet<String>();
        var matcher = ROUTE_PARAMETER.matcher(route);
        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }
        return List.copyOf(parameters);
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

    private String rebaseRoute(String parent, String child) {
        var normalizedParent = normalizeRoute(parent);
        var normalizedChild = normalizeRoute(child);
        if (!StringUtils.hasText(normalizedParent)) {
            return StringUtils.hasText(normalizedChild) ? "/" + normalizedChild : "/";
        }
        if (!StringUtils.hasText(normalizedChild)) {
            return "/" + normalizedParent;
        }
        return "/" + normalizedParent + "/" + normalizedChild;
    }

    private String normalizeRoute(String route) {
        if (!StringUtils.hasText(route) || "/".equals(route.trim())) {
            return "";
        }
        var normalized = route.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean withinScope(String path, List<String> prefixes) {
        if (prefixes.isEmpty()) {
            return true;
        }
        return prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private String normalizeRelativePath(String parent, String relative) {
        var stack = new ArrayDeque<String>();
        for (var segment : (StringUtils.hasText(parent) ? parent : "").split("/")) {
            if (StringUtils.hasText(segment)) {
                stack.addLast(segment);
            }
        }
        for (var segment : relative.replace('\\', '/').split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
            } else {
                stack.addLast(segment);
            }
        }
        return String.join("/", stack);
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        var normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String parentPath(String path) {
        var index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(0, index) : "";
    }

    private String fileName(String path) {
        var index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private int lineNumber(String source, int index) {
        var line = 1;
        for (var offset = 0; offset < Math.max(0, index) && offset < source.length(); offset++) {
            if (source.charAt(offset) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private static String sourceContent(GitLabRepositoryFileContent content) {
        return content != null ? content.content() : null;
    }

    private final class RepositorySession {
        private final GitLabFrontendRepositoryScope scope;
        private final GitLabFrontendDiscoveryLimits limits;
        private final List<GitLabFrontendDiagnostic> diagnostics;
        private final Map<String, GitLabRepositoryFileContent> files = new LinkedHashMap<>();
        private int returnedCharacters;

        private RepositorySession(
                GitLabFrontendRepositoryScope scope,
                GitLabFrontendDiscoveryLimits limits,
                List<GitLabFrontendDiagnostic> diagnostics
        ) {
            this.scope = scope;
            this.limits = limits;
            this.diagnostics = diagnostics;
        }

        private GitLabRepositoryFileContent read(String path) {
            if (files.containsKey(path)) {
                return files.get(path);
            }
            if (returnedCharacters >= limits.maxTotalCharacters()) {
                diagnostics.add(limitDiagnostic(
                        "SOURCE_SESSION_CHARACTER_LIMIT_REACHED",
                        "Repository reads reached maxTotalCharacters=" + limits.maxTotalCharacters() + "."
                ));
                files.put(path, null);
                return null;
            }
            try {
                var remainingCharacters = limits.maxTotalCharacters() - returnedCharacters;
                var content = gitLabRepositoryPort.readFile(
                        scope.group(),
                        scope.projectName(),
                        scope.ref(),
                        path,
                        Math.min(limits.maxFileCharacters(), remainingCharacters)
                );
                if (content == null) {
                    diagnostics.add(diagnostic(
                            GitLabFrontendDiagnosticSeverity.WARNING,
                            "SOURCE_FILE_READ_FAILED",
                            "A repository source file read returned no content.",
                            path
                    ));
                }
                files.put(path, content);
                if (content != null && content.content() != null) {
                    returnedCharacters += content.content().length();
                }
                if (content != null && content.truncated()) {
                    diagnostics.add(diagnostic(
                            GitLabFrontendDiagnosticSeverity.WARNING,
                            "SOURCE_FILE_TRUNCATED",
                            "A source file reached maxFileCharacters=" + limits.maxFileCharacters() + ".",
                            path
                    ));
                }
                return content;
            } catch (RuntimeException exception) {
                diagnostics.add(diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        "SOURCE_FILE_READ_FAILED",
                        "A repository source file could not be read: " + exception.getClass().getSimpleName(),
                        path
                ));
                files.put(path, null);
                return null;
            }
        }
    }

    private record Inventory(List<String> paths, int totalCount, boolean truncated) {
    }

    private record RouteWork(
            String sourcePath,
            String parentRoute,
            boolean lazy,
            int depth,
            List<String> inheritedGuards
    ) {
    }

    private record SourceWork(String path, int depth) {
    }

    private record ViewResolution(String symbol, String path, boolean ambiguous) {
    }

    private record BoundedFiles(List<GitLabFrontendSourceFile> files, int totalCharacters, boolean truncated) {
    }

    private record SourceAccumulator(
            String path,
            EnumSet<GitLabFrontendSourceRole> roles,
            String content,
            boolean truncated
    ) {
    }
}
