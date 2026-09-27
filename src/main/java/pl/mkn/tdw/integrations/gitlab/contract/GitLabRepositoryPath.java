package pl.mkn.tdw.integrations.gitlab.contract;

/** Validation shared by repository reads and their callers. */
public final class GitLabRepositoryPath {

    private GitLabRepositoryPath() {
    }

    public static boolean isSafePath(String raw, boolean allowRoot) {
        if (allowRoot && (raw == null || raw.isBlank())) {
            return true;
        }
        if (raw == null || raw.isBlank() || raw.length() > 1_024 || !raw.equals(raw.trim())
                || raw.startsWith("/") || raw.endsWith("/") || raw.contains("//")
                || raw.contains("\\") || raw.contains("?") || raw.contains("#")
                || raw.contains("%") || raw.contains(":") || raw.contains("@{")) {
            return false;
        }
        for (var segment : raw.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return raw.chars().noneMatch(Character::isISOControl);
    }
}
