package pl.mkn.tdw.features.runtimeconfigurationverification.ai.report;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationAffectedEntity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuntimeConfigurationReportFactory {

    public AnalysisReport createInitialReport(
            String reportId,
            RuntimeConfigurationVerificationMode mode,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deep
    ) {
        var sections = new ArrayList<AnalysisReportSection>();
        sections.add(section(
                RuntimeConfigurationReportSectionIds.VERIFICATION_SUMMARY,
                "Verification summary",
                sections.size(),
                summary(deterministic),
                deterministicMeta(deterministic)
        ));
        sections.add(section(
                RuntimeConfigurationReportSectionIds.DETERMINISTIC_DIFFERENCES,
                "Deterministic differences",
                sections.size(),
                differences(deterministic),
                differenceMeta(deterministic)
        ));
        sections.add(section(
                RuntimeConfigurationReportSectionIds.DETERMINISTIC_FINDINGS,
                "Deterministic findings",
                sections.size(),
                findings(deterministic),
                findingMeta(deterministic)
        ));
        sections.add(section(
                RuntimeConfigurationReportSectionIds.AI_SECOND_OPINION,
                "AI second opinion",
                sections.size(),
                "AI second opinion is being prepared.",
                AnalysisReportMeta.empty()
        ));
        sections.add(section(
                RuntimeConfigurationReportSectionIds.RECOMMENDED_HUMAN_CHECKS,
                "Recommended human checks",
                sections.size(),
                "AI recommendations are being prepared.",
                AnalysisReportMeta.empty()
        ));
        if (mode == RuntimeConfigurationVerificationMode.DEEP) {
            sections.add(section(
                    RuntimeConfigurationReportSectionIds.AFFECTED_SYSTEMS_AND_CONTEXT,
                    "Affected systems and context",
                    sections.size(),
                    affectedContext(deep),
                    deepMeta(deep)
            ));
            sections.add(section(
                    RuntimeConfigurationReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING,
                    "Functional impact and code grounding",
                    sections.size(),
                    codeGrounding(deep),
                    codeMeta(deep)
            ));
            sections.add(section(
                    RuntimeConfigurationReportSectionIds.OWNERSHIP_AND_HANDOFF,
                    "Ownership and handoff",
                    sections.size(),
                    ownership(deep),
                    deepMeta(deep)
            ));
        }
        sections.add(section(
                RuntimeConfigurationReportSectionIds.VISIBILITY_AND_GAPS,
                "Visibility and gaps",
                sections.size(),
                visibility(mode, deep),
                new AnalysisReportMeta(
                        List.of(),
                        deep != null ? deep.visibilityLimits() : List.of(),
                        List.of(),
                        List.of(),
                        "",
                        List.of()
                )
        ));

        return new AnalysisReport(
                reportId,
                "Runtime Configuration Verification",
                deterministic.sourceBranch() + " → " + deterministic.targetBranch()
                        + " · " + mode,
                "Deterministic facts and an independent AI second opinion are shown separately.",
                sections,
                deterministicMeta(deterministic)
        );
    }

    private AnalysisReportSection section(
            String id,
            String title,
            int order,
            String markdown,
            AnalysisReportMeta meta
    ) {
        return new AnalysisReportSection(id, title, order, markdown, meta);
    }

    private String summary(RuntimeConfigurationDeterministicContext context) {
        return """
                - Deterministic status: `%s`
                - System: `%s`
                - Configuration directory: `%s`
                - Differences: %d
                - Findings: %d
                """.formatted(
                context.status(),
                context.systemId(),
                context.configurationDirectory(),
                context.differences().size(),
                context.findings().size()
        ).trim();
    }

    private String differences(RuntimeConfigurationDeterministicContext context) {
        if (context.differences().isEmpty()) {
            return "No structural or effective configuration differences were detected.";
        }
        return context.differences().stream()
                .map(difference -> "- `%s` · `%s` · `%s` · `%s`".formatted(
                        difference.differenceId(),
                        difference.kind(),
                        difference.role(),
                        difference.path()
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String findings(RuntimeConfigurationDeterministicContext context) {
        if (context.findings().isEmpty()) {
            return "No deterministic findings require attention.";
        }
        return context.findings().stream()
                .map(finding -> "- `%s` · `%s` · `%s` · `%s`".formatted(
                        finding.findingId(),
                        finding.severity(),
                        finding.code(),
                        finding.path()
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String affectedContext(RuntimeConfigurationDeepContext deep) {
        if (deep == null) {
            return "DEEP context is unavailable.";
        }
        var lines = new ArrayList<String>();
        if (deep.primarySystem() != null) {
            lines.add("- Primary system: `%s` — %s".formatted(
                    deep.primarySystem().systemId(),
                    deep.primarySystem().label()
            ));
        }
        appendEntities(lines, "Affected system", deep.affectedSystems());
        appendEntities(lines, "Integration", deep.integrations());
        appendEntities(lines, "Process", deep.processes());
        appendEntities(lines, "Bounded context", deep.boundedContexts());
        return lines.isEmpty() ? "No affected operational entities were resolved." : String.join("\n", lines);
    }

    private void appendEntities(
            List<String> lines,
            String label,
            List<RuntimeConfigurationAffectedEntity> entities
    ) {
        entities.forEach(entity -> lines.add("- " + label + ": `" + entity.contextId()
                + "` — " + entity.label() + " (" + entity.confidence() + ")"));
    }

    private String codeGrounding(RuntimeConfigurationDeepContext deep) {
        if (deep == null || deep.codeGrounding().isEmpty()) {
            return "No code grounding was resolved. AI may only add a clearly labelled hypothesis.";
        }
        return deep.codeGrounding().stream()
                .map(grounding -> "- `%s` · `%s@%s:%s` · property `%s` · difference `%s`".formatted(
                        grounding.groundingId(),
                        grounding.projectPath(),
                        grounding.usedRef(),
                        grounding.filePath(),
                        grounding.matchedPropertyPath(),
                        grounding.differenceId()
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String ownership(RuntimeConfigurationDeepContext deep) {
        if (deep == null || deep.ownership() == null) {
            return "Ownership is unknown.";
        }
        var ownership = deep.ownership();
        var lines = new ArrayList<String>();
        lines.add("- Resolution: `" + ownership.situationType() + "`");
        ownership.primaryOwners().forEach(owner ->
                lines.add("- Primary owner: **" + owner.ownerLabel() + "** (`" + owner.source() + "`)"));
        ownership.partnerOwners().forEach(owner ->
                lines.add("- Partner owner: **" + owner.ownerLabel() + "** (`" + owner.source() + "`)"));
        if (ownership.handoffReason() != null) {
            lines.add("- Handoff: " + ownership.handoffReason());
        }
        return String.join("\n", lines);
    }

    private String visibility(RuntimeConfigurationVerificationMode mode, RuntimeConfigurationDeepContext deep) {
        var lines = new ArrayList<String>();
        if (mode == RuntimeConfigurationVerificationMode.BASIC) {
            lines.add("- BASIC does not use Operational Context enrichment or source code.");
        }
        if (deep != null) {
            deep.visibilityLimits().forEach(limit -> lines.add("- " + limit));
        }
        return lines.isEmpty() ? "No additional visibility gaps were reported." : String.join("\n", lines);
    }

    private AnalysisReportMeta deterministicMeta(RuntimeConfigurationDeterministicContext context) {
        var references = new ArrayList<AnalysisReportReference>();
        references.addAll(differenceMeta(context).references());
        references.addAll(findingMeta(context).references());
        return new AnalysisReportMeta(references, List.of(), List.of(), List.of(), "", List.of());
    }

    private AnalysisReportMeta differenceMeta(RuntimeConfigurationDeterministicContext context) {
        return new AnalysisReportMeta(
                context.differences().stream()
                        .map(value -> new AnalysisReportReference(
                                "difference",
                                value.differenceId(),
                                value.differenceId(),
                                value.path()
                        ))
                        .toList(),
                List.of(), List.of(), List.of(), "", List.of()
        );
    }

    private AnalysisReportMeta findingMeta(RuntimeConfigurationDeterministicContext context) {
        return new AnalysisReportMeta(
                context.findings().stream()
                        .map(value -> new AnalysisReportReference(
                                "finding",
                                value.findingId(),
                                value.findingId(),
                                value.code()
                        ))
                        .toList(),
                List.of(), List.of(), List.of(), "", List.of()
        );
    }

    private AnalysisReportMeta deepMeta(RuntimeConfigurationDeepContext deep) {
        if (deep == null) {
            return AnalysisReportMeta.empty();
        }
        var references = new ArrayList<AnalysisReportReference>();
        allAffected(deep).forEach(value -> references.add(new AnalysisReportReference(
                "operational-context",
                value.contextId(),
                value.entityId(),
                value.label()
        )));
        return new AnalysisReportMeta(
                references,
                deep.visibilityLimits(),
                List.of(),
                List.of(),
                "",
                List.of()
        );
    }

    private AnalysisReportMeta codeMeta(RuntimeConfigurationDeepContext deep) {
        if (deep == null) {
            return AnalysisReportMeta.empty();
        }
        return new AnalysisReportMeta(
                deep.codeGrounding().stream()
                        .map(value -> new AnalysisReportReference(
                                "code-grounding",
                                value.groundingId(),
                                value.projectPath() + "@" + value.usedRef() + ":" + value.filePath(),
                                value.symbol()
                        ))
                        .toList(),
                deep.visibilityLimits(),
                List.of(),
                List.of(),
                "",
                List.of()
        );
    }

    private List<RuntimeConfigurationAffectedEntity> allAffected(RuntimeConfigurationDeepContext deep) {
        var entities = new ArrayList<RuntimeConfigurationAffectedEntity>();
        entities.addAll(deep.affectedSystems());
        entities.addAll(deep.integrations());
        entities.addAll(deep.processes());
        entities.addAll(deep.boundedContexts());
        return List.copyOf(entities);
    }
}
