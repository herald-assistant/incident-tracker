package pl.mkn.tdw.features.changeverification.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;

import java.util.List;

public record ChangeVerificationRepositorySnapshot(
        String repositoryKey,
        String projectPath,
        String rootGroup,
        String groupPath,
        String repositoryName,
        String projectName,
        String sourceRef,
        String targetRef,
        String analysisRef,
        String analysisRefSource,
        Boolean sourceRefAvailable,
        Boolean targetRefAvailable,
        List<GitLabMergeRequest> mergeRequests,
        List<ChangeVerificationChangedFileSnapshot> changedFiles,
        List<InstructionSource> instructionSources,
        List<ChangeVerificationOperationalContextMatch> operationalContextMatches,
        List<String> limitations
) {

    public ChangeVerificationRepositorySnapshot {
        analysisRef = normalize(analysisRef).isEmpty() ? ref(sourceRef, targetRef) : normalize(analysisRef);
        analysisRefSource = normalize(analysisRefSource).isEmpty()
                ? refSource(analysisRef, sourceRef, targetRef)
                : normalize(analysisRefSource);
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        changedFiles = changedFiles != null ? List.copyOf(changedFiles) : List.of();
        instructionSources = instructionSources != null ? List.copyOf(instructionSources) : List.of();
        operationalContextMatches = operationalContextMatches != null ? List.copyOf(operationalContextMatches) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public ChangeVerificationRepositorySnapshot(
            String repositoryKey,
            String projectPath,
            String projectName,
            String sourceRef,
            String targetRef,
            List<GitLabMergeRequest> mergeRequests,
            List<ChangeVerificationChangedFileSnapshot> changedFiles,
            List<InstructionSource> instructionSources,
            List<String> limitations
    ) {
        this(
                repositoryKey,
                projectPath,
                rootGroup(projectPath),
                groupPath(projectPath),
                repositoryName(projectPath),
                projectName,
                sourceRef,
                targetRef,
                ref(sourceRef, targetRef),
                refSource(ref(sourceRef, targetRef), sourceRef, targetRef),
                null,
                null,
                mergeRequests,
                changedFiles,
                instructionSources,
                List.of(),
                limitations
        );
    }

    public ChangeVerificationRepositorySnapshot withRefSelection(
            ChangeVerificationRepositoryRefSelection refSelection
    ) {
        if (refSelection == null) {
            return this;
        }
        var mergedLimitations = new java.util.ArrayList<>(limitations);
        refSelection.limitations().stream()
                .filter(org.springframework.util.StringUtils::hasText)
                .filter(limitation -> !mergedLimitations.contains(limitation))
                .forEach(mergedLimitations::add);
        return new ChangeVerificationRepositorySnapshot(
                repositoryKey,
                projectPath,
                rootGroup,
                groupPath,
                repositoryName,
                projectName,
                sourceRef,
                targetRef,
                refSelection.analysisRef(),
                refSelection.analysisRefSource(),
                refSelection.sourceRefAvailable(),
                refSelection.targetRefAvailable(),
                mergeRequests,
                changedFiles,
                instructionSources,
                operationalContextMatches,
                mergedLimitations
        );
    }

    public ChangeVerificationRepositorySnapshot withOperationalContextMatches(
            List<ChangeVerificationOperationalContextMatch> operationalContextMatches
    ) {
        return new ChangeVerificationRepositorySnapshot(
                repositoryKey,
                projectPath,
                rootGroup,
                groupPath,
                repositoryName,
                projectName,
                sourceRef,
                targetRef,
                analysisRef,
                analysisRefSource,
                sourceRefAvailable,
                targetRefAvailable,
                mergeRequests,
                changedFiles,
                instructionSources,
                operationalContextMatches,
                limitations
        );
    }

    private static String rootGroup(String projectPath) {
        var normalized = normalize(projectPath);
        var index = normalized.indexOf('/');
        return index > 0 ? normalized.substring(0, index) : "";
    }

    private static String groupPath(String projectPath) {
        var normalized = normalize(projectPath);
        var index = normalized.lastIndexOf('/');
        return index > 0 ? normalized.substring(0, index) : "";
    }

    private static String repositoryName(String projectPath) {
        var normalized = normalize(projectPath);
        var index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static String normalize(String value) {
        return value != null ? value.trim().replace('\\', '/') : "";
    }

    private static String ref(String sourceRef, String targetRef) {
        return !normalize(sourceRef).isEmpty() ? normalize(sourceRef) : normalize(targetRef);
    }

    private static String refSource(String analysisRef, String sourceRef, String targetRef) {
        if (!normalize(analysisRef).isEmpty() && normalize(analysisRef).equals(normalize(sourceRef))) {
            return ChangeVerificationRepositoryRefSelection.SOURCE_REF;
        }
        if (!normalize(analysisRef).isEmpty() && normalize(analysisRef).equals(normalize(targetRef))) {
            return ChangeVerificationRepositoryRefSelection.TARGET_REF;
        }
        return ChangeVerificationRepositoryRefSelection.UNRESOLVED;
    }
}
