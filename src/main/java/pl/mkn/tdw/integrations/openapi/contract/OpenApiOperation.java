package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;

public record OpenApiOperation(String name, String description, String method, URI server,
                               String path, List<Parameter> parameters, JsonNode inputSchema,
                               JsonNode validationSchema) {
    public OpenApiOperation {
        parameters = List.copyOf(parameters);
        inputSchema = inputSchema.deepCopy();
        validationSchema = validationSchema.deepCopy();
    }

    @Override
    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    @Override
    public JsonNode validationSchema() {
        return validationSchema.deepCopy();
    }

    public record Parameter(String name, String in) {
    }

    public boolean mutation() {
        return !method.equals("GET");
    }
}
