package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.testsupport.integrations.GitLabIntegrationTestCreator;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitLabMergeRequestDiffAdapterTest {
    private static final String URL = "https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer-api/merge_requests/7/versions";

    @Test
    void readsOneImmutableVersionWithDistinctBaseAndTargetTipAndExplicitFileLimits() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com/");
        properties.setMaxMergeRequestChangedFiles(2);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties,
                new GitLabRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
        server.expect(requestTo(URL + "?per_page=1&page=1"))
                .andRespond(withSuccess("[{\"id\":11}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/11")).andRespond(withSuccess("""
                {"id":11,"state":"overflow","base_commit_sha":"%s","start_commit_sha":"%s","head_commit_sha":"%s",
                 "diffs":[
                 {"old_path":"Old.java","new_path":"New.java","renamed_file":true,"diff":"-old\\n+new"},
                 {"old_path":"Deleted.java","new_path":"Deleted.java","deleted_file":true,"too_large":true,"diff":""},
                 {"old_path":"logo.png","new_path":"logo.png","collapsed":true,"diff":""}]}
                """.formatted("a".repeat(40), "b".repeat(40), "c".repeat(40)), MediaType.APPLICATION_JSON));

        var snapshot = adapter.readMergeRequestDiff("CRM", "customer-api", 7);

        assertThat(snapshot.hasRevisions()).isTrue();
        assertThat(snapshot.versionId()).isEqualTo(11L);
        assertThat(snapshot.baseSha()).isNotEqualTo(snapshot.startSha());
        assertThat(snapshot.files()).hasSize(2);
        assertThat(snapshot.files().get(0).renamedFile()).isTrue();
        assertThat(snapshot.files().get(1).deletedFile()).isTrue();
        assertThat(snapshot.files().get(1).tooLarge()).isTrue();
        assertThat(snapshot.limitations()).anyMatch(l -> l.contains("overflow")).anyMatch(l -> l.contains("limit"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void unavailableVersionIsAnExplicitLimitWithoutBranchFallback(int status) {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties,
                new GitLabRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
        server.expect(requestTo(URL + "?per_page=1&page=1")).andRespond(withStatus(HttpStatus.valueOf(status)));
        var snapshot = adapter.readMergeRequestDiff("CRM", "customer-api", 7);
        assertThat(snapshot.hasRevisions()).isFalse();
        assertThat(snapshot.files()).isEmpty();
        assertThat(snapshot.limitations()).anyMatch(l -> l.contains("HTTP " + status));
        server.verify();
    }

    @Test
    void emptyVersionsAreNotTreatedAsNoChange() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties,
                new GitLabRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
        server.expect(requestTo(URL + "?per_page=1&page=1")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        var snapshot = adapter.readMergeRequestDiff("CRM", "customer-api", 7);
        assertThat(snapshot.hasRevisions()).isFalse();
        assertThat(snapshot.limitations()).isNotEmpty();
        server.verify();
    }
}
