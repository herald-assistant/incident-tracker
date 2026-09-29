package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

public class UxInspectorCaptureDeserializer extends JsonDeserializer<UxInspectorCapture> {
    @Override
    public UxInspectorCapture deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        com.fasterxml.jackson.databind.JsonNode node = parser.getCodec().readTree(parser);
        try {
            return UxInspectorCapture.fromJson(node);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(parser, exception.getMessage(), exception);
        }
    }
}
