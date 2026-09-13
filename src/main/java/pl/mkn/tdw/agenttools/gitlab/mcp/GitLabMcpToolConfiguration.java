package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitLabMcpToolConfiguration {

    @Bean
    ToolCallbackProvider gitLabToolCallbackProvider(
            GitLabMcpTools gitLabMcpTools,
            GitLabRepositoryNavigationMcpTools navigationTools
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(gitLabMcpTools, navigationTools)
                .build();
    }

}
