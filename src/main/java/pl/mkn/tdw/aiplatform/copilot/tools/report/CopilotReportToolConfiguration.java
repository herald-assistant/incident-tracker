package pl.mkn.tdw.aiplatform.copilot.tools.report;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CopilotReportToolConfiguration {

    @Bean
    ToolCallbackProvider copilotReportToolCallbackProvider(CopilotReportTools copilotReportTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(copilotReportTools)
                .build();
    }
}
