package pl.mkn.tdw.localworkspace.analysisruns.tools;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.common.JsonTextView;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.common.HttpHistoryTestFixtures.*;

class RunHttpHistoryToolsTest {
    @Test
    void readsNativeMetadataRepeatedFormFieldsAndReassemblesLargeBinaryContentThroughCallback() throws Exception {
        var bytes = new byte[90000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        var form = MAPPER.createObjectNode().put("$tdwType", "FormData");
        var fields = form.putArray("entries");
        fields.addObject().put("name", "tag").put("value", "one");
        fields.addObject().put("name", "tag").put("value", "two");
        fields.addObject().put("name", "attachment").set("value", file(bytes));
        var runs = run(form);
        var tools = new RunHttpHistoryTools(runs, "crm-run");
        assertThat(tools.readValue(1L, "responseBody", "/entries/1/value", null, null, null)).containsEntry("content", "\"two\"");
        assertThat(tools.readValue(1L, "responseBody", "/entries/2/value/name", null, null, null)).containsEntry("content", "\"crm-contacts.bin\"");
        assertThat(tools.readValue(1L, "responseBody", "/entries/2/value/lastModified", null, null, null)).containsEntry("content", "123456789");
        assertThat(tools.listPaths(1L, "responseBody", "/entries/2/value/content", null).get("content").toString())
                .contains("/entries/2/value/content/encoding", "/entries/2/value/content/data");
        var callback = new RunHttpHistoryToolSetFactory(runs).create("crm-run").stream()
                .filter(c -> c.getToolDefinition().name().equals(RunHttpHistoryTools.READ)).findFirst().orElseThrow();
        var joined = new StringBuilder();
        int offset = 0;
        while (true) {
            var raw = callback.call(MAPPER.writeValueAsString(Map.of("entryId", 1, "part", "responseBody",
                    "pointer", "/entries/2/value/content/data", "offset", offset)));
            assertThat(raw.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8000);
            var page = MAPPER.readTree(raw);
            assertThat(page.path("status").asText()).isEqualTo("ok");
            joined.append(page.path("content").asText());
            if (!page.path("hasMore").asBoolean()) break;
            assertThat(page.path("nextOffset").intValue()).isGreaterThan(offset);
            offset = page.path("nextOffset").intValue();
        }
        assertThat(java.util.Base64.getDecoder().decode(MAPPER.readTree(joined.toString()).asText())).isEqualTo(bytes);
    }

    @Test
    void reportsSourceOmissionSeparatelyFromSerializationFailure() {
        for (var reason : new String[]{"SOURCE_OMITTED", "BODY_SERIALIZATION_FAILED"}) {
            var entry = entry(1, MAPPER.nullNode());
            ((com.fasterxml.jackson.databind.node.ObjectNode) entry.at("/response/body")).put("format", "unavailable").put("reason", reason);
            var runs = mock(LocalAnalysisRunStore.class);
            when(runs.findById("crm-run")).thenReturn(Optional.of(LocalAnalysisRunRecord.v1(MAPPER.createObjectNode(), null)
                    .withHttpHistorySnapshot(snapshot(MAPPER.createArrayNode().add(entry)))));
            assertThat(new RunHttpHistoryTools(runs, "crm-run").readValue(1L, "responseBody", "", null, null, null))
                    .containsEntry("status", "unavailable").containsEntry("reason", reason);
        }
    }

    @Test
    void selectsNestedBodyWithoutLargeSiblingsAndDistinguishesMissingNullFalseAndUnavailable() throws Exception {
        var value = MAPPER.readTree("{\"customer\":{\"permissions\":[false,null,{\"a/b~c\":42}]}}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) value).put("notes", "x".repeat(120000));
        var run = run(value);
        var tools = new RunHttpHistoryTools(run, "crm-run");
        assertThat(tools.readValue(1L, "responseBody", "/customer/permissions/0", null, null, null))
                .containsEntry("status", "ok").containsEntry("content", "false");
        assertThat(tools.readValue(1L, "responseBody", "/customer/permissions/1", null, null, null)).containsEntry("content", "null");
        assertThat(tools.readValue(1L, "responseBody", "/customer/permissions/2/a~1b~0c", null, null, null)).containsEntry("content", "42");
        assertThat(tools.readValue(1L, "responseBody", "/missing", null, null, null)).containsEntry("status", "not_found");
        assertThat(tools.readValue(1L, "responseBody", "/~2", null, null, null)).containsEntry("status", "invalid_pointer");
        assertThat(tools.readValue(null, "responseBody", "", null, null, null)).containsEntry("status", "invalid_selection");
        assertThat(tools.readValue(2L, "entry", "", null, null, null)).containsEntry("status", "not_found");
        assertThat(new RunHttpHistoryTools(run, "other-run").readValue(null, null, "", null, null, null)).containsEntry("status", "unavailable");
        assertThat(tools.listPaths(1L, "responseBody", "/customer/permissions/2", null).get("content").toString())
                .contains("/customer/permissions/2/a~1b~0c").doesNotContain("120000");
    }

    @Test
    void readsJsonInTextWithoutRoundingAndPreservesDuplicateKeysAsText() throws Exception {
        var json = "{\"id\":900719925474099312345,\"rate\":1.230000000000001,\"values\":[1,2,3]}";
        var tools = new RunHttpHistoryTools(run(MAPPER.getNodeFactory().textNode(json)), "crm-run");
        assertThat(tools.readValue(1L, "responseBody", "/id", null, null, null)).containsEntry("content", "900719925474099312345");
        assertThat(tools.readValue(1L, "responseBody", "/rate", null, null, null)).containsEntry("content", "1.230000000000001");
        var array = tools.readValue(1L, "responseBody", "/values", null, null, null);
        assertThat(array.get("content").toString()).contains("\n  1,\n  2,\n  3\n");
        var duplicate = "{\"id\":1,\"id\":2}";
        tools = new RunHttpHistoryTools(run(MAPPER.getNodeFactory().textNode(duplicate)), "crm-run");
        assertThat(tools.readValue(1L, "responseBody", "", null, null, null)).containsEntry("format", "text").containsEntry("content", duplicate);
        assertThat(tools.readValue(1L, "responseBody", "/id", null, null, null)).containsEntry("status", "not_json");
    }

    @Test
    void reassemblesHugeSingleLinesUnicodeAndEscapesThroughActualCallbacks() throws Exception {
        for (var value : new String[]{"CRM 🙂漢\"\\\t".repeat(15000), "{\"notes\":\"" + "🙂".repeat(55000) + "\"}"}) {
            var run = run(MAPPER.getNodeFactory().textNode(value));
            var callbacks = new RunHttpHistoryToolSetFactory(run).create("crm-run");
            assertThat(callbacks).hasSize(3);
            var callback = callbacks.stream().filter(c -> c.getToolDefinition().name().equals(RunHttpHistoryTools.READ)).findFirst().orElseThrow();
            assertThat(callback.getToolDefinition().inputSchema()).doesNotContain("runId");
            var all = new StringBuilder();
            int offset = 0;
            while (true) {
                var raw = callback.call(MAPPER.writeValueAsString(Map.of("entryId", 1, "part", "responseBody", "pointer", "", "offset", offset)));
                assertThat(raw.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8000);
                var page = MAPPER.readTree(raw);
                assertThat(page.path("status").asText()).isEqualTo("ok");
                var chunk = page.path("content").asText();
                assertThat(chunk.length()).isLessThanOrEqualTo(4000);
                assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
                assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
                all.append(chunk);
                if (!page.path("hasMore").asBoolean()) break;
                assertThat(page.path("nextOffset").intValue()).isGreaterThan(offset);
                offset = page.path("nextOffset").intValue();
            }
            assertThat(all.toString()).isEqualTo(JsonTextView.parse(value).map(JsonTextView::pretty).orElse(value));
        }
    }

    @Test
    void paginatesFullUrlsAndLongKeysAndHonorsLinesAndEndOffsets() throws Exception {
        var value = MAPPER.createObjectNode().put("k".repeat(12000), "CRM");
        var entry = entry(1, value);
        var url = "https://crm.example.com/contacts?query=" + "a".repeat(18000);
        ((com.fasterxml.jackson.databind.node.ObjectNode) entry.get("request")).put("url", url);
        var runs = mock(LocalAnalysisRunStore.class);
        when(runs.findById("crm-run")).thenReturn(Optional.of(LocalAnalysisRunRecord.v1(MAPPER.createObjectNode(), null)
                .withHttpHistorySnapshot(snapshot(MAPPER.createArrayNode().add(entry)))));
        var tools = new RunHttpHistoryTools(runs, "crm-run");
        var index = collect(offset -> tools.listEntries(offset));
        assertThat(MAPPER.readTree(index).get(0).path("url").asText()).isEqualTo(url);
        assertThat(MAPPER.readTree(collect(offset -> tools.listPaths(1L, "responseBody", "", offset))).get(0).path("pointer").asText()).isEqualTo("/" + "k".repeat(12000));
        var lines = new RunHttpHistoryTools(run(MAPPER.getNodeFactory().textNode("one\ntwo\n🙂end")), "crm-run");
        assertThat(lines.readValue(1L, "responseBody", "", 2, null, 1)).containsEntry("content", "two\n").containsEntry("nextOffset", 8);
        assertThat(lines.readValue(1L, "responseBody", "", null, 9, null)).containsEntry("status", "invalid_offset");
        assertThat(lines.readValue(1L, "responseBody", "", null, 13, null)).containsEntry("hasMore", false).containsEntry("content", "");
        assertThat(lines.readValue(1L, "responseBody", "", 1, 0, null)).containsEntry("status", "invalid_offset");
    }

    private String collect(java.util.function.IntFunction<Map<String, Object>> read) throws Exception {
        var content = new StringBuilder();
        int offset = 0;
        while (true) {
            var page = read.apply(offset);
            assertThat(MAPPER.writeValueAsBytes(page).length).isLessThanOrEqualTo(8000);
            assertThat(page).containsEntry("status", "ok");
            content.append(page.get("content"));
            if (!Boolean.TRUE.equals(page.get("hasMore"))) return content.toString();
            int next = (Integer) page.get("nextOffset");
            assertThat(next).isGreaterThan(offset);
            offset = next;
        }
    }

    private LocalAnalysisRunStore run(com.fasterxml.jackson.databind.JsonNode value) {
        var runs = mock(LocalAnalysisRunStore.class);
        when(runs.findById("crm-run")).thenReturn(Optional.of(LocalAnalysisRunRecord.v1(MAPPER.createObjectNode(), null)
                .withHttpHistorySnapshot(snapshot(MAPPER.createArrayNode().add(entry(1, value))))));
        return runs;
    }
}
