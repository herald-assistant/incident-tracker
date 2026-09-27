package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFilePage;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryProjectCandidate;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryRevision;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchQuery;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreePage;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryFileRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryInventory;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryInventoryRequest;

import java.util.List;

public interface GitLabRepositoryPort extends GitLabProjectSearchPort, GitLabCodeSearchPort,
        GitLabRepositoryTreePort, GitLabRepositoryReadPort, GitLabRepositoryRevisionPort,
        GitLabMergeRequestPort, GitLabInstructionRepositoryPort {

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

    /** Lists a bounded tree window without materializing the whole repository. */
    default GitLabRepositoryFilePage listRepositoryFilesPage(
            String group,
            String projectName,
            String revision,
            String pathPrefix,
            String cursor,
            int maxResults
    ) {
        throw new UnsupportedOperationException("Paged GitLab repository tree is not implemented by this port.");
    }

    /** Lists one bounded directory page, including direct child directories and files. */
    default GitLabRepositoryTreePage listRepositoryTreeChildrenPage(
            String group,
            String projectName,
            String revision,
            String directory,
            String cursor,
            int maxEntries
    ) {
        throw new UnsupportedOperationException("Paged GitLab directory tree is not implemented by this port.");
    }

    GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String filePath,
            int maxCharacters
    );

    /** Reads an entire small file with a transport-level byte limit; never returns truncated content. */
    default GitLabRepositoryFileContent readFileBounded(
            String group,
            String projectName,
            String revision,
            String filePath,
            int maxBytes
    ) {
        throw new UnsupportedOperationException("Bounded GitLab file read is not implemented by this port.");
    }

    /** Reads an entire text file without an application byte budget; callers verify revision and content. */
    default GitLabRepositoryFileContent readFileComplete(
            String group, String projectName, String revision, String filePath
    ) {
        throw new UnsupportedOperationException("Complete GitLab file read is not implemented by this port.");
    }

    default GitLabRepositoryFileMetadata readFileMetadata(
            String group,
            String projectName,
            String branch,
            String filePath
    ) {
        return null;
    }

    default GitLabRepositoryRevision resolveRevision(
            String group,
            String projectName,
            String ref
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

    default boolean refExists(String group, String projectName, String ref) {
        return branchExists(group, projectName, ref);
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
