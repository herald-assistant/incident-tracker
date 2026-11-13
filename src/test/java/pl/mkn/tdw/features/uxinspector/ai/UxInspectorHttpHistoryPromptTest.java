package pl.mkn.tdw.features.uxinspector.ai;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.common.HttpHistoryTestFixtures.*;

class UxInspectorHttpHistoryPromptTest {
    @Test
    void explainsNativeContentPathsEncodingAndDistinctMissingDataReasons() {
        assertThat(UxInspectorHttpHistoryPrompt.guidance()).contains("$tdwType", "/entries/2/value/content/data",
                "Base64", "lastModified", "SOURCE_OMITTED", "BODY_SERIALIZATION_FAILED", "BINARY_READ_FAILED",
                "HttpDebugSnapshotUtils", "powtorzone nazwy");
    }
    @Test
    void includesAllFiftyEntriesAndEntireEscapedUrlsWithoutBodiesOrHeaders() {
        var entries = MAPPER.createArrayNode();
        for (int i = 0; i < 50; i++) {
            var entry = entry(100 + i, MAPPER.createObjectNode().put("notes", "PRIVATE_CRM_BODY"));
            ((ObjectNode) entry.get("request")).put("url", "https://crm.example.com/api/contacts/" + i + "?query=" + "a".repeat(10000) + "&mode=<all>|`\n");
            entries.add(entry);
        }
        var prompt = UxInspectorHttpHistoryPrompt.manifest(snapshot(entries));
        assertThat(prompt.lines().filter(line -> line.startsWith("| ")).count()).isEqualTo(52);
        assertThat(prompt).contains("| 0 | 100 | GET |", "| 49 | 149 | GET |", "entryCount=50", "success / 200",
                "a".repeat(10000) + "&amp;mode=&lt;all&gt;&#124;&#96;&#10;");
        assertThat(prompt).doesNotContain("PRIVATE_CRM_BODY", "Accept", "application/json");
        assertThat(UxInspectorHttpHistoryPrompt.manifest(snapshot(MAPPER.createArrayNode())))
                .contains("AVAILABLE", "0 wpisow").doesNotContain("| index");
        assertThat(UxInspectorHttpHistoryPrompt.manifest(null)).startsWith("UNAVAILABLE").doesNotContain("| index");
    }
}
