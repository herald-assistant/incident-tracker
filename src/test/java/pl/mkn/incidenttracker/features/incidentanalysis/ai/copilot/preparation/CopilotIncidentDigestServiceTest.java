package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentDigestServiceTest {

    private final CopilotIncidentDigestService service = new CopilotIncidentDigestService();
    private final CopilotIncidentEvidenceCoverageEvaluator coverageEvaluator = new CopilotIncidentEvidenceCoverageEvaluator();

    @Test
    void shouldRenderDigestWithSessionCoverageAndEvidenceHighlights() {
        var request = requestWithEvidence();
        var digest = service.renderDigest(request, coverageEvaluator.evaluate(request));

        assertTrue(digest.contains("# Incident digest"));
        assertTrue(digest.contains("## Session"));
        assertTrue(digest.contains("- correlationId: `corr-123`"));
        assertTrue(digest.contains("- environment: `dev3`"));
        assertTrue(digest.contains("## Evidence coverage"));
        assertTrue(digest.contains("- Elasticsearch: `SUFFICIENT`"));
        assertTrue(digest.contains("- GitLab: `DIRECT_COLLABORATOR_ATTACHED`"));
        assertTrue(digest.contains("## Strongest log signals"));
        assertTrue(digest.contains("- exception: `java.lang.IllegalStateException: failed`"));
        assertTrue(digest.contains("- className: `com.example.CustomerBillingService`"));
        assertTrue(digest.contains("## Deployment facts"));
        assertTrue(digest.contains("- projectNameHint: `crm-billing-service`"));
        assertTrue(digest.contains("- commitSha: `abc123`"));
        assertTrue(digest.contains("## Operational code search scope"));
        assertTrue(digest.contains("- code search scopes: `billing-code-search`"));
        assertTrue(digest.contains("- GitLab projects to search as one semantic implementation scope: `crm-billing-service`, `libs/billing-shared`"));
        assertTrue(digest.contains("- code search repository roles: `billing-code-search:crm-billing-service-repo:primary-implementation:priority=1`, `billing-code-search:billing-shared-repo:supporting-library:priority=2`"));
        assertTrue(digest.contains("- package roots: `com.example.billing.shared`"));
        assertTrue(digest.contains("- class hints: `BillingRules`"));
        assertTrue(digest.contains("## Runtime signals"));
        assertTrue(digest.contains("- Dynatrace collection status: `COLLECTED`"));
        assertTrue(digest.contains("- matched services: `crm-billing-service`"));
        assertTrue(digest.contains("## Code evidence"));
        assertTrue(digest.contains("- project: `crm-billing-service`"));
        assertTrue(digest.contains("- coverage: `DIRECT_COLLABORATOR_ATTACHED`"));
        assertTrue(digest.contains("## Known evidence gaps"));
        assertTrue(digest.contains("`MISSING_FLOW_CONTEXT`"));
    }

    @Test
    void shouldRenderDigestEvenWhenEvidenceIsMissing() {
        var request = new InitialAnalysisRequest("corr-123", null, null, "CRM/runtime", List.of());
        var digest = service.renderDigest(request, coverageEvaluator.evaluate(request));

        assertTrue(digest.contains("# Incident digest"));
        assertTrue(digest.contains("- environment: `unknown`"));
        assertTrue(digest.contains("- Elasticsearch: `NONE`"));
        assertTrue(digest.contains("- GitLab: `NONE`"));
        assertTrue(digest.contains("`MISSING_LOGS`"));
        assertTrue(digest.contains("`MISSING_CODE_CONTEXT`"));
    }

    private InitialAnalysisRequest requestWithEvidence() {
        return new InitialAnalysisRequest(
                "corr-123",
                "dev3",
                "release/2026.04",
                "CRM/runtime",
                List.of(
                        new AnalysisEvidenceSection(
                                "elasticsearch",
                                "logs",
                                List.of(item(
                                        "crm-billing-service log entry",
                                        attr("serviceName", "crm-billing-service"),
                                        attr("className", "com.example.CustomerBillingService"),
                                        attr("message", "Failed to settle invoice"),
                                        attr("exception", """
                                                java.lang.IllegalStateException: failed
                                                \tat com.example.CustomerBillingService.settle(CustomerBillingService.java:42)
                                                """)
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "deployment-context",
                                "resolved-deployment",
                                List.of(item(
                                        "deployment",
                                        attr("projectNameHint", "crm-billing-service"),
                                        attr("containerName", "billing"),
                                        attr("containerImage", "registry/billing:abc123"),
                                        attr("commitSha", "abc123")
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "dynatrace",
                                "runtime-signals",
                                List.of(
                                        item(
                                                "Dynatrace collection status",
                                                attr("dynatraceItemType", "collection-status"),
                                                attr("collectionStatus", "COLLECTED"),
                                                attr("correlationStatus", "MATCHED")
                                        ),
                                        item(
                                                "Dynatrace component",
                                                attr("dynatraceItemType", "component-status"),
                                                attr("componentName", "crm-billing-service"),
                                                attr("correlationStatus", "MATCHED"),
                                                attr("componentSignalStatus", "SIGNALS_PRESENT"),
                                                attr("problemDisplayId", "P-123"),
                                                attr("problemTitle", "Failure rate increase")
                                        )
                                )
                        ),
                        new AnalysisEvidenceSection(
                                "gitlab",
                                "resolved-code",
                                List.of(item(
                                        "CustomerBillingService and repository collaborator",
                                        attr("projectName", "crm-billing-service"),
                                        attr("filePath", "src/main/java/com/example/CustomerBillingService.java"),
                                        attr("symbol", "com.example.CustomerBillingService"),
                                        attr("lineNumber", "42"),
                                        attr("content", """
                                                class CustomerBillingService {
                                                    Invoice settle(String invoiceId) {
                                                        return billingRepository.find(invoiceId);
                                                    }
                                                }
                                                """),
                                        attr("contentTruncated", "false")
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "operational-context",
                                "matched-context",
                                List.of(
                                        item(
                                                "Operational system billing",
                                                attr("systemId", "billing"),
                                                attr("name", "Billing"),
                                                attr("repositoryIds", "crm-billing-service-repo; billing-shared-repo"),
                                                attr("codeSearchScopeIds", "billing-code-search"),
                                                attr("codeSearchRepositoryIds", "crm-billing-service-repo; billing-shared-repo"),
                                                attr("codeSearchProjects", "crm-billing-service; libs/billing-shared"),
                                                attr("codeSearchRepositoryRoles", "billing-code-search:crm-billing-service-repo:primary-implementation:priority=1; billing-code-search:billing-shared-repo:supporting-library:priority=2"),
                                                attr("sourcePackages", "com.example.billing.shared"),
                                                attr("classHints", "BillingRules")
                                        ),
                                        item(
                                                "Operational repository billing-shared-repo",
                                                attr("repositoryId", "billing-shared-repo"),
                                                attr("projectPath", "libs/billing-shared"),
                                                attr("systemIds", "billing"),
                                                attr("sourcePackages", "com.example.billing.shared"),
                                                attr("classHints", "BillingRules")
                                        )
                                )
                        )
                )
        );
    }

    private AnalysisEvidenceItem item(String title, AnalysisEvidenceAttribute... attributes) {
        return new AnalysisEvidenceItem(title, List.of(attributes));
    }

    private AnalysisEvidenceAttribute attr(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value);
    }
}
