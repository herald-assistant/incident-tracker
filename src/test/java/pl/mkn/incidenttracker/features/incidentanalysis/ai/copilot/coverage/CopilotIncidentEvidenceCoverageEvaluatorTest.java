package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

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
        assertTrue(report.hasGap("AFFECTED_FUNCTION_GITLAB_RECOMMENDED"));
    }

    @Test
    void shouldClassifyFailingMethodOnlyAsMissingFlowContext() {
        var report = evaluator.evaluate(request("dev3", List.of(gitLabSection(
                "CheckoutService.java around failing method",
                "src/main/java/com/example/CheckoutService.java",
                """
                        class CheckoutService {
                            Order submit(CheckoutCommand command) {
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
                            Order submit(CheckoutCommand command) {
                                var validation = validator.validate(command);
                                return downstreamClient.reserve(validation);
                            }
                        }
                        """
        ))));

        assertEquals(IncidentGitLabEvidenceCoverage.FLOW_CONTEXT_ATTACHED, report.gitLab());
        assertTrue(report.hasGap("AFFECTED_FUNCTION_GITLAB_RECOMMENDED"));
    }

    @Test
    void shouldClassifyCompleteElasticStacktraceAsSufficient() {
        var report = evaluator.evaluate(request("dev3", List.of(new AnalysisEvidenceSection(
                "elasticsearch",
                "logs",
                List.of(item(
                        "error log",
                        attr("serviceName", "billing-service"),
                        attr("className", "com.example.BillingService"),
                        attr("message", "Failed to settle invoice INV-1"),
                        attr("exception", """
                                java.lang.IllegalStateException: failed
                                \tat com.example.BillingService.settle(BillingService.java:42)
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
                        attr("serviceName", "billing-service"),
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
                "sample/runtime",
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
