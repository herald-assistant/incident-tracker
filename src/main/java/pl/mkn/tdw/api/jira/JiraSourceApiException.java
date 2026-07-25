package pl.mkn.tdw.api.jira;

import org.springframework.http.HttpStatus;

public class JiraSourceApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private JiraSourceApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static JiraSourceApiException badRequest(String message) {
        return new JiraSourceApiException(HttpStatus.BAD_REQUEST, "JIRA_SOURCE_BAD_REQUEST", message);
    }

    public static JiraSourceApiException unavailable(String message) {
        return new JiraSourceApiException(HttpStatus.SERVICE_UNAVAILABLE, "JIRA_SOURCE_UNAVAILABLE", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
