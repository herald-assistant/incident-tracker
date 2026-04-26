package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentDigestServiceTest {

    private final CopilotIncidentDigestService service = new CopilotIncidentDigestService();
    private final CopilotEvidenceCoverageEvaluator coverageEvaluator = new CopilotEvidenceCoverageEvaluator();

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
        assertTrue(digest.contains("- className: `com.example.BillingService`"));
        assertTrue(digest.contains("## Deployment facts"));
        assertTrue(digest.contains("- projectNameHint: `billing-service`"));
        assertTrue(digest.contains("- commitSha: `abc123`"));
        assertTrue(digest.contains("## Runtime signals"));
        assertTrue(digest.contains("- Dynatrace collection status: `COLLECTED`"));
        assertTrue(digest.contains("- matched services: `billing-service`"));
        assertTrue(digest.contains("## Code evidence"));
        assertTrue(digest.contains("- project: `billing-service`"));
        assertTrue(digest.contains("- coverage: `DIRECT_COLLABORATOR_ATTACHED`"));
        assertTrue(digest.contains("## Known evidence gaps"));
        assertTrue(digest.contains("`MISSING_FLOW_CONTEXT`"));
    }

    @Test
    void shouldRenderDigestEvenWhenEvidenceIsMissing() {
        var request = new AnalysisAiAnalysisRequest("corr-123", null, null, "sample/runtime", List.of());
        var digest = service.renderDigest(request, coverageEvaluator.evaluate(request));

        assertTrue(digest.contains("# Incident digest"));
        assertTrue(digest.contains("- environment: `unknown`"));
        assertTrue(digest.contains("- Elasticsearch: `NONE`"));
        assertTrue(digest.contains("- GitLab: `NONE`"));
        assertTrue(digest.contains("`MISSING_LOGS`"));
        assertTrue(digest.contains("`MISSING_CODE_CONTEXT`"));
    }

    private AnalysisAiAnalysisRequest requestWithEvidence() {
        return new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev3",
                "release/2026.04",
                "sample/runtime",
                List.of(
                        new AnalysisEvidenceSection(
                                "elasticsearch",
                                "logs",
                                List.of(item(
                                        "billing-service log entry",
                                        attr("serviceName", "billing-service"),
                                        attr("className", "com.example.BillingService"),
                                        attr("message", "Failed to settle invoice"),
                                        attr("exception", """
                                                java.lang.IllegalStateException: failed
                                                \tat com.example.BillingService.settle(BillingService.java:42)
                                                """)
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "deployment-context",
                                "resolved-deployment",
                                List.of(item(
                                        "deployment",
                                        attr("projectNameHint", "billing-service"),
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
                                                attr("componentName", "billing-service"),
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
                                        "BillingService and repository collaborator",
                                        attr("projectName", "billing-service"),
                                        attr("filePath", "src/main/java/com/example/BillingService.java"),
                                        attr("symbol", "com.example.BillingService"),
                                        attr("lineNumber", "42"),
                                        attr("content", """
                                                class BillingService {
                                                    Invoice settle(String invoiceId) {
                                                        return billingRepository.find(invoiceId);
                                                    }
                                                }
                                                """),
                                        attr("contentTruncated", "false")
                                ))
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
