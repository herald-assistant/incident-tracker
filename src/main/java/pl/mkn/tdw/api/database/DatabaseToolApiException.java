package pl.mkn.tdw.api.database;

import org.springframework.http.HttpStatus;

public class DatabaseToolApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DatabaseToolApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DatabaseToolApiException disabled() {
        return new DatabaseToolApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_TOOLS_DISABLED",
                "Database tools are disabled. Set analysis.database.enabled=true and configure database environments."
        );
    }

    public static DatabaseToolApiException badRequest(String message) {
        return new DatabaseToolApiException(
                HttpStatus.BAD_REQUEST,
                "DATABASE_TOOL_BAD_REQUEST",
                message
        );
    }

    public static DatabaseToolApiException conflict(String message) {
        return new DatabaseToolApiException(
                HttpStatus.CONFLICT,
                "DATABASE_TOOL_UNAVAILABLE",
                message
        );
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
