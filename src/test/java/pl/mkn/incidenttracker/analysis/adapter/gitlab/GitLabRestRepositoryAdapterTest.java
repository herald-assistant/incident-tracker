package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabRestRepositoryAdapterTest {

    @Test
    void shouldSearchProjectsInGroupAndResolveNestedProjectPath() {
        var properties = gitLabProperties("TENANT-ALPHA");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/TENANT-ALPHA/projects?include_subgroups=true&simple=true&per_page=100&search=document-workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/TENANT-ALPHA/projects?include_subgroups=true&simple=true&per_page=100&search=document_workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "DOCUMENT_WORKFLOW",
                            "path": "DOCUMENT_WORKFLOW",
                            "path_with_namespace": "TENANT-ALPHA/WORKFLOWS/DOCUMENT_WORKFLOW"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var projectCandidates = adapter.searchProjects("TENANT-ALPHA", List.of("document-workflow"));

        assertEquals(1, projectCandidates.size());
        assertEquals("TENANT-ALPHA", projectCandidates.get(0).group());
        assertEquals("WORKFLOWS/DOCUMENT_WORKFLOW", projectCandidates.get(0).projectPath());
        assertTrue(projectCandidates.get(0).matchReason().contains("document_workflow"));

        server.verify();
    }

    @Test
    void shouldSearchRepositoryCandidatesThroughGitLabRestApi() {
        var properties = gitLabProperties("sample/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/sample%2Fruntime/projects?include_subgroups=true&simple=true&per_page=100&search=ledger-write-service&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "ledger-write-service",
                            "path": "ledger-write-service",
                            "path_with_namespace": "sample/runtime/ledger-write-service"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/sample%2Fruntime/projects?include_subgroups=true&simple=true&per_page=100&search=ledger_write_service&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/sample%2Fruntime%2Fledger-write-service/search?scope=blobs&search=deadlock&ref=release/2026.04&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var candidates = adapter.searchCandidateFiles(new GitLabRepositorySearchQuery(
                "db-lock-123",
                "sample/runtime",
                "release/2026.04",
                List.of("ledger-write-service"),
                List.of(),
                List.of("deadlock")
        ));

        assertEquals(1, candidates.size());
        assertEquals("sample/runtime", candidates.get(0).group());
        assertEquals("ledger-write-service", candidates.get(0).projectName());
        assertEquals("release/2026.04", candidates.get(0).branch());
        assertEquals("src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java", candidates.get(0).filePath());
        assertTrue(candidates.get(0).matchReason().contains("deadlock"));

        server.verify();
    }

    @Test
    void shouldResolveNestedProjectPathBeforeSearchingRepositoryCandidates() {
        var properties = gitLabProperties("TENANT-ALPHA");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/TENANT-ALPHA/projects?include_subgroups=true&simple=true&per_page=100&search=document-workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/TENANT-ALPHA/projects?include_subgroups=true&simple=true&per_page=100&search=document_workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "DOCUMENT_WORKFLOW",
                            "path": "DOCUMENT_WORKFLOW",
                            "path_with_namespace": "TENANT-ALPHA/WORKFLOWS/DOCUMENT_WORKFLOW"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/TENANT-ALPHA%2FWORKFLOWS%2FDOCUMENT_WORKFLOW/search?scope=blobs&search=document&ref=release-candidate&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/TENANT-ALPHA%2Fdocument-workflow/search?scope=blobs&search=document&ref=release-candidate&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        var candidates = adapter.searchCandidateFiles(new GitLabRepositorySearchQuery(
                "agreement-123",
                "TENANT-ALPHA",
                "release-candidate",
                List.of("document-workflow"),
                List.of(),
                List.of("document")
        ));

        assertEquals(1, candidates.size());
        assertEquals("WORKFLOWS/DOCUMENT_WORKFLOW", candidates.get(0).projectName());
        assertEquals("src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java", candidates.get(0).filePath());

        server.verify();
    }

    @Test
    void shouldReadRawFileAndBuildChunkFromGitLabRestApi() {
        var properties = gitLabProperties("sample/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/sample%2Fruntime%2Fedge-client-service/repository/files/src%2Fmain%2Fjava%2Fcom%2Fexample%2Fsynthetic%2Fedge%2FCatalogGatewayClient.java/raw?ref=release/2026.04"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        package com.example.synthetic.edge;

                        public class CatalogGatewayClient {

                            public CatalogResponse fetchCatalog(String sku) {
                                return catalogWebClient.get()
                                        .uri("/catalog/{sku}", sku)
                                        .retrieve()
                                        .bodyToMono(CatalogResponse.class)
                                        .timeout(Duration.ofSeconds(2))
                                        .block();
                            }

                        }
                        """, MediaType.TEXT_PLAIN));

        var fileChunk = adapter.readFileChunk(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                10,
                4_000
        );

        assertEquals("sample/runtime", fileChunk.group());
        assertEquals("edge-client-service", fileChunk.projectName());
        assertEquals("release/2026.04", fileChunk.branch());
        assertEquals(5, fileChunk.returnedStartLine());
        assertEquals(10, fileChunk.returnedEndLine());
        assertTrue(fileChunk.content().contains("catalogWebClient.get()"));
        assertFalse(fileChunk.truncated());

        server.verify();
    }

    private static GitLabProperties gitLabProperties(String group) {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup(group);
        properties.setToken("glpat-test");
        return properties;
    }

}

