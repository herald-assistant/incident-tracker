package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
final class GitLabFrontendSemanticContextBuilder {

    private static final Pattern IMPORT_STATEMENT = Pattern.compile(
            "(?ms)^\\s*import\\b.*?;\\s*$|^\\s*export\\s+(?:\\*|\\{).*?\\bfrom\\b.*?;\\s*$"
    );
    private static final Pattern TEMPLATE_EXPRESSION = Pattern.compile(
            "(?:\\{\\{([^}]*)}}|(?:\\[[^]]+]|\\([^)]*\\)|\\*?[A-Za-z][\\w.-]*)\\s*=\\s*['\"]([^'\"]*)['\"])"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern METHOD_START = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|readonly|override|async)\\s+)*"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>|\\([^;{}]*\\)\\s*(?::[^={]+)?)\\s*\\{"
    );
    private static final Pattern METHOD_CALL = Pattern.compile("(?<![.$])\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern ROUTE_LINE = Pattern.compile(
            "\\b(path|redirectTo|component|loadComponent|loadChildren|children|canActivate|canActivateChild|canDeactivate|resolve|data|title|outlet)\\s*:"
    );
    private static final Pattern SEMANTIC_TS = Pattern.compile(
            "FormGroup|FormControl|FormArray|Validators\\.|setValidators|addValidators|clearValidators|"
                    + "patchValue|setValue|valueChanges|enable\\(|disable\\(|markAs|updateValueAndValidity|"
                    + "store\\.|dispatch\\(|select\\(|createEffect|createReducer|on\\(|"
                    + "HttpClient|\\.get\\(|\\.post\\(|\\.put\\(|\\.patch\\(|\\.delete\\(|"
                    + "WebSocket|webSocket\\(|subscribe\\(|navigate\\(|navigateByUrl\\(|dialog\\.open|"
                    + "router\\.|hasRole|hasPermission|canActivate|canDeactivate|catchError|throwError|"
                    + "loading|error|success|empty|visible|hidden|required|disabled",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FORM_SLICE = Pattern.compile(
            "Form(Group|Control|Array)|Validators\\.|valueChanges|setValue|patchValue|enable\\(|disable\\(|updateValueAndValidity",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATE_SLICE = Pattern.compile(
            "store\\.|dispatch\\(|select\\(|createEffect|createReducer|createSelector",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BACKEND_SLICE = Pattern.compile(
            "HttpClient|webSocket\\(|\\.(get|post|put|patch|delete)\\(",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEMANTIC_TEMPLATE = Pattern.compile(
            "\\{\\{|\\([^)]*\\)\\s*=|\\[[^]]+]\\s*=|\\*ng|@if|@for|form(Group|Control|ControlName)?|"
                    + "<(input|select|textarea|button|label|table|tr|td|th|mat-|h[1-6]|p)(?:\\s|>)|"
                    + "routerLink|aria-|role\\s*=|error|empty|loading|disabled|hidden",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VISUAL_STATE = Pattern.compile(
            "display\\s*:|visibility\\s*:|pointer-events\\s*:|opacity\\s*:|\\[hidden]|\\.hidden|\\.disabled|\\.loading|\\.error",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> IGNORED_TEMPLATE_IDENTIFIERS = Set.of(
            "true", "false", "null", "undefined", "let", "of", "as", "index", "first", "last",
            "click", "change", "input", "submit", "blur", "focus", "keydown", "keyup",
            "class", "style", "attr", "ngIf", "ngFor", "ngClass", "ngStyle", "formGroup",
            "formControl", "formControlName", "routerLink", "async"
    );

    private final AngularBootstrapSourceParser sourceParser = new AngularBootstrapSourceParser();

    GitLabFrontendSemanticContext build(
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendTargetedImportResolver imports,
            GitLabFrontendRouteNode screen,
            List<GitLabFrontendRouteNode> descendants
    ) {
        var templateIdentifiers = templateIdentifiers(files);
        var usedSymbols = new LinkedHashMap<String, LinkedHashSet<String>>();
        var usedMembers = new LinkedHashMap<String, LinkedHashSet<String>>();
        var relations = new LinkedHashMap<String, GitLabFrontendUseCaseRelation>();
        var unresolved = new LinkedHashMap<String, GitLabFrontendUnresolvedFrontier>();
        var routeRanges = routeSourceRanges(screen, descendants);

        addRouteRelations(relations, screen, descendants);
        addSourceRelations(files, imports, templateIdentifiers, usedSymbols, usedMembers, relations, unresolved);

        var slices = new ArrayList<GitLabFrontendSourceSlice>();
        for (var file : files.values()) {
            slices.addAll(slices(file, templateIdentifiers.getOrDefault(componentKey(file.path()), Set.of()),
                    usedSymbols.getOrDefault(file.path(), new LinkedHashSet<>()),
                    usedMembers.getOrDefault(file.path(), new LinkedHashSet<>()),
                    routeRanges.getOrDefault(file.path(), List.of())));
        }
        slices.sort(Comparator.comparing(GitLabFrontendSourceSlice::path)
                .thenComparingInt(GitLabFrontendSourceSlice::startLine)
                .thenComparing(GitLabFrontendSourceSlice::sliceId));

        var sliceCountByPath = new LinkedHashMap<String, Integer>();
        slices.forEach(slice -> sliceCountByPath.merge(slice.path(), 1, Integer::sum));
        var manifest = files.values().stream()
                .map(file -> new GitLabFrontendSourceManifestEntry(
                        file.path(), file.roles(), file.content().length(), sha256(file.content()),
                        sliceCountByPath.getOrDefault(file.path(), 0)
                ))
                .toList();
        var sourceCharacters = files.values().stream().mapToInt(file -> file.content().length()).sum();
        var returnedCharacters = slices.stream().mapToInt(GitLabFrontendSourceSlice::returnedCharacters).sum();
        var metrics = new GitLabFrontendContextMetrics(
                files.size(), sourceCharacters, slices.size(), returnedCharacters,
                Math.max(0, sourceCharacters - returnedCharacters),
                (int) manifest.stream().filter(entry -> entry.sliceCount() == 0).count(),
                relations.size(), unresolved.size()
        );
        return new GitLabFrontendSemanticContext(
                manifest, slices, List.copyOf(relations.values()), List.copyOf(unresolved.values()), metrics
        );
    }

    private void addRouteRelations(
            Map<String, GitLabFrontendUseCaseRelation> relations,
            GitLabFrontendRouteNode screen,
            List<GitLabFrontendRouteNode> descendants
    ) {
        if (screen != null && screen.viewTarget() != null) {
            addRelation(relations, screen.nodeId(), screen.viewTarget().sourcePath(),
                    GitLabFrontendUseCaseRelationKind.ROUTE_TO_VIEW, screen.viewTarget().symbol(),
                    GitLabFrontendSignalConfidence.HIGH, screen.routeSource());
        }
        for (var descendant : descendants != null ? descendants : List.<GitLabFrontendRouteNode>of()) {
            if (descendant.viewTarget() == null) {
                continue;
            }
            addRelation(relations, descendant.parentNodeId(), descendant.viewTarget().sourcePath(),
                    GitLabFrontendUseCaseRelationKind.ROUTED_CHILD, descendant.viewTarget().symbol(),
                    GitLabFrontendSignalConfidence.HIGH, descendant.routeSource());
        }
    }

    private void addSourceRelations(
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendTargetedImportResolver imports,
            Map<String, Set<String>> templateIdentifiers,
            Map<String, LinkedHashSet<String>> usedSymbols,
            Map<String, LinkedHashSet<String>> usedMembers,
            Map<String, GitLabFrontendUseCaseRelation> relations,
            Map<String, GitLabFrontendUnresolvedFrontier> unresolved
    ) {
        for (var file : files.values()) {
            if (!file.path().endsWith(".ts")) {
                continue;
            }
            var parsed = sourceParser.parse(file.path(), file.content());
            var bodyWithoutImports = IMPORT_STATEMENT.matcher(file.content()).replaceAll("\n");
            for (var binding : parsed.imports().values()) {
                if (!containsIdentifier(bodyWithoutImports, binding.localName())) {
                    continue;
                }
                var targets = imports.resolve(file.path(), binding.moduleSpecifier());
                var includedTargets = targets.stream().filter(files::containsKey).distinct().toList();
                var referencedMembers = memberCalls(file.content(), binding.localName());
                for (var target : includedTargets) {
                    usedSymbols.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(binding.exportedName());
                    usedMembers.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).addAll(referencedMembers);
                    addRelation(relations, file.path(), target,
                            GitLabFrontendUseCaseRelationKind.USES_IMPORTED_SYMBOL, binding.localName(),
                            GitLabFrontendSignalConfidence.HIGH,
                            new GitLabFrontendSourceReference(file.path(), binding.localName(), null, null));
                }
                if (includedTargets.isEmpty() && repositoryOwned(binding.moduleSpecifier())) {
                    var frontierId = stableId(file.path(), binding.localName(), binding.moduleSpecifier());
                    unresolved.putIfAbsent(frontierId, new GitLabFrontendUnresolvedFrontier(
                            frontierId, file.path(), binding.localName(),
                            "Used imported symbol could not be mapped to source inside the selected-screen context.",
                            affectedCategories(file.roles(), binding.localName(), targets),
                            targets
                    ));
                }
            }
            componentTemplate(file, files).ifPresent(templatePath -> addRelation(
                    relations, file.path(), templatePath,
                    GitLabFrontendUseCaseRelationKind.COMPONENT_TO_TEMPLATE, null,
                    GitLabFrontendSignalConfidence.HIGH,
                    new GitLabFrontendSourceReference(file.path(), "templateUrl", null, null)
            ));
            var identifiers = templateIdentifiers.getOrDefault(componentKey(file.path()), Set.of());
            for (var identifier : identifiers) {
                if (methodNames(file.content()).contains(identifier)) {
                    var templatePath = templatePathForComponent(file.path(), files);
                    addRelation(relations, templatePath != null ? templatePath : file.path(), file.path(),
                            GitLabFrontendUseCaseRelationKind.TEMPLATE_TO_HANDLER, identifier,
                            GitLabFrontendSignalConfidence.HIGH,
                            new GitLabFrontendSourceReference(file.path(), identifier, null, null));
                }
            }
        }
    }

    private List<GitLabFrontendSourceSlice> slices(
            GitLabFrontendSourceFile file,
            Set<String> templateIdentifiers,
            Set<String> usedSymbols,
            Set<String> usedMembers,
            List<LineRange> exactRouteRanges
    ) {
        var path = file.path().toLowerCase(Locale.ROOT);
        if (path.endsWith(".html")) {
            return slicesFromRanges(file, semanticTemplateRanges(file.content()), GitLabFrontendSourceSliceKind.TEMPLATE_INTERACTION);
        }
        if (path.endsWith(".scss") || path.endsWith(".css")) {
            return slicesFromRanges(file, visualStateRanges(file.content()), GitLabFrontendSourceSliceKind.VISUAL_STATE);
        }
        if (!path.endsWith(".ts")) {
            return slicesFromRanges(file, nonBlankRanges(file.content()), GitLabFrontendSourceSliceKind.RELATED_DECLARATION);
        }
        if (file.roles().contains(GitLabFrontendSourceRole.ROUTE_CONFIGURATION)) {
            return slicesFromRanges(file, routeRanges(file.content(), exactRouteRanges), GitLabFrontendSourceSliceKind.ROUTE_CONFIGURATION);
        }
        return typeScriptSlices(file, templateIdentifiers, usedSymbols, usedMembers);
    }

    private List<GitLabFrontendSourceSlice> typeScriptSlices(
            GitLabFrontendSourceFile file,
            Set<String> templateIdentifiers,
            Set<String> usedSymbols,
            Set<String> usedMembers
    ) {
        var lines = lines(file.content());
        var selected = new LinkedHashSet<Integer>();
        var blocks = methodBlocks(lines);
        var selectedMethods = new LinkedHashSet<String>();
        selectedMethods.addAll(templateIdentifiers);
        selectedMethods.addAll(usedMembers);
        var importedBackendBoundary = !usedMembers.isEmpty()
                && (file.roles().contains(GitLabFrontendSourceRole.BACKEND_CLIENT)
                || file.roles().contains(GitLabFrontendSourceRole.WEBSOCKET_STREAM));
        for (var block : blocks.values()) {
            if ((!importedBackendBoundary || usedMembers.contains(block.name()))
                    && SEMANTIC_TS.matcher(block.content(lines)).find()) {
                selectedMethods.add(block.name());
            }
        }
        closeLocalCalls(blocks, lines, selectedMethods);
        selectUsedDataDeclarations(lines, usedSymbols, selected);

        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.contains("@Component") || line.contains("@Directive") || line.contains("@Injectable")
                    || line.matches(".*\\b(export\\s+)?(class|interface|type|enum)\\b.*")
                    || identifiersOnLine(line, templateIdentifiers)
                    || identifiersOnLine(line, usedSymbols)
                    || SEMANTIC_TS.matcher(line).find()) {
                selected.add(index + 1);
            }
        }
        for (var methodName : selectedMethods) {
            var block = blocks.get(methodName);
            if (block != null) {
                for (var line = block.startLine(); line <= block.endLine(); line++) {
                    selected.add(line);
                }
            }
        }
        expandDecoratorAndStatements(lines, selected);
        var ranges = mergeSelectedLines(selected, 1);
        var result = new ArrayList<GitLabFrontendSourceSlice>();
        for (var range : ranges) {
            var content = content(lines, range);
            var kind = kind(file.roles(), content);
            var symbol = blocks.values().stream()
                    .filter(block -> block.startLine() == range.startLine() && block.endLine() == range.endLine())
                    .map(MethodBlock::name)
                    .findFirst()
                    .orElse(null);
            result.add(slice(file, range, kind, symbol, content));
        }
        return result;
    }

    private void closeLocalCalls(
            Map<String, MethodBlock> blocks,
            List<String> lines,
            Set<String> selectedMethods
    ) {
        var changed = true;
        while (changed) {
            changed = false;
            for (var selected : List.copyOf(selectedMethods)) {
                var block = blocks.get(selected);
                if (block == null) {
                    continue;
                }
                var matcher = METHOD_CALL.matcher(block.content(lines));
                while (matcher.find()) {
                    var called = matcher.group(1);
                    if (blocks.containsKey(called) && selectedMethods.add(called)) {
                        changed = true;
                    }
                }
            }
        }
    }

    private Map<String, MethodBlock> methodBlocks(List<String> lines) {
        var result = new LinkedHashMap<String, MethodBlock>();
        for (var index = 0; index < lines.size(); index++) {
            var matcher = METHOD_START.matcher(lines.get(index));
            if (!matcher.find()) {
                continue;
            }
            var depth = 0;
            var started = false;
            var end = index;
            for (var cursor = index; cursor < lines.size(); cursor++) {
                for (var character : lines.get(cursor).toCharArray()) {
                    if (character == '{') {
                        depth++;
                        started = true;
                    } else if (character == '}') {
                        depth--;
                    }
                }
                end = cursor;
                if (started && depth <= 0) {
                    break;
                }
            }
            result.putIfAbsent(matcher.group(1), new MethodBlock(matcher.group(1), index + 1, end + 1));
            index = Math.max(index, end);
        }
        return result;
    }

    private void expandDecoratorAndStatements(List<String> lines, Set<Integer> selected) {
        for (var lineNumber : List.copyOf(selected)) {
            var start = lineNumber;
            while (start > 1 && continuation(lines.get(start - 2))) {
                selected.add(--start);
            }
            var end = lineNumber;
            var balance = delimiterBalance(lines.get(end - 1));
            while (end < lines.size() && (balance > 0 || continuation(lines.get(end - 1)))) {
                balance += delimiterBalance(lines.get(end));
                selected.add(++end);
            }
        }
    }

    private boolean continuation(String line) {
        var trimmed = line.trim();
        return trimmed.startsWith("@") || trimmed.endsWith(",") || trimmed.endsWith("(")
                || trimmed.endsWith("{") || trimmed.endsWith("[") || !trimmed.endsWith(";") && trimmed.contains("=");
    }

    private int delimiterBalance(String line) {
        var result = 0;
        for (var character : line.toCharArray()) {
            if (character == '(' || character == '[' || character == '{') result++;
            if (character == ')' || character == ']' || character == '}') result--;
        }
        return result;
    }

    private List<LineRange> semanticTemplateRanges(String source) {
        var lines = lines(source);
        var selected = new LinkedHashSet<Integer>();
        var inComment = false;
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.contains("<!--")) inComment = true;
            if (!inComment && SEMANTIC_TEMPLATE.matcher(line).find()) {
                selected.add(index + 1);
                if (index > 0 && StringUtils.hasText(lines.get(index - 1))) selected.add(index);
                if (index + 1 < lines.size() && StringUtils.hasText(lines.get(index + 1))) selected.add(index + 2);
            }
            if (line.contains("-->")) inComment = false;
        }
        return mergeSelectedLines(selected, 0);
    }

    private List<LineRange> visualStateRanges(String source) {
        var lines = lines(source);
        var selected = new LinkedHashSet<Integer>();
        for (var index = 0; index < lines.size(); index++) {
            if (VISUAL_STATE.matcher(lines.get(index)).find()) {
                selected.add(index + 1);
                if (index > 0) selected.add(index);
            }
        }
        return mergeSelectedLines(selected, 1);
    }

    private List<LineRange> routeRanges(String source, List<LineRange> exactRanges) {
        var lines = lines(source);
        var selected = new LinkedHashSet<Integer>();
        for (var range : exactRanges) {
            var start = Math.max(1, Math.min(lines.size(), range.startLine()));
            var end = Math.max(start, Math.min(lines.size(), range.endLine()));
            for (var line = start; line <= end; line++) selected.add(line);
        }
        if (!selected.isEmpty()) {
            return mergeSelectedLines(selected, 1);
        }
        for (var index = 0; index < lines.size(); index++) {
            if (ROUTE_LINE.matcher(lines.get(index)).find() || lines.get(index).contains("Routes")) {
                selected.add(index + 1);
            }
        }
        return mergeSelectedLines(selected, 2);
    }

    private List<LineRange> nonBlankRanges(String source) {
        var selected = new LinkedHashSet<Integer>();
        var lines = lines(source);
        for (var index = 0; index < lines.size(); index++) {
            if (StringUtils.hasText(lines.get(index))) selected.add(index + 1);
        }
        return mergeSelectedLines(selected, 0);
    }

    private List<GitLabFrontendSourceSlice> slicesFromRanges(
            GitLabFrontendSourceFile file,
            List<LineRange> ranges,
            GitLabFrontendSourceSliceKind kind
    ) {
        var lines = lines(file.content());
        return ranges.stream().map(range -> slice(file, range, kind, null, content(lines, range))).toList();
    }

    private GitLabFrontendSourceSlice slice(
            GitLabFrontendSourceFile file,
            LineRange range,
            GitLabFrontendSourceSliceKind kind,
            String symbol,
            String content
    ) {
        var contentHash = sha256(content);
        var sliceId = stableId(file.path(), kind.name(), symbol, Integer.toString(range.startLine()),
                Integer.toString(range.endLine()), contentHash);
        return new GitLabFrontendSourceSlice(
                sliceId, file.path(), file.roles(), kind, symbol, range.startLine(), range.endLine(),
                content, content.length(), contentHash
        );
    }

    private GitLabFrontendSourceSliceKind kind(List<GitLabFrontendSourceRole> roles, String content) {
        if (roles.contains(GitLabFrontendSourceRole.AUTHORIZATION)) return GitLabFrontendSourceSliceKind.AUTHORIZATION_RULE;
        if (FORM_SLICE.matcher(content).find()) {
            return GitLabFrontendSourceSliceKind.FORM_RULE;
        }
        if (roles.contains(GitLabFrontendSourceRole.STATE_MANAGEMENT) || STATE_SLICE.matcher(content).find()) {
            return GitLabFrontendSourceSliceKind.STATE_FLOW;
        }
        if (roles.contains(GitLabFrontendSourceRole.BACKEND_CLIENT)
                || roles.contains(GitLabFrontendSourceRole.WEBSOCKET_STREAM)
                || BACKEND_SLICE.matcher(content).find()) {
            return GitLabFrontendSourceSliceKind.BACKEND_OPERATION;
        }
        if (content.contains("@Component") || content.matches("(?s).*\\bclass\\b.*")) {
            return GitLabFrontendSourceSliceKind.COMPONENT_CONTRACT;
        }
        return GitLabFrontendSourceSliceKind.COMPONENT_BEHAVIOR;
    }

    private Map<String, Set<String>> templateIdentifiers(Map<String, GitLabFrontendSourceFile> files) {
        var result = new LinkedHashMap<String, Set<String>>();
        for (var file : files.values()) {
            if (!file.path().endsWith(".html")) continue;
            var identifiers = new LinkedHashSet<String>();
            var matcher = TEMPLATE_EXPRESSION.matcher(file.content());
            while (matcher.find()) {
                for (var group = 1; group <= matcher.groupCount(); group++) {
                    var expression = matcher.group(group);
                    if (!StringUtils.hasText(expression)) continue;
                    var identifierMatcher = IDENTIFIER.matcher(expression);
                    while (identifierMatcher.find()) {
                        var identifier = identifierMatcher.group();
                        if (!IGNORED_TEMPLATE_IDENTIFIERS.contains(identifier)) identifiers.add(identifier);
                    }
                }
            }
            result.put(componentKey(file.path()), Set.copyOf(identifiers));
        }
        return result;
    }

    private java.util.Optional<String> componentTemplate(
            GitLabFrontendSourceFile component,
            Map<String, GitLabFrontendSourceFile> files
    ) {
        return java.util.Optional.ofNullable(templatePathForComponent(component.path(), files));
    }

    private String templatePathForComponent(String componentPath, Map<String, GitLabFrontendSourceFile> files) {
        var key = componentKey(componentPath);
        return files.keySet().stream()
                .filter(path -> path.endsWith(".html") && componentKey(path).equals(key))
                .findFirst()
                .orElse(null);
    }

    private String componentKey(String path) {
        if (path == null) return "";
        return path.replaceAll("\\.(component\\.)?(ts|html|scss|css)$", "");
    }

    private Set<String> methodNames(String source) {
        return methodBlocks(lines(source)).keySet();
    }

    private boolean containsIdentifier(String source, String identifier) {
        return StringUtils.hasText(identifier)
                && Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(identifier) + "(?![A-Za-z0-9_$])")
                .matcher(source).find();
    }

    private boolean identifiersOnLine(String line, Set<String> identifiers) {
        return identifiers.stream().anyMatch(identifier -> containsIdentifier(line, identifier));
    }

    private Map<String, List<LineRange>> routeSourceRanges(
            GitLabFrontendRouteNode screen,
            List<GitLabFrontendRouteNode> descendants
    ) {
        var result = new LinkedHashMap<String, LinkedHashSet<LineRange>>();
        addRouteSourceRange(result, screen != null ? screen.routeSource() : null);
        if (screen != null) {
            screen.configuration().forEach(configuration -> addRouteSourceRange(result, configuration.source()));
        }
        for (var descendant : descendants != null ? descendants : List.<GitLabFrontendRouteNode>of()) {
            addRouteSourceRange(result, descendant.routeSource());
            descendant.configuration().forEach(configuration -> addRouteSourceRange(result, configuration.source()));
        }
        var immutable = new LinkedHashMap<String, List<LineRange>>();
        result.forEach((path, ranges) -> immutable.put(path, List.copyOf(ranges)));
        return Map.copyOf(immutable);
    }

    private void addRouteSourceRange(
            Map<String, LinkedHashSet<LineRange>> ranges,
            GitLabFrontendSourceReference source
    ) {
        if (source == null || !StringUtils.hasText(source.path())
                || source.startLine() == null || source.endLine() == null) {
            return;
        }
        var path = GitLabFrontendTargetedSourceSession.normalize(source.path());
        ranges.computeIfAbsent(path, ignored -> new LinkedHashSet<>())
                .add(new LineRange(source.startLine(), source.endLine()));
    }

    private Set<String> memberCalls(String source, String importedSymbol) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(importedSymbol)) {
            return Set.of();
        }
        var aliases = new LinkedHashSet<String>();
        aliases.add(importedSymbol);
        var injected = Pattern.compile(
                "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*inject\\s*\\(\\s*"
                        + Pattern.quote(importedSymbol) + "\\b"
        ).matcher(source);
        while (injected.find()) aliases.add(injected.group(1));
        var constructorParameter = Pattern.compile(
                "(?:private|protected|public)(?:\\s+readonly)?\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*"
                        + Pattern.quote(importedSymbol) + "\\b"
        ).matcher(source);
        while (constructorParameter.find()) aliases.add(constructorParameter.group(1));

        var result = new LinkedHashSet<String>();
        for (var alias : aliases) {
            var call = Pattern.compile(
                    "(?:this\\.)?" + Pattern.quote(alias) + "\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\("
            ).matcher(source);
            while (call.find()) result.add(call.group(1));
        }
        return Set.copyOf(result);
    }

    private void selectUsedDataDeclarations(
            List<String> lines,
            Set<String> usedSymbols,
            Set<Integer> selected
    ) {
        for (var symbol : usedSymbols) {
            var declaration = Pattern.compile(
                    "\\b(?:export\\s+)?(?:interface|type|enum)\\s+" + Pattern.quote(symbol) + "\\b"
            );
            for (var index = 0; index < lines.size(); index++) {
                if (!declaration.matcher(lines.get(index)).find()) continue;
                var end = declarationEnd(lines, index);
                for (var line = index + 1; line <= end + 1; line++) selected.add(line);
                break;
            }
        }
    }

    private int declarationEnd(List<String> lines, int startIndex) {
        var depth = 0;
        var started = false;
        for (var index = startIndex; index < lines.size(); index++) {
            for (var character : lines.get(index).toCharArray()) {
                if (character == '{') {
                    depth++;
                    started = true;
                } else if (character == '}') {
                    depth--;
                }
            }
            if ((started && depth <= 0) || !started && lines.get(index).contains(";")) return index;
        }
        return startIndex;
    }

    private List<String> affectedCategories(
            List<GitLabFrontendSourceRole> ownerRoles,
            String symbol,
            List<String> candidates
    ) {
        var token = ((symbol != null ? symbol : "") + " " + String.join(" ", candidates)).toLowerCase(Locale.ROOT);
        var result = new LinkedHashSet<String>();
        if (token.matches(".*(guard|auth|role|permission|keycloak).*")
                || ownerRoles.contains(GitLabFrontendSourceRole.AUTHORIZATION)) result.add("AUTHORIZATION");
        if (token.matches(".*(form|control|validator|field|calculation).*")
                || ownerRoles.contains(GitLabFrontendSourceRole.FORM_LOGIC)) result.add("FORMS");
        if (token.matches(".*(selector|effect|reducer|store|state).*")
                || ownerRoles.contains(GitLabFrontendSourceRole.STATE_MANAGEMENT)) result.add("STATE");
        if (token.matches(".*(client|service|api|http|socket).*")
                || ownerRoles.contains(GitLabFrontendSourceRole.BACKEND_CLIENT)
                || ownerRoles.contains(GitLabFrontendSourceRole.WEBSOCKET_STREAM)) result.add("BACKEND_SERVICES");
        if (token.matches(".*(component|dialog|modal|view|template).*")
                || ownerRoles.contains(GitLabFrontendSourceRole.VIEW_COMPONENT)
                || ownerRoles.contains(GitLabFrontendSourceRole.CHILD_COMPONENT)) {
            result.add("VIEW");
            result.add("TEMPLATE");
        }
        return result.isEmpty() ? List.of("VIEW") : List.copyOf(result);
    }

    private boolean repositoryOwned(String moduleSpecifier) {
        return StringUtils.hasText(moduleSpecifier)
                && (moduleSpecifier.startsWith(".") || !moduleSpecifier.startsWith("@angular/")
                && !moduleSpecifier.startsWith("rxjs") && !moduleSpecifier.startsWith("@ngrx/")
                && !moduleSpecifier.startsWith("@material/"));
    }

    private void addRelation(
            Map<String, GitLabFrontendUseCaseRelation> relations,
            String from,
            String to,
            GitLabFrontendUseCaseRelationKind kind,
            String symbol,
            GitLabFrontendSignalConfidence confidence,
            GitLabFrontendSourceReference source
    ) {
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) return;
        var key = from + "|" + to + "|" + kind + "|" + symbol;
        relations.putIfAbsent(key, new GitLabFrontendUseCaseRelation(from, to, kind, symbol, confidence, source));
    }

    private List<LineRange> mergeSelectedLines(Set<Integer> selected, int adjacency) {
        var sorted = selected.stream().filter(line -> line > 0).sorted().toList();
        if (sorted.isEmpty()) return List.of();
        var result = new ArrayList<LineRange>();
        var start = sorted.get(0);
        var end = start;
        for (var line : sorted.subList(1, sorted.size())) {
            if (line <= end + adjacency + 1) {
                end = line;
            } else {
                result.add(new LineRange(start, end));
                start = line;
                end = line;
            }
        }
        result.add(new LineRange(start, end));
        return List.copyOf(result);
    }

    private List<String> lines(String content) {
        return List.of((content != null ? content : "").split("\\R", -1));
    }

    private String content(List<String> lines, LineRange range) {
        return String.join(System.lineSeparator(), lines.subList(range.startLine() - 1, range.endLine())).strip();
    }

    private String stableId(String... values) {
        return "frontend-" + sha256(String.join("\u001f", values)).substring(0, 20);
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value != null ? value : "").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record LineRange(int startLine, int endLine) {
    }

    private record MethodBlock(String name, int startLine, int endLine) {
        String content(List<String> lines) {
            return String.join(System.lineSeparator(), lines.subList(startLine - 1, endLine));
        }
    }
}
