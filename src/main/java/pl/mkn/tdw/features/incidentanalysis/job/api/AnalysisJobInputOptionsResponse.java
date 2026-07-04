package pl.mkn.tdw.features.incidentanalysis.job.api;

public record AnalysisJobInputOptionsResponse(
        LogSourceOption elasticsearch,
        LogSourceOption csvUpload
) {

    public record LogSourceOption(
            String source,
            boolean enabled,
            String disabledReason
    ) {
    }
}
