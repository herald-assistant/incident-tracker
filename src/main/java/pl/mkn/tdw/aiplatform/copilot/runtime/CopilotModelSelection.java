package pl.mkn.tdw.aiplatform.copilot.runtime;

public record CopilotModelSelection(
        String model,
        String reasoningEffort
) {

    public static final CopilotModelSelection DEFAULT = new CopilotModelSelection(null, null);

    public CopilotModelSelection {
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
