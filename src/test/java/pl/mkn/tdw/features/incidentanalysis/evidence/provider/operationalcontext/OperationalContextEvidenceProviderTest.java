package pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextAdapterTestCreator;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextProperties;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                OperationalContextAdapterTestCreator.create(properties),
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
                OperationalContextAdapterTestCreator.create(properties),
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
        assertTrue(titles.contains("Operational repository app-shared-lib-repo"));
        assertTrue(titles.contains("Operational bounded context core-context"));
        assertTrue(titles.contains("Operational glossary term soap-fault"));
        assertTrue(titles.contains("Operational handoff rule integration-external-sync-failure"));
        assertEquals("app-core", view.systems().get(0).systemId());
        assertTrue(view.systems().get(0).ownerTeamIds().contains("core-team"));
        assertTrue(view.systems().get(0).ownerLabels().contains("core-team"));
        assertEquals("inside-system", view.systems().get(0).ownershipSituationType());
        assertTrue(view.systems().get(0).repositoryIds().contains("app-shared-lib-repo"));
        assertTrue(view.systems().get(0).codeSearchScopeIds().contains("app-core-code-search"));
        assertTrue(view.systems().get(0).codeSearchRepositoryIds().contains("app-shared-lib-repo"));
        assertTrue(view.systems().get(0).codeSearchProjects().contains("libs/app-shared-lib"));
        assertTrue(view.systems().get(0).codeSearchRepositoryRoles().contains(
                "app-core-code-search:app-shared-lib-repo:supporting-library:priority=2"
        ));
        assertTrue(view.systems().get(0).codeSearchRepositoryReasons().contains(
                "app-core-code-search:app-shared-lib-repo:Shared domain predicates are used by app-core."
        ));
        assertEquals("app-core-to-partner-sync", view.integrations().get(0).integrationId());
        assertEquals("synchronous-request", view.integrations().get(0).integrationStyle());
        assertTrue(view.integrations().get(0).ownerTeamIds().contains("core-team"));
        assertTrue(view.integrations().get(0).partnerOwnerLabels().stream()
                .anyMatch(label -> label.contains("Partner context")));
        assertEquals("bounded-context-boundary", view.integrations().get(0).ownershipSituationType());
        assertEquals("main-process", view.processes().get(0).processId());
        assertTrue(view.processes().get(0).ownerTeamIds().contains("core-team"));
        assertEquals("app-core-repo", view.repositories().get(0).repositoryId());
        assertTrue(view.repositories().get(0).ownerTeamIds().contains("core-team"));
        assertTrue(view.repositories().stream()
                .anyMatch(repository -> repository.repositoryId().equals("app-shared-lib-repo")
                        && repository.systemIds().contains("app-core")));
        assertEquals("core-context", view.boundedContexts().get(0).boundedContextId());
        assertTrue(view.boundedContexts().get(0).ownerTeamIds().contains("core-team"));
        assertEquals("soap-fault", view.glossaryTerms().get(0).termId());
        assertEquals("integration-external-sync-failure", view.handoffRules().get(0).ruleId());
        assertTrue(view.handoffRules().get(0).requiredEvidence().contains("host"));
    }

    @Test
    void shouldKeepAllDirectlyDetectedInternalServicesAboveGenericLimit() {
        var properties = new OperationalContextProperties();
        properties.setMaxItemsPerType(1);
        var matcher = new OperationalContextCatalogMatcher(properties);
        var context = AnalysisContext.initialize("corr-multi-service")
                .withSection(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(
                                logItem("crm-entry-service"),
                                logItem("crm-support-service"),
                                logItem("crm-decision-service")
                        )
                ));
        var catalog = new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(
                        internalService("crm-entry", "crm-entry-service"),
                        internalService("crm-support", "crm-support-service"),
                        internalService("crm-decision", "crm-decision-service")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        var matches = matcher.match(catalog, OperationalContextIncidentSignals.from(context));

        assertEquals(
                List.of("crm-decision", "crm-entry", "crm-support"),
                matches.systemMatches().stream()
                        .map(match -> match.entry().id())
                        .sorted()
                        .toList()
        );
    }

    @Test
    void shouldKeepInternalServicesMentionedInLogMessagesAboveGenericLimit() {
        var properties = new OperationalContextProperties();
        properties.setMaxItemsPerType(1);
        var matcher = new OperationalContextCatalogMatcher(properties);
        var context = AnalysisContext.initialize("corr-multi-service-message")
                .withSection(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(
                                logItem("crm-entry-service"),
                                logItem(
                                        "crm-entry-service",
                                        "Calling https://crm-support-service.runtime.svc.cluster.local"
                                ),
                                logItem(
                                        "crm-entry-service",
                                        "Calling https://crm-decision-service.runtime.svc.cluster.local"
                                )
                        )
                ));
        var catalog = new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(
                        internalService("crm-entry", "crm-entry-service"),
                        internalService("crm-support", "crm-support-service"),
                        internalService("crm-decision", "crm-decision-service")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        var matches = matcher.match(catalog, OperationalContextIncidentSignals.from(context));

        assertEquals(
                List.of("crm-decision", "crm-entry", "crm-support"),
                matches.systemMatches().stream()
                        .map(match -> match.entry().id())
                        .sorted()
                        .toList()
        );
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

    private AnalysisEvidenceItem logItem(String serviceName) {
        return logItem(serviceName, null);
    }

    private AnalysisEvidenceItem logItem(String serviceName, String message) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        attributes.add(attribute("serviceName", serviceName));
        attributes.add(attribute("containerName", serviceName));
        if (message != null) {
            attributes.add(attribute("message", message));
        }
        return new AnalysisEvidenceItem(
                "ERROR " + serviceName,
                List.copyOf(attributes)
        );
    }

    private OperationalContextDtos.OperationalContextSystem internalService(String id, String runtimeName) {
        return OperationalContextDtos.system(Map.of(
                "id", id,
                "name", id,
                "kind", "internal-service",
                "aliases", List.of(runtimeName)
        ));
    }

}
