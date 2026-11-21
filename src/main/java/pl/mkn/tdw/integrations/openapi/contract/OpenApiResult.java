package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenApiResult(String outcome, Integer statusCode, JsonNode body, String message) {
    public boolean failed() {
        return !"RESPONSE_RECEIVED".equals(outcome) || statusCode == null || statusCode >= 300;
    }
}
