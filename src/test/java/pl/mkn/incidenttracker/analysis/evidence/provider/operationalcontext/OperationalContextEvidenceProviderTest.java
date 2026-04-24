package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextAdapter;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextProperties;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextEvidenceProviderTest {

    @Test
    void shouldStayDisabledByDefault() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(false);

        var provider = new OperationalContextEvidenceProvider(
                properties,
                new OperationalContextAdapter(properties),
                new OperationalContextCatalogMatcher(properties),
                new OperationalContextEvidenceMapper()
        );

        var section = provider.collect(sampleContext());

        assertEquals("operational-context", section.provider());
        assertEquals("matched-context", section.category());
        assertTrue(section.items().isEmpty());
    }

    @Test
    void shouldEnrichIncidentWithMatchedOperationalContextWhenEnabled() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(true);
        properties.setResourceRoot("operational-context-test");

        var provider = new OperationalContextEvidenceProvider(
                properties,
                new OperationalContextAdapter(properties),
                new OperationalContextCatalogMatcher(properties),
                new OperationalContextEvidenceMapper()
        );

        var section = provider.collect(sampleContext());
        var titles = section.items().stream().map(AnalysisEvidenceItem::title).toList();
        var view = OperationalContextEvidenceView.from(section);

        assertEquals("operational-context", section.provider());
        assertEquals("matched-context", section.category());
        assertFalse(section.items().isEmpty());
        assertFalse(view.isEmpty());
        assertTrue(titles.contains("Operational system app-core"));
        assertTrue(titles.contains("Operational integration app-core-to-partner-sync"));
        assertTrue(titles.contains("Operational process main-process"));
        assertTrue(titles.contains("Operational repository app-core-repo"));
        assertTrue(titles.contains("Operational bounded context core-context"));
        assertTrue(titles.contains("Operational team core-team"));
        assertTrue(titles.contains("Operational glossary term soap-fault"));
        assertTrue(titles.contains("Operational handoff rule integration-external-sync-failure"));
        assertEquals("app-core", view.systems().get(0).systemId());
        assertEquals("app-core-to-partner-sync", view.integrations().get(0).integrationId());
        assertEquals("main-process", view.processes().get(0).processId());
        assertEquals("app-core-repo", view.repositories().get(0).repositoryId());
        assertEquals("core-context", view.boundedContexts().get(0).boundedContextId());
        assertEquals("core-team", view.teams().get(0).teamId());
        assertEquals("soap-fault", view.glossaryTerms().get(0).termId());
        assertEquals("integration-external-sync-failure", view.handoffRules().get(0).ruleId());
        assertEquals("Integration Team", view.handoffRules().get(0).routeTo());
    }

    private AnalysisContext sampleContext() {
        return AnalysisContext.initialize("corr-123")
                .withSection(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(new AnalysisEvidenceItem(
                                "ERROR app-core log entry",
                                List.of(
                                        attribute("serviceName", "app-core"),
                                        attribute("containerName", "app-core"),
                                        attribute("className", "com.example.app.core.SyncGateway"),
                                        attribute("message", "SOAPFault while calling api.partner.local /partner/resource"),
                                        attribute("exception", "Read timed out in SyncGateway.call"),
                                        attribute("host", "api.partner.local"),
                                        attribute("endpoint", "/partner/resource")
                                )
                        ))
                ))
                .withSection(new AnalysisEvidenceSection(
                        "gitlab",
                        "resolved-code",
                        List.of(new AnalysisEvidenceItem(
                                "app-core-repo file SyncGateway.java",
                                List.of(
                                        attribute("projectName", "app-core-repo"),
                                        attribute("filePath", "src/main/java/com/example/app/core/SyncGateway.java")
                                )
                        ))
                ));
    }

    private AnalysisEvidenceAttribute attribute(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value);
    }

}
