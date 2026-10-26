package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitLabDiffMcpToolConfiguration {
    @Bean
    ToolCallbackProvider gitLabDiffToolCallbackProvider(GitLabDiffMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
