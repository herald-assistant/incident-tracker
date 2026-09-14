package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabVerifiedRepositoryFileReaderTest {

    private static final String COMMIT = "1234567890abcdef1234567890abcdef12345678";
    private final GitLabRepositoryPort port = mock(GitLabRepositoryPort.class);

    @Test
    void acceptsCompletePomAtExactCommit() {
        var content = "<project><artifactId>shared-client</artifactId></project>";
        stub("pom.xml", content, COMMIT, (long) content.length());

        var file = GitLabVerifiedRepositoryFileReader.read(port, "CRM", "lib", COMMIT,
                "pom.xml", 1024);

        assertThat(file.content()).isEqualTo(content);
        assertThat(file.sizeBytes()).isEqualTo(content.length());
    }

    @Test
    void includesTextRegardlessOfPathOrContentKeywords() {
        var content = "apiToken = fictional-example";
        stub(".env", content, COMMIT, (long) content.length());
        var file = GitLabVerifiedRepositoryFileReader.read(port, "CRM", "lib", COMMIT, ".env", 1024);

        assertThat(file.content()).isEqualTo(content);
        assertThat(GitLabVerifiedRepositoryFileReader.isSafePath("config/secrets.xml", false)).isTrue();
        assertThat(GitLabVerifiedRepositoryFileReader.isSafePath(".github/copilot-instructions.md", false)).isTrue();
        assertThat(GitLabVerifiedRepositoryFileReader.isSafePath("keys/example.pem", false)).isTrue();
        assertThat(GitLabVerifiedRepositoryFileReader.isSafePath("../outside.xml", false)).isFalse();
    }

    @Test
    void rejectsMismatchedRevisionWithoutCitableContent() {
        var other = "<project />";
        stub("pom.xml", other, "abcdef1234567890abcdef1234567890abcdef12", (long) other.length());
        assertThatThrownBy(() -> GitLabVerifiedRepositoryFileReader.read(
                port, "CRM", "lib", COMMIT, "pom.xml", 1024))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonTextBodyDespiteValidPathAndRevision() {
        var binary = "CRM\u0000data";
        stub("artifacts/sample.bin", binary, COMMIT, (long) binary.length());

        assertThatThrownBy(() -> GitLabVerifiedRepositoryFileReader.read(
                port, "CRM", "lib", COMMIT, "artifacts/sample.bin", 1024))
                .isInstanceOf(IllegalStateException.class);
    }

    private void stub(String path, String content, String revision, Long sizeBytes) {
        when(port.readFileMetadata("CRM", "lib", COMMIT, path))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "lib", revision, path,
                        null, revision, null, null, null, sizeBytes));
        when(port.readFileBounded("CRM", "lib", COMMIT, path, 1024))
                .thenReturn(new GitLabRepositoryFileContent("CRM", "lib", revision, path, content, false));
    }
}
