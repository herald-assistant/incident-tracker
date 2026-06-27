package pl.mkn.tdw.aiplatform.copilot.tools.description;

@FunctionalInterface
public interface CopilotToolDescriptionCustomizer {

    String customize(CopilotToolDescriptionContext descriptionContext, String toolName, String description);
}
