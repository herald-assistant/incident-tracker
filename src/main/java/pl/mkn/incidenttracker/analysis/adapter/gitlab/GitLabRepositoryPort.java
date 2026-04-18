package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import java.util.List;

public interface GitLabRepositoryPort {

    List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints);

    List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query);

    GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String filePath,
            int maxCharacters
    );

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
