package pl.mkn.tdw.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Optional;

/** Lossless structural interpretation of JSON text, with deterministic line rendering. */
public final class JsonTextView {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private JsonTextView() {}

    public static Optional<JsonNode> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        try { return Optional.ofNullable(MAPPER.readTree(text)); }
        catch (JsonProcessingException exception) { return Optional.empty(); }
    }

    public static String pretty(JsonNode value) {
        var printer = new DefaultPrettyPrinter();
        var indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        try { return MAPPER.writer(printer).writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Cannot render JSON value", exception); }
    }
}
