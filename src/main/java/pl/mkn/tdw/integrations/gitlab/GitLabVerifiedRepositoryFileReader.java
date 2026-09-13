package pl.mkn.tdw.integrations.gitlab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** A bounded, complete GitLab text read that can be used as cited source material. */
public final class GitLabVerifiedRepositoryFileReader {

    public static final int MAX_FILE_BYTES = 256 * 1024;

    private static final Pattern READABLE_FILE = Pattern.compile(
            "(?i).*(?:\\.(?:java|kt|kts|scala|groovy|ts|tsx|js|jsx|mjs|cjs|py|go|rs|cs|cpp|c|h|hpp|rb|php|swift|sql|md|markdown|adoc|rst|txt|xml|json|gradle)|(?:^|/)Dockerfile)$"
    );
    private static final Pattern SENSITIVE_PATH = Pattern.compile(
            "(?i)(?:^|/)(?:\\.[^/]+|[^/]*(?:secret|credential|password|passwd|token|private[-_]?key|keystore|keyring|cert|vault)[^/]*)(?:/|$)"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?im)[A-Za-z0-9_.-]*(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|signing[_-]?key|database[_-]?url|datasource[_-]?url|connection[_-]?string|authorization)"
                    + "[A-Za-z0-9_.-]*\\s*(?:[=:]|>\\s*|\\\"\\s*:)\\s*[\\\"']?[^\\s<\\\"']{2,}"
    );
    private static final Pattern SENSITIVE_URL = Pattern.compile(
            "(?i)[a-z][a-z0-9+.-]*://[^\\s/@:]+:[^\\s/@]+@"
    );
    private static final Pattern SENSITIVE_TOKEN = Pattern.compile(
            "(?i)-----BEGIN [A-Z ]*PRIVATE KEY-----|glpat-[A-Za-z0-9_-]{8,}|gh[opusr]_[A-Za-z0-9]{8,}"
                    + "|xox[baprs]-[A-Za-z0-9-]{8,}|AKIA[0-9A-Z]{16}|eyJ[A-Za-z0-9_-]{16,}\\.eyJ"
                    + "|(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/-]{16,}={0,2}"
    );

    private GitLabVerifiedRepositoryFileReader() {
    }

    public static boolean isReadablePath(String path) {
        return isSafePath(path, false) && READABLE_FILE.matcher(path).matches()
                && !SENSITIVE_PATH.matcher(path).find();
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
        if (!isReadablePath(filePath)) {
            throw new IllegalArgumentException("Only safe code and documentation files can be read.");
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
                || !matchesContentHash(content, metadata.contentSha256()) || !safeText(content)) {
            throw new IllegalStateException("GitLab file content is incomplete, unsafe or differs from metadata.");
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

    private static boolean safeText(String content) {
        if (SENSITIVE_ASSIGNMENT.matcher(content).find() || SENSITIVE_TOKEN.matcher(content).find()
                || SENSITIVE_URL.matcher(content).find()) {
            return false;
        }
        return content.chars().noneMatch(codePoint -> codePoint == 0
                || Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
    }

    public record VerifiedFile(String path, String content, int sizeBytes) {
    }
}
