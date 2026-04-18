package pl.mkn.incidenttracker.analysis.mcp.elasticsearch;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticMcpToolConfiguration {

    @Bean
    ToolCallbackProvider elasticToolCallbackProvider(ElasticMcpTools elasticMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(elasticMcpTools)
                .build();
    }

}
