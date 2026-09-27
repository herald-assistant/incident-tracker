package pl.mkn.tdw.integrations.gitlab.service.frontend;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendSourceReference;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class AngularBootstrapSourceParser {

    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "(?s)\\bimport\\s*\\{([^}]*)}\\s*from\\s*(['\"])([^'\"]+)\\2"
    );
    private static final Pattern DEFAULT_IMPORT = Pattern.compile(
            "(?s)\\bimport\\s+(?!type\\b)([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "(?:\\s*,\\s*\\{[^}]*})?\\s+from\\s*(['\"])([^'\"]+)\\2"
    );
    private static final Pattern CONST_DECLARATION = Pattern.compile(
            "(?s)\\b(export\\s+)?const\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\s*(?::[^=;]+)?="
    );
    private static final Pattern NAMED_RE_EXPORT = Pattern.compile(
            "(?s)\\bexport\\s*\\{([^}]*)}\\s*from\\s*(['\"])([^'\"]+)\\2"
    );
    private static final Pattern STAR_RE_EXPORT = Pattern.compile(
            "(?s)\\bexport\\s*\\*\\s*from\\s*(['\"])([^'\"]+)\\1"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern CLASS_DECLARATION = Pattern.compile(
            "\\b(?:export\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Pattern MODULE_IMPORTS = Pattern.compile("\\bimports\\s*:\\s*\\[");

    ParsedSource parse(String sourcePath, String source) {
        var safeSource = source != null ? source : "";
        var commentMasked = maskComments(safeSource);
        var codeMasked = maskNonCode(safeSource);
        var imports = imports(commentMasked);
        var reExports = reExports(commentMasked);
        var constants = constants(sourcePath, commentMasked, codeMasked);
        var bootstrapNames = importedLocalNames(imports, "@angular/platform-browser", "bootstrapApplication");
        var moduleFactoryNames = importedLocalNames(imports,
                "@angular/platform-browser-dynamic", "platformBrowserDynamic");
        var routerProviderNames = importedLocalNames(imports, "@angular/router", "provideRouter");
        var routerModuleNames = importedLocalNames(imports, "@angular/router", "RouterModule");
        var ngModuleNames = importedLocalNames(imports, "@angular/core", "NgModule");
        var bootstrapCalls = new ArrayList<>(calls(
                sourcePath, commentMasked, codeMasked, bootstrapNames, CallKind.BOOTSTRAP));
        bootstrapCalls.addAll(moduleBootstrapCalls(sourcePath, commentMasked, codeMasked, moduleFactoryNames));
        bootstrapCalls.sort(java.util.Comparator.comparingInt(CallExpression::start));
        var routerProviderCalls = new ArrayList<>(calls(
                sourcePath,
                commentMasked,
                codeMasked,
                routerProviderNames,
                CallKind.ROUTER_PROVIDER
        ));
        routerProviderCalls.addAll(routerModuleCalls(sourcePath, commentMasked, codeMasked, routerModuleNames));
        routerProviderCalls.sort(java.util.Comparator.comparingInt(CallExpression::start));
        return new ParsedSource(
                sourcePath,
                safeSource,
                Map.copyOf(imports),
                List.copyOf(reExports),
                List.copyOf(constants),
                List.copyOf(bootstrapCalls),
                List.copyOf(routerProviderCalls),
                moduleDeclarations(sourcePath, commentMasked, codeMasked, ngModuleNames)
        );
    }

    private List<CallExpression> moduleBootstrapCalls(
            String sourcePath, String source, String codeMasked, List<String> factoryNames
    ) {
        var result = new ArrayList<CallExpression>();
        for (var factoryName : factoryNames) {
            var matcher = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(factoryName)
                    + "\\s*\\([^)]*\\)\\s*\\.\\s*bootstrapModule\\s*\\(").matcher(codeMasked);
            while (matcher.find()) {
                var open = matcher.end() - 1;
                var close = matchingDelimiter(codeMasked, open, '(', ')');
                if (close >= 0) {
                    result.add(new CallExpression(CallKind.MODULE_BOOTSTRAP, "bootstrapModule",
                            arguments(source, codeMasked, open + 1, close), matcher.start(), close + 1,
                            reference(sourcePath, "bootstrapModule", source, matcher.start())));
                }
            }
        }
        return result;
    }

    private List<CallExpression> routerModuleCalls(
            String sourcePath, String source, String codeMasked, List<String> moduleNames
    ) {
        var result = new ArrayList<CallExpression>();
        for (var moduleName : moduleNames) {
            var matcher = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(moduleName)
                    + "\\s*\\.\\s*forRoot\\s*\\(").matcher(codeMasked);
            while (matcher.find()) {
                var open = matcher.end() - 1;
                var close = matchingDelimiter(codeMasked, open, '(', ')');
                if (close >= 0) {
                    result.add(new CallExpression(CallKind.ROUTER_MODULE_ROOT, "RouterModule.forRoot",
                            arguments(source, codeMasked, open + 1, close), matcher.start(), close + 1,
                            reference(sourcePath, "RouterModule.forRoot", source, matcher.start())));
                }
            }
        }
        return result;
    }

    private List<ModuleDeclaration> moduleDeclarations(
            String sourcePath, String source, String codeMasked, List<String> decoratorNames
    ) {
        var result = new ArrayList<ModuleDeclaration>();
        for (var decoratorName : decoratorNames) {
            var matcher = Pattern.compile("@\\s*" + Pattern.quote(decoratorName) + "\\s*\\(")
                    .matcher(codeMasked);
            while (matcher.find()) {
                var open = matcher.end() - 1;
                var close = matchingDelimiter(codeMasked, open, '(', ')');
                if (close < 0) continue;
                var nextDecorator = codeMasked.indexOf('@', close + 1);
                var classSearchEnd = nextDecorator >= 0 ? nextDecorator : codeMasked.length();
                var classMatcher = CLASS_DECLARATION.matcher(codeMasked);
                classMatcher.region(close + 1, classSearchEnd);
                if (!classMatcher.find()) continue;
                var imports = new ArrayList<String>();
                var importsMatcher = MODULE_IMPORTS.matcher(codeMasked);
                importsMatcher.region(open + 1, close);
                if (importsMatcher.find()) {
                    var arrayOpen = importsMatcher.end() - 1;
                    var arrayClose = matchingDelimiter(codeMasked, arrayOpen, '[', ']');
                    if (arrayClose > arrayOpen && arrayClose < close) {
                        for (var expression : arguments(source, codeMasked, arrayOpen + 1, arrayClose)) {
                            if (expression.identifier() != null) imports.add(expression.identifier());
                        }
                    }
                }
                result.add(new ModuleDeclaration(classMatcher.group(1), List.copyOf(imports),
                        open + 1, close,
                        reference(sourcePath, classMatcher.group(1), source, matcher.start())));
            }
        }
        return List.copyOf(result);
    }

    private List<ReExportBinding> reExports(String source) {
        var bindings = new ArrayList<ReExportBinding>();
        var namedMatcher = NAMED_RE_EXPORT.matcher(source);
        while (namedMatcher.find()) {
            for (var declaration : namedMatcher.group(1).split(",")) {
                var normalized = declaration.trim().replaceFirst("^type\\s+", "");
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                var alias = normalized.split("\\s+as\\s+");
                var sourceName = alias[0].trim();
                var exportedName = alias[alias.length - 1].trim();
                if (IDENTIFIER.matcher(sourceName).matches() && IDENTIFIER.matcher(exportedName).matches()) {
                    bindings.add(new ReExportBinding(sourceName, exportedName, namedMatcher.group(3), false));
                }
            }
        }
        var starMatcher = STAR_RE_EXPORT.matcher(source);
        while (starMatcher.find()) {
            bindings.add(new ReExportBinding(null, null, starMatcher.group(2), true));
        }
        return bindings;
    }

    private Map<String, ImportBinding> imports(String source) {
        var imports = new LinkedHashMap<String, ImportBinding>();
        var defaultMatcher = DEFAULT_IMPORT.matcher(source);
        while (defaultMatcher.find()) {
            var localName = defaultMatcher.group(1);
            imports.put(localName, new ImportBinding("default", localName, defaultMatcher.group(3).trim()));
        }
        var matcher = NAMED_IMPORT.matcher(source);
        while (matcher.find()) {
            var moduleSpecifier = matcher.group(3).trim();
            for (var declaration : matcher.group(1).split(",")) {
                var normalized = declaration.trim().replaceFirst("^type\\s+", "");
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                var alias = normalized.split("\\s+as\\s+");
                var exportedName = alias[0].trim();
                var localName = alias[alias.length - 1].trim();
                if (IDENTIFIER.matcher(exportedName).matches() && IDENTIFIER.matcher(localName).matches()) {
                    imports.put(localName, new ImportBinding(exportedName, localName, moduleSpecifier));
                }
            }
        }
        return imports;
    }

    private List<String> importedLocalNames(
            Map<String, ImportBinding> imports,
            String moduleSpecifier,
            String exportedName
    ) {
        return imports.values().stream()
                .filter(binding -> moduleSpecifier.equals(binding.moduleSpecifier()))
                .filter(binding -> exportedName.equals(binding.exportedName()))
                .map(ImportBinding::localName)
                .distinct()
                .toList();
    }

    private List<ConstDeclaration> constants(String sourcePath, String source, String codeMasked) {
        var declarations = new ArrayList<ConstDeclaration>();
        var matcher = CONST_DECLARATION.matcher(codeMasked);
        while (matcher.find()) {
            var expressionStart = matcher.end();
            var expressionEnd = expressionEnd(codeMasked, expressionStart);
            declarations.add(new ConstDeclaration(
                    matcher.group(2),
                    matcher.group(1) != null,
                    source.substring(expressionStart, expressionEnd).trim(),
                    expressionStart,
                    expressionEnd,
                    reference(sourcePath, matcher.group(2), source, matcher.start())
            ));
        }
        return declarations;
    }

    private List<CallExpression> calls(
            String sourcePath,
            String source,
            String codeMasked,
            List<String> localNames,
            CallKind kind
    ) {
        var calls = new ArrayList<CallExpression>();
        for (var localName : localNames) {
            var matcher = Pattern.compile(
                    "(?<![A-Za-z0-9_$])" + Pattern.quote(localName) + "\\s*\\("
            ).matcher(codeMasked);
            while (matcher.find()) {
                var openParenthesis = codeMasked.indexOf('(', matcher.start());
                var closeParenthesis = matchingDelimiter(codeMasked, openParenthesis, '(', ')');
                if (closeParenthesis < 0) {
                    continue;
                }
                calls.add(new CallExpression(
                        kind,
                        localName,
                        arguments(source, codeMasked, openParenthesis + 1, closeParenthesis),
                        matcher.start(),
                        closeParenthesis + 1,
                        reference(sourcePath, localName, source, matcher.start())
                ));
            }
        }
        return calls.stream().sorted(java.util.Comparator.comparingInt(CallExpression::start)).toList();
    }

    private List<Expression> arguments(String source, String codeMasked, int start, int end) {
        var arguments = new ArrayList<Expression>();
        var argumentStart = start;
        var roundDepth = 0;
        var squareDepth = 0;
        var curlyDepth = 0;
        for (var index = start; index < end; index++) {
            switch (codeMasked.charAt(index)) {
                case '(' -> roundDepth++;
                case ')' -> roundDepth--;
                case '[' -> squareDepth++;
                case ']' -> squareDepth--;
                case '{' -> curlyDepth++;
                case '}' -> curlyDepth--;
                case ',' -> {
                    if (roundDepth == 0 && squareDepth == 0 && curlyDepth == 0) {
                        addExpression(arguments, source, argumentStart, index);
                        argumentStart = index + 1;
                    }
                }
                default -> {
                }
            }
        }
        addExpression(arguments, source, argumentStart, end);
        return List.copyOf(arguments);
    }

    private void addExpression(List<Expression> expressions, String source, int start, int end) {
        while (start < end && Character.isWhitespace(source.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(source.charAt(end - 1))) {
            end--;
        }
        if (start < end) {
            expressions.add(new Expression(source.substring(start, end), start, end));
        }
    }

    private int expressionEnd(String codeMasked, int start) {
        var roundDepth = 0;
        var squareDepth = 0;
        var curlyDepth = 0;
        for (var index = start; index < codeMasked.length(); index++) {
            switch (codeMasked.charAt(index)) {
                case '(' -> roundDepth++;
                case ')' -> roundDepth--;
                case '[' -> squareDepth++;
                case ']' -> squareDepth--;
                case '{' -> curlyDepth++;
                case '}' -> curlyDepth--;
                case ';' -> {
                    if (roundDepth == 0 && squareDepth == 0 && curlyDepth == 0) {
                        return index;
                    }
                }
                default -> {
                }
            }
        }
        return codeMasked.length();
    }

    private int matchingDelimiter(String source, int start, char open, char close) {
        if (start < 0 || start >= source.length() || source.charAt(start) != open) {
            return -1;
        }
        var depth = 0;
        for (var index = start; index < source.length(); index++) {
            var current = source.charAt(index);
            if (current == open) {
                depth++;
            } else if (current == close && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private GitLabFrontendSourceReference reference(
            String sourcePath,
            String symbol,
            String source,
            int offset
    ) {
        var line = 1;
        for (var index = 0; index < Math.min(offset, source.length()); index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return new GitLabFrontendSourceReference(sourcePath, symbol, line, line);
    }

    private String maskComments(String source) {
        return mask(source, false);
    }

    private String maskNonCode(String source) {
        return mask(source, true);
    }

    private String mask(String source, boolean maskStrings) {
        var result = new StringBuilder(source);
        var state = LexicalState.CODE;
        var escaped = false;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (state == LexicalState.CODE) {
                if (current == '/' && next == '/') {
                    mask(result, index);
                    mask(result, ++index);
                    state = LexicalState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    mask(result, index);
                    mask(result, ++index);
                    state = LexicalState.BLOCK_COMMENT;
                } else if (current == '\'' || current == '"' || current == '`') {
                    state = current == '\'' ? LexicalState.SINGLE_QUOTE
                            : current == '"' ? LexicalState.DOUBLE_QUOTE : LexicalState.TEMPLATE;
                    escaped = false;
                    if (maskStrings) {
                        mask(result, index);
                    }
                }
                continue;
            }
            if (state == LexicalState.LINE_COMMENT) {
                if (current == '\n' || current == '\r') {
                    state = LexicalState.CODE;
                } else {
                    mask(result, index);
                }
                continue;
            }
            if (state == LexicalState.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    mask(result, index);
                    mask(result, ++index);
                    state = LexicalState.CODE;
                } else {
                    mask(result, index);
                }
                continue;
            }
            if (maskStrings) {
                mask(result, index);
            }
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if ((state == LexicalState.SINGLE_QUOTE && current == '\'')
                    || (state == LexicalState.DOUBLE_QUOTE && current == '"')
                    || (state == LexicalState.TEMPLATE && current == '`')) {
                state = LexicalState.CODE;
            }
        }
        return result.toString();
    }

    private void mask(StringBuilder source, int index) {
        var current = source.charAt(index);
        if (current != '\n' && current != '\r') {
            source.setCharAt(index, ' ');
        }
    }

    record ParsedSource(
            String sourcePath,
            String source,
            Map<String, ImportBinding> imports,
            List<ReExportBinding> reExports,
            List<ConstDeclaration> constants,
            List<CallExpression> bootstrapCalls,
            List<CallExpression> routerProviderCalls,
            List<ModuleDeclaration> moduleDeclarations
    ) {

        ImportBinding imported(String localName) {
            return imports.get(localName);
        }

        ConstDeclaration constant(String name) {
            return constants.stream().filter(constant -> constant.name().equals(name)).findFirst().orElse(null);
        }

        List<CallExpression> routerCallsWithin(int start, int end) {
            return routerProviderCalls.stream()
                    .filter(call -> call.kind() == CallKind.ROUTER_PROVIDER)
                    .filter(call -> call.start() >= start && call.end() <= end)
                    .toList();
        }

        ModuleDeclaration module(String name) {
            return moduleDeclarations.stream().filter(module -> module.name().equals(name))
                    .findFirst().orElse(null);
        }

        List<CallExpression> routerModuleCalls() {
            return routerProviderCalls.stream()
                    .filter(call -> call.kind() == CallKind.ROUTER_MODULE_ROOT)
                    .toList();
        }

        List<ReExportBinding> namedReExports(String exportedName) {
            return reExports.stream()
                    .filter(binding -> !binding.star())
                    .filter(binding -> binding.exportedName().equals(exportedName))
                    .toList();
        }

        List<ReExportBinding> starReExports() {
            return reExports.stream().filter(ReExportBinding::star).toList();
        }
    }

    record ImportBinding(String exportedName, String localName, String moduleSpecifier) {
    }

    record ReExportBinding(
            String sourceName,
            String exportedName,
            String moduleSpecifier,
            boolean star
    ) {
    }

    record ConstDeclaration(
            String name,
            boolean exported,
            String expression,
            int expressionStart,
            int expressionEnd,
            GitLabFrontendSourceReference source
    ) {
    }

    record ModuleDeclaration(
            String name, List<String> imports, int metadataStart, int metadataEnd,
            GitLabFrontendSourceReference source
    ) {
    }

    record CallExpression(
            CallKind kind,
            String localName,
            List<Expression> arguments,
            int start,
            int end,
            GitLabFrontendSourceReference source
    ) {
    }

    record Expression(String text, int start, int end) {

        String identifier() {
            var normalized = text.trim();
            return IDENTIFIER.matcher(normalized).matches() ? normalized : null;
        }
    }

    enum CallKind {
        BOOTSTRAP,
        ROUTER_PROVIDER,
        MODULE_BOOTSTRAP,
        ROUTER_MODULE_ROOT
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        TEMPLATE
    }
}
