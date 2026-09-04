package pl.mkn.tdw.features.changeverification.job.export;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationComplianceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record ChangeVerificationExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {
    public static final String SCHEMA = "tdw.change-verification-export";
    public static final int VERSION = 5;
    public static final String PAYLOAD_TYPE = "change-verification-analysis";
    public static final String RESULT_CONTRACT = "change-verification-result-v4";

    public static ChangeVerificationExportEnvelope from(
            ChangeVerificationJobStateSnapshot snapshot,
            Instant exportedAt
    ) {
        return new ChangeVerificationExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(
                        PAYLOAD_TYPE,
                        RESULT_CONTRACT,
                        ChangeVerificationExportDiagnostics.from(snapshot),
                        snapshot
                )
        );
    }

    public record Payload(
            String type,
            String resultContract,
            ChangeVerificationExportDiagnostics diagnostics,
            ChangeVerificationJobStateSnapshot job
    ) {
    }

    public record ChangeVerificationExportDiagnostics(
            String resultContract,
            Target target,
            Request request,
            Result result,
            Workflow workflow,
            CopilotRuntime copilotRuntime,
            List<DiagnosticArtifactSummary> artifacts
    ) {
        static ChangeVerificationExportDiagnostics from(ChangeVerificationJobStateSnapshot snapshot) {
            return new ChangeVerificationExportDiagnostics(
                    RESULT_CONTRACT,
                    ChangeVerificationExportEnvelope.target(snapshot),
                    ChangeVerificationExportEnvelope.request(snapshot),
                    ChangeVerificationExportEnvelope.result(snapshot),
                    ChangeVerificationExportEnvelope.workflow(snapshot),
                    ChangeVerificationExportEnvelope.copilotRuntime(snapshot),
                    ChangeVerificationExportEnvelope.artifacts(snapshot)
            );
        }
    }

    public record Target(
            String issueKey,
            String issueUrl
    ) {
    }

    public record Request(
            boolean checkStoryCompliance,
            boolean checkInstructionCompliance,
            String aiModel,
            String reasoningEffort
    ) {
    }

    public record Result(
            String status,
            String complianceStatus,
            int findingCount,
            int visibilityLimitCount
    ) {
    }

    public record Workflow(
            int stepCount,
            int contextEvidenceItemCount,
            int toolEvidenceItemCount,
            int aiActivityEventCount,
            boolean usageIncluded
    ) {
    }

    public record CopilotRuntime(
            String sdkVersion,
            String cliVersion,
            Integer protocolVersion,
            String minimumCliVersion,
            boolean compatible
    ) {
    }

    public record DiagnosticArtifactSummary(
            String name,
            String kind,
            boolean included,
            Integer itemCount,
            Integer characterCount
    ) {
    }

    private static Target target(ChangeVerificationJobStateSnapshot snapshot) {
        return new Target(
                text(snapshot != null ? snapshot.issueKey() : null),
                text(snapshot != null ? snapshot.issueUrl() : null)
        );
    }

    private static Request request(ChangeVerificationJobStateSnapshot snapshot) {
        return new Request(
                snapshot != null && snapshot.checkStoryCompliance(),
                snapshot != null && snapshot.checkInstructionCompliance(),
                text(snapshot != null ? snapshot.aiModel() : null),
                text(snapshot != null ? snapshot.reasoningEffort() : null)
        );
    }

    private static Result result(ChangeVerificationJobStateSnapshot snapshot) {
        var result = snapshot != null ? snapshot.result() : null;
        var compliance = result != null ? result.compliance() : null;
        return new Result(
                text(result != null ? result.status() : snapshot != null ? snapshot.status() : null),
                text(compliance != null ? compliance.status() : null),
                compliance != null ? safeList(compliance.findings()).size() : 0,
                visibilityLimitCount(compliance)
        );
    }

    private static Workflow workflow(ChangeVerificationJobStateSnapshot snapshot) {
        return new Workflow(
                snapshot != null ? safeList(snapshot.steps()).size() : 0,
                snapshot != null ? evidenceItemCount(snapshot.contextSections()) : 0,
                snapshot != null ? evidenceItemCount(snapshot.toolEvidenceSections()) : 0,
                snapshot != null ? safeList(snapshot.aiActivityEvents()).size() : 0,
                snapshot != null && snapshot.result() != null && snapshot.result().usage() != null
        );
    }

    private static CopilotRuntime copilotRuntime(ChangeVerificationJobStateSnapshot snapshot) {
        var event = snapshot != null
                ? safeList(snapshot.aiActivityEvents()).stream()
                .filter(ChangeVerificationExportEnvelope::isCopilotRuntimeEvent)
                .reduce((first, second) -> second)
                .orElse(null)
                : null;
        if (event == null) {
            return null;
        }

        var details = event.details();
        return new CopilotRuntime(
                detailText(details.get("sdkVersion")),
                detailText(details.get("cliVersion")),
                detailInteger(details.get("protocolVersion")),
                detailText(details.get("minimumCliVersion")),
                Boolean.TRUE.equals(details.get("compatible"))
        );
    }

    private static boolean isCopilotRuntimeEvent(AnalysisAiActivityEvent event) {
        return event != null && "platform.copilot_runtime".equals(event.type());
    }

    private static List<DiagnosticArtifactSummary> artifacts(ChangeVerificationJobStateSnapshot snapshot) {
        var result = snapshot != null ? snapshot.result() : null;
        return List.of(
                new DiagnosticArtifactSummary(
                        "change-verification-result",
                        "result-json",
                        result != null,
                        result != null ? 1 : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "contextSections",
                        "workflow-evidence",
                        snapshot != null && !safeList(snapshot.contextSections()).isEmpty(),
                        snapshot != null ? evidenceItemCount(snapshot.contextSections()) : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "toolEvidenceSections",
                        "tool-evidence",
                        snapshot != null && !safeList(snapshot.toolEvidenceSections()).isEmpty(),
                        snapshot != null ? evidenceItemCount(snapshot.toolEvidenceSections()) : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "aiActivityEvents",
                        "ai-activity",
                        snapshot != null && !safeList(snapshot.aiActivityEvents()).isEmpty(),
                        snapshot != null ? safeList(snapshot.aiActivityEvents()).size() : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "analysisReport",
                        "canonical-report",
                        snapshot != null && snapshot.report() != null,
                        snapshot != null && snapshot.report() != null
                                ? safeList(snapshot.report().sections()).size()
                                : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "preparedPrompt",
                        "canonical-prompt",
                        snapshot != null && hasText(snapshot.preparedPrompt()),
                        null,
                        snapshot != null && hasText(snapshot.preparedPrompt())
                                ? snapshot.preparedPrompt().length()
                                : null
                ),
                new DiagnosticArtifactSummary(
                        "usage",
                        "token-and-cost-usage",
                        result != null && result.usage() != null,
                        result != null && result.usage() != null ? 1 : 0,
                        null
                )
        );
    }

    private static int visibilityLimitCount(ChangeVerificationComplianceResponse compliance) {
        return safeList(compliance != null ? compliance.visibilityLimits() : List.of()).size();
    }

    private static int evidenceItemCount(List<AnalysisEvidenceSection> sections) {
        return safeList(sections).stream()
                .mapToInt(section -> safeList(section.items()).size())
                .sum();
    }

    private static String text(String value) {
        return value != null ? value : "";
    }

    private static String detailText(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Integer detailInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }
}
