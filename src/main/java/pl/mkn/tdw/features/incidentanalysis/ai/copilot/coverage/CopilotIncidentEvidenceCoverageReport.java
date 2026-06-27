package pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage;

import java.util.List;

public record CopilotIncidentEvidenceCoverageReport(
        IncidentElasticEvidenceCoverage elastic,
        IncidentGitLabEvidenceCoverage gitLab,
        IncidentRuntimeEvidenceCoverage runtime,
        IncidentOperationalContextCoverage operationalContext,
        IncidentDataDiagnosticNeed dataDiagnosticNeed,
        boolean environmentResolved,
        List<IncidentEvidenceGap> gaps
) {

    public CopilotIncidentEvidenceCoverageReport {
        elastic = elastic != null ? elastic : IncidentElasticEvidenceCoverage.NONE;
        gitLab = gitLab != null ? gitLab : IncidentGitLabEvidenceCoverage.NONE;
        runtime = runtime != null ? runtime : IncidentRuntimeEvidenceCoverage.NONE;
        operationalContext = operationalContext != null ? operationalContext : IncidentOperationalContextCoverage.NONE;
        dataDiagnosticNeed = dataDiagnosticNeed != null ? dataDiagnosticNeed : IncidentDataDiagnosticNeed.NONE;
        gaps = gaps != null ? List.copyOf(gaps) : List.of();
    }

    public static CopilotIncidentEvidenceCoverageReport empty() {
        return new CopilotIncidentEvidenceCoverageReport(
                IncidentElasticEvidenceCoverage.NONE,
                IncidentGitLabEvidenceCoverage.NONE,
                IncidentRuntimeEvidenceCoverage.NONE,
                IncidentOperationalContextCoverage.NONE,
                IncidentDataDiagnosticNeed.NONE,
                false,
                List.of()
        );
    }

    public boolean hasGap(String code) {
        return code != null && gaps.stream().anyMatch(gap -> gap != null && code.equals(gap.code()));
    }

    public boolean elasticNeedsTooling() {
        return elastic == IncidentElasticEvidenceCoverage.NONE
                || elastic == IncidentElasticEvidenceCoverage.TRUNCATED
                || elastic == IncidentElasticEvidenceCoverage.EXCEPTION_PRESENT
                || hasGap("MISSING_LOGS")
                || hasGap("MISSING_STACKTRACE")
                || hasGap("TRUNCATED_LOGS");
    }

    public boolean gitLabNeedsTooling() {
        return gitLab == IncidentGitLabEvidenceCoverage.NONE
                || gitLab == IncidentGitLabEvidenceCoverage.SYMBOL_ONLY
                || gitLab == IncidentGitLabEvidenceCoverage.STACK_FRAME_ONLY
                || gitLab == IncidentGitLabEvidenceCoverage.FAILING_METHOD_ONLY
                || gitLab == IncidentGitLabEvidenceCoverage.DIRECT_COLLABORATOR_ATTACHED
                || hasGap("MISSING_CODE_CONTEXT")
                || hasGap("MISSING_FLOW_CONTEXT")
                || technicalAnalysisGitLabRecommended();
    }

    public boolean databaseNeedsTooling() {
        return environmentResolved
                && (dataDiagnosticNeed == IncidentDataDiagnosticNeed.LIKELY
                || dataDiagnosticNeed == IncidentDataDiagnosticNeed.REQUIRED);
    }

    public boolean databaseDiscoveryOnly() {
        return environmentResolved && dataDiagnosticNeed == IncidentDataDiagnosticNeed.POSSIBLE;
    }

    public boolean databaseCodeGroundingNeedsTooling() {
        return hasGap("DB_CODE_GROUNDING_NEEDED");
    }

    public boolean technicalAnalysisGitLabRecommended() {
        return hasGap("TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED");
    }
}
