package pl.mkn.incidenttracker.analysis.deployment;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record DeploymentContextEvidenceView(
        List<ResolvedDeploymentContext> deployments
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("deployment-context", "resolved-deployment");

    public static DeploymentContextEvidenceView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static DeploymentContextEvidenceView from(List<AnalysisEvidenceSection> evidenceSections) {
        var deployments = new ArrayList<ResolvedDeploymentContext>();

        for (var section : evidenceSections) {
            if (!matches(section)) {
                continue;
            }

            for (var item : section.items()) {
                var attributes = AnalysisEvidenceAttributes.byName(item.attributes());
                deployments.add(new ResolvedDeploymentContext(
                        AnalysisEvidenceAttributes.text(attributes, "environment"),
                        AnalysisEvidenceAttributes.text(attributes, "branch"),
                        firstNonBlank(
                                AnalysisEvidenceAttributes.text(attributes, "projectNameHint"),
                                AnalysisEvidenceAttributes.text(attributes, "projectName")
                        ),
                        AnalysisEvidenceAttributes.text(attributes, "containerName"),
                        AnalysisEvidenceAttributes.text(attributes, "containerImage"),
                        AnalysisEvidenceAttributes.text(attributes, "commitSha")
                ));
            }
        }

        return new DeploymentContextEvidenceView(List.copyOf(deployments));
    }

    public boolean isEmpty() {
        return deployments.isEmpty();
    }

    public String environment() {
        return deployments.stream()
                .map(ResolvedDeploymentContext::environment)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    public String gitLabBranch() {
        return deployments.stream()
                .map(ResolvedDeploymentContext::branch)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    public Optional<ResolvedDeploymentContext> findFor(ElasticLogEvidenceView.LogEntry logEntry) {
        return deployments.stream()
                .filter(deployment -> deployment.matches(logEntry))
                .findFirst();
    }

    private static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

}
