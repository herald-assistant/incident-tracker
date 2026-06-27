package pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentEvidenceCoverageEvaluatorTest {

    private final CopilotIncidentEvidenceCoverageEvaluator evaluator = new CopilotIncidentEvidenceCoverageEvaluator();

    @Test
    void shouldReportMissingGitLabEvidenceWhenNoResolvedCodeIsAttached() {
        var report = evaluator.evaluate(request("dev3", List.of()));

        assertEquals(IncidentGitLabEvidenceCoverage.NONE, report.gitLab());
        assertTrue(report.hasGap("MISSING_CODE_CONTEXT"));
        assertTrue(report.hasGap("TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED"));
    }

    @Test
    void shouldRecommendGitLabGroundingWhenBranchIsResolvedWithoutGitLabGroup() {
        var report = evaluator.evaluate(new InitialAnalysisRequest(
                "corr-123",
                "dev3",
                "release/2026.04",
                null,
                List.of()
        ));

        assertTrue(report.hasGap("TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED"));
    }

    @Test
    void shouldClassifyFailingMethodOnlyAsMissingFlowContext() {
        var report = evaluator.evaluate(request("dev3", List.of(gitLabSection(
                "CheckoutService.java around failing method",
                "src/main/java/com/example/CheckoutService.java",
                """
                        class CheckoutService {
                            Customer submit(CheckoutCommand command) {
                                if (command == null) {
                                    throw new IllegalArgumentException("command");
                                }
                                return command.toOrder();
                            }
                        }
                        """
        ))));

        assertEquals(IncidentGitLabEvidenceCoverage.FAILING_METHOD_ONLY, report.gitLab());
        assertTrue(report.hasGap("MISSING_FLOW_CONTEXT"));
    }

    @Test
    void shouldClassifyFlowContextAttachedWhenEvidenceExplainsSurroundingFlow() {
        var report = evaluator.evaluate(request("dev3", List.of(gitLabSection(
                "Flow context downstream for CheckoutService",
                "src/main/java/com/example/CheckoutService.java",
                """
                        class CheckoutService {
                            Customer submit(CheckoutCommand command) {
                                var validation = validator.validate(command);
                                return downstreamClient.reserve(validation);
                            }
                        }
                        """
        ))));

        assertEquals(IncidentGitLabEvidenceCoverage.FLOW_CONTEXT_ATTACHED, report.gitLab());
        assertTrue(report.hasGap("TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED"));
    }

    @Test
    void shouldClassifyCompleteElasticStacktraceAsSufficient() {
        var report = evaluator.evaluate(request("dev3", List.of(new AnalysisEvidenceSection(
                "elasticsearch",
                "logs",
                List.of(item(
                        "error log",
                        attr("serviceName", "crm-catalog-service"),
                        attr("className", "com.example.CustomerCatalogService"),
                        attr("message", "Failed to resolve catalog item ITEM-1"),
                        attr("exception", """
                                java.lang.IllegalStateException: failed
                                \tat com.example.CustomerCatalogService.resolve(CustomerCatalogService.java:42)
                                """)
                ))
        ))));

        assertEquals(IncidentElasticEvidenceCoverage.SUFFICIENT, report.elastic());
    }

    @Test
    void shouldRequireDbDiagnosticsForJpaEntityNotFoundAndReportUnresolvedEnvironment() {
        var report = evaluator.evaluate(request(null, List.of(new AnalysisEvidenceSection(
                "elasticsearch",
                "logs",
                List.of(item(
                        "repository error",
                        attr("serviceName", "crm-catalog-service"),
                        attr("message", "EntityNotFoundException while loading tenant status by business key")
                ))
        ))));

        assertEquals(IncidentDataDiagnosticNeed.REQUIRED, report.dataDiagnosticNeed());
        assertTrue(report.hasGap("DB_ENVIRONMENT_UNRESOLVED"));
    }

    private InitialAnalysisRequest request(String environment, List<AnalysisEvidenceSection> sections) {
        return new InitialAnalysisRequest(
                "corr-123",
                environment,
                "release/2026.04",
                "CRM/runtime",
                sections
        );
    }

    private AnalysisEvidenceSection gitLabSection(String title, String filePath, String content) {
        return new AnalysisEvidenceSection(
                "gitlab",
                "resolved-code",
                List.of(item(
                        title,
                        attr("projectName", "checkout-service"),
                        attr("filePath", filePath),
                        attr("lineNumber", "12"),
                        attr("returnedStartLine", "8"),
                        attr("returnedEndLine", "18"),
                        attr("content", content),
                        attr("contentTruncated", "false")
                ))
        );
    }

    private AnalysisEvidenceItem item(String title, AnalysisEvidenceAttribute... attributes) {
        return new AnalysisEvidenceItem(title, List.of(attributes));
    }

    private AnalysisEvidenceAttribute attr(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value);
    }
}
