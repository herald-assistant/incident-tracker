package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSchema;
import static org.assertj.core.api.Assertions.*;

class OpenApiSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void projectsWritableArgumentsAndRetainsValidationSemantics() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","required":["id","email","note"],"additionalProperties":false,
                 "properties":{
                   "id":{"type":"string","readOnly":true,"format":"uuid"},
                   "email":{"type":"string","format":"email"},
                   "note":{"type":"string","nullable":true},
                   "items":{"type":"array","items":{"type":"object","required":["generated"],
                     "properties":{"generated":{"type":"integer","readOnly":true},"value":{"type":"string"}}}}
                 },"example":{"id":"example","nullable":true}}
                """);
        OpenApiSchema.check(schema, "body", 0);
        var original = schema.deepCopy(); var tool = OpenApiSchema.toolArguments(schema);
        assertThat(tool.path("properties").has("id")).isFalse();
        assertThat(tool.path("required").toString()).isEqualTo("[\"email\",\"note\"]");
        assertThat(tool.path("properties").path("email").path("format").asText()).isEqualTo("email");
        assertThat(tool.path("properties").path("note").path("type").toString()).isEqualTo("[\"string\",\"null\"]");
        assertThat(tool.path("properties").path("note").has("nullable")).isFalse();
        assertThat(tool.path("properties").path("items").path("items").path("properties").has("generated")).isFalse();
        assertThat(tool.path("example")).isEqualTo(original.path("example"));
        assertThat(schema).isEqualTo(original);
        OpenApiSchema.validate(schema, mapper.readTree("{\"email\":\"tester@example.com\",\"note\":null,\"items\":[{\"value\":\"ok\"}]}"), "body");
        for (var invalid : new String[]{
                "{\"id\":\"generated\",\"email\":\"a\",\"note\":null}",
                "{\"email\":null,\"note\":null}", "{\"email\":\"a\"}",
                "{\"email\":\"a\",\"note\":null,\"items\":[{\"generated\":1}]}"})
            assertThatThrownBy(() -> OpenApiSchema.validate(schema, mapper.readTree(invalid), "body")).isInstanceOf(OpenApiException.class);
    }

    @Test void nullableStillHonorsEnumAndRequiredObjectMembers() throws Exception {
        var nullable = mapper.readTree("{\"type\":\"string\",\"nullable\":true,\"enum\":[\"NEW\"]}");
        assertThatThrownBy(() -> OpenApiSchema.validate(nullable, mapper.nullNode(), "note")).isInstanceOf(OpenApiException.class);
        ((com.fasterxml.jackson.databind.node.ArrayNode) nullable.get("enum")).addNull();
        OpenApiSchema.validate(nullable, mapper.nullNode(), "note");
        var nullableObject = mapper.readTree("{\"type\":\"object\",\"nullable\":true,\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");
        OpenApiSchema.validate(nullableObject, mapper.nullNode(), "body");
        assertThatThrownBy(() -> OpenApiSchema.validate(nullableObject, mapper.createObjectNode(), "body")).isInstanceOf(OpenApiException.class);
    }

    @Test void validatesKeywordTypesAndPreservesOpenEndedFormatAnnotations() throws Exception {
        for (var format : new String[]{"uuid", "date-time", "email", "int64", "vendor-ticket-id"}) {
            var schema = mapper.createObjectNode().put("type", "string").put("format", format);
            OpenApiSchema.check(schema, "value", 0);
            assertThat(OpenApiSchema.toolArguments(schema).path("format").asText()).isEqualTo(format);
            OpenApiSchema.validate(schema, mapper.getNodeFactory().textNode("annotation-only"), "value");
        }
        for (var keyword : new String[]{"readOnly", "nullable", "format"}) {
            var invalid = mapper.createObjectNode().put("type", "string"); invalid.put(keyword, 1);
            assertThatThrownBy(() -> OpenApiSchema.check(invalid, "value", 0)).isInstanceOf(OpenApiException.class).hasMessageContaining(keyword);
        }
    }
}
