package pl.mkn.tdw.integrations.gitlab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** A bounded, complete GitLab text read that can be used as cited source material. */
public final class GitLabVerifiedRepositoryFileReader {

    public static final int MAX_FILE_BYTES = 256 * 1024;

    private GitLabVerifiedRepositoryFileReader() {
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

    public static VerifiedFile read(
            GitLabRepositoryPort port, String group, String projectName, String commitId,
            String filePath, int maxBytes
    ) {
        if (!isSafePath(filePath, false)) {
            throw new IllegalArgumentException("Only relative text file paths can be read.");
        }
        if (maxBytes <= 0 || maxBytes > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("GitLab verified read byte limit is invalid.");
        }
        var metadata = port.readFileMetadata(group, projectName, commitId, filePath);
        if (metadata == null || !group.equals(metadata.group())
                || !projectName.equals(metadata.projectName()) || !commitId.equals(metadata.branch())
                || !filePath.equals(metadata.filePath())
                || metadata.commitId() != null && !commitId.equals(metadata.commitId())
                || metadata.sizeBytes() == null || metadata.sizeBytes() < 0
                || metadata.sizeBytes() > maxBytes) {
            throw new IllegalStateException("GitLab file metadata or size cannot be verified.");
        }
        var file = port.readFileBounded(group, projectName, commitId, filePath, maxBytes);
        if (file == null || !group.equals(file.group()) || !projectName.equals(file.projectName())
                || !commitId.equals(file.branch()) || !filePath.equals(file.filePath())
                || file.content() == null || file.truncated()) {
            throw new IllegalStateException("GitLab file does not match the pinned commit or is incomplete.");
        }
        var content = file.content();
        var actualBytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (actualBytes != metadata.sizeBytes() || actualBytes > maxBytes
                || !matchesContentHash(content, metadata.contentSha256()) || !isText(content)) {
            throw new IllegalStateException("GitLab file content is incomplete, not text or differs from metadata.");
        }
        return new VerifiedFile(filePath, content, actualBytes);
    }

    private static boolean matchesContentHash(String content, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return true;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).equals(expectedHash.toLowerCase(Locale.ROOT));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static boolean isText(String content) {
        return content.chars().noneMatch(codePoint -> codePoint == 0
                || Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
    }

    public record VerifiedFile(String path, String content, int sizeBytes) {
    }
}
