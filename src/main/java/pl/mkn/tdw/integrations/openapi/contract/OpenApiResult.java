package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record OpenApiResult(String outcome, Integer statusCode, JsonNode body, String message,
                            @JsonIgnore OpenApiFile file) {
    public OpenApiResult(String outcome, Integer statusCode, JsonNode body, String message) {
        this(outcome, statusCode, body, message, null);
    }
    public boolean failed() {
        return !"RESPONSE_RECEIVED".equals(outcome) || statusCode == null || statusCode >= 300;
    }
}
