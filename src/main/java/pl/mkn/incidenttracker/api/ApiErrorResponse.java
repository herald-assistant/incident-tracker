package pl.mkn.incidenttracker.api;

import java.util.List;

public record ApiErrorResponse(String code, String message, List<ApiFieldError> fieldErrors, String authStartUrl) {

    public ApiErrorResponse(String code, String message, List<ApiFieldError> fieldErrors) {
        this(code, message, fieldErrors, null);
    }
}
