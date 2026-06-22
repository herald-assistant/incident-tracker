package pl.mkn.incidenttracker.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
        var properties = gitLabProperties("CRM");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM/projects?include_subgroups=true&simple=true&per_page=100&search=crm-customer-workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM/projects?include_subgroups=true&simple=true&per_page=100&search=crm_customer_workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM/projects?include_subgroups=true&simple=true&per_page=100&search=customer_workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "CUSTOMER_WORKFLOW",
                            "path": "CUSTOMER_WORKFLOW",
                            "path_with_namespace": "CRM/CRM_WORKFLOWS/CUSTOMER_WORKFLOW"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var projectCandidates = adapter.searchProjects("CRM", List.of("crm-customer-workflow"));

        assertEquals(1, projectCandidates.size());
        assertEquals("CRM", projectCandidates.get(0).group());
        assertEquals("CRM_WORKFLOWS/CUSTOMER_WORKFLOW", projectCandidates.get(0).projectPath());
        assertTrue(projectCandidates.get(0).matchReason().contains("customer_workflow"));

        server.verify();
    }

    @Test
    void shouldSearchRepositoryCandidatesThroughGitLabRestApi() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM%2Fruntime/projects?include_subgroups=true&simple=true&per_page=100&search=crm-customer-account-service&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "crm-customer-account-service",
                            "path": "crm-customer-account-service",
                            "path_with_namespace": "CRM/runtime/crm-customer-account-service"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM%2Fruntime/projects?include_subgroups=true&simple=true&per_page=100&search=crm_customer_account_service&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-account-service/search?scope=blobs&search=deadlock&ref=release/2026.04&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/crm/customer/account/CustomerAccountService.java"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var candidates = adapter.searchCandidateFiles(new GitLabRepositorySearchQuery(
                "db-lock-123",
                "CRM/runtime",
                "release/2026.04",
                List.of("crm-customer-account-service"),
                List.of(),
                List.of("deadlock")
        ));

        assertEquals(1, candidates.size());
        assertEquals("CRM/runtime", candidates.get(0).group());
        assertEquals("crm-customer-account-service", candidates.get(0).projectName());
        assertEquals("release/2026.04", candidates.get(0).branch());
        assertEquals("src/main/java/com/example/crm/customer/account/CustomerAccountService.java", candidates.get(0).filePath());
        assertTrue(candidates.get(0).matchReason().contains("deadlock"));

        server.verify();
    }

    @Test
    void shouldSearchRepositoryFilesByContentTermsWithoutGlobalCandidateLimit() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/search?scope=blobs&search=@RestController&ref=release/2026.04&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/crm/customer/api/CustomerController.java"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var candidates = adapter.searchRepositoryFilesByContent(
                "CRM/runtime",
                "crm-customer-api",
                "release/2026.04",
                List.of("@RestController"),
                100
        );

        assertEquals(1, candidates.size());
        assertEquals("CRM/runtime", candidates.get(0).group());
        assertEquals("crm-customer-api", candidates.get(0).projectName());
        assertEquals("release/2026.04", candidates.get(0).branch());
        assertEquals("src/main/java/com/example/crm/customer/api/CustomerController.java", candidates.get(0).filePath());
        assertTrue(candidates.get(0).matchReason().contains("@RestController"));

        server.verify();
    }

    @Test
    void shouldResolveNestedProjectPathBeforeSearchingRepositoryCandidates() {
        var properties = gitLabProperties("CRM");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM/projects?include_subgroups=true&simple=true&per_page=100&search=crm-customer-workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM/projects?include_subgroups=true&simple=true&per_page=100&search=crm_customer_workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM/projects?include_subgroups=true&simple=true&per_page=100&search=customer_workflow&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "CUSTOMER_WORKFLOW",
                            "path": "CUSTOMER_WORKFLOW",
                            "path_with_namespace": "CRM/CRM_WORKFLOWS/CUSTOMER_WORKFLOW"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2FCRM_WORKFLOWS%2FCUSTOMER_WORKFLOW/search?scope=blobs&search=customer&ref=release-candidate&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/synthetic/workflow/CustomerWorkflowArchiveService.java"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fcrm-customer-workflow/search?scope=blobs&search=customer&ref=release-candidate&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        []
                        """, MediaType.APPLICATION_JSON));

        var candidates = adapter.searchCandidateFiles(new GitLabRepositorySearchQuery(
                "customer-123",
                "CRM",
                "release-candidate",
                List.of("crm-customer-workflow"),
                List.of(),
                List.of("customer")
        ));

        assertEquals(1, candidates.size());
        assertEquals("CRM_WORKFLOWS/CUSTOMER_WORKFLOW", candidates.get(0).projectName());
        assertEquals("src/main/java/com/example/synthetic/workflow/CustomerWorkflowArchiveService.java", candidates.get(0).filePath());

        server.verify();
    }

    @Test
    void shouldListRepositoryFilesThroughGitLabRepositoryTreeApi() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/tree?recursive=true&per_page=100&ref=release/2026.04&page=1&path=src/main/java"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/crm/customer/CustomerController.java",
                            "type": "blob"
                          },
                          {
                            "path": "src/main/java/com/example/crm/customer",
                            "type": "tree"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var files = adapter.listRepositoryFiles(
                "CRM/runtime",
                "crm-customer-api",
                "release/2026.04",
                "src/main/java"
        );

        assertEquals(1, files.size());
        assertEquals("CRM/runtime", files.get(0).group());
        assertEquals("crm-customer-api", files.get(0).projectName());
        assertEquals("release/2026.04", files.get(0).branch());
        assertEquals("src/main/java/com/example/crm/customer/CustomerController.java", files.get(0).filePath());

        server.verify();
    }

    @Test
    void shouldReadRawFileAndBuildChunkFromGitLabRestApi() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-client-service/repository/files/src%2Fmain%2Fjava%2Fcom%2Fexample%2Fsynthetic%2Fedge%2FCustomerProfileClient.java/raw?ref=release/2026.04"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        package com.example.synthetic.edge;

                        public class CustomerProfileClient {

                            public CustomerProfileResponse fetchCustomerProfile(String sku) {
                                return customerProfileWebClient.get()
                                        .uri("/customer-profile/{sku}", sku)
                                        .retrieve()
                                        .bodyToMono(CustomerProfileResponse.class)
                                        .timeout(Duration.ofSeconds(2))
                                        .block();
                            }

                        }
                        """, MediaType.TEXT_PLAIN));

        var fileChunk = adapter.readFileChunk(
                "CRM/runtime",
                "crm-customer-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                5,
                10,
                4_000
        );

        assertEquals("CRM/runtime", fileChunk.group());
        assertEquals("crm-customer-client-service", fileChunk.projectName());
        assertEquals("release/2026.04", fileChunk.branch());
        assertEquals(5, fileChunk.returnedStartLine());
        assertEquals(10, fileChunk.returnedEndLine());
        assertTrue(fileChunk.content().contains("customerProfileWebClient.get()"));
        assertFalse(fileChunk.truncated());

        server.verify();
    }

    @Test
    void shouldReadFileMetadataAndResolveLastModifiedAtFromCommit() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new GitLabRestRepositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));
        var metadataHeaders = new HttpHeaders();
        metadataHeaders.add("X-Gitlab-File-Path", "src/main/java/com/example/crm/customer/CustomerController.java");
        metadataHeaders.add("X-Gitlab-Blob-Id", "blob-customer-controller");
        metadataHeaders.add("X-Gitlab-Commit-Id", "branch-tip-commit");
        metadataHeaders.add("X-Gitlab-Last-Commit-Id", "last-file-commit");
        metadataHeaders.add("X-Gitlab-Content-Sha256", "content-sha-256");
        metadataHeaders.add("X-Gitlab-Size", "2048");

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/files/src%2Fmain%2Fjava%2Fcom%2Fexample%2Fcrm%2Fcustomer%2FCustomerController.java?ref=release/2026.04"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess().headers(metadataHeaders));
        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/commits/last-file-commit?stats=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "last-file-commit",
                          "committed_date": "2026-06-14T10:20:00.000Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        var metadata = adapter.readFileMetadata(
                "CRM/runtime",
                "crm-customer-api",
                "release/2026.04",
                "src/main/java/com/example/crm/customer/CustomerController.java"
        );

        assertEquals("CRM/runtime", metadata.group());
        assertEquals("crm-customer-api", metadata.projectName());
        assertEquals("release/2026.04", metadata.branch());
        assertEquals("src/main/java/com/example/crm/customer/CustomerController.java", metadata.filePath());
        assertEquals("blob-customer-controller", metadata.blobId());
        assertEquals("branch-tip-commit", metadata.commitId());
        assertEquals("last-file-commit", metadata.lastCommitId());
        assertEquals("2026-06-14T10:20:00.000Z", metadata.lastModifiedAt());
        assertEquals("content-sha-256", metadata.contentSha256());
        assertEquals(2048L, metadata.sizeBytes());

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

