package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabJavaInterfaceImplementorResolverTest {

    private final TestRepositoryPort repositoryPort = new TestRepositoryPort();
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main"
    );
    private final GitLabJavaInterfaceImplementorResolver resolver = new GitLabJavaInterfaceImplementorResolver();

    @Test
    void shouldResolveSimplePortImplementation() {
        repositoryPort.add("src/main/java/com/example/crm/customer/UpdateCustomerPort.java", """
                package com.example.crm.customer;

                interface UpdateCustomerPort {
                    void update(Customer customer);
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/UpdateCustomerService.java", """
                package com.example.crm.customer;

                class UpdateCustomerService implements UpdateCustomerPort {
                    public void update(Customer customer) {
                    }
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "UpdateCustomerPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("UpdateCustomerPort", resolution.interfaceName());
        assertTrue(resolution.searchKeywords().contains("implements UpdateCustomerPort"));
        assertEquals(1, resolution.candidates().size());
        assertEquals("UpdateCustomerService", resolution.candidates().get(0).implementationSimpleName());
        assertEquals("com.example.crm.customer.UpdateCustomerService", resolution.candidates().get(0).implementationQualifiedName());
        assertEquals("src/main/java/com/example/crm/customer/UpdateCustomerService.java", resolution.candidates().get(0).filePath());
        assertEquals(List.of("UpdateCustomerPort"), resolution.candidates().get(0).implementedTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.candidates().get(0).confidence());
    }

    @Test
    void shouldResolveNestedRepositoryPortImplementation() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerRepositoryPort.java", """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                    interface Query {
                    }

                    interface Command {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerQueryRepository.java", """
                package com.example.crm.customer;

                class CustomerQueryRepository implements CustomerRepositoryPort.Query {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "CustomerRepositoryPort.Query");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerQueryRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(List.of("CustomerRepositoryPort.Query"), resolution.candidates().get(0).implementedTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.candidates().get(0).confidence());
    }

    @Test
    void shouldResolveConventionalServiceImplementationBeforeFallbackScan() {
        repositoryPort.add("src/main/java/com/example/crm/customer/UpdateCustomerPort.java", """
                package com.example.crm.customer;

                interface UpdateCustomerPort {
                    void update(Customer customer);
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/UpdateCustomerService.java", """
                package com.example.crm.customer;

                class UpdateCustomerService implements UpdateCustomerPort {
                    public void update(Customer customer) {
                    }
                }
                """);
        for (var index = 0; index < 100; index++) {
            repositoryPort.add("src/main/java/com/example/crm/noise/CustomerService" + index + ".java", """
                    package com.example.crm.noise;

                    class CustomerService%s implements OtherPort {
                    }
                    """.formatted(index));
        }

        var session = session();
        var resolution = resolver.resolveImplementors(session, "UpdateCustomerPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("UpdateCustomerService", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(1, session.readFileCount());
    }

    @Test
    void shouldResolveDomainInterfaceImplementedByDerivedModelName() {
        repositoryPort.add("src/main/java/com/example/crm/customer/behaviour/UpdateCustomer.java", """
                package com.example.crm.customer.behaviour;

                interface UpdateCustomer {
                    void update(CustomerModel customerModel);
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerModel.java", """
                package com.example.crm.customer;

                import com.example.crm.customer.behaviour.UpdateCustomer;

                class CustomerModel implements UpdateCustomer {
                    public void update(CustomerModel customerModel) {
                    }
                }
                """);
        for (var index = 0; index < 100; index++) {
            repositoryPort.add("src/main/java/com/example/crm/noise/UpdateCustomerNoise" + index + ".java", """
                    package com.example.crm.noise;

                    class UpdateCustomerNoise%s {
                    }
                    """.formatted(index));
        }

        var resolution = resolver.resolveImplementors(
                session(),
                "com.example.crm.customer.behaviour.UpdateCustomer"
        );

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerModel", resolution.candidates().get(0).implementationSimpleName());
        assertEquals("com.example.crm.customer.CustomerModel", resolution.candidates().get(0).implementationQualifiedName());
        assertEquals(List.of("UpdateCustomer"), resolution.candidates().get(0).implementedTypes());
    }

    @Test
    void shouldResolveSubtypesExtendingDomainModel() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerModel.java", """
                package com.example.crm.customer;

                class CustomerModel {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CivilLawCustomerModel.java", """
                package com.example.crm.customer;

                class CivilLawCustomerModel extends CustomerModel {
                    void update(CustomerModel customerModel) {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerModelMapper.java", """
                package com.example.crm.customer;

                class CustomerModelMapper {
                }
                """);

        var resolution = resolver.resolveSubtypes(
                session(),
                "com.example.crm.customer.CustomerModel"
        );

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CivilLawCustomerModel", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(List.of("CustomerModel"), resolution.candidates().get(0).implementedTypes());
    }

    @Test
    void shouldResolveSubtypesByExtendsSyntaxWhenFileNameDoesNotContainBaseTypeName() {
        repositoryPort.add("src/main/java/com/example/crm/customer/BaseCustomerData.java", """
                package com.example.crm.customer;

                class BaseCustomerData {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CorporateCustomer.java", """
                package com.example.crm.customer;

                class CorporateCustomer extends BaseCustomerData {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/IndividualCustomer.java", """
                package com.example.crm.customer;

                class IndividualCustomer extends BaseCustomerData {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/BaseCustomerDataMapper.java", """
                package com.example.crm.customer;

                class BaseCustomerDataMapper {
                }
                """);

        var resolution = resolver.resolveSubtypes(
                session(),
                "com.example.crm.customer.BaseCustomerData"
        );

        assertEquals(GitLabJavaImplementorResolutionStatus.AMBIGUOUS, resolution.status());
        assertEquals(List.of("CorporateCustomer", "IndividualCustomer"),
                resolution.candidates().stream()
                        .map(GitLabJavaImplementorCandidate::implementationSimpleName)
                        .sorted()
                        .toList());
        assertTrue(resolution.candidates().stream()
                .allMatch(candidate -> candidate.implementedTypes().equals(List.of("BaseCustomerData"))));
    }

    @Test
    void shouldResolveNestedCommandRepositoryBeforeFallbackScan() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerRepositoryPort.java", """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                    interface Command {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerCommandRepository.java", """
                package com.example.crm.customer;

                class CustomerCommandRepository implements CustomerRepositoryPort.Command {
                }
                """);
        for (var index = 0; index < 100; index++) {
            repositoryPort.add("src/main/java/com/example/crm/noise/CustomerService" + index + ".java", """
                    package com.example.crm.noise;

                    class CustomerService%s implements OtherPort {
                    }
                    """.formatted(index));
        }

        var session = session();
        var resolution = resolver.resolveImplementors(session, "CustomerRepositoryPort.Command");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerCommandRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(1, session.readFileCount());
    }

    @Test
    void shouldResolveNestedInterfaceImportedBySimpleName() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerRepositoryPort.java", """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                    interface Query {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerQueryRepository.java", """
                package com.example.crm.customer;

                import com.example.crm.customer.CustomerRepositoryPort.Query;

                class CustomerQueryRepository implements Query {
                }
                """);

        var resolution = resolver.resolveImplementors(
                session(),
                "com.example.crm.customer.CustomerRepositoryPort.Query"
        );

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerQueryRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(List.of("Query"), resolution.candidates().get(0).implementedTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, resolution.candidates().get(0).confidence());
    }

    @Test
    void shouldResolveSealedInterfaceImplementationsFromPermitsBeforeFallbackScan() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerConfigurationCache.java", """
                package com.example.crm.customer;

                sealed interface CustomerConfigurationCache
                        permits CustomerConfigurationSimpleLocalCache {
                    CustomerConfiguration newest();
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerConfigurationSimpleLocalCache.java", """
                package com.example.crm.customer;

                final class CustomerConfigurationSimpleLocalCache implements CustomerConfigurationCache {
                    public CustomerConfiguration newest() {
                        return new CustomerConfiguration();
                    }
                }
                """);
        for (var index = 0; index < 100; index++) {
            repositoryPort.add("src/main/java/com/example/crm/noise/CustomerConfigurationCacheNoise" + index + ".java", """
                    package com.example.crm.noise;

                    class CustomerConfigurationCacheNoise%s {
                    }
                    """.formatted(index));
        }

        var resolution = resolver.resolveImplementors(session(), "CustomerConfigurationCache");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerConfigurationSimpleLocalCache", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(List.of("CustomerConfigurationCache"), resolution.candidates().get(0).implementedTypes());
        assertTrue(resolution.limitations().isEmpty());
    }

    @Test
    void shouldRejectDifferentNestedInterfaceWithSameSimpleName() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerRepositoryPort.java", """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                    interface Command {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerDomainCommandRepository.java", """
                package com.example.crm.customer;

                class CustomerDomainCommandRepository implements CustomerRepositoryPort.Command {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/OtherRepositoryPort.java", """
                package com.example.crm.customer;

                interface OtherRepositoryPort {
                    interface Command {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/OtherDomainCommandRepository.java", """
                package com.example.crm.customer;

                class OtherDomainCommandRepository implements OtherRepositoryPort.Command {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "CustomerRepositoryPort.Command");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerDomainCommandRepository", resolution.candidates().get(0).implementationSimpleName());
        assertFalse(resolution.candidates().stream()
                .anyMatch(candidate -> "OtherDomainCommandRepository".equals(candidate.implementationSimpleName())));
    }

    @Test
    void shouldReturnAmbiguousWhenMultipleImplementationsMatch() {
        repositoryPort.add("src/main/java/com/example/crm/customer/UpdateCustomerService.java", """
                package com.example.crm.customer;

                class UpdateCustomerService implements UpdateCustomerPort {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/LegacyUpdateCustomerService.java", """
                package com.example.crm.customer;

                class LegacyUpdateCustomerService implements UpdateCustomerPort {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "UpdateCustomerPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.AMBIGUOUS, resolution.status());
        assertEquals(2, resolution.candidates().size());
        assertEquals(List.of(
                        "src/main/java/com/example/crm/customer/LegacyUpdateCustomerService.java",
                        "src/main/java/com/example/crm/customer/UpdateCustomerService.java"
                ),
                resolution.candidates().stream().map(GitLabJavaImplementorCandidate::filePath).toList());
        assertEquals(List.of("More than one implementation matched interface UpdateCustomerPort."),
                resolution.limitations());
    }

    @Test
    void shouldReturnNotFoundWhenImplementationIsMissing() {
        repositoryPort.add("src/main/java/com/example/crm/customer/UpdateCustomerPort.java", """
                package com.example.crm.customer;

                interface UpdateCustomerPort {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerQueryRepository.java", """
                package com.example.crm.customer;

                class CustomerQueryRepository implements CustomerRepositoryPort.Query {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "UpdateCustomerPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.NOT_FOUND, resolution.status());
        assertTrue(resolution.candidates().isEmpty());
        assertEquals(List.of("No implementation was found for interface UpdateCustomerPort."),
                resolution.limitations());
    }

    @Test
    void shouldFindPackagePrivateImplementationAmongManyTypesInOneFile() {
        repositoryPort.add("src/main/java/com/example/crm/customer/CustomerRepositories.java", """
                package com.example.crm.customer;

                class CustomerRepositorySupport {
                }

                class CustomerCommandRepository implements CustomerRepositoryPort.Command {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "CustomerRepositoryPort.Command");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CustomerCommandRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals("CustomerCommandRepository", resolution.candidates().get(0).implementationRelativeName());
        assertEquals(GitLabJavaTypeKind.CLASS, resolution.candidates().get(0).implementationKind());
    }

    @Test
    void shouldReturnInvalidRequestForBlankInterfaceName() {
        var resolution = resolver.resolveImplementors(session(), " ");

        assertEquals(GitLabJavaImplementorResolutionStatus.INVALID_REQUEST, resolution.status());
        assertEquals(List.of("Interface name is required."), resolution.limitations());
    }

    private GitLabEndpointUseCaseSourceSession session() {
        return new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);
    }

    private static final class TestRepositoryPort implements GitLabRepositoryPort {

        private final Map<String, String> files = new LinkedHashMap<>();

        private void add(String filePath, String content) {
            files.put(filePath, content);
        }

        @Override
        public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
            var keywords = query.keywords() != null ? query.keywords() : List.<String>of();
            var projectNames = query.projectNames() != null ? query.projectNames() : List.<String>of();
            if (keywords.isEmpty()) {
                return List.of();
            }
            return files.entrySet().stream()
                    .filter(entry -> keywords.stream().anyMatch(keyword -> entry.getValue().contains(keyword)))
                    .map(entry -> new GitLabRepositoryFileCandidate(
                            query.group(),
                            projectNames.isEmpty() ? "crm-customer-service" : projectNames.get(0),
                            query.branch(),
                            entry.getKey(),
                            "matched test keyword",
                            100
                    ))
                    .toList();
        }

        @Override
        public List<GitLabRepositoryFile> listRepositoryFiles(
                String group,
                String projectName,
                String branch,
                String pathPrefix
        ) {
            return files.keySet().stream()
                    .filter(filePath -> pathPrefix == null || filePath.startsWith(pathPrefix))
                    .map(filePath -> new GitLabRepositoryFile(group, projectName, branch, filePath))
                    .toList();
        }

        @Override
        public GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileContent(
                    group,
                    projectName,
                    branch,
                    filePath,
                    files.getOrDefault(filePath, ""),
                    false
            );
        }

        @Override
        public GitLabRepositoryFileChunk readFileChunk(
                String group,
                String projectName,
                String branch,
                String filePath,
                int startLine,
                int endLine,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileChunk(
                    group,
                    projectName,
                    branch,
                    filePath,
                    startLine,
                    endLine,
                    startLine,
                    endLine,
                    0,
                    "",
                    false
            );
        }
    }
}
