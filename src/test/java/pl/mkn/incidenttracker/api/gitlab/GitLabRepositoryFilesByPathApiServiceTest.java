package pl.mkn.incidenttracker.api.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GitLabRepositoryFilesByPathApiServiceTest {

    @Test
    void shouldReadFilesByNormalizedPathsAndReturnPartialErrors() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositoryFilesByPathApiService(repositoryPort);
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerController.java",
                10
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerController.java",
                "1234567890",
                false
        ));
        when(repositoryPort.readFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerController.java"
        )).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerController.java",
                "blob-controller",
                "commit-controller",
                "last-controller",
                "2026-06-14T10:20:00.000Z",
                "sha-controller",
                1234L
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                5
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                "abcde",
                true
        ));
        when(repositoryPort.readFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerService.java"
        )).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                "blob-service",
                "commit-service",
                "last-service",
                "2026-06-15T11:30:00.000Z",
                "sha-service",
                5678L
        ));

        var response = service.readFiles(new GitLabRepositoryFilesByPathApiRequest(
                "CRM",
                "crm-customer-api",
                "main",
                List.of(
                        "crm-customer-api:src/main/java/com/example/crm/customer/CustomerController.java via gitlab_read_repository_file_outline",
                        "\\src\\main\\java\\com\\example\\crm\\customer\\CustomerService.java",
                        "src/main/java/com/example/crm/customer/CustomerMapper.java"
                ),
                10,
                15
        ));

        verify(repositoryPort).readFile(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerController.java",
                10
        );
        verify(repositoryPort).readFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerController.java"
        );
        verify(repositoryPort).readFile(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                5
        );
        verify(repositoryPort).readFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerService.java"
        );
        verifyNoMoreInteractions(repositoryPort);

        assertEquals(3, response.requestedFileCount());
        assertEquals(2, response.processedFileCount());
        assertEquals(2, response.returnedFileCount());
        assertEquals(0, response.failedFileCount());
        assertEquals(15, response.totalReturnedCharacters());
        assertTrue(response.totalCharacterLimitReached());
        assertFalse(response.fileCountTruncated());
        assertEquals("entrypoint", response.files().get(0).inferredRole());
        assertEquals("RESOLVED", response.files().get(0).metadataStatus());
        assertEquals("last-controller", response.files().get(0).lastCommitId());
        assertEquals("2026-06-14T10:20:00.000Z", response.files().get(0).lastModifiedAt());
        assertEquals(1234L, response.files().get(0).sizeBytes());
        assertEquals("service-or-orchestrator", response.files().get(1).inferredRole());
        assertTrue(response.files().get(1).truncated());
    }

    @Test
    void shouldReturnErrorForSingleMissingFileAndContinueWithNextFile() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositoryFilesByPathApiService(repositoryPort);
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/Missing.java",
                20
        )).thenThrow(new IllegalStateException("file not found"));
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                20
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                "interface CustomerRepository {}",
                true
        ));
        when(repositoryPort.readFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerRepository.java"
        )).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM",
                "crm-customer-api",
                "main",
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                "blob-repository",
                "commit-repository",
                "last-repository",
                "2026-06-16T09:00:00.000Z",
                "sha-repository",
                4321L
        ));

        var response = service.readFiles(new GitLabRepositoryFilesByPathApiRequest(
                "CRM",
                "crm-customer-api",
                "main",
                List.of(
                        "src/main/java/com/example/crm/customer/Missing.java",
                        "src/main/java/com/example/crm/customer/CustomerRepository.java"
                ),
                20,
                100
        ));

        assertEquals(2, response.processedFileCount());
        assertEquals(1, response.returnedFileCount());
        assertEquals(1, response.failedFileCount());
        assertTrue(response.files().get(0).error().contains("file not found"));
        assertEquals("SKIPPED", response.files().get(0).metadataStatus());
        assertEquals("repository", response.files().get(1).inferredRole());
        assertEquals("last-repository", response.files().get(1).lastCommitId());
    }
}
