package pl.mkn.incidenttracker.analysis.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalysisEvidenceModelTest {

    @Test
    void shouldNormalizeNullEvidenceListsToEmptyLists() {
        var request = new AnalysisAiAnalysisRequest("corr-123", null, null, null, null);
        var section = new AnalysisEvidenceSection("x", "y", null);
        var item = new AnalysisEvidenceItem("title", null);

        assertEquals(0, request.evidenceSections().size());
        assertEquals(AnalysisAiOptions.DEFAULT, request.options());
        assertEquals(0, section.items().size());
        assertEquals(0, item.attributes().size());
        assertFalse(section.hasItems());
    }

    @Test
    void shouldNormalizeAiOptions() {
        var options = new AnalysisAiOptions(" gpt-5.4 ", " high ");

        assertEquals("gpt-5.4", options.model());
        assertEquals("high", options.reasoningEffort());
        assertNull(new AnalysisAiOptions(" ", "").model());
        assertNull(new AnalysisAiOptions(" ", "").reasoningEffort());
    }

    @Test
    void shouldKeepStringNullsUnchanged() {
        var section = new AnalysisEvidenceSection(null, null, null);
        var item = new AnalysisEvidenceItem(null, null);
        var attribute = new AnalysisEvidenceAttribute(null, null);

        assertNull(section.provider());
        assertNull(section.category());
        assertNull(item.title());
        assertNull(attribute.name());
        assertNull(attribute.value());
    }
}
