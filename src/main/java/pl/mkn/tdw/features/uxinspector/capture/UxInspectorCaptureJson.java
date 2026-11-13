package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.deser.std.StdDelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.StdConverter;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Feature-local binding: application-wide Jackson settings are never modified. */
@Component
public final class UxInspectorCaptureJson {
    private final ObjectMapper mapper;
    private final Validator validator;

    public UxInspectorCaptureJson(ObjectMapper objectMapper, Validator validator) {
        this.validator = validator;
        this.mapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                        DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
                        DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                        DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS,
                        DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                        DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS, MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS,
                MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        // JSR-310's InstantDeserializer accepts numeric timestamps even with Shape.STRING/coercion Fail.
        // Delegate token checking to the strict String deserializer, then parse the contract's ISO instant.
        mapper.registerModule(new SimpleModule("ux-inspector-iso-instants").addDeserializer(Instant.class,
                new StdDelegatingDeserializer<>(new StdConverter<String, Instant>() {
                    @Override public Instant convert(String value) { return Instant.parse(value); }
                })));
        mapper.coercionConfigDefaults()
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyArray, CoercionAction.Fail);
        // ALLOW_COERCION_OF_SCALARS does not cover number/boolean -> String conversions.
        var text = mapper.coercionConfigFor(LogicalType.Textual);
        text.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    public <T> T read(JsonNode node, Class<T> type) {
        if (node == null || node.isNull()) throw new IllegalArgumentException("Missing " + type.getSimpleName());
        try {
            return validate(mapper.treeToValue(node, type));
        } catch (JsonProcessingException exception) {
            // Do not include rejected values (possibly secrets) in an API error.
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " structure", exception);
        }
    }

    public <T> T validate(T value) {
        if (value == null) throw new IllegalArgumentException("Missing capture data");
        var violations = validator.validate(value);
        if (!violations.isEmpty()) throw new IllegalArgumentException("Invalid " + value.getClass().getSimpleName()
                + " fields: " + violations.stream().map(v -> v.getPropertyPath().toString()).sorted().toList());
        return value;
    }

    public JsonNode tree(Object value) { return mapper.valueToTree(value); }
}
