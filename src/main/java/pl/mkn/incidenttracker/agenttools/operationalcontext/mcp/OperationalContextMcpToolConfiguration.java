package pl.mkn.incidenttracker.agenttools.operationalcontext.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "analysis.operational-context", name = "enabled", havingValue = "true")
public class OperationalContextMcpToolConfiguration {

    @Bean
    ToolCallbackProvider operationalContextToolCallbackProvider(OperationalContextMcpTools operationalContextMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(operationalContextMcpTools)
                .build();
    }
}
