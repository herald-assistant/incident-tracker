package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseCallTargetResolverTest {

    private final GitLabEndpointUseCaseCodeIndexService codeIndexService = new GitLabEndpointUseCaseCodeIndexService();
    private final GitLabEndpointUseCaseSpringBeanRegistryService registryService =
            new GitLabEndpointUseCaseSpringBeanRegistryService();
    private final GitLabEndpointUseCaseDependencyInjectionResolver dependencyResolver =
            new GitLabEndpointUseCaseDependencyInjectionResolver();
    private final GitLabEndpointUseCaseCallTargetResolver callTargetResolver =
            new GitLabEndpointUseCaseCallTargetResolver();

    @Test
    void shouldResolveThisSuperStaticLocalVariableAndDefaultConstructorCalls() {
        var resolution = resolution("""
                package com.example.orders;

                import org.springframework.stereotype.Service;

                class BaseService {
                    void audit(String id) {
                    }
                }

                class StaticMapper {
                    static Order map(String id) {
                        return new Order();
                    }
                }

                class LocalWorker {
                    void run() {
                    }
                }

                class Order {
                }

                @Service
                class OrderService extends BaseService {
                    void process(String id) {
                        validate(id);
                        super.audit(id);
                        StaticMapper.map(id);
                        var localWorker = new LocalWorker();
                        localWorker.run();
                    }

                    void validate(String id) {
                    }
                }
                """);

        assertResolvedCall(resolution, "validate(id)", "com.example.orders.OrderService#validate(String)",
                GitLabEndpointUseCaseResolutionKind.THIS_METHOD);
        assertResolvedCall(resolution, "super.audit(id)", "com.example.orders.BaseService#audit(String)",
                GitLabEndpointUseCaseResolutionKind.SUPER_METHOD);
        assertResolvedCall(resolution, "StaticMapper.map(id)", "com.example.orders.StaticMapper#map(String)",
                GitLabEndpointUseCaseResolutionKind.STATIC_METHOD);
        assertResolvedCall(resolution, "localWorker.run()", "com.example.orders.LocalWorker#run()",
                GitLabEndpointUseCaseResolutionKind.DIRECT_METHOD);

        var constructorCall = callByExpression(resolution, "new LocalWorker()");
        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.TERMINAL, constructorCall.status());
        assertEquals(GitLabEndpointUseCaseResolutionKind.NEW_INSTANCE, constructorCall.resolutionKind());
        assertEquals("com.example.orders.LocalWorker", constructorCall.targetType());
    }

    @Test
    void shouldResolveDependencyInjectedInterfaceReceiverToImplementation() {
        var resolution = resolution("""
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.RestController;

                interface OrderPort {
                    void create(String id);
                }

                @Service
                class DefaultOrderService implements OrderPort {
                    public void create(String id) {
                    }
                }

                @RestController
                @RequiredArgsConstructor
                class OrderController {
                    private final OrderPort orderPort;

                    void handle(String id) {
                        orderPort.create(id);
                    }
                }
                """);

        assertResolvedCall(resolution, "orderPort.create(id)", "com.example.orders.DefaultOrderService#create(String)",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC);
    }

    @Test
    void shouldResolveNestedAdapterPortAndMapperSingletonReceiver() {
        var resolution = resolution("""
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.mapstruct.Mapper;
                import org.springframework.web.bind.annotation.RestController;

                class TtaId {
                }

                enum ProductType {
                    OVERDRAFT
                }

                interface FormView {
                }

                class ProductWebModel {
                }

                class Product {
                }

                interface ProductRepositoryPort {
                    interface Query {
                        FormView getProductFormView(TtaId ttaId, ProductType productType);
                    }
                }

                @AdapterBean
                class ProductQueryRepository implements ProductRepositoryPort.Query {
                    public FormView getProductFormView(TtaId ttaId, ProductType productType) {
                        return null;
                    }
                }

                @Mapper
                interface ProductWebModelMapper {
                    ProductWebModelMapper INSTANCE = null;

                    default ProductWebModel from(FormView formView) {
                        return new ProductWebModel();
                    }

                    default Product from(ProductWebModel webModel) {
                        return new Product();
                    }

                    default ProductWebModel from(Product product) {
                        return new ProductWebModel();
                    }
                }

                @RestController
                @RequiredArgsConstructor
                class DataProductController {
                    private final ProductRepositoryPort.Query productQueryRepository;

                    ProductWebModel getProductWebModel(TtaId id, ProductType productType) {
                        var productView = productQueryRepository.getProductFormView(id, productType);
                        return ProductWebModelMapper.INSTANCE.from(productView);
                    }
                }
                """);

        assertResolvedCall(
                resolution,
                "productQueryRepository.getProductFormView(id, productType)",
                "com.example.orders.ProductQueryRepository#getProductFormView(TtaId,ProductType)",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
        assertResolvedCall(
                resolution,
                "ProductWebModelMapper.INSTANCE.from(productView)",
                "com.example.orders.ProductWebModelMapper#from(FormView)",
                GitLabEndpointUseCaseResolutionKind.STATIC_METHOD
        );
    }

    @Test
    void shouldResolveInjectedPortCallThroughContractWhenImplementationHasOverloadedEventHandlers() {
        var resolution = resolution("""
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.mapstruct.Mapper;
                import org.springframework.web.bind.annotation.RestController;

                class Product {
                }

                class ProductWebModel {
                }

                class ProductUpdatedEvent {
                }

                class DecisionChangeEvent {
                }

                class CreditCaseUpdatedEvent {
                }

                interface FormView {
                }

                interface UpdateProductPort {
                    ProductUpdatedEvent update(Product product);
                }

                @UseCaseBean
                class UpdateProductService implements UpdateProductPort {
                    public ProductUpdatedEvent update(Product product) {
                        return new ProductUpdatedEvent();
                    }

                    public void update(DecisionChangeEvent event) {
                    }

                    public void update(CreditCaseUpdatedEvent event) {
                    }
                }

                @Mapper
                interface ProductWebModelMapper {
                    ProductWebModelMapper INSTANCE = null;

                    default ProductWebModel from(FormView formView) {
                        return new ProductWebModel();
                    }

                    default Product from(ProductWebModel webModel) {
                        return new Product();
                    }

                    default ProductWebModel from(Product product) {
                        return new ProductWebModel();
                    }
                }

                @RestController
                @RequiredArgsConstructor
                class DataProductController {
                    private final UpdateProductPort updateProductPort;

                    void updateProduct(ProductWebModel productWebModel) {
                        updateProductPort.update(ProductWebModelMapper.INSTANCE.from(productWebModel));
                    }
                }
                """);

        assertResolvedCall(
                resolution,
                "ProductWebModelMapper.INSTANCE.from(productWebModel)",
                "com.example.orders.ProductWebModelMapper#from(ProductWebModel)",
                GitLabEndpointUseCaseResolutionKind.STATIC_METHOD
        );
        assertResolvedCall(
                resolution,
                "updateProductPort.update(ProductWebModelMapper.INSTANCE.from(productWebModel))",
                "com.example.orders.UpdateProductService#update(Product)",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
    }

    @Test
    void shouldResolveParameterReceiverAndReportExternalTerminal() {
        var resolution = resolution("""
                package com.example.orders;

                import org.springframework.http.ResponseEntity;
                import org.springframework.stereotype.Service;

                class Worker {
                    void run() {
                    }
                }

                @Service
                class WorkerUseCase {
                    void handle(Worker worker) {
                        worker.run();
                        ResponseEntity.ok("done");
                    }
                }
                """);

        assertResolvedCall(resolution, "worker.run()", "com.example.orders.Worker#run()",
                GitLabEndpointUseCaseResolutionKind.DIRECT_METHOD);

        var externalCall = callByExpression(resolution, "ResponseEntity.ok(\"done\")");
        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.TERMINAL, externalCall.status());
        assertEquals(GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY, externalCall.resolutionKind());
    }

    @Test
    void shouldTreatLombokGeneratedAccessorsAsKnownTerminalCallsWithoutWarnings() {
        var resolution = resolution("""
                package com.example.orders;

                import lombok.Getter;
                import lombok.Setter;
                import org.springframework.stereotype.Service;

                @Getter
                @Setter
                class Customer {
                    private String name;
                    private boolean active;
                }

                @Service
                class CustomerService {
                    void handle(Customer customer) {
                        customer.getName();
                        customer.isActive();
                        customer.setName("Alice");
                    }
                }
                """);

        var getterCall = callByExpression(resolution, "customer.getName()");
        var booleanGetterCall = callByExpression(resolution, "customer.isActive()");
        var setterCall = callByExpression(resolution, "customer.setName(\"Alice\")");

        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.TERMINAL, getterCall.status());
        assertEquals("com.example.orders.Customer", getterCall.targetType());
        assertEquals("getName():String", getterCall.targetMethodSignature());
        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.TERMINAL, booleanGetterCall.status());
        assertEquals("isActive():boolean", booleanGetterCall.targetMethodSignature());
        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.TERMINAL, setterCall.status());
        assertEquals("setName(String):void", setterCall.targetMethodSignature());
        assertTrue(resolution.warnings().stream()
                .noneMatch(warning -> GitLabEndpointUseCaseWarningCodes.CALL_TARGET_UNRESOLVED.equals(warning.code())));
    }

    @Test
    void shouldReportAmbiguousInterfaceReceiverAndUnresolvedReceiver() {
        var resolution = resolution("""
                package com.example.orders;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Service;

                interface NotificationPort {
                    void send(String id);
                }

                @Service
                class EmailNotificationAdapter implements NotificationPort {
                    public void send(String id) {
                    }
                }

                @Service
                class SmsNotificationAdapter implements NotificationPort {
                    public void send(String id) {
                    }
                }

                @Service
                class NotificationUseCase {
                    @Autowired
                    NotificationPort notificationPort;

                    void handle(String id) {
                        notificationPort.send(id);
                        missingReceiver.run();
                    }
                }
                """);

        var ambiguousCall = callByExpression(resolution, "notificationPort.send(id)");
        var unresolvedCall = callByExpression(resolution, "missingReceiver.run()");

        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.AMBIGUOUS, ambiguousCall.status());
        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.UNRESOLVED, unresolvedCall.status());
        assertTrue(hasWarning(resolution, GitLabEndpointUseCaseWarningCodes.CALL_TARGET_AMBIGUOUS));
        assertTrue(hasWarning(resolution, GitLabEndpointUseCaseWarningCodes.CALL_TARGET_UNRESOLVED));
    }

    private GitLabEndpointUseCaseCallTargetResolution resolution(String source) {
        var snapshot = new GitLabEndpointUseCaseSourceSnapshot(
                "tenant-alpha",
                "orders-api",
                "main",
                "src/main/java",
                GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL,
                1,
                1,
                500,
                80_000,
                false,
                false,
                List.of(new GitLabEndpointUseCaseSourceFile(
                        "src/main/java/com/example/orders/CallTargetFixture.java",
                        source,
                        source.length(),
                        false
                )),
                List.of()
        );
        var codeIndex = codeIndexService.buildIndex(snapshot);
        var registry = registryService.buildRegistry(codeIndex);
        var dependencyResolution = dependencyResolver.resolve(codeIndex, registry);
        return callTargetResolver.resolve(codeIndex, registry, dependencyResolution);
    }

    private static void assertResolvedCall(
            GitLabEndpointUseCaseCallTargetResolution resolution,
            String expression,
            String expectedTargetMethodId,
            GitLabEndpointUseCaseResolutionKind expectedResolutionKind
    ) {
        var call = callByExpression(resolution, expression);
        assertEquals(GitLabEndpointUseCaseCallResolutionStatus.RESOLVED, call.status());
        assertEquals(expectedTargetMethodId, call.targetMethodId());
        assertEquals(expectedResolutionKind, call.resolutionKind());
    }

    private static GitLabEndpointUseCaseResolvedCall callByExpression(
            GitLabEndpointUseCaseCallTargetResolution resolution,
            String expression
    ) {
        var call = resolution.calls().stream()
                .filter(candidate -> expression.equals(candidate.call().expression()))
                .findFirst()
                .orElse(null);
        assertNotNull(call, "Expected call expression: " + expression);
        return call;
    }

    private static boolean hasWarning(
            GitLabEndpointUseCaseCallTargetResolution resolution,
            String code
    ) {
        return resolution.warnings().stream().anyMatch(warning -> code.equals(warning.code()));
    }
}
