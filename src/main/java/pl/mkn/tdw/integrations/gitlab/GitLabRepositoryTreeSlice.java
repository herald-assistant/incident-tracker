package pl.mkn.tdw.integrations.gitlab;

import java.util.List;

/** A bounded navigation view. Paths are never evidence of file contents. */
public record GitLabRepositoryTreeSlice(
        String path,
        int depth,
        List<Entry> entries,
        List<Continuation> continuations,
        boolean truncated
) {
    public GitLabRepositoryTreeSlice {
        entries = List.copyOf(entries);
        continuations = List.copyOf(continuations);
    }

    public record Entry(String path, String type) {
    }

    public record Continuation(String path, String cursor) {
    }
}
