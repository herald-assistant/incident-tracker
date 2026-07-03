package pl.mkn.tdw.integrations.gitlab;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GitLabRepositoryEndpointService {

    private static final int DEFAULT_MAX_SCANNED_FILES = 120;
    private static final int MAX_SCANNED_FILES = 250;
    private static final int ENDPOINT_SEARCH_RESULTS_PER_TERM = 100;
    private static final int OPENAPI_SEARCH_RESULTS_PER_TERM = 50;
    private static final int MAX_CONTROLLER_FILE_CHARACTERS = 80_000;
    private static final int MAX_CONSTANT_FILE_CHARACTERS = 80_000;
    private static final int MAX_STATIC_CONSTANT_IMPORT_DEPTH = 4;
    private static final int MAX_OPENAPI_FILE_CHARACTERS = 260_000;
    private static final int MAX_OPENAPI_FILES = 40;
    private static final int MAX_SIGNATURE_LINES = 8;
    private static final int MAX_DOCUMENTATION_TEXT_LENGTH = 700;
    private static final int MAX_PARAMETER_DESCRIPTION_LENGTH = 360;

    private static final String DOCUMENTATION_SOURCE_OPENAPI_YAML = "OPENAPI_YAML";
    private static final String DOCUMENTATION_SOURCE_JAVA_OPENAPI_ANNOTATION = "JAVA_OPENAPI_ANNOTATION";
    private static final String DOCUMENTATION_SOURCE_SPRING_SIGNATURE = "SPRING_SIGNATURE";

    private static final Set<String> SOURCE_FILE_SUFFIXES = Set.of(".java");
    private static final Set<String> OPENAPI_FILE_SUFFIXES = Set.of(".yaml", ".yml");
    private static final List<String> ENDPOINT_DISCOVERY_TERMS = List.of(
            "@RestController",
            "@Controller",
            "@RequestMapping",
            "RouterFunctions.route",
            "RouterFunction<",
            "@RepositoryRestResource"
    );
    private static final List<String> OPENAPI_DISCOVERY_TERMS = List.of(
            "openapi:",
            "swagger:"
    );
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
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern IDENTIFIER_BEFORE_PAREN_PATTERN = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(?!static\\b)([\\w.]+|[\\w.]+\\.\\*)\\s*;"
    );
    private static final Pattern STATIC_IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+static\\s+([\\w.]+)\\.([A-Za-z_$][\\w$]*|\\*)\\s*;"
    );
    private static final Pattern CLASS_QUALIFIED_REFERENCE_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9_$]*)\\.([A-Za-z_$][\\w$]*)\\b"
    );
    private static final Pattern CONSTANT_REFERENCE_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Z0-9_$]*(?:_[A-Z0-9_$]+)*)\\b"
    );
    private static final Pattern STRING_CONSTANT_PATTERN = Pattern.compile(
            "(?s)\\b((?:(?:public|protected|private)\\s+)?(?:(?:static|final)\\s+)+String)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;]+);"
    );
    private static final Set<String> ENDPOINT_CONSTANT_ANNOTATIONS = Set.of(
            "RequestMapping",
            "GetMapping",
            "PostMapping",
            "PutMapping",
            "PatchMapping",
            "DeleteMapping",
            "PathVariable",
            "RequestParam"
    );

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
        var maxScannedFiles = normalizeMaxScannedFiles(request.maxScannedFiles());
        var inventory = endpointInventory(group, projectName, branch, maxScannedFiles, request.refreshCache());
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
                inventory.dataCollectedAt(),
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
            int maxScannedFiles,
            boolean refreshCache
    ) {
        if (analysisCache == null) {
            return buildEndpointInventory(group, projectName, branch, maxScannedFiles);
        }

        var keyParts = List.of(group, projectName, branch, maxScannedFiles);
        if (refreshCache) {
            analysisCache.evict("gitlab.repository-endpoint-inventory", keyParts);
        }

        return analysisCache.getOrCompute(
                "gitlab.repository-endpoint-inventory",
                keyParts,
                () -> buildEndpointInventory(group, projectName, branch, maxScannedFiles)
        );
    }

    private EndpointInventory buildEndpointInventory(
            String group,
            String projectName,
            String branch,
            int maxScannedFiles
    ) {
        var limitations = new ArrayList<String>();

        var endpointDiscovery = endpointCandidateFiles(group, projectName, branch, limitations);
        var candidateFiles = endpointDiscovery.files();
        var scannedFiles = candidateFiles.stream()
                .limit(maxScannedFiles)
                .toList();
        var openApiFiles = openApiCandidateFiles(
                group,
                projectName,
                branch,
                endpointDiscovery.repositoryTreeFiles(),
                limitations
        );
        var scannedFileLimitReached = candidateFiles.size() > scannedFiles.size();
        var endpoints = new ArrayList<GitLabRepositoryEndpoint>();
        var controllerImplementations = new ArrayList<ControllerImplementation>();
        var openApiOperations = new ArrayList<OpenApiOperation>();
        var openApiDocumentCount = 0;
        var constantResolutionContext = new JavaStringConstantResolutionContext(endpointDiscovery.repositoryTreeFiles());

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
                var constantResolver = javaStringConstantResolver(
                        group,
                        projectName,
                        branch,
                        content.filePath(),
                        content.content(),
                        fileLimitations,
                        constantResolutionContext
                );
                endpoints.addAll(parseEndpointFile(
                        projectName,
                        content.filePath(),
                        content.content(),
                        fileLimitations,
                        constantResolver
                ));
                controllerImplementations.addAll(parseControllerImplementations(
                        content.filePath(),
                        content.content()
                ));
            } catch (RuntimeException exception) {
                limitations.add("Could not read " + file.filePath() + ": " + safeMessage(exception));
            }
        }

        for (var file : openApiFiles) {
            try {
                var content = gitLabRepositoryPort.readFile(
                        group,
                        projectName,
                        branch,
                        file.filePath(),
                        MAX_OPENAPI_FILE_CHARACTERS
                );
                if (!hasOpenApiRootMarker(content.content())) {
                    continue;
                }
                openApiDocumentCount++;
                if (openApiDocumentCount > MAX_OPENAPI_FILES) {
                    continue;
                }
                var fileLimitations = new ArrayList<String>();
                if (content.truncated()) {
                    fileLimitations.add("OpenAPI contract file content was truncated before endpoint parsing.");
                }
                openApiOperations.addAll(parseOpenApiOperations(content.filePath(), content.content(), fileLimitations));
            } catch (RuntimeException exception) {
                limitations.add("Could not read OpenAPI contract " + file.filePath() + ": " + safeMessage(exception));
            }
        }

        if (openApiDocumentCount > MAX_OPENAPI_FILES) {
            limitations.add("OpenAPI endpoint parsing scanned the top %d of %d OpenAPI YAML files."
                    .formatted(MAX_OPENAPI_FILES, openApiDocumentCount));
        }

        endpoints = new ArrayList<>(mergeOpenApiDocumentation(endpoints, openApiOperations));
        endpoints.addAll(openApiBackedEndpoints(projectName, openApiOperations, controllerImplementations, endpoints));

        if (candidateFiles.isEmpty()) {
            limitations.add("No Spring REST endpoint candidate files were found by GitLab search or repository tree fallback.");
        }
        if (scannedFileLimitReached) {
            limitations.add("Endpoint parsing scanned the top %d of %d Spring REST endpoint candidate files; increase maxScannedFiles for broader inventory."
                    .formatted(scannedFiles.size(), candidateFiles.size()));
        }

        return new EndpointInventory(
                Instant.now(),
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
        return parseEndpointFile(
                projectName,
                filePath,
                content,
                inheritedLimitations,
                javaStringConstantResolver(null, projectName, null, filePath, content, inheritedLimitations)
        );
    }

    private List<GitLabRepositoryEndpoint> parseEndpointFile(
            String projectName,
            String filePath,
            String content,
            List<String> inheritedLimitations,
            JavaStringConstantResolver constantResolver
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
                controller = buildControllerContext(packageName, className, pendingAnnotations, filePath, constantResolver);
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
                            inheritedLimitations,
                            constantResolver
                    ));
                    pendingAnnotations.clear();
                    continue;
                }
            }

            pendingAnnotations.clear();
        }

        return List.copyOf(endpoints);
    }

    private JavaStringConstantResolver javaStringConstantResolver(
            String group,
            String projectName,
            String branch,
            String filePath,
            String content,
            List<String> limitations
    ) {
        return javaStringConstantResolver(
                group,
                projectName,
                branch,
                filePath,
                content,
                limitations,
                null
        );
    }

    private JavaStringConstantResolver javaStringConstantResolver(
            String group,
            String projectName,
            String branch,
            String filePath,
            String content,
            List<String> limitations,
            JavaStringConstantResolutionContext resolutionContext
    ) {
        var constants = new LinkedHashMap<String, String>();
        var diagnostics = new ArrayList<String>();
        addStringConstants(constants, extractPackageName(content), firstDeclaredTypeName(content), content);

        if (!StringUtils.hasText(group) || !StringUtils.hasText(projectName) || !StringUtils.hasText(branch)) {
            return new JavaStringConstantResolver(constants, diagnostics);
        }

        addImportedStringConstants(
                group,
                projectName,
                branch,
                filePath,
                content,
                constants,
                diagnostics,
                resolutionContext,
                new LinkedHashSet<>(),
                0
        );

        return new JavaStringConstantResolver(constants, diagnostics);
    }

    private void addImportedStringConstants(
            String group,
            String projectName,
            String branch,
            String filePath,
            String content,
            Map<String, String> constants,
            List<String> diagnostics,
            JavaStringConstantResolutionContext resolutionContext,
            Set<String> visitedImportedClasses,
            int depth
    ) {
        var importedClasses = depth == 0
                ? endpointConstantClasses(content)
                : stringConstantDependencyClasses(content);

        if (depth >= MAX_STATIC_CONSTANT_IMPORT_DEPTH) {
            if (!importedClasses.isEmpty()) {
                diagnostics.add("Java string constant import traversal reached depth limit at " + filePath + ".");
            }
            return;
        }

        for (var importedClass : importedClasses) {
            if (!visitedImportedClasses.add(importedClass)) {
                continue;
            }

            var importedContent = readJavaStringConstantFile(
                    group,
                    projectName,
                    branch,
                    filePath,
                    importedClass,
                    diagnostics,
                    resolutionContext
            );
            if (importedContent == null) {
                continue;
            }
            if (importedContent.truncated()) {
                diagnostics.add("Java string constants file was truncated before endpoint parsing: "
                        + importedContent.filePath());
            }
            addStringConstants(
                    constants,
                    packageName(importedClass),
                    simpleName(importedClass),
                    importedContent.content()
            );
            addImportedStringConstants(
                    group,
                    projectName,
                    branch,
                    importedContent.filePath(),
                    importedContent.content(),
                    constants,
                    diagnostics,
                    resolutionContext,
                    visitedImportedClasses,
                    depth + 1
            );
        }
    }

    private Set<String> endpointConstantClasses(String content) {
        return javaStringConstantClassesForExpressions(content, endpointAnnotationConstantExpressions(content));
    }

    private Set<String> stringConstantDependencyClasses(String content) {
        return javaStringConstantClassesForExpressions(content, stringConstantExpressions(content));
    }

    private Set<String> javaStringConstantClassesForExpressions(String content, List<String> expressions) {
        var importedClasses = new LinkedHashSet<String>();
        if (expressions == null || expressions.isEmpty()) {
            return importedClasses;
        }

        var packageName = extractPackageName(content);
        var normalImports = javaImportClasses(content);
        var staticImports = new LinkedHashMap<String, String>();
        var wildcardStaticImports = new LinkedHashSet<String>();

        var staticImportMatcher = STATIC_IMPORT_PATTERN.matcher(content != null ? content : "");
        while (staticImportMatcher.find()) {
            var importedClass = staticImportMatcher.group(1);
            var importedMember = staticImportMatcher.group(2);
            if ("*".equals(importedMember)) {
                wildcardStaticImports.add(importedClass);
            } else {
                staticImports.putIfAbsent(importedMember, importedClass);
            }
        }

        for (var reference : javaStringConstantReferences(expressions)) {
            if (StringUtils.hasText(reference.ownerClassName())) {
                if (!likelyEndpointConstantImport(reference.ownerClassName(), reference.memberName())) {
                    continue;
                }
                var importedClass = normalImports.get(reference.ownerClassName());
                if (StringUtils.hasText(importedClass)) {
                    importedClasses.add(importedClass);
                } else if (StringUtils.hasText(packageName)) {
                    importedClasses.add(packageName + "." + reference.ownerClassName());
                }
                continue;
            }

            var importedClass = staticImports.get(reference.memberName());
            if (StringUtils.hasText(importedClass)) {
                if (likelyEndpointConstantImport(importedClass, reference.memberName())) {
                    importedClasses.add(importedClass);
                }
            }
            for (var wildcardImportedClass : wildcardStaticImports) {
                if (likelyEndpointConstantImport(wildcardImportedClass, reference.memberName())) {
                    importedClasses.add(wildcardImportedClass);
                }
            }
        }
        return importedClasses;
    }

    private List<String> endpointAnnotationConstantExpressions(String content) {
        var expressions = new LinkedHashSet<String>();
        for (var annotationText : javaAnnotationSegments(content, ENDPOINT_CONSTANT_ANNOTATIONS)) {
            var annotationName = annotationName(annotationText);
            if (!httpMethods(annotationName, annotationText).isEmpty()) {
                expressions.addAll(annotationPathExpressions(annotationText));
                continue;
            }
            if ("PathVariable".equals(annotationName) || "RequestParam".equals(annotationName)) {
                addIfText(expressions, annotationRawAttribute(annotationText, "name"));
                addIfText(expressions, annotationRawAttribute(annotationText, "value"));
                addIfText(expressions, annotationDefaultRawAttribute(annotationText));
            }
        }
        return List.copyOf(expressions);
    }

    private List<String> stringConstantExpressions(String content) {
        var expressions = new ArrayList<String>();
        var matcher = STRING_CONSTANT_PATTERN.matcher(content != null ? content : "");
        while (matcher.find()) {
            var declaration = matcher.group(1);
            if (!declaration.contains("static") || !declaration.contains("final")) {
                continue;
            }
            addIfText(expressions, matcher.group(3));
        }
        return List.copyOf(expressions);
    }

    private List<JavaStringConstantReference> javaStringConstantReferences(List<String> expressions) {
        var references = new ArrayList<JavaStringConstantReference>();
        for (var expression : expressions != null ? expressions : List.<String>of()) {
            if (!StringUtils.hasText(expression)) {
                continue;
            }

            var expressionWithoutStrings = withoutStringLiterals(expression);
            var classQualifiedMatcher = CLASS_QUALIFIED_REFERENCE_PATTERN.matcher(expressionWithoutStrings);
            var withoutClassQualifiedReferences = classQualifiedMatcher.replaceAll(" ");
            classQualifiedMatcher.reset();
            while (classQualifiedMatcher.find()) {
                references.add(new JavaStringConstantReference(
                        classQualifiedMatcher.group(1),
                        classQualifiedMatcher.group(2)
                ));
            }

            var constantMatcher = CONSTANT_REFERENCE_PATTERN.matcher(withoutClassQualifiedReferences);
            while (constantMatcher.find()) {
                references.add(new JavaStringConstantReference(null, constantMatcher.group(1)));
            }
        }
        return references;
    }

    private List<String> javaAnnotationSegments(String content, Set<String> simpleNames) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        var segments = new ArrayList<String>();
        var matcher = ANNOTATION_NAME_PATTERN.matcher(content);
        while (matcher.find()) {
            var qualifiedName = matcher.group(1);
            var dotIndex = qualifiedName.lastIndexOf('.');
            var simpleName = dotIndex >= 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
            if (!simpleNames.contains(simpleName)) {
                continue;
            }

            var argumentsStart = nextNonWhitespaceIndex(content, matcher.end());
            if (argumentsStart < content.length() && content.charAt(argumentsStart) == '(') {
                var argumentsEnd = matchingParenthesisEnd(content, argumentsStart);
                if (argumentsEnd > argumentsStart) {
                    segments.add(content.substring(matcher.start(), argumentsEnd + 1));
                }
            } else {
                var lineEnd = content.indexOf('\n', matcher.end());
                segments.add(content.substring(matcher.start(), lineEnd >= 0 ? lineEnd : content.length()));
            }
        }
        return List.copyOf(segments);
    }

    private int nextNonWhitespaceIndex(String content, int startIndex) {
        var index = startIndex;
        while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        return index;
    }

    private int matchingParenthesisEnd(String content, int openIndex) {
        var depth = 0;
        var inString = false;
        var escaped = false;
        for (var index = openIndex; index < content.length(); index++) {
            var character = content.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private String withoutStringLiterals(String expression) {
        var result = new StringBuilder();
        var inString = false;
        var escaped = false;
        for (var index = 0; index < expression.length(); index++) {
            var character = expression.charAt(index);
            if (inString) {
                result.append(' ');
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
                result.append(' ');
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private Map<String, String> javaImportClasses(String content) {
        var imports = new LinkedHashMap<String, String>();
        var matcher = IMPORT_PATTERN.matcher(content != null ? content : "");
        while (matcher.find()) {
            var importedClass = matcher.group(1);
            if (importedClass.endsWith(".*")) {
                continue;
            }
            imports.putIfAbsent(simpleName(importedClass), importedClass);
        }
        return imports;
    }

    private GitLabRepositoryFileContent readJavaStringConstantFile(
            String group,
            String projectName,
            String branch,
            String currentFilePath,
            String importedClass,
            List<String> diagnostics,
            JavaStringConstantResolutionContext resolutionContext
    ) {
        if (resolutionContext != null) {
            var cachedLookup = resolutionContext.constantFileLookup(importedClass);
            if (cachedLookup != null) {
                if (StringUtils.hasText(cachedLookup.diagnostic())) {
                    diagnostics.add(cachedLookup.diagnostic());
                }
                return cachedLookup.content();
            }
        }

        var lookup = readJavaStringConstantFileLookup(
                group,
                projectName,
                branch,
                currentFilePath,
                importedClass,
                resolutionContext
        );
        if (resolutionContext != null) {
            resolutionContext.constantFileLookup(importedClass, lookup);
        }
        if (StringUtils.hasText(lookup.diagnostic())) {
            diagnostics.add(lookup.diagnostic());
        }
        return lookup.content();
    }

    private JavaStringConstantFileLookup readJavaStringConstantFileLookup(
            String group,
            String projectName,
            String branch,
            String currentFilePath,
            String importedClass,
            JavaStringConstantResolutionContext resolutionContext
    ) {
        var failures = new ArrayList<String>();
        var directPath = javaSourcePathForClass(currentFilePath, importedClass);
        var directContent = readFirstJavaStringConstantFileCandidate(
                group,
                projectName,
                branch,
                List.of(directPath),
                failures
        );
        if (directContent != null) {
            return new JavaStringConstantFileLookup(directContent, null);
        }

        var searchContent = readFirstJavaStringConstantFileCandidate(
                group,
                projectName,
                branch,
                searchJavaStringConstantFilePaths(group, projectName, branch, importedClass),
                failures
        );
        if (searchContent != null) {
            return new JavaStringConstantFileLookup(searchContent, null);
        }

        var treeContent = readFirstJavaStringConstantFileCandidate(
                group,
                projectName,
                branch,
                repositoryTreeJavaStringConstantFilePaths(group, projectName, branch, importedClass, resolutionContext),
                failures
        );
        if (treeContent != null) {
            return new JavaStringConstantFileLookup(treeContent, null);
        }

        return new JavaStringConstantFileLookup(
                null,
                "Could not resolve Java string constants from static import "
                        + importedClass + ". Tried: " + abbreviate(String.join(", ", failures), 420)
        );
    }

    private GitLabRepositoryFileContent readFirstJavaStringConstantFileCandidate(
            String group,
            String projectName,
            String branch,
            List<String> candidatePaths,
            List<String> failures
    ) {
        for (var candidatePath : deduplicate(candidatePaths)) {
            try {
                return gitLabRepositoryPort.readFile(
                        group,
                        projectName,
                        branch,
                        candidatePath,
                        MAX_CONSTANT_FILE_CHARACTERS
                );
            } catch (RuntimeException exception) {
                failures.add(candidatePath + " (" + safeMessage(exception) + ")");
            }
        }
        return null;
    }

    private List<String> searchJavaStringConstantFilePaths(
            String group,
            String projectName,
            String branch,
            String importedClass
    ) {
        try {
            var simpleClassName = simpleName(importedClass);
            return gitLabRepositoryPort.searchRepositoryFilesByContent(
                            group,
                            projectName,
                            branch,
                            List.of("class " + simpleClassName, "interface " + simpleClassName, simpleClassName),
                            20
                    ).stream()
                    .filter(candidate -> candidate != null && javaSourcePathMatchesClass(candidate.filePath(), importedClass))
                    .sorted(Comparator.comparing(GitLabRepositoryFileCandidate::filePath))
                    .map(GitLabRepositoryFileCandidate::filePath)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<String> repositoryTreeJavaStringConstantFilePaths(
            String group,
            String projectName,
            String branch,
            String importedClass,
            JavaStringConstantResolutionContext resolutionContext
    ) {
        try {
            var files = repositoryTreeFilesForJavaStringConstants(group, projectName, branch, resolutionContext);
            return files.stream()
                    .filter(file -> file != null && javaSourcePathMatchesClass(file.filePath(), importedClass))
                    .sorted(Comparator.comparing(GitLabRepositoryFile::filePath))
                    .map(GitLabRepositoryFile::filePath)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<GitLabRepositoryFile> repositoryTreeFilesForJavaStringConstants(
            String group,
            String projectName,
            String branch,
            JavaStringConstantResolutionContext resolutionContext
    ) {
        if (resolutionContext != null && resolutionContext.repositoryTreeLoaded()) {
            return resolutionContext.repositoryTreeFiles();
        }
        var files = gitLabRepositoryPort.listRepositoryFiles(group, projectName, branch, null);
        var safeFiles = files != null ? files : List.<GitLabRepositoryFile>of();
        if (resolutionContext != null) {
            resolutionContext.repositoryTreeFiles(safeFiles);
        }
        return safeFiles;
    }

    private boolean javaSourcePathMatchesClass(String filePath, String importedClass) {
        if (!StringUtils.hasText(filePath) || !StringUtils.hasText(importedClass)) {
            return false;
        }
        var normalizedPath = filePath.replace('\\', '/');
        var classPath = importedClass.replace('.', '/') + ".java";
        return normalizedPath.endsWith(classPath);
    }

    private boolean likelyEndpointConstantImport(String importedClass, String importedMember) {
        var className = simpleName(importedClass);
        var memberName = importedMember != null ? importedMember.toLowerCase(Locale.ROOT) : "";
        return likelyEndpointConstantClassName(className)
                || memberName.contains("uri")
                || memberName.contains("url")
                || memberName.contains("path")
                || memberName.contains("route")
                || memberName.contains("endpoint")
                || memberName.contains("param");
    }

    private boolean likelyEndpointConstantClassName(String className) {
        var normalized = className != null ? className.toLowerCase(Locale.ROOT) : "";
        return normalized.contains("uri")
                || normalized.contains("url")
                || normalized.contains("path")
                || normalized.contains("route")
                || normalized.contains("endpoint")
                || normalized.contains("param")
                || normalized.contains("resource");
    }

    private void addStringConstants(
            Map<String, String> constants,
            String packageName,
            String className,
            String content
    ) {
        if (!StringUtils.hasText(content)) {
            return;
        }

        var matcher = STRING_CONSTANT_PATTERN.matcher(content);
        while (matcher.find()) {
            var declaration = matcher.group(1);
            if (!declaration.contains("static") || !declaration.contains("final")) {
                continue;
            }

            var constantName = matcher.group(2).trim();
            var expression = matcher.group(3).trim();
            constants.putIfAbsent(constantName, expression);
            if (StringUtils.hasText(className)) {
                constants.putIfAbsent(className + "." + constantName, expression);
            }
            if (StringUtils.hasText(packageName) && StringUtils.hasText(className)) {
                constants.putIfAbsent(packageName + "." + className + "." + constantName, expression);
            }
        }
    }

    private String javaSourcePathForClass(String currentFilePath, String fullyQualifiedClassName) {
        var classPath = fullyQualifiedClassName.replace('.', '/') + ".java";
        var normalizedCurrentPath = currentFilePath != null ? currentFilePath.replace('\\', '/') : "";
        for (var sourceRoot : List.of("src/main/java/", "src/test/java/")) {
            var rootIndex = normalizedCurrentPath.indexOf(sourceRoot);
            if (rootIndex >= 0) {
                return normalizedCurrentPath.substring(0, rootIndex) + sourceRoot + classPath;
            }
        }
        return "src/main/java/" + classPath;
    }

    private String firstDeclaredTypeName(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        var matcher = CLASS_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String packageName(String fullyQualifiedClassName) {
        if (!StringUtils.hasText(fullyQualifiedClassName)) {
            return null;
        }
        var dotIndex = fullyQualifiedClassName.lastIndexOf('.');
        return dotIndex > 0 ? fullyQualifiedClassName.substring(0, dotIndex) : null;
    }

    private List<GitLabRepositoryEndpoint> buildEndpoints(
            String projectName,
            String filePath,
            ControllerContext controller,
            List<AnnotationBlock> methodAnnotationBlocks,
            MethodSignature signature,
            List<String> inheritedLimitations,
            JavaStringConstantResolver constantResolver
    ) {
        var methodMappings = mappings(methodAnnotationBlocks, constantResolver);
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
                            limitations.addAll(constantResolver.diagnostics());
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
                        var documentation = documentationFromJavaAnnotations(
                                methodAnnotationBlocks,
                                signature,
                                constantResolver
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
                                documentation,
                                confidence,
                                List.copyOf(limitations),
                                suggestedNextReads(projectName, filePath, lineStart, lineEnd)
                        ));
                    }
                }
            }
        }

        return List.copyOf(endpoints);
    }

    private GitLabRepositoryEndpointDocumentation documentationFromJavaAnnotations(
            List<AnnotationBlock> methodAnnotationBlocks,
            MethodSignature signature,
            JavaStringConstantResolver constantResolver
    ) {
        var summary = (String) null;
        var description = (String) null;
        var operationId = (String) null;
        var tags = new ArrayList<String>();
        var hasOpenApiAnnotation = false;

        for (var block : methodAnnotationBlocks != null ? methodAnnotationBlocks : List.<AnnotationBlock>of()) {
            var annotationName = annotationName(block.text());
            if ("Operation".equals(annotationName)) {
                hasOpenApiAnnotation = true;
                summary = firstText(summary, annotationStringAttribute(block.text(), "summary"));
                description = firstText(description, annotationStringAttribute(block.text(), "description"));
                operationId = firstText(operationId, annotationStringAttribute(block.text(), "operationId"));
                tags.addAll(annotationStringListAttribute(block.text(), "tags"));
            } else if ("Parameter".equals(annotationName)) {
                hasOpenApiAnnotation = true;
            }
        }

        var parameters = methodParameterDocumentations(signature.parameters(), constantResolver);
        var source = hasOpenApiAnnotation
                ? DOCUMENTATION_SOURCE_JAVA_OPENAPI_ANNOTATION
                : parameters.isEmpty() ? null : DOCUMENTATION_SOURCE_SPRING_SIGNATURE;
        var documentation = new GitLabRepositoryEndpointDocumentation(
                source,
                abbreviate(summary, MAX_DOCUMENTATION_TEXT_LENGTH),
                abbreviate(description, MAX_DOCUMENTATION_TEXT_LENGTH),
                operationId,
                tags,
                parameters
        );
        return documentation.empty() ? null : documentation;
    }

    private List<GitLabRepositoryEndpointParameterDocumentation> methodParameterDocumentations(
            List<String> parameters,
            JavaStringConstantResolver constantResolver
    ) {
        var documented = new ArrayList<GitLabRepositoryEndpointParameterDocumentation>();
        for (var parameter : parameters != null ? parameters : List.<String>of()) {
            var documentation = methodParameterDocumentation(parameter, constantResolver);
            if (documentation == null) {
                continue;
            }
            var existingIndex = existingParameterIndex(documented, documentation);
            if (existingIndex >= 0) {
                documented.set(existingIndex, documented.get(existingIndex).merge(documentation));
            } else {
                documented.add(documentation);
            }
        }
        return List.copyOf(documented);
    }

    private GitLabRepositoryEndpointParameterDocumentation methodParameterDocumentation(
            String parameterText,
            JavaStringConstantResolver constantResolver
    ) {
        if (!StringUtils.hasText(parameterText) || annotationSegment(parameterText, "RequestHeader") != null) {
            return null;
        }

        var pathVariableAnnotation = annotationSegment(parameterText, "PathVariable");
        var requestParamAnnotation = annotationSegment(parameterText, "RequestParam");
        var parameterAnnotation = annotationSegment(parameterText, "Parameter");
        var in = (String) null;
        if (pathVariableAnnotation != null) {
            in = "path";
        } else if (requestParamAnnotation != null) {
            in = "query";
        } else if (parameterAnnotation != null) {
            in = javaParameterIn(parameterAnnotation);
        }

        if (!"path".equals(in) && !"query".equals(in)) {
            return null;
        }

        var name = firstText(
                parameterBindingName(pathVariableAnnotation, constantResolver),
                parameterBindingName(requestParamAnnotation, constantResolver)
        );
        name = firstText(name, annotationStringAttribute(parameterAnnotation, "name"));
        name = firstText(name, javaParameterName(parameterText));
        if (!StringUtils.hasText(name)) {
            return null;
        }

        var required = "path".equals(in);
        if (!required && requestParamAnnotation != null) {
            required = booleanAttribute(requestParamAnnotation, "required", true);
        }
        if (!required && parameterAnnotation != null) {
            required = booleanAttribute(parameterAnnotation, "required", false);
        }
        return new GitLabRepositoryEndpointParameterDocumentation(
                name,
                in,
                required,
                javaParameterType(parameterText),
                abbreviate(annotationStringAttribute(parameterAnnotation, "description"), MAX_PARAMETER_DESCRIPTION_LENGTH)
        );
    }

    private String javaParameterIn(String parameterAnnotation) {
        var rawValue = annotationRawAttribute(parameterAnnotation, "in");
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        if (rawValue.toUpperCase(Locale.ROOT).contains("PATH")) {
            return "path";
        }
        if (rawValue.toUpperCase(Locale.ROOT).contains("QUERY")) {
            return "query";
        }
        return null;
    }

    private String parameterBindingName(String annotationText, JavaStringConstantResolver constantResolver) {
        if (!StringUtils.hasText(annotationText)) {
            return null;
        }
        var literalName = firstText(
                annotationStringAttribute(annotationText, "name"),
                firstText(
                        annotationStringAttribute(annotationText, "value"),
                        annotationDefaultStringAttribute(annotationText)
                )
        );
        if (StringUtils.hasText(literalName)) {
            return literalName;
        }

        return firstText(
                resolveStringConstant(annotationRawAttribute(annotationText, "name"), constantResolver),
                firstText(
                        resolveStringConstant(annotationRawAttribute(annotationText, "value"), constantResolver),
                        resolveStringConstant(annotationDefaultRawAttribute(annotationText), constantResolver)
                )
        );
    }

    private String annotationSegment(String text, String simpleName) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        var matcher = Pattern.compile("@(?:[A-Za-z_$][\\w$]*\\.)*"
                + Pattern.quote(simpleName)
                + "\\b\\s*(\\([^)]*\\))?").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String annotationStringAttribute(String annotationText, String attributeName) {
        var expression = annotationRawAttribute(annotationText, attributeName);
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var matcher = STRING_LITERAL_PATTERN.matcher(expression);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private List<String> annotationStringListAttribute(String annotationText, String attributeName) {
        var expression = annotationRawAttribute(annotationText, attributeName);
        if (!StringUtils.hasText(expression)) {
            return List.of();
        }
        var values = new ArrayList<String>();
        var matcher = STRING_LITERAL_PATTERN.matcher(expression);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return deduplicate(values);
    }

    private String annotationDefaultStringAttribute(String annotationText) {
        if (!StringUtils.hasText(annotationText)) {
            return null;
        }
        var arguments = annotationDefaultRawAttribute(annotationText);
        if (!StringUtils.hasText(arguments)) {
            return null;
        }
        var matcher = STRING_LITERAL_PATTERN.matcher(arguments);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String annotationDefaultRawAttribute(String annotationText) {
        if (!StringUtils.hasText(annotationText)) {
            return null;
        }
        var open = annotationText.indexOf('(');
        var close = annotationText.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        var arguments = annotationText.substring(open + 1, close).trim();
        if (arguments.contains("=")) {
            return null;
        }
        return arguments;
    }

    private String annotationRawAttribute(String annotationText, String attributeName) {
        if (!StringUtils.hasText(annotationText) || !StringUtils.hasText(attributeName)) {
            return null;
        }
        var matcher = Pattern.compile("\\b"
                + Pattern.quote(attributeName)
                + "\\s*=").matcher(annotationText);
        if (!matcher.find()) {
            return null;
        }
        return readAnnotationExpression(annotationText, matcher.end());
    }

    private String readAnnotationExpression(String annotationText, int startIndex) {
        var expression = new StringBuilder();
        var inString = false;
        var escaped = false;
        var depth = 0;
        for (var index = startIndex; index < annotationText.length(); index++) {
            var character = annotationText.charAt(index);
            if (inString) {
                expression.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            if (character == '"') {
                inString = true;
                expression.append(character);
                continue;
            }
            if (character == '{' || character == '(' || character == '[') {
                depth++;
                expression.append(character);
                continue;
            }
            if (character == '}' || character == ')' || character == ']') {
                if (depth == 0) {
                    break;
                }
                depth--;
                expression.append(character);
                continue;
            }
            if (character == ',' && depth == 0) {
                break;
            }
            expression.append(character);
        }
        var value = expression.toString().trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private List<String> resolveStringValues(String expression, JavaStringConstantResolver constantResolver) {
        if (!StringUtils.hasText(expression)) {
            return List.of();
        }

        return expressionListItems(expression).stream()
                .map(item -> resolveStringConstant(item, constantResolver))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String resolveStringConstant(String expression, JavaStringConstantResolver constantResolver) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        var resolver = constantResolver != null ? constantResolver : JavaStringConstantResolver.empty();
        return resolver.resolve(expression);
    }

    private List<String> expressionListItems(String expression) {
        var trimmed = expression.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            var inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (!StringUtils.hasText(inner)) {
                return List.of();
            }
            return splitTopLevel(inner, ',');
        }
        return List.of(trimmed);
    }

    private static List<String> splitTopLevel(String expression, char delimiter) {
        var items = new ArrayList<String>();
        var current = new StringBuilder();
        var inString = false;
        var escaped = false;
        var depth = 0;
        for (var index = 0; index < expression.length(); index++) {
            var character = expression.charAt(index);
            if (inString) {
                current.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            if (character == '"') {
                inString = true;
                current.append(character);
                continue;
            }
            if (character == '{' || character == '(' || character == '[') {
                depth++;
                current.append(character);
                continue;
            }
            if (character == '}' || character == ')' || character == ']') {
                depth = Math.max(0, depth - 1);
                current.append(character);
                continue;
            }
            if (character == delimiter && depth == 0) {
                var item = current.toString().trim();
                if (StringUtils.hasText(item)) {
                    items.add(item);
                }
                current.setLength(0);
                continue;
            }
            current.append(character);
        }

        var item = current.toString().trim();
        if (StringUtils.hasText(item)) {
            items.add(item);
        }
        return List.copyOf(items);
    }

    private boolean booleanAttribute(String annotationText, String attributeName, boolean defaultValue) {
        var rawValue = annotationRawAttribute(annotationText, attributeName);
        return StringUtils.hasText(rawValue) ? Boolean.parseBoolean(rawValue) : defaultValue;
    }

    private String javaParameterName(String parameterText) {
        var cleaned = stripJavaParameterAnnotations(parameterText);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        var tokens = cleaned.split("\\s+");
        return tokens.length > 0 ? tokens[tokens.length - 1].trim() : null;
    }

    private String javaParameterType(String parameterText) {
        var cleaned = stripJavaParameterAnnotations(parameterText);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        var tokens = new ArrayList<>(List.of(cleaned.split("\\s+")));
        tokens.removeIf(token -> METHOD_MODIFIERS.contains(token));
        if (tokens.size() < 2) {
            return null;
        }
        tokens.remove(tokens.size() - 1);
        return String.join(" ", tokens);
    }

    private String stripJavaParameterAnnotations(String parameterText) {
        return parameterText
                .replaceAll("@(?:[A-Za-z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*\\s*(\\([^)]*\\))?", "")
                .trim();
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
                        operation.documentation(),
                        "high",
                        List.copyOf(limitations),
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
                "%s:%s %s %s via gitlab_read_openapi_endpoint_slice".formatted(
                        projectName,
                        operation.filePath(),
                        operation.httpMethod(),
                        operation.path()
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

        var limitations = new ArrayList<String>(inheritedLimitations != null ? inheritedLimitations : List.of());
        var document = parseYamlDocument(filePath, content, limitations);
        var paths = asMap(document.get("paths"));
        if (paths.isEmpty()) {
            return List.of();
        }

        var operations = new ArrayList<OpenApiOperation>();
        var lines = content.lines().toList();

        for (var pathEntry : paths.entrySet()) {
            var path = String.valueOf(pathEntry.getKey());
            if (!StringUtils.hasText(path) || !path.startsWith("/")) {
                continue;
            }
            var pathItem = asMap(pathEntry.getValue());
            if (pathItem.isEmpty()) {
                continue;
            }

            var pathParameters = openApiParameters(pathItem.get("parameters"), document);
            for (var operationEntry : pathItem.entrySet()) {
                var method = String.valueOf(operationEntry.getKey()).toUpperCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                var operation = asMap(operationEntry.getValue());
                if (operation.isEmpty()) {
                    continue;
                }
                var operationParameters = new ArrayList<GitLabRepositoryEndpointParameterDocumentation>();
                operationParameters.addAll(pathParameters);
                operationParameters.addAll(openApiParameters(operation.get("parameters"), document));
                var operationId = textValue(operation.get("operationId"));
                var tags = stringList(operation.get("tags"));
                var documentation = new GitLabRepositoryEndpointDocumentation(
                        DOCUMENTATION_SOURCE_OPENAPI_YAML,
                        abbreviate(textValue(operation.get("summary")), MAX_DOCUMENTATION_TEXT_LENGTH),
                        abbreviate(textValue(operation.get("description")), MAX_DOCUMENTATION_TEXT_LENGTH),
                        operationId,
                        tags,
                        operationParameters
                );
                operations.add(new OpenApiOperation(
                        filePath,
                        path,
                        method,
                        operationId,
                        tags,
                        openApiOperationLineStart(lines, path, method),
                        documentation,
                        limitations
                ));
            }
        }

        return List.copyOf(operations);
    }

    private Map<String, Object> parseYamlDocument(
            String filePath,
            String content,
            List<String> limitations
    ) {
        var factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), filePath));
        try {
            factoryBean.afterPropertiesSet();
            var document = factoryBean.getObject();
            return document != null ? document : Map.of();
        } catch (RuntimeException exception) {
            limitations.add("Could not parse OpenAPI YAML structure from " + filePath + ": " + safeMessage(exception));
            return Map.of();
        }
    }

    private List<GitLabRepositoryEndpoint> mergeOpenApiDocumentation(
            List<GitLabRepositoryEndpoint> endpoints,
            List<OpenApiOperation> openApiOperations
    ) {
        if (endpoints.isEmpty() || openApiOperations.isEmpty()) {
            return endpoints;
        }

        var operationsByKey = new LinkedHashMap<String, OpenApiOperation>();
        for (var operation : openApiOperations) {
            operationsByKey.put(endpointDocumentationKey(operation.httpMethod(), operation.path()), operation);
        }

        return endpoints.stream()
                .map(endpoint -> {
                    var documentation = endpoint.documentation();
                    for (var method : endpoint.httpMethods()) {
                        var operation = operationsByKey.get(endpointDocumentationKey(method, endpoint.path()));
                        if (operation == null || operation.documentation() == null) {
                            continue;
                        }
                        documentation = documentation == null
                                ? operation.documentation()
                                : documentation.merge(operation.documentation());
                    }
                    return documentation == endpoint.documentation() ? endpoint : endpoint.withDocumentation(documentation);
                })
                .toList();
    }

    private String endpointDocumentationKey(String httpMethod, String path) {
        return (StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase(Locale.ROOT) : "")
                + " "
                + (StringUtils.hasText(path) ? path.trim() : "");
    }

    private List<GitLabRepositoryEndpointParameterDocumentation> openApiParameters(
            Object rawParameters,
            Map<String, Object> document
    ) {
        if (!(rawParameters instanceof List<?> parameters)) {
            return List.of();
        }

        var parsed = new ArrayList<GitLabRepositoryEndpointParameterDocumentation>();
        for (var rawParameter : parameters) {
            var parameter = openApiParameter(rawParameter, document);
            if (parameter == null) {
                continue;
            }
            var existingIndex = existingParameterIndex(parsed, parameter);
            if (existingIndex >= 0) {
                parsed.set(existingIndex, parsed.get(existingIndex).merge(parameter));
            } else {
                parsed.add(parameter);
            }
        }
        return List.copyOf(parsed);
    }

    private GitLabRepositoryEndpointParameterDocumentation openApiParameter(
            Object rawParameter,
            Map<String, Object> document
    ) {
        var parameter = asMap(rawParameter);
        if (parameter.containsKey("$ref")) {
            parameter = resolveOpenApiRef(textValue(parameter.get("$ref")), document);
        }
        if (parameter.isEmpty()) {
            return null;
        }

        var in = textValue(parameter.get("in"));
        if (!"path".equalsIgnoreCase(in) && !"query".equalsIgnoreCase(in)) {
            return null;
        }

        var name = textValue(parameter.get("name"));
        if (!StringUtils.hasText(name)) {
            return null;
        }

        return new GitLabRepositoryEndpointParameterDocumentation(
                name,
                in.toLowerCase(Locale.ROOT),
                booleanValue(parameter.get("required")) || "path".equalsIgnoreCase(in),
                schemaType(parameter.get("schema")),
                abbreviate(textValue(parameter.get("description")), MAX_PARAMETER_DESCRIPTION_LENGTH)
        );
    }

    private int existingParameterIndex(
            List<GitLabRepositoryEndpointParameterDocumentation> parameters,
            GitLabRepositoryEndpointParameterDocumentation candidate
    ) {
        for (var index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).sameParameter(candidate)) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Object> resolveOpenApiRef(String ref, Map<String, Object> document) {
        if (!StringUtils.hasText(ref) || !ref.startsWith("#/")) {
            return Map.of();
        }

        Object current = document;
        for (var segment : ref.substring(2).split("/")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Map.of();
            }
            current = map.get(segment.replace("~1", "/").replace("~0", "~"));
        }
        return asMap(current);
    }

    private String schemaType(Object rawSchema) {
        var schema = asMap(rawSchema);
        if (schema.isEmpty()) {
            return null;
        }
        var type = textValue(schema.get("type"));
        if (StringUtils.hasText(type)) {
            var format = textValue(schema.get("format"));
            return StringUtils.hasText(format) ? type + "(" + format + ")" : type;
        }
        var ref = textValue(schema.get("$ref"));
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        var slashIndex = ref.lastIndexOf('/');
        return slashIndex >= 0 ? ref.substring(slashIndex + 1) : ref;
    }

    private int openApiOperationLineStart(List<String> lines, String path, String httpMethod) {
        var pathLineIndex = -1;
        var pathIndent = -1;
        for (var index = 0; index < lines.size(); index++) {
            var rawLine = lines.get(index);
            var trimmedLine = rawLine.trim();
            if (pathLineIndex < 0) {
                if (path.equals(yamlKey(trimmedLine))) {
                    pathLineIndex = index;
                    pathIndent = leadingSpaces(rawLine);
                }
                continue;
            }
            var indent = leadingSpaces(rawLine);
            if (indent <= pathIndent && StringUtils.hasText(trimmedLine)) {
                return pathLineIndex + 1;
            }
            if (HTTP_METHODS.contains(yamlKey(trimmedLine).toUpperCase(Locale.ROOT))
                    && httpMethod.equalsIgnoreCase(yamlKey(trimmedLine))) {
                return index + 1;
            }
        }
        return pathLineIndex >= 0 ? pathLineIndex + 1 : 1;
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return deduplicate(list.stream()
                    .map(this::textValue)
                    .filter(StringUtils::hasText)
                    .toList());
        }
        var text = textValue(value);
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private String textValue(Object value) {
        return value != null && StringUtils.hasText(String.valueOf(value)) ? String.valueOf(value).trim() : null;
    }

    private String firstText(String left, String right) {
        return StringUtils.hasText(left) ? left : StringUtils.hasText(right) ? right : null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
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
                new GitLabRepositoryEndpointDocumentation(
                        DOCUMENTATION_SOURCE_OPENAPI_YAML,
                        null,
                        null,
                        builder.operationId(),
                        builder.tags(),
                        List.of()
                ),
                inheritedLimitations
        ));
    }

    private ControllerContext buildControllerContext(
            String packageName,
            String className,
            List<AnnotationBlock> annotations,
            String filePath,
            JavaStringConstantResolver constantResolver
    ) {
        var qualifiedClassName = StringUtils.hasText(packageName) ? packageName + "." + className : className;
        var annotationNames = annotationNames(annotations);
        if (annotationNames.contains("FeignClient")) {
            return null;
        }
        var mappings = mappings(annotations, constantResolver);
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

    private List<MappingDefinition> mappings(
            List<AnnotationBlock> annotationBlocks,
            JavaStringConstantResolver constantResolver
    ) {
        var mappings = new ArrayList<MappingDefinition>();
        for (var block : annotationBlocks) {
            var annotationName = annotationName(block.text());
            var httpMethods = httpMethods(annotationName, block.text());
            if (httpMethods.isEmpty()) {
                continue;
            }

            var paths = annotationPaths(block.text(), constantResolver);
            mappings.add(new MappingDefinition(
                    httpMethods,
                    paths,
                    paths.isEmpty() ? pathExpression(block.text(), constantResolver) : null,
                    List.of(annotationName),
                    block.startLine()
            ));
        }
        return List.copyOf(mappings);
    }

    private List<String> annotationPaths(String annotationText, JavaStringConstantResolver constantResolver) {
        var paths = new LinkedHashSet<String>();
        for (var expression : annotationPathExpressions(annotationText)) {
            for (var value : resolveStringValues(expression, constantResolver)) {
                if (isPathLiteral(value)) {
                    paths.add(value.trim());
                }
            }
        }
        return List.copyOf(paths);
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

    private String pathExpression(String annotationText, JavaStringConstantResolver constantResolver) {
        var unresolved = new ArrayList<String>();
        for (var expression : annotationPathExpressions(annotationText)) {
            if (resolveStringValues(expression, constantResolver).isEmpty()) {
                unresolved.add(expression);
            }
        }
        return unresolved.isEmpty() ? null : abbreviate(String.join(", ", unresolved), 220);
    }

    private List<String> annotationPathExpressions(String annotationText) {
        var expressions = new LinkedHashSet<String>();
        for (var attributeName : List.of("value", "path")) {
            var expression = annotationRawAttribute(annotationText, attributeName);
            if (StringUtils.hasText(expression)) {
                expressions.add(expression);
            }
        }

        if (expressions.isEmpty()) {
            var expression = annotationDefaultRawAttribute(annotationText);
            if (StringUtils.hasText(expression)) {
                expressions.add(expression);
            }
        }
        return List.copyOf(expressions);
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
        return deduplicate(splitTopLevelParameters(parameters));
    }

    private List<String> splitTopLevelParameters(String parameters) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        var parenthesisDepth = 0;
        var angleDepth = 0;
        var inString = false;

        for (var index = 0; index < parameters.length(); index++) {
            var character = parameters.charAt(index);
            if (character == '"' && (index == 0 || parameters.charAt(index - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (character == '(') {
                    parenthesisDepth++;
                } else if (character == ')' && parenthesisDepth > 0) {
                    parenthesisDepth--;
                } else if (character == '<') {
                    angleDepth++;
                } else if (character == '>' && angleDepth > 0) {
                    angleDepth--;
                } else if (character == ',' && parenthesisDepth == 0 && angleDepth == 0) {
                    if (StringUtils.hasText(current.toString())) {
                        result.add(current.toString().trim());
                    }
                    current.setLength(0);
                    continue;
                }
            }
            current.append(character);
        }

        if (StringUtils.hasText(current.toString())) {
            result.add(current.toString().trim());
        }
        return List.copyOf(result);
    }

    private List<String> responseTypes(String responseType) {
        return StringUtils.hasText(responseType) ? List.of(responseType) : List.of();
    }

    private List<GitLabRepositoryFile> openApiCandidateFiles(
            String group,
            String projectName,
            String branch,
            List<GitLabRepositoryFile> knownRepositoryTreeFiles,
            List<String> limitations
    ) {
        var searchedFiles = searchFilesByContent(
                group,
                projectName,
                branch,
                OPENAPI_DISCOVERY_TERMS,
                OPENAPI_SEARCH_RESULTS_PER_TERM
        ).stream()
                .filter(file -> isYamlFile(file.filePath()))
                .toList();
        if (!searchedFiles.isEmpty()) {
            return sortOpenApiFiles(searchedFiles);
        }

        var repositoryFiles = knownRepositoryTreeFiles != null && !knownRepositoryTreeFiles.isEmpty()
                ? knownRepositoryTreeFiles
                : repositoryTreeFiles(group, projectName, branch, limitations, "OpenAPI contract discovery");
        return sortOpenApiFiles(repositoryFiles.stream()
                .filter(file -> file != null && isYamlFile(file.filePath()))
                .toList());
    }

    private List<GitLabRepositoryFile> sortOpenApiFiles(List<GitLabRepositoryFile> files) {
        return deduplicateFiles(files).stream()
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

    private EndpointCandidateDiscovery endpointCandidateFiles(
            String group,
            String projectName,
            String branch,
            List<String> limitations
    ) {
        var searchedFiles = searchFilesByContent(
                group,
                projectName,
                branch,
                ENDPOINT_DISCOVERY_TERMS,
                ENDPOINT_SEARCH_RESULTS_PER_TERM
        ).stream()
                .filter(file -> isProductionJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .toList();
        if (!searchedFiles.isEmpty()) {
            return new EndpointCandidateDiscovery(sortEndpointCandidateFiles(searchedFiles), List.of());
        }

        var repositoryFiles = repositoryTreeFiles(group, projectName, branch, limitations, "Spring REST endpoint discovery");
        var fallbackFiles = sortEndpointCandidateFiles(candidateFiles(repositoryFiles));
        if (!fallbackFiles.isEmpty()) {
            limitations.add("GitLab endpoint candidate search returned no Spring REST signals; repository tree fallback selected likely endpoint files by path.");
        }
        return new EndpointCandidateDiscovery(fallbackFiles, repositoryFiles);
    }

    private List<GitLabRepositoryFile> candidateFiles(List<GitLabRepositoryFile> repositoryFiles) {
        return repositoryFiles.stream()
                .filter(file -> file != null && isProductionJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .filter(file -> likelyEndpointSourceFile(file.filePath()))
                .toList();
    }

    private List<GitLabRepositoryFile> sortEndpointCandidateFiles(List<GitLabRepositoryFile> files) {
        return deduplicateFiles(files).stream()
                .sorted(Comparator.comparingInt((GitLabRepositoryFile file) -> candidateScore(file.filePath())).reversed()
                        .thenComparing(GitLabRepositoryFile::filePath))
                .toList();
    }

    private List<GitLabRepositoryFile> searchFilesByContent(
            String group,
            String projectName,
            String branch,
            List<String> searchTerms,
            int maxResultsPerTerm
    ) {
        try {
            var candidates = gitLabRepositoryPort.searchRepositoryFilesByContent(
                    group,
                    projectName,
                    branch,
                    searchTerms,
                    maxResultsPerTerm
            );
            if (candidates == null) {
                return List.of();
            }
            return candidates.stream()
                    .filter(candidate -> candidate != null && StringUtils.hasText(candidate.filePath()))
                    .map(candidate -> new GitLabRepositoryFile(
                            group,
                            projectName,
                            branch,
                            candidate.filePath()
                    ))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<GitLabRepositoryFile> repositoryTreeFiles(
            String group,
            String projectName,
            String branch,
            List<String> limitations,
            String purpose
    ) {
        try {
            var files = gitLabRepositoryPort.listRepositoryFiles(group, projectName, branch, null);
            return files != null ? files : List.of();
        } catch (RuntimeException exception) {
            limitations.add(purpose + " repository tree fallback failed: " + safeMessage(exception));
            return List.of();
        }
    }

    private List<GitLabRepositoryFile> deduplicateFiles(List<GitLabRepositoryFile> files) {
        var deduplicated = new LinkedHashMap<String, GitLabRepositoryFile>();
        for (var file : files != null ? files : List.<GitLabRepositoryFile>of()) {
            if (file == null || !StringUtils.hasText(file.filePath())) {
                continue;
            }
            deduplicated.putIfAbsent(file.filePath(), file);
        }
        return List.copyOf(deduplicated.values());
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
        if (normalized.contains("/contract/") || normalized.contains("/contracts/")) {
            score += 40;
        }
        if (normalized.contains("/adapter/in/") || normalized.contains("/inbound/")) {
            score += 30;
        }
        if (normalized.contains("/generated/")) {
            score -= 40;
        }
        return score;
    }

    private boolean likelyEndpointSourceFile(String filePath) {
        return candidateScore(filePath) > 0;
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

    private boolean isProductionJavaSource(String filePath) {
        if (!isSourceFile(filePath)) {
            return false;
        }
        var normalized = filePath.toLowerCase(Locale.ROOT).replace('\\', '/');
        return normalized.startsWith("src/main/java/") || normalized.contains("/src/main/java/");
    }

    private boolean isYamlFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        var normalized = filePath.toLowerCase(Locale.ROOT);
        return OPENAPI_FILE_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    private boolean hasOpenApiRootMarker(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        return content.lines()
                .map(line -> line.replace("\uFEFF", ""))
                .anyMatch(line -> line.startsWith("openapi:") || line.startsWith("swagger:"));
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

    private void addIfText(Collection<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
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
            GitLabRepositoryEndpointDocumentation documentation,
            List<String> limitations
    ) {
        private OpenApiOperation {
            tags = tags != null ? List.copyOf(tags) : List.of();
            documentation = documentation != null && !documentation.empty() ? documentation : null;
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }

    private record EndpointInventory(
            Instant dataCollectedAt,
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

    private record EndpointCandidateDiscovery(
            List<GitLabRepositoryFile> files,
            List<GitLabRepositoryFile> repositoryTreeFiles
    ) {
        private EndpointCandidateDiscovery {
            files = files != null ? List.copyOf(files) : List.of();
            repositoryTreeFiles = repositoryTreeFiles != null ? List.copyOf(repositoryTreeFiles) : List.of();
        }
    }

    private record JavaStringConstantReference(
            String ownerClassName,
            String memberName
    ) {
    }

    private record JavaStringConstantFileLookup(
            GitLabRepositoryFileContent content,
            String diagnostic
    ) {
    }

    private static final class JavaStringConstantResolutionContext {

        private final Map<String, JavaStringConstantFileLookup> constantFileLookups = new LinkedHashMap<>();
        private List<GitLabRepositoryFile> repositoryTreeFiles;
        private boolean repositoryTreeLoaded;

        private JavaStringConstantResolutionContext(List<GitLabRepositoryFile> repositoryTreeFiles) {
            this.repositoryTreeFiles = repositoryTreeFiles != null ? List.copyOf(repositoryTreeFiles) : List.of();
            this.repositoryTreeLoaded = repositoryTreeFiles != null && !repositoryTreeFiles.isEmpty();
        }

        private JavaStringConstantFileLookup constantFileLookup(String importedClass) {
            return constantFileLookups.get(importedClass);
        }

        private void constantFileLookup(String importedClass, JavaStringConstantFileLookup lookup) {
            constantFileLookups.put(importedClass, lookup);
        }

        private List<GitLabRepositoryFile> repositoryTreeFiles() {
            return repositoryTreeFiles;
        }

        private void repositoryTreeFiles(List<GitLabRepositoryFile> repositoryTreeFiles) {
            this.repositoryTreeFiles = repositoryTreeFiles != null ? List.copyOf(repositoryTreeFiles) : List.of();
            this.repositoryTreeLoaded = true;
        }

        private boolean repositoryTreeLoaded() {
            return repositoryTreeLoaded;
        }
    }

    private static final class JavaStringConstantResolver {

        private static final JavaStringConstantResolver EMPTY = new JavaStringConstantResolver(Map.of(), List.of());

        private final Map<String, String> expressions;
        private final List<String> diagnostics;

        private JavaStringConstantResolver(Map<String, String> expressions, List<String> diagnostics) {
            this.expressions = expressions != null ? Map.copyOf(expressions) : Map.of();
            this.diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        }

        private static JavaStringConstantResolver empty() {
            return EMPTY;
        }

        private String resolve(String expression) {
            return resolve(expression, new LinkedHashSet<>());
        }

        private List<String> diagnostics() {
            return diagnostics;
        }

        private String resolve(String expression, Set<String> resolving) {
            var normalized = stripOuterParentheses(expression != null ? expression.trim() : "");
            if (!StringUtils.hasText(normalized)) {
                return null;
            }

            var parts = splitTopLevel(normalized, '+');
            if (parts.size() > 1) {
                var resolved = new StringBuilder();
                for (var part : parts) {
                    var value = resolve(part, resolving);
                    if (value == null) {
                        return null;
                    }
                    resolved.append(value);
                }
                return resolved.toString();
            }

            if (isStringLiteral(normalized)) {
                return unescapeJavaString(normalized.substring(1, normalized.length() - 1));
            }

            var constantExpression = expressions.get(normalized);
            if (constantExpression == null) {
                return null;
            }
            if (!resolving.add(normalized)) {
                return null;
            }
            try {
                return resolve(constantExpression, resolving);
            } finally {
                resolving.remove(normalized);
            }
        }

        private static boolean isStringLiteral(String expression) {
            return expression.length() >= 2
                    && expression.startsWith("\"")
                    && expression.endsWith("\"");
        }

        private static String stripOuterParentheses(String expression) {
            var stripped = expression;
            while (stripped.startsWith("(") && stripped.endsWith(")")
                    && outerParenthesesWrapExpression(stripped)) {
                stripped = stripped.substring(1, stripped.length() - 1).trim();
            }
            return stripped;
        }

        private static boolean outerParenthesesWrapExpression(String expression) {
            var depth = 0;
            var inString = false;
            var escaped = false;
            for (var index = 0; index < expression.length(); index++) {
                var character = expression.charAt(index);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (character == '\\') {
                        escaped = true;
                    } else if (character == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (character == '"') {
                    inString = true;
                    continue;
                }
                if (character == '(') {
                    depth++;
                } else if (character == ')') {
                    depth--;
                    if (depth == 0 && index < expression.length() - 1) {
                        return false;
                    }
                }
            }
            return depth == 0;
        }

        private static String unescapeJavaString(String value) {
            var result = new StringBuilder();
            var escaped = false;
            for (var index = 0; index < value.length(); index++) {
                var character = value.charAt(index);
                if (!escaped) {
                    if (character == '\\') {
                        escaped = true;
                    } else {
                        result.append(character);
                    }
                    continue;
                }

                switch (character) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case '"', '\'', '\\' -> result.append(character);
                    default -> result.append(character);
                }
                escaped = false;
            }
            if (escaped) {
                result.append('\\');
            }
            return result.toString();
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
