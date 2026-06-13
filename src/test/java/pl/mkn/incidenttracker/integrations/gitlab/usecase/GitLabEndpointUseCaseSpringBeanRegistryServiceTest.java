package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseSpringBeanRegistryServiceTest {

    private final GitLabEndpointUseCaseCodeIndexService codeIndexService = new GitLabEndpointUseCaseCodeIndexService();
    private final GitLabEndpointUseCaseSpringBeanRegistryService registryService =
            new GitLabEndpointUseCaseSpringBeanRegistryService();

    @Test
    void shouldIndexComponentBeansWithPrimaryQualifierAndAssignableTypes() {
        var registry = registry("""
                package com.example.orders;

                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Primary;
                import org.springframework.stereotype.Service;

                interface OrderPort {
                }

                @Primary
                @Service("orderService")
                class DefaultOrderService implements OrderPort {
                }

                @Service
                @Qualifier("legacyOrders")
                class LegacyOrderService implements OrderPort {
                }
                """);

        var candidates = registry.candidatesForType("com.example.orders.OrderPort");

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().anyMatch(bean -> bean.beanName().equals("orderService")
                && bean.primary()
                && bean.assignableTypes().contains("OrderPort")
                && bean.assignableTypes().contains("com.example.orders.OrderPort")));
        assertTrue(candidates.stream().anyMatch(bean -> bean.beanName().equals("legacyOrderService")
                && bean.qualifiers().contains("legacyOrders")
                && bean.sourceKind() == GitLabEndpointUseCaseSpringBeanSourceKind.COMPONENT));
    }

    @Test
    void shouldIndexConfigurationBeanMethodsWithAliasesAndPrimary() {
        var registry = registry("""
                package com.example.orders;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Primary;

                interface ClockPort {
                }

                class SystemClock implements ClockPort {
                }

                @Configuration
                class OrderConfiguration {

                    @Bean(name = {"clockPort", "systemClock"})
                    @Primary
                    ClockPort clockPort() {
                        return new SystemClock();
                    }
                }
                """);

        var configurationBean = registry.beansByName().get("orderConfiguration");
        var beanMethod = registry.beansByName().get("clockPort");

        assertEquals(GitLabEndpointUseCaseSpringBeanSourceKind.CONFIGURATION_CLASS, configurationBean.sourceKind());
        assertEquals(GitLabEndpointUseCaseSpringBeanSourceKind.BEAN_METHOD, beanMethod.sourceKind());
        assertEquals(List.of("systemClock"), beanMethod.aliases());
        assertTrue(beanMethod.primary());
        assertEquals(beanMethod, registry.beansByName().get("systemClock"));
        assertTrue(registry.candidatesForType("ClockPort").contains(beanMethod));
    }

    @Test
    void shouldIndexMapperFeignClientAndSpringDataRepository() {
        var registry = registry("""
                package com.example.orders;

                import org.mapstruct.Mapper;
                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.data.jpa.repository.JpaRepository;

                class Order {
                }

                @Mapper(componentModel = "spring")
                interface OrderMapper {
                }

                @FeignClient(name = "billingClient")
                interface BillingClient {
                }

                interface OrderRepository extends JpaRepository<Order, String> {
                }
                """);

        assertEquals(
                GitLabEndpointUseCaseSpringBeanSourceKind.MAPSTRUCT_MAPPER,
                registry.beansByName().get("orderMapper").sourceKind()
        );
        assertEquals(
                GitLabEndpointUseCaseSpringBeanSourceKind.FEIGN_CLIENT,
                registry.beansByName().get("billingClient").sourceKind()
        );
        assertEquals(
                GitLabEndpointUseCaseSpringBeanSourceKind.SPRING_DATA_REPOSITORY,
                registry.beansByName().get("orderRepository").sourceKind()
        );
        assertTrue(registry.candidatesForType("JpaRepository").contains(registry.beansByName().get("orderRepository")));
    }

    @Test
    void shouldIndexAdapterBeanAsComponentWithNestedPortAssignableType() {
        var registry = registry("""
                package com.example.orders;

                interface ProductRepositoryPort {
                    interface Query {
                    }
                }

                @AdapterBean
                class ProductQueryRepository implements ProductRepositoryPort.Query {
                }
                """);

        var repositoryBean = registry.beansByName().get("productQueryRepository");

        assertEquals(GitLabEndpointUseCaseSpringBeanSourceKind.COMPONENT, repositoryBean.sourceKind());
        assertTrue(repositoryBean.stereotypes().contains("AdapterBean"));
        assertTrue(registry.candidatesForType("ProductRepositoryPort.Query").contains(repositoryBean));
        assertTrue(registry.candidatesForType("com.example.orders.ProductRepositoryPort.Query")
                .contains(repositoryBean));
    }

    @Test
    void shouldWarnWhenAssignableParentIsOutsideSnapshot() {
        var registry = registry("""
                package com.example.orders;

                import org.springframework.stereotype.Service;

                @Service
                class OrderService implements MissingPort {
                }
                """);

        assertTrue(registry.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.BEAN_ASSIGNABLE_TYPE_UNRESOLVED.equals(warning.code())
                        && warning.candidates().contains("MissingPort")));
    }

    private GitLabEndpointUseCaseSpringBeanRegistry registry(String source) {
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
                        "src/main/java/com/example/orders/OrderUseCaseFixture.java",
                        source,
                        source.length(),
                        false
                )),
                List.of()
        );
        return registryService.buildRegistry(codeIndexService.buildIndex(snapshot));
    }
}
