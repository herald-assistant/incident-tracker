package pl.mkn.tdw.integrations.gitlab.contract;

import java.time.Instant;

public record GitLabCommit(String id, Instant committedAt, String title) {
    public GitLabCommit {
        if (id == null || !id.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}") || committedAt == null || title == null)
            throw new IllegalArgumentException("Invalid commit metadata.");
    }
}
