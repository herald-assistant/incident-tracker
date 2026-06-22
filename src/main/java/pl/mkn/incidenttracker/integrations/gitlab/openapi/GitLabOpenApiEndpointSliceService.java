package pl.mkn.incidenttracker.integrations.gitlab.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

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

    private static final int MAX_OPENAPI_FILE_CHARACTERS = 500_000;
    private static final int DEFAULT_MAX_OUTPUT_CHARACTERS = 20_000;
    public static final int MAX_OUTPUT_CHARACTERS = 50_000;
    private static final int DEFAULT_SCHEMA_DEPTH = 2;
    public static final int MAX_SCHEMA_DEPTH = 4;
    private static final int MAX_REFERENCES = 40;
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
        var httpMethod = normalizeHttpMethod(request.httpMethod());
        var endpointPath = normalizeEndpointPath(required(request.endpointPath(), "endpointPath"));
        var includeReferencedSchemas = request.includeReferencedSchemas() == null || request.includeReferencedSchemas();
        var schemaDepth = normalizeDepth(request.schemaDepth());
        var maxCharacters = normalizeMaxCharacters(request.maxCharacters());
        var limitations = new ArrayList<String>();

        if (!yamlFile(filePath)) {
            limitations.add("Requested file is not a YAML file.");
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
            limitations.add("OpenAPI YAML file was truncated before parsing.");
        }
        var content = fileContent != null ? fileContent.content() : null;
        var document = parseYamlDocument(filePath, content, limitations);
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
            limitations.add("YAML document does not expose OpenAPI `openapi` or Swagger `swagger` version.");
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
        var matchedPath = matchPath(paths, endpointPath);
        var pathItem = asMap(paths.get(matchedPath));
        var operation = asMap(pathItem.get(httpMethod.toLowerCase(Locale.ROOT)));
        if (!StringUtils.hasText(matchedPath) || operation.isEmpty()) {
            limitations.add("OpenAPI YAML does not contain requested operation " + httpMethod + " " + endpointPath + ".");
            return response(
                    group,
                    projectName,
                    branch,
                    filePath,
                    STATUS_ENDPOINT_NOT_FOUND,
                    spec.type(),
                    spec.version(),
                    httpMethod,
                    endpointPath,
                    matchedPath,
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

        var endpointSlice = endpointSlice(document, spec, matchedPath, httpMethod, pathItem, operation);
        if (includeReferencedSchemas) {
            var referencedComponents = referencedComponents(document, pathItem.get("parameters"), operation, schemaDepth, limitations);
            if (!referencedComponents.isEmpty()) {
                endpointSlice.put("referencedComponents", referencedComponents);
            }
        }

        var sourceRef = projectName + ":" + filePath + "#" + httpMethod + " " + matchedPath;
        var operationId = text(operation.get("operationId"));
        var summary = text(operation.get("summary"));
        var description = text(operation.get("description"));
        var tags = stringList(operation.get("tags"));
        var rendered = renderContent(sourceRef, spec, httpMethod, endpointPath, matchedPath, endpointSlice);
        var truncated = false;
        if (rendered.length() > maxCharacters) {
            limitations.add("OpenAPI endpoint slice was truncated to maxCharacters=" + maxCharacters + ".");
            rendered = rendered.substring(0, maxCharacters).stripTrailing()
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + "... truncated by gitlab_read_openapi_endpoint_slice ...";
            truncated = true;
        }

        return response(
                group,
                projectName,
                branch,
                filePath,
                STATUS_OK,
                spec.type(),
                spec.version(),
                httpMethod,
                endpointPath,
                matchedPath,
                operationId,
                summary,
                description,
                tags,
                sourceRef,
                rendered,
                truncated,
                limitations
        );
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

    private Map<String, Object> parseYamlDocument(String filePath, String content, List<String> limitations) {
        if (!StringUtils.hasText(content)) {
            limitations.add("OpenAPI YAML file is empty.");
            return Map.of();
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
        var normalized = normalizeEndpointPath(value);
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

    private boolean yamlFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        var normalized = filePath.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".yaml") || normalized.endsWith(".yml");
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("OpenAPI endpoint slice requires " + label + ".");
        }
        return value.trim();
    }

    private String normalizeHttpMethod(String value) {
        var method = required(value, "httpMethod").toUpperCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(method)) {
            throw new IllegalArgumentException("OpenAPI endpoint slice does not support HTTP method " + method + ".");
        }
        return method;
    }

    private String normalizeEndpointPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = value.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
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
        return Math.min(value, MAX_OUTPUT_CHARACTERS);
    }

    private String text(Object value) {
        return value != null && StringUtils.hasText(String.valueOf(value))
                ? String.valueOf(value).trim()
                : null;
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private record SpecVersion(String type, String version, boolean supported) {
    }
}
