package pl.mkn.tdw.features.incidentanalysis.job.api;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobInputException;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public record AnalysisJobStartRequest(
        AnalysisJobLogSource source,
        String correlationId,
        MultipartFile logFile,
        String model,
        String reasoningEffort
) {

    public AnalysisJobStartRequest {
        correlationId = StringUtils.hasText(correlationId) ? correlationId.trim() : correlationId;
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    public AnalysisJobStartRequest(String correlationId, String model, String reasoningEffort) {
        this(AnalysisJobLogSource.ELASTICSEARCH, correlationId, null, model, reasoningEffort);
    }

    public static AnalysisJobStartRequest fromMultipart(
            String source,
            String correlationId,
            MultipartFile logFile,
            String model,
            String reasoningEffort
    ) {
        return new AnalysisJobStartRequest(
                AnalysisJobLogSource.parse(source),
                correlationId,
                logFile,
                model,
                reasoningEffort
        );
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    public void validateForStart() {
        validateTextLength(model, 80, "model");
        validateTextLength(reasoningEffort, 40, "reasoningEffort");

        if (source == null) {
            throw new AnalysisJobInputException(
                    "INCIDENT_LOG_SOURCE_REQUIRED",
                    "Log source must be provided. Allowed values: ELASTICSEARCH, CSV_UPLOAD."
            );
        }

        switch (source) {
            case ELASTICSEARCH -> validateElasticsearchInput();
            case CSV_UPLOAD -> validateCsvUploadInput();
        }
    }

    public boolean csvUpload() {
        return source == AnalysisJobLogSource.CSV_UPLOAD;
    }

    private void validateElasticsearchInput() {
        if (!StringUtils.hasText(correlationId)) {
            throw new AnalysisJobInputException(
                    "INCIDENT_LOG_SOURCE_REQUIRED",
                    "correlationId must be provided when source is ELASTICSEARCH."
            );
        }
    }

    private void validateCsvUploadInput() {
        if (logFile == null || logFile.isEmpty()) {
            throw new AnalysisJobInputException(
                    "INCIDENT_LOG_FILE_MISSING",
                    "CSV log file must be attached when source is CSV_UPLOAD."
            );
        }
    }

    private void validateTextLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new AnalysisJobInputException(
                    "VALIDATION_ERROR",
                    fieldName + " must not exceed " + maxLength + " characters."
            );
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
