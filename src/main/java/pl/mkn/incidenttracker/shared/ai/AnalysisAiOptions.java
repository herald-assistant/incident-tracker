package pl.mkn.incidenttracker.shared.ai;

public record AnalysisAiOptions(
        String model,
        String reasoningEffort
) {

    public static final AnalysisAiOptions DEFAULT = new AnalysisAiOptions(null, null);

    public AnalysisAiOptions {
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
