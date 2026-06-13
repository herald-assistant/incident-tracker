package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
class GitLabEndpointUseCaseEndpointIndexService {

    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Pattern ATTRIBUTE_PATH_PATTERN = Pattern.compile("\\b(?:value|path)\\s*=\\s*(\\{[^}]*}|\"[^\"]*\")");
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]*)\"");

    GitLabEndpointUseCaseEndpointIndex buildIndex(GitLabEndpointUseCaseCodeIndex codeIndex) {
        if (codeIndex == null || codeIndex.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT) {
            return new GitLabEndpointUseCaseEndpointIndex(List.of(), codeIndex != null ? codeIndex.warnings() : List.of());
        }

        var endpoints = new ArrayList<GitLabEndpointUseCaseEndpointCandidate>();
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>(codeIndex.warnings());

        for (var type : codeIndex.types()) {
            if (!isController(type)) {
                continue;
            }

            var classMappings = mappings(type.annotationDetails());
            if (classMappings.isEmpty()) {
                classMappings = List.of(MappingDefinition.empty());
            }

            for (var method : type.methods()) {
                if (method.constructor()) {
                    continue;
                }
                var methodMappings = mappings(method.annotationDetails());
                if (methodMappings.isEmpty()) {
                    continue;
                }

                endpoints.addAll(endpointCandidates(type, method, classMappings, methodMappings, warnings));
            }
        }

        return new GitLabEndpointUseCaseEndpointIndex(
                endpoints.stream()
                        .sorted(Comparator.comparing(
                                        GitLabEndpointUseCaseEndpointCandidate::pathPattern,
                                        Comparator.nullsLast(String::compareTo))
                                .thenComparing(GitLabEndpointUseCaseEndpointCandidate::controllerClass)
                                .thenComparing(GitLabEndpointUseCaseEndpointCandidate::controllerMethod))
                        .toList(),
                warnings
        );
    }

    GitLabEndpointUseCaseEndpointMatchResult match(
            GitLabEndpointUseCaseContextRequest request,
            GitLabEndpointUseCaseEndpointIndex endpointIndex
    ) {
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>(
                endpointIndex != null ? endpointIndex.warnings() : List.of());
        var endpoints = endpointIndex != null ? endpointIndex.endpoints() : List.<GitLabEndpointUseCaseEndpointCandidate>of();
        var matches = matchingEndpoints(request, endpoints);

        if (matches.size() == 1) {
            return new GitLabEndpointUseCaseEndpointMatchResult(matches.get(0), matches, warnings);
        }
        if (matches.size() > 1) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.ENDPOINT_AMBIGUOUS,
                    GitLabEndpointUseCaseWarningSeverity.WARNING,
                    "Endpoint match is ambiguous; narrow endpointId or httpMethod + endpointPath.",
                    null,
                    null,
                    endpointIds(matches)
            ));
            return new GitLabEndpointUseCaseEndpointMatchResult(null, matches, warnings);
        }

        if (!hasEndpointMatchInput(request)) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.ENDPOINT_MATCH_INPUT_MISSING,
                    GitLabEndpointUseCaseWarningSeverity.ERROR,
                    "Endpoint matching requires endpointId or httpMethod + endpointPath.",
                    null,
                    null,
                    endpointIds(endpoints)
            ));
        } else {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.ENDPOINT_NOT_FOUND,
                    GitLabEndpointUseCaseWarningSeverity.ERROR,
                    "No endpoint matched the requested endpointId/path.",
                    null,
                    null,
                    endpointIds(endpoints)
            ));
        }

        return new GitLabEndpointUseCaseEndpointMatchResult(null, List.of(), warnings);
    }

    private List<GitLabEndpointUseCaseEndpointCandidate> matchingEndpoints(
            GitLabEndpointUseCaseContextRequest request,
            List<GitLabEndpointUseCaseEndpointCandidate> endpoints
    ) {
        if (request == null) {
            return List.of();
        }

        if (StringUtils.hasText(request.endpointId())) {
            var requestedEndpointId = request.endpointId().trim();
            return endpoints.stream()
                    .filter(endpoint -> requestedEndpointId.equals(endpoint.endpointId()))
                    .toList();
        }

        var requestedHttpMethod = normalizeHttpMethod(request.httpMethod());
        var requestedPath = normalizeHttpPath(request.endpointPath());
        if (!StringUtils.hasText(requestedHttpMethod) || !StringUtils.hasText(requestedPath)) {
            return List.of();
        }

        return endpoints.stream()
                .filter(endpoint -> matchesHttpMethod(endpoint, requestedHttpMethod))
                .filter(endpoint -> matchesPath(endpoint.pathPattern(), requestedPath))
                .toList();
    }

    private List<GitLabEndpointUseCaseEndpointCandidate> endpointCandidates(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseMethodInfo method,
            List<MappingDefinition> classMappings,
            List<MappingDefinition> methodMappings,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var endpoints = new ArrayList<GitLabEndpointUseCaseEndpointCandidate>();
        for (var classMapping : classMappings) {
            for (var methodMapping : methodMappings) {
                var basePaths = pathsOrBlank(classMapping.paths());
                var methodPaths = pathsOrBlank(methodMapping.paths());
                for (var basePath : basePaths) {
                    for (var methodPath : methodPaths) {
                        var path = combinePaths(basePath, methodPath);
                        var pathExpression = combinePathExpression(
                                classMapping.pathExpression(),
                                methodMapping.pathExpression()
                        );
                        var limitations = new ArrayList<String>();
                        if (StringUtils.hasText(pathExpression)) {
                            limitations.add("Endpoint path uses expression that was not fully resolved: " + pathExpression);
                            warnings.add(new GitLabEndpointUseCaseWarning(
                                    GitLabEndpointUseCaseWarningCodes.ENDPOINT_PATH_UNRESOLVED,
                                    GitLabEndpointUseCaseWarningSeverity.WARNING,
                                    "Endpoint path uses expression that was not fully resolved.",
                                    type.sourcePath(),
                                    methodMapping.lineStart(),
                                    List.of(pathExpression)
                            ));
                        }

                        endpoints.add(new GitLabEndpointUseCaseEndpointCandidate(
                                endpointId(methodMapping.httpMethods(), path, type.fqn(), method.name()),
                                methodMapping.httpMethods(),
                                path,
                                pathExpression,
                                type.fqn(),
                                method.name(),
                                method.id(),
                                type.sourcePath(),
                                methodMapping.lineStart() != null ? methodMapping.lineStart() : method.lineStart(),
                                method.lineEnd(),
                                method.parameters().stream()
                                        .map(GitLabEndpointUseCaseParameterInfo::type)
                                        .toList(),
                                StringUtils.hasText(method.returnType()) ? List.of(method.returnType()) : List.of(),
                                deduplicate(joinLists(type.annotations(), method.annotations())),
                                StringUtils.hasText(pathExpression)
                                        ? GitLabEndpointUseCaseConfidence.MEDIUM
                                        : GitLabEndpointUseCaseConfidence.HIGH,
                                limitations
                        ));
                    }
                }
            }
        }
        return endpoints;
    }

    private boolean isController(GitLabEndpointUseCaseTypeInfo type) {
        return type.kind() == GitLabEndpointUseCaseTypeKind.CLASS
                && type.annotations().stream().anyMatch(annotation ->
                "RestController".equals(simpleName(annotation)) || "Controller".equals(simpleName(annotation)));
    }

    private List<MappingDefinition> mappings(List<GitLabEndpointUseCaseAnnotationInfo> annotations) {
        var mappings = new ArrayList<MappingDefinition>();
        for (var annotation : annotations != null ? annotations : List.<GitLabEndpointUseCaseAnnotationInfo>of()) {
            var annotationName = simpleName(annotation.name());
            var httpMethods = httpMethods(annotationName, annotation.expression());
            if (httpMethods.isEmpty()) {
                continue;
            }

            var paths = annotationPaths(annotation.expression());
            mappings.add(new MappingDefinition(
                    httpMethods,
                    paths,
                    paths.isEmpty() ? pathExpression(annotation.expression()) : null,
                    annotation.line()
            ));
        }
        return List.copyOf(mappings);
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
        var matcher = REQUEST_METHOD_PATTERN.matcher(annotationText != null ? annotationText : "");
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods.isEmpty() ? List.of("ANY") : List.copyOf(methods);
    }

    private List<String> annotationPaths(String annotationText) {
        var paths = new LinkedHashSet<String>();
        var safeAnnotationText = annotationText != null ? annotationText : "";
        var attributeMatcher = ATTRIBUTE_PATH_PATTERN.matcher(safeAnnotationText);
        while (attributeMatcher.find()) {
            addPathLiterals(paths, attributeMatcher.group(1));
        }

        if (paths.isEmpty()) {
            var open = safeAnnotationText.indexOf('(');
            var close = safeAnnotationText.lastIndexOf(')');
            if (open >= 0 && close > open) {
                var arguments = safeAnnotationText.substring(open + 1, close).trim();
                if (arguments.startsWith("\"") || arguments.startsWith("{")) {
                    addPathLiterals(paths, arguments);
                }
            }
        }

        return List.copyOf(paths);
    }

    private void addPathLiterals(LinkedHashSet<String> paths, String expression) {
        var matcher = STRING_LITERAL_PATTERN.matcher(expression != null ? expression : "");
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
        var safeAnnotationText = annotationText != null ? annotationText : "";
        var open = safeAnnotationText.indexOf('(');
        var close = safeAnnotationText.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        var expression = safeAnnotationText.substring(open + 1, close).trim();
        if (!StringUtils.hasText(expression) || expression.contains("\"")) {
            return null;
        }
        return abbreviate(expression, 220);
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

    private boolean matchesHttpMethod(GitLabEndpointUseCaseEndpointCandidate endpoint, String httpMethod) {
        return endpoint.httpMethods().contains(httpMethod) || endpoint.httpMethods().contains("ANY");
    }

    private boolean matchesPath(String pathPattern, String requestedPath) {
        var normalizedPattern = normalizeHttpPath(pathPattern);
        var normalizedRequestedPath = normalizeHttpPath(requestedPath);
        if (!StringUtils.hasText(normalizedPattern) || !StringUtils.hasText(normalizedRequestedPath)) {
            return false;
        }
        if (normalizedPattern.equals(normalizedRequestedPath)) {
            return true;
        }
        return Pattern.compile(pathPatternRegex(normalizedPattern))
                .matcher(normalizedRequestedPath)
                .matches();
    }

    private String pathPatternRegex(String pathPattern) {
        if ("/".equals(pathPattern)) {
            return "^/$";
        }
        var regex = new StringBuilder("^");
        for (var segment : pathPattern.substring(1).split("/")) {
            regex.append('/');
            if (segment.startsWith("{") && segment.endsWith("}")) {
                regex.append("[^/]+");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private String normalizeHttpMethod(String httpMethod) {
        return StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeHttpPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        var normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean hasEndpointMatchInput(GitLabEndpointUseCaseContextRequest request) {
        return request != null
                && (StringUtils.hasText(request.endpointId())
                || (StringUtils.hasText(request.httpMethod()) && StringUtils.hasText(request.endpointPath())));
    }

    private String endpointId(List<String> methods, String path, String controllerClass, String handlerMethod) {
        return String.join(",", methods) + " " + (StringUtils.hasText(path) ? path : "<unresolved-path>")
                + " -> " + controllerClass + "#" + handlerMethod;
    }

    private List<String> endpointIds(List<GitLabEndpointUseCaseEndpointCandidate> endpoints) {
        return endpoints.stream()
                .map(GitLabEndpointUseCaseEndpointCandidate::endpointId)
                .limit(20)
                .toList();
    }

    private String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var dotIndex = value.lastIndexOf('.');
        return dotIndex >= 0 ? value.substring(dotIndex + 1) : value;
    }

    private List<String> joinLists(List<String> left, List<String> right) {
        var joined = new ArrayList<String>();
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

    private record MappingDefinition(
            List<String> httpMethods,
            List<String> paths,
            String pathExpression,
            Integer lineStart
    ) {
        private static MappingDefinition empty() {
            return new MappingDefinition(List.of("ANY"), List.of(""), null, null);
        }
    }
}
