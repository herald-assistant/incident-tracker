package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class AngularRouteSourceParser {

    private static final Pattern ROUTE_ARRAY = Pattern.compile(
            "(?s)(?:\\bRoutes\\b\\s*=|RouterModule\\.for(?:Root|Child)\\s*\\(|provideRouter\\s*\\()\\s*(\\[)"
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("^\\s*(['\"])(.*?)\\1\\s*$", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern DYNAMIC_IMPORT = Pattern.compile("import\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern THEN_MEMBER_SYMBOL = Pattern.compile(
            "\\.then\\s*\\([^=]*=>\\s*[A-Za-z_$][A-Za-z0-9_$]*\\.([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern THEN_DESTRUCTURED_SYMBOL = Pattern.compile(
            "\\.then\\s*\\(\\s*\\(?\\s*\\{\\s*([A-Za-z_$][A-Za-z0-9_$]*)"
    );

    ParseResult parse(String sourcePath, String source) {
        return parse(sourcePath, source, (path, content, expression) ->
                new StaticStringResolution(null, null));
    }

    ParseResult parse(String sourcePath, String source, StaticStringResolver stringResolver) {
        var routes = new ArrayList<ParsedRoute>();
        var limitations = new LinkedHashSet<String>();
        var matcher = ROUTE_ARRAY.matcher(source);
        var foundArray = false;
        while (matcher.find()) {
            var arrayStart = matcher.start(1);
            var arrayEnd = matchingDelimiter(source, arrayStart, '[', ']');
            if (arrayEnd < 0) {
                limitations.add("Route array could not be parsed because its closing bracket was not found.");
                continue;
            }
            foundArray = true;
            parseArray(
                    sourcePath,
                    source,
                    arrayStart,
                    arrayEnd,
                    "",
                    false,
                    List.of(),
                    null,
                    routes,
                    limitations,
                    stringResolver
            );
        }
        if (!foundArray && likelyRouteSource(source)) {
            limitations.add("Route source uses a non-literal or unsupported route definition.");
        }
        return new ParseResult(routes, List.copyOf(limitations));
    }

    ParseResult parseCollection(
            String sourcePath,
            String source,
            String collectionSymbol,
            StaticStringResolver stringResolver
    ) {
        return parseCollection(sourcePath, source, collectionSymbol, "", false, List.of(), stringResolver);
    }

    ParseResult parseCollection(
            String sourcePath,
            String source,
            String collectionSymbol,
            String parentPath,
            boolean inheritedLazy,
            List<String> inheritedGuards,
            StaticStringResolver stringResolver
    ) {
        if (!StringUtils.hasText(collectionSymbol)) {
            return parse(sourcePath, source, stringResolver);
        }
        var declaration = Pattern.compile(
                "(?s)(?:export\\s+)?const\\s+" + Pattern.quote(collectionSymbol)
                        + "\\b\\s*(?::[^=;]+)?=\\s*"
        ).matcher(source);
        if (!declaration.find()) {
            return new ParseResult(
                    List.of(),
                    List.of("Route collection '" + collectionSymbol + "' was not found in " + sourcePath + ".")
            );
        }
        var arrayStart = firstNonWhitespace(source, declaration.end());
        if (arrayStart < 0 || source.charAt(arrayStart) != '[') {
            return new ParseResult(
                    List.of(),
                    List.of("Route collection '" + collectionSymbol + "' is not a static array in "
                            + sourcePath + ".")
            );
        }
        var arrayEnd = matchingDelimiter(source, arrayStart, '[', ']');
        if (arrayEnd < 0) {
            return new ParseResult(
                    List.of(),
                    List.of("Route collection '" + collectionSymbol + "' has no closing bracket in "
                            + sourcePath + ".")
            );
        }
        var routes = new ArrayList<ParsedRoute>();
        var limitations = new LinkedHashSet<String>();
        parseArray(
                sourcePath,
                source,
                arrayStart,
                arrayEnd,
                parentPath,
                inheritedLazy,
                inheritedGuards,
                null,
                routes,
                limitations,
                stringResolver
        );
        return new ParseResult(routes, List.copyOf(limitations));
    }

    private void parseArray(
            String sourcePath,
            String source,
            int arrayStart,
            int arrayEnd,
            String parentPath,
            boolean inheritedLazy,
            List<String> inheritedGuards,
            Integer parentSourceOffset,
            List<ParsedRoute> routes,
            LinkedHashSet<String> limitations,
            StaticStringResolver stringResolver
    ) {
        for (var span : topLevelSpans(source, arrayStart + 1, arrayEnd, '{', '}')) {
            var properties = properties(source.substring(span.start() + 1, span.end()));
            var pathExpression = properties.value("path");
            var pathResolution = staticString(sourcePath, source, pathExpression, stringResolver);
            var path = pathResolution.value();
            if (pathExpression != null && path == null) {
                limitations.add(pathResolution.limitation() != null
                        ? pathResolution.limitation()
                        : "Dynamic route path was not resolved in " + sourcePath + ".");
            }
            var fullPath = joinRoute(parentPath, path);
            var redirectExpression = properties.value("redirectTo");
            var redirectResolution = staticString(
                    sourcePath,
                    source,
                    redirectExpression,
                    stringResolver
            );
            var redirect = redirectResolution.value();
            if (redirectExpression != null && redirect == null && redirectResolution.limitation() != null) {
                limitations.add(redirectResolution.limitation());
            }
            var componentExpression = properties.value("component");
            var loadComponentExpression = properties.value("loadComponent");
            var loadChildrenExpression = properties.value("loadChildren");
            var children = properties.value("children");
            var outletResolution = staticString(sourcePath, source, properties.value("outlet"), stringResolver);
            var outlet = StringUtils.hasText(outletResolution.value()) ? outletResolution.value() : "primary";
            var guards = new LinkedHashSet<>(inheritedGuards);
            guards.addAll(guards(properties));
            var lazy = inheritedLazy || loadComponentExpression != null || loadChildrenExpression != null;
            var line = lineNumber(source, span.start());
            var configuration = configuration(properties, sourcePath, source, line, stringResolver);

            if (pathExpression != null || redirect != null || componentExpression != null
                    || loadComponentExpression != null || loadChildrenExpression != null) {
                routes.add(new ParsedRoute(
                        path,
                        fullPath,
                        parentPath,
                        redirect,
                        identifier(componentExpression),
                        loadComponentExpression != null,
                        importPath(loadComponentExpression),
                        importedSymbol(loadComponentExpression),
                        loadChildrenExpression != null,
                        importPath(loadChildrenExpression),
                        importedSymbol(loadChildrenExpression),
                        children != null,
                        exactIdentifier(children),
                        outlet,
                        configuration,
                        List.copyOf(guards),
                        lazy,
                        sourcePath,
                        line,
                        span.start(),
                        parentSourceOffset,
                        pathExpression != null
                ));
            }

            if (children != null) {
                var childArrayStart = firstNonWhitespace(children, 0);
                if (childArrayStart >= 0 && children.charAt(childArrayStart) == '[') {
                    var absoluteChildStart = source.indexOf(children, span.start() + 1) + childArrayStart;
                    var absoluteChildEnd = matchingDelimiter(source, absoluteChildStart, '[', ']');
                    if (absoluteChildEnd >= 0) {
                        parseArray(
                                sourcePath,
                                source,
                                absoluteChildStart,
                                absoluteChildEnd,
                                fullPath,
                                lazy,
                                List.copyOf(guards),
                                span.start(),
                                routes,
                                limitations,
                                stringResolver
                        );
                    }
                } else {
                    if (exactIdentifier(children) == null) {
                        limitations.add("Dynamic children route definition was not resolved in " + sourcePath + ".");
                    }
                }
            }
        }

        var body = source.substring(arrayStart + 1, arrayEnd);
        if (containsTopLevelSpread(body)) {
            limitations.add("Spread route definitions are runtime-dependent and were not expanded in " + sourcePath + ".");
        }
    }

    private List<GitLabFrontendRouteConfiguration> configuration(
            Properties properties,
            String sourcePath,
            String source,
            int line,
            StaticStringResolver stringResolver
    ) {
        var result = new ArrayList<GitLabFrontendRouteConfiguration>();
        addConfiguration(result, properties, "canActivate", GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "canActivateChild",
                GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE_CHILD,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "canDeactivate",
                GitLabFrontendRouteConfigurationKind.CAN_DEACTIVATE,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "canMatch", GitLabFrontendRouteConfigurationKind.CAN_MATCH,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "canLoad", GitLabFrontendRouteConfigurationKind.CAN_LOAD,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "resolve", GitLabFrontendRouteConfigurationKind.RESOLVE,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "data", GitLabFrontendRouteConfigurationKind.DATA,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "title", GitLabFrontendRouteConfigurationKind.TITLE,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "providers", GitLabFrontendRouteConfigurationKind.PROVIDERS,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "pathMatch", GitLabFrontendRouteConfigurationKind.PATH_MATCH,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "outlet", GitLabFrontendRouteConfigurationKind.OUTLET,
                sourcePath, source, line, stringResolver);
        addConfiguration(result, properties, "runGuardsAndResolvers",
                GitLabFrontendRouteConfigurationKind.RUN_GUARDS_AND_RESOLVERS,
                sourcePath, source, line, stringResolver);
        return List.copyOf(result);
    }

    private void addConfiguration(
            List<GitLabFrontendRouteConfiguration> result,
            Properties properties,
            String key,
            GitLabFrontendRouteConfigurationKind kind,
            String sourcePath,
            String source,
            int line,
            StaticStringResolver stringResolver
    ) {
        var expression = properties.value(key);
        if (!StringUtils.hasText(expression)) {
            return;
        }
        var stringLike = kind == GitLabFrontendRouteConfigurationKind.TITLE
                || kind == GitLabFrontendRouteConfigurationKind.PATH_MATCH
                || kind == GitLabFrontendRouteConfigurationKind.OUTLET
                || kind == GitLabFrontendRouteConfigurationKind.RUN_GUARDS_AND_RESOLVERS;
        var staticResolution = stringLike
                ? staticString(sourcePath, source, expression, stringResolver)
                : new StaticStringResolution(null, null);
        var staticValue = staticResolution.value();
        if (staticValue == null && (expression.trim().startsWith("{")
                || expression.trim().startsWith("[")
                || expression.trim().matches("(?:true|false|null|-?\\d+(?:\\.\\d+)?)"))) {
            staticValue = bounded(expression.trim());
        }
        var dynamic = expression.contains("=>") || expression.contains("import(");
        var limitation = staticResolution.limitation();
        result.add(new GitLabFrontendRouteConfiguration(
                kind,
                key,
                referencedSymbols(expression),
                staticValue,
                dynamic || limitation != null
                        ? GitLabFrontendDiscoveryStatus.PARTIAL
                        : GitLabFrontendDiscoveryStatus.RESOLVED,
                new GitLabFrontendSourceReference(sourcePath, null, line, line),
                limitation != null ? List.of(limitation) : List.of()
        ));
    }

    private List<String> referencedSymbols(String expression) {
        var excluded = Set.of(
                "true", "false", "null", "undefined", "return", "const", "let", "new",
                "inject", "import", "then", "path", "data", "title"
        );
        var result = new LinkedHashSet<String>();
        var matcher = IDENTIFIER.matcher(expression.replaceAll("(['\"])(?:\\\\.|(?!\\1).)*\\1", " "));
        while (matcher.find()) {
            var value = matcher.group();
            if (!excluded.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private String bounded(String value) {
        return value.length() <= 240 ? value : value.substring(0, 240) + "...";
    }

    private Properties properties(String objectBody) {
        var names = new ArrayList<String>();
        var values = new ArrayList<String>();
        for (var segment : splitTopLevel(objectBody, ',')) {
            var colon = topLevelColon(segment);
            if (colon < 0) {
                continue;
            }
            var name = segment.substring(0, colon).trim().replace("'", "").replace("\"", "");
            if (IDENTIFIER.matcher(name).matches()) {
                names.add(name);
                values.add(segment.substring(colon + 1).trim());
            }
        }
        return new Properties(names, values);
    }

    private List<String> guards(Properties properties) {
        var values = new LinkedHashSet<String>();
        for (var property : List.of("canActivate", "canActivateChild", "canMatch", "canLoad", "canDeactivate")) {
            var expression = properties.value(property);
            if (expression == null) {
                continue;
            }
            var matcher = IDENTIFIER.matcher(expression);
            while (matcher.find()) {
                var identifier = matcher.group();
                if (!List.of("true", "false", "inject", "return").contains(identifier)) {
                    values.add(identifier);
                }
            }
        }
        return List.copyOf(values);
    }

    private String identifier(String expression) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var matcher = IDENTIFIER.matcher(expression.trim());
        return matcher.find() ? matcher.group() : null;
    }

    private String exactIdentifier(String expression) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var normalized = expression.trim();
        return IDENTIFIER.matcher(normalized).matches() ? normalized : null;
    }

    private String importPath(String expression) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var matcher = DYNAMIC_IMPORT.matcher(expression);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String importedSymbol(String expression) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var member = THEN_MEMBER_SYMBOL.matcher(expression);
        if (member.find()) {
            return member.group(1);
        }
        var destructured = THEN_DESTRUCTURED_SYMBOL.matcher(expression);
        if (destructured.find()) {
            return destructured.group(1);
        }
        return !expression.contains(".then") && DYNAMIC_IMPORT.matcher(expression).find()
                ? "default"
                : null;
    }

    private String stringLiteral(String expression) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var matcher = STRING_LITERAL.matcher(expression);
        return matcher.matches() ? matcher.group(2) : null;
    }

    private StaticStringResolution staticString(
            String sourcePath,
            String source,
            String expression,
            StaticStringResolver resolver
    ) {
        if (expression == null) {
            return new StaticStringResolution(null, null);
        }
        var literal = stringLiteral(expression);
        if (literal != null) {
            return new StaticStringResolution(literal, null);
        }
        var resolved = resolver.resolve(sourcePath, source, expression);
        return resolved != null ? resolved : new StaticStringResolution(null, null);
    }

    private boolean likelyRouteSource(String source) {
        return source.contains("Routes") || source.contains("RouterModule.forRoot")
                || source.contains("RouterModule.forChild") || source.contains("provideRouter");
    }

    private boolean containsTopLevelSpread(String source) {
        return splitTopLevel(source, ',').stream().map(String::trim).anyMatch(value -> value.startsWith("..."));
    }

    private String joinRoute(String parent, String child) {
        var left = normalizeRoute(parent);
        var right = normalizeRoute(child);
        if (!StringUtils.hasText(left)) {
            return StringUtils.hasText(right) ? "/" + right : "/";
        }
        if (!StringUtils.hasText(right)) {
            return "/" + left;
        }
        return "/" + left + "/" + right;
    }

    private String normalizeRoute(String value) {
        if (!StringUtils.hasText(value) || "/".equals(value.trim())) {
            return "";
        }
        var normalized = value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<Span> topLevelSpans(String source, int start, int end, char open, char close) {
        var spans = new ArrayList<Span>();
        var index = start;
        while (index < end) {
            index = skipWhitespaceAndCommas(source, index, end);
            if (index >= end) {
                break;
            }
            if (source.charAt(index) == open) {
                var blockEnd = matchingDelimiter(source, index, open, close);
                if (blockEnd < 0 || blockEnd > end) {
                    break;
                }
                spans.add(new Span(index, blockEnd));
                index = blockEnd + 1;
            } else {
                index = nextTopLevelComma(source, index, end) + 1;
            }
        }
        return spans;
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
                values.add(source.substring(start, index));
                start = index + 1;
            }
        }
        values.add(source.substring(start));
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

    private int nextTopLevelComma(String source, int start, int end) {
        var state = new ScanState();
        for (var index = start; index < end; index++) {
            var current = source.charAt(index);
            if (state.consume(source, index)) {
                continue;
            }
            state.adjustDepth(current);
            if (current == ',' && state.atTopLevel()) {
                return index;
            }
        }
        return end;
    }

    private int skipWhitespaceAndCommas(String source, int index, int end) {
        while (index < end && (Character.isWhitespace(source.charAt(index)) || source.charAt(index) == ',')) {
            index++;
        }
        return index;
    }

    private int firstNonWhitespace(String source, int start) {
        for (var index = start; index < source.length(); index++) {
            if (!Character.isWhitespace(source.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private int lineNumber(String source, int index) {
        var line = 1;
        for (var offset = 0; offset < Math.min(index, source.length()); offset++) {
            if (source.charAt(offset) == '\n') {
                line++;
            }
        }
        return line;
    }

    record ParseResult(List<ParsedRoute> routes, List<String> limitations) {
    }

    @FunctionalInterface
    interface StaticStringResolver {
        StaticStringResolution resolve(String sourcePath, String source, String expression);
    }

    record StaticStringResolution(String value, String limitation) {
    }

    record ParsedRoute(
            String path,
            String fullPath,
            String parentPath,
            String redirectTo,
            String componentSymbol,
            boolean loadComponentDeclared,
            String loadComponentImportPath,
            String loadComponentSymbol,
            boolean loadChildrenDeclared,
            String loadChildrenImportPath,
            String loadChildrenSymbol,
            boolean childrenDeclared,
            String childrenSymbol,
            String outlet,
            List<GitLabFrontendRouteConfiguration> configuration,
            List<String> guards,
            boolean lazy,
            String sourcePath,
            int sourceLine,
            int sourceOffset,
            Integer parentSourceOffset,
            boolean pathDeclared
    ) {
    }

    private record Span(int start, int end) {
    }

    private record Properties(List<String> names, List<String> values) {
        private String value(String name) {
            for (var index = 0; index < names.size(); index++) {
                if (name.equals(names.get(index))) {
                    return values.get(index);
                }
            }
            return null;
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
            if (current == '\'' || current == '\"' || current == '`') {
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
