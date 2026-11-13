package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.common.JsonTextView;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static pl.mkn.tdw.features.uxinspector.capture.UxInspectorHttpNativeBody.*;

/** Sanitizes validated HTTP data; structural binding belongs to UxInspectorCaptureJson. */
@Component
@RequiredArgsConstructor
public class UxInspectorHttpHistorySanitizer {
    private final UxInspectorCaptureJson json;
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|token|secret|authorization|bearer|cookie|session|csrf|jwt|api.?key|one[-_ ]?time|otp|cvv|cvc)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s\"'<>;,}]+");
    private static final Pattern JWT = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}");
    private static final Pattern TEXT_SECRET = Pattern.compile("(?i)(?<![\\w-])((?:[\\w-]*(?:password|passwd|token|secret|authorization|cookie|session|csrf|jwt|api.?key)[\\w-]*)[\"']?\\s*[:=]\\s*)(?:\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'|[^\\s&,;<>}]+)");

    Map<String, List<String>> headers(Map<String, List<String>> source, int[] count) {
        var result = new LinkedHashMap<String, List<String>>();
        source.forEach((key, values) -> {
            if (SECRET.matcher(key).find()) {
                var replacement = List.of("[REDACTED]");
                if (!values.equals(replacement)) count[0]++;
                result.put(key, replacement);
            } else result.put(key, values.stream().map(value ->
                    redact(JsonNodeFactory.instance.textNode(value), count).textValue()).toList());
        });
        return result;
    }

    JsonNode redact(JsonNode node, int[] count) {
        if (node.isObject()) {
            var object = (ObjectNode) node;
            if ("FormData".equals(node.path("$tdwType").asText())) return formData(object, count);
            if (Set.of("File", "Blob").contains(node.path("$tdwType").asText())) return binary(object, count);
            var fields = object.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (SECRET.matcher(field.getKey()).find()) {
                    if (!field.getValue().isTextual() || !"[REDACTED]".equals(field.getValue().textValue())) count[0]++;
                    object.put(field.getKey(), "[REDACTED]");
                } else object.set(field.getKey(), redact(field.getValue(), count));
            }
        } else if (node.isArray()) {
            var array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) array.set(i, redact(array.get(i), count));
        } else if (node.isTextual()) {
            var text = node.textValue();
            var parsed = JsonTextView.parse(text).filter(JsonNode::isContainerNode);
            if (parsed.isPresent()) {
                int before = count[0];
                var safe = redact(parsed.get(), count);
                return JsonNodeFactory.instance.textNode(count[0] > before ? safe.toString() : text);
            }
            var safe = redactText(text);
            if (!safe.equals(text)) count[0]++;
            return JsonNodeFactory.instance.textNode(safe);
        }
        return node;
    }

    private JsonNode formData(ObjectNode node, int[] count) {
        var form = json.read(node, FormData.class);
        var fields = form.entries().stream().map(field -> {
            var value = field.value();
            if (SECRET.matcher(field.name()).find()) {
                if (!value.isTextual() || !"[REDACTED]".equals(value.textValue())) count[0]++;
                value = JsonNodeFactory.instance.textNode("[REDACTED]");
            } else value = redact(value, count);
            var name = redact(JsonNodeFactory.instance.textNode(field.name()), count).textValue();
            return new Field(name, value);
        }).toList();
        return json.tree(new FormData(form.kind(), fields));
    }

    private JsonNode binary(ObjectNode node, int[] count) {
        Binary binary = "File".equals(node.path("$tdwType").asText())
                ? json.read(node, File.class) : json.read(node, Blob.class);
        var content = binary.content();
        if (content.reason() == null) {
            byte[] bytes = content.encoding() == Encoding.UTF8 ? utf8(content.data()) : Base64.getDecoder().decode(content.data());
            if (content.encoding() == Encoding.BASE64 && !Base64.getEncoder().encodeToString(bytes).equals(content.data())
                    || bytes.length != content.byteLength() || !content.redacted() && bytes.length != binary.size()) {
                throw new IllegalArgumentException("Inconsistent binary content");
            }
            // Decode before redaction; Base64 is never processed as plain text.
            try {
                var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                var safe = redact(JsonNodeFactory.instance.textNode(text), count).textValue();
                content = new Content(Encoding.UTF8, safe, (long) utf8(safe).length,
                        content.redacted() || !safe.equals(text), null);
            } catch (CharacterCodingException ignored) {
                // Retain opaque binary bytes exactly, without a lossy text conversion.
            }
        }
        var type = redact(JsonNodeFactory.instance.textNode(binary.type()), count).textValue();
        if (binary instanceof File file) {
            var name = redact(JsonNodeFactory.instance.textNode(file.name()), count).textValue();
            return json.tree(new File(file.kind(), type, file.size(), name, file.lastModified(), content));
        }
        return json.tree(new Blob("Blob", type, binary.size(), content));
    }

    private byte[] utf8(String value) {
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            var bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 content", exception);
        }
    }

    String url(String source, int[] count) {
        var safe = source.replaceFirst("(//)[^/?#]*@", "$1[REDACTED]@");
        // Preserve spelling, order, encoding and all ordinary query values.
        var matcher = Pattern.compile("([?&#])([^=&#]+)=([^&#]*)").matcher(safe);
        var result = new StringBuffer();
        while (matcher.find()) {
            var name = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
            var value = URLDecoder.decode(matcher.group(3), StandardCharsets.UTF_8);
            var replacement = SECRET.matcher(name).find() || !redactText(value).equals(value)
                    ? matcher.group(1) + matcher.group(2) + "=[REDACTED]" : matcher.group();
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        safe = JWT.matcher(BEARER.matcher(result.toString()).replaceAll("[REDACTED]")).replaceAll("[REDACTED]");
        if (!safe.equals(source)) count[0]++;
        return safe;
    }

    private String redactText(String text) {
        return TEXT_SECRET.matcher(JWT.matcher(BEARER.matcher(text).replaceAll("[REDACTED]")).replaceAll("[REDACTED]"))
                .replaceAll("$1\"[REDACTED]\"");
    }

}
