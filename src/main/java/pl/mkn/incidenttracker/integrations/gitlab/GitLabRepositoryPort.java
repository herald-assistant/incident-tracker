package pl.mkn.incidenttracker.integrations.gitlab;

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

}
