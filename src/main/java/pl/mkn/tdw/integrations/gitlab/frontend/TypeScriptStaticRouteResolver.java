package pl.mkn.tdw.integrations.gitlab.frontend;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

final class TypeScriptStaticRouteResolver implements AngularRouteSourceParser.StaticStringResolver {

    private static final int MAX_CONFIGURATION_FILES = 20;
    private static final int MAX_RESOLUTION_DEPTH = 16;
    private static final Pattern PROPERTY_CHAIN = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+"
    );
    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "(?s)import\\s*\\{([^}]+)}\\s*from\\s*['\"]([^'\"]+)['\"]"
    );
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    private final List<String> inventory;
    private final Set<String> inventorySet;
    private final Function<String, String> sourceReader;
    private final List<PathAlias> pathAliases;
    private final Map<String, String> sourceCache = new LinkedHashMap<>();

    TypeScriptStaticRouteResolver(List<String> inventory, Function<String, String> sourceReader) {
        this.inventory = List.copyOf(inventory);
        this.inventorySet = Set.copyOf(inventory);
        this.sourceReader = sourceReader;
        this.pathAliases = loadPathAliases();
    }

    @Override
    public AngularRouteSourceParser.StaticStringResolution resolve(
            String sourcePath,
            String source,
            String expression
    ) {
        var result = evaluate(sourcePath, source, expression, new LinkedHashSet<>(), 0);
        if (result.value() != null) {
            return new AngularRouteSourceParser.StaticStringResolution(result.value(), null);
        }
        return new AngularRouteSourceParser.StaticStringResolution(
                null,
                result.limitation() != null
                        ? result.limitation()
                        : "Static route expression could not be resolved in " + sourcePath + "."
        );
    }

    List<String> resolveImportPaths(String sourcePath, String importPath) {
        if (!StringUtils.hasText(importPath)) {
            return List.of();
        }
        var bases = new LinkedHashSet<String>();
        if (importPath.startsWith(".")) {
            bases.add(normalizeRelativePath(parentPath(sourcePath), importPath));
        } else {
            for (var alias : pathAliases) {
                var target = alias.resolve(importPath);
                if (target != null) {
                    bases.add(target);
                }
            }
        }

        var candidates = new LinkedHashSet<String>();
        for (var base : bases) {
            addImportCandidates(candidates, base);
        }
        return inventory.stream().filter(candidates::contains).distinct().sorted().toList();
    }

    private ValueResolution evaluate(
            String sourcePath,
            String source,
            String expression,
            LinkedHashSet<String> resolutionStack,
            int depth
    ) {
        if (depth > MAX_RESOLUTION_DEPTH) {
            return unresolved("Static route expression exceeded the bounded resolution depth in "
                    + sourcePath + ".");
        }
        var normalized = stripTypeAssertions(expression);
        var literal = quotedLiteral(normalized);
        if (literal != null) {
            return resolved(literal);
        }
        if (normalized.startsWith("`") && normalized.endsWith("`")) {
            return evaluateTemplate(sourcePath, source, normalized, resolutionStack, depth);
        }

        var concatenated = splitTopLevel(normalized, '+');
        if (concatenated.size() > 1) {
            var value = new StringBuilder();
            for (var part : concatenated) {
                var partResolution = evaluate(sourcePath, source, part, resolutionStack, depth + 1);
                if (partResolution.value() == null) {
                    return partResolution;
                }
                value.append(partResolution.value());
            }
            return resolved(value.toString());
        }

        if (!PROPERTY_CHAIN.matcher(normalized).matches()) {
            return unresolved("Route expression '" + boundedExpression(normalized)
                    + "' is not a supported static string expression in " + sourcePath + ".");
        }

        var key = sourcePath + "|" + normalized;
        if (!resolutionStack.add(key)) {
            return unresolved("Static route expression cycle was detected in " + sourcePath + ".");
        }
        try {
            var segments = normalized.split("\\.");
            var root = segments[0];
            var local = resolveConstObject(sourcePath, source, root, segments, 1, resolutionStack, depth);
            if (hasStaticDeclaration(source, root)) {
                return local;
            }

            var binding = imports(source).get(root);
            if (binding == null) {
                return unresolved("Static route object '" + root + "' could not be found in "
                        + sourcePath + ".");
            }
            var targets = resolveImportPaths(sourcePath, binding.importPath());
            if (targets.size() != 1) {
                return unresolved(targets.isEmpty()
                        ? "Static route model import '" + binding.importPath()
                                + "' could not be mapped to repository source from " + sourcePath + "."
                        : "Static route model import '" + binding.importPath()
                                + "' is ambiguous in the bounded repository inventory.");
            }
            var targetPath = targets.get(0);
            var targetSource = read(targetPath);
            if (targetSource == null) {
                return unresolved("Static route model source could not be read: " + targetPath + ".");
            }
            return resolveConstObject(
                    targetPath,
                    targetSource,
                    binding.exportedName(),
                    segments,
                    1,
                    resolutionStack,
                    depth + 1
            );
        } finally {
            resolutionStack.remove(key);
        }
    }

    private ValueResolution resolveConstObject(
            String sourcePath,
            String source,
            String constName,
            String[] segments,
            int segmentIndex,
            LinkedHashSet<String> resolutionStack,
            int depth
    ) {
        var initializer = constInitializer(source, constName);
        if (initializer == null) {
            initializer = enumInitializer(source, constName);
        }
        if (initializer == null) {
            return unresolved("Static route object '" + constName + "' was not found in "
                    + sourcePath + ".");
        }
        var current = initializer;
        for (var index = segmentIndex; index < segments.length; index++) {
            current = objectProperty(current, segments[index]);
            if (current == null) {
                return unresolved("Static route property '" + String.join(".", segments)
                        + "' was not found in " + sourcePath + ".");
            }
        }
        return evaluate(sourcePath, source, current, resolutionStack, depth + 1);
    }

    private ValueResolution evaluateTemplate(
            String sourcePath,
            String source,
            String template,
            LinkedHashSet<String> resolutionStack,
            int depth
    ) {
        var body = template.substring(1, template.length() - 1);
        var result = new StringBuilder();
        for (var index = 0; index < body.length();) {
            var expressionStart = body.indexOf("${", index);
            if (expressionStart < 0) {
                result.append(body.substring(index));
                break;
            }
            result.append(body, index, expressionStart);
            var expressionEnd = matchingBrace(body, expressionStart + 1);
            if (expressionEnd < 0) {
                return unresolved("Static route template has an unclosed interpolation in "
                        + sourcePath + ".");
            }
            var nested = body.substring(expressionStart + 2, expressionEnd);
            var nestedResolution = evaluate(sourcePath, source, nested, resolutionStack, depth + 1);
            if (nestedResolution.value() == null) {
                return nestedResolution;
            }
            result.append(nestedResolution.value());
            index = expressionEnd + 1;
        }
        return resolved(result.toString());
    }

    private String constInitializer(String source, String constName) {
        var declaration = Pattern.compile(
                "(?s)(?:export\\s+)?const\\s+" + Pattern.quote(constName)
                        + "\\b\\s*(?::[^=;]+)?=\\s*"
        ).matcher(source);
        if (!declaration.find()) {
            return null;
        }
        var start = declaration.end();
        if (start >= source.length()) {
            return null;
        }
        var first = source.charAt(start);
        if (first == '{') {
            var end = matchingDelimiter(source, start, '{', '}');
            return end >= 0 ? source.substring(start, end + 1) : null;
        }
        var end = nextTopLevel(source, start, ';');
        return source.substring(start, end).trim();
    }

    private String enumInitializer(String source, String enumName) {
        var declaration = Pattern.compile(
                "(?s)(?:export\\s+)?(?:const\\s+)?enum\\s+" + Pattern.quote(enumName)
                        + "\\b[^\\{]*\\{"
        ).matcher(source);
        if (!declaration.find()) {
            return null;
        }
        var start = declaration.end() - 1;
        var end = matchingDelimiter(source, start, '{', '}');
        return end >= 0 ? source.substring(start, end + 1) : null;
    }

    private boolean hasStaticDeclaration(String source, String name) {
        return constInitializer(source, name) != null || enumInitializer(source, name) != null;
    }

    private String objectProperty(String objectExpression, String propertyName) {
        var expression = objectExpression.trim();
        if (!expression.startsWith("{")) {
            return null;
        }
        var end = matchingDelimiter(expression, 0, '{', '}');
        if (end < 0) {
            return null;
        }
        for (var segment : splitTopLevel(expression.substring(1, end), ',')) {
            var separator = topLevelColon(segment);
            if (separator < 0) {
                separator = topLevelEquals(segment);
            }
            if (separator < 0) {
                continue;
            }
            var name = unquote(segment.substring(0, separator).trim());
            if (propertyName.equals(name)) {
                return segment.substring(separator + 1).trim();
            }
        }
        return null;
    }

    private Map<String, ImportBinding> imports(String source) {
        var result = new LinkedHashMap<String, ImportBinding>();
        var matcher = NAMED_IMPORT.matcher(source);
        while (matcher.find()) {
            for (var imported : matcher.group(1).split(",")) {
                var normalized = imported.trim().replaceFirst("^type\\s+", "");
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                var alias = normalized.split("\\s+as\\s+");
                var exportedName = alias[0].trim();
                var localName = alias[alias.length - 1].trim();
                result.put(localName, new ImportBinding(exportedName, matcher.group(2)));
            }
        }
        return result;
    }

    private List<PathAlias> loadPathAliases() {
        var aliases = new ArrayList<PathAlias>();
        inventory.stream()
                .filter(path -> fileName(path).startsWith("tsconfig") && path.endsWith(".json"))
                .sorted((left, right) -> Integer.compare(pathDepth(left), pathDepth(right)))
                .limit(MAX_CONFIGURATION_FILES)
                .forEach(path -> loadPathAliases(path, aliases));
        return List.copyOf(aliases);
    }

    private void loadPathAliases(String configPath, List<PathAlias> aliases) {
        var source = read(configPath);
        if (source == null) {
            return;
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(source);
            var compilerOptions = root.path("compilerOptions");
            var baseUrl = compilerOptions.path("baseUrl").isTextual()
                    ? compilerOptions.path("baseUrl").asText()
                    : ".";
            var mappingBase = normalizeRelativePath(parentPath(configPath), baseUrl);
            var paths = compilerOptions.path("paths");
            if (!paths.isObject()) {
                return;
            }
            paths.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isArray()) {
                    return;
                }
                entry.getValue().forEach(target -> {
                    if (target.isTextual()) {
                        aliases.add(new PathAlias(
                                entry.getKey(),
                                normalizeRelativePath(mappingBase, target.asText())
                        ));
                    }
                });
            });
        } catch (Exception ignored) {
            // An unreadable JSONC config remains an explicit unresolved import at the route expression.
        }
    }

    private String read(String path) {
        if (sourceCache.containsKey(path)) {
            return sourceCache.get(path);
        }
        var source = sourceReader.apply(path);
        sourceCache.put(path, source);
        return source;
    }

    private void addImportCandidates(Set<String> candidates, String base) {
        candidates.add(base);
        candidates.add(base + ".ts");
        candidates.add(base + ".component.ts");
        candidates.add(base + ".routes.ts");
        candidates.add(base + ".module.ts");
        candidates.add(base + "/index.ts");
    }

    private String stripTypeAssertions(String expression) {
        var normalized = expression.trim();
        normalized = normalized.replaceFirst("\\s+as\\s+(?:const|string)\\s*$", "").trim();
        while (normalized.startsWith("(") && normalized.endsWith(")")
                && matchingDelimiter(normalized, 0, '(', ')') == normalized.length() - 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String quotedLiteral(String expression) {
        if (expression.length() < 2) {
            return null;
        }
        var quote = expression.charAt(0);
        return (quote == '\'' || quote == '"') && expression.charAt(expression.length() - 1) == quote
                ? expression.substring(1, expression.length() - 1)
                : null;
    }

    private List<String> splitTopLevel(String source, char delimiter) {
        var values = new ArrayList<String>();
        var start = 0;
        var state = new ScanState();
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            if (state.consume(source, index)) {
                continue;
            }
            state.adjustDepth(current);
            if (current == delimiter && state.atTopLevel()) {
                values.add(source.substring(start, index).trim());
                start = index + 1;
            }
        }
        values.add(source.substring(start).trim());
        return values;
    }

    private int topLevelColon(String source) {
        var state = new ScanState();
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            if (state.consume(source, index)) {
                continue;
            }
            if (current == ':' && state.atTopLevel()) {
                return index;
            }
            state.adjustDepth(current);
        }
        return -1;
    }

    private int topLevelEquals(String source) {
        var state = new ScanState();
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            if (state.consume(source, index)) {
                continue;
            }
            if (current == '=' && state.atTopLevel()) {
                return index;
            }
            state.adjustDepth(current);
        }
        return -1;
    }

    private int matchingDelimiter(String source, int start, char open, char close) {
        var depth = 0;
        var state = new ScanState();
        for (var index = start; index < source.length(); index++) {
            var current = source.charAt(index);
            if (state.consume(source, index)) {
                continue;
            }
            if (current == open) {
                depth++;
            } else if (current == close && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private int matchingBrace(String source, int openBraceIndex) {
        return matchingDelimiter(source, openBraceIndex, '{', '}');
    }

    private int nextTopLevel(String source, int start, char delimiter) {
        var state = new ScanState();
        for (var index = start; index < source.length(); index++) {
            var current = source.charAt(index);
            if (state.consume(source, index)) {
                continue;
            }
            if (current == delimiter && state.atTopLevel()) {
                return index;
            }
            state.adjustDepth(current);
        }
        return source.length();
    }

    private String normalizeRelativePath(String parent, String relative) {
        var combined = StringUtils.hasText(parent) ? parent + "/" + relative : relative;
        var stack = new ArrayList<String>();
        for (var segment : combined.replace('\\', '/').split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                stack.add(segment);
            }
        }
        return String.join("/", stack);
    }

    private String parentPath(String path) {
        var index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(0, index) : "";
    }

    private String fileName(String path) {
        var index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private int pathDepth(String path) {
        return (int) path.chars().filter(character -> character == '/').count();
    }

    private String unquote(String value) {
        return value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")))
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private String boundedExpression(String expression) {
        return expression.length() <= 120 ? expression : expression.substring(0, 120) + "...";
    }

    private ValueResolution resolved(String value) {
        return new ValueResolution(value, null);
    }

    private ValueResolution unresolved(String limitation) {
        return new ValueResolution(null, limitation);
    }

    private record ValueResolution(String value, String limitation) {
    }

    private record ImportBinding(String exportedName, String importPath) {
    }

    private record PathAlias(String pattern, String targetPattern) {
        private String resolve(String importPath) {
            var wildcard = pattern.indexOf('*');
            if (wildcard < 0) {
                return pattern.equals(importPath) ? targetPattern : null;
            }
            var prefix = pattern.substring(0, wildcard);
            var suffix = pattern.substring(wildcard + 1);
            if (!importPath.startsWith(prefix) || !importPath.endsWith(suffix)
                    || importPath.length() < prefix.length() + suffix.length()) {
                return null;
            }
            var value = importPath.substring(prefix.length(), importPath.length() - suffix.length());
            return targetPattern.replace("*", value);
        }
    }

    private static final class ScanState {
        private int round;
        private int square;
        private int curly;
        private char quote;
        private boolean escaped;
        private boolean lineComment;
        private boolean blockComment;

        private boolean consume(String source, int index) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                }
                return true;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                }
                return true;
            }
            if (quote != '\0') {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = '\0';
                }
                return true;
            }
            if (current == '/' && next == '/') {
                lineComment = true;
                return true;
            }
            if (current == '/' && next == '*') {
                blockComment = true;
                return true;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                return true;
            }
            return false;
        }

        private void adjustDepth(char current) {
            switch (current) {
                case '(' -> round++;
                case ')' -> round--;
                case '[' -> square++;
                case ']' -> square--;
                case '{' -> curly++;
                case '}' -> curly--;
                default -> {
                }
            }
        }

        private boolean atTopLevel() {
            return round == 0 && square == 0 && curly == 0 && quote == '\0'
                    && !lineComment && !blockComment;
        }
    }
}
