package pl.mkn.tdw.integrations.gitlab;

import java.util.List;

/** One bounded repository-tree window; the cursor is valid only with the same project, ref and prefix. */
public record GitLabRepositoryFilePage(List<GitLabRepositoryFile> files, String nextCursor) {
}
