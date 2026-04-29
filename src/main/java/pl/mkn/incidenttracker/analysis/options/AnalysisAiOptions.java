package pl.mkn.incidenttracker.analysis.options;

import org.springframework.util.StringUtils;

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
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
