package pl.mkn.tdw.integrations.confluence;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfluenceRestPageAdapterTest {

    @Test
    void shouldFetchConfluencePageByPageIdFromRemoteLinkUrl() {
        var properties = confluenceProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new ConfluenceRestPageAdapter(
                properties,
                new ConfluenceRestClientFactory(properties, restClientBuilder)
        );

        server.expect(requestTo("https://confluence.example.com:9999/rest/api/content/686722831?expand=body.storage,version"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer confluence-token"))
                .andRespond(withSuccess("""
                        {
                          "id": "686722831",
                          "title": "CRM case profile design",
                          "version": { "number": 12 },
                          "body": {
                            "storage": {
                              "value": "<p>Prepare CRM case profile data.</p><ul><li>customer init</li><li>case event</li></ul>"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var page = adapter.getPageContent(
                "https://confluence.example.com:9999/pages/viewpage.action?pageId=686722831"
        );

        assertThat(page).isPresent();
        assertThat(page.orElseThrow().pageId()).isEqualTo("686722831");
        assertThat(page.orElseThrow().title()).isEqualTo("CRM case profile design");
        assertThat(page.orElseThrow().version()).isEqualTo("12");
        assertThat(page.orElseThrow().content()).contains("Prepare CRM case profile data");
        assertThat(page.orElseThrow().content()).contains("customer init");
        server.verify();
    }

    @Test
    void shouldSkipUrlsOutsideConfiguredPattern() {
        var properties = confluenceProperties();
        var adapter = new ConfluenceRestPageAdapter(
                properties,
                new ConfluenceRestClientFactory(properties, RestClient.builder())
        );

        assertThat(adapter.getPageContent("https://other.example.com/pages/viewpage.action?pageId=1")).isEmpty();
    }

    private static ConfluenceProperties confluenceProperties() {
        var properties = new ConfluenceProperties();
        properties.setToken("confluence-token");
        properties.setUrlPattern("^https://confluence\\.example\\.com:9999/.*$");
        return properties;
    }
}
