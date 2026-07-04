package pl.mkn.tdw.features.incidentanalysis.job.api;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobInputException;

import java.util.Locale;

public enum AnalysisJobLogSource {
    ELASTICSEARCH,
    CSV_UPLOAD;

    public static AnalysisJobLogSource parse(String value) {
        if (!StringUtils.hasText(value)) {
            throw new AnalysisJobInputException(
                    "INCIDENT_LOG_SOURCE_REQUIRED",
                    "Log source must be provided. Allowed values: ELASTICSEARCH, CSV_UPLOAD."
            );
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AnalysisJobInputException(
                    "INCIDENT_LOG_SOURCE_REQUIRED",
                    "Unsupported log source. Allowed values: ELASTICSEARCH, CSV_UPLOAD."
            );
        }
    }
}
