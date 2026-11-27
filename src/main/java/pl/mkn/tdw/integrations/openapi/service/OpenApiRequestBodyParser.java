package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiFile;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiUpload;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiUpload.Kind;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiUpload.Part;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static pl.mkn.tdw.integrations.openapi.service.OpenApiImporter.fields;
import static pl.mkn.tdw.integrations.openapi.service.OpenApiSchema.fail;

final class OpenApiRequestBodyParser {
    private static final String OCTETS = "application/octet-stream";
    private static final String MULTIPART = "multipart/form-data";
    private static final String JSON = "application/json";

    record Parsed(JsonNode schema, OpenApiUpload upload) {}

    static Parsed parse(ObjectMapper mapper, OpenApiSchemaResolver resolver, String version, JsonNode content, String path) {
        if (!content.isObject()) fail(path, "Wymagany obiekt content.");
        if (content.has(JSON)) {
            fields(content, path, JSON);
            var media = content.get(JSON);
            fields(media, path + "/" + JSON, "schema", "example");
            return new Parsed(resolver.request(media.path("schema"), path + "/" + JSON + "/schema"), null);
        }
        boolean version31 = version.startsWith("3.1.");
        if (content.has(MULTIPART)) {
            fields(content, path, MULTIPART);
            return multipart(mapper, resolver, version31, content.get(MULTIPART), path + "/" + MULTIPART);
        }
        var mediaTypes = new ArrayList<String>();
        var entries = content.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            var mime = entry.getKey();
            if (mime.startsWith("x-")) continue;
            var pointer = path + "/" + mime;
            if (!fileMime(mime)) fail(pointer, "Nieobsługiwany typ request body.");
            var media = entry.getValue();
            fields(media, pointer, "schema", "example");
            if (!version31 || media.has("schema")) {
                var schema = resolver.request(media.path("schema"), pointer + "/schema");
                requireFileSchema(schema, version31, pointer + "/schema");
            }
            mediaTypes.add(mime);
        }
        if (mediaTypes.isEmpty()) fail(path, "Wymagany typ request body.");
        return new Parsed(reference(mapper, mapper.createObjectNode()), new OpenApiUpload(mediaTypes, Map.of()));
    }

    private static Parsed multipart(ObjectMapper mapper, OpenApiSchemaResolver resolver, boolean version31,
                                    JsonNode media, String path) {
        fields(media, path, "schema", "example", "encoding");
        var schema = resolver.request(media.path("schema"), path + "/schema");
        if (!schema.path("type").asText().equals("object") || !schema.path("properties").isObject()
                || schema.path("properties").isEmpty()) fail(path, "Multipart wymaga obiektu z nazwanymi częściami.");
        fields(schema, path + "/schema", "type", "description", "title", "properties", "required", "additionalProperties",
                "readOnly", "writeOnly", "deprecated", "example", "examples");
        var additional = schema.get("additionalProperties");
        if (additional != null && !(additional.isBoolean() && !additional.booleanValue()))
            fail(path, "Multipart obsługuje jawnie nazwane części. Otwarte mapy są obsługiwane wewnątrz części JSON.");
        var arguments = (ObjectNode) schema.deepCopy();
        arguments.put("additionalProperties", false);
        var properties = arguments.withObject("properties");
        var encoding = media.path("encoding");
        if (media.has("encoding") && !encoding.isObject()) fail(path + "/encoding", "Wymagany obiekt encoding.");
        encoding.fieldNames().forEachRemaining(name -> {
            if (!properties.has(name)) fail(path + "/encoding/" + name, "Brak takiej części multipart.");
        });
        var parts = new LinkedHashMap<String, Part>();
        schema.path("properties").fields().forEachRemaining(entry -> {
            var name = entry.getKey();
            var pointer = path + "/schema/properties/" + name;
            if (name.isBlank() || name.chars().anyMatch(Character::isISOControl) || name.contains("\"") || name.contains("\\"))
                fail(pointer, "Nieprawidłowa nazwa części multipart.");
            var part = entry.getValue();
            Kind kind;
            if (isBinary(part, version31)) {
                requireFileSchema(part, version31, pointer);
                kind = Kind.FILE;
                properties.set(name, reference(mapper, part));
            } else if (part.path("type").asText().equals("array") && isBinary(part.path("items"), version31)) {
                requireFileSchema(part.get("items"), version31, pointer + "/items");
                fields(part, pointer, "type", "items", "description", "title", "readOnly", "writeOnly",
                        "deprecated", "minItems", "maxItems", "uniqueItems", "example", "examples");
                var array = (ObjectNode) part.deepCopy();
                array.set("items", reference(mapper, part.get("items")));
                array.put("minItems", Math.max(1, part.path("minItems").asLong()));
                properties.set(name, array);
                kind = Kind.FILE_ARRAY;
            } else kind = metadataKind(part, pointer);
            parts.put(name, encoding(kind, encoding.path(name), path + "/encoding/" + name));
        });
        if (parts.values().stream().noneMatch(part -> part.kind() == Kind.FILE || part.kind() == Kind.FILE_ARRAY))
            fail(path, "Wymagana co najmniej jedna część plikowa.");
        return new Parsed(arguments, new OpenApiUpload(List.of(), parts));
    }

    private static boolean fileMime(String mime) {
        return OCTETS.equals(mime) || OpenApiFile.EXTENSIONS.containsKey(mime);
    }

    private static Kind metadataKind(JsonNode schema, String pointer) {
        if (schema.has("oneOf") || schema.has("anyOf") || schema.has("allOf"))
            fail(pointer, "Część multipart wymaga jednoznacznego sposobu kodowania.");
        return switch (schema.path("type").asText()) {
            case "object" -> Kind.JSON;
            case "string", "boolean", "integer", "number" -> Kind.TEXT;
            default -> throw new OpenApiException(pointer + ": Nieobsługiwany typ części multipart.");
        };
    }

    private static Part encoding(Kind kind, JsonNode settings, String pointer) {
        fields(settings, pointer, "contentType");
        var defaultType = switch (kind) {
            case FILE, FILE_ARRAY -> OCTETS;
            case JSON -> JSON;
            case TEXT -> "text/plain";
        };
        var mime = settings.has("contentType") ? settings.get("contentType").asText("") : defaultType;
        if (!mime.equals(defaultType) && !(defaultType.equals(OCTETS) && fileMime(mime)))
            fail(pointer + "/contentType", "Nieobsługiwany Content-Type tej części.");
        return new Part(kind, mime);
    }

    private static boolean isBinary(JsonNode schema, boolean version31) {
        return (schema.isObject() && schema.isEmpty() && version31)
                || (schema.path("type").asText().equals("string") && schema.path("format").asText().equals("binary"));
    }

    private static void requireFileSchema(JsonNode schema, boolean version31, String pointer) {
        if (!isBinary(schema, version31)) fail(pointer, "Wymagany schemat pliku binarnego.");
        fields(schema, pointer, "type", "format", "title", "description", "readOnly", "writeOnly", "deprecated", "example", "examples");
    }

    private static ObjectNode reference(ObjectMapper mapper, JsonNode source) {
        var result = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        for (var key : Set.of("title", "description", "readOnly", "writeOnly")) {
            if (source.has(key)) result.set(key, source.get(key));
        }
        result.putArray("required").add("attachmentId");
        result.putObject("properties").putObject("attachmentId").put("type", "string")
                .put("description", "ID oryginalnego pliku z katalogu załączników tej tury.");
        return result;
    }
}
