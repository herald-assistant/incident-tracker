package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabJavaExternalTypePolicyTest {

    private final GitLabJavaExternalTypePolicy policy = new GitLabJavaExternalTypePolicy();

    @Test
    void shouldSkipSourceLookupForFrameworkLibraryTypes() {
        var decision = policy.classify("java.util.List");

        assertEquals(GitLabJavaExternalTypeClassification.SKIP_SOURCE_LOOKUP, decision.classification());
        assertEquals("FRAMEWORK_LIBRARY_TYPE", decision.signal());
        assertFalse(decision.sourceLookupAllowed());
    }

    @Test
    void shouldClassifyLombokRequiredArgsConstructorAsSemanticSignal() {
        var decision = policy.classify("lombok.RequiredArgsConstructor");

        assertEquals(GitLabJavaExternalTypeClassification.SEMANTIC_SIGNAL, decision.classification());
        assertEquals("LOMBOK_REQUIRED_ARGS_CONSTRUCTOR", decision.signal());
        assertFalse(decision.sourceLookupAllowed());
    }

    @Test
    void shouldClassifySpringDataRepositoryBaseTypeAsSemanticSignal() {
        var decision = policy.classify("org.springframework.data.jpa.repository.JpaRepository");

        assertEquals(GitLabJavaExternalTypeClassification.SEMANTIC_SIGNAL, decision.classification());
        assertEquals("SPRING_DATA_REPOSITORY_BOUNDARY", decision.signal());
        assertFalse(decision.sourceLookupAllowed());
    }

    @Test
    void shouldKeepLocalGeneratedApiTypesInRepositoryLookupScope() {
        var decision = policy.classify("com.example.crm.generated.DataProductApi");

        assertEquals(GitLabJavaExternalTypeClassification.LOCAL_LOOKUP_FIRST, decision.classification());
        assertEquals("LOCAL_LOOKUP_FIRST", decision.signal());
        assertTrue(decision.sourceLookupAllowed());
    }

    @Test
    void shouldMarkInternalSharedLibraryMissAsTerminalBoundary() {
        var decision = policy.localLookupMiss(
                "SharedCustomerPolicy",
                "pl.centrum24.crm.contract.SharedCustomerPolicy"
        );

        assertEquals(GitLabJavaExternalTypeClassification.TERMINAL_BOUNDARY, decision.classification());
        assertEquals("INTERNAL_SHARED_LIBRARY_BOUNDARY", decision.signal());
        assertFalse(decision.sourceLookupAllowed());
    }
}
