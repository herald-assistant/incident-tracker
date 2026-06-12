package pl.mkn.incidenttracker.integrations.gitlab;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabRepositoryEndpointService {

    private static final int DEFAULT_MAX_SCANNED_FILES = 120;
    private static final int MAX_SCANNED_FILES = 250;
    private static final int MAX_CONTROLLER_FILE_CHARACTERS = 80_000;
    private static final int MAX_SIGNATURE_LINES = 8;

    private static final Set<String> SOURCE_FILE_SUFFIXES = Set.of(".java");
    private static final Set<String> METHOD_MODIFIERS = Set.of(
            "public",
            "protected",
            "private",
            "static",
            "final",
            "abstract",
            "synchronized",
            "default",
            "native",
            "strictfp"
    );
    private static final Set<String> CONTROL_METHOD_NAMES = Set.of(
            "if",
            "for",
            "while",
            "switch",
            "catch",
            "return",
            "new",
            "throw"
    );

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b(?:class|interface|record)\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern ANNOTATION_NAME_PATTERN = Pattern.compile("@([A-Za-z_$][\\w$.]*)");
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Pattern ATTRIBUTE_PATH_PATTERN = Pattern.compile("\\b(?:value|path)\\s*=\\s*(\\{[^}]*}|\"[^\"]*\")");
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern IDENTIFIER_BEFORE_PAREN_PATTERN = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(");

    private final GitLabRepositoryPort gitLabRepositoryPort;

    public GitLabRepositoryEndpointListResult listEndpoints(GitLabRepositoryEndpointListRequest request) {
        var group = required(request.group(), "group");
        var projectName = required(request.projectName(), "projectName");
        var branch = required(request.branch(), "branch");
        var endpointPathPrefix = normalizeEndpointPathPrefix(request.endpointPathPrefix());
        var httpMethod = normalizeHttpMethod(request.httpMethod());
        var sourcePathPrefix = normalizeSourcePathPrefix(request.sourcePathPrefix());
        var maxScannedFiles = normalizeMaxScannedFiles(request.maxScannedFiles());
        var limitations = new ArrayList<String>();

        var repositoryFiles = gitLabRepositoryPort.listRepositoryFiles(group, projectName, branch, sourcePathPrefix);
        var candidateFiles = candidateFiles(repositoryFiles);
        var scannedFiles = candidateFiles.stream()
                .limit(maxScannedFiles)
                .toList();
        var scannedFileLimitReached = candidateFiles.size() > scannedFiles.size();
        var endpoints = new ArrayList<GitLabRepositoryEndpoint>();

        for (var file : scannedFiles) {
            try {
                var content = gitLabRepositoryPort.readFile(
                        group,
                        projectName,
                        branch,
                        file.filePath(),
                        MAX_CONTROLLER_FILE_CHARACTERS
                );
                var fileLimitations = new ArrayList<String>();
                if (content.truncated()) {
                    fileLimitations.add("File content was truncated before endpoint parsing.");
                }
                endpoints.addAll(parseEndpointFile(projectName, content.filePath(), content.content(), fileLimitations));
            } catch (RuntimeException exception) {
                limitations.add("Could not read " + file.filePath() + ": " + safeMessage(exception));
            }
        }

        if (repositoryFiles.isEmpty()) {
            limitations.add("No repository files were returned by GitLab for the requested source path prefix.");
        }
        if (candidateFiles.isEmpty() && !repositoryFiles.isEmpty()) {
            limitations.add("Repository tree was read, but no Java source files were eligible for endpoint parsing.");
        }
        if (scannedFileLimitReached) {
            limitations.add("Endpoint parsing scanned the top %d of %d Java source files; narrow sourcePathPrefix for exhaustive inventory."
                    .formatted(scannedFiles.size(), candidateFiles.size()));
        }

        var filteredEndpoints = endpoints.stream()
                .filter(endpoint -> matchesHttpMethod(endpoint, httpMethod))
                .filter(endpoint -> matchesEndpointPathPrefix(endpoint, endpointPathPrefix))
                .sorted(Comparator.comparing(GitLabRepositoryEndpoint::path, Comparator.nullsLast(String::compareTo))
                        .thenComparing(GitLabRepositoryEndpoint::controllerClass, Comparator.nullsLast(String::compareTo))
                        .thenComparing(GitLabRepositoryEndpoint::handlerMethod, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (filteredEndpoints.isEmpty() && !endpoints.isEmpty()) {
            limitations.add("Endpoints were found, but none matched the requested endpointPathPrefix/httpMethod filters.");
        }

        return new GitLabRepositoryEndpointListResult(
                group,
                projectName,
                branch,
                endpointPathPrefix,
                httpMethod,
                sourcePathPrefix,
                candidateFiles.size(),
                scannedFiles.size(),
                scannedFileLimitReached,
                filteredEndpoints,
                List.copyOf(limitations)
        );
    }

    List<GitLabRepositoryEndpoint> parseEndpointFile(
            String projectName,
            String filePath,
            String content,
            List<String> inheritedLimitations
    ) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        var lines = content.lines().toList();
        var packageName = extractPackageName(content);
        var endpoints = new ArrayList<GitLabRepositoryEndpoint>();
        var pendingAnnotations = new ArrayList<AnnotationBlock>();
        var annotationBuffer = new StringBuilder();
        var annotationStartLine = 0;
        var annotationDepth = 0;
        var collectingAnnotation = false;
        ControllerContext controller = null;

        for (int index = 0; index < lines.size(); index++) {
            var rawLine = lines.get(index);
            var trimmedLine = rawLine.trim();

            if (collectingAnnotation) {
                annotationBuffer.append(' ').append(trimmedLine);
                annotationDepth += parenthesisDelta(trimmedLine);
                if (annotationDepth <= 0) {
                    pendingAnnotations.add(new AnnotationBlock(annotationBuffer.toString(), annotationStartLine));
                    collectingAnnotation = false;
                    annotationBuffer.setLength(0);
                }
                continue;
            }

            if (trimmedLine.startsWith("@")) {
                annotationStartLine = index + 1;
                annotationBuffer.setLength(0);
                annotationBuffer.append(trimmedLine);
                annotationDepth = parenthesisDelta(trimmedLine);
                if (annotationDepth <= 0) {
                    pendingAnnotations.add(new AnnotationBlock(annotationBuffer.toString(), annotationStartLine));
                    annotationBuffer.setLength(0);
                } else {
                    collectingAnnotation = true;
                }
                continue;
            }

            if (isIgnorableBetweenAnnotations(trimmedLine)) {
                continue;
            }

            var className = className(trimmedLine);
            if (className != null) {
                controller = buildControllerContext(packageName, className, pendingAnnotations, filePath);
                pendingAnnotations.clear();
                continue;
            }

            if (controller != null && hasEndpointMapping(pendingAnnotations)) {
                var signature = methodSignature(lines, index);
                if (signature != null) {
                    endpoints.addAll(buildEndpoints(
                            projectName,
                            filePath,
                            controller,
                            pendingAnnotations,
                            signature,
                            inheritedLimitations
                    ));
                    pendingAnnotations.clear();
                    continue;
                }
            }

            pendingAnnotations.clear();
        }

        return List.copyOf(endpoints);
    }

    private List<GitLabRepositoryEndpoint> buildEndpoints(
            String projectName,
            String filePath,
            ControllerContext controller,
            List<AnnotationBlock> methodAnnotationBlocks,
            MethodSignature signature,
            List<String> inheritedLimitations
    ) {
        var methodMappings = mappings(methodAnnotationBlocks);
        if (methodMappings.isEmpty()) {
            return List.of();
        }

        var classMappings = controller.classMappings().isEmpty()
                ? List.of(MappingDefinition.empty())
                : controller.classMappings();
        var endpoints = new ArrayList<GitLabRepositoryEndpoint>();

        for (var classMapping : classMappings) {
            for (var methodMapping : methodMappings) {
                var methods = methodMapping.httpMethods();
                var basePaths = pathsOrBlank(classMapping.paths());
                var methodPaths = pathsOrBlank(methodMapping.paths());

                for (var basePath : basePaths) {
                    for (var methodPath : methodPaths) {
                        var path = combinePaths(basePath, methodPath);
                        var pathExpression = combinePathExpression(classMapping.pathExpression(), methodMapping.pathExpression());
                        var limitations = new ArrayList<String>(inheritedLimitations != null ? inheritedLimitations : List.of());
                        if (StringUtils.hasText(pathExpression)) {
                            limitations.add("Endpoint path uses an expression or constant that was not fully resolved: " + pathExpression);
                        }
                        if (!controller.controllerLike()) {
                            limitations.add("Class has mapping annotations but was not explicitly marked as RestController/Controller.");
                        }

                        var annotations = deduplicate(joinLists(
                                controller.annotationNames(),
                                methodMapping.annotationNames()
                        ));
                        var endpointId = endpointId(methods, path, controller.qualifiedClassName(), signature.methodName());
                        var lineStart = methodMapping.lineStart();
                        var lineEnd = signature.lineEnd();
                        var confidence = confidence(pathExpression, controller.controllerLike(), inheritedLimitations);
                        var useCaseInput = new GitLabRepositoryEndpointUseCaseInput(
                                projectName,
                                endpointId,
                                methods,
                                path,
                                filePath,
                                lineStart,
                                lineEnd
                        );

                        endpoints.add(new GitLabRepositoryEndpoint(
                                endpointId,
                                methods,
                                path,
                                pathExpression,
                                controller.qualifiedClassName(),
                                signature.methodName(),
                                filePath,
                                lineStart,
                                lineEnd,
                                signature.parameters(),
                                responseTypes(signature.responseType()),
                                annotations,
                                confidence,
                                List.copyOf(limitations),
                                useCaseInput,
                                suggestedNextReads(projectName, filePath, lineStart, lineEnd)
                        ));
                    }
                }
            }
        }

        return List.copyOf(endpoints);
    }

    private ControllerContext buildControllerContext(
            String packageName,
            String className,
            List<AnnotationBlock> annotations,
            String filePath
    ) {
        var qualifiedClassName = StringUtils.hasText(packageName) ? packageName + "." + className : className;
        var annotationNames = annotationNames(annotations);
        var mappings = mappings(annotations);
        var controllerLike = annotationNames.stream().anyMatch(name ->
                "RestController".equals(name)
                        || "Controller".equals(name)
                        || "RequestMapping".equals(name)
        ) || likelyControllerFile(filePath);

        return new ControllerContext(
                qualifiedClassName,
                annotationNames,
                mappings,
                controllerLike
        );
    }

    private List<MappingDefinition> mappings(List<AnnotationBlock> annotationBlocks) {
        var mappings = new ArrayList<MappingDefinition>();
        for (var block : annotationBlocks) {
            var annotationName = annotationName(block.text());
            var httpMethods = httpMethods(annotationName, block.text());
            if (httpMethods.isEmpty()) {
                continue;
            }

            var paths = annotationPaths(block.text());
            mappings.add(new MappingDefinition(
                    httpMethods,
                    paths,
                    paths.isEmpty() ? pathExpression(block.text()) : null,
                    List.of(annotationName),
                    block.startLine()
            ));
        }
        return List.copyOf(mappings);
    }

    private List<String> annotationPaths(String annotationText) {
        var paths = new LinkedHashSet<String>();
        var attributeMatcher = ATTRIBUTE_PATH_PATTERN.matcher(annotationText);
        while (attributeMatcher.find()) {
            addPathLiterals(paths, attributeMatcher.group(1));
        }

        if (paths.isEmpty()) {
            var open = annotationText.indexOf('(');
            var close = annotationText.lastIndexOf(')');
            if (open >= 0 && close > open) {
                var arguments = annotationText.substring(open + 1, close).trim();
                if (arguments.startsWith("\"") || arguments.startsWith("{")) {
                    addPathLiterals(paths, arguments);
                }
            }
        }

        return List.copyOf(paths);
    }

    private void addPathLiterals(LinkedHashSet<String> paths, String expression) {
        var matcher = STRING_LITERAL_PATTERN.matcher(expression);
        while (matcher.find()) {
            var candidate = matcher.group(1);
            if (isPathLiteral(candidate)) {
                paths.add(candidate.trim());
            }
        }
    }

    private boolean isPathLiteral(String value) {
        if (value == null) {
            return false;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty()
                || trimmed.startsWith("/")
                || (!trimmed.contains("=")
                && !trimmed.contains(":")
                && !trimmed.contains(".")
                && !trimmed.contains("*")
                && trimmed.length() <= 120);
    }

    private String pathExpression(String annotationText) {
        var open = annotationText.indexOf('(');
        var close = annotationText.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        var expression = annotationText.substring(open + 1, close).trim();
        if (!StringUtils.hasText(expression) || expression.contains("\"")) {
            return null;
        }
        return abbreviate(expression, 220);
    }

    private List<String> httpMethods(String annotationName, String annotationText) {
        return switch (annotationName) {
            case "GetMapping" -> List.of("GET");
            case "PostMapping" -> List.of("POST");
            case "PutMapping" -> List.of("PUT");
            case "PatchMapping" -> List.of("PATCH");
            case "DeleteMapping" -> List.of("DELETE");
            case "RequestMapping" -> requestMappingMethods(annotationText);
            default -> List.of();
        };
    }

    private List<String> requestMappingMethods(String annotationText) {
        var methods = new LinkedHashSet<String>();
        var matcher = REQUEST_METHOD_PATTERN.matcher(annotationText);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods.isEmpty() ? List.of("ANY") : List.copyOf(methods);
    }

    private MethodSignature methodSignature(List<String> lines, int startIndex) {
        var text = new StringBuilder(lines.get(startIndex).trim());
        var endIndex = startIndex;
        while (!signatureTerminated(text.toString())
                && endIndex + 1 < lines.size()
                && endIndex - startIndex < MAX_SIGNATURE_LINES) {
            endIndex++;
            text.append(' ').append(lines.get(endIndex).trim());
        }

        var signatureText = stripMethodBody(text.toString());
        var methodName = methodName(signatureText);
        if (!StringUtils.hasText(methodName) || CONTROL_METHOD_NAMES.contains(methodName)) {
            return null;
        }

        return new MethodSignature(
                methodName,
                responseType(signatureText, methodName),
                parameters(signatureText),
                endIndex + 1
        );
    }

    private boolean signatureTerminated(String text) {
        return text.contains("{") || text.contains(";");
    }

    private String stripMethodBody(String signatureText) {
        var bodyIndex = signatureText.indexOf('{');
        var semicolonIndex = signatureText.indexOf(';');
        var endIndex = bodyIndex >= 0 ? bodyIndex : semicolonIndex;
        return endIndex >= 0 ? signatureText.substring(0, endIndex).trim() : signatureText.trim();
    }

    private String methodName(String signatureText) {
        var matcher = IDENTIFIER_BEFORE_PAREN_PATTERN.matcher(signatureText);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String responseType(String signatureText, String methodName) {
        var parenIndex = signatureText.indexOf('(');
        if (parenIndex < 0) {
            return null;
        }
        var beforeMethod = signatureText.substring(0, parenIndex).trim();
        if (!beforeMethod.endsWith(methodName)) {
            return null;
        }
        beforeMethod = beforeMethod.substring(0, beforeMethod.length() - methodName.length()).trim();
        if (beforeMethod.isEmpty()) {
            return null;
        }
        var tokens = new ArrayList<>(List.of(beforeMethod.split("\\s+")));
        while (!tokens.isEmpty() && METHOD_MODIFIERS.contains(tokens.get(0))) {
            tokens.remove(0);
        }
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.get(tokens.size() - 1);
    }

    private List<String> parameters(String signatureText) {
        var open = signatureText.indexOf('(');
        var close = signatureText.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return List.of();
        }
        var parameters = signatureText.substring(open + 1, close).trim();
        if (!StringUtils.hasText(parameters)) {
            return List.of();
        }
        return deduplicate(List.of(parameters.split("\\s*,\\s*")));
    }

    private List<String> responseTypes(String responseType) {
        return StringUtils.hasText(responseType) ? List.of(responseType) : List.of();
    }

    private List<GitLabRepositoryFile> candidateFiles(List<GitLabRepositoryFile> repositoryFiles) {
        return repositoryFiles.stream()
                .filter(file -> file != null && isSourceFile(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .sorted(Comparator.comparingInt((GitLabRepositoryFile file) -> candidateScore(file.filePath())).reversed()
                        .thenComparing(GitLabRepositoryFile::filePath))
                .toList();
    }

    private int candidateScore(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return 0;
        }
        var normalized = filePath.toLowerCase(Locale.ROOT).replace('\\', '/');
        var score = 0;
        if (normalized.endsWith("controller.java")) {
            score += 120;
        }
        if (normalized.endsWith("resource.java")) {
            score += 100;
        }
        if (normalized.endsWith("endpoint.java")) {
            score += 90;
        }
        if (normalized.contains("/controller/")) {
            score += 80;
        }
        if (normalized.contains("/api/") || normalized.contains("/web/") || normalized.contains("/rest/")) {
            score += 60;
        }
        if (normalized.contains("/adapter/in/") || normalized.contains("/inbound/")) {
            score += 30;
        }
        if (normalized.contains("/generated/")) {
            score -= 40;
        }
        return score;
    }

    private boolean likelyControllerFile(String filePath) {
        return candidateScore(filePath) >= 60;
    }

    private boolean isSourceFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        var normalized = filePath.toLowerCase(Locale.ROOT);
        return SOURCE_FILE_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    private boolean isTestSource(String filePath) {
        var normalized = filePath.toLowerCase(Locale.ROOT).replace('\\', '/');
        return normalized.contains("/src/test/") || normalized.contains("/test/");
    }

    private boolean hasEndpointMapping(List<AnnotationBlock> annotations) {
        return annotations.stream()
                .map(block -> annotationName(block.text()))
                .anyMatch(name -> !httpMethods(name, "").isEmpty());
    }

    private List<String> annotationNames(List<AnnotationBlock> annotations) {
        return deduplicate(annotations.stream()
                .map(block -> annotationName(block.text()))
                .filter(StringUtils::hasText)
                .toList());
    }

    private String annotationName(String annotationText) {
        var matcher = ANNOTATION_NAME_PATTERN.matcher(annotationText);
        if (!matcher.find()) {
            return "";
        }
        var qualifiedName = matcher.group(1);
        var dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex >= 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
    }

    private String className(String line) {
        var matcher = CLASS_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractPackageName(String content) {
        var matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isIgnorableBetweenAnnotations(String line) {
        return !StringUtils.hasText(line)
                || line.startsWith("//")
                || line.startsWith("/*")
                || line.startsWith("*");
    }

    private int parenthesisDelta(String line) {
        var delta = 0;
        for (var index = 0; index < line.length(); index++) {
            var character = line.charAt(index);
            if (character == '(') {
                delta++;
            } else if (character == ')') {
                delta--;
            }
        }
        return delta;
    }

    private List<String> pathsOrBlank(List<String> paths) {
        return paths == null || paths.isEmpty() ? List.of("") : paths;
    }

    private String combinePaths(String basePath, String methodPath) {
        var base = normalizePathPart(basePath);
        var method = normalizePathPart(methodPath);
        if (!StringUtils.hasText(base) && !StringUtils.hasText(method)) {
            return "/";
        }
        if (!StringUtils.hasText(base)) {
            return "/" + method;
        }
        if (!StringUtils.hasText(method)) {
            return "/" + base;
        }
        return "/" + base + "/" + method;
    }

    private String normalizePathPart(String value) {
        if (!StringUtils.hasText(value)) {
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

    private String combinePathExpression(String baseExpression, String methodExpression) {
        if (StringUtils.hasText(baseExpression) && StringUtils.hasText(methodExpression)) {
            return baseExpression + " + " + methodExpression;
        }
        if (StringUtils.hasText(baseExpression)) {
            return baseExpression;
        }
        return StringUtils.hasText(methodExpression) ? methodExpression : null;
    }

    private String endpointId(List<String> methods, String path, String controllerClass, String handlerMethod) {
        return String.join(",", methods) + " " + (StringUtils.hasText(path) ? path : "<unresolved-path>")
                + " -> " + controllerClass + "#" + handlerMethod;
    }

    private List<String> suggestedNextReads(String projectName, String filePath, int startLine, int endLine) {
        return List.of(
                "%s:%s lines %d-%d via gitlab_read_repository_file_chunk".formatted(
                        projectName,
                        filePath,
                        startLine,
                        Math.max(startLine, endLine)
                )
        );
    }

    private String confidence(String pathExpression, boolean controllerLike, List<String> inheritedLimitations) {
        if (!controllerLike || StringUtils.hasText(pathExpression)) {
            return "medium";
        }
        return inheritedLimitations != null && !inheritedLimitations.isEmpty() ? "medium" : "high";
    }

    private boolean matchesHttpMethod(GitLabRepositoryEndpoint endpoint, String httpMethod) {
        return !StringUtils.hasText(httpMethod)
                || endpoint.httpMethods().contains(httpMethod)
                || endpoint.httpMethods().contains("ANY");
    }

    private boolean matchesEndpointPathPrefix(GitLabRepositoryEndpoint endpoint, String endpointPathPrefix) {
        return !StringUtils.hasText(endpointPathPrefix)
                || (StringUtils.hasText(endpoint.path()) && endpoint.path().startsWith(endpointPathPrefix));
    }

    private String normalizeEndpointPathPrefix(String endpointPathPrefix) {
        if (!StringUtils.hasText(endpointPathPrefix)) {
            return null;
        }
        var normalized = endpointPathPrefix.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeHttpMethod(String httpMethod) {
        return StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeSourcePathPrefix(String sourcePathPrefix) {
        if (!StringUtils.hasText(sourcePathPrefix)) {
            return null;
        }
        var normalized = sourcePathPrefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int normalizeMaxScannedFiles(Integer maxScannedFiles) {
        if (maxScannedFiles == null || maxScannedFiles <= 0) {
            return DEFAULT_MAX_SCANNED_FILES;
        }
        return Math.min(maxScannedFiles, MAX_SCANNED_FILES);
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("GitLab endpoint listing requires " + label + ".");
        }
        return value.trim();
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private <T> List<T> joinLists(List<T> left, List<T> right) {
        var joined = new ArrayList<T>();
        if (left != null) {
            joined.addAll(left);
        }
        if (right != null) {
            joined.addAll(right);
        }
        return joined;
    }

    private List<String> deduplicate(List<String> values) {
        var deduplicated = new LinkedHashSet<String>();
        for (var value : values != null ? values : List.<String>of()) {
            if (StringUtils.hasText(value)) {
                deduplicated.add(value.trim());
            }
        }
        return List.copyOf(deduplicated);
    }

    private String abbreviate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private record AnnotationBlock(
            String text,
            int startLine
    ) {
    }

    private record ControllerContext(
            String qualifiedClassName,
            List<String> annotationNames,
            List<MappingDefinition> classMappings,
            boolean controllerLike
    ) {
    }

    private record MappingDefinition(
            List<String> httpMethods,
            List<String> paths,
            String pathExpression,
            List<String> annotationNames,
            int lineStart
    ) {
        private static MappingDefinition empty() {
            return new MappingDefinition(List.of("ANY"), List.of(""), null, List.of(), 1);
        }
    }

    private record MethodSignature(
            String methodName,
            String responseType,
            List<String> parameters,
            int lineEnd
    ) {
    }
}
