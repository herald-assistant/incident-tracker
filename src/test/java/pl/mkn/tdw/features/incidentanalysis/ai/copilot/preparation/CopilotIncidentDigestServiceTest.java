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
        assertTrue(digest.contains("- className: `com.example.CustomerCatalogService`"));
        assertTrue(digest.contains("## Deployment facts"));
        assertTrue(digest.contains("- projectNameHint: `crm-catalog-service`"));
        assertTrue(digest.contains("- commitSha: `abc123`"));
        assertTrue(digest.contains("## Operational code search scope"));
        assertTrue(digest.contains("- code search scopes: `catalog-code-search`"));
        assertTrue(digest.contains("- GitLab projects to search as one semantic implementation scope: `crm-catalog-service`, `libs/catalog-shared`"));
        assertTrue(digest.contains("- code search repository roles: `catalog-code-search:crm-catalog-service-repo:primary-implementation:priority=1`, `catalog-code-search:catalog-shared-repo:supporting-library:priority=2`"));
        assertTrue(digest.contains("- package roots: `com.example.catalog.shared`"));
        assertTrue(digest.contains("- class hints: `CatalogRules`"));
        assertTrue(digest.contains("## Runtime signals"));
        assertTrue(digest.contains("- Dynatrace collection status: `COLLECTED`"));
        assertTrue(digest.contains("- matched services: `crm-catalog-service`"));
        assertTrue(digest.contains("## Code evidence"));
        assertTrue(digest.contains("- project: `crm-catalog-service`"));
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
                                        "crm-catalog-service log entry",
                                        attr("serviceName", "crm-catalog-service"),
                                        attr("className", "com.example.CustomerCatalogService"),
                                        attr("message", "Failed to resolve catalog item"),
                                        attr("exception", """
                                                java.lang.IllegalStateException: failed
                                                \tat com.example.CustomerCatalogService.resolve(CustomerCatalogService.java:42)
                                                """)
                                ))
                        ),
                        new AnalysisEvidenceSection(
                                "deployment-context",
                                "resolved-deployment",
                                List.of(item(
                                        "deployment",
                                        attr("projectNameHint", "crm-catalog-service"),
                                        attr("containerName", "catalog"),
                                        attr("containerImage", "registry/catalog:abc123"),
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
                                                attr("componentName", "crm-catalog-service"),
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
                                        "CustomerCatalogService and repository collaborator",
                                        attr("projectName", "crm-catalog-service"),
                                        attr("filePath", "src/main/java/com/example/CustomerCatalogService.java"),
                                        attr("symbol", "com.example.CustomerCatalogService"),
                                        attr("lineNumber", "42"),
                                        attr("content", """
                                                class CustomerCatalogService {
                                                    CatalogItem resolve(String itemId) {
                                                        return catalogRepository.find(itemId);
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
                                                "Operational system catalog",
                                                attr("systemId", "catalog"),
                                                attr("name", "Catalog"),
                                                attr("repositoryIds", "crm-catalog-service-repo; catalog-shared-repo"),
                                                attr("codeSearchScopeIds", "catalog-code-search"),
                                                attr("codeSearchRepositoryIds", "crm-catalog-service-repo; catalog-shared-repo"),
                                                attr("codeSearchProjects", "crm-catalog-service; libs/catalog-shared"),
                                                attr("codeSearchRepositoryRoles", "catalog-code-search:crm-catalog-service-repo:primary-implementation:priority=1; catalog-code-search:catalog-shared-repo:supporting-library:priority=2"),
                                                attr("sourcePackages", "com.example.catalog.shared"),
                                                attr("classHints", "CatalogRules")
                                        ),
                                        item(
                                                "Operational repository catalog-shared-repo",
                                                attr("repositoryId", "catalog-shared-repo"),
                                                attr("projectPath", "libs/catalog-shared"),
                                                attr("systemIds", "catalog"),
                                                attr("sourcePackages", "com.example.catalog.shared"),
                                                attr("classHints", "CatalogRules")
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
