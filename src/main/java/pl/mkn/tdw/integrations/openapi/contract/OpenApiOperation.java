package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record OpenApiOperation(String name, String description, String method, URI server,
                               String path, List<Parameter> parameters, JsonNode inputSchema,
                               JsonNode validationSchema, String effect, Map<String, List<String>> responseContentTypes) {
    public OpenApiOperation {
        parameters = List.copyOf(parameters);
        inputSchema = inputSchema.deepCopy();
        validationSchema = validationSchema.deepCopy();
        var contentTypes = new LinkedHashMap<String, List<String>>();
        responseContentTypes.forEach((status, types) -> contentTypes.put(status, List.copyOf(types)));
        responseContentTypes = Collections.unmodifiableMap(contentTypes);
    }

    @Override
    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    @Override
    public JsonNode validationSchema() {
        return validationSchema.deepCopy();
    }

    public record Parameter(String name, String in, boolean explode, boolean catchAll, boolean queryObject) {
    }

    public boolean mutation() {
        return !"READ_ONLY".equals(effect);
    }
}
