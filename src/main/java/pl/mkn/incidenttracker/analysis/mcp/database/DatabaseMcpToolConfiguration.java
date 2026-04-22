package pl.mkn.incidenttracker.analysis.mcp.database;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseMcpToolConfiguration {

    @Bean
    ToolCallbackProvider databaseToolCallbackProvider(DatabaseMcpTools databaseMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(databaseMcpTools)
                .build();
    }
}
