package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseDependencyInjectionResolverTest {

    private final GitLabEndpointUseCaseCodeIndexService codeIndexService = new GitLabEndpointUseCaseCodeIndexService();
    private final GitLabEndpointUseCaseSpringBeanRegistryService registryService =
            new GitLabEndpointUseCaseSpringBeanRegistryService();
    private final GitLabEndpointUseCaseDependencyInjectionResolver resolver =
            new GitLabEndpointUseCaseDependencyInjectionResolver();

    @Test
    void shouldResolveConstructorInjectionByPrimaryQualifierAndNameFallback() {
        var resolution = resolution("""
                package com.example.orders;

                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Primary;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.RestController;

                interface OrderPort {
                }

                @Primary
                @Service
                class DefaultOrderService implements OrderPort {
                }

                @Service
                @Qualifier("legacyOrders")
                class LegacyOrderService implements OrderPort {
                }

                interface PaymentPort {
                }

                @Service("cardPaymentPort")
                class CardPaymentAdapter implements PaymentPort {
                }

                @Service("cashPaymentPort")
                class CashPaymentAdapter implements PaymentPort {
                }

                @RestController
                class OrderController {
                    OrderController(OrderPort orderPort, @Qualifier("legacyOrders") OrderPort legacyPort) {
                    }
                }

                @Service
                class PaymentUseCase {
                    PaymentUseCase(PaymentPort cashPaymentPort) {
                    }
                }
                """);

        var orderPort = resolution.findDependency("com.example.orders.OrderController", "orderPort");
        var legacyPort = resolution.findDependency("com.example.orders.OrderController", "legacyPort");
        var cashPaymentPort = resolution.findDependency("com.example.orders.PaymentUseCase", "cashPaymentPort");

        assertResolved(orderPort, "com.example.orders.DefaultOrderService",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC);
        assertResolved(legacyPort, "com.example.orders.LegacyOrderService",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC);
        assertResolved(cashPaymentPort, "com.example.orders.CashPaymentAdapter",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC);
    }

    @Test
    void shouldResolveLombokFieldMethodAndRecordInjection() {
        var resolution = resolution("""
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Primary;
                import org.springframework.stereotype.Component;
                import org.springframework.stereotype.Service;

                interface OrderPort {
                }

                @Primary
                @Service
                class DefaultOrderService implements OrderPort {
                }

                @Service
                @Qualifier("legacyOrders")
                class LegacyOrderService implements OrderPort {
                }

                interface AuditPort {
                }

                @Service
                class AuditAdapter implements AuditPort {
                }

                @Service
                @RequiredArgsConstructor
                class RequiredArgsUseCase {
                    private final OrderPort orderPort;
                    @Qualifier("legacyOrders")
                    private final OrderPort legacyPort;
                }

                @Service
                class FieldConsumer {
                    @Autowired
                    OrderPort orderPort;
                }

                @Service
                class MethodConsumer {
                    @Autowired
                    void configure(AuditPort auditPort) {
                    }
                }

                @Component
                record RecordConsumer(OrderPort orderPort) {
                }
                """);

        assertResolved(
                resolution.findDependency("com.example.orders.RequiredArgsUseCase", "orderPort"),
                "com.example.orders.DefaultOrderService",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
        assertResolved(
                resolution.findDependency("com.example.orders.RequiredArgsUseCase", "legacyPort"),
                "com.example.orders.LegacyOrderService",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
        assertResolved(
                resolution.findDependency("com.example.orders.FieldConsumer", "orderPort"),
                "com.example.orders.DefaultOrderService",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
        assertResolved(
                resolution.findDependency("com.example.orders.MethodConsumer", "auditPort"),
                "com.example.orders.AuditAdapter",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
        assertResolved(
                resolution.findDependency("com.example.orders.RecordConsumer", "orderPort"),
                "com.example.orders.DefaultOrderService",
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC
        );
    }

    @Test
    void shouldReportAmbiguousUnresolvedAndQualifierMisses() {
        var resolution = resolution("""
                package com.example.orders;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.stereotype.Service;

                interface NotificationPort {
                }

                @Service
                class EmailNotificationAdapter implements NotificationPort {
                }

                @Service
                class SmsNotificationAdapter implements NotificationPort {
                }

                @Service
                class NotificationUseCase {
                    NotificationUseCase(NotificationPort notificationPort) {
                    }
                }

                @Service
                class MissingUseCase {
                    @Autowired
                    MissingPort missingPort;
                }

                @Service
                class QualifiedUseCase {
                    QualifiedUseCase(@Qualifier("pushNotifications") NotificationPort notificationPort) {
                    }
                }
                """);

        assertEquals(
                GitLabEndpointUseCaseDependencyResolutionStatus.AMBIGUOUS,
                resolution.findDependency("com.example.orders.NotificationUseCase", "notificationPort").status()
        );
        assertEquals(
                GitLabEndpointUseCaseDependencyResolutionStatus.UNRESOLVED,
                resolution.findDependency("com.example.orders.MissingUseCase", "missingPort").status()
        );
        assertEquals(
                GitLabEndpointUseCaseDependencyResolutionStatus.UNRESOLVED,
                resolution.findDependency("com.example.orders.QualifiedUseCase", "notificationPort").status()
        );
        assertTrue(hasWarning(resolution, GitLabEndpointUseCaseWarningCodes.DI_BEAN_AMBIGUOUS));
        assertTrue(hasWarning(resolution, GitLabEndpointUseCaseWarningCodes.DI_BEAN_NOT_FOUND));
        assertTrue(hasWarning(resolution, GitLabEndpointUseCaseWarningCodes.DI_QUALIFIER_NOT_FOUND));
    }

    private GitLabEndpointUseCaseDependencyResolution resolution(String source) {
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
                        "src/main/java/com/example/orders/DependencyInjectionFixture.java",
                        source,
                        source.length(),
                        false
                )),
                List.of()
        );
        var codeIndex = codeIndexService.buildIndex(snapshot);
        var registry = registryService.buildRegistry(codeIndex);
        return resolver.resolve(codeIndex, registry);
    }

    private static void assertResolved(
            GitLabEndpointUseCaseResolvedDependency dependency,
            String expectedBeanType,
            GitLabEndpointUseCaseResolutionKind expectedResolutionKind
    ) {
        assertNotNull(dependency);
        assertEquals(GitLabEndpointUseCaseDependencyResolutionStatus.RESOLVED, dependency.status());
        assertEquals(expectedBeanType, dependency.resolvedBean().type());
        assertEquals(expectedResolutionKind, dependency.resolutionKind());
    }

    private static boolean hasWarning(
            GitLabEndpointUseCaseDependencyResolution resolution,
            String code
    ) {
        return resolution.warnings().stream().anyMatch(warning -> code.equals(warning.code()));
    }
}
