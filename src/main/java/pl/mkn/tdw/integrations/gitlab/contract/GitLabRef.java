package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.Objects;

public record GitLabRef(Kind kind, String name) {
    public enum Kind { BRANCH, TAG, COMMIT }
    public GitLabRef {
        Objects.requireNonNull(kind, "Ref kind is required");
        if (name == null || name.isBlank() || name.length() > 255)
            throw new IllegalArgumentException("Ref name is required and must not exceed 255 characters");
        if (kind == Kind.COMMIT) {
            if (!name.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))
                throw new IllegalArgumentException("Commit must be a full SHA");
        } else if (name.startsWith("/") || name.endsWith("/") || name.endsWith(".")
                || name.contains("..") || name.contains("//") || name.contains("@{") || name.equals("@")
                || name.chars().anyMatch(c -> c <= 32 || c == 127 || c == 92 || "~^:?*[".indexOf(c) >= 0)
                || java.util.Arrays.stream(name.split("/")).anyMatch(p -> p.startsWith(".") || p.endsWith(".lock"))) {
            throw new IllegalArgumentException("Invalid Git ref");
        }
    }
    public String canonical() {
        return switch (kind) {
            case BRANCH -> "refs/heads/" + name;
            case TAG -> "refs/tags/" + name;
            case COMMIT -> name.toLowerCase(java.util.Locale.ROOT);
        };
    }
}

