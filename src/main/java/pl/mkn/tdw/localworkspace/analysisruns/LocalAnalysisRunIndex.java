package pl.mkn.tdw.localworkspace.analysisruns;

import java.util.List;

public record LocalAnalysisRunIndex(
        String schema,
        int version,
        List<LocalAnalysisRunIndexEntry> runs
) {
    public static final String SCHEMA = "tdw.local-analysis-run-index";
    public static final int VERSION = 1;

    public LocalAnalysisRunIndex {
        if (schema == null || schema.isBlank()) {
            schema = SCHEMA;
        }
        if (version <= 0) {
            version = VERSION;
        }
        runs = runs == null ? List.of() : List.copyOf(runs);
    }

    public static LocalAnalysisRunIndex empty() {
        return new LocalAnalysisRunIndex(SCHEMA, VERSION, List.of());
    }
}
