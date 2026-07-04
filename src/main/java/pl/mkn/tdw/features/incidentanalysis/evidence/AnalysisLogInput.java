package pl.mkn.tdw.features.incidentanalysis.evidence;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogEntry;

import java.util.List;

public record AnalysisLogInput(
        Source source,
        String correlationId,
        List<ElasticLogEntry> uploadedLogEntries
) {

    public AnalysisLogInput {
        if (source == null) {
            throw new IllegalArgumentException("Log source must not be null.");
        }
        if (!StringUtils.hasText(correlationId)) {
            throw new IllegalArgumentException("correlationId must not be blank.");
        }
        correlationId = correlationId.trim();
        uploadedLogEntries = uploadedLogEntries != null ? List.copyOf(uploadedLogEntries) : List.of();
        if (source == Source.CSV_UPLOAD && uploadedLogEntries.isEmpty()) {
            throw new IllegalArgumentException("CSV upload log input must contain at least one log entry.");
        }
    }

    public static AnalysisLogInput elasticsearch(String correlationId) {
        return new AnalysisLogInput(Source.ELASTICSEARCH, correlationId, List.of());
    }

    public static AnalysisLogInput csvUpload(String correlationId, List<ElasticLogEntry> entries) {
        return new AnalysisLogInput(Source.CSV_UPLOAD, correlationId, entries);
    }

    public boolean csvUpload() {
        return source == Source.CSV_UPLOAD;
    }

    public enum Source {
        ELASTICSEARCH,
        CSV_UPLOAD
    }
}
