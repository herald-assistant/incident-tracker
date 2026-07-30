package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabNamedExactRepositoryAdapterTest {

    @Test
    void shouldReadAndLimitFilesFromTwoNamedInstancesWithEncodedTargets() {
        var fixture = fixture(5);

        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/files/backend%2Fapplication.yml.kv/raw?ref=dev1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", "token-one"))
                .andRespond(withSuccess("abcdefghij", MediaType.TEXT_PLAIN));
        fixture.server.expect(requestTo(
                        "https://config-two.example.com/api/v4/projects/team%2Fother-config/repository/files/global.var/raw?ref=zt001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", "token-two"))
                .andRespond(withSuccess("xyz", MediaType.TEXT_PLAIN));

        var first = fixture.adapter.readFile(
                "config-one",
                "platform/runtime-config",
                "dev1",
                "backend/application.yml.kv",
                20
        );
        var second = fixture.adapter.readFile(
                "config-two",
                "team/other-config",
                "zt001",
                "global.var",
                20
        );

        assertEquals("abcde", first.content());
        assertEquals(5, first.returnedCharacters());
        assertTrue(first.truncated());
        assertEquals("xyz", second.content());
        assertFalse(second.truncated());
        fixture.server.verify();
    }

    @Test
    void shouldReadFileAndCommitMetadata() {
        var fixture = fixture(100);

        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/files/backend%2Flocal.var?ref=zt001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "file_path": "backend/local.var",
                          "blob_id": "blob-1",
                          "commit_id": "commit-2",
                          "last_commit_id": "commit-1",
                          "content_sha256": "sha-1",
                          "size": 321
                        }
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/commits/commit-1?stats=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "commit-1",
                          "committed_date": "2026-07-30T08:15:30Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        var metadata = fixture.adapter.readFileMetadata(
                "config-one",
                "platform/runtime-config",
                "zt001",
                "backend/local.var"
        );

        assertEquals("blob-1", metadata.blobId());
        assertEquals("commit-2", metadata.commitId());
        assertEquals("commit-1", metadata.lastCommitId());
        assertEquals("2026-07-30T08:15:30Z", metadata.lastModifiedAt());
        assertEquals("sha-1", metadata.contentSha256());
        assertEquals(321L, metadata.sizeBytes());
        fixture.server.verify();
    }

    @Test
    void shouldReturnFalseOnlyForMissingBranch() {
        var fixture = fixture(100);

        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/branches/dev9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(fixture.adapter.branchExists("config-one", "platform/runtime-config", "dev9"));
        fixture.server.verify();
    }

    @Test
    void shouldMapUnauthorizedWithoutExposingToken() {
        var fixture = fixture(100);

        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/files/global.var/raw?ref=dev1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var exception = assertThrows(
                GitLabExactReadException.class,
                () -> fixture.adapter.readFile(
                        "config-one",
                        "platform/runtime-config",
                        "dev1",
                        "global.var",
                        100
                )
        );

        assertEquals(GitLabExactReadError.UNAUTHORIZED, exception.error());
        assertEquals(401, exception.upstreamStatus());
        assertFalse(exception.getMessage().contains("token-one"));
        fixture.server.verify();
    }

    @Test
    void shouldMapForbiddenWithoutExposingToken() {
        var fixture = fixture(100);

        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/files/global.var/raw?ref=dev1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        var exception = assertThrows(
                GitLabExactReadException.class,
                () -> fixture.adapter.readFile(
                        "config-one",
                        "platform/runtime-config",
                        "dev1",
                        "global.var",
                        100
                )
        );

        assertEquals(GitLabExactReadError.FORBIDDEN, exception.error());
        assertEquals(403, exception.upstreamStatus());
        assertFalse(exception.getMessage().contains("token-one"));
        fixture.server.verify();
    }

    @Test
    void shouldMapTransportTimeoutWithoutExposingCauseOrToken() {
        var fixture = fixture(100);

        fixture.server.expect(requestTo(
                        "https://config-one.example.com/api/v4/projects/platform%2Fruntime-config/repository/files/global.var/raw?ref=dev1"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout while using token-one");
                });

        var exception = assertThrows(
                GitLabExactReadException.class,
                () -> fixture.adapter.readFile(
                        "config-one",
                        "platform/runtime-config",
                        "dev1",
                        "global.var",
                        100
                )
        );

        assertEquals(GitLabExactReadError.UPSTREAM_FAILURE, exception.error());
        assertNull(exception.upstreamStatus());
        assertFalse(exception.getMessage().contains("timeout"));
        assertFalse(exception.getMessage().contains("token-one"));
        fixture.server.verify();
    }

    @Test
    void shouldRejectTraversalBeforeCallingGitLab() {
        var fixture = fixture(100);

        var exception = assertThrows(
                GitLabExactReadException.class,
                () -> fixture.adapter.readFile(
                        "config-one",
                        "platform/runtime-config",
                        "dev1",
                        "../global.var",
                        100
                )
        );

        assertEquals(GitLabExactReadError.INVALID_TARGET, exception.error());
        fixture.server.verify();
    }

    private static Fixture fixture(int maxFileCharacters) {
        var namedProperties = new GitLabNamedConnectionsProperties();
        namedProperties.setMaxFileCharacters(maxFileCharacters);
        var connections = new LinkedHashMap<String, GitLabNamedConnectionsProperties.Connection>();
        connections.put("config-one", connection("https://config-one.example.com/", "token-one", false));
        connections.put("config-two", connection("https://config-two.example.com", "token-two", false));
        namedProperties.setConnections(connections);

        var registry = new GitLabNamedConnectionRegistry(namedProperties);
        var legacyProperties = new GitLabProperties();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var factory = new GitLabRestClientFactory(legacyProperties, builder);
        return new Fixture(new GitLabNamedExactRepositoryAdapter(registry, factory), server);
    }

    private static GitLabNamedConnectionsProperties.Connection connection(
            String baseUrl,
            String token,
            boolean ignoreSslErrors
    ) {
        var connection = new GitLabNamedConnectionsProperties.Connection();
        connection.setBaseUrl(baseUrl);
        connection.setToken(token);
        connection.setIgnoreSslErrors(ignoreSslErrors);
        return connection;
    }

    private record Fixture(
            GitLabNamedExactRepositoryAdapter adapter,
            MockRestServiceServer server
    ) {
    }
}
