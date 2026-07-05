package pl.mkn.tdw.integrations.gitlab.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabJavaMethodSliceServiceTest {

    private static final String FILE_PATH = "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java";
    private static final String ACCESSOR_FILE_PATH = "src/main/java/com/example/crm/customer/CustomerLifecycle.java";

    private final GitLabJavaMethodSliceService service = new GitLabJavaMethodSliceService(new SliceRepositoryPort());

    @Test
    void shouldRenderFocusedMethodSliceWithRelevantFieldsImportsAndLocalCallees() {
        var response = service.readMethodSlice(new GitLabJavaMethodSliceRequest(
                "CRM",
                "crm-customer-profile",
                "main",
                FILE_PATH,
                "CustomerProfileService",
                List.of(new GitLabJavaMethodSliceMethodSelector("updateCustomerProfile", 18)),
                true,
                true,
                true,
                12_000
        ));

        assertEquals("OK", response.status());
        assertEquals("com.example.crm.customerprofile.CustomerProfileService", response.declaringTypeName());
        assertEquals(List.of(new GitLabJavaMethodSliceMethodSelector("updateCustomerProfile", 18)), response.requestedMethods());
        assertFalse(response.truncated());
        assertTrue(response.includedFields().contains("customerProfileRepository"));
        assertTrue(response.includedFields().contains("auditClient"));
        assertTrue(response.includedFields().contains("clock"));
        assertFalse(response.includedFields().contains("noiseClient"));
        assertTrue(response.includedMethods().stream().anyMatch(method -> "updateCustomerProfile".equals(method.methodName())));
        assertTrue(response.includedMethods().stream().anyMatch(method -> "prepareEntity".equals(method.methodName())));
        assertTrue(response.includedMethods().stream().anyMatch(method -> "resultFrom".equals(method.methodName())));
        assertTrue(response.includedMethods().stream().anyMatch(method -> "map".equals(method.methodName())));
        assertFalse(response.includedMethods().stream().anyMatch(method -> "record".equals(method.methodName())));

        var content = response.content();
        var normalizedContent = content.replace("\r\n", "\n");
        assertTrue(normalizedContent.contains("""
                package com.example.crm.customerprofile;

                import java.time.Clock;
                import java.time.Instant;
                import lombok.RequiredArgsConstructor;
                """.stripIndent()));
        assertFalse(normalizedContent.contains("""
                import java.time.Clock;

                import java.time.Instant;
                """.stripIndent()));
        assertTrue(content.contains("import java.time.Clock;"));
        assertTrue(content.contains("import java.time.Instant;"));
        assertTrue(content.contains("import lombok.RequiredArgsConstructor;"));
        assertFalse(content.contains("import java.util.UUID;"));
        assertTrue(content.contains("private final CustomerProfileRepository customerProfileRepository;"));
        assertTrue(content.contains("private final AuditClient auditClient;"));
        assertFalse(content.contains("private final NoiseClient noiseClient;"));
        assertTrue(content.contains("public CustomerProfileResult updateCustomerProfile"));
        assertTrue(content.contains("CustomerProfileEntity prepareEntity"));
        assertTrue(content.contains("protected CustomerProfileResult resultFrom"));
        assertTrue(content.contains("private CustomerProfileEntity map"));
        assertFalse(content.contains("private void record"));
        assertFalse(content.contains("public void unrelated"));
        assertTrue(content.contains("// ... omitted fields ..."));
        assertTrue(content.contains("// ... omitted methods ..."));
        assertFalse(content.contains("omitted unrelated"));
    }

    @Test
    void shouldRenderAllOverloadsWhenLineStartIsNotProvided() {
        var response = service.readMethodSlice(new GitLabJavaMethodSliceRequest(
                "CRM",
                "crm-customer-profile",
                "main",
                FILE_PATH,
                "CustomerProfileService",
                List.of(new GitLabJavaMethodSliceMethodSelector("updateCustomerProfile", null)),
                true,
                true,
                true,
                12_000
        ));

        assertEquals("OK", response.status());
        assertTrue(response.candidates().isEmpty());
        assertEquals(2, response.includedMethods().stream()
                .filter(method -> "updateCustomerProfile".equals(method.methodName()))
                .count());
        assertTrue(response.content().contains("public CustomerProfileResult updateCustomerProfile(CustomerProfileRequest request)"));
        assertTrue(response.content().contains("public CustomerProfileResult updateCustomerProfile(String externalId)"));
        assertTrue(response.content().contains("private CustomerProfileEntity map"));
    }

    @Test
    void shouldIncludeFieldsReferencedThroughLocalAccessorCalls() {
        var response = service.readMethodSlice(new GitLabJavaMethodSliceRequest(
                "CRM",
                "crm-customer-service",
                "main",
                ACCESSOR_FILE_PATH,
                "CustomerLifecycle",
                List.of(new GitLabJavaMethodSliceMethodSelector("shouldTriggerReview", null)),
                true,
                true,
                true,
                12_000
        ));

        assertEquals("OK", response.status());
        assertEquals(List.of("customerStatus", "vip"), response.includedFields());
        assertTrue(response.limitations().isEmpty());

        var content = response.content();
        assertTrue(content.contains("private CustomerStatus customerStatus;"));
        assertTrue(content.contains("private boolean vip;"));
        assertFalse(content.contains("private CustomerStatus ignoredStatus;"));
        assertTrue(content.contains("getCustomerStatus().requiresReview()"));
        assertTrue(content.contains("isVip()"));
        assertTrue(content.contains("setCustomerStatus(nextStatus)"));
    }

    @Test
    void shouldIncludeFieldsReferencedThroughSameTypeBuilderCalls() {
        var response = service.readMethodSlice(new GitLabJavaMethodSliceRequest(
                "CRM",
                "crm-customer-service",
                "main",
                ACCESSOR_FILE_PATH,
                "CustomerLifecycle",
                List.of(new GitLabJavaMethodSliceMethodSelector("activate", null)),
                true,
                true,
                true,
                12_000
        ));

        assertEquals("OK", response.status());
        assertEquals(List.of("customerStatus", "vip"), response.includedFields());
        assertTrue(response.limitations().isEmpty());

        var content = response.content();
        assertTrue(content.contains("private CustomerStatus customerStatus;"));
        assertTrue(content.contains("private boolean vip;"));
        assertFalse(content.contains("private CustomerStatus ignoredStatus;"));
        assertTrue(content.contains("CustomerLifecycle.builder()"));
        assertTrue(content.contains(".customerStatus(CustomerStatus.ACTIVE)"));
        assertTrue(content.contains(".vip(true)"));
    }

    private static final class SliceRepositoryPort implements GitLabRepositoryPort {

        @Override
        public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFile> listRepositoryFiles(String group, String projectName, String branch, String pathPrefix) {
            return List.of();
        }

        @Override
        public GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileContent(group, projectName, branch, filePath, source(filePath), false);
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
            throw new UnsupportedOperationException("not used");
        }

        private String source(String filePath) {
            if (ACCESSOR_FILE_PATH.equals(filePath)) {
                return accessorSource();
            }
            return orderSource();
        }

        private String orderSource() {
            return """
                    package com.example.crm.customerprofile;

                    import java.time.Clock;
                    import java.time.Instant;
                    import java.util.UUID;
                    import lombok.RequiredArgsConstructor;
                    import org.springframework.stereotype.Service;

                    @Service
                    @RequiredArgsConstructor
                    public class CustomerProfileService {

                        private final CustomerProfileRepository customerProfileRepository;
                        private final AuditClient auditClient;
                        private final Clock clock;
                        private final NoiseClient noiseClient;

                        public CustomerProfileResult updateCustomerProfile(CustomerProfileRequest request) {
                            var entity = prepareEntity(request);
                            customerProfileRepository.save(entity);
                            auditClient.record(entity.id(), Instant.now(clock));
                            return resultFrom(entity);
                        }

                        public CustomerProfileResult updateCustomerProfile(String externalId) {
                            return new CustomerProfileResult(externalId);
                        }

                        CustomerProfileEntity prepareEntity(CustomerProfileRequest request) {
                            return map(request);
                        }

                        protected CustomerProfileResult resultFrom(CustomerProfileEntity entity) {
                            return new CustomerProfileResult(entity.id());
                        }

                        private CustomerProfileEntity map(CustomerProfileRequest request) {
                            return new CustomerProfileEntity(request.customerId());
                        }

                        private void record(String id, Instant at) {
                            noiseClient.call(id + at.toString());
                        }

                        public void unrelated() {
                            noiseClient.call(UUID.randomUUID().toString());
                        }
                    }
                    """;
        }

        private String accessorSource() {
            return """
                    package com.example.crm.customer;

                    import lombok.Builder;
                    import lombok.Getter;
                    import lombok.Setter;

                    @Builder(toBuilder = true)
                    @Getter
                    @Setter
                    class CustomerLifecycle {

                        private CustomerStatus customerStatus;
                        private boolean vip;
                        private CustomerStatus ignoredStatus;

                        boolean shouldTriggerReview(CustomerStatus nextStatus) {
                            setCustomerStatus(nextStatus);
                            return getCustomerStatus().requiresReview() && isVip();
                        }

                        CustomerLifecycle activate() {
                            return CustomerLifecycle.builder()
                                    .customerStatus(CustomerStatus.ACTIVE)
                                    .vip(true)
                                    .build();
                        }

                        void unrelated() {
                            ignoredStatus.requiresReview();
                        }
                    }

                    enum CustomerStatus {
                        ACTIVE;

                        boolean requiresReview() {
                            return true;
                        }
                    }
                    """;
        }
    }
}
