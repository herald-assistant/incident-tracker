package pl.mkn.tdw.integrations.gitlab.source;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Providers;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabJavaMethodSliceService {

    public static final int DEFAULT_OUTPUT_CHARACTERS = 8_000;
    public static final int MAX_OUTPUT_CHARACTERS = 40_000;

    private static final int MAX_SOURCE_CHARACTERS = 200_000;
    private static final int MAX_INCLUDED_HELPERS = 8;
    private static final int MAX_CANDIDATES = 30;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]*\\b");
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "false",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "null", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while",
            "record", "sealed", "permits", "non", "var"
    );

    private final GitLabRepositoryPort gitLabRepositoryPort;

    public GitLabJavaMethodSliceResponse readMethodSlice(GitLabJavaMethodSliceRequest request) {
        var limitations = new ArrayList<String>();
        var maxCharacters = normalizeLimit(request.maxCharacters());
        var filePath = normalizeFilePath(request.filePath());
        if (request.methodSelectors() == null || request.methodSelectors().isEmpty()) {
            limitations.add("At least one method selector is required.");
            return response(request, filePath, "INVALID_REQUEST", null, 0, 0, 0,
                    "", false, List.of(), List.of(), List.of(), 0, 0, List.of(), limitations);
        }

        GitLabRepositoryFileContent fileContent;
        try {
            fileContent = gitLabRepositoryPort.readFile(
                    request.group(),
                    request.projectName(),
                    request.branch(),
                    filePath,
                    MAX_SOURCE_CHARACTERS
            );
        } catch (RuntimeException exception) {
            limitations.add("Could not read Java source: " + safeMessage(exception));
            return response(request, filePath, "READ_FAILED", null, 0, 0, 0,
                    "", false, List.of(), List.of(), List.of(), 0, 0, List.of(), limitations);
        }

        var source = fileContent != null && fileContent.content() != null ? fileContent.content() : "";
        if (fileContent != null && fileContent.truncated()) {
            limitations.add("Source file content was truncated before Java parsing.");
        }
        var sourceLines = source.lines().toList();
        var totalLines = sourceLines.size();

        var parsed = parse(source, limitations);
        if (parsed == null) {
            return response(request, filePath, "PARSE_FAILED", null, 0, 0, totalLines,
                    "", false, List.of(), List.of(), List.of(), 0, 0, List.of(), limitations);
        }

        var allCandidates = parsed.findAll(MethodDeclaration.class).stream()
                .map(method -> toCandidate(parsed, method))
                .sorted(Comparator
                        .comparing(GitLabJavaMethodSliceMethodCandidate::declaringTypeName, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(GitLabJavaMethodSliceMethodCandidate::lineStart))
                .toList();
        var selectedMethods = selectedMethods(parsed, request, limitations);
        if (selectedMethods.isEmpty()) {
            limitations.add("No method matched requested selectors.");
            return response(request, filePath, "NOT_FOUND", null, 0, 0, totalLines,
                    "", false, List.of(), List.of(), List.of(), 0, 0, limitCandidates(allCandidates), limitations);
        }

        var declaringTypes = selectedMethods.stream()
                .map(this::declaringType)
                .filter(Objects::nonNull)
                .toList();
        if (declaringTypes.size() != selectedMethods.size()) {
            limitations.add("At least one selected method has no declaring type.");
            return response(request, filePath, "UNSUPPORTED_SOURCE", null, 0, 0, totalLines,
                    "", false, List.of(), List.of(), List.of(), 0, 0, limitCandidates(selectedMethods.stream()
                            .map(method -> toCandidate(parsed, method))
                            .toList()), limitations);
        }

        var declaringTypeNames = declaringTypes.stream()
                .map(type -> qualifiedTypeName(parsed, type))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (declaringTypeNames.size() > 1) {
            limitations.add("Selected methods belong to more than one declaring type. Provide declaringTypeName to narrow the slice.");
            return response(request, filePath, "MULTIPLE_DECLARING_TYPES", null, 0, 0, totalLines,
                    "", false, List.of(), List.of(), List.of(), 0, 0, limitCandidates(selectedMethods.stream()
                            .map(method -> toCandidate(parsed, method))
                            .toList()), limitations);
        }

        var declaringType = declaringTypes.get(0);
        var includedMethods = includedMethods(declaringType, selectedMethods, include(request.includeDirectPrivateHelpers()));
        var includedFields = includedFields(declaringType, includedMethods, include(request.includeRelevantFields()));
        var includedImports = includedImports(parsed, declaringType, includedMethods, includedFields, include(request.includeRelevantImports()));
        var renderResult = renderSlice(
                parsed,
                declaringType,
                includedMethods,
                includedFields,
                includedImports,
                sourceLines,
                maxCharacters,
                limitations
        );

        var methodCandidates = includedMethods.stream()
                .map(method -> toCandidate(parsed, method))
                .toList();
        var fieldNames = includedFields.stream()
                .flatMap(field -> field.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .toList();

        return response(
                request,
                filePath,
                "OK",
                qualifiedTypeName(parsed, declaringType),
                includedMethods.stream().mapToInt(this::beginLine).filter(line -> line > 0).min().orElse(0),
                includedMethods.stream().mapToInt(this::endLine).filter(line -> line > 0).max().orElse(0),
                totalLines,
                renderResult.content(),
                renderResult.truncated(),
                includedImports,
                fieldNames,
                methodCandidates,
                immediateFields(declaringType).size() - includedFields.size(),
                immediateMethods(declaringType).size() - includedMethods.size(),
                List.of(),
                limitations
        );
    }

    private CompilationUnit parse(String source, List<String> limitations) {
        try {
            var javaParser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                    .setStoreTokens(true));
            var result = javaParser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(source));
            result.getProblems().stream()
                    .map(problem -> "Could not parse Java source: " + problem.getMessage())
                    .forEach(limitations::add);
            return result.isSuccessful() ? result.getResult().orElse(null) : null;
        } catch (RuntimeException exception) {
            limitations.add("Could not parse Java source: " + safeMessage(exception));
            return null;
        }
    }

    private List<MethodDeclaration> selectedMethods(
            CompilationUnit compilationUnit,
            GitLabJavaMethodSliceRequest request,
            List<String> limitations
    ) {
        var selected = new LinkedHashMap<String, MethodDeclaration>();
        for (var selector : request.methodSelectors()) {
            if (selector == null || !StringUtils.hasText(selector.methodName())) {
                limitations.add("Ignored blank method selector.");
                continue;
            }
            var matches = matchingMethods(compilationUnit, request.declaringTypeName(), selector);
            if (matches.isEmpty()) {
                limitations.add("No method matched selector " + methodSelectorLabel(selector) + ".");
                continue;
            }
            for (var method : matches) {
                selected.put(methodKey(method), method);
            }
        }
        return selected.values().stream()
                .sorted(Comparator.comparingInt(this::beginLine))
                .toList();
    }

    private List<MethodDeclaration> matchingMethods(
            CompilationUnit compilationUnit,
            String declaringTypeName,
            GitLabJavaMethodSliceMethodSelector selector
    ) {
        var normalizedMethodName = normalize(selector.methodName());
        var normalizedDeclaringType = normalizeTypeSelector(declaringTypeName);
        return compilationUnit.findAll(MethodDeclaration.class).stream()
                .filter(method -> normalizedMethodName != null && normalizedMethodName.equals(normalize(method.getNameAsString())))
                .filter(method -> normalizedDeclaringType == null || declaringTypeMatches(compilationUnit, method, normalizedDeclaringType))
                .filter(method -> lineMatches(method, selector.lineStart()))
                .sorted(Comparator.comparingInt(this::beginLine))
                .toList();
    }

    private boolean declaringTypeMatches(CompilationUnit compilationUnit, MethodDeclaration method, String normalizedDeclaringType) {
        var type = declaringType(method);
        if (type == null) {
            return false;
        }
        var simple = normalize(type.getNameAsString());
        var qualified = normalizeTypeSelector(qualifiedTypeName(compilationUnit, type));
        var relative = normalizeTypeSelector(relativeTypeName(type));
        return normalizedDeclaringType.equals(simple)
                || normalizedDeclaringType.equals(qualified)
                || normalizedDeclaringType.equals(relative);
    }

    private boolean lineMatches(MethodDeclaration method, Integer lineStart) {
        if (lineStart == null || lineStart <= 0) {
            return true;
        }
        var begin = beginLine(method);
        var end = endLine(method);
        return begin == lineStart || (begin > 0 && end > 0 && lineStart >= begin && lineStart <= end);
    }

    private List<MethodDeclaration> includedMethods(
            TypeDeclaration<?> declaringType,
            List<MethodDeclaration> selectedMethods,
            boolean includeHelpers
    ) {
        var included = new LinkedHashMap<String, MethodDeclaration>();
        for (var selectedMethod : selectedMethods) {
            included.put(methodKey(selectedMethod), selectedMethod);
        }
        if (!includeHelpers) {
            return List.copyOf(included.values());
        }

        var maxIncludedMethods = selectedMethods.size() + MAX_INCLUDED_HELPERS;
        var immediateMethods = immediateMethods(declaringType);
        var cursor = 0;
        while (cursor < included.size() && included.size() < maxIncludedMethods) {
            var sourceMethod = new ArrayList<>(included.values()).get(cursor);
            cursor++;
            var localCalls = directLocalMethodCalls(sourceMethod);
            for (var method : immediateMethods) {
                if (included.size() >= maxIncludedMethods) {
                    break;
                }
                if (included.containsKey(methodKey(method))) {
                    continue;
                }
                if (localCalls.contains(new LocalMethodCall(method.getNameAsString(), method.getParameters().size()))) {
                    included.put(methodKey(method), method);
                }
            }
            if (cursor > maxIncludedMethods) {
                break;
            }
        }
        return included.values().stream()
                .sorted(Comparator.comparingInt(this::beginLine))
                .toList();
    }

    private Set<LocalMethodCall> directLocalMethodCalls(MethodDeclaration method) {
        var calls = new LinkedHashSet<LocalMethodCall>();
        method.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.findAncestor(MethodDeclaration.class).orElse(null) == method)
                .filter(this::isLocalMethodCall)
                .map(call -> new LocalMethodCall(call.getNameAsString(), call.getArguments().size()))
                .forEach(calls::add);
        return calls;
    }

    private boolean isLocalMethodCall(MethodCallExpr call) {
        var scope = call.getScope().orElse(null);
        return scope == null || scope instanceof ThisExpr;
    }

    private List<FieldDeclaration> includedFields(
            TypeDeclaration<?> declaringType,
            List<MethodDeclaration> includedMethods,
            boolean includeRelevantFields
    ) {
        if (!includeRelevantFields) {
            return List.of();
        }
        var fields = immediateFields(declaringType);
        var fieldNames = new LinkedHashSet<String>();
        fields.stream()
                .flatMap(field -> field.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .forEach(fieldNames::add);
        var accessorFieldNames = accessorFieldNames(fields);

        var usedFieldNames = new LinkedHashSet<String>();
        for (var method : includedMethods) {
            method.findAll(NameExpr.class).stream()
                    .map(NameExpr::getNameAsString)
                    .filter(fieldNames::contains)
                    .forEach(usedFieldNames::add);
            method.findAll(FieldAccessExpr.class).stream()
                    .filter(access -> access.getScope() instanceof ThisExpr)
                    .map(access -> access.getNameAsString())
                    .filter(fieldNames::contains)
                    .forEach(usedFieldNames::add);
            method.findAll(MethodCallExpr.class).stream()
                    .map(MethodCallExpr::getScope)
                    .flatMap(scope -> scope.stream())
                    .filter(NameExpr.class::isInstance)
                    .map(NameExpr.class::cast)
                    .map(NameExpr::getNameAsString)
                    .filter(fieldNames::contains)
                    .forEach(usedFieldNames::add);
            method.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.findAncestor(MethodDeclaration.class).orElse(null) == method)
                    .filter(this::isCurrentTypeAccessorCall)
                    .filter(this::hasAccessorArgumentCount)
                    .map(call -> accessorFieldNames.get(call.getNameAsString()))
                    .filter(Objects::nonNull)
                    .forEach(usedFieldNames::add);
            method.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.findAncestor(MethodDeclaration.class).orElse(null) == method)
                    .filter(call -> fieldNames.contains(call.getNameAsString()))
                    .filter(call -> isCurrentTypeBuilderSetterCall(call, declaringType))
                    .map(MethodCallExpr::getNameAsString)
                    .forEach(usedFieldNames::add);
        }

        return fields.stream()
                .filter(field -> field.getVariables().stream()
                        .map(VariableDeclarator::getNameAsString)
                        .anyMatch(usedFieldNames::contains))
                .sorted(Comparator.comparingInt(this::beginLine))
                .toList();
    }

    private Map<String, String> accessorFieldNames(List<FieldDeclaration> fields) {
        var accessors = new LinkedHashMap<String, String>();
        for (var field : fields) {
            for (var variable : field.getVariables()) {
                var fieldName = variable.getNameAsString();
                var suffix = accessorSuffix(fieldName);
                if (!StringUtils.hasText(suffix)) {
                    continue;
                }
                accessors.putIfAbsent("get" + suffix, fieldName);
                accessors.putIfAbsent("set" + suffix, fieldName);
                if (isBooleanField(variable)) {
                    var booleanSuffix = booleanAccessorSuffix(fieldName);
                    if (StringUtils.hasText(booleanSuffix)) {
                        accessors.putIfAbsent("is" + booleanSuffix, fieldName);
                        accessors.putIfAbsent("set" + booleanSuffix, fieldName);
                    }
                }
            }
        }
        return accessors;
    }

    private boolean isCurrentTypeAccessorCall(MethodCallExpr call) {
        var scope = call.getScope().orElse(null);
        return scope == null || scope instanceof ThisExpr;
    }

    private boolean hasAccessorArgumentCount(MethodCallExpr call) {
        var methodName = call.getNameAsString();
        var argumentCount = call.getArguments().size();
        if (methodName.startsWith("get") || methodName.startsWith("is")) {
            return argumentCount == 0;
        }
        if (methodName.startsWith("set")) {
            return argumentCount == 1;
        }
        return false;
    }

    private boolean isCurrentTypeBuilderSetterCall(MethodCallExpr call, TypeDeclaration<?> declaringType) {
        if (call.getArguments().size() != 1) {
            return false;
        }
        return call.getScope()
                .filter(MethodCallExpr.class::isInstance)
                .map(MethodCallExpr.class::cast)
                .filter(scope -> builderChainStartsFromCurrentType(scope, declaringType))
                .isPresent();
    }

    private boolean builderChainStartsFromCurrentType(MethodCallExpr call, TypeDeclaration<?> declaringType) {
        var current = call;
        while (current != null) {
            if (isCurrentTypeBuilderRoot(current, declaringType)) {
                return true;
            }
            current = current.getScope()
                    .filter(MethodCallExpr.class::isInstance)
                    .map(MethodCallExpr.class::cast)
                    .orElse(null);
        }
        return false;
    }

    private boolean isCurrentTypeBuilderRoot(MethodCallExpr call, TypeDeclaration<?> declaringType) {
        var methodName = call.getNameAsString();
        if ("toBuilder".equals(methodName)) {
            return call.getScope()
                    .map(scope -> scope instanceof ThisExpr)
                    .orElse(true);
        }
        if (!"builder".equals(methodName)) {
            return false;
        }
        return call.getScope()
                .map(scope -> isCurrentTypeReference(scope, declaringType))
                .orElse(true);
    }

    private boolean isCurrentTypeReference(Expression expression, TypeDeclaration<?> declaringType) {
        if (expression instanceof ThisExpr) {
            return true;
        }
        var normalizedScope = normalizeTypeSelector(expression.toString());
        var normalizedDeclaringType = normalize(declaringType.getNameAsString());
        return normalizedScope != null && normalizedScope.equals(normalizedDeclaringType);
    }

    private String accessorSuffix(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return null;
        }
        var trimmed = fieldName.trim();
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(trimmed.charAt(0)) && Character.isUpperCase(trimmed.charAt(1))) {
            return trimmed;
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private String booleanAccessorSuffix(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return null;
        }
        var trimmed = fieldName.trim();
        if (trimmed.length() > 2
                && trimmed.startsWith("is")
                && Character.isUpperCase(trimmed.charAt(2))) {
            return trimmed.substring(2);
        }
        return accessorSuffix(trimmed);
    }

    private boolean isBooleanField(VariableDeclarator variable) {
        var type = variable.getType().asString();
        return "boolean".equals(type) || "Boolean".equals(type) || "java.lang.Boolean".equals(type);
    }

    private List<ImportDeclaration> includedImports(
            CompilationUnit compilationUnit,
            TypeDeclaration<?> declaringType,
            List<MethodDeclaration> includedMethods,
            List<FieldDeclaration> includedFields,
            boolean includeRelevantImports
    ) {
        if (!includeRelevantImports) {
            return List.of();
        }

        var sourceText = new StringBuilder();
        sourceText.append(declaringType.getAnnotations()).append('\n');
        sourceText.append(declaringType.getNameAsString()).append('\n');
        includedFields.forEach(field -> sourceText.append(field).append('\n'));
        includedMethods.forEach(method -> sourceText.append(method).append('\n'));

        var usedTokens = identifiers(sourceText.toString());
        return compilationUnit.getImports().stream()
                .filter(importDeclaration -> importDeclaration.isAsterisk()
                        || usedTokens.contains(simpleImportName(importDeclaration)))
                .toList();
    }

    private RenderResult renderSlice(
            CompilationUnit compilationUnit,
            TypeDeclaration<?> declaringType,
            List<MethodDeclaration> includedMethods,
            List<FieldDeclaration> includedFields,
            List<ImportDeclaration> includedImports,
            List<String> sourceLines,
            int maxCharacters,
            List<String> limitations
    ) {
        var content = new StringBuilder();
        compilationUnit.getPackageDeclaration()
                .ifPresent(packageDeclaration -> content.append(packageDeclaration.toString().strip())
                        .append(System.lineSeparator())
                        .append(System.lineSeparator()));
        for (var importDeclaration : includedImports) {
            content.append(importDeclaration.toString().strip()).append(System.lineSeparator());
        }
        var omittedImportCount = compilationUnit.getImports().size() - includedImports.size();
        if (omittedImportCount > 0) {
            if (!includedImports.isEmpty()) {
                content.append(System.lineSeparator());
            }
            content.append("// ... omitted imports (").append(omittedImportCount).append(") ...")
                    .append(System.lineSeparator());
        }
        if (!compilationUnit.getImports().isEmpty()) {
            content.append(System.lineSeparator());
        }

        content.append(typeHeader(declaringType, sourceLines)).append(System.lineSeparator());

        var allFields = immediateFields(declaringType);
        var includedFieldKeys = includedFields.stream().map(this::fieldKey).collect(java.util.stream.Collectors.toSet());
        var omittedFieldRun = false;
        for (var field : allFields) {
            if (includedFieldKeys.contains(fieldKey(field))) {
                if (omittedFieldRun) {
                    content.append(indent("// ... omitted fields ...")).append(System.lineSeparator()).append(System.lineSeparator());
                    omittedFieldRun = false;
                }
                content.append(indent(field.toString().strip())).append(System.lineSeparator()).append(System.lineSeparator());
            } else {
                omittedFieldRun = true;
            }
        }
        if (omittedFieldRun) {
            content.append(indent("// ... omitted fields ...")).append(System.lineSeparator()).append(System.lineSeparator());
        }

        var includedMethodKeys = includedMethods.stream().map(this::methodKey).collect(java.util.stream.Collectors.toSet());
        var omittedMethodRun = false;
        for (var method : immediateMethods(declaringType)) {
            if (includedMethodKeys.contains(methodKey(method))) {
                if (omittedMethodRun) {
                    content.append(indent("// ... omitted methods ...")).append(System.lineSeparator()).append(System.lineSeparator());
                    omittedMethodRun = false;
                }
                content.append(indent(method.toString().strip())).append(System.lineSeparator()).append(System.lineSeparator());
            } else {
                omittedMethodRun = true;
            }
        }
        if (omittedMethodRun) {
            content.append(indent("// ... omitted methods ...")).append(System.lineSeparator());
        }

        content.append("}");
        var rendered = content.toString().strip();
        if (rendered.length() <= maxCharacters) {
            return new RenderResult(rendered, false);
        }
        limitations.add("Rendered method slice was truncated by maxCharacters.");
        return new RenderResult(rendered.substring(0, maxCharacters).strip()
                + System.lineSeparator()
                + "// ... truncated by maxCharacters ...", true);
    }

    private String typeHeader(TypeDeclaration<?> type, List<String> sourceLines) {
        var begin = beginLine(type);
        if (begin <= 0 || begin > sourceLines.size()) {
            return fallbackTypeHeader(type);
        }

        var headerLines = new ArrayList<String>();
        for (int index = begin - 1; index < sourceLines.size(); index++) {
            var line = sourceLines.get(index);
            var braceIndex = line.indexOf('{');
            if (braceIndex >= 0) {
                headerLines.add(line.substring(0, braceIndex + 1));
                break;
            }
            headerLines.add(line);
        }
        var header = String.join(System.lineSeparator(), headerLines).strip();
        return StringUtils.hasText(header) ? header : fallbackTypeHeader(type);
    }

    private String fallbackTypeHeader(TypeDeclaration<?> type) {
        var keyword = "class";
        if (type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isInterface()) {
            keyword = "interface";
        } else if (type.isEnumDeclaration()) {
            keyword = "enum";
        } else if (type.isRecordDeclaration()) {
            keyword = "record";
        } else if (type.isAnnotationDeclaration()) {
            keyword = "@interface";
        }
        return keyword + " " + type.getNameAsString() + " {";
    }

    private String indent(String text) {
        return text.lines()
                .map(line -> line.isBlank() ? line : "    " + line)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private List<MethodDeclaration> immediateMethods(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .sorted(Comparator.comparingInt(this::beginLine))
                .toList();
    }

    private List<FieldDeclaration> immediateFields(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .sorted(Comparator.comparingInt(this::beginLine))
                .toList();
    }

    private TypeDeclaration<?> declaringType(MethodDeclaration method) {
        return (TypeDeclaration<?>) method.findAncestor(TypeDeclaration.class).orElse(null);
    }

    private GitLabJavaMethodSliceMethodCandidate toCandidate(CompilationUnit compilationUnit, MethodDeclaration method) {
        return new GitLabJavaMethodSliceMethodCandidate(
                qualifiedTypeName(compilationUnit, declaringType(method)),
                method.getNameAsString(),
                method.getDeclarationAsString(false, false, true),
                beginLine(method),
                endLine(method),
                method.getParameters().size(),
                method.getParameters().stream()
                        .map(parameter -> parameter.getType().asString())
                        .toList()
        );
    }

    private List<GitLabJavaMethodSliceMethodCandidate> limitCandidates(List<GitLabJavaMethodSliceMethodCandidate> candidates) {
        return candidates.stream().limit(MAX_CANDIDATES).toList();
    }

    private String qualifiedTypeName(CompilationUnit compilationUnit, TypeDeclaration<?> type) {
        if (type == null) {
            return null;
        }
        var relative = relativeTypeName(type);
        var packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getName().asString())
                .orElse(null);
        return StringUtils.hasText(packageName) ? packageName + "." + relative : relative;
    }

    private String relativeTypeName(TypeDeclaration<?> type) {
        var names = new ArrayList<String>();
        TypeDeclaration<?> current = type;
        while (current != null) {
            names.add(0, current.getNameAsString());
            current = (TypeDeclaration<?>) current.findAncestor(TypeDeclaration.class).orElse(null);
        }
        return String.join(".", names);
    }

    private int beginLine(Node node) {
        return node.getBegin().map(position -> position.line).orElse(0);
    }

    private int endLine(Node node) {
        return node.getEnd().map(position -> position.line).orElse(0);
    }

    private String fieldKey(FieldDeclaration field) {
        return beginLine(field) + ":" + field.getVariables().stream()
                .map(VariableDeclarator::getNameAsString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String methodKey(MethodDeclaration method) {
        return beginLine(method) + ":" + method.getNameAsString() + ":" + method.getParameters().size();
    }

    private Set<String> identifiers(String text) {
        var identifiers = new LinkedHashSet<String>();
        var matcher = IDENTIFIER_PATTERN.matcher(text != null ? text : "");
        while (matcher.find()) {
            var token = matcher.group();
            if (!JAVA_KEYWORDS.contains(token.toLowerCase(Locale.ROOT))) {
                identifiers.add(token);
            }
        }
        return identifiers;
    }

    private String simpleImportName(ImportDeclaration importDeclaration) {
        var name = importDeclaration.getNameAsString();
        var lastDotIndex = name.lastIndexOf('.');
        return lastDotIndex >= 0 ? name.substring(lastDotIndex + 1) : name;
    }

    private String normalizeTypeSelector(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.trim()
                .replace("...", "[]")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        var genericIndex = normalized.indexOf('<');
        if (genericIndex > 0) {
            normalized = normalized.substring(0, genericIndex);
        }
        var lastDotIndex = normalized.lastIndexOf('.');
        return lastDotIndex >= 0 ? normalized.substring(lastDotIndex + 1) : normalized;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean include(Boolean value) {
        return value == null || value;
    }

    private int normalizeLimit(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_OUTPUT_CHARACTERS;
        }
        return Math.min(value, MAX_OUTPUT_CHARACTERS);
    }

    private String normalizeFilePath(String filePath) {
        var normalized = filePath != null ? filePath.trim().replace('\\', '/') : "";
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private String methodSelectorLabel(GitLabJavaMethodSliceMethodSelector selector) {
        if (selector == null) {
            return "<null>";
        }
        return selector.lineStart() != null
                ? selector.methodName() + "@" + selector.lineStart()
                : selector.methodName();
    }

    private GitLabJavaMethodSliceResponse response(
            GitLabJavaMethodSliceRequest request,
            String filePath,
            String status,
            String declaringTypeName,
            int returnedLineStart,
            int returnedLineEnd,
            int totalLines,
            String content,
            boolean truncated,
            List<ImportDeclaration> includedImports,
            List<String> includedFields,
            List<GitLabJavaMethodSliceMethodCandidate> includedMethods,
            int omittedFieldCount,
            int omittedMethodCount,
            List<GitLabJavaMethodSliceMethodCandidate> candidates,
            List<String> limitations
    ) {
        return new GitLabJavaMethodSliceResponse(
                request.group(),
                request.projectName(),
                request.branch(),
                filePath,
                status,
                declaringTypeName,
                request.methodSelectors(),
                returnedLineStart,
                returnedLineEnd,
                totalLines,
                content,
                content != null ? content.length() : 0,
                truncated,
                includedImports.stream().map(ImportDeclaration::toString).map(String::trim).toList(),
                includedFields,
                includedMethods,
                Math.max(0, omittedFieldCount),
                Math.max(0, omittedMethodCount),
                candidates,
                distinct(limitations)
        );
    }

    private List<String> distinct(List<String> values) {
        var distinct = new LinkedHashSet<String>();
        for (var value : values != null ? values : List.<String>of()) {
            if (StringUtils.hasText(value)) {
                distinct.add(value.trim());
            }
        }
        return List.copyOf(distinct);
    }

    private record RenderResult(String content, boolean truncated) {
    }

    private record LocalMethodCall(String methodName, int argumentCount) {
    }
}
