package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;

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
        assertTrue(digest.contains("- className: `com.example.CustomerProfileService`"));
        assertTrue(digest.contains("## Deployment facts"));
        assertTrue(digest.contains("- projectNameHint: `crm-customer-profile-service`"));
        assertTrue(digest.contains("- commitSha: `abc123`"));
        assertTrue(digest.contains("## Operational code search scope"));
        assertTrue(digest.contains("- code search scopes: `crm-customer-code-search`"));
        assertTrue(digest.contains("- GitLab projects to search as one semantic implementation scope: `crm-customer-profile-service`, `libs/crm-customer-shared`"));
        assertTrue(digest.contains("- code search repository roles: `crm-customer-code-search:crm-customer-profile-service-repo:primary:priority=1`, `crm-customer-code-search:crm-customer-shared-repo:supporting-library:priority=2`"));
        assertTrue(digest.contains("- code search repository reasons: `crm-customer-code-search:crm-customer-profile-service-repo:Main CRM customer profile service repository.`, `crm-customer-code-search:crm-customer-shared-repo:Shared CRM customer profile rules.`"));
        assertTrue(digest.contains("## Runtime signals"));
        assertTrue(digest.contains("- Dynatrace collection status: `COLLECTED`"));
        assertTrue(digest.contains("- matched services: `crm-customer-profile-service`"));
        assertTrue(digest.contains("## Code evidence"));
        assertTrue(digest.contains("- project: `crm-customer-profile-service`"));
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
                                        "crm-customer-profile-service log entry",
                                        attr("serviceName", "crm-customer-profile-service"),
                                        attr("className", "com.example.CustomerProfileService"),
                                        attr("message", "Failed to resolve customer profile"),
                                        attr("exception", """
                                                java.lang.IllegalStateException: failed
                                                \tat com.example.CustomerProfileService.resolve(CustomerProfileService.java:42)
                                                """)
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "deployment-context",
                                "resolved-deployment",
                                List.of(item(
                                        "deployment",
                                        attr("projectNameHint", "crm-customer-profile-service"),
                                        attr("containerName", "crm-customer-profile"),
                                        attr("containerImage", "registry/crm-customer-profile:abc123"),
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
                                                attr("componentName", "crm-customer-profile-service"),
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
                                        "CustomerProfileService and repository collaborator",
                                        attr("projectName", "crm-customer-profile-service"),
                                        attr("filePath", "src/main/java/com/example/CustomerProfileService.java"),
                                        attr("symbol", "com.example.CustomerProfileService"),
                                        attr("lineNumber", "42"),
                                        attr("content", """
                                                class CustomerProfileService {
                                                    CustomerProfile resolve(String itemId) {
                                                        return customerProfileRepository.find(itemId);
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
                                                "Operational system customer profile",
                                                attr("systemId", "crm-customer-profile"),
                                                attr("name", "CRM Customer Profile"),
                                                attr("repositoryIds", "crm-customer-profile-service-repo; crm-customer-shared-repo"),
                                                attr("codeSearchScopeIds", "crm-customer-code-search"),
                                                attr("codeSearchRepositoryIds", "crm-customer-profile-service-repo; crm-customer-shared-repo"),
                                                attr("codeSearchProjects", "crm-customer-profile-service; libs/crm-customer-shared"),
                                                attr("codeSearchRepositoryRoles", "crm-customer-code-search:crm-customer-profile-service-repo:primary:priority=1; crm-customer-code-search:crm-customer-shared-repo:supporting-library:priority=2"),
                                                attr("codeSearchRepositoryReasons", "crm-customer-code-search:crm-customer-profile-service-repo:Main CRM customer profile service repository.; crm-customer-code-search:crm-customer-shared-repo:Shared CRM customer profile rules.")
                                        ),
                                        item(
                                                "Operational repository crm-customer-shared-repo",
                                                attr("repositoryId", "crm-customer-shared-repo"),
                                                attr("projectPath", "libs/crm-customer-shared"),
                                                attr("systemIds", "crm-customer-profile")
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
