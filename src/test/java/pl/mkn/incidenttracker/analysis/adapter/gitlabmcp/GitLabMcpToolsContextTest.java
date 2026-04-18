package pl.mkn.incidenttracker.analysis.adapter.gitlabmcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GitLabMcpToolsContextTest {

    @Autowired
    private ToolCallbackProvider[] toolCallbackProviders;

    @Test
    void shouldRegisterGitLabMcpToolsInSpringAi() {
        var toolNames = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("gitlab_search_repository_candidates"));
        assertTrue(toolNames.contains("gitlab_read_repository_file"));
        assertTrue(toolNames.contains("gitlab_read_repository_file_chunk"));
    }

}
