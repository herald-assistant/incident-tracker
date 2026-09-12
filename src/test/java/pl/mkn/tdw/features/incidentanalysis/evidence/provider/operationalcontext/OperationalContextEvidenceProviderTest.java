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
        assertTrue(titles.contains("Operational system crm-contact-service"));
        assertTrue(titles.contains("Operational integration crm-contact-to-notification-sync"));
        assertTrue(titles.contains("Operational process crm-contact-notification-flow"));
        assertTrue(titles.contains("Operational repository crm-contact-service-repo"));
        assertTrue(titles.contains("Operational repository crm-contact-rules-repo"));
        assertTrue(titles.contains("Operational bounded context crm-contact-context"));
        assertTrue(titles.contains("Operational glossary term soap-fault"));
        assertTrue(titles.contains("Operational handoff rule integration-external-sync-failure"));
        assertEquals("crm-contact-service", view.systems().get(0).systemId());
        assertTrue(view.systems().get(0).ownerTeamIds().contains("crm-contact-team"));
        assertTrue(view.systems().get(0).ownerLabels().contains("crm-contact-team"));
        assertEquals("inside-system", view.systems().get(0).ownershipSituationType());
        assertTrue(view.systems().get(0).repositoryIds().contains("crm-contact-rules-repo"));
        assertTrue(view.systems().get(0).codeSearchScopeIds().contains("crm-contact-code-search"));
        assertTrue(view.systems().get(0).codeSearchRepositoryIds().contains("crm-contact-rules-repo"));
        assertTrue(view.systems().get(0).codeSearchProjects().contains("libs/crm-contact-rules"));
        assertTrue(view.systems().get(0).codeSearchRepositoryRoles().contains(
                "crm-contact-code-search:crm-contact-rules-repo:supporting-library:priority=2"
        ));
        assertTrue(view.systems().get(0).codeSearchRepositoryReasons().contains(
                "crm-contact-code-search:crm-contact-rules-repo:Shared CRM contact rules are used by crm-contact-service."
        ));
        assertEquals("crm-contact-to-notification-sync", view.integrations().get(0).integrationId());
        assertEquals("synchronous-request", view.integrations().get(0).integrationStyle());
        assertTrue(view.integrations().get(0).ownerTeamIds().contains("crm-contact-team"));
        assertTrue(view.integrations().get(0).partnerOwnerLabels().stream()
                .anyMatch(label -> label.toLowerCase(java.util.Locale.ROOT).contains("crm notification context")));
        assertEquals("bounded-context-boundary", view.integrations().get(0).ownershipSituationType());
        assertEquals("crm-contact-notification-flow", view.processes().get(0).processId());
        assertTrue(view.processes().get(0).ownerTeamIds().contains("crm-contact-team"));
        assertEquals("crm-contact-service-repo", view.repositories().get(0).repositoryId());
        assertTrue(view.repositories().get(0).ownerTeamIds().contains("crm-contact-team"));
        assertTrue(view.repositories().stream()
                .anyMatch(repository -> repository.repositoryId().equals("crm-contact-rules-repo")
                        && repository.systemIds().contains("crm-contact-service")));
        assertEquals("crm-contact-context", view.boundedContexts().get(0).boundedContextId());
        assertTrue(view.boundedContexts().get(0).ownerTeamIds().contains("crm-contact-team"));
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
                List.of()
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
                List.of()
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
                                "ERROR crm-contact-service log entry",
                                List.of(
                                        attribute("serviceName", "crm-contact-service"),
                                        attribute("containerName", "crm-contact-service"),
                                        attribute("className", "com.example.crm.contact.CrmNotificationGateway"),
                                        attribute("message", "SOAPFault while calling api.crm-notification.example.invalid /crm/notifications"),
                                        attribute("exception", "Read timed out in CrmNotificationGateway.call"),
                                        attribute("host", "api.crm-notification.example.invalid"),
                                        attribute("endpoint", "/crm/notifications")
                                )
                        ))
                ))
                .withSection(new AnalysisEvidenceSection(
                        "gitlab",
                        "resolved-code",
                        List.of(new AnalysisEvidenceItem(
                                "crm-contact-service-repo file CrmNotificationGateway.java",
                                List.of(
                                        attribute("projectName", "crm-contact-service-repo"),
                                        attribute("filePath", "src/main/java/com/example/app/core/CrmNotificationGateway.java")
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
                "systemType", "internal-service",
                "systemSubtype", "backend",
                "aliases", List.of(runtimeName)
        ));
    }

}
