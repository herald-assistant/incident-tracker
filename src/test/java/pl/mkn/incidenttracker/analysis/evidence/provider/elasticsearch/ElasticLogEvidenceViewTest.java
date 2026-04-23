package pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticLogEvidenceViewTest {

    @Test
    void shouldRenderReadableMarkdownFromCollectedElasticEvidence() {
        var provider = new ElasticLogEvidenceProvider(new TestElasticLogPort());
        var section = provider.collect(AnalysisContext.initialize("db-lock-123"));

        assertEquals("elasticsearch", section.provider());
        assertEquals("logs", section.category());
        assertEquals(2, section.items().size());

        var evidenceView = ElasticLogEvidenceView.from(section);
        assertEquals(2, evidenceView.entries().size());
        assertEquals("ERROR", evidenceView.entries().get(0).level());
        assertEquals("svc", evidenceView.entries().get(0).serviceName());

        var markdown = evidenceView.toMarkdown();
        assertTrue(markdown.contains("Elasticsearch log evidence"));
        assertTrue(markdown.contains("Log entry `1` `ERROR` `svc`"));
        assertTrue(markdown.contains("- message:"));
        assertTrue(markdown.contains("Deadlock updating order"));
        assertTrue(markdown.contains("- exception:"));
        assertTrue(markdown.contains("ActiveCaseRecordDomainRepository.java:74"));
        assertTrue(markdown.contains("- container: `backend`"));
    }
}
