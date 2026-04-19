package pl.mkn.incidenttracker.analysis.flow;

public record AnalysisResultVariants(
        AnalysisVariantResultResponse conservative,
        AnalysisVariantResultResponse exploratory
) {
}
