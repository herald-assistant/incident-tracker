package pl.mkn.tdw.features.systemassistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.agenttools.openapi.mcp.OpenApiMcpEndpoint;

/**
 * The feature composes neutral MCP capability; its single flag owns exposure.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantMcpConfiguration {
    @Bean
    OpenApiMcpEndpoint systemAssistantOpenApiEndpoint(OpenApiToolCatalog catalog,
                                                      OpenApiToolSetFactory tools, ObjectMapper mapper) {
        return new OpenApiMcpEndpoint(catalog, tools, mapper);
    }

    @Bean
    RouterFunction<ServerResponse> systemAssistantMcpRoutes(OpenApiMcpEndpoint endpoint) {
        return endpoint.routes();
    }
}
