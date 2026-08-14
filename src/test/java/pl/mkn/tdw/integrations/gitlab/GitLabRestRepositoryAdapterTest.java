package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryFileRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryInventoryRequest;
import pl.mkn.tdw.testsupport.integrations.GitLabIntegrationTestCreator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabRestRepositoryAdapterTest {

    @Test
    void shouldSearchProjectsInGroupAndResolveNestedProjectPath() {
        var properties = gitLabProperties("CRM");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

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
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

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
                List.of("deadlock"),
                List.of()
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
    void shouldFilterRepositoryCandidatesByPathPrefixes() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

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
                          },
                          {
                            "path": "src/test/java/com/example/crm/customer/account/CustomerAccountServiceTest.java"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var candidates = adapter.searchCandidateFiles(new GitLabRepositorySearchQuery(
                "db-lock-123",
                "CRM/runtime",
                "release/2026.04",
                List.of("crm-customer-account-service"),
                List.of(),
                List.of("deadlock"),
                List.of("src/main/java/com/example/crm/customer/account")
        ));

        assertEquals(1, candidates.size());
        assertEquals("src/main/java/com/example/crm/customer/account/CustomerAccountService.java", candidates.get(0).filePath());

        server.verify();
    }

    @Test
    void shouldSearchRepositoryFilesByContentTermsWithoutGlobalCandidateLimit() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/search?scope=blobs&search=@RestController&ref=release/2026.04&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/crm/customer/api/CustomerProfileController.java"
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
        assertEquals("src/main/java/com/example/crm/customer/api/CustomerProfileController.java", candidates.get(0).filePath());
        assertTrue(candidates.get(0).matchReason().contains("@RestController"));

        server.verify();
    }

    @Test
    void shouldResolveNestedProjectPathBeforeSearchingRepositoryCandidates() {
        var properties = gitLabProperties("CRM");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

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
                List.of("customer"),
                List.of()
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
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/tree?recursive=true&per_page=100&ref=release/2026.04&page=1&path=src/main/java"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "src/main/java/com/example/crm/customer/CustomerProfileController.java",
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
        assertEquals("src/main/java/com/example/crm/customer/CustomerProfileController.java", files.get(0).filePath());

        server.verify();
    }

    @Test
    void shouldReadRawFileAndBuildChunkFromGitLabRestApi() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-client-service/repository/files/src%2Fmain%2Fjava%2Fcom%2Fexample%2Fsynthetic%2Fedge%2FCustomerProfileClient.java/raw?ref=release/2026.04"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        package com.example.synthetic.edge;

                        public class CustomerProfileClient {

                            public CustomerProfileResponse fetchCustomerProfile(String customerId) {
                                return customerProfileWebClient.get()
                                        .uri("/customer-profile/{customerId}", customerId)
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
    void shouldReadInstructionFileFromRepositoryKeyUsingConfiguredGitLabGroup() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcustomer-api/repository/files/AGENTS.md/raw?ref=feature/CRM-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("Use repository boundaries.", MediaType.TEXT_PLAIN));

        var file = adapter.readFile(new InstructionRepositoryFileRequest(
                "CRM/runtime/customer-api",
                "feature/CRM-123",
                "AGENTS.md",
                12_000
        ));

        assertTrue(file.exists());
        assertEquals("CRM/runtime/customer-api", file.repositoryKey());
        assertEquals("feature/CRM-123", file.ref());
        assertEquals("AGENTS.md", file.path());
        assertEquals("Use repository boundaries.", file.content());
        assertFalse(file.truncated());

        server.verify();
    }

    @Test
    void shouldLoadInstructionRepositoryInventoryForNestedGitLabGroup() {
        var properties = gitLabProperties("CRM");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fprocesses%2Fcustomer-api/repository/tree?recursive=true&per_page=100&ref=main&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "path": "AGENTS.md",
                            "type": "blob"
                          },
                          {
                            "path": "src/main/java/com/example/customer/AGENTS.md",
                            "type": "blob"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var inventory = adapter.loadFileInventory(new InstructionRepositoryInventoryRequest(
                "CRM/processes/customer-api",
                "main"
        ));

        assertTrue(inventory.available());
        assertEquals(
                List.of("AGENTS.md", "src/main/java/com/example/customer/AGENTS.md"),
                inventory.paths()
        );

        server.verify();
    }

    @Test
    void shouldReadFileMetadataAndResolveLastModifiedAtFromCommit() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));
        var metadataHeaders = new HttpHeaders();
        metadataHeaders.add("X-Gitlab-File-Path", "src/main/java/com/example/crm/customer/CustomerProfileController.java");
        metadataHeaders.add("X-Gitlab-Blob-Id", "blob-customer-controller");
        metadataHeaders.add("X-Gitlab-Commit-Id", "branch-tip-commit");
        metadataHeaders.add("X-Gitlab-Last-Commit-Id", "last-file-commit");
        metadataHeaders.add("X-Gitlab-Content-Sha256", "content-sha-256");
        metadataHeaders.add("X-Gitlab-Size", "2048");

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/files/src%2Fmain%2Fjava%2Fcom%2Fexample%2Fcrm%2Fcustomer%2FCustomerProfileController.java?ref=release/2026.04"))
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
                "src/main/java/com/example/crm/customer/CustomerProfileController.java"
        );

        assertEquals("CRM/runtime", metadata.group());
        assertEquals("crm-customer-api", metadata.projectName());
        assertEquals("release/2026.04", metadata.branch());
        assertEquals("src/main/java/com/example/crm/customer/CustomerProfileController.java", metadata.filePath());
        assertEquals("blob-customer-controller", metadata.blobId());
        assertEquals("branch-tip-commit", metadata.commitId());
        assertEquals("last-file-commit", metadata.lastCommitId());
        assertEquals("2026-06-14T10:20:00.000Z", metadata.lastModifiedAt());
        assertEquals("content-sha-256", metadata.contentSha256());
        assertEquals(2048L, metadata.sizeBytes());

        server.verify();
    }

    @Test
    void shouldCheckBranchExistence() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/branches/feature%2FCRM-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/CRM%2Fruntime%2Fcrm-customer-api/repository/branches/feature%2FCRM-124"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(adapter.branchExists("CRM/runtime", "crm-customer-api", "feature/CRM-123"));
        assertFalse(adapter.branchExists("CRM/runtime", "crm-customer-api", "feature/CRM-124"));

        server.verify();
    }

    @Test
    void shouldFindMergeRequestsByIssueKeyWithCommitsAndChangedFiles() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM%2Fruntime/merge_requests?scope=all&state=all&search=CRM-123&in=title,source_branch&per_page=10&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 1001,
                            "iid": 7,
                            "project_id": 77,
                            "title": "CRM-123 customer status",
                            "state": "merged",
                            "web_url": "https://gitlab.example.com/CRM/runtime/customer-api/-/merge_requests/7",
                            "source_branch": "feature/CRM-123-customer-status",
                            "target_branch": "release/2026.08",
                            "author": { "name": "Jan Nowak" },
                            "created_at": "2026-07-20T10:00:00.000Z",
                            "updated_at": "2026-07-21T10:00:00.000Z",
                            "merged_at": "2026-07-21T11:00:00.000Z",
                            "changes_count": "4",
                            "references": { "full": "CRM/runtime/customer-api!7" }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/77/merge_requests/7/commits?per_page=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": "abcdef123456",
                            "short_id": "abcdef12",
                            "title": "CRM-123 add status",
                            "author_name": "Jan Nowak",
                            "created_at": "2026-07-20T10:00:00.000Z"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/77/merge_requests/7/diffs?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "old_path": "src/main/java/CustomerController.java",
                            "new_path": "src/main/java/CustomerController.java",
                            "new_file": false,
                            "renamed_file": false,
                            "deleted_file": false,
                            "diff": "@@ -1 +1 @@\\n-old\\n+new"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10);

        assertEquals("CRM-123", result.issueKey());
        assertEquals(1, result.mergeRequests().size());
        var mergeRequest = result.mergeRequests().get(0);
        assertEquals("CRM/runtime/customer-api", mergeRequest.projectPath());
        assertEquals("CRM-123 customer status", mergeRequest.title());
        assertEquals("feature/CRM-123-customer-status", mergeRequest.sourceBranch());
        assertEquals(1, mergeRequest.commits().size());
        assertEquals("abcdef12", mergeRequest.commits().get(0).shortId());
        assertEquals(1, mergeRequest.changedFiles().size());
        assertEquals("src/main/java/CustomerController.java", mergeRequest.changedFiles().get(0).newPath());
        assertEquals("@@ -1 +1 @@\n-old\n+new", mergeRequest.changedFiles().get(0).diff());

        server.verify();
    }

    @Test
    void shouldPageMergeRequestChangedFilesBeyondSingleGitLabPage() {
        var properties = gitLabProperties("CRM/runtime");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/groups/CRM%2Fruntime/merge_requests?scope=all&state=all&search=CRM-456&in=title,source_branch&per_page=10&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 2001,
                            "iid": 8,
                            "project_id": 78,
                            "title": "CRM-456 large change",
                            "state": "merged",
                            "web_url": "https://gitlab.example.com/CRM/runtime/customer-api/-/merge_requests/8",
                            "source_branch": "feature/CRM-456-large-change",
                            "target_branch": "release/2026.08",
                            "author": { "name": "Jan Nowak" },
                            "changes_count": "101",
                            "references": { "full": "CRM/runtime/customer-api!8" }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/78/merge_requests/8/commits?per_page=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/78/merge_requests/8/diffs?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(firstChangedFilesPageJson(), MediaType.APPLICATION_JSON)
                        .header("X-Next-Page", "2"));

        server.expect(requestTo(
                        "https://gitlab.example.com/api/v4/projects/78/merge_requests/8/diffs?per_page=100&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "old_path": "src/main/java/PageTwo.java",
                            "new_path": "src/main/java/PageTwo.java",
                            "new_file": false,
                            "renamed_file": false,
                            "deleted_file": false
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.findMergeRequestsByIssueKey("CRM/runtime", "CRM-456", 10);

        var mergeRequest = result.mergeRequests().get(0);
        assertEquals(101, mergeRequest.changedFiles().size());
        assertEquals("src/main/java/File001.java", mergeRequest.changedFiles().get(0).newPath());
        assertEquals("src/main/java/PageTwo.java", mergeRequest.changedFiles().get(100).newPath());
        assertTrue(mergeRequest.limitations().isEmpty());

        server.verify();
    }

    private static GitLabProperties gitLabProperties(String group) {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup(group);
        properties.setToken("glpat-test");
        return properties;
    }

    private static String firstChangedFilesPageJson() {
        var builder = new StringBuilder("[");
        for (int index = 1; index <= 100; index++) {
            if (index > 1) {
                builder.append(',');
            }
            builder.append("""
                    {
                      "old_path": "src/main/java/File%03d.java",
                      "new_path": "src/main/java/File%03d.java",
                      "new_file": false,
                      "renamed_file": false,
                      "deleted_file": false
                    }
                    """.formatted(index, index));
        }
        builder.append(']');
        return builder.toString();
    }

}

