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
    void rejectsSensitiveTextAndMismatchedRevisionWithoutCitableContent() {
        var content = "apiToken = exposed-value";
        stub("package.json", content, COMMIT, (long) content.length());
        assertThatThrownBy(() -> GitLabVerifiedRepositoryFileReader.read(
                port, "CRM", "lib", COMMIT, "package.json", 1024))
                .isInstanceOf(IllegalStateException.class);

        var other = "<project />";
        stub("pom.xml", other, "abcdef1234567890abcdef1234567890abcdef12", (long) other.length());
        assertThatThrownBy(() -> GitLabVerifiedRepositoryFileReader.read(
                port, "CRM", "lib", COMMIT, "pom.xml", 1024))
                .isInstanceOf(IllegalStateException.class);
        assertThat(GitLabVerifiedRepositoryFileReader.isReadablePath(".env")).isFalse();
        assertThat(GitLabVerifiedRepositoryFileReader.isReadablePath("config/secrets.xml")).isFalse();
    }

    private void stub(String path, String content, String revision, Long sizeBytes) {
        when(port.readFileMetadata("CRM", "lib", COMMIT, path))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "lib", revision, path,
                        null, revision, null, null, null, sizeBytes));
        when(port.readFileBounded("CRM", "lib", COMMIT, path, 1024))
                .thenReturn(new GitLabRepositoryFileContent("CRM", "lib", revision, path, content, false));
    }
}
