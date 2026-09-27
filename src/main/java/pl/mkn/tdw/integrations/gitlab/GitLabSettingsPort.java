package pl.mkn.tdw.integrations.gitlab;

public interface GitLabSettingsPort {

    String getBaseUrl();

    String getGroup();

    String getToken();

    int getSearchResultsPerTerm();

    int getMaxCandidateCount();

    int getMaxMergeRequests();

    int getMaxMergeRequestCommits();

    int getMaxMergeRequestChangedFiles();
}
