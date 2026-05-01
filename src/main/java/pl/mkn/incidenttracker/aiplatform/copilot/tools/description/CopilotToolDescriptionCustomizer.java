package pl.mkn.incidenttracker.aiplatform.copilot.tools.description;

@FunctionalInterface
public interface CopilotToolDescriptionCustomizer {

    String customize(String toolName, String description);
}
