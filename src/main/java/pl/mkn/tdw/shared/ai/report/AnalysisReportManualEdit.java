package pl.mkn.tdw.shared.ai.report;

import java.time.Instant;
import java.util.List;

public record AnalysisReportManualEdit(long revision, Instant editedAt, List<String> changedParts) {
    public AnalysisReportManualEdit {
        changedParts = changedParts != null ? List.copyOf(changedParts) : List.of();
    }
}
