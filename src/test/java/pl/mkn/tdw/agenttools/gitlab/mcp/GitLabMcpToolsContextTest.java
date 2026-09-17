package pl.mkn.tdw.agenttools.gitlab.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GitLabMcpToolsContextTest {

    @Autowired
    private ToolCallbackProvider[] toolCallbackProviders;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterGitLabMcpToolsInSpringAi() {
        var toolNames = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("gitlab_search_repository_candidates"));
        assertTrue(toolNames.contains("gitlab_list_available_repositories"));
        assertTrue(toolNames.contains("gitlab_list_repository_endpoints"));
        assertTrue(toolNames.contains("gitlab_build_endpoint_use_case_context"));
        assertTrue(toolNames.contains("gitlab_build_java_method_use_case_context"));
        assertTrue(toolNames.contains("gitlab_find_class_references"));
        assertTrue(toolNames.contains("gitlab_read_repository_file"));
        assertTrue(toolNames.contains("gitlab_read_repository_files_by_path"));
        assertTrue(toolNames.contains("gitlab_read_repository_file_chunk"));
        assertTrue(toolNames.contains("gitlab_find_flow_context"));
        assertTrue(toolNames.contains("gitlab_read_repository_file_outline"));
        assertTrue(toolNames.contains("gitlab_read_repository_file_chunks"));
        assertTrue(toolNames.contains("gitlab_read_java_method_slice"));
        assertTrue(toolNames.contains("gitlab_read_openapi_endpoint_slice"));
        assertTrue(toolNames.contains("gitlab_read_frontend_route_branch_slice"));
        assertTrue(toolNames.contains("gitlab_read_frontend_typescript_symbol_slice"));
        assertTrue(toolNames.contains("gitlab_list_repository_files"));
        assertTrue(toolNames.contains("gitlab_list_repository_branches"));
        assertTrue(toolNames.contains("gitlab_list_repository_tree"));
        assertTrue(toolNames.contains("gitlab_search_repository_files"));
        assertFalse(toolNames.stream().anyMatch(name -> name.startsWith("gitlab_pinned_")));
        assertFalse(toolNames.contains("gitlab_build_frontend_screen_reachability"));
    }

    @Test
    void shouldExposeOneRepositoryNavigationSchemaWithoutGroupOrCommit() throws Exception {
        var callbacksByName = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool));

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("projectName", "search", "reason"),
                schemaProperties(callbacksByName.get("gitlab_list_repository_branches")).keySet());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("projectName", "branchRef", "pathPrefix", "afterPath", "reason"),
                schemaProperties(callbacksByName.get("gitlab_list_repository_files")).keySet());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("projectName", "branchRef", "path", "cursor", "reason"),
                schemaProperties(callbacksByName.get("gitlab_list_repository_tree")).keySet());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("projectName", "branchRef", "query", "pathPrefix", "afterPath", "reason"),
                schemaProperties(callbacksByName.get("gitlab_search_repository_files")).keySet());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("projectName", "branchRef", "applicationNames", "filePath", "maxCharacters", "reason"),
                schemaProperties(callbacksByName.get("gitlab_read_repository_file")).keySet());
    }

    @Test
    void shouldExposeScreenReferenceForRouteAndNaturalCoordinatesForTypeScript() throws Exception {
        var callbacksByName = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool));

        var routeProperties = schemaProperties(callbacksByName.get("gitlab_read_frontend_route_branch_slice"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("sliceRef", "reason"), routeProperties.keySet());

        var typeScriptProperties = schemaProperties(
                callbacksByName.get("gitlab_read_frontend_typescript_symbol_slice"));
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of(
                        "filePath", "declaringTypeName", "consumerFilePath", "moduleSpecifier",
                        "importedSymbol", "memberNames", "reason"),
                typeScriptProperties.keySet()
        );
        for (var properties : java.util.List.of(routeProperties, typeScriptProperties)) {
            assertFalse(properties.containsKey("group"));
            assertFalse(properties.containsKey("projectName"));
            assertFalse(properties.containsKey("ref"));
            assertFalse(properties.containsKey("toolContext"));
        }
        assertFalse(typeScriptProperties.containsKey("sliceRef"));
    }

    @Test
    void shouldKeepJavaMethodUseCaseContextFocusedAndSessionScoped() throws Exception {
        var callbacksByName = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool));
        var callback = callbacksByName.get("gitlab_build_java_method_use_case_context");

        assertSchemaPropertiesContain(callback, "className");
        assertSchemaPropertiesContain(callback, "methodName");
        assertSchemaPropertiesContain(callback, "maxResults");
        assertSchemaPropertiesDoNotContain(callback, "focusHints");
        assertSchemaPropertiesDoNotContain(callback, "maxFiles");
        assertSchemaPropertiesDoNotContain(callback, "gitLabGroup");
        assertSchemaPropertiesDoNotContain(callback, "gitLabBranch");
        assertSchemaPropertiesDoNotContain(callback, "environment");
        assertSchemaPropertiesDoNotContain(callback, "toolContext");
    }

    private void assertSchemaPropertiesContain(ToolCallback callback, String propertyName) throws Exception {
        assertNotNull(callback);
        var properties = schemaProperties(callback);

        assertTrue(properties.containsKey(propertyName));
    }

    private void assertSchemaPropertiesDoNotContain(ToolCallback callback, String propertyName) throws Exception {
        assertNotNull(callback);
        var properties = schemaProperties(callback);

        assertFalse(properties.containsKey(propertyName));
    }

    private Map<String, Object> schemaProperties(ToolCallback callback) throws Exception {
        var schema = objectMapper.readValue(callback.getToolDefinition().inputSchema(), Map.class);
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) schema.get("properties");

        assertNotNull(properties);
        return properties;
    }

}
