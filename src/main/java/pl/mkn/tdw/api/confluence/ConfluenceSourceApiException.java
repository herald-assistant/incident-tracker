package pl.mkn.tdw.api.confluence;

import org.springframework.http.HttpStatus;

public class ConfluenceSourceApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ConfluenceSourceApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ConfluenceSourceApiException badRequest(String message) {
        return new ConfluenceSourceApiException(
                HttpStatus.BAD_REQUEST,
                "CONFLUENCE_SOURCE_BAD_REQUEST",
                message
        );
    }

    public static ConfluenceSourceApiException unavailable(String message) {
        return new ConfluenceSourceApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CONFLUENCE_SOURCE_UNAVAILABLE",
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
