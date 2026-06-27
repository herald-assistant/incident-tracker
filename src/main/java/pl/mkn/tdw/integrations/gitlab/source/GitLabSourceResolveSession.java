package pl.mkn.tdw.integrations.gitlab.source;

import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeSession;

public final class GitLabSourceResolveSession {

    private final GitLabRepositoryTreeSession repositoryTreeSession;

    public GitLabSourceResolveSession() {
        this(new GitLabRepositoryTreeSession());
    }

    GitLabSourceResolveSession(GitLabRepositoryTreeSession repositoryTreeSession) {
        this.repositoryTreeSession = repositoryTreeSession != null
                ? repositoryTreeSession
                : new GitLabRepositoryTreeSession();
    }

    GitLabRepositoryTreeSession repositoryTreeSession() {
        return repositoryTreeSession;
    }
}
