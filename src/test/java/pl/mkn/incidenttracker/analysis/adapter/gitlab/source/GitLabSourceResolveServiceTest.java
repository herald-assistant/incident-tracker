package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRestClientFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabSourceResolveServiceTest {

    @Test
    void shouldResolveBestFileAndFollowTreePagination() {
        var serviceFixture = newServiceFixture();
        var headers = new HttpHeaders();
        headers.add("X-Next-Page", "2");

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=HEAD&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"path":"src/main/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"}
                        ]
                        """, MediaType.APPLICATION_JSON).headers(headers));

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=HEAD&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/files/src%2Fmain%2Fjava%2Fc%2Fe%2Fsynthetic%2Fresponse%2FResponsePathSelector.java/raw?ref=HEAD"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("package c.e.synthetic.response;\npublic class ResponsePathSelector {}", MediaType.TEXT_PLAIN));

        var response = serviceFixture.service.resolve(new GitLabSourceResolveRequest(
                "https://gitlab.example.com",
                "my-group/subgroup",
                "my-service",
                null,
                "c.e.synthetic.response.ResponsePathSelector"
        ));

        assertEquals("src/main/java/c/e/synthetic/response/ResponsePathSelector.java", response.matchedPath());
        assertEquals(130, response.score());
        assertEquals(1, response.candidates().size());
        assertTrue(response.content().contains("ResponsePathSelector"));
        assertEquals("OK", response.message());

        serviceFixture.server.verify();
    }

    @Test
    void shouldResolveFileWhenSymbolStartsWithLeadingDot() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=dev/atlas&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"path":"src/main/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/files/src%2Fmain%2Fjava%2Fc%2Fe%2Fsynthetic%2Fresponse%2FResponsePathSelector.java/raw?ref=dev/atlas"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("class ResponsePathSelector {}", MediaType.TEXT_PLAIN));

        var response = serviceFixture.service.resolve(new GitLabSourceResolveRequest(
                "https://gitlab.example.com",
                "my-group/subgroup",
                "my-service",
                "dev/atlas",
                ".e.synthetic.response.ResponsePathSelector"
        ));

        assertEquals("src/main/java/c/e/synthetic/response/ResponsePathSelector.java", response.matchedPath());
        assertEquals(130, response.score());
        serviceFixture.server.verify();
    }

    @Test
    void shouldCacheRepositoryTreeWithinSingleHttpRequest() {
        var serviceFixture = newServiceFixture();
        var request = new GitLabSourceResolveRequest(
                "https://gitlab.example.com",
                "my-group/subgroup",
                "my-service",
                "dev/atlas",
                "c.e.synthetic.response.ResponsePathSelector"
        );

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=dev/atlas&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"path":"src/main/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        var requestAttributes = new ServletRequestAttributes(new MockHttpServletRequest());
        RequestContextHolder.setRequestAttributes(requestAttributes);
        try {
            var first = serviceFixture.service.resolveMatch(request);
            var second = serviceFixture.service.resolveMatch(request);

            assertEquals("src/main/java/c/e/synthetic/response/ResponsePathSelector.java", first.matchedPath());
            assertEquals(first, second);
        } finally {
            requestAttributes.requestCompleted();
            RequestContextHolder.resetRequestAttributes();
        }

        serviceFixture.server.verify();
    }

    @Test
    void shouldCacheRepositoryTreeWithinExplicitResolveSession() {
        var serviceFixture = newServiceFixture();
        var request = new GitLabSourceResolveRequest(
                "https://gitlab.example.com",
                "my-group/subgroup",
                "my-service",
                "dev/atlas",
                "c.e.synthetic.response.ResponsePathSelector"
        );

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=dev/atlas&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"path":"src/main/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        var session = serviceFixture.service.openSession();

        var first = serviceFixture.service.resolveMatch(request, session);
        var second = serviceFixture.service.resolveMatch(request, session);

        assertEquals("src/main/java/c/e/synthetic/response/ResponsePathSelector.java", first.matchedPath());
        assertEquals(first, second);

        serviceFixture.server.verify();
    }

    @Test
    void shouldRankMainSourceAboveTestAndGeneratedCandidates() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=main&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"path":"src/test/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"},
                          {"path":"generated/src/main/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"},
                          {"path":"src/main/java/c/e/synthetic/response/ResponsePathSelector.java","type":"blob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/files/src%2Fmain%2Fjava%2Fc%2Fe%2Fsynthetic%2Fresponse%2FResponsePathSelector.java/raw?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("class ResponsePathSelector {}", MediaType.TEXT_PLAIN));

        var response = serviceFixture.service.resolve(new GitLabSourceResolveRequest(
                "https://gitlab.example.com",
                "my-group/subgroup",
                "my-service",
                "main",
                "c.e.synthetic.response.ResponsePathSelector"
        ));

        assertEquals("src/main/java/c/e/synthetic/response/ResponsePathSelector.java", response.matchedPath());
        assertEquals(130, response.score());
        assertEquals(
                "src/main/java/c/e/synthetic/response/ResponsePathSelector.java",
                response.candidates().get(0)
        );
        assertEquals(
                "src/test/java/c/e/synthetic/response/ResponsePathSelector.java",
                response.candidates().get(1)
        );
        assertEquals(
                "generated/src/main/java/c/e/synthetic/response/ResponsePathSelector.java",
                response.candidates().get(2)
        );

        serviceFixture.server.verify();
    }

    @Test
    void shouldReturnReadableErrorWhenNoCandidatesAreFound() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmy-service/repository/tree?recursive=true&per_page=100&ref=HEAD&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"path":"src/main/java/c/e/synthetic/response/AnotherClass.java","type":"blob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                GitLabSourceResolveException.class,
                () -> serviceFixture.service.resolve(new GitLabSourceResolveRequest(
                        "https://gitlab.example.com",
                        "my-group/subgroup",
                        "my-service",
                        null,
                        "c.e.synthetic.response.ResponsePathSelector"
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("No source file candidates found for symbol: c.e.synthetic.response.ResponsePathSelector", exception.getResponse().message());
        assertTrue(exception.getResponse().candidates().isEmpty());

        serviceFixture.server.verify();
    }

    @Test
    void shouldReturnReadableErrorWhenGitLabProjectIsNotFound() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/my-group%2Fsubgroup%2Fmissing-service/repository/tree?recursive=true&per_page=100&ref=HEAD&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        var exception = assertThrows(
                GitLabSourceResolveException.class,
                () -> serviceFixture.service.resolve(new GitLabSourceResolveRequest(
                        "https://gitlab.example.com",
                        "my-group/subgroup",
                        "missing-service",
                        null,
                        "c.e.synthetic.response.ResponsePathSelector"
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("GitLab project or ref not found: my-group/subgroup/missing-service@HEAD", exception.getResponse().message());

        serviceFixture.server.verify();
    }

    private ServiceFixture newServiceFixture() {
        var properties = new GitLabProperties();
        properties.setToken("glpat-test");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var factory = new GitLabRestClientFactory(properties, restClientBuilder);
        return new ServiceFixture(new GitLabSourceResolveService(factory), server);
    }

    private record ServiceFixture(
            GitLabSourceResolveService service,
            MockRestServiceServer server
    ) {
    }

}


