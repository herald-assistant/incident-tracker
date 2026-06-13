package pl.mkn.incidenttracker.integrations.gitlab;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GitLabRepositoryEndpointService {

    private static final int DEFAULT_MAX_SCANNED_FILES = 120;
    private static final int MAX_SCANNED_FILES = 250;
    private static final int MAX_CONTROLLER_FILE_CHARACTERS = 80_000;
    private static final int MAX_OPENAPI_FILE_CHARACTERS = 260_000;
    private static final int MAX_OPENAPI_FILES = 30;
    private static final int MAX_SIGNATURE_LINES = 8;

    private static final Set<String> SOURCE_FILE_SUFFIXES = Set.of(".java");
    private static final Set<String> OPENAPI_FILE_SUFFIXES = Set.of(".yaml", ".yml");
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "HEAD",
            "OPTIONS",
            "TRACE"
    );
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
    private final GitLabRepositoryAnalysisCache analysisCache;

    @Autowired
    public GitLabRepositoryEndpointService(
            GitLabRepositoryPort gitLabRepositoryPort,
            GitLabRepositoryAnalysisCache analysisCache
    ) {
        this.gitLabRepositoryPort = gitLabRepositoryPort;
        this.analysisCache = analysisCache;
    }

    public GitLabRepositoryEndpointService(GitLabRepositoryPort gitLabRepositoryPort) {
        this(gitLabRepositoryPort, null);
    }

    public GitLabRepositoryEndpointListResult listEndpoints(GitLabRepositoryEndpointListRequest request) {
        var group = required(request.group(), "group");
        var projectName = required(request.projectName(), "projectName");
        var branch = required(request.branch(), "branch");
        var endpointPathPrefix = normalizeEndpointPathPrefix(request.endpointPathPrefix());
        var httpMethod = normalizeHttpMethod(request.httpMethod());
        var sourcePathPrefix = normalizeSourcePathPrefix(request.sourcePathPrefix());
        var maxScannedFiles = normalizeMaxScannedFiles(request.maxScannedFiles());
        var inventory = endpointInventory(group, projectName, branch, sourcePathPrefix, maxScannedFiles);
        var limitations = new ArrayList<String>(inventory.limitations());

        var filteredEndpoints = inventory.endpoints().stream()
                .filter(endpoint -> matchesHttpMethod(endpoint, httpMethod))
                .filter(endpoint -> matchesEndpointPathPrefix(endpoint, endpointPathPrefix))
                .sorted(Comparator.comparing(GitLabRepositoryEndpoint::path, Comparator.nullsLast(String::compareTo))
                        .thenComparing(GitLabRepositoryEndpoint::controllerClass, Comparator.nullsLast(String::compareTo))
                        .thenComparing(GitLabRepositoryEndpoint::handlerMethod, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (filteredEndpoints.isEmpty() && !inventory.endpoints().isEmpty()) {
            limitations.add("Endpoints were found, but none matched the requested endpointPathPrefix/httpMethod filters.");
        }

        return new GitLabRepositoryEndpointListResult(
                group,
                projectName,
                branch,
                endpointPathPrefix,
                httpMethod,
                sourcePathPrefix,
                inventory.candidateFileCount(),
                inventory.scannedFileCount(),
                inventory.scannedFileLimitReached(),
                filteredEndpoints,
                List.copyOf(limitations)
        );
    }

    private EndpointInventory endpointInventory(
            String group,
            String projectName,
            String branch,
            String sourcePathPrefix,
            int maxScannedFiles
    ) {
        if (analysisCache == null) {
            return buildEndpointInventory(group, projectName, branch, sourcePathPrefix, maxScannedFiles);
        }

        return analysisCache.getOrCompute(
                "gitlab.repository-endpoint-inventory",
                Arrays.asList(group, projectName, branch, sourcePathPrefix, maxScannedFiles),
                () -> buildEndpointInventory(group, projectName, branch, sourcePathPrefix, maxScannedFiles)
        );
    }

    private EndpointInventory buildEndpointInventory(
            String group,
            String projectName,
            String branch,
            String sourcePathPrefix,
            int maxScannedFiles
    ) {
        var limitations = new ArrayList<String>();

        var repositoryFiles = gitLabRepositoryPort.listRepositoryFiles(group, projectName, branch, sourcePathPrefix);
        var contractRepositoryFiles = sourcePathPrefix == null
                ? repositoryFiles
                : gitLabRepositoryPort.listRepositoryFiles(group, projectName, branch, null);
        var candidateFiles = candidateFiles(repositoryFiles);
        var scannedFiles = candidateFiles.stream()
                .limit(maxScannedFiles)
                .toList();
        var openApiFiles = openApiCandidateFiles(contractRepositoryFiles);
        var scannedOpenApiFiles = openApiFiles.stream()
                .limit(MAX_OPENAPI_FILES)
                .toList();
        var scannedFileLimitReached = candidateFiles.size() > scannedFiles.size();
        var endpoints = new ArrayList<GitLabRepositoryEndpoint>();
        var controllerImplementations = new ArrayList<ControllerImplementation>();
        var openApiOperations = new ArrayList<OpenApiOperation>();

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
                controllerImplementations.addAll(parseControllerImplementations(
                        content.filePath(),
                        content.content()
                ));
            } catch (RuntimeException exception) {
                limitations.add("Could not read " + file.filePath() + ": " + safeMessage(exception));
            }
        }

        for (var file : scannedOpenApiFiles) {
            try {
                var content = gitLabRepositoryPort.readFile(
                        group,
                        projectName,
                        branch,
                        file.filePath(),
                        MAX_OPENAPI_FILE_CHARACTERS
                );
                var fileLimitations = new ArrayList<String>();
                if (content.truncated()) {
                    fileLimitations.add("OpenAPI contract file content was truncated before endpoint parsing.");
                }
                openApiOperations.addAll(parseOpenApiOperations(content.filePath(), content.content(), fileLimitations));
            } catch (RuntimeException exception) {
                limitations.add("Could not read OpenAPI contract " + file.filePath() + ": " + safeMessage(exception));
            }
        }

        if (openApiFiles.size() > scannedOpenApiFiles.size()) {
            limitations.add("OpenAPI endpoint parsing scanned the top %d of %d YAML contract files."
                    .formatted(scannedOpenApiFiles.size(), openApiFiles.size()));
        }

        endpoints.addAll(openApiBackedEndpoints(projectName, openApiOperations, controllerImplementations, endpoints));

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

        return new EndpointInventory(
                candidateFiles.size(),
                scannedFiles.size(),
                scannedFileLimitReached,
                endpoints,
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

    private List<ControllerImplementation> parseControllerImplementations(String filePath, String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        var lines = content.lines().toList();
        var packageName = extractPackageName(content);
        var implementations = new ArrayList<ControllerImplementation>();
        var pendingAnnotations = new ArrayList<AnnotationBlock>();
        var annotationBuffer = new StringBuilder();
        var annotationStartLine = 0;
        var annotationDepth = 0;
        var collectingAnnotation = false;
        ControllerImplementationContext controller = null;

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

            var classDeclaration = classDeclaration(lines, index);
            var className = className(classDeclaration);
            if (className != null) {
                controller = buildControllerImplementationContext(
                        packageName,
                        className,
                        classDeclaration,
                        pendingAnnotations
                );
                pendingAnnotations.clear();
                continue;
            }

            if (controller != null) {
                var signature = methodSignature(lines, index);
                if (signature != null) {
                    implementations.add(new ControllerImplementation(
                            controller.qualifiedClassName(),
                            controller.annotationNames(),
                            controller.implementedInterfaces(),
                            filePath,
                            signature
                    ));
                    pendingAnnotations.clear();
                    continue;
                }
            }

            pendingAnnotations.clear();
        }

        return List.copyOf(implementations);
    }

    private ControllerImplementationContext buildControllerImplementationContext(
            String packageName,
            String className,
            String classDeclaration,
            List<AnnotationBlock> annotations
    ) {
        var annotationNames = annotationNames(annotations);
        if (annotationNames.contains("FeignClient")
                || annotationNames.stream().noneMatch(name ->
                "RestController".equals(name) || "Controller".equals(name))) {
            return null;
        }

        var implementedInterfaces = implementedInterfaces(classDeclaration);
        if (implementedInterfaces.isEmpty()) {
            return null;
        }

        var qualifiedClassName = StringUtils.hasText(packageName) ? packageName + "." + className : className;
        return new ControllerImplementationContext(
                qualifiedClassName,
                annotationNames,
                implementedInterfaces
        );
    }

    private List<GitLabRepositoryEndpoint> openApiBackedEndpoints(
            String projectName,
            List<OpenApiOperation> openApiOperations,
            List<ControllerImplementation> controllerImplementations,
            List<GitLabRepositoryEndpoint> existingEndpoints
    ) {
        if (openApiOperations.isEmpty() || controllerImplementations.isEmpty()) {
            return List.of();
        }

        var existingEndpointIds = existingEndpoints.stream()
                .map(GitLabRepositoryEndpoint::endpointId)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        var endpoints = new ArrayList<GitLabRepositoryEndpoint>();

        for (var operation : openApiOperations) {
            for (var implementation : controllerImplementations) {
                var matchedInterface = matchedOpenApiInterface(operation, implementation);
                if (!StringUtils.hasText(matchedInterface)) {
                    continue;
                }

                var endpointId = endpointId(
                        List.of(operation.httpMethod()),
                        operation.path(),
                        implementation.qualifiedClassName(),
                        implementation.signature().methodName()
                );
                if (existingEndpointIds.contains(endpointId)) {
                    continue;
                }

                existingEndpointIds.add(endpointId);
                var lineStart = implementation.signature().lineStart();
                var lineEnd = implementation.signature().lineEnd();
                var annotations = deduplicate(joinLists(
                        implementation.annotationNames(),
                        List.of(
                                "OpenApiContract",
                                "Implements " + matchedInterface,
                                "OperationId " + operation.operationId()
                        )
                ));
                var limitations = new ArrayList<String>(operation.limitations());
                limitations.add("Endpoint mapping resolved from OpenAPI YAML contract, not Java annotations.");
                var useCaseInput = new GitLabRepositoryEndpointUseCaseInput(
                        projectName,
                        endpointId,
                        List.of(operation.httpMethod()),
                        operation.path(),
                        implementation.filePath(),
                        lineStart,
                        lineEnd
                );

                endpoints.add(new GitLabRepositoryEndpoint(
                        endpointId,
                        List.of(operation.httpMethod()),
                        operation.path(),
                        null,
                        implementation.qualifiedClassName(),
                        implementation.signature().methodName(),
                        implementation.filePath(),
                        lineStart,
                        lineEnd,
                        implementation.signature().parameters(),
                        responseTypes(implementation.signature().responseType()),
                        annotations,
                        "high",
                        List.copyOf(limitations),
                        useCaseInput,
                        suggestedNextReads(projectName, operation, implementation)
                ));
            }
        }

        return List.copyOf(endpoints);
    }

    private String matchedOpenApiInterface(OpenApiOperation operation, ControllerImplementation implementation) {
        if (!implementation.signature().methodName().equals(operation.operationId())) {
            return null;
        }

        for (var tag : operation.tags()) {
            var expectedInterface = normalizeTypeName(tag) + "Api";
            for (var implementedInterface : implementation.implementedInterfaces()) {
                if (expectedInterface.equals(simpleName(implementedInterface))) {
                    return implementedInterface;
                }
            }
        }

        return null;
    }

    private List<String> suggestedNextReads(
            String projectName,
            OpenApiOperation operation,
            ControllerImplementation implementation
    ) {
        return List.of(
                "%s:%s lines %d-%d via gitlab_read_repository_file_chunk".formatted(
                        projectName,
                        operation.filePath(),
                        operation.lineStart(),
                        operation.lineStart()
                ),
                "%s:%s lines %d-%d via gitlab_read_repository_file_chunk".formatted(
                        projectName,
                        implementation.filePath(),
                        implementation.signature().lineStart(),
                        Math.max(implementation.signature().lineStart(), implementation.signature().lineEnd())
                )
        );
    }

    private List<OpenApiOperation> parseOpenApiOperations(
            String filePath,
            String content,
            List<String> inheritedLimitations
    ) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        var lines = content.lines().toList();
        var operations = new ArrayList<OpenApiOperation>();
        var limitations = inheritedLimitations != null ? inheritedLimitations : List.<String>of();
        var inPaths = false;
        var pathsIndent = -1;
        var currentPath = (String) null;
        var currentPathLine = 0;
        var currentOperation = (OpenApiOperationBuilder) null;
        var collectingTags = false;
        var tagsIndent = -1;

        for (int index = 0; index < lines.size(); index++) {
            var rawLine = lines.get(index);
            var trimmedLine = rawLine.trim();
            if (!StringUtils.hasText(trimmedLine) || trimmedLine.startsWith("#")) {
                continue;
            }

            var indent = leadingSpaces(rawLine);
            if (!inPaths) {
                if ("paths:".equals(trimmedLine)) {
                    inPaths = true;
                    pathsIndent = indent;
                }
                continue;
            }

            if (indent <= pathsIndent && !"paths:".equals(trimmedLine)) {
                addOpenApiOperation(operations, currentOperation, limitations);
                currentOperation = null;
                break;
            }

            if (indent == pathsIndent + 2 && isOpenApiPathKey(trimmedLine)) {
                addOpenApiOperation(operations, currentOperation, limitations);
                currentOperation = null;
                currentPath = yamlKey(trimmedLine);
                currentPathLine = index + 1;
                collectingTags = false;
                continue;
            }

            if (currentPath != null && indent == pathsIndent + 4 && isOpenApiHttpMethodKey(trimmedLine)) {
                addOpenApiOperation(operations, currentOperation, limitations);
                currentOperation = new OpenApiOperationBuilder(
                        filePath,
                        currentPath,
                        yamlKey(trimmedLine).toUpperCase(Locale.ROOT),
                        currentPathLine
                );
                collectingTags = false;
                continue;
            }

            if (currentOperation == null || indent <= pathsIndent + 4) {
                collectingTags = false;
                continue;
            }

            if (trimmedLine.startsWith("operationId:")) {
                currentOperation.operationId(yamlValue(trimmedLine));
                collectingTags = false;
                continue;
            }

            if (trimmedLine.startsWith("tags:")) {
                var inlineTags = inlineYamlList(trimmedLine);
                currentOperation.addTags(inlineTags);
                collectingTags = inlineTags.isEmpty();
                tagsIndent = indent;
                continue;
            }

            if (collectingTags) {
                if (indent > tagsIndent && trimmedLine.startsWith("-")) {
                    currentOperation.addTag(yamlListItem(trimmedLine));
                } else {
                    collectingTags = false;
                }
            }
        }

        addOpenApiOperation(operations, currentOperation, limitations);
        return List.copyOf(operations);
    }

    private void addOpenApiOperation(
            List<OpenApiOperation> operations,
            OpenApiOperationBuilder builder,
            List<String> inheritedLimitations
    ) {
        if (builder == null || !StringUtils.hasText(builder.operationId())) {
            return;
        }
        operations.add(new OpenApiOperation(
                builder.filePath(),
                builder.path(),
                builder.httpMethod(),
                builder.operationId(),
                builder.tags(),
                builder.lineStart(),
                inheritedLimitations
        ));
    }

    private ControllerContext buildControllerContext(
            String packageName,
            String className,
            List<AnnotationBlock> annotations,
            String filePath
    ) {
        var qualifiedClassName = StringUtils.hasText(packageName) ? packageName + "." + className : className;
        var annotationNames = annotationNames(annotations);
        if (annotationNames.contains("FeignClient")) {
            return null;
        }
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
                startIndex + 1,
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

    private List<GitLabRepositoryFile> openApiCandidateFiles(List<GitLabRepositoryFile> repositoryFiles) {
        return repositoryFiles.stream()
                .filter(file -> file != null && isOpenApiSpecFile(file.filePath()))
                .sorted(Comparator.comparingInt((GitLabRepositoryFile file) -> openApiCandidateScore(file.filePath())).reversed()
                        .thenComparing(GitLabRepositoryFile::filePath))
                .toList();
    }

    private int openApiCandidateScore(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return 0;
        }
        var normalized = filePath.toLowerCase(Locale.ROOT).replace('\\', '/');
        var score = 0;
        if (normalized.contains("openapi") || normalized.contains("swagger")) {
            score += 120;
        }
        if (normalized.endsWith("_api.yaml") || normalized.endsWith("_api.yml")
                || normalized.endsWith("-api.yaml") || normalized.endsWith("-api.yml")) {
            score += 100;
        }
        if (normalized.contains("/api/") || normalized.contains("/openapi/") || normalized.contains("/contract/")) {
            score += 80;
        }
        if (normalized.contains("/src/main/resources/")) {
            score += 40;
        }
        return score;
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

    private boolean isOpenApiSpecFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        var normalized = filePath.toLowerCase(Locale.ROOT);
        return OPENAPI_FILE_SUFFIXES.stream().anyMatch(normalized::endsWith);
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

    private String classDeclaration(List<String> lines, int startIndex) {
        var text = new StringBuilder(lines.get(startIndex).trim());
        var endIndex = startIndex;
        while (!text.toString().contains("{")
                && endIndex + 1 < lines.size()
                && endIndex - startIndex < MAX_SIGNATURE_LINES) {
            endIndex++;
            text.append(' ').append(lines.get(endIndex).trim());
        }
        return text.toString();
    }

    private List<String> implementedInterfaces(String classDeclaration) {
        if (!StringUtils.hasText(classDeclaration)) {
            return List.of();
        }

        var implementsIndex = classDeclaration.indexOf(" implements ");
        if (implementsIndex < 0) {
            return List.of();
        }

        var declarationTail = classDeclaration.substring(implementsIndex + " implements ".length());
        var bodyIndex = declarationTail.indexOf('{');
        if (bodyIndex >= 0) {
            declarationTail = declarationTail.substring(0, bodyIndex);
        }

        return deduplicate(List.of(declarationTail.split("\\s*,\\s*")).stream()
                .map(this::cleanImplementedInterface)
                .filter(StringUtils::hasText)
                .toList());
    }

    private String cleanImplementedInterface(String implementedInterface) {
        if (!StringUtils.hasText(implementedInterface)) {
            return null;
        }
        var cleaned = implementedInterface.trim();
        var genericIndex = cleaned.indexOf('<');
        if (genericIndex >= 0) {
            cleaned = cleaned.substring(0, genericIndex);
        }
        var tokens = cleaned.split("\\s+");
        return tokens.length > 0 ? tokens[tokens.length - 1].trim() : null;
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

    private int leadingSpaces(String value) {
        var spaces = 0;
        while (spaces < value.length() && value.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    private boolean isOpenApiPathKey(String trimmedLine) {
        var key = yamlKey(trimmedLine);
        return StringUtils.hasText(key) && key.startsWith("/");
    }

    private boolean isOpenApiHttpMethodKey(String trimmedLine) {
        var key = yamlKey(trimmedLine);
        return StringUtils.hasText(key) && HTTP_METHODS.contains(key.toUpperCase(Locale.ROOT));
    }

    private String yamlKey(String trimmedLine) {
        var colonIndex = trimmedLine.indexOf(':');
        if (colonIndex < 0) {
            return "";
        }
        return unquote(trimmedLine.substring(0, colonIndex).trim());
    }

    private String yamlValue(String trimmedLine) {
        var colonIndex = trimmedLine.indexOf(':');
        if (colonIndex < 0 || colonIndex + 1 >= trimmedLine.length()) {
            return "";
        }
        return unquote(stripInlineComment(trimmedLine.substring(colonIndex + 1).trim()));
    }

    private List<String> inlineYamlList(String trimmedLine) {
        var value = yamlValue(trimmedLine);
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return List.of();
        }
        var inner = value.substring(1, value.length() - 1).trim();
        if (!StringUtils.hasText(inner)) {
            return List.of();
        }
        return deduplicate(List.of(inner.split("\\s*,\\s*")).stream()
                .map(this::unquote)
                .filter(StringUtils::hasText)
                .toList());
    }

    private String yamlListItem(String trimmedLine) {
        return unquote(stripInlineComment(trimmedLine.substring(1).trim()));
    }

    private String stripInlineComment(String value) {
        var commentIndex = value.indexOf(" #");
        return commentIndex >= 0 ? value.substring(0, commentIndex).trim() : value;
    }

    private String unquote(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = value.trim();
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private String normalizeTypeName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = new StringBuilder();
        var uppercaseNext = true;
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(uppercaseNext ? Character.toUpperCase(character) : character);
                uppercaseNext = false;
            } else {
                uppercaseNext = true;
            }
        }
        return normalized.toString();
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

    private record ControllerImplementationContext(
            String qualifiedClassName,
            List<String> annotationNames,
            List<String> implementedInterfaces
    ) {
        private ControllerImplementationContext {
            annotationNames = annotationNames != null ? List.copyOf(annotationNames) : List.of();
            implementedInterfaces = implementedInterfaces != null ? List.copyOf(implementedInterfaces) : List.of();
        }
    }

    private record ControllerImplementation(
            String qualifiedClassName,
            List<String> annotationNames,
            List<String> implementedInterfaces,
            String filePath,
            MethodSignature signature
    ) {
        private ControllerImplementation {
            annotationNames = annotationNames != null ? List.copyOf(annotationNames) : List.of();
            implementedInterfaces = implementedInterfaces != null ? List.copyOf(implementedInterfaces) : List.of();
        }
    }

    private record OpenApiOperation(
            String filePath,
            String path,
            String httpMethod,
            String operationId,
            List<String> tags,
            int lineStart,
            List<String> limitations
    ) {
        private OpenApiOperation {
            tags = tags != null ? List.copyOf(tags) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }

    private record EndpointInventory(
            int candidateFileCount,
            int scannedFileCount,
            boolean scannedFileLimitReached,
            List<GitLabRepositoryEndpoint> endpoints,
            List<String> limitations
    ) {
        private EndpointInventory {
            endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }

    private static final class OpenApiOperationBuilder {

        private final String filePath;
        private final String path;
        private final String httpMethod;
        private final int lineStart;
        private final List<String> tags = new ArrayList<>();
        private String operationId;

        private OpenApiOperationBuilder(String filePath, String path, String httpMethod, int lineStart) {
            this.filePath = filePath;
            this.path = path;
            this.httpMethod = httpMethod;
            this.lineStart = lineStart;
        }

        private String filePath() {
            return filePath;
        }

        private String path() {
            return path;
        }

        private String httpMethod() {
            return httpMethod;
        }

        private int lineStart() {
            return lineStart;
        }

        private String operationId() {
            return operationId;
        }

        private void operationId(String operationId) {
            this.operationId = operationId;
        }

        private List<String> tags() {
            return List.copyOf(tags);
        }

        private void addTag(String tag) {
            if (StringUtils.hasText(tag)) {
                tags.add(tag.trim());
            }
        }

        private void addTags(List<String> tags) {
            for (var tag : tags != null ? tags : List.<String>of()) {
                addTag(tag);
            }
        }
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
            int lineStart,
            int lineEnd
    ) {
    }
}
