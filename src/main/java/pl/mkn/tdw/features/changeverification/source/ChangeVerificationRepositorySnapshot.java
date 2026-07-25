package pl.mkn.tdw.features.changeverification.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;

import java.util.List;

public record ChangeVerificationRepositorySnapshot(
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

    public ChangeVerificationRepositorySnapshot {
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        changedFiles = changedFiles != null ? List.copyOf(changedFiles) : List.of();
        instructionSources = instructionSources != null ? List.copyOf(instructionSources) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
