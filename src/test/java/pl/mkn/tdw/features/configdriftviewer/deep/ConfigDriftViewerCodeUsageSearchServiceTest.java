package pl.mkn.tdw.features.configdriftviewer.deep;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeRefSource;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeUsageKind;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflight;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflightStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepRepositoryScope;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigDriftViewerCodeUsageSearchServiceTest {

    @Test
    void shouldConfirmValueConfigurationPropertiesAndRelaxedBindingInsideScopeOnly() {
        var properties = new GitLabProperties();
        properties.setGroup("platform");
        var gitLabPort = mock(GitLabRepositoryPort.class);
        var capturedQuery = new AtomicReference<GitLabRepositorySearchQuery>();
        when(gitLabPort.searchCandidateFiles(any())).thenAnswer(invocation -> {
            capturedQuery.set(invocation.getArgument(0));
            return List.of(
                    candidate("src/main/java/NotificationProperties.java"),
                    candidate("src/main/java/CustomerProfileClient.java"),
                    candidate("src/test/java/OutOfScopeTest.java")
            );
        });
        when(gitLabPort.readFile(
                "platform",
                "backend",
                "release-42",
                "src/main/java/NotificationProperties.java",
                250_000
        )).thenReturn(content(
                "src/main/java/NotificationProperties.java",
                """
                        @ConfigurationProperties(prefix = "client.notifications")
                        public class NotificationProperties {
                            private int maxRetries;
                        }
                        """
        ));
        when(gitLabPort.readFile(
                "platform",
                "backend",
                "release-42",
                "src/main/java/CustomerProfileClient.java",
                250_000
        )).thenReturn(content(
                "src/main/java/CustomerProfileClient.java",
                """
                        public class CustomerProfileClient {
                            @Value("${feature.customer-profile.url}")
                            private String url;
                            private static final String ENV = "FEATURE_CUSTOMER_PROFILE_URL";
                        }
                        """
        ));
        var service = new ConfigDriftViewerCodeUsageSearchService(properties, gitLabPort);

        var result = service.search(preflight(), deterministicContext());

        assertEquals(List.of("src/main/java"), capturedQuery.get().pathPrefixes());
        assertTrue(capturedQuery.get().keywords().contains("client.notifications"));
        assertTrue(capturedQuery.get().keywords().contains("FEATURE_CUSTOMER_PROFILE_URL"));
        assertTrue(result.groundings().stream()
                .anyMatch(grounding ->
                        grounding.matchedPropertyPath().equals("client.notifications.maxRetries")
                                && grounding.usageKind()
                                == ConfigDriftViewerCodeUsageKind.CONFIGURATION_PROPERTIES
                                && "NotificationProperties".equals(grounding.symbol())));
        assertTrue(result.groundings().stream()
                .anyMatch(grounding ->
                        grounding.matchedPropertyPath().equals("feature.customer-profile.url")
                                && grounding.usageKind()
                                == ConfigDriftViewerCodeUsageKind.VALUE_ANNOTATION
                                && "CustomerProfileClient".equals(grounding.symbol())));
        assertTrue(result.groundings().stream()
                .anyMatch(grounding ->
                        grounding.matchedPropertyPath().equals("feature.customer-profile.url")
                                && grounding.usageKind()
                                == ConfigDriftViewerCodeUsageKind.RELAXED_BINDING));
        assertTrue(result.groundings().stream()
                .allMatch(grounding -> grounding.filePath().startsWith("src/main/java/")));
        assertEquals(2, result.filesInspected());
        verify(gitLabPort, never()).readFile(
                eq("platform"),
                eq("backend"),
                eq("release-42"),
                eq("src/test/java/OutOfScopeTest.java"),
                anyInt()
        );
        assertFalse(result.groundings().stream()
                .anyMatch(grounding -> grounding.groundingId() == null));
    }

    private static ConfigDriftViewerDeepPreflight preflight() {
        return new ConfigDriftViewerDeepPreflight(
                ConfigDriftViewerDeepPreflightStatus.READY,
                "runtime-config",
                "backend",
                "Backend",
                "backend",
                List.of(new ConfigDriftViewerDeepRepositoryScope(
                        "backend-scope",
                        "backend-repo",
                        "primary",
                        1,
                        "platform/backend",
                        "backend",
                        "path-prefixes",
                        List.of("src/main/java"),
                        "release-42",
                        "release-42",
                        ConfigDriftViewerCodeRefSource.REQUESTED,
                        true,
                        false,
                        true,
                        List.of()
                )),
                List.of(),
                List.of()
        );
    }

    private static ConfigDriftViewerDeterministicContext deterministicContext() {
        return new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "backend",
                "Backend",
                "backend",
                "dev1",
                "zt001",
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(),
                List.of(),
                List.of(
                        difference("difference-001", "client.notifications.maxRetries"),
                        difference("difference-002", "feature.customer-profile.url")
                ),
                List.of()
        );
    }

    private static ConfigDriftViewerDifference difference(String id, String path) {
        return new ConfigDriftViewerDifference(
                id,
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                0,
                path,
                ConfigDriftViewerChangeKind.CHANGED,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerSensitivity.NON_SENSITIVE,
                "source-token",
                "target-token"
        );
    }

    private static GitLabRepositoryFileCandidate candidate(String path) {
        return new GitLabRepositoryFileCandidate(
                "platform",
                "backend",
                "release-42",
                path,
                "matched",
                10
        );
    }

    private static GitLabRepositoryFileContent content(String path, String content) {
        return new GitLabRepositoryFileContent(
                "platform",
                "backend",
                "release-42",
                path,
                content,
                false
        );
    }
}
