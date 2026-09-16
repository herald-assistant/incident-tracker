package pl.mkn.tdw.features.uxinspector.job.api;

import java.util.List;

public record UxInspectorOutputAvailability(String status, String code, String message,
                                            List<String> missingCapabilities) {
    public UxInspectorOutputAvailability {
        missingCapabilities = missingCapabilities != null ? List.copyOf(missingCapabilities) : List.of();
    }
}

