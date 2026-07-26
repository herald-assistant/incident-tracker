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
        List<GitLabMergeRequest> mergeRequests,
        List<ChangeVerificationChangedFileSnapshot> changedFiles,
        List<InstructionSource> instructionSources,
        List<ChangeVerificationOperationalContextMatch> operationalContextMatches,
        List<String> limitations
) {

    public ChangeVerificationRepositorySnapshot {
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
                mergeRequests,
                changedFiles,
                instructionSources,
                List.of(),
                limitations
        );
    }

    public ChangeVerificationRepositorySnapshot withOperationalContextMatches(
            List<ChangeVerificationOperationalContextMatch> operationalContextMatches
    ) {
        return new  ChangeVerificationRepositorySnapshot(
                repositoryKey,
                projectPath,
                rootGroup,
                groupPath,
                repositoryName,
                projectName,
                sourceRef,
                targetRef,
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
}
