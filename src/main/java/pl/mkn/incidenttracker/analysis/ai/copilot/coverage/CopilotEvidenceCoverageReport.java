package pl.mkn.incidenttracker.analysis.ai.copilot.coverage;

import java.util.List;

public record CopilotEvidenceCoverageReport(
        ElasticEvidenceCoverage elastic,
        GitLabEvidenceCoverage gitLab,
        RuntimeEvidenceCoverage runtime,
        OperationalContextCoverage operationalContext,
        DataDiagnosticNeed dataDiagnosticNeed,
        boolean environmentResolved,
        List<EvidenceGap> gaps
) {

    public CopilotEvidenceCoverageReport {
        elastic = elastic != null ? elastic : ElasticEvidenceCoverage.NONE;
        gitLab = gitLab != null ? gitLab : GitLabEvidenceCoverage.NONE;
        runtime = runtime != null ? runtime : RuntimeEvidenceCoverage.NONE;
        operationalContext = operationalContext != null ? operationalContext : OperationalContextCoverage.NONE;
        dataDiagnosticNeed = dataDiagnosticNeed != null ? dataDiagnosticNeed : DataDiagnosticNeed.NONE;
        gaps = gaps != null ? List.copyOf(gaps) : List.of();
    }

    public static CopilotEvidenceCoverageReport empty() {
        return new CopilotEvidenceCoverageReport(
                ElasticEvidenceCoverage.NONE,
                GitLabEvidenceCoverage.NONE,
                RuntimeEvidenceCoverage.NONE,
                OperationalContextCoverage.NONE,
                DataDiagnosticNeed.NONE,
                false,
                List.of()
        );
    }

    public boolean hasGap(String code) {
        return code != null && gaps.stream().anyMatch(gap -> gap != null && code.equals(gap.code()));
    }

    public boolean elasticNeedsTooling() {
        return elastic == ElasticEvidenceCoverage.NONE
                || elastic == ElasticEvidenceCoverage.TRUNCATED
                || elastic == ElasticEvidenceCoverage.EXCEPTION_PRESENT
                || hasGap("MISSING_LOGS")
                || hasGap("MISSING_STACKTRACE")
                || hasGap("TRUNCATED_LOGS");
    }

    public boolean gitLabNeedsTooling() {
        return gitLab == GitLabEvidenceCoverage.NONE
                || gitLab == GitLabEvidenceCoverage.SYMBOL_ONLY
                || gitLab == GitLabEvidenceCoverage.STACK_FRAME_ONLY
                || gitLab == GitLabEvidenceCoverage.FAILING_METHOD_ONLY
                || gitLab == GitLabEvidenceCoverage.DIRECT_COLLABORATOR_ATTACHED
                || hasGap("MISSING_CODE_CONTEXT")
                || hasGap("MISSING_FLOW_CONTEXT");
    }

    public boolean databaseNeedsTooling() {
        return environmentResolved
                && (dataDiagnosticNeed == DataDiagnosticNeed.LIKELY
                || dataDiagnosticNeed == DataDiagnosticNeed.REQUIRED);
    }

    public boolean databaseDiscoveryOnly() {
        return environmentResolved && dataDiagnosticNeed == DataDiagnosticNeed.POSSIBLE;
    }
}
