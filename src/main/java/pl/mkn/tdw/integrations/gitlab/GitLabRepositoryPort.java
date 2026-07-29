package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryFileRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryInventory;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryInventoryRequest;

import java.util.List;

public interface GitLabRepositoryPort {

    List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints);

    List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query);

    default List<GitLabRepositoryFileCandidate> searchRepositoryFilesByContent(
            String group,
            String projectName,
            String branch,
            List<String> searchTerms,
            int maxResultsPerTerm
    ) {
        return List.of();
    }

    List<GitLabRepositoryFile> listRepositoryFiles(
            String group,
            String projectName,
            String branch,
            String pathPrefix
    );

    GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String filePath,
            int maxCharacters
    );

    default GitLabRepositoryFileMetadata readFileMetadata(
            String group,
            String projectName,
            String branch,
            String filePath
    ) {
        return null;
    }

    GitLabRepositoryFileChunk readFileChunk(
            String group,
            String projectName,
            String branch,
            String filePath,
            int startLine,
            int endLine,
            int maxCharacters
    );

    default boolean branchExists(String group, String projectName, String branch) {
        return true;
    }

    default GitLabMergeRequestSearchResult findMergeRequestsByIssueKey(
            String group,
            String issueKey,
            int maxResults
    ) {
        return new GitLabMergeRequestSearchResult(issueKey, group, List.of(), List.of("GitLab MR discovery is not implemented by this port."));
    }

    default InstructionRepositoryFile readFile(InstructionRepositoryFileRequest request) {
        return InstructionRepositoryFile.failed(
                request.repositoryKey(),
                request.ref(),
                request.path(),
                "GitLab instruction file read is not implemented by this port."
        );
    }

    default InstructionRepositoryInventory loadFileInventory(InstructionRepositoryInventoryRequest request) {
        return InstructionRepositoryInventory.unavailable(
                "GitLab repository file inventory is not implemented by this port."
        );
    }

}
