package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageReport;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceRuntimeEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class CopilotIncidentDigestService {

    public String renderDigest(
            AnalysisAiAnalysisRequest request,
            CopilotEvidenceCoverageReport coverage
    ) {
        var evidenceSections = request != null
                ? request.evidenceSections()
                : List.<AnalysisEvidenceSection>of();
        var effectiveCoverage = coverage != null ? coverage : CopilotEvidenceCoverageReport.empty();
        var elastic = ElasticLogEvidenceView.from(evidenceSections);
        var deployments = DeploymentContextEvidenceView.from(evidenceSections);
        var dynatrace = DynatraceRuntimeEvidenceView.from(evidenceSections);
        var gitLab = GitLabResolvedCodeEvidenceView.from(evidenceSections);
        var operationalContext = OperationalContextEvidenceView.from(evidenceSections);
        var lines = new ArrayList<String>();

        lines.add("# Incident digest");
        lines.add("");
        addSession(lines, request);
        addCoverage(lines, effectiveCoverage);
        addStrongestLogSignals(lines, elastic);
        addDeploymentFacts(lines, deployments);
        addOperationalCodeSearchScope(lines, operationalContext);
        addRuntimeSignals(lines, dynatrace);
        addCodeEvidence(lines, gitLab, effectiveCoverage);
        addEvidenceGaps(lines, effectiveCoverage);

        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private void addSession(List<String> lines, AnalysisAiAnalysisRequest request) {
        lines.add("## Session");
        addInlineValue(lines, "correlationId", request != null ? request.correlationId() : null);
        addInlineValue(lines, "environment", request != null ? request.environment() : null);
        addInlineValue(lines, "gitLabBranch", request != null ? request.gitLabBranch() : null);
        addInlineValue(lines, "gitLabGroup", request != null ? request.gitLabGroup() : null);
        lines.add("");
    }

    private void addCoverage(List<String> lines, CopilotEvidenceCoverageReport coverage) {
        lines.add("## Evidence coverage");
        lines.add("- Elasticsearch: `" + coverage.elastic().name() + "`");
        lines.add("- GitLab: `" + coverage.gitLab().name() + "`");
        lines.add("- Data diagnostic need: `" + coverage.dataDiagnosticNeed().name() + "`");
        lines.add("- Operational context: `" + coverage.operationalContext().name() + "`");
        lines.add("");
    }

    private void addStrongestLogSignals(List<String> lines, ElasticLogEvidenceView elastic) {
        lines.add("## Strongest log signals");
        var logEntry = elastic.entries().stream()
                .filter(entry -> hasText(entry.exception()) || hasText(entry.message()))
                .findFirst();
        if (logEntry.isEmpty()) {
            lines.add("- none");
            lines.add("");
            return;
        }

        var entry = logEntry.get();
        addInlineValue(lines, "exception", firstLine(entry.exception()));
        addInlineValue(lines, "className", entry.className());
        addInlineValue(lines, "message", firstLine(entry.message()));
        addInlineValue(lines, "service/container", firstNonBlank(entry.serviceName(), entry.containerName()));
        lines.add("");
    }

    private void addDeploymentFacts(List<String> lines, DeploymentContextEvidenceView deployments) {
        lines.add("## Deployment facts");
        var deployment = deployments.deployments().stream().findFirst();
        if (deployment.isEmpty()) {
            lines.add("- none");
            lines.add("");
            return;
        }

        var item = deployment.get();
        addInlineValue(lines, "projectNameHint", item.projectNameHint());
        addInlineValue(lines, "containerName", item.containerName());
        addInlineValue(lines, "containerImage", item.containerImage());
        addInlineValue(lines, "commitSha", item.commitSha());
        lines.add("");
    }

    private void addOperationalCodeSearchScope(
            List<String> lines,
            OperationalContextEvidenceView operationalContext
    ) {
        lines.add("## Operational code search scope");
        if (operationalContext == null || operationalContext.isEmpty()) {
            lines.add("- none");
            lines.add("");
            return;
        }

        var systems = distinct(operationalContext.systems().stream()
                .map(system -> firstNonBlank(system.systemId(), system.name()))
                .toList());
        var projects = distinct(joinLists(
                operationalContext.systems().stream()
                        .flatMap(system -> system.codeSearchProjects().stream())
                        .toList(),
                operationalContext.repositories().stream()
                        .map(OperationalContextEvidenceView.RepositoryItem::project)
                        .toList()
        ));
        var packages = distinct(joinLists(
                operationalContext.systems().stream()
                        .flatMap(system -> system.sourcePackages().stream())
                        .toList(),
                operationalContext.repositories().stream()
                        .flatMap(repository -> repository.sourcePackages().stream())
                        .toList()
        ));
        var classHints = distinct(joinLists(
                operationalContext.systems().stream()
                        .flatMap(system -> system.classHints().stream())
                        .toList(),
                operationalContext.repositories().stream()
                        .flatMap(repository -> repository.classHints().stream())
                        .toList()
        ));

        addListValue(lines, "matched systems", systems);
        addListValue(lines, "GitLab projects to search as one deployment component", projects);
        addListValue(lines, "package roots", packages.stream().limit(8).toList());
        addListValue(lines, "class hints", classHints.stream().limit(8).toList());
        lines.add("");
    }

    private void addRuntimeSignals(List<String> lines, DynatraceRuntimeEvidenceView dynatrace) {
        lines.add("## Runtime signals");
        if (!dynatrace.hasStructuredStatusSummary()) {
            lines.add("- none");
            lines.add("");
            return;
        }

        if (dynatrace.collectionStatus() != null) {
            lines.add("- Dynatrace collection status: `" + dynatrace.collectionStatus().status().name() + "`");
        }
        var matchedServices = dynatrace.componentStatuses().stream()
                .map(DynatraceRuntimeEvidenceView.ComponentStatusItem::componentName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        addListValue(lines, "matched services", matchedServices);
        var problems = dynatrace.componentStatuses().stream()
                .map(component -> firstNonBlank(component.problemDisplayId(), component.problemTitle()))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        addListValue(lines, "problems", problems);
        lines.add("");
    }

    private void addCodeEvidence(
            List<String> lines,
            GitLabResolvedCodeEvidenceView gitLab,
            CopilotEvidenceCoverageReport coverage
    ) {
        lines.add("## Code evidence");
        var codeItem = gitLab.items().stream().findFirst();
        if (codeItem.isEmpty()) {
            lines.add("- none");
            lines.add("");
            return;
        }

        var item = codeItem.get();
        addInlineValue(lines, "project", item.projectName());
        addInlineValue(lines, "file", item.filePath());
        addInlineValue(lines, "symbol", item.symbol());
        addInlineValue(lines, "line", item.lineNumber() != null ? item.lineNumber().toString() : null);
        lines.add("- coverage: `" + coverage.gitLab().name() + "`");
        lines.add("");
    }

    private void addEvidenceGaps(List<String> lines, CopilotEvidenceCoverageReport coverage) {
        lines.add("## Known evidence gaps");
        if (coverage.gaps().isEmpty()) {
            lines.add("- none");
            return;
        }
        for (var gap : coverage.gaps()) {
            lines.add("- `" + escapeInlineCode(gap.code()) + "`: " + gap.description());
        }
    }

    private void addInlineValue(List<String> lines, String label, String value) {
        lines.add("- " + label + ": `" + escapeInlineCode(normalizeValue(value)) + "`");
    }

    private void addListValue(List<String> lines, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            lines.add("- " + label + ": `none`");
            return;
        }
        lines.add("- " + label + ": " + values.stream()
                .map(this::escapeInlineCode)
                .map("`%s`"::formatted)
                .reduce((left, right) -> left + ", " + right)
                .orElse("`none`"));
    }

    private List<String> joinLists(List<String> first, List<String> second) {
        var joined = new ArrayList<String>();
        if (first != null) {
            joined.addAll(first);
        }
        if (second != null) {
            joined.addAll(second);
        }
        return joined;
    }

    private List<String> distinct(List<String> values) {
        var distinct = new LinkedHashSet<String>();
        for (var value : values) {
            if (hasText(value)) {
                distinct.add(value.trim());
            }
        }
        return List.copyOf(distinct);
    }

    private String firstLine(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.lines().findFirst().orElse(value);
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String normalizeValue(String value) {
        return hasText(value) ? value : "unknown";
    }

    private String escapeInlineCode(String value) {
        return normalizeValue(value).replace('`', '\'');
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
