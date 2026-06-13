package pl.mkn.incidenttracker.integrations.gitlab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GitLabRepositoryEndpointServiceTest {

    private final GitLabRepositoryEndpointService service = new GitLabRepositoryEndpointService(
            mock(GitLabRepositoryPort.class)
    );

    @Test
    void shouldIgnoreTopLevelFeignClientMappings() {
        var endpoints = service.parseEndpointFile(
                "crm-case-service",
                "src/main/java/com/example/crm/casehandling/adapter/out/rest/CrmTaskClient.java",
                """
                        package com.example.crm.casehandling.adapter.out.rest;

                        import java.util.List;
                        import java.util.Map;
                        import org.springframework.cloud.openfeign.FeignClient;
                        import org.springframework.http.ResponseEntity;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.PutMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestParam;

                        @FeignClient(url = "${integration.crm.url}")
                        interface CrmTaskClient {

                          @PutMapping("/crm/tasks/{task-id}/complete")
                          void completeInteractionTask(@PathVariable("task-id") String id,
                                                       @RequestBody Map<String, String> resolution);

                          @PostMapping("/crm/tasks/search-by-owner")
                          List<CustomerTaskDto> findTasksForOwner(
                              @RequestBody CustomerOwnerContextDto context,
                              @RequestParam(name = "onlyCurrentAdvisor") boolean onlyCurrentAdvisor);
                        }
                        """,
                List.of()
        );

        assertEquals(List.of(), endpoints);
    }

    @Test
    void shouldIgnoreNestedFeignClientMappings() {
        var endpoints = service.parseEndpointFile(
                "crm-customer-service",
                "src/main/java/com/example/crm/customer/adapter/out/external/segmentation/CustomerSegmentIntegrationService.java",
                """
                        package com.example.crm.customer.adapter.out.external.segmentation;

                        import java.util.List;
                        import lombok.RequiredArgsConstructor;
                        import org.springframework.cloud.openfeign.FeignClient;
                        import org.springframework.web.bind.annotation.GetMapping;

                        @AdapterBean
                        @RequiredArgsConstructor
                        public class CustomerSegmentIntegrationService {

                          @FeignClient(url = "${integration.crm-data.url}")
                          @FunctionalInterface
                          interface CustomerSegmentationClient {

                            @GetMapping("/crm/customer-segments/active")
                            List<CustomerSegmentWebModel> getActiveSegments();
                          }
                        }
                        """,
                List.of()
        );

        assertEquals(List.of(), endpoints);
    }

    @Test
    void shouldKeepRestControllerMappings() {
        var endpoints = service.parseEndpointFile(
                "crm-case-api",
                "src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java",
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api/crm/customer-cases")
                        public class CustomerCaseController {

                          @GetMapping("/{caseId}")
                          CustomerCaseResponse getCase(@PathVariable String caseId) {
                            return new CustomerCaseResponse(caseId);
                          }
                        }
                        """,
                List.of()
        );

        assertEquals(1, endpoints.size());
        assertEquals("GET /api/crm/customer-cases/{caseId} -> com.example.crm.casehandling.api.CustomerCaseController#getCase",
                endpoints.get(0).endpointId());
    }
}
