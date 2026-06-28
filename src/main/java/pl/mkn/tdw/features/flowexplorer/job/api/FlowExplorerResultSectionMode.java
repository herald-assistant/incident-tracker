package pl.mkn.tdw.features.flowexplorer.job.api;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum FlowExplorerResultSectionMode {
    OFF,
    COMPACT,
    DEEP;

    @JsonCreator
    public static FlowExplorerResultSectionMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return FlowExplorerResultSectionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
