package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.gitlab.adapter.rest.GitLabRestCommitHistoryAdapter;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitLabCommitHistoryTest {
    private static final String HEAD = "c".repeat(40), SHA = "a".repeat(40), PARENT = "b".repeat(40);
    private static final String BASE = "https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/commits";
    private static final Instant DATE = Instant.parse("2026-10-01T00:00:00Z");

    @Test void pinsHistoryEncodesFiltersAndKeepsCommitterDistinctFromAuthor() {
        var f = new Fixture();
        f.server.expect(requestTo(listUrl(1) + "&path=src%2Fcontact%20%26%20phone.java&since=2026-10-01T00%3A00%3A00Z&until=2026-10-01T00%3A00%3A00Z"))
                .andRespond(withSuccess("[" + commit("\"committer_name\":\"CRM Integrator\",\"committer_email\":\"integrator@example.com\",") + "]", MediaType.APPLICATION_JSON)
                        .header("X-Next-Page", "2"));
        var page = f.adapter.listCommits("CRM", "customer", HEAD, "src/contact & phone.java", DATE, DATE, 1);
        assertThat(page.nextPage()).isEqualTo(2);
        var commit = page.commits().get(0);
        assertThat(commit.committerName()).isEqualTo("CRM Integrator");
        assertThat(commit.committerEmail()).isEqualTo("integrator@example.com");
        assertThat(commit.committedAt()).isEqualTo(DATE);
        assertThat(commit.parentIds()).containsExactly(PARENT);
        f.server.verify();
    }

    @Test void absentPartialAndBlankCommitterMetadataNeverFallsBackToAuthor() {
        for (var metadata : new String[]{"", "\"committer_name\":\" \",\"committer_email\":null,", "\"committer_name\":\"CRM Integrator\","}) {
            var f = new Fixture();
            f.server.expect(requestTo(listUrl(1))).andRespond(withSuccess("[" + commit(metadata) + "]", MediaType.APPLICATION_JSON));
            var result = f.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 1);
            assertThat(result.commits().get(0).committerEmail()).isNull();
            assertThat(result.commits().get(0).committerName()).isEqualTo(metadata.contains("CRM Integrator") ? "CRM Integrator" : null);
            assertThat(result.nextPage()).isNull();
            f.server.verify();
        }
    }

    @Test void missingPaginationHeaderRequiresExtraReadForFullPageAndMalformedPagesFail() {
        var f = new Fixture();
        var body = IntStream.range(0, 20).mapToObj(i -> commit("")).collect(Collectors.joining(",", "[", "]"));
        f.server.expect(requestTo(listUrl(1))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        f.server.expect(requestTo(listUrl(2))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(f.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 1).nextPage()).isEqualTo(2);
        assertThat(f.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 2).nextPage()).isNull();
        f.server.verify();
        for (var invalid : new String[]{"null", "[null]", "[{\"id\":\"short\"}]"}) {
            var broken = new Fixture();
            broken.server.expect(requestTo(listUrl(1))).andRespond(withSuccess(invalid, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> broken.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 1)).isInstanceOf(RuntimeException.class);
            broken.server.verify();
        }
        var badHeader = new Fixture();
        badHeader.server.expect(requestTo(listUrl(1))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON).header("X-Next-Page", "1"));
        assertThatThrownBy(() -> badHeader.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test void preservesRenameDeleteAndMissingPatchInsteadOfInventingCompleteDiff() {
        var f = new Fixture();
        f.server.expect(requestTo(BASE + "/" + SHA + "/diff?per_page=20&page=1")).andRespond(withSuccess("""
                [{"old_path":"src/Old.java","new_path":"src/Contact.java","renamed_file":true,"diff":"-old\\n+new"},
                 {"old_path":"src/Removed.java","new_path":"src/Removed.java","deleted_file":true,"collapsed":true,"too_large":true}]
                """, MediaType.APPLICATION_JSON));
        var diff = f.adapter.readDiff("CRM", "customer", SHA, 1);
        assertThat(diff.files().get(0).renamedFile()).isTrue();
        assertThat(diff.files().get(1).deletedFile()).isTrue();
        assertThat(diff.files().get(1).tooLarge()).isTrue();
        assertThat(diff.files().get(1).patch()).isNull();
        f.server.verify();
    }

    @Test void doesNotTreatForbiddenOrMissingAsEmptyAndRetriesRateLimit() {
        for (var status : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND}) {
            var f = new Fixture();
            f.server.expect(requestTo(listUrl(1))).andRespond(withStatus(status));
            assertThatThrownBy(() -> f.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 1)).isInstanceOf(RuntimeException.class);
            f.server.verify();
        }
        var f = new Fixture();
        f.server.expect(requestTo(listUrl(1))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        f.server.expect(requestTo(listUrl(1))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(f.adapter.listCommits("CRM", "customer", HEAD, null, null, null, 1).commits()).isEmpty();
        assertThatThrownBy(() -> f.adapter.listCommits("CRM", "customer", "main", null, null, null, 1)).isInstanceOf(IllegalArgumentException.class);
        f.server.verify();
    }

    private static String listUrl(int page) { return BASE + "?ref_name=" + HEAD + "&order=default&per_page=20&page=" + page; }
    private static String commit(String metadata) {
        return "{" + metadata + "\"id\":\"" + SHA + "\",\"parent_ids\":[\"" + PARENT + "\"],\"committed_date\":\"" + DATE
                + "\",\"author_name\":\"CRM Author\",\"author_email\":\"author@example.com\",\"title\":\"Walidacja telefonu klienta\"}";
    }
    private static class Fixture {
        final MockRestServiceServer server;
        final GitLabRestCommitHistoryAdapter adapter;
        Fixture() {
            var builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            var properties = new GitLabProperties(); properties.setBaseUrl("https://gitlab.example.com");
            adapter = new GitLabRestCommitHistoryAdapter(properties, new GitLabRestClientFactory(properties,
                    IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
        }
    }
}
