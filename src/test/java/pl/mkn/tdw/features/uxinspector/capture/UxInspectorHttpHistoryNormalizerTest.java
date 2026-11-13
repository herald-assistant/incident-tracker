package pl.mkn.tdw.features.uxinspector.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.common.HttpHistoryTestFixtures.*;

class UxInspectorHttpHistoryNormalizerTest {
    private final UxInspectorHttpHistoryNormalizer normalizer = UxInspectorCaptureTestSupport.history(MAPPER);

    @ParameterizedTest(name = "rejects HTTP {0} = {1}")
    @MethodSource("malformedValues")
    void rejectsMalformedTypedHistoryWithoutCoercion(String pointer, String value) throws Exception {
        var entry = entry(1, MAPPER.nullNode());
        int separator = pointer.lastIndexOf('/');
        ((ObjectNode) entry.at(pointer.substring(0, separator))).set(pointer.substring(separator + 1), MAPPER.readTree(value));
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").reason()).isEqualTo("INVALID_HISTORY");
    }

    static java.util.stream.Stream<Arguments> malformedValues() {
        return java.util.stream.Stream.of(
                Arguments.of("/id", "\"1\""), Arguments.of("/id", "0"), Arguments.of("/id", "1.1"),
                Arguments.of("/id", "9007199254740992"), Arguments.of("/id", "9223372036854775808"),
                Arguments.of("/state", "0"), Arguments.of("/state", "\"SUCCESS\""),
                Arguments.of("/startedAt", "1794480000"), Arguments.of("/durationMs", "-1"),
                Arguments.of("/durationMs", "\"0\""), Arguments.of("/durationMs", "1e999"),
                Arguments.of("/request/method", "42"), Arguments.of("/request/url", "null"),
                Arguments.of("/request/headers", "{\"Accept\":[42]}"),
                Arguments.of("/request/headers", "{\"Accept\":\"application/json\"}"),
                Arguments.of("/request/headers", "{\"Accept\":[null]}"),
                Arguments.of("/response/status", "\"200\""), Arguments.of("/response/status", "200.1"),
                Arguments.of("/response/status", "null"), Arguments.of("/response/status", "600"),
                Arguments.of("/response/body/format", "0"), Arguments.of("/response/body/format", "null"),
                Arguments.of("/response/body", "{\"format\":\"text\",\"value\":42,\"reason\":null}"),
                Arguments.of("/response/body", "{\"format\":\"unavailable\",\"value\":null,\"reason\":null}"));
    }

    @Test
    void requiresNullablePropertiesButAllowsNullValuesAndArbitraryJsonBody() {
        var entry = entry(1, MAPPER.createObjectNode().put("value", "42").put("enabled", false).putNull("extra"));
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isTrue();
        entry.remove("durationMs");
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isFalse();
        entry.putNull("durationMs");
        ((ObjectNode) entry.at("/response/body")).remove("reason");
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isFalse();
        ((ObjectNode) entry.at("/response/body")).putNull("reason");
        var result = normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm");
        assertThat(result.available()).isTrue();
        assertThat(result.entries().get(0).at("/response/body/value/value").textValue()).isEqualTo("42");
        assertThat(result.entries().get(0).at("/response/body/value/enabled").booleanValue()).isFalse();
        assertThat(result.entries().get(0).at("/response/body/value/extra").isNull()).isTrue();
    }

    @Test
    void validatesNativeMetadataAndNullableContentPropertiesWithoutCoercion() {
        for (var field : java.util.List.of("size", "lastModified")) {
            var file = file(new byte[]{-1}); file.put(field, "1");
            assertThat(normalizeValue(file).available()).isFalse();
        }
        var file = file(new byte[]{-1});
        ((ObjectNode) file.path("content")).put("redacted", "false");
        assertThat(normalizeValue(file).available()).isFalse();
        ((ObjectNode) file.path("content")).put("redacted", false).remove("reason");
        assertThat(normalizeValue(file).available()).isFalse();
        ((ObjectNode) file.path("content")).putNull("reason");
        assertThat(normalizeValue(file).available()).isTrue();
    }

    @Test
    void retainsNestedFilesBlobsRepeatedFormFieldsAndAllBinaryBytes() throws Exception {
        var bytes = new byte[65536];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        var file = file(bytes);
        var blob = file.deepCopy().put("$tdwType", "Blob"); blob.remove(java.util.List.of("name", "lastModified"));
        var form = MAPPER.createObjectNode().put("$tdwType", "FormData");
        var fields = form.putArray("entries");
        fields.addObject().put("name", "tag").put("value", "one");
        fields.addObject().put("name", "tag").put("value", "two");
        fields.addObject().put("name", "document").set("value", file);
        var payload = MAPPER.createObjectNode().set("upload", form);
        ((ObjectNode) payload).set("preview", blob);
        var result = normalizedBody(payload);
        assertThat(result.at("/upload/entries/0/value").asText()).isEqualTo("one");
        assertThat(result.at("/upload/entries/1/value").asText()).isEqualTo("two");
        assertThat(MAPPER.readTree(result.at("/upload/entries/2/value").toString())).isEqualTo(file);
        assertThat(MAPPER.readTree(result.get("preview").toString())).isEqualTo(blob);
        assertThat(java.util.Base64.getDecoder().decode(result.at("/preview/content/data").asText())).isEqualTo(bytes);
    }

    @Test
    void redactsFormFieldNamesAndEncodedTextWithoutLosingMetadataPrecisionOrBom() {
        var text = "{\"token\":\"CRM_SECRET\",\"id\":900719925474099312345,\"rate\":1.230000000000001}";
        var file = file(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = normalizedBody(file);
        assertThat(result.path("size").asInt()).isEqualTo(file.path("size").asInt());
        assertThat(result.path("name").asText()).isEqualTo("crm-contacts.bin");
        assertThat(result.at("/content/encoding").asText()).isEqualTo("utf-8");
        assertThat(result.at("/content/redacted").booleanValue()).isTrue();
        assertThat(result.at("/content/data").asText()).doesNotContain("CRM_SECRET")
                .contains("900719925474099312345", "1.230000000000001", "[REDACTED]");
        var form = MAPPER.createObjectNode().put("$tdwType", "FormData");
        form.putArray("entries").addObject().put("name", "accessToken").put("value", "CRM_FORM_SECRET");
        assertThat(normalizedBody(form).at("/entries/0/value").asText()).isEqualTo("[REDACTED]");
        var bom = "\uFEFFCRM 🙂\r\n\u0000";
        var bomFile = file(bom.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(normalizedBody(bomFile).at("/content/data").asText()).isEqualTo(bom);
        assertThat(normalizedBody(file(new byte[0])).at("/content/data").asText()).isEmpty();
    }

    @Test
    void rejectsMalformedNativeRepresentationsAndLengthMismatches() {
        var valid = file(new byte[]{-1, -2, 0, 65});
        for (var field : java.util.List.of("encoding", "data", "byteLength", "redacted", "reason")) {
            var broken = valid.deepCopy(); ((ObjectNode) broken.path("content")).remove(field);
            assertThat(normalizeValue(broken).reason()).isEqualTo("INVALID_HISTORY");
        }
        var invalidBase64 = valid.deepCopy(); ((ObjectNode) invalidBase64.path("content")).put("data", "%%%CRM");
        assertThat(normalizeValue(invalidBase64).reason()).isEqualTo("INVALID_HISTORY");
        var wrongLength = valid.deepCopy(); ((ObjectNode) wrongLength.path("content")).put("byteLength", 0);
        assertThat(normalizeValue(wrongLength).reason()).isEqualTo("INVALID_HISTORY");
        assertThat(normalizeValue(valid.deepCopy().put("size", 1)).reason()).isEqualTo("INVALID_HISTORY");
        assertThat(normalizeValue(valid.deepCopy().put("lastModified", 1.5)).reason()).isEqualTo("INVALID_HISTORY");
        var malformedForm = MAPPER.createObjectNode().put("$tdwType", "FormData");
        malformedForm.putArray("entries").addObject().put("name", "contact").put("value", 42);
        assertThat(normalizeValue(malformedForm).reason()).isEqualTo("INVALID_HISTORY");
    }

    @Test
    void retainsSourceOmissionAndUnreadableFileMetadataAsExplicitDistinctReasons() throws Exception {
        var entry = entry(1, MAPPER.nullNode());
        ((ObjectNode) entry.at("/response/body")).put("format", "unavailable").put("reason", "SOURCE_OMITTED");
        var result = normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm");
        assertThat(result.available()).isTrue();
        assertThat(result.entries().get(0).at("/response/body/reason").asText()).isEqualTo("SOURCE_OMITTED");
        var failed = file(new byte[10]);
        failed.putObject("content").putNull("encoding").putNull("data").putNull("byteLength")
                .put("redacted", false).put("reason", "BINARY_READ_FAILED");
        assertThat(MAPPER.readTree(normalizedBody(failed).toString())).isEqualTo(failed);
        ((ObjectNode) entry.at("/response/body")).put("reason", "arbitrary");
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isFalse();
    }

    private pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunHttpHistorySnapshot normalizeValue(com.fasterxml.jackson.databind.JsonNode value) {
        return normalizer.normalize(block(MAPPER.createArrayNode().add(entry(1, value))), "https://crm.example.com");
    }

    private com.fasterxml.jackson.databind.JsonNode normalizedBody(com.fasterxml.jackson.databind.JsonNode value) {
        var result = normalizeValue(value);
        assertThat(result.available()).isTrue();
        return result.entries().get(0).at("/response/body/value");
    }

    @Test
    void retainsAllFiftyFullEntriesAndDistinguishesEmptyUnavailableAndInvalid() {
        var entries = MAPPER.createArrayNode();
        var longUrl = "https://crm.example.com/api/contacts?view=" + "active".repeat(2000);
        for (int i = 1; i <= 50; i++) {
            var entry = entry(i, MAPPER.createObjectNode().put("notes", "CRM".repeat(40000)));
            ((ObjectNode) entry.get("request")).put("url", longUrl);
            entries.add(entry);
        }
        var result = normalizer.normalize(block(entries), "https://crm.example.com");
        assertThat(result.available()).isTrue();
        assertThat(result.entries()).hasSize(50);
        assertThat(result.entries().get(49).path("request").path("url").asText()).isEqualTo(longUrl);
        assertThat(result.entries().get(0).path("response").path("body").path("value").path("notes").asText()).hasSize(120000);
        ((ObjectNode) entries.get(0)).put("id", 999);
        assertThat(result.entries().get(0).path("id").longValue()).isEqualTo(1);
        entries.add(entry(51, MAPPER.nullNode()));
        assertThat(normalizer.normalize(block(entries), "crm").reason()).isEqualTo("INVALID_HISTORY");
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode()), "crm").available()).isTrue();
        assertThat(normalizer.normalize(null, "crm").available()).isFalse();
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry(1, MAPPER.nullNode()))
                .add(entry(1, MAPPER.nullNode()))), "crm").reason()).isEqualTo("INVALID_HISTORY");
    }

    @Test
    void redactsSecretsButKeepsFullOrdinaryQueryValuesAndJsonTextPrecision() {
        var value = MAPPER.getNodeFactory().textNode("{\"id\":900719925474099312345,\"rate\":1.230000000000001,\"token\":\"CRM_SECRET\",\"memo\":\"password=CRM_PLAIN\"}");
        var entry = entry(1, value);
        ((ObjectNode) entry.get("request")).put("url", "https://user:CRM_PASS@crm.example.com/api?view=all%20contacts&token=CRM_TOKEN&sort=name");
        ((ObjectNode) entry.path("request").path("headers")).putArray("Authorization").add("Bearer CRM_AUTH");
        var result = normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "https://crm.example.com");
        assertThat(result.available()).isTrue();
        assertThat(result.entries().toString()).doesNotContain("CRM_SECRET", "CRM_PASS", "CRM_TOKEN", "CRM_AUTH", "CRM_PLAIN")
                .contains("view=all%20contacts", "sort=name", "900719925474099312345", "1.230000000000001");
        assertThat(result.redactions()).isGreaterThan(0);
        assertThat(entry.toString()).contains("CRM_SECRET");
    }

    @Test
    void preservesPendingNullCancelledStatusZeroAndRejectsMalformedMessages() {
        var entry = entry(1, MAPPER.nullNode());
        entry.put("state", "pending").putNull("durationMs").putNull("response");
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isTrue();
        entry.put("state", "cancelled");
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isTrue();
        var error = entry(2, MAPPER.nullNode()).put("state", "error");
        ((ObjectNode) error.path("response")).put("status", 0);
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(error)), "crm").entries().get(0).path("response").path("status").intValue()).isZero();
        ((ObjectNode) error.path("response")).put("status", 4294967296L);
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(error)), "crm").available()).isFalse();
        entry.put("unexpected", true);
        assertThat(normalizer.normalize(block(MAPPER.createArrayNode().add(entry)), "crm").available()).isFalse();
    }
}
