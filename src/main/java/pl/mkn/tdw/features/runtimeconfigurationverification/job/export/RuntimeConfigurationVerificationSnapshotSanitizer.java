package pl.mkn.tdw.features.runtimeconfigurationverification.job.export;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationResult;

import java.util.List;

public final class RuntimeConfigurationVerificationSnapshotSanitizer {

    private RuntimeConfigurationVerificationSnapshotSanitizer() {
    }

    public static RuntimeConfigurationVerificationJobStateSnapshot sanitize(
            RuntimeConfigurationVerificationJobStateSnapshot snapshot
    ) {
        if (snapshot == null) {
            return null;
        }
        var result = snapshot.result();
        var safeResult = result != null
                ? new RuntimeConfigurationVerificationResult(
                result.status(),
                result.mode(),
                sanitize(result.deterministicResult()),
                result.aiSecondOpinion(),
                result.agreement(),
                result.deepAnalysis(),
                result.visibilityLimits(),
                result.prompt(),
                result.usage()
        )
                : null;
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                snapshot.jobId(),
                snapshot.mode(),
                snapshot.repositoryId(),
                snapshot.systemId(),
                snapshot.sourceBranch(),
                snapshot.targetBranch(),
                snapshot.codeRef(),
                snapshot.aiModel(),
                snapshot.reasoningEffort(),
                snapshot.status(),
                snapshot.currentStepCode(),
                snapshot.currentStepLabel(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt(),
                snapshot.steps(),
                snapshot.contextSections(),
                snapshot.toolEvidenceSections(),
                snapshot.aiActivityEvents(),
                snapshot.preparedPrompt(),
                safeResult,
                snapshot.report(),
                snapshot.imported()
        );
    }

    public static RuntimeConfigurationDeterministicContext sanitize(
            RuntimeConfigurationDeterministicContext context
    ) {
        if (context == null) {
            return null;
        }
        return new RuntimeConfigurationDeterministicContext(
                context.repositoryId(),
                context.systemId(),
                context.systemLabel(),
                context.configurationDirectory(),
                context.sourceBranch(),
                context.targetBranch(),
                context.status(),
                context.sourceCoverage(),
                context.targetCoverage(),
                context.documents().stream().map(RuntimeConfigurationVerificationSnapshotSanitizer::sanitize).toList(),
                context.references(),
                context.differences().stream().map(RuntimeConfigurationVerificationSnapshotSanitizer::sanitize).toList(),
                context.findings()
        );
    }

    private static SanitizedConfigurationDocument sanitize(SanitizedConfigurationDocument document) {
        return new SanitizedConfigurationDocument(
                document.role(),
                document.sourcePath(),
                document.targetPath(),
                document.documentIndex(),
                document.sourcePresent(),
                document.targetPresent(),
                document.sourceProfileToken(),
                document.targetProfileToken(),
                sanitize(document.root())
        );
    }

    private static SanitizedConfigurationNode sanitize(SanitizedConfigurationNode node) {
        if (node == null) {
            return null;
        }
        var sensitive = node.sensitivity() == RuntimeConfigurationSensitivity.SENSITIVE;
        return new SanitizedConfigurationNode(
                node.name(),
                node.path(),
                node.sourceType(),
                node.targetType(),
                node.relation(),
                node.sensitivity(),
                sensitive ? null : node.sourceValueToken(),
                sensitive ? null : node.targetValueToken(),
                node.sourceCardinality(),
                node.targetCardinality(),
                node.children() != null
                        ? node.children().stream()
                        .map(RuntimeConfigurationVerificationSnapshotSanitizer::sanitize)
                        .toList()
                        : List.of()
        );
    }

    private static RuntimeConfigurationDifference sanitize(RuntimeConfigurationDifference difference) {
        var sensitive = difference.sensitivity() == RuntimeConfigurationSensitivity.SENSITIVE;
        return new RuntimeConfigurationDifference(
                difference.differenceId(),
                difference.role(),
                difference.documentIndex(),
                difference.path(),
                difference.kind(),
                difference.sourceType(),
                difference.targetType(),
                difference.sensitivity(),
                sensitive ? null : difference.sourceValueToken(),
                sensitive ? null : difference.targetValueToken()
        );
    }
}
