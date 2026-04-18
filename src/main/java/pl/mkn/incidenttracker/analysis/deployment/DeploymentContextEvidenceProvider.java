package pl.mkn.incidenttracker.analysis.deployment;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.view.ElasticLogEvidenceView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
@Order(15)
@RequiredArgsConstructor
public class DeploymentContextEvidenceProvider implements AnalysisEvidenceProvider {

    private final DeploymentContextResolver deploymentContextResolver;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        var logEvidence = ElasticLogEvidenceView.from(context);
        if (logEvidence.isEmpty()) {
            return emptySection();
        }

        var deployments = new LinkedHashMap<String, ResolvedDeploymentContext>();
        for (var logEntry : logEvidence.entries()) {
            var deployment = deploymentContextResolver.resolve(logEntry);
            if (deployment == null) {
                continue;
            }

            deployments.putIfAbsent(deployment.key(), deployment);
        }

        if (deployments.isEmpty()) {
            return emptySection();
        }

        var items = deployments.values().stream()
                .map(this::toItem)
                .toList();

        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.copyOf(items)
        );
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return new AnalysisEvidenceReference("deployment-context", "resolved-deployment");
    }

    @Override
    public List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of(ElasticLogEvidenceView.EVIDENCE_REFERENCE);
    }

    @Override
    public AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.CONTEXT;
    }

    @Override
    public String stepCode() {
        return "DEPLOYMENT_CONTEXT";
    }

    @Override
    public String stepLabel() {
        return "Rozwiazywanie kontekstu deploymentu";
    }

    private AnalysisEvidenceSection emptySection() {
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.of()
        );
    }

    private AnalysisEvidenceItem toItem(ResolvedDeploymentContext deployment) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "environment", deployment.environment());
        addAttribute(attributes, "branch", deployment.branch());
        addAttribute(attributes, "projectName", deployment.projectName());
        addAttribute(attributes, "containerName", deployment.containerName());
        addAttribute(attributes, "containerImage", deployment.containerImage());
        addAttribute(attributes, "commitSha", deployment.commitSha());

        return new AnalysisEvidenceItem(
                "Deployment context " + firstNonBlank(deployment.projectName(), deployment.containerName()),
                List.copyOf(attributes)
        );
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

}
