package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

public record OpenApiArguments(JsonNode json, @JsonIgnore Map<String, OpenApiFile> files) {
    public OpenApiArguments {
        files = Map.copyOf(files);
    }
    public OpenApiArguments(JsonNode json) { this(json, Map.of()); }
}
