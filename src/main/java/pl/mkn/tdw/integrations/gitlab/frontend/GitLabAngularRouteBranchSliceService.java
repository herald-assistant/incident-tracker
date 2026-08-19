package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabAngularRouteBranchSliceService {

    public static final int DEFAULT_OUTPUT_CHARACTERS = 24_000;
    public static final int MAX_OUTPUT_CHARACTERS = 80_000;

    private static final int MAX_SOURCE_CHARACTERS = 200_000;
    private static final Pattern IMPORT = Pattern.compile(
            "(?ms)^\\s*import\\s+(?!\\()(?:(?:type\\s+)?(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?"
    );
    private static final Pattern TOP_LEVEL_VARIABLE = Pattern.compile(
            "(?m)(?:^|\\n)\\s*(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?::[^=;]+)?="
    );
    private static final Pattern TOP_LEVEL_FUNCTION = Pattern.compile(
            "(?m)(?:^|\\n)\\s*(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z_$][A-Za-z0-9_$]*)[^\\{]*\\{"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]*\\b");

    private final GitLabFrontendRouteGraphDiscoveryService routeGraphDiscoveryService;
    private final GitLabRepositoryPort repositoryPort;

    public GitLabAngularRouteBranchSliceResponse readBranchSlice(GitLabAngularRouteBranchSliceRequest request) {
        var graph = routeGraphDiscoveryService.discover(request.scope(), GitLabFrontendGraphLimits.defaults());
        var selected = graph.nodes().stream()
                .filter(node -> node.screen() != null)
                .filter(node -> request.screenId().equals(node.screen().screenId()))
                .findFirst()
                .orElseThrow(() -> new GitLabFrontendDiscoveryException(
                        "FRONTEND_SCREEN_NOT_FOUND", "Selected frontend screen was not found in the current revision."
                ));
        if (StringUtils.hasText(request.expectedRevision())
                && !request.expectedRevision().equals(graph.sourceRevision().commitId())) {
            throw new GitLabFrontendDiscoveryException(
                    "FRONTEND_SOURCE_REVISION_CHANGED",
                    "Frontend source revision changed. Reload the screen catalog before reading a route slice."
            );
        }

        var chain = graph.effectiveRouteChains().stream()
                .filter(candidate -> request.screenId().equals(candidate.screen().screenId()))
                .findFirst()
                .orElseThrow(() -> new GitLabFrontendDiscoveryException(
                        "FRONTEND_ROUTE_CHAIN_NOT_FOUND", "Effective route chain was not found for the selected screen."
                ));
        var byId = new LinkedHashMap<String, GitLabFrontendRouteNode>();
        graph.nodes().forEach(node -> byId.put(node.nodeId(), node));
        var includedNodeIds = new LinkedHashSet<String>();
        chain.segments().forEach(segment -> includedNodeIds.add(segment.nodeId()));
        if (Boolean.TRUE.equals(request.includeDescendantRoutes())) {
            includeDescendants(selected.nodeId(), graph.nodes(), includedNodeIds);
        }

        var limitations = new LinkedHashSet<String>();
        var files = new ArrayList<GitLabAngularRouteBranchSliceFile>();
        var maxCharacters = normalizeLimit(request.maxCharacters());
        var remaining = maxCharacters;
        var nodesByPath = includedNodeIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                        node -> node.routeSource().path(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        for (var entry : nodesByPath.entrySet()) {
            var file = sliceFile(request.scope(), entry.getKey(), entry.getValue(), remaining, limitations);
            if (file != null) {
                files.add(file);
                remaining = Math.max(0, remaining - file.returnedCharacters());
            }
            if (remaining == 0) {
                limitations.add("Route branch slice reached maxCharacters=" + maxCharacters + ".");
                break;
            }
        }

        var childRoutes = graph.nodes().stream()
                .filter(node -> selected.nodeId().equals(node.parentNodeId()))
                .sorted(Comparator.comparing(GitLabFrontendRouteNode::routePattern)
                        .thenComparing(GitLabFrontendRouteNode::nodeId))
                .map(node -> childReference(selected, node, graph.nodes()))
                .toList();
        var sourceCharacters = files.stream().mapToInt(GitLabAngularRouteBranchSliceFile::sourceCharacters).sum();
        var returnedCharacters = files.stream().mapToInt(GitLabAngularRouteBranchSliceFile::returnedCharacters).sum();
        var omittedImports = files.stream().mapToInt(GitLabAngularRouteBranchSliceFile::omittedImportCount).sum();
        var omittedRoutes = files.stream().mapToInt(GitLabAngularRouteBranchSliceFile::omittedSiblingRouteCount).sum();
        var unresolvedSymbols = files.stream().flatMap(file -> file.unresolvedSymbols().stream()).distinct().toList();
        var truncated = files.stream().anyMatch(GitLabAngularRouteBranchSliceFile::truncated)
                || files.size() < nodesByPath.size();
        var diagnostics = new ArrayList<>(graph.diagnostics());
        files.stream().filter(file -> !file.unresolvedSymbols().isEmpty()).forEach(file -> diagnostics.add(
                new GitLabFrontendGraphDiagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        GitLabFrontendGraphDiagnosticCode.SYMBOL_DEPENDENCY_UNRESOLVED,
                        "Route slice could not resolve referenced symbols: "
                                + String.join(", ", file.unresolvedSymbols()) + ".",
                        null,
                        null,
                        new GitLabFrontendSourceReference(file.path(), null, null, null)
                )
        ));
        if (!unresolvedSymbols.isEmpty()) {
            limitations.add("Route branch contains unresolved symbol dependencies: "
                    + String.join(", ", unresolvedSymbols) + ".");
        }
        var partial = truncated || !unresolvedSymbols.isEmpty();
        return new GitLabAngularRouteBranchSliceResponse(
                graph.scope(), graph.sourceRevision(), partial ? "PARTIAL" : "OK", selected, chain,
                files, childRoutes, sourceCharacters, returnedCharacters,
                Math.max(0, sourceCharacters - returnedCharacters), omittedImports, omittedRoutes,
                truncated, List.copyOf(limitations), List.copyOf(diagnostics)
        );
    }

    private GitLabAngularRouteBranchSliceFile sliceFile(
            GitLabFrontendRepositoryScope scope,
            String path,
            List<GitLabFrontendRouteNode> nodes,
            int remaining,
            Set<String> limitations
    ) {
        if (remaining <= 0 || !inScope(scope, path)) {
            limitations.add("Route source is outside the configured code-search scope: " + path + ".");
            return null;
        }
        final String source;
        try {
            var content = repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), path, MAX_SOURCE_CHARACTERS);
            if (content == null || content.content() == null || content.truncated()) {
                limitations.add("Route source could not be read completely: " + path + ".");
                return null;
            }
            source = content.content();
        } catch (RuntimeException exception) {
            limitations.add("Route source could not be read: " + path + ".");
            return null;
        }

        var parser = new AngularRouteSourceParser();
        var symbols = nodes.stream().map(node -> node.routeSource().symbol()).filter(StringUtils::hasText).distinct().toList();
        var parsedRoutes = new LinkedHashMap<Integer, AngularRouteSourceParser.ParsedRoute>();
        if (symbols.isEmpty()) {
            parser.parse(path, source).routes().forEach(route -> parsedRoutes.put(route.sourceOffset(), route));
        } else {
            for (var symbol : symbols) {
                parser.parseCollection(path, source, symbol, (sourcePath, content, expression) ->
                                new AngularRouteSourceParser.StaticStringResolution(null, null))
                        .routes().forEach(route -> parsedRoutes.put(route.sourceOffset(), route));
            }
        }
        if (parsedRoutes.isEmpty()) {
            parser.parse(path, source).routes().forEach(route -> parsedRoutes.put(route.sourceOffset(), route));
        }

        var includedOffsets = new LinkedHashSet<Integer>();
        for (var node : nodes) {
            parsedRoutes.values().stream()
                    .filter(route -> route.sourceLine() == node.routeSource().startLine())
                    .findFirst()
                    .ifPresent(route -> includedOffsets.add(route.sourceOffset()));
        }
        var rootRoutes = includedOffsets.stream()
                .map(parsedRoutes::get)
                .filter(java.util.Objects::nonNull)
                .filter(route -> route.parentSourceOffset() == null || !includedOffsets.contains(route.parentSourceOffset()))
                .sorted(Comparator.comparingInt(AngularRouteSourceParser.ParsedRoute::sourceOffset))
                .toList();
        var omittedRoutes = new int[]{0};
        var fragments = new ArrayList<String>();
        for (var route : rootRoutes) {
            fragments.add(renderRoute(source, route, parsedRoutes.values(), includedOffsets, omittedRoutes));
        }
        if (fragments.isEmpty()) {
            limitations.add("Exact route object could not be matched in " + path + ".");
            return null;
        }
        var semanticText = String.join("\n", fragments) + "\n" + nodes.stream()
                .flatMap(node -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                node.viewTarget() != null ? node.viewTarget().symbol() : null,
                                node.lazyTarget() != null ? node.lazyTarget().symbol() : null
                        ),
                        node.configuration().stream().flatMap(configuration -> configuration.referencedSymbols().stream())
                ))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(" "));
        var imports = imports(source);
        var localDeclarations = localDeclarations(source);
        var retainedDeclarations = reachableLocalDeclarations(localDeclarations, semanticText);
        var declarationText = retainedDeclarations.stream()
                .map(LocalDeclaration::content)
                .collect(java.util.stream.Collectors.joining("\n"));
        var usedIdentifiers = identifiers(semanticText + "\n" + declarationText);
        var retainedImports = imports.stream().filter(candidate -> candidate.identifiers().stream()
                .anyMatch(usedIdentifiers::contains)).toList();
        var unresolvedSymbols = unresolvedSymbols(nodes, fragments, imports, localDeclarations);
        var omittedImports = Math.max(0, imports.size() - retainedImports.size());
        var builder = new StringBuilder();
        retainedImports.forEach(candidate -> builder.append(candidate.statement().strip()).append('\n'));
        if (omittedImports > 0) {
            builder.append("// ... ").append(omittedImports).append(" unrelated imports omitted ...\n");
        }
        if (!retainedDeclarations.isEmpty()) {
            builder.append("// Local declarations required by the retained route branch\n");
            retainedDeclarations.forEach(declaration -> builder.append(declaration.content().strip()).append('\n'));
        }
        builder.append("// Route branch slice from ").append(path).append("\n");
        for (var fragment : fragments) {
            builder.append(fragment.strip()).append("\n");
        }
        var rendered = builder.toString().stripTrailing();
        var truncated = rendered.length() > remaining;
        if (truncated) {
            rendered = rendered.substring(0, Math.max(0, remaining - 44))
                    + "\n// ... route slice output truncated ...";
        }
        return new GitLabAngularRouteBranchSliceFile(
                path, rendered, source.length(), rendered.length(),
                nodes.stream().map(GitLabFrontendRouteNode::nodeId).toList(),
                retainedImports.stream().map(ImportStatement::statement).toList(),
                retainedDeclarations.stream().map(LocalDeclaration::name).toList(),
                unresolvedSymbols,
                omittedImports, omittedRoutes[0], truncated
        );
    }

    private List<LocalDeclaration> reachableLocalDeclarations(
            List<LocalDeclaration> declarations,
            String semanticText
    ) {
        var byName = declarations.stream().collect(java.util.stream.Collectors.toMap(
                LocalDeclaration::name,
                declaration -> declaration,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        var required = new LinkedHashSet<>(identifiers(semanticText));
        var retained = new LinkedHashSet<LocalDeclaration>();
        var changed = true;
        while (changed) {
            changed = false;
            for (var name : List.copyOf(required)) {
                var declaration = byName.get(name);
                if (declaration != null && retained.add(declaration)) {
                    required.addAll(identifiers(declaration.content()));
                    changed = true;
                }
            }
        }
        return retained.stream().sorted(Comparator.comparingInt(LocalDeclaration::start)).toList();
    }

    private List<String> unresolvedSymbols(
            List<GitLabFrontendRouteNode> nodes,
            List<String> fragments,
            List<ImportStatement> imports,
            List<LocalDeclaration> declarations
    ) {
        var imported = imports.stream().flatMap(candidate -> candidate.identifiers().stream())
                .collect(java.util.stream.Collectors.toSet());
        var local = declarations.stream().map(LocalDeclaration::name).collect(java.util.stream.Collectors.toSet());
        var rendered = String.join("\n", fragments);
        return nodes.stream()
                .flatMap(node -> node.configuration().stream())
                .flatMap(configuration -> configuration.referencedSymbols().stream())
                .filter(StringUtils::hasText)
                .filter(symbol -> !imported.contains(symbol) && !local.contains(symbol))
                .filter(symbol -> requiresLexicalBinding(rendered, symbol))
                .distinct()
                .sorted()
                .toList();
    }

    private boolean requiresLexicalBinding(String source, String symbol) {
        var matcher = Pattern.compile("(?<![.$\\w])" + Pattern.quote(symbol) + "\\b").matcher(source);
        while (matcher.find()) {
            var tail = source.substring(matcher.end());
            if (!tail.matches("(?s)^\\s*:.*")) {
                return true;
            }
        }
        return false;
    }

    private List<LocalDeclaration> localDeclarations(String source) {
        var mask = mask(source);
        var result = new ArrayList<LocalDeclaration>();
        var variables = TOP_LEVEL_VARIABLE.matcher(mask);
        while (variables.find()) {
            if (!topLevel(mask, variables.start())) {
                continue;
            }
            var end = statementEnd(mask, variables.end());
            if (end > variables.start()) {
                var start = declarationStart(source, variables.start());
                result.add(new LocalDeclaration(variables.group(1), start, end, source.substring(start, end)));
            }
        }
        var functions = TOP_LEVEL_FUNCTION.matcher(mask);
        while (functions.find()) {
            if (!topLevel(mask, functions.start())) {
                continue;
            }
            var open = mask.lastIndexOf('{', functions.end() - 1);
            var close = matching(mask, open, '{', '}');
            if (close > open) {
                var start = declarationStart(source, functions.start());
                result.add(new LocalDeclaration(
                        functions.group(1), start, close + 1, source.substring(start, close + 1)
                ));
            }
        }
        return result.stream().sorted(Comparator.comparingInt(LocalDeclaration::start)).toList();
    }

    private int declarationStart(String source, int candidate) {
        var start = candidate;
        while (start < source.length() && Character.isWhitespace(source.charAt(start))) {
            start++;
        }
        return start;
    }

    private int statementEnd(String source, int start) {
        var round = 0;
        var square = 0;
        var curly = 0;
        for (var index = start; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '(') round++;
            else if (character == ')') round = Math.max(0, round - 1);
            else if (character == '[') square++;
            else if (character == ']') square = Math.max(0, square - 1);
            else if (character == '{') curly++;
            else if (character == '}') curly = Math.max(0, curly - 1);
            else if (character == ';' && round == 0 && square == 0 && curly == 0) return index + 1;
        }
        return -1;
    }

    private boolean topLevel(String source, int offset) {
        var depth = 0;
        for (var index = 0; index < Math.min(offset, source.length()); index++) {
            if (source.charAt(index) == '{') depth++;
            else if (source.charAt(index) == '}') depth = Math.max(0, depth - 1);
        }
        return depth == 0;
    }

    private int matching(String source, int open, char opening, char closing) {
        if (open < 0 || open >= source.length() || source.charAt(open) != opening) return -1;
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            if (source.charAt(index) == opening) depth++;
            else if (source.charAt(index) == closing && --depth == 0) return index;
        }
        return -1;
    }

    private String mask(String source) {
        var masked = new StringBuilder(source);
        var state = MaskState.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (state == MaskState.CODE) {
                if (current == '/' && next == '/') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.BLOCK_COMMENT;
                } else if (current == '\'') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.SINGLE;
                } else if (current == '\"') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.DOUBLE;
                } else if (current == '`') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.TEMPLATE;
                }
            } else {
                if (current != '\n' && current != '\r') masked.setCharAt(index, ' ');
                if (state == MaskState.LINE_COMMENT && (current == '\n' || current == '\r')) state = MaskState.CODE;
                else if (state == MaskState.BLOCK_COMMENT && current == '*' && next == '/') {
                    if (index + 1 < masked.length()) masked.setCharAt(index + 1, ' ');
                    index++;
                    state = MaskState.CODE;
                } else if ((state == MaskState.SINGLE && current == '\'')
                        || (state == MaskState.DOUBLE && current == '\"')
                        || (state == MaskState.TEMPLATE && current == '`')) {
                    var escaped = index > 0 && source.charAt(index - 1) == '\\';
                    if (!escaped) state = MaskState.CODE;
                }
            }
        }
        return masked.toString();
    }

    private String renderRoute(
            String source,
            AngularRouteSourceParser.ParsedRoute route,
            java.util.Collection<AngularRouteSourceParser.ParsedRoute> allRoutes,
            Set<Integer> includedOffsets,
            int[] omittedRoutes
    ) {
        var children = allRoutes.stream()
                .filter(candidate -> java.util.Objects.equals(candidate.parentSourceOffset(), route.sourceOffset()))
                .sorted(Comparator.comparingInt(AngularRouteSourceParser.ParsedRoute::sourceOffset).reversed())
                .toList();
        var rendered = new StringBuilder(source.substring(route.sourceOffset(), route.sourceEndOffset() + 1));
        for (var child : children) {
            var relativeStart = child.sourceOffset() - route.sourceOffset();
            var relativeEnd = child.sourceEndOffset() - route.sourceOffset() + 1;
            var replacement = includedOffsets.contains(child.sourceOffset())
                    ? renderRoute(source, child, allRoutes, includedOffsets, omittedRoutes)
                    : "{ /* ... 1 sibling route entry omitted ... */ }";
            if (!includedOffsets.contains(child.sourceOffset())) {
                omittedRoutes[0]++;
            }
            rendered.replace(relativeStart, relativeEnd, replacement);
        }
        return rendered.toString();
    }

    private List<ImportStatement> imports(String source) {
        var result = new ArrayList<ImportStatement>();
        var matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            var bindings = matcher.group(1) != null ? matcher.group(1) : "";
            result.add(new ImportStatement(matcher.group(), identifiers(bindings)));
        }
        return List.copyOf(result);
    }

    private Set<String> identifiers(String value) {
        var result = new LinkedHashSet<String>();
        var matcher = IDENTIFIER.matcher(value != null ? value : "");
        while (matcher.find()) {
            var identifier = matcher.group();
            if (!Set.of("import", "from", "as", "type").contains(identifier)) {
                result.add(identifier);
            }
        }
        return result;
    }

    private void includeDescendants(String parentId, List<GitLabFrontendRouteNode> nodes, Set<String> included) {
        for (var node : nodes) {
            if (parentId.equals(node.parentNodeId()) && included.add(node.nodeId())) {
                includeDescendants(node.nodeId(), nodes, included);
            }
        }
    }

    private GitLabAngularRouteChildReference childReference(
            GitLabFrontendRouteNode parent,
            GitLabFrontendRouteNode node,
            List<GitLabFrontendRouteNode> nodes
    ) {
        var referenceId = node.screen() != null ? node.screen().screenId() : node.nodeId();
        var structural = node.screen() == null
                && node.viewTarget() == null
                && node.lazyTarget() == null
                && !StringUtils.hasText(node.redirectTarget());
        return new GitLabAngularRouteChildReference(
                "angular-route:" + referenceId, node.nodeId(),
                node.screen() != null ? node.screen().screenId() : null,
                node.routePattern(), node.label(), node.kind(), node.status(), node.viewTarget(), node.lazyTarget(),
                node.redirectTarget(), structural, node.routePattern().equals(parent.routePattern()),
                nodes.stream().anyMatch(candidate -> node.nodeId().equals(candidate.parentNodeId()))
        );
    }

    private int normalizeLimit(Integer maxCharacters) {
        if (maxCharacters == null) {
            return DEFAULT_OUTPUT_CHARACTERS;
        }
        return Math.max(1_000, Math.min(MAX_OUTPUT_CHARACTERS, maxCharacters));
    }

    private boolean inScope(GitLabFrontendRepositoryScope scope, String path) {
        return scope.pathPrefixes().isEmpty() || scope.pathPrefixes().stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private record ImportStatement(String statement, Set<String> identifiers) {
    }

    private record LocalDeclaration(String name, int start, int end, String content) {
    }

    private enum MaskState {CODE, LINE_COMMENT, BLOCK_COMMENT, SINGLE, DOUBLE, TEMPLATE}
}
