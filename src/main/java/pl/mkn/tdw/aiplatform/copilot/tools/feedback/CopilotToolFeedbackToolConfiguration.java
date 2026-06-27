package pl.mkn.tdw.aiplatform.copilot.tools.feedback;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CopilotToolFeedbackToolConfiguration {

    @Bean
    ToolCallbackProvider copilotToolFeedbackToolCallbackProvider(CopilotToolFeedbackTools copilotToolFeedbackTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(copilotToolFeedbackTools)
                .build();
    }
}
