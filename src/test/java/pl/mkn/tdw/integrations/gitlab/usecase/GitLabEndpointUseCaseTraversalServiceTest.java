package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseTraversalServiceTest {

    private static final String GROUP = "CRM";
    private static final String PROJECT = "crm-customer-service";
    private static final String BRANCH = "main";
    private static final String SOURCE_PREFIX = "src/main/java";

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            GROUP,
            PROJECT,
            BRANCH
    );
    private final GitLabEndpointUseCaseTraversalService traversalService = new GitLabEndpointUseCaseTraversalService();

    @Test
    void shouldBuildCompactGetEndpointUseCaseContext() {
        var files = crmCustomerSources();
        stubRepository(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                endpoint("GET /api/customers/{customerId} -> CustomerController#getCustomer", "GET", "getCustomer"),
                new GitLabEndpointUseCaseLimits(6, 30, 80, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.CONTROLLER,
                byPath.get(path("api/CustomerController.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_PORT,
                byPath.get(path("domain/CustomerRepositoryPort.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/CustomerQueryRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/CustomerMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/CustomerProfileMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/MultilineModelMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.PROJECTION,
                byPath.get(path("domain/CustomerProfileFormView.java")).role());
        assertTrue(byPath.containsKey(path("domain/MultilineFormView.java")), byPath.keySet().toString());
        assertEquals(GitLabEndpointUseCaseFileRole.PROJECTION,
                byPath.get(path("domain/MultilineFormView.java")).role());
        assertTrue(byPath.get(path("api/CustomerController.java")).symbols().contains("getCustomerModel"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.MAPPER_CALL
                        && relation.to().contains("CustomerMapper#from")));
        assertFalse(result.limits().maxDepthReached());
        assertFalse(result.limits().maxFilesReached());
    }

    @Test
    void shouldBuildCompactPutEndpointUseCaseContextWithUseCaseServiceAndDomainMethod() {
        var files = crmCustomerSources();
        stubRepository(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                endpoint("PUT /api/customers/{customerId} -> CustomerController#updateCustomer", "PUT", "updateCustomer"),
                new GitLabEndpointUseCaseLimits(6, 35, 90, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_PORT,
                byPath.get(path("application/UpdateCustomerPort.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                byPath.get(path("application/UpdateCustomerService.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/CustomerQueryRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/CustomerCommandRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("domain/Customer.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/CustomerMapper.java")).role());
        var updateServiceMethod = byPath.get(path("application/UpdateCustomerService.java")).methods().stream()
                .filter(method -> "update".equals(method.methodName()))
                .filter(method -> method.parameterTypes().equals(List.of("Customer")))
                .findFirst()
                .orElseThrow();
        assertEquals("com.example.crm.customer.application.UpdateCustomerService",
                updateServiceMethod.declaringTypeQualifiedName());
        assertEquals("void update(Customer customer)", updateServiceMethod.signature());
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE, updateServiceMethod.role());
        assertEquals(1, updateServiceMethod.depth());
        assertTrue(updateServiceMethod.lineStart() > 0);
        assertTrue(updateServiceMethod.lineEnd() >= updateServiceMethod.lineStart());
        assertTrue(byPath.get(path("domain/Customer.java")).symbols().contains("update"));
        assertTrue(byPath.get(path("domain/Customer.java")).symbols().contains("calculateStatus"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION
                        && relation.to().contains("UpdateCustomerService#update")));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.DOMAIN_METHOD_CALL
                        && relation.to().contains("Customer#update")));
        assertFalse(byPath.containsKey(path("domain/DecisionChangeEvent.java")), byPath.keySet().toString());
        assertFalse(byPath.containsKey(path("domain/CreditCaseUpdatedEvent.java")), byPath.keySet().toString());
        assertFalse(result.relations().stream()
                .anyMatch(relation -> relation.to().contains("DecisionChangeEvent#")));
        assertFalse(result.relations().stream()
                .anyMatch(relation -> relation.to().contains("CreditCaseUpdatedEvent#")));
    }

    @Test
    void shouldTraverseDomainInterfaceImplementationAndSubtypeOverrides() {
        var sources = domainInterfaceSources();
        stubRepository(sources);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                new GitLabEndpointUseCaseEndpointContext(
                        "PUT /api/customers/{customerId} -> CustomerController#updateCustomer",
                        List.of("PUT"),
                        "/api/customers/{customerId}",
                        null,
                        "com.example.crm.customer.api.CustomerController",
                        "updateCustomer",
                        path("api/CustomerController.java"),
                        1,
                        20,
                        List.of("CustomerModel model"),
                        List.of("CustomerModel"),
                        List.of("OpenAPI contract src/main/resources/openapi/customer-api.yaml"),
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of(),
                        List.of("src/main/resources/openapi/customer-api.yaml via gitlab_read_repository_file")
                ),
                new GitLabEndpointUseCaseLimits(7, 35, 80, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("domain/CustomerModel.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("domain/CivilLawCustomerModel.java")).role());
        assertTrue(byPath.get(path("domain/CustomerModel.java")).symbols().contains("update"));
        assertTrue(byPath.get(path("domain/CivilLawCustomerModel.java")).symbols().contains("update"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION
                        && relation.to().contains("CustomerModel#update")));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION
                        && relation.to().contains("CivilLawCustomerModel#update")));
        assertFalse(result.unresolved().stream()
                .anyMatch(reference -> "UpdateCustomer".equals(reference.symbol())
                        || String.valueOf(reference.symbol()).endsWith(".UpdateCustomer")));
    }

    @Test
    void shouldMapGeneratedOpenApiWebModelToYamlContractInsteadOfJavaSource() {
        var sources = generatedOpenApiModelSources();
        stubRepository(sources);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                new GitLabEndpointUseCaseEndpointContext(
                        "PUT /api/customers/{customerId} -> CustomerController#updateCustomer",
                        List.of("PUT"),
                        "/api/customers/{customerId}",
                        null,
                        "com.example.crm.customer.api.CustomerController",
                        "updateCustomer",
                        path("api/CustomerController.java"),
                        1,
                        20,
                        List.of("CustomerWebModel customerWebModel"),
                        List.of("CustomerWebModel"),
                        List.of("OpenAPI contract src/main/resources/openapi/customer-api.yaml"),
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of("Endpoint mapping resolved from OpenAPI YAML contract, not Java annotations."),
                        List.of("src/main/resources/openapi/customer-api.yaml via gitlab_read_repository_file")
                ),
                new GitLabEndpointUseCaseLimits(5, 20, 40, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        var contract = byPath.get("src/main/resources/openapi/customer-api.yaml");
        assertEquals(GitLabEndpointUseCaseFileRole.OPENAPI_CONTRACT, contract.role());
        assertTrue(contract.symbols().contains("CustomerWebModel"));
        assertFalse(result.unresolved().stream()
                .anyMatch(reference -> String.valueOf(reference.symbol()).contains("CustomerWebModel")));
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("CustomerWebModel")));
    }

    @Test
    void shouldResolveThisScopedInjectedFieldCallInMultiModuleRepository() {
        var rootRepository = new GitLabEndpointUseCaseRepositoryContext(
                GROUP,
                PROJECT,
                BRANCH
        );
        var controllerPath = "crm-customer-service/customer-adapter/src/main/java/com/example/crm/customer/api/CustomerController.java";
        var portPath = "crm-customer-service/customer-application/src/main/java/com/example/crm/customer/application/UpdateCustomerPort.java";
        var servicePath = "crm-customer-service/customer-application/src/main/java/com/example/crm/customer/application/UpdateCustomerService.java";
        var customerPath = "crm-customer-service/customer-domain/src/main/java/com/example/crm/customer/domain/Customer.java";
        var sources = new LinkedHashMap<String, String>();
        sources.put(controllerPath, """
                package com.example.crm.customer.api;

                import com.example.crm.customer.application.UpdateCustomerPort;
                import com.example.crm.customer.domain.Customer;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class CustomerController {
                    private final UpdateCustomerPort updateCustomerPort;

                    public void updateCustomer(Customer customer) {
                        this.updateCustomerPort.update(customer);
                    }
                }
                """);
        sources.put(portPath, """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.Customer;

                interface UpdateCustomerPort {
                    void update(Customer customer);
                }
                """);
        sources.put(servicePath, """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.Customer;
                import org.springframework.stereotype.Service;

                @Service
                class UpdateCustomerService implements UpdateCustomerPort {
                    public void update(Customer customer) {
                        customer.update();
                    }
                }
                """);
        sources.put(customerPath, """
                package com.example.crm.customer.domain;

                public class Customer {
                    public void update() {
                    }
                }
                """);
        stubRepository(rootRepository, sources);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, rootRepository);

        var result = traversalService.traverse(
                session,
                new GitLabEndpointUseCaseEndpointContext(
                        "PUT /api/customers/{customerId} -> CustomerController#updateCustomer",
                        List.of("PUT"),
                        "/api/customers/{customerId}",
                        null,
                        "com.example.crm.customer.api.CustomerController",
                        "updateCustomer",
                        controllerPath,
                        1,
                        20,
                        List.of("Customer"),
                        List.of(),
                        List.of(),
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of(),
                        List.of()
                ),
                new GitLabEndpointUseCaseLimits(5, 20, 40, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_PORT, byPath.get(portPath).role());
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE, byPath.get(servicePath).role());
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL, byPath.get(customerPath).role());
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INJECTED_PORT_CALL
                        && relation.to().contains("UpdateCustomerPort#update")));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION
                        && relation.to().contains("UpdateCustomerService#update")));
    }

    @Test
    void shouldTraverseAutowiredSetterInjectedBranchAndConstructorInjectedBranch() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("api/CustomerController.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.application.CustomerAuditFacade;
                import com.example.crm.customer.application.CustomerActivationService;
                import lombok.RequiredArgsConstructor;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.context.annotation.Lazy;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class CustomerController {
                    private final CustomerAuditFacade customerAuditFacade;
                    private CustomerActivationService customerActivationService;

                    @Autowired
                    public void setCustomerActivationService(@Lazy CustomerActivationService customerActivationService) {
                        this.customerActivationService = customerActivationService;
                    }

                    public void activateCustomer(CustomerActivationRequest request) {
                        customerActivationService.activate(request);
                        customerAuditFacade.recordActivation(request);
                    }
                }
                """);
        sources.put(path("api/CustomerActivationRequest.java"), """
                package com.example.crm.customer.api;

                record CustomerActivationRequest(String customerId) {
                }
                """);
        sources.put(path("application/CustomerActivationService.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.api.CustomerActivationRequest;
                import com.example.crm.customer.domain.CustomerCommandRepositoryPort;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;

                @Service
                @RequiredArgsConstructor
                class CustomerActivationService {
                    private final CustomerCommandRepositoryPort customerCommandRepository;

                    public void activate(CustomerActivationRequest request) {
                        customerCommandRepository.saveActivation(request.customerId());
                    }
                }
                """);
        sources.put(path("application/CustomerAuditFacade.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.api.CustomerActivationRequest;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Component;

                @Component
                @RequiredArgsConstructor
                class CustomerAuditFacade {
                    private final CustomerAuditService customerAuditService;

                    public void recordActivation(CustomerActivationRequest request) {
                        customerAuditService.recordActivation(request.customerId());
                    }
                }
                """);
        sources.put(path("application/CustomerAuditService.java"), """
                package com.example.crm.customer.application;

                import org.springframework.stereotype.Service;

                @Service
                class CustomerAuditService {
                    public void recordActivation(String customerId) {
                    }
                }
                """);
        sources.put(path("domain/CustomerCommandRepositoryPort.java"), """
                package com.example.crm.customer.domain;

                interface CustomerCommandRepositoryPort {
                    void saveActivation(String customerId);
                }
                """);
        sources.put(path("adapter/out/CustomerJpaCommandRepository.java"), """
                package com.example.crm.customer.adapter.out;

                import com.example.crm.customer.domain.CustomerCommandRepositoryPort;
                import org.springframework.stereotype.Repository;

                @Repository
                class CustomerJpaCommandRepository implements CustomerCommandRepositoryPort {
                    public void saveActivation(String customerId) {
                    }
                }
                """);
        stubRepository(sources);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                new GitLabEndpointUseCaseEndpointContext(
                        "POST /api/customers/activation -> CustomerController#activateCustomer",
                        List.of("POST"),
                        "/api/customers/activation",
                        null,
                        "com.example.crm.customer.api.CustomerController",
                        "activateCustomer",
                        path("api/CustomerController.java"),
                        1,
                        30,
                        List.of("CustomerActivationRequest"),
                        List.of(),
                        List.of(),
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of(),
                        List.of()
                ),
                new GitLabEndpointUseCaseLimits(6, 30, 80, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                byPath.get(path("application/CustomerActivationService.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("application/CustomerAuditFacade.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                byPath.get(path("application/CustomerAuditService.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_PORT,
                byPath.get(path("domain/CustomerCommandRepositoryPort.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/CustomerJpaCommandRepository.java")).role());
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INJECTED_PORT_CALL
                        && relation.to().contains("CustomerActivationService#activate")));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INJECTED_PORT_CALL
                        && relation.to().contains("CustomerAuditFacade#recordActivation")));
        assertFalse(result.unresolved().stream()
                .anyMatch(reference -> String.valueOf(reference.symbol()).contains("CustomerActivationService")));
    }

    @Test
    void shouldTreatLombokBuilderAsGeneratedTerminalCall() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("api/CustomerController.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.application.CreateCustomerService;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class CustomerController {
                    private final CreateCustomerService createCustomerService;

                    public void createCustomer(String customerId) {
                        createCustomerService.createCustomer(customerId);
                    }
                }
                """);
        sources.put(path("application/CreateCustomerService.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.CustomerCreateModel;
                import org.springframework.stereotype.Service;

                @Service
                class CreateCustomerService {
                    public void createCustomer(String customerId) {
                        CustomerCreateModel model = CustomerCreateModel.builder()
                                .customerId(customerId)
                                .build();
                    }
                }
                """);
        sources.put(path("domain/CustomerCreateModel.java"), """
                package com.example.crm.customer.domain;

                import lombok.Builder;

                public class CustomerCreateModel {
                    private final String customerId;

                    @Builder
                    public CustomerCreateModel(String customerId) {
                        this.customerId = customerId;
                        initializeCustomerId();
                    }

                    private void initializeCustomerId() {
                    }
                }
                """);
        stubRepository(sources);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                new GitLabEndpointUseCaseEndpointContext(
                        "POST /api/customers -> CustomerController#createCustomer",
                        List.of("POST"),
                        "/api/customers",
                        null,
                        "com.example.crm.customer.api.CustomerController",
                        "createCustomer",
                        path("api/CustomerController.java"),
                        1,
                        20,
                        List.of("String customerId"),
                        List.of(),
                        List.of(),
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of(),
                        List.of()
                ),
                new GitLabEndpointUseCaseLimits(5, 20, 40, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("domain/CustomerCreateModel.java")).role());
        assertTrue(byPath.get(path("domain/CustomerCreateModel.java")).symbols().contains("builder"));
        assertFalse(result.unresolved().stream()
                .anyMatch(reference -> String.valueOf(reference.symbol()).contains("#builder")));
        assertFalse(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("#builder")));
    }

    @Test
    void shouldRespectMaxDepthLimit() {
        var files = crmCustomerSources();
        stubRepository(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                endpoint("PUT /api/customers/{customerId} -> CustomerController#updateCustomer", "PUT", "updateCustomer"),
                new GitLabEndpointUseCaseLimits(1, 35, 90, false, false, 0, false)
        );

        assertTrue(result.limits().maxDepthReached());
        var customer = filesByPath(result).get(path("domain/Customer.java"));
        assertTrue(customer == null || !customer.symbols().contains("calculateStatus"));
    }

    private GitLabEndpointUseCaseEndpointContext endpoint(String endpointId, String method, String handlerMethod) {
        return new GitLabEndpointUseCaseEndpointContext(
                endpointId,
                List.of(method),
                "/api/customers/{customerId}",
                null,
                "com.example.crm.customer.api.CustomerController",
                handlerMethod,
                path("api/CustomerController.java"),
                1,
                20,
                List.of("CustomerModel"),
                List.of("CustomerModel"),
                List.of("OpenAPI contract src/main/resources/openapi/customer-api.yaml"),
                GitLabEndpointUseCaseConfidence.HIGH,
                List.of(),
                List.of("src/main/resources/openapi/customer-api.yaml via gitlab_read_repository_file")
        );
    }

    private void stubRepository(Map<String, String> sources) {
        stubRepository(repository, sources);
    }

    private void stubRepository(GitLabEndpointUseCaseRepositoryContext repository, Map<String, String> sources) {
        when(repositoryPort.listRepositoryFiles(
                repository.group(),
                repository.projectName(),
                repository.branch(),
                null
        ))
                .thenReturn(sources.keySet().stream()
                        .map(filePath -> new GitLabRepositoryFile(
                                repository.group(),
                                repository.projectName(),
                                repository.branch(),
                                filePath
                        ))
                        .toList());
        sources.forEach((filePath, content) -> when(repositoryPort.readFile(
                repository.group(),
                repository.projectName(),
                repository.branch(),
                filePath,
                120_000
        )).thenReturn(new GitLabRepositoryFileContent(
                repository.group(),
                repository.projectName(),
                repository.branch(),
                filePath,
                content,
                false
        )));
        when(repositoryPort.searchCandidateFiles(any(GitLabRepositorySearchQuery.class)))
                .thenAnswer(invocation -> {
                    GitLabRepositorySearchQuery query = invocation.getArgument(0);
                    var keywords = query.keywords() != null ? query.keywords() : List.<String>of();
                    var projectNames = query.projectNames() != null ? query.projectNames() : List.<String>of();
                    if (keywords.isEmpty()) {
                        return List.of();
                    }
                    return sources.entrySet().stream()
                            .filter(entry -> keywords.stream().anyMatch(keyword -> entry.getValue().contains(keyword)))
                            .map(entry -> new GitLabRepositoryFileCandidate(
                                    query.group(),
                                    projectNames.isEmpty() ? repository.projectName() : projectNames.get(0),
                                    query.branch(),
                                    entry.getKey(),
                                    "matched test keyword",
                                    100
                            ))
                            .toList();
                });
    }

    private Map<String, GitLabEndpointUseCaseFileCandidate> filesByPath(GitLabEndpointUseCaseContextResult result) {
        return result.files().stream()
                .collect(Collectors.toMap(
                        GitLabEndpointUseCaseFileCandidate::path,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> crmCustomerSources() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("api/CustomerController.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.application.UpdateCustomerPort;
                import com.example.crm.customer.domain.FormView;
                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import com.example.crm.customer.domain.CustomerType;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class CustomerController implements CustomerApi {
                    private final CustomerRepositoryPort.Query customerQueryRepository;
                    private final UpdateCustomerPort updateCustomerPort;

                    public CustomerModel getCustomer(String customerId) {
                        return getCustomerModel(customerQueryRepository.getCustomerFormView(customerId));
                    }

                    public CustomerModel updateCustomer(String customerId, CustomerModel model) {
                        Customer customer = CustomerMapper.INSTANCE.from(model);
                        updateCustomerPort.update(customer);
                        return getCustomerModel(customerQueryRepository.getCustomerFormView(customerId));
                    }

                    private String getTtaId(String customerId) {
                        return customerId;
                    }

                    private CustomerModel getCustomerModel(FormView<CustomerType> formView) {
                        return CustomerMapper.INSTANCE.from(formView);
                    }
                }
                """);
        sources.put(path("api/CustomerApi.java"), """
                package com.example.crm.customer.api;

                interface CustomerApi {
                    CustomerModel getCustomer(String customerId);
                    CustomerModel updateCustomer(String customerId, CustomerModel model);
                }
                """);
        sources.put(path("api/CustomerModel.java"), """
                package com.example.crm.customer.api;

                record CustomerModel(String type) {
                }
                """);
        sources.put(path("api/CustomerMapper.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.domain.FormView;
                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerType;
                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper
                interface CustomerMapper {
                    CustomerMapper INSTANCE = Mappers.getMapper(CustomerMapper.class);

                    default Customer from(CustomerModel model) {
                        return switch (model.type()) {
                            case "CUSTOMER_PROFILE" -> CustomerProfileMapper.INSTANCE.fromCustomerProfile(model);
                            default -> MultilineModelMapper.INSTANCE.fromMultiline(model);
                        };
                    }

                    default CustomerModel from(FormView<CustomerType> formView) {
                        return switch (formView.type()) {
                            case CUSTOMER_PROFILE -> CustomerProfileMapper.INSTANCE.toModel(formView);
                            case MULTILINE -> MultilineModelMapper.INSTANCE.toModel(formView);
                        };
                    }
                }
                """);
        sources.put(path("api/CustomerProfileMapper.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.domain.FormView;
                import com.example.crm.customer.domain.CustomerProfile;
                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerType;
                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper
                interface CustomerProfileMapper {
                    CustomerProfileMapper INSTANCE = Mappers.getMapper(CustomerProfileMapper.class);

                    default Customer fromCustomerProfile(CustomerModel model) {
                        return new CustomerProfile();
                    }

                    CustomerModel toModel(com.example.crm.customer.domain.CustomerProfileFormView formView);
                }
                """);
        sources.put(path("api/MultilineModelMapper.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.domain.FormView;
                import com.example.crm.customer.domain.Multiline;
                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerType;
                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper
                interface MultilineModelMapper {
                    MultilineModelMapper INSTANCE = Mappers.getMapper(MultilineModelMapper.class);

                    default Customer fromMultiline(CustomerModel model) {
                        return new Multiline();
                    }

                    CustomerModel toModel(com.example.crm.customer.domain.MultilineFormView formView);
                }
                """);
        sources.put(path("application/UpdateCustomerPort.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.Customer;

                interface UpdateCustomerPort {
                    void update(Customer customer);
                }
                """);
        sources.put(path("application/UpdateCustomerService.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.CreditCaseUpdatedEvent;
                import com.example.crm.customer.domain.DecisionChangeEvent;
                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import lombok.RequiredArgsConstructor;
                import org.springframework.context.event.EventListener;
                import org.springframework.stereotype.Service;

                @Service
                @RequiredArgsConstructor
                class UpdateCustomerService implements UpdateCustomerPort {
                    private final CustomerRepositoryPort.Query queryRepository;
                    private final CustomerRepositoryPort.Command commandRepository;

                    public void update(Customer customer) {
                        Customer current = queryRepository.getCustomerForUpdate(customer.customerId());
                        customer.update(current);
                        commandRepository.update(customer);
                    }

                    @EventListener
                    void update(DecisionChangeEvent event) {
                        event.recalculateDecision();
                    }

                    @EventListener
                    void update(CreditCaseUpdatedEvent event) {
                        event.refreshCreditCase();
                    }
                }
                """);
        sources.put(path("domain/CustomerRepositoryPort.java"), """
                package com.example.crm.customer.domain;

                public interface CustomerRepositoryPort {
                    interface Query {
                        FormView<CustomerType> getCustomerFormView(String customerId);
                        Customer getCustomerForUpdate(String customerId);
                    }

                    interface Command {
                        void update(Customer customer);
                    }
                }
                """);
        sources.put(path("adapter/out/CustomerQueryRepository.java"), """
                package com.example.crm.customer.adapter.out;

                import com.example.crm.customer.domain.FormView;
                import com.example.crm.customer.domain.CustomerProfileFormView;
                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerEntity;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import com.example.crm.customer.domain.CustomerType;
                import lombok.RequiredArgsConstructor;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                @Repository
                @RequiredArgsConstructor
                class CustomerQueryRepository implements CustomerRepositoryPort.Query {
                    private final CustomerQueryJpaRepository customerQueryJpaRepository;
                    private final CustomerProfileQueryJpaRepository customerProfileQueryJpaRepository;

                    public FormView<CustomerType> getCustomerFormView(String customerId) {
                        customerQueryJpaRepository.findByCustomerId(customerId);
                        return new CustomerProfileFormView();
                    }

                    public Customer getCustomerForUpdate(String customerId) {
                        customerQueryJpaRepository.findByCustomerId(customerId);
                        return new Customer();
                    }
                }

                interface CustomerQueryJpaRepository extends JpaRepository<CustomerEntity, Long> {
                    CustomerEntity findByCustomerId(String customerId);
                }

                interface CustomerProfileQueryJpaRepository extends JpaRepository<CustomerProfileFormView, Long> {
                }
                """);
        sources.put(path("adapter/out/CustomerCommandRepository.java"), """
                package com.example.crm.customer.adapter.out;

                import com.example.crm.customer.domain.Customer;
                import com.example.crm.customer.domain.CustomerEntity;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import lombok.RequiredArgsConstructor;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                @Repository
                @RequiredArgsConstructor
                class CustomerCommandRepository implements CustomerRepositoryPort.Command {
                    private final CustomerCommandJpaRepository customerCommandJpaRepository;

                    public void update(Customer customer) {
                        customerCommandJpaRepository.save(customer);
                    }
                }

                interface CustomerCommandJpaRepository extends JpaRepository<CustomerEntity, Long> {
                }
                """);
        sources.put(path("domain/FormView.java"), """
                package com.example.crm.customer.domain;

                interface FormView<T> {
                    T type();
                }
                """);
        sources.put(path("domain/CustomerProfileFormView.java"), """
                package com.example.crm.customer.domain;

                public class CustomerProfileFormView implements FormView<CustomerType> {
                    public CustomerType type() {
                        return CustomerType.CUSTOMER_PROFILE;
                    }
                }
                """);
        sources.put(path("domain/MultilineFormView.java"), """
                package com.example.crm.customer.domain;

                public class MultilineFormView implements FormView<CustomerType> {
                    public CustomerType type() {
                        return CustomerType.MULTILINE;
                    }
                }
                """);
        sources.put(path("domain/Customer.java"), """
                package com.example.crm.customer.domain;

                public class Customer {
                    public String customerId() {
                        return "customer-1";
                    }

                    public void update(Customer current) {
                        calculateStatus();
                    }

                    private CustomerStatus calculateStatus() {
                        return CustomerStatus.ACTIVE;
                    }
                }
                """);
        sources.put(path("domain/CustomerProfile.java"), """
                package com.example.crm.customer.domain;

                public class CustomerProfile extends Customer {
                }
                """);
        sources.put(path("domain/Multiline.java"), """
                package com.example.crm.customer.domain;

                public class Multiline extends Customer {
                }
                """);
        sources.put(path("domain/CustomerEntity.java"), """
                package com.example.crm.customer.domain;

                public class CustomerEntity {
                }
                """);
        sources.put(path("domain/CustomerStatus.java"), """
                package com.example.crm.customer.domain;

                enum CustomerStatus {
                    ACTIVE
                }
                """);
        sources.put(path("domain/DecisionChangeEvent.java"), """
                package com.example.crm.customer.domain;

                public class DecisionChangeEvent {
                    public void recalculateDecision() {
                    }
                }
                """);
        sources.put(path("domain/CreditCaseUpdatedEvent.java"), """
                package com.example.crm.customer.domain;

                public class CreditCaseUpdatedEvent {
                    public void refreshCreditCase() {
                    }
                }
                """);
        sources.put(path("domain/CustomerType.java"), """
                package com.example.crm.customer.domain;

                public enum CustomerType {
                    CUSTOMER_PROFILE,
                    MULTILINE
                }
                """);
        sources.put("src/main/resources/openapi/customer-api.yaml", """
                openapi: 3.0.0
                paths:
                  /api/customers/{customerId}:
                    get:
                      operationId: getCustomer
                    put:
                      operationId: updateCustomer
                """);
        return sources;
    }

    private Map<String, String> domainInterfaceSources() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("api/CustomerController.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.application.UpdateCustomerPort;
                import com.example.crm.customer.domain.CustomerModel;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class CustomerController {
                    private final UpdateCustomerPort updateCustomerPort;

                    public CustomerModel updateCustomer(CustomerModel model) {
                        updateCustomerPort.update(model);
                        return model;
                    }
                }
                """);
        sources.put(path("application/UpdateCustomerPort.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.CustomerModel;

                interface UpdateCustomerPort {
                    void update(CustomerModel model);
                }
                """);
        sources.put(path("application/UpdateCustomerService.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.domain.CustomerModel;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import com.example.crm.customer.domain.behaviour.UpdateCustomer;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;

                @Service
                @RequiredArgsConstructor
                class UpdateCustomerService implements UpdateCustomerPort {
                    private final CustomerRepositoryPort.Query queryRepository;
                    private final CustomerRepositoryPort.Command commandRepository;

                    public void update(CustomerModel model) {
                        UpdateCustomer current = queryRepository.getCustomerForUpdate(model.customerId());
                        current.update(model);
                        commandRepository.save((CustomerModel) current);
                    }
                }
                """);
        sources.put(path("domain/CustomerRepositoryPort.java"), """
                package com.example.crm.customer.domain;

                import com.example.crm.customer.domain.behaviour.UpdateCustomer;

                public interface CustomerRepositoryPort {
                    interface Query {
                        UpdateCustomer getCustomerForUpdate(String customerId);
                    }

                    interface Command {
                        void save(CustomerModel customer);
                    }
                }
                """);
        sources.put(path("adapter/out/CustomerQueryRepository.java"), """
                package com.example.crm.customer.adapter.out;

                import com.example.crm.customer.domain.CustomerModel;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import com.example.crm.customer.domain.behaviour.UpdateCustomer;
                import org.springframework.stereotype.Repository;

                @Repository
                class CustomerQueryRepository implements CustomerRepositoryPort.Query {
                    public UpdateCustomer getCustomerForUpdate(String customerId) {
                        return new CustomerModel();
                    }
                }
                """);
        sources.put(path("adapter/out/CustomerCommandRepository.java"), """
                package com.example.crm.customer.adapter.out;

                import com.example.crm.customer.domain.CustomerModel;
                import com.example.crm.customer.domain.CustomerRepositoryPort;
                import org.springframework.stereotype.Repository;

                @Repository
                class CustomerCommandRepository implements CustomerRepositoryPort.Command {
                    public void save(CustomerModel customer) {
                    }
                }
                """);
        sources.put(path("domain/behaviour/UpdateCustomer.java"), """
                package com.example.crm.customer.domain.behaviour;

                import com.example.crm.customer.domain.CustomerModel;

                public interface UpdateCustomer {
                    void update(CustomerModel formMappedModel);
                }
                """);
        sources.put(path("domain/CustomerModel.java"), """
                package com.example.crm.customer.domain;

                import com.example.crm.customer.domain.behaviour.UpdateCustomer;

                public class CustomerModel implements UpdateCustomer {
                    public String customerId() {
                        return "customer-1";
                    }

                    public void update(CustomerModel formMappedModel) {
                    }
                }
                """);
        sources.put(path("domain/CivilLawCustomerModel.java"), """
                package com.example.crm.customer.domain;

                public class CivilLawCustomerModel extends CustomerModel {
                    @Override
                    public void update(CustomerModel formMappedModel) {
                        calculateStatus();
                    }

                    private CustomerStatus calculateStatus() {
                        return CustomerStatus.ACTIVE;
                    }
                }
                """);
        sources.put(path("domain/CustomerStatus.java"), """
                package com.example.crm.customer.domain;

                enum CustomerStatus {
                    ACTIVE
                }
                """);
        sources.put("src/main/resources/openapi/customer-api.yaml", """
                openapi: 3.0.0
                paths:
                  /api/customers/{customerId}:
                    put:
                      operationId: updateCustomer
                """);
        return sources;
    }

    private Map<String, String> generatedOpenApiModelSources() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("api/CustomerController.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.application.UpdateCustomerPort;
                import com.example.crm.customer.generated.CustomerWebModel;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class CustomerController {
                    private final UpdateCustomerPort updateCustomerPort;

                    public CustomerWebModel updateCustomer(CustomerWebModel customerWebModel) {
                        updateCustomerPort.update(customerWebModel.customerId());
                        return customerWebModel;
                    }
                }
                """);
        sources.put(path("application/UpdateCustomerPort.java"), """
                package com.example.crm.customer.application;

                interface UpdateCustomerPort {
                    void update(String customerId);
                }
                """);
        sources.put(path("application/UpdateCustomerService.java"), """
                package com.example.crm.customer.application;

                import org.springframework.stereotype.Service;

                @Service
                class UpdateCustomerService implements UpdateCustomerPort {
                    public void update(String customerId) {
                    }
                }
                """);
        sources.put("src/main/resources/openapi/customer-api.yaml", """
                openapi: 3.0.0
                paths:
                  /api/customers/{customerId}:
                    put:
                      operationId: updateCustomer
                      requestBody:
                        content:
                          application/json:
                            schema:
                              $ref: '#/components/schemas/CustomerWebModel'
                components:
                  schemas:
                    CustomerWebModel:
                      type: object
                """);
        return sources;
    }

    private String path(String relativePath) {
        return SOURCE_PREFIX + "/com/example/crm/customer/" + relativePath;
    }
}
