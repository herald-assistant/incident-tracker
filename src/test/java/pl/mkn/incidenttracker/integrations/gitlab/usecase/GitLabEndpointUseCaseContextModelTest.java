package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseContextModelTest {

    @Test
    void shouldNormalizeRequestDefaultsAndLimits() {
        var request = new GitLabEndpointUseCaseContextRequest(
                " crm-customer-service ",
                "  GET /api/customers/{id} -> Controller#getCustomer ",
                " get ",
                "api/customers/{id}",
                99,
                999
        );

        assertEquals("crm-customer-service", request.projectName());
        assertEquals("GET /api/customers/{id} -> Controller#getCustomer", request.endpointId());
        assertEquals("GET", request.httpMethod());
        assertEquals("/api/customers/{id}", request.endpointPath());
        assertEquals(GitLabEndpointUseCaseContextRequest.MAX_MAX_DEPTH, request.maxDepth());
        assertEquals(GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES, request.maxFiles());
    }

    @Test
    void shouldUseRequestDefaultsWhenOptionalValuesAreBlankOrInvalid() {
        var request = new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                " ",
                " ",
                " ",
                0,
                -10
        );

        assertEquals("crm-customer-service", request.projectName());
        assertEquals(GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH, request.maxDepth());
        assertEquals(GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_FILES, request.maxFiles());
        assertEquals(null, request.endpointId());
        assertEquals(null, request.httpMethod());
        assertEquals(null, request.endpointPath());
    }

    @Test
    void shouldKeepResultListsNullSafeAndImmutable() {
        var method = new GitLabEndpointUseCaseMethodCandidate(
                "\\src\\main\\java\\com\\example\\crm\\CustomerController.java",
                " CustomerController ",
                " customer.api.CustomerController ",
                " com.example.crm.CustomerController ",
                GitLabJavaTypeKind.CLASS,
                " getCustomer ",
                null,
                -10,
                42,
                1,
                List.of(" String "),
                List.of(" customerId "),
                " CustomerResponse ",
                List.of(" public "),
                GitLabEndpointUseCaseFileRole.CONTROLLER,
                0,
                -5,
                " endpoint handler ",
                GitLabEndpointUseCaseConfidence.HIGH
        );
        var files = new ArrayList<>(List.of(new GitLabEndpointUseCaseFileCandidate(
                "/src/main/java/com/example/crm/CustomerController.java",
                GitLabEndpointUseCaseFileRole.CONTROLLER,
                0,
                List.of(" getCustomer ", " "),
                List.of(method),
                " endpoint handler ",
                GitLabEndpointUseCaseConfidence.HIGH
        )));

        var result = new GitLabEndpointUseCaseContextResult(
                null,
                null,
                files,
                null,
                null,
                List.of(" limitation "),
                List.of(" read controller "),
                null,
                null
        );
        files.clear();

        assertEquals(1, result.files().size());
        assertEquals("src/main/java/com/example/crm/CustomerController.java", result.files().get(0).path());
        assertEquals(1, result.files().get(0).priority());
        assertEquals(List.of("getCustomer"), result.files().get(0).symbols());
        assertEquals(1, result.files().get(0).methods().size());
        assertEquals("CustomerResponse getCustomer(String customerId)",
                result.files().get(0).methods().get(0).signature());
        assertEquals("com.example.crm.CustomerController",
                result.files().get(0).methods().get(0).declaringTypeQualifiedName());
        assertEquals(0, result.files().get(0).methods().get(0).lineStart());
        assertEquals(42, result.files().get(0).methods().get(0).lineEnd());
        assertEquals("endpoint handler", result.files().get(0).reason());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, result.files().get(0).confidence());
        assertTrue(result.relations().isEmpty());
        assertTrue(result.unresolved().isEmpty());
        assertEquals(List.of("limitation"), result.limitations());
        assertEquals(List.of("read controller"), result.suggestedNextReads());
        assertEquals(GitLabEndpointUseCaseLimits.defaults(), result.limits());
        assertEquals(GitLabEndpointUseCaseConfidence.LOW, result.confidence());
        assertThrows(UnsupportedOperationException.class, () -> result.files().add(result.files().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> result.files().get(0).methods().add(method));
    }

    @Test
    void shouldNormalizeEndpointContextAndLimits() {
        var endpoint = new GitLabEndpointUseCaseEndpointContext(
                "endpoint-id",
                List.of(" get ", "POST"),
                "api/customers/{id}",
                " /api/customers/{id} ",
                " com.example.crm.CustomerController ",
                " getCustomer ",
                "\\src\\main\\java\\com\\example\\crm\\CustomerController.java",
                -10,
                -1,
                List.of(" CustomerRequest "),
                List.of(" CustomerResponse "),
                List.of(" RestController "),
                GitLabEndpointUseCaseConfidence.HIGH,
                List.of(" endpoint limitation "),
                List.of(" read endpoint ")
        );
        var limits = new GitLabEndpointUseCaseLimits(0, 999, 0, true, true, -5, true);

        assertEquals(List.of("GET", "POST"), endpoint.httpMethods());
        assertEquals("/api/customers/{id}", endpoint.path());
        assertEquals("/api/customers/{id}", endpoint.pathExpression());
        assertEquals("com.example.crm.CustomerController", endpoint.controllerClass());
        assertEquals("getCustomer", endpoint.handlerMethod());
        assertEquals("src/main/java/com/example/crm/CustomerController.java", endpoint.filePath());
        assertEquals(0, endpoint.lineStart());
        assertEquals(0, endpoint.lineEnd());
        assertEquals(List.of("CustomerRequest"), endpoint.requestTypes());
        assertEquals(List.of("CustomerResponse"), endpoint.responseTypes());
        assertEquals(List.of("RestController"), endpoint.annotations());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, endpoint.confidence());
        assertEquals(List.of("endpoint limitation"), endpoint.limitations());
        assertEquals(List.of("read endpoint"), endpoint.suggestedNextReads());
        assertEquals(GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH, limits.maxDepth());
        assertEquals(GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES, limits.maxFiles());
        assertEquals(GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES, limits.maxReadFiles());
        assertEquals(0, limits.readFileCount());
        assertTrue(limits.maxDepthReached());
        assertTrue(limits.maxFilesReached());
        assertTrue(limits.readFileLimitReached());
    }

    @Test
    void shouldKeepUnresolvedReferenceListsNullSafe() {
        var unresolved = new GitLabEndpointUseCaseUnresolvedReference(
                " CustomerRepositoryPort.Query ",
                "\\src\\main\\java\\com\\example\\crm\\CustomerController.java",
                " multiple implementations ",
                List.of(" implements Query ", " "),
                List.of(" CustomerQueryRepository.java ")
        );

        assertEquals("CustomerRepositoryPort.Query", unresolved.symbol());
        assertEquals("src/main/java/com/example/crm/CustomerController.java", unresolved.ownerPath());
        assertEquals("multiple implementations", unresolved.reason());
        assertEquals(List.of("implements Query"), unresolved.searchedKeywords());
        assertEquals(List.of("CustomerQueryRepository.java"), unresolved.candidates());
    }
}
