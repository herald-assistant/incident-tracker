package pl.mkn.tdw.integrations.elasticsearch;

import java.util.List;

public class ElasticLogCsvImportException extends RuntimeException {

    private final Reason reason;
    private final List<String> details;

    public ElasticLogCsvImportException(Reason reason, String message) {
        this(reason, message, List.of(), null);
    }

    public ElasticLogCsvImportException(Reason reason, String message, List<String> details) {
        this(reason, message, details, null);
    }

    public ElasticLogCsvImportException(Reason reason, String message, Throwable cause) {
        this(reason, message, List.of(), cause);
    }

    public ElasticLogCsvImportException(
            Reason reason,
            String message,
            List<String> details,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = reason;
        this.details = details != null ? List.copyOf(details) : List.of();
    }

    public Reason reason() {
        return reason;
    }

    public List<String> details() {
        return details;
    }

    public enum Reason {
        INVALID_CSV,
        MISSING_HEADER,
        MISSING_COLUMNS,
        EMPTY,
        MISSING_CORRELATION_ID,
        MULTIPLE_CORRELATION_IDS,
        INVALID_TIMESTAMP
    }
}
