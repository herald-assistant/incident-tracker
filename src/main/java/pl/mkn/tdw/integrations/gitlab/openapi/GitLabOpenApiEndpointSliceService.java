package pl.mkn.tdw.integrations.gitlab.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GitLabOpenApiEndpointSliceService {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
    public static final String STATUS_PARSE_ERROR = "PARSE_ERROR";
    public static final String STATUS_NOT_OPENAPI = "NOT_OPENAPI";
    public static final String STATUS_UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION";
    public static final String STATUS_ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND";
    public static final String STATUS_AMBIGUOUS_OPERATION = "AMBIGUOUS_OPERATION";

    private static final int MAX_OPENAPI_FILE_CHARACTERS = 500_000;
    private static final int DEFAULT_MAX_OUTPUT_CHARACTERS = 20_000;
    public static final int MIN_OUTPUT_CHARACTERS = 1_000;
    public static final int MAX_OUTPUT_CHARACTERS = 50_000;
    private static final int DEFAULT_SCHEMA_DEPTH = 2;
    public static final int MAX_SCHEMA_DEPTH = 4;
    private static final int MAX_REFERENCES = 40;
    private static final int MAX_CANDIDATES = 12;
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

    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final ObjectMapper objectMapper;

    public GitLabOpenApiEndpointSliceResponse readEndpointSlice(GitLabOpenApiEndpointSliceRequest request) {
        var group = required(request.group(), "group");
        var projectName = required(request.projectName(), "projectName");
        var branch = required(request.branch(), "branch");
        var filePath = required(request.filePath(), "filePath");
        var httpMethod = normalizeOptionalHttpMethod(request.httpMethod());
        var endpointPath = normalizeOptionalEndpointPath(request.endpointPath());
        var requestedOperationId = optional(request.operationId());
        requireOperationLocator(httpMethod, endpointPath, requestedOperationId);
        var includeReferencedSchemas = request.includeReferencedSchemas() == null || request.includeReferencedSchemas();
        var schemaDepth = normalizeDepth(request.schemaDepth());
        var maxCharacters = normalizeMaxCharacters(request.maxCharacters());
        var limitations = new ArrayList<String>();
        var format = documentFormat(filePath);

        if (format == null) {
            limitations.add("Requested file is not a JSON, YAML or YML file.");
            return response(
                    group,
                    projectName,
                    branch,
                    filePath,
                    STATUS_UNSUPPORTED_FILE_TYPE,
                    null,
                    null,
                    httpMethod,
                    endpointPath,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    "",
                    false,
                    limitations
            );
        }

        var fileContent = gitLabRepositoryPort.readFile(
                group,
                projectName,
                branch,
                filePath,
                MAX_OPENAPI_FILE_CHARACTERS
        );
        if (fileContent != null && fileContent.truncated()) {
            limitations.add("OpenAPI document was truncated before parsing.");
        }
        var content = fileContent != null ? fileContent.content() : null;
        var document = parseDocument(filePath, format, content, limitations);
        if (document.isEmpty()) {
            return response(
                    group,
                    projectName,
                    branch,
                    filePath,
                    limitations.stream().anyMatch(limitation -> limitation.startsWith("Could not parse"))
                            ? STATUS_PARSE_ERROR
                            : STATUS_NOT_OPENAPI,
                    null,
                    null,
                    httpMethod,
                    endpointPath,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    "",
                    false,
                    limitations
            );
        }

        var spec = spec(document);
        if (spec == null) {
            limitations.add("Document does not expose OpenAPI `openapi` or Swagger `swagger` version.");
            return response(
                    group,
                    projectName,
                    branch,
                    filePath,
                    STATUS_NOT_OPENAPI,
                    null,
                    null,
                    httpMethod,
                    endpointPath,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    "",
                    false,
                    limitations
            );
        }
        if (!spec.supported()) {
            limitations.add("Only OpenAPI 3.x and Swagger 2.0 endpoint slices are supported.");
            return response(
                    group,
                    projectName,
                    branch,
                    filePath,
                    STATUS_UNSUPPORTED_VERSION,
                    spec.type(),
                    spec.version(),
                    httpMethod,
                    endpointPath,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    "",
                    false,
                    limitations
            );
        }

        var paths = asMap(document.get("paths"));
        var allCandidates = operationCandidates(paths);
        var matches = findOperations(paths, httpMethod, endpointPath, requestedOperationId);
        if (matches.size() > 1) {
            limitations.add("OpenAPI document contains more than one operation matching the requested locator.");
            return typedResponse(
                    group, projectName, branch, filePath, STATUS_AMBIGUOUS_OPERATION, spec,
                    httpMethod, endpointPath, null, null, null, null, List.of(), null, "", false,
                    limitations, format, null, Map.of(), Map.of(), Map.of(),
                    matches.stream().map(this::candidate).limit(MAX_CANDIDATES).toList(), List.of(), List.of()
            );
        }
        var match = matches.isEmpty() ? null : matches.get(0);
        var matchedPath = match != null ? match.path() : null;
        var pathItem = asMap(paths.get(matchedPath));
        var operation = match != null ? match.operation() : Map.<String, Object>of();
        if (match == null || operation.isEmpty()) {
            limitations.add("OpenAPI document does not contain the requested operation locator.");
            return typedResponse(
                    group, projectName, branch, filePath, STATUS_ENDPOINT_NOT_FOUND, spec,
                    httpMethod, endpointPath, matchedPath, null, null, null, List.of(), null, "", false,
                    limitations, format, null, Map.of(), Map.of(), Map.of(),
                    relevantCandidates(allCandidates, httpMethod, endpointPath, requestedOperationId), List.of(), List.of()
            );
        }

        var effectiveHttpMethod = match.httpMethod();
        var referencedComponents = new LinkedHashMap<String, Object>();
        if (includeReferencedSchemas) {
            referencedComponents.putAll(referencedComponents(
                    document, pathItem.get("parameters"), operation, schemaDepth, limitations));
        }
        var unresolvedReferences = externalReferences(pathItem.get("parameters"), operation);
        if (!unresolvedReferences.isEmpty()) {
            limitations.add("External OpenAPI references are reported but not resolved by this tool.");
        }

        var bounded = boundedSlice(
                document, spec, matchedPath, effectiveHttpMethod, pathItem, operation,
                referencedComponents, maxCharacters);
        if (bounded.truncated()) {
            limitations.add("OpenAPI endpoint slice was compacted to maxCharacters=" + maxCharacters + ".");
        }
        var sourceRef = projectName + ":" + filePath + "#" + effectiveHttpMethod + " " + matchedPath;
        var operationId = text(operation.get("operationId"));
        var summary = text(operation.get("summary"));
        var description = text(operation.get("description"));
        var tags = stringList(operation.get("tags"));
        var rendered = renderContent(
                sourceRef, spec, effectiveHttpMethod,
                StringUtils.hasText(endpointPath) ? endpointPath : matchedPath,
                matchedPath, bounded.endpointSlice());

        return typedResponse(
                group, projectName, branch, filePath, STATUS_OK, spec, effectiveHttpMethod,
                StringUtils.hasText(endpointPath) ? endpointPath : matchedPath, matchedPath,
                operationId, summary, description, tags, sourceRef, rendered, bounded.truncated(),
                limitations, format, match.matchedBy(), effectiveContext(document, pathItem),
                bounded.operation(), bounded.referencedComponents(), List.of(), unresolvedReferences,
                bounded.omittedSections()
        );
    }

    private List<String> externalReferences(Object... values) {
        var references = new LinkedHashSet<String>();
        for (var value : values) {
            collectExternalReferences(value, references);
        }
        return List.copyOf(references);
    }

    private void collectExternalReferences(Object value, LinkedHashSet<String> references) {
        if (value instanceof Map<?, ?> map) {
            var ref = text(map.get("$ref"));
            if (StringUtils.hasText(ref) && !localRef(ref)) {
                references.add(ref);
            }
            map.values().forEach(child -> collectExternalReferences(child, references));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child -> collectExternalReferences(child, references));
        }
    }

    private List<OperationMatch> findOperations(
            Map<String, Object> paths,
            String httpMethod,
            String endpointPath,
            String operationId
    ) {
        if (StringUtils.hasText(operationId)) {
            return operationMatches(paths).stream()
                    .filter(match -> operationId.equals(text(match.operation().get("operationId"))))
                    .filter(match -> !StringUtils.hasText(httpMethod) || httpMethod.equals(match.httpMethod()))
                    .filter(match -> !StringUtils.hasText(endpointPath)
                            || pathCompatible(match.path(), endpointPath))
                    .map(match -> match.withMatchedBy(
                            StringUtils.hasText(httpMethod) && StringUtils.hasText(endpointPath)
                                    ? "METHOD_PATH_AND_OPERATION_ID"
                                    : "OPERATION_ID"))
                    .toList();
        }
        var matchedPath = matchPath(paths, endpointPath);
        if (!StringUtils.hasText(matchedPath)) {
            return List.of();
        }
        var operation = asMap(asMap(paths.get(matchedPath)).get(httpMethod.toLowerCase(Locale.ROOT)));
        if (operation.isEmpty()) {
            return List.of();
        }
        return List.of(new OperationMatch(
                httpMethod,
                matchedPath,
                operation,
                pathMatchKind(matchedPath, endpointPath)
        ));
    }

    private List<OperationMatch> operationMatches(Map<String, Object> paths) {
        var matches = new ArrayList<OperationMatch>();
        for (var pathEntry : paths.entrySet()) {
            var pathItem = asMap(pathEntry.getValue());
            for (var method : HTTP_METHODS) {
                var operation = asMap(pathItem.get(method.toLowerCase(Locale.ROOT)));
                if (!operation.isEmpty()) {
                    matches.add(new OperationMatch(method, pathEntry.getKey(), operation, null));
                }
            }
        }
        return List.copyOf(matches);
    }

    private List<GitLabOpenApiOperationCandidate> operationCandidates(Map<String, Object> paths) {
        return operationMatches(paths).stream().map(this::candidate).toList();
    }

    private GitLabOpenApiOperationCandidate candidate(OperationMatch match) {
        return new GitLabOpenApiOperationCandidate(
                match.httpMethod(),
                match.path(),
                text(match.operation().get("operationId")),
                text(match.operation().get("summary")),
                stringList(match.operation().get("tags"))
        );
    }

    private List<GitLabOpenApiOperationCandidate> relevantCandidates(
            List<GitLabOpenApiOperationCandidate> candidates,
            String httpMethod,
            String endpointPath,
            String operationId
    ) {
        var relevant = candidates.stream()
                .filter(candidate -> !StringUtils.hasText(httpMethod)
                        || httpMethod.equals(candidate.httpMethod()))
                .filter(candidate -> !StringUtils.hasText(operationId)
                        || containsIgnoreCase(candidate.operationId(), operationId)
                        || containsIgnoreCase(operationId, candidate.operationId()))
                .filter(candidate -> !StringUtils.hasText(endpointPath)
                        || pathCompatible(candidate.path(), endpointPath)
                        || sharesLastPathSegment(candidate.path(), endpointPath))
                .limit(MAX_CANDIDATES)
                .toList();
        return relevant.isEmpty()
                ? candidates.stream().limit(MAX_CANDIDATES).toList()
                : relevant;
    }

    private boolean containsIgnoreCase(String value, String query) {
        return StringUtils.hasText(value) && StringUtils.hasText(query)
                && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private boolean sharesLastPathSegment(String first, String second) {
        var left = stripTrailingSlash(first);
        var right = stripTrailingSlash(second);
        var leftIndex = left.lastIndexOf('/');
        var rightIndex = right.lastIndexOf('/');
        return leftIndex >= 0 && rightIndex >= 0
                && left.substring(leftIndex + 1).equalsIgnoreCase(right.substring(rightIndex + 1));
    }

    private boolean pathCompatible(String candidate, String requested) {
        return stripTrailingSlash(candidate).equals(stripTrailingSlash(requested))
                || templateCompatible(candidate, requested);
    }

    private String pathMatchKind(String matchedPath, String requestedPath) {
        if (matchedPath.equals(requestedPath)) {
            return "METHOD_PATH_EXACT";
        }
        if (stripTrailingSlash(matchedPath).equals(stripTrailingSlash(requestedPath))) {
            return "METHOD_PATH_NORMALIZED";
        }
        return "METHOD_PATH_TEMPLATE";
    }

    private BoundedSlice boundedSlice(
            Map<String, Object> document,
            SpecVersion spec,
            String matchedPath,
            String httpMethod,
            Map<String, Object> pathItem,
            Map<String, Object> operation,
            Map<String, Object> referencedComponents,
            int maxCharacters
    ) {
        var originalSlice = endpointSlice(document, spec, matchedPath, httpMethod, pathItem, operation);
        if (!referencedComponents.isEmpty()) {
            originalSlice.put("referencedComponents", referencedComponents);
        }
        if (serializedLength(originalSlice) <= maxCharacters) {
            return new BoundedSlice(originalSlice, operation, referencedComponents, false, List.of());
        }

        var omitted = new ArrayList<String>();
        omitted.add("examples-and-long-descriptions");
        var compactOperation = asMap(compactValue(operation, null));
        var compactReferences = new LinkedHashMap<String, Object>();
        referencedComponents.forEach((key, value) -> compactReferences.put(key, compactValue(value, null)));
        var compactSlice = endpointSlice(document, spec, matchedPath, httpMethod, pathItem, compactOperation);
        if (!compactReferences.isEmpty()) {
            compactSlice.put("referencedComponents", compactReferences);
        }

        while (serializedLength(compactSlice) > maxCharacters && !compactReferences.isEmpty()) {
            var lastKey = compactReferences.keySet().stream().reduce((first, second) -> second).orElse(null);
            compactReferences.remove(lastKey);
            omitted.add("referencedComponents:" + lastKey);
            if (compactReferences.isEmpty()) {
                compactSlice.remove("referencedComponents");
            }
        }

        for (var field : List.of("callbacks", "externalDocs", "description", "requestBody", "responses", "parameters")) {
            if (serializedLength(compactSlice) <= maxCharacters) {
                break;
            }
            if (compactOperation.remove(field) != null) {
                omitted.add("operation." + field);
            }
        }
        if (serializedLength(compactSlice) > maxCharacters) {
            var minimalOperation = new LinkedHashMap<String, Object>();
            var minimalOperationId = text(operation.get("operationId"));
            if (StringUtils.hasText(minimalOperationId)) {
                minimalOperation.put("operationId", truncate(minimalOperationId, 200));
            }
            var minimalSummary = text(operation.get("summary"));
            if (StringUtils.hasText(minimalSummary)) {
                minimalOperation.put("summary", truncate(minimalSummary, 200));
            }
            var minimalTags = stringList(operation.get("tags")).stream()
                    .limit(5)
                    .map(tag -> truncate(tag, 60))
                    .toList();
            if (!minimalTags.isEmpty()) {
                minimalOperation.put("tags", minimalTags);
            }
            if (operation.get("deprecated") instanceof Boolean deprecated) {
                minimalOperation.put("deprecated", deprecated);
            }
            compactReferences.clear();
            compactSlice = endpointSlice(
                    Map.of(), spec, matchedPath, httpMethod, Map.of(), minimalOperation);
            compactOperation = minimalOperation;
            omitted.add("operation-contract-details");
        }
        return new BoundedSlice(compactSlice, compactOperation, compactReferences, true, List.copyOf(omitted));
    }

    private String truncate(String value, int maxCharacters) {
        return value.length() > maxCharacters
                ? value.substring(0, maxCharacters) + "..."
                : value;
    }

    private Object compactValue(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                var childKey = String.valueOf(entry.getKey());
                if ("example".equals(childKey) || "examples".equals(childKey)) {
                    continue;
                }
                result.put(childKey, compactValue(entry.getValue(), childKey));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> compactValue(item, key)).toList();
        }
        if (value instanceof String string && "description".equals(key) && string.length() > 1_000) {
            return string.substring(0, 1_000) + "...";
        }
        return value;
    }

    private int serializedLength(Object value) {
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private Map<String, Object> effectiveContext(Map<String, Object> document, Map<String, Object> pathItem) {
        var context = new LinkedHashMap<String, Object>();
        for (var key : List.of("servers", "security", "host", "basePath", "schemes", "consumes", "produces")) {
            if (document.get(key) != null) {
                context.put(key, compactValue(document.get(key), key));
            }
        }
        if (pathItem.get("servers") != null) {
            context.put("pathServers", compactValue(pathItem.get("servers"), "servers"));
        }
        if (pathItem.get("parameters") != null) {
            context.put("pathParameters", compactValue(pathItem.get("parameters"), "parameters"));
        }
        return context;
    }

    private LinkedHashMap<String, Object> endpointSlice(
            Map<String, Object> document,
            SpecVersion spec,
            String matchedPath,
            String httpMethod,
            Map<String, Object> pathItem,
            Map<String, Object> operation
    ) {
        var slice = new LinkedHashMap<String, Object>();
        slice.put(spec.type(), spec.version());
        var info = slimMap(asMap(document.get("info")), "title", "version", "description");
        if (!info.isEmpty()) {
            slice.put("info", info);
        }

        var operationNode = new LinkedHashMap<String, Object>();
        var pathParameters = pathItem.get("parameters");
        if (pathParameters != null) {
            operationNode.put("parameters", pathParameters);
        }
        operationNode.put(httpMethod.toLowerCase(Locale.ROOT), operation);

        var paths = new LinkedHashMap<String, Object>();
        paths.put(matchedPath, operationNode);
        slice.put("paths", paths);
        return slice;
    }

    private LinkedHashMap<String, Object> referencedComponents(
            Map<String, Object> document,
            Object pathParameters,
            Object operation,
            int schemaDepth,
            List<String> limitations
    ) {
        var resolved = new LinkedHashMap<String, Object>();
        var visiting = new LinkedHashSet<String>();
        collectReferences(pathParameters, document, resolved, visiting, schemaDepth, limitations);
        collectReferences(operation, document, resolved, visiting, schemaDepth, limitations);
        return resolved;
    }

    private void collectReferences(
            Object value,
            Map<String, Object> document,
            LinkedHashMap<String, Object> resolved,
            LinkedHashSet<String> visiting,
            int depth,
            List<String> limitations
    ) {
        if (value == null || depth < 0) {
            return;
        }
        if (resolved.size() >= MAX_REFERENCES) {
            if (limitations.stream().noneMatch(limitation -> limitation.startsWith("OpenAPI local references truncated"))) {
                limitations.add("OpenAPI local references truncated to maxReferences=" + MAX_REFERENCES + ".");
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            var ref = text(map.get("$ref"));
            if (localRef(ref)) {
                if (!resolved.containsKey(ref)) {
                    var target = resolveLocalRef(ref, document);
                    if (target != null) {
                        resolved.put(ref, target);
                        if (visiting.add(ref)) {
                            collectReferences(target, document, resolved, visiting, depth - 1, limitations);
                            visiting.remove(ref);
                        }
                    } else {
                        limitations.add("Could not resolve local OpenAPI reference " + ref + ".");
                    }
                }
            }
            for (var child : map.values()) {
                collectReferences(child, document, resolved, visiting, depth, limitations);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (var child : list) {
                collectReferences(child, document, resolved, visiting, depth, limitations);
            }
        }
    }

    private Object resolveLocalRef(String ref, Map<String, Object> document) {
        if (!localRef(ref)) {
            return null;
        }
        Object current = document;
        for (var segment : ref.substring(2).split("/")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(unescapePointer(segment));
        }
        return current;
    }

    private boolean localRef(String ref) {
        return StringUtils.hasText(ref) && ref.startsWith("#/");
    }

    private String unescapePointer(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }

    private String renderContent(
            String sourceRef,
            SpecVersion spec,
            String httpMethod,
            String requestedPath,
            String matchedPath,
            Map<String, Object> endpointSlice
    ) {
        return """
                # OpenAPI Endpoint Contract

                source: `%s`
                spec: `%s %s`
                requested: `%s %s`
                matched: `%s %s`

                ```json
                %s
                ```
                """.formatted(
                sourceRef,
                spec.type(),
                spec.version(),
                httpMethod,
                requestedPath,
                httpMethod,
                matchedPath,
                toPrettyJson(endpointSlice)
        ).trim();
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> parseDocument(
            String filePath,
            String format,
            String content,
            List<String> limitations
    ) {
        if (!StringUtils.hasText(content)) {
            limitations.add("OpenAPI document is empty.");
            return Map.of();
        }
        if ("JSON".equals(format)) {
            try {
                var document = objectMapper.readValue(
                        content,
                        new TypeReference<LinkedHashMap<String, Object>>() { }
                );
                return document != null ? document : Map.of();
            } catch (JsonProcessingException exception) {
                limitations.add("Could not parse OpenAPI JSON structure from " + filePath + ": "
                        + safeMessage(exception));
                return Map.of();
            }
        }
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

    private SpecVersion spec(Map<String, Object> document) {
        var openApi = text(document.get("openapi"));
        if (StringUtils.hasText(openApi)) {
            return new SpecVersion("openapi", openApi, openApi.startsWith("3."));
        }
        var swagger = text(document.get("swagger"));
        if (StringUtils.hasText(swagger)) {
            return new SpecVersion("swagger", swagger, swagger.startsWith("2."));
        }
        return null;
    }

    private String matchPath(Map<String, Object> paths, String endpointPath) {
        if (paths.containsKey(endpointPath)) {
            return endpointPath;
        }
        var normalizedEndpointPath = stripTrailingSlash(endpointPath);
        for (var candidate : paths.keySet()) {
            if (stripTrailingSlash(candidate).equals(normalizedEndpointPath)) {
                return candidate;
            }
        }
        for (var candidate : paths.keySet()) {
            if (templateCompatible(candidate, endpointPath)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean templateCompatible(String candidate, String endpointPath) {
        var candidateSegments = stripTrailingSlash(candidate).split("/");
        var endpointSegments = stripTrailingSlash(endpointPath).split("/");
        if (candidateSegments.length != endpointSegments.length) {
            return false;
        }
        for (var index = 0; index < candidateSegments.length; index++) {
            var left = candidateSegments[index];
            var right = endpointSegments[index];
            if (left.equals(right)) {
                continue;
            }
            if (templateSegment(left) && templateSegment(right)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean templateSegment(String value) {
        return value.startsWith("{") && value.endsWith("}") && value.length() > 2;
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = normalizeOptionalEndpointPath(value);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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

    private LinkedHashMap<String, Object> slimMap(Map<String, Object> source, String... keys) {
        var result = new LinkedHashMap<String, Object>();
        for (var key : keys) {
            var value = source.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            var values = new LinkedHashSet<String>();
            for (var item : list) {
                var text = text(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
            return List.copyOf(values);
        }
        var text = text(value);
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private GitLabOpenApiEndpointSliceResponse response(
            String group,
            String projectName,
            String branch,
            String filePath,
            String status,
            String specType,
            String specVersion,
            String httpMethod,
            String endpointPath,
            String matchedPath,
            String operationId,
            String summary,
            String description,
            List<String> tags,
            String sourceRef,
            String content,
            boolean truncated,
            List<String> limitations
    ) {
        var safeContent = content != null ? content : "";
        return new GitLabOpenApiEndpointSliceResponse(
                group,
                projectName,
                branch,
                filePath,
                status,
                specType,
                specVersion,
                httpMethod,
                endpointPath,
                matchedPath,
                operationId,
                summary,
                description,
                tags,
                sourceRef,
                safeContent,
                safeContent.length(),
                truncated,
                limitations
        );
    }

    private GitLabOpenApiEndpointSliceResponse typedResponse(
            String group,
            String projectName,
            String branch,
            String filePath,
            String status,
            SpecVersion spec,
            String httpMethod,
            String endpointPath,
            String matchedPath,
            String operationId,
            String summary,
            String description,
            List<String> tags,
            String sourceRef,
            String content,
            boolean truncated,
            List<String> limitations,
            String format,
            String matchedBy,
            Map<String, Object> effectiveContext,
            Map<String, Object> operation,
            Map<String, Object> referencedComponents,
            List<GitLabOpenApiOperationCandidate> candidates,
            List<String> unresolvedReferences,
            List<String> omittedSections
    ) {
        var safeContent = content != null ? content : "";
        return new GitLabOpenApiEndpointSliceResponse(
                group,
                projectName,
                branch,
                filePath,
                status,
                spec != null ? spec.type() : null,
                spec != null ? spec.version() : null,
                httpMethod,
                endpointPath,
                matchedPath,
                operationId,
                summary,
                description,
                tags,
                sourceRef,
                safeContent,
                safeContent.length(),
                truncated,
                limitations,
                format,
                matchedBy,
                effectiveContext,
                operation,
                referencedComponents,
                candidates,
                unresolvedReferences,
                omittedSections
        );
    }

    private String documentFormat(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        var normalized = filePath.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".json")) {
            return "JSON";
        }
        if (normalized.endsWith(".yaml") || normalized.endsWith(".yml")) {
            return "YAML";
        }
        return null;
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("OpenAPI endpoint slice requires " + label + ".");
        }
        return value.trim();
    }

    private String normalizeOptionalHttpMethod(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var method = value.trim().toUpperCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(method)) {
            throw new IllegalArgumentException("OpenAPI endpoint slice does not support HTTP method " + method + ".");
        }
        return method;
    }

    private String normalizeOptionalEndpointPath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private void requireOperationLocator(String httpMethod, String endpointPath, String operationId) {
        if (StringUtils.hasText(operationId)) {
            return;
        }
        if (!StringUtils.hasText(httpMethod) || !StringUtils.hasText(endpointPath)) {
            throw new IllegalArgumentException(
                    "OpenAPI endpoint slice requires operationId or both httpMethod and endpointPath.");
        }
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizeDepth(Integer value) {
        if (value == null || value < 0) {
            return DEFAULT_SCHEMA_DEPTH;
        }
        return Math.min(value, MAX_SCHEMA_DEPTH);
    }

    private int normalizeMaxCharacters(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_MAX_OUTPUT_CHARACTERS;
        }
        return Math.max(MIN_OUTPUT_CHARACTERS, Math.min(value, MAX_OUTPUT_CHARACTERS));
    }

    private String text(Object value) {
        return value != null && StringUtils.hasText(String.valueOf(value))
                ? String.valueOf(value).trim()
                : null;
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private record SpecVersion(String type, String version, boolean supported) {
    }

    private record OperationMatch(
            String httpMethod,
            String path,
            Map<String, Object> operation,
            String matchedBy
    ) {
        private OperationMatch withMatchedBy(String value) {
            return new OperationMatch(httpMethod, path, operation, value);
        }
    }

    private record BoundedSlice(
            Map<String, Object> endpointSlice,
            Map<String, Object> operation,
            Map<String, Object> referencedComponents,
            boolean truncated,
            List<String> omittedSections
    ) {
    }
}
