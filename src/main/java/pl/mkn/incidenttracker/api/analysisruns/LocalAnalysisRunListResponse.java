package pl.mkn.incidenttracker.api.analysisruns;

import java.util.List;

public record LocalAnalysisRunListResponse(
        List<LocalAnalysisRunListItemResponse> runs
) {
    public LocalAnalysisRunListResponse {
        runs = runs != null ? List.copyOf(runs) : List.of();
    }
}
