package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseCodeIndexServiceTest {

    private final GitLabEndpointUseCaseCodeIndexService service = new GitLabEndpointUseCaseCodeIndexService();

    @Test
    void shouldIndexTypesHierarchyFieldsMethodsAndCalls() {
        var snapshot = snapshot(List.of(new GitLabEndpointUseCaseSourceFile(
                "src/main/java/com/example/orders/api/OrderController.java",
                """
                        package com.example.orders.api;

                        import jakarta.validation.Valid;
                        import org.springframework.beans.factory.annotation.Qualifier;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/orders")
                        public class OrderController extends BaseController implements OrdersApi {

                            private final OrderService orderService;
                            private final OrderMapper mapper;
                            @Qualifier("auditPublisher")
                            private EventPublisher auditPublisher;

                            public OrderController(OrderService orderService, OrderMapper mapper) {
                                this.orderService = orderService;
                                this.mapper = mapper;
                            }

                            @PostMapping
                            public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
                                var order = orderService.create(request);
                                auditPublisher.publish(order.id());
                                return mapper.toResponse(order);
                            }
                        }
                        """,
                1_000,
                false
        )));

        var index = service.buildIndex(snapshot);

        assertEquals(GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL, index.indexStatus());
        var controller = index.findType("com.example.orders.api.OrderController").orElseThrow();
        assertEquals(GitLabEndpointUseCaseTypeKind.CLASS, controller.kind());
        assertEquals(List.of("RestController", "RequestMapping"), controller.annotations());
        assertEquals(List.of("BaseController"), controller.extendsTypes());
        assertEquals(List.of("OrdersApi"), controller.implementsTypes());
        assertEquals(List.of("BaseController", "OrdersApi"),
                index.hierarchyIndex().directParentsByType().get(controller.fqn()));
        assertEquals(List.of(controller.fqn()), index.hierarchyIndex().childrenByParent().get("OrdersApi"));

        assertTrue(controller.fields().stream()
                .anyMatch(field -> field.name().equals("orderService")
                        && field.type().equals("OrderService")
                        && field.finalField()));
        assertTrue(controller.fields().stream()
                .anyMatch(field -> field.name().equals("auditPublisher")
                        && field.annotations().contains("Qualifier")));

        var createMethod = controller.methods().stream()
                .filter(method -> method.name().equals("create"))
                .findFirst()
                .orElseThrow();
        assertEquals("create(CreateOrderRequest)", createMethod.signature());
        assertEquals(List.of("PostMapping"), createMethod.annotations());
        assertEquals(List.of("Valid", "RequestBody"), createMethod.parameters().get(0).annotations());

        var callNames = index.methodCallIndex().callsByCaller().get(createMethod.id()).stream()
                .map(call -> call.receiver() + "." + call.name())
                .toList();
        assertTrue(callNames.contains("orderService.create"));
        assertTrue(callNames.contains("auditPublisher.publish"));
        assertTrue(callNames.contains("mapper.toResponse"));
    }

    @Test
    void shouldContinueIndexingAfterParseProblem() {
        var snapshot = snapshot(List.of(
                new GitLabEndpointUseCaseSourceFile(
                        "src/main/java/com/example/orders/ValidService.java",
                        """
                                package com.example.orders;

                                class ValidService {
                                    void handle() {
                                    }
                                }
                                """,
                        200,
                        false
                ),
                new GitLabEndpointUseCaseSourceFile(
                        "src/main/java/com/example/orders/BrokenService.java",
                        "package com.example.orders; class BrokenService {",
                        100,
                        false
                )
        ));

        var index = service.buildIndex(snapshot);

        assertEquals(GitLabEndpointUseCaseIndexStatus.PARTIAL, index.indexStatus());
        assertTrue(index.findType("com.example.orders.ValidService").isPresent());
        assertTrue(index.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.SOURCE_PARSE_FAILED.equals(warning.code())
                        && "src/main/java/com/example/orders/BrokenService.java".equals(warning.sourcePath())));
    }

    @Test
    void shouldIndexJava21PatternSwitchSources() {
        var snapshot = snapshot(List.of(new GitLabEndpointUseCaseSourceFile(
                "src/main/java/com/example/orders/ProductMapper.java",
                """
                        package com.example.orders;

                        interface FormView {
                        }

                        class OverdraftFormView implements FormView {
                        }

                        class ProductWebModel {
                        }

                        class OverdraftProductWebModel extends ProductWebModel {
                        }

                        interface ProductMapper {
                            default ProductWebModel from(FormView formView) {
                                return switch (formView) {
                                    case OverdraftFormView overdraftFormView -> new OverdraftProductWebModel();
                                    default -> throw new IllegalArgumentException("Unsupported form view");
                                };
                            }
                        }
                        """,
                1_000,
                false
        )));

        var index = service.buildIndex(snapshot);

        assertEquals(GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL, index.indexStatus());
        var mapper = index.findType("com.example.orders.ProductMapper").orElseThrow();
        assertTrue(mapper.methods().stream().anyMatch(method -> "from(FormView)".equals(method.signature())));
        assertTrue(index.warnings().stream()
                .noneMatch(warning -> GitLabEndpointUseCaseWarningCodes.SOURCE_PARSE_FAILED.equals(warning.code())));
    }

    private static GitLabEndpointUseCaseSourceSnapshot snapshot(List<GitLabEndpointUseCaseSourceFile> files) {
        return new GitLabEndpointUseCaseSourceSnapshot(
                "tenant-alpha",
                "orders-api",
                "main",
                "src/main/java",
                GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL,
                files.size(),
                files.size(),
                500,
                80_000,
                false,
                false,
                files,
                List.of(new GitLabEndpointUseCaseWarning(
                        GitLabEndpointUseCaseWarningCodes.BRANCH_REF_NOT_IMMUTABLE,
                        GitLabEndpointUseCaseWarningSeverity.INFO,
                        "branch",
                        null,
                        null,
                        List.of()
                ))
        );
    }
}
