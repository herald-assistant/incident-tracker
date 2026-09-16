package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class UxInspectorCaptureDeserializer extends JsonDeserializer<UxInspectorCapture> {
    @Override
    public UxInspectorCapture deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        com.fasterxml.jackson.databind.JsonNode node = parser.getCodec().readTree(parser);
        if (node.toString().getBytes(StandardCharsets.UTF_8).length > UxInspectorCapture.MAX_BYTES) {
            throw JsonMappingException.from(parser, "UX Inspector capture exceeds the 128 KiB pre-normalization limit");
        }
        try {
            return UxInspectorCapture.fromJson(node);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(parser, exception.getMessage(), exception);
        }
    }
}
