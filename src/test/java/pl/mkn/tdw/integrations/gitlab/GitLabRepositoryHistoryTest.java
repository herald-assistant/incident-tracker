package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.gitlab.adapter.rest.GitLabRestRepositoryHistoryAdapter;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitLabRepositoryHistoryTest {
    private static final String HEAD="c".repeat(40), SHA="a".repeat(40);
    private static final Instant BOUNDARY=Instant.parse("2026-10-01T00:00:00Z");
    @Test void readsOneFirstParentCommitAtPinnedHeadUsingExclusiveBoundaryAndCommittedDate() {
        var f=new Fixture();
        f.server.expect(requestTo(url())).andRespond(withSuccess(commit("2026-09-30T23:59:59Z"),MediaType.APPLICATION_JSON));
        var commit=f.adapter.latestBefore("CRM","customer",HEAD,BOUNDARY).orElseThrow();
        assertEquals(SHA,commit.id());assertEquals(BOUNDARY.minusSeconds(1),commit.committedAt());
        assertEquals("Zmiana kontaktu klienta",commit.title());f.server.verify();
    }
    @Test void emptyHistoryIsDifferentFromMalformedOrFailedResponse() {
        var f=new Fixture();
        f.server.expect(requestTo(url())).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        assertTrue(f.adapter.latestBefore("CRM","customer",HEAD,BOUNDARY).isEmpty());f.server.verify();
        for(var body:new String[]{"null","[null]",commit("2026-10-01T00:00:00Z"),commit("bad"),"[{\"id\":\"short\"}]"}) {
            var invalid=new Fixture();invalid.server.expect(requestTo(url())).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThrows(RuntimeException.class,()->invalid.adapter.latestBefore("CRM","customer",HEAD,BOUNDARY));invalid.server.verify();
        }
        var unavailable=new Fixture();unavailable.server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(RuntimeException.class,()->unavailable.adapter.latestBefore("CRM","customer",HEAD,BOUNDARY));unavailable.server.verify();
    }
    @Test void retriesTransientReadsButNeverUsesAnUnpinnedBranch() {
        var f=new Fixture();
        assertThrows(IllegalArgumentException.class,()->f.adapter.latestBefore("CRM","customer","main",BOUNDARY));
        f.server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        f.server.expect(requestTo(url())).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        assertTrue(f.adapter.latestBefore("CRM","customer",HEAD,BOUNDARY).isEmpty());f.server.verify();
    }
    private static String commit(String date) {return "[{\"id\":\""+SHA+"\",\"committed_date\":\""+date+"\",\"authored_date\":\"2020-01-01T00:00:00Z\",\"title\":\"Zmiana kontaktu klienta\"}]";}
    private static String url() {return "https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/commits?ref_name="+HEAD+
            "&until=2026-09-30T23:59:59Z&first_parent=true&order=default&per_page=1&page=1";}
    private static class Fixture {
        final MockRestServiceServer server;
        final GitLabRestRepositoryHistoryAdapter adapter;
        Fixture() {
            var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
            var properties=new GitLabProperties();properties.setBaseUrl("https://gitlab.example.com");
            adapter=new GitLabRestRepositoryHistoryAdapter(properties,new GitLabRestClientFactory(properties,
                    IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
        }
    }
}
