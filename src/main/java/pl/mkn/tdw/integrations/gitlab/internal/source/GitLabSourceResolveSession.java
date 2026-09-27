package pl.mkn.tdw.integrations.gitlab.internal.source;

import pl.mkn.tdw.integrations.gitlab.GitLabSourceResolveSessionPort;

import pl.mkn.tdw.integrations.gitlab.internal.GitLabRepositoryTreeSession;

public final class GitLabSourceResolveSession implements GitLabSourceResolveSessionPort {

    private final GitLabRepositoryTreeSession repositoryTreeSession;

    public GitLabSourceResolveSession() {
        this(new GitLabRepositoryTreeSession());
    }

    public GitLabSourceResolveSession(GitLabRepositoryTreeSession repositoryTreeSession) {
        this.repositoryTreeSession = repositoryTreeSession != null
                ? repositoryTreeSession
                : new GitLabRepositoryTreeSession();
    }

    public GitLabRepositoryTreeSession repositoryTreeSession() {
        return repositoryTreeSession;
    }
}
