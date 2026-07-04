package pl.mkn.tdw.features.incidentanalysis.job.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisLogInput;
import pl.mkn.tdw.features.incidentanalysis.job.AnalysisJobInputOptionsService;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobLogSource;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStartRequest;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobInputException;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogCsvImportException;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogCsvImportService;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AnalysisJobStartValidationService {

    private final AnalysisJobInputOptionsService inputOptionsService;
    private final ElasticLogCsvImportService elasticLogCsvImportService;

    public AnalysisLogInput validateAndResolveLogInput(AnalysisJobStartRequest request) {
        request.validateForStart();
        ensureStartSourceAvailable(request);
        return resolveLogInput(request);
    }

    private void ensureStartSourceAvailable(AnalysisJobStartRequest request) {
        if (request.source() == AnalysisJobLogSource.ELASTICSEARCH
                && !inputOptionsService.elasticsearchStartEnabled()) {
            throw new AnalysisJobInputException(
                    "ELASTICSEARCH_LOG_SOURCE_NOT_CONFIGURED",
                    inputOptionsService.currentOptions().elasticsearch().disabledReason()
            );
        }
    }

    private AnalysisLogInput resolveLogInput(AnalysisJobStartRequest request) {
        if (!request.csvUpload()) {
            return AnalysisLogInput.elasticsearch(request.correlationId());
        }

        try {
            var importResult = elasticLogCsvImportService.importCsv(request.logFile().getInputStream());
            return AnalysisLogInput.csvUpload(importResult.correlationId(), importResult.entries());
        } catch (IOException exception) {
            throw new AnalysisJobInputException(
                    "INCIDENT_LOG_FILE_INVALID_CSV",
                    "CSV log file could not be read."
            );
        } catch (ElasticLogCsvImportException exception) {
            throw csvImportInputException(exception);
        }
    }

    private AnalysisJobInputException csvImportInputException(ElasticLogCsvImportException exception) {
        return new AnalysisJobInputException(
                switch (exception.reason()) {
                    case MISSING_COLUMNS -> "INCIDENT_LOG_FILE_MISSING_COLUMNS";
                    case EMPTY -> "INCIDENT_LOG_FILE_EMPTY";
                    case MULTIPLE_CORRELATION_IDS -> "INCIDENT_LOG_FILE_MULTIPLE_CORRELATION_IDS";
                    case INVALID_TIMESTAMP -> "INCIDENT_LOG_FILE_INVALID_TIMESTAMP";
                    default -> "INCIDENT_LOG_FILE_INVALID_CSV";
                },
                exception.getMessage()
        );
    }
}
