package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabJavaMapStructResolverTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main"
    );
    private final GitLabEndpointUseCaseSourceSession session = new GitLabEndpointUseCaseSourceSession(
            repositoryPort,
            repository
    );
    private final GitLabJavaSourceResolver sourceResolver = new GitLabJavaSourceResolver();
    private final GitLabJavaMapStructResolver resolver = new GitLabJavaMapStructResolver();

    @Test
    void shouldDetectMapStructMapperWithUsesAndDefaultMethod() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerMapper.java", """
                package com.example.crm.customer;

                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper(uses = {IdentificationMapper.class, AddressMapper.class})
                interface CustomerMapper {
                    CustomerMapper INSTANCE = Mappers.getMapper(CustomerMapper.class);

                    Customer from(CustomerModel model);

                    default CustomerModel map(FormView<CustomerType> view) {
                        return switch (view.type()) {
                            case CUSTOMER_PROFILE -> CustomerProfileMapper.INSTANCE.mapCustomerProfile(view);
                            case MULTILINE -> MultilineModelMapper.INSTANCE.mapMultiline(view);
                        };
                    }
                }
                """);

        var detection = resolver.detectMapper(astFile, "CustomerMapper");

        assertEquals(GitLabJavaMapStructMapperStatus.DETECTED, detection.status());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, detection.confidence());
        assertEquals("CustomerMapper", detection.mapper().simpleName());
        assertEquals("com.example.crm.customer.CustomerMapper", detection.mapper().qualifiedName());
        assertEquals("src/main/java/com/example/crm/customer/CustomerMapper.java", detection.mapper().filePath());
        assertEquals(List.of("IdentificationMapper", "AddressMapper"), detection.mapper().usesTypes());
        assertEquals(List.of("from", "map"), detection.mapper().declaredMethodNames());
        assertEquals(List.of("map"), detection.mapper().defaultMethodNames());
    }

    @Test
    void shouldDetectSingleUsesMapper() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerMapper.java", """
                package com.example.crm.customer;

                @org.mapstruct.Mapper(uses = IdentificationMapper.class)
                interface CustomerMapper {
                }
                """);

        var detection = resolver.detectMapper(astFile, "CustomerMapper");

        assertEquals(GitLabJavaMapStructMapperStatus.DETECTED, detection.status());
        assertEquals(List.of("IdentificationMapper"), detection.mapper().usesTypes());
    }

    @Test
    void shouldFindInstanceMapperCallsAndGetMapperCalls() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerController.java", """
                package com.example.crm.customer;

                import org.mapstruct.factory.Mappers;

                class CustomerController {
                    CustomerMapper mapper = Mappers.getMapper(CustomerMapper.class);

                    CustomerModel getCustomer(Customer customer) {
                        return CustomerMapper.INSTANCE.from(customer);
                    }
                }
                """);

        var calls = resolver.findMapperCalls(astFile);

        assertEquals(2, calls.size());
        assertEquals(GitLabJavaMapStructCallKind.GET_MAPPER, calls.get(0).kind());
        assertEquals("CustomerMapper", calls.get(0).mapperType());
        assertEquals("getMapper", calls.get(0).methodName());
        assertFalse(calls.get(0).switchBranchCandidate());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, calls.get(0).confidence());
        assertEquals(GitLabJavaMapStructCallKind.INSTANCE_METHOD, calls.get(1).kind());
        assertEquals("CustomerMapper", calls.get(1).mapperType());
        assertEquals("from", calls.get(1).methodName());
        assertEquals("CustomerMapper.INSTANCE.from(customer)", calls.get(1).sourceExpression());
        assertFalse(calls.get(1).switchBranchCandidate());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, calls.get(1).confidence());
    }

    @Test
    void shouldMarkMapperCallsInsideSwitchBranchesAsPotential() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerMapper.java", """
                package com.example.crm.customer;

                interface CustomerMapper {
                    default CustomerModel map(FormView<CustomerType> view) {
                        return switch (view.type()) {
                            case CUSTOMER_PROFILE -> CustomerProfileMapper.INSTANCE.mapCustomerProfile(view);
                            case MULTILINE -> MultilineModelMapper.INSTANCE.mapMultiline(view);
                        };
                    }
                }
                """);

        var calls = resolver.findMapperCalls(astFile);

        assertEquals(2, calls.size());
        assertEquals("CustomerProfileMapper", calls.get(0).mapperType());
        assertEquals("mapCustomerProfile", calls.get(0).methodName());
        assertTrue(calls.get(0).switchBranchCandidate());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, calls.get(0).confidence());
        assertEquals("MultilineModelMapper", calls.get(1).mapperType());
        assertEquals("mapMultiline", calls.get(1).methodName());
        assertTrue(calls.get(1).switchBranchCandidate());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, calls.get(1).confidence());
    }

    @Test
    void shouldReturnNotMapperForPlainType() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerFormatter.java", """
                package com.example.crm.customer;

                class CustomerFormatter {
                }
                """);

        var detection = resolver.detectMapper(astFile, "CustomerFormatter");

        assertEquals(GitLabJavaMapStructMapperStatus.NOT_MAPSTRUCT_MAPPER, detection.status());
        assertEquals(List.of("Type is not annotated with MapStruct @Mapper. Type: CustomerFormatter."),
                detection.limitations());
    }

    @Test
    void shouldReturnTypeNotFoundWhenMapperTypeIsMissing() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerFormatter.java", """
                package com.example.crm.customer;

                class CustomerFormatter {
                }
                """);

        var detection = resolver.detectMapper(astFile, "MissingMapper");

        assertEquals(GitLabJavaMapStructMapperStatus.TYPE_NOT_FOUND, detection.status());
        assertEquals(List.of("Type was not found in parsed Java source: MissingMapper."), detection.limitations());
    }

    @Test
    void shouldReturnParseFailedWhenAstFileWasNotParsed() {
        var astFile = new GitLabJavaAstFile(
                "src/main/java/com/example/crm/customer/BrokenMapper.java",
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of("Could not parse Java source.")
        );

        var detection = resolver.detectMapper(astFile, "BrokenMapper");

        assertEquals(GitLabJavaMapStructMapperStatus.PARSE_FAILED, detection.status());
        assertEquals(List.of("Could not parse Java source."), detection.limitations());
        assertTrue(resolver.findMapperCalls(astFile).isEmpty());
    }

    private GitLabJavaAstFile astFile(String path, String content) {
        when(repositoryPort.listRepositoryFiles("CRM", "crm-customer-service", "main", "src/main/java"))
                .thenReturn(List.of(new GitLabRepositoryFile("CRM", "crm-customer-service", "main", path)));
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main", path, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        path,
                        content,
                        false
                ));
        return sourceResolver.astFile(session, path);
    }
}
