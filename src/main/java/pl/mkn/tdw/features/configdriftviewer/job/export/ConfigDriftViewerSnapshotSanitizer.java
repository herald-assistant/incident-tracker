package pl.mkn.tdw.features.configdriftviewer.job.export;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerComponentRunSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerResult;

import java.util.List;

public final class ConfigDriftViewerSnapshotSanitizer {

    private ConfigDriftViewerSnapshotSanitizer() {
    }

    public static ConfigDriftViewerJobStateSnapshot sanitize(
            ConfigDriftViewerJobStateSnapshot snapshot
    ) {
        if (snapshot == null) {
            return null;
        }
        return new ConfigDriftViewerJobStateSnapshot(
                snapshot.jobId(),
                snapshot.mode(),
                snapshot.repositoryId(),
                snapshot.systemIds(),
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
                snapshot.components().stream()
                        .map(ConfigDriftViewerSnapshotSanitizer::sanitize)
                        .toList(),
                snapshot.imported()
        );
    }

    private static ConfigDriftViewerComponentRunSnapshot sanitize(
            ConfigDriftViewerComponentRunSnapshot component
    ) {
        var result = component.result();
        var safeResult = result != null
                ? new ConfigDriftViewerResult(
                result.status(),
                result.mode(),
                sanitize(result.deterministicResult()),
                result.configurationDiff(),
                result.configurationDiffAnnotations(),
                result.aiSecondOpinion(),
                result.agreement(),
                result.deepAnalysis(),
                result.visibilityLimits(),
                result.prompt(),
                result.usage()
        )
                : null;
        return new ConfigDriftViewerComponentRunSnapshot(
                component.componentRunId(),
                component.systemId(),
                component.systemLabel(),
                component.configurationDirectory(),
                component.status(),
                component.currentStepCode(),
                component.currentStepLabel(),
                component.errorCode(),
                component.errorMessage(),
                component.createdAt(),
                component.updatedAt(),
                component.completedAt(),
                component.steps(),
                component.contextSections(),
                component.toolEvidenceSections(),
                component.aiActivityEvents(),
                component.preparedPrompt(),
                safeResult,
                component.report()
        );
    }

    public static ConfigDriftViewerDeterministicContext sanitize(
            ConfigDriftViewerDeterministicContext context
    ) {
        if (context == null) {
            return null;
        }
        return new ConfigDriftViewerDeterministicContext(
                context.repositoryId(),
                context.systemId(),
                context.systemLabel(),
                context.configurationDirectory(),
                context.sourceBranch(),
                context.targetBranch(),
                context.status(),
                context.sourceCoverage(),
                context.targetCoverage(),
                context.documents().stream().map(ConfigDriftViewerSnapshotSanitizer::sanitize).toList(),
                context.references(),
                context.differences().stream().map(ConfigDriftViewerSnapshotSanitizer::sanitize).toList(),
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
        var sensitive = node.sensitivity() == ConfigDriftViewerSensitivity.SENSITIVE;
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
                        .map(ConfigDriftViewerSnapshotSanitizer::sanitize)
                        .toList()
                        : List.of()
        );
    }

    private static ConfigDriftViewerDifference sanitize(ConfigDriftViewerDifference difference) {
        var sensitive = difference.sensitivity() == ConfigDriftViewerSensitivity.SENSITIVE;
        return new ConfigDriftViewerDifference(
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
