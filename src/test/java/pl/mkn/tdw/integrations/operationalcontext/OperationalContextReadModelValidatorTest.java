package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextReadModelValidatorTest {

    private final OperationalContextReadModelValidator validator = new OperationalContextReadModelValidator();

    @Test
    void shouldReportSelfReferencesUnknownTargetsAndMergedDuplicates() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "references", map(
                                "systems", List.of("crm-customer-service", "missing-system"),
                                "repositories", List.of("crm-customer-service-repo", "crm-customer-service-repo")
                        )
                )),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "SELF_REFERENCE");
        assertHasError(findings, "UNKNOWN_RELATION_TARGET");
        assertHasWarning(findings, "DUPLICATE_RELATION_MERGED");
    }

    @Test
    void shouldReportIntegrationParticipantsDuplicatedInReferences() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service"), map("id", "notification-provider")),
                List.of(map(
                        "id", "crm-customer-to-notification-sync",
                        "participants", map(
                                "source", map("system", "notification-provider", "boundedContext", "notification-context"),
                                "targets", List.of(map("system", "crm-customer-service", "boundedContext", "customer-profile-context"))
                        ),
                        "references", map(
                                "systems", List.of("notification-provider", "crm-customer-service"),
                                "boundedContexts", List.of("notification-context", "customer-profile-context")
                        )
                )),
                List.of(),
                List.of(),
                List.of(map("id", "customer-profile-context"), map("id", "notification-context")),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "DUPLICATED_PARTICIPANT_REFERENCE_SYSTEM");
        assertHasError(findings, "DUPLICATED_PARTICIPANT_REFERENCE_BOUNDED_CONTEXT");
    }

    @Test
    void shouldReportSystemDependenciesDerivedFromIntegrations() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(
                        map(
                                "id", "crm-customer-service",
                                "dependencies", map(
                                        "upstream", List.of("crm-portal"),
                                        "downstream", List.of("notification-provider")
                                )
                        ),
                        map("id", "crm-portal"),
                        map("id", "notification-provider")
                ),
                List.of(
                        map(
                                "id", "crm-portal-to-customer-service",
                                "participants", map(
                                        "source", map("system", "crm-portal"),
                                        "targets", List.of(map("system", "crm-customer-service"))
                                )
                        ),
                        map(
                                "id", "crm-customer-to-notification-sync",
                                "participants", map(
                                        "source", map("system", "crm-customer-service"),
                                        "targets", List.of(map("system", "notification-provider"))
                                )
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "SYSTEM_DEPENDENCY_DERIVED_FROM_INTEGRATION");
    }

    @Test
    void shouldReportBoundedContextReferencesDerivedFromReadModelSources() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service"), map("id", "notification-provider")),
                List.of(map(
                        "id", "crm-customer-to-notification-sync",
                        "participants", map(
                                "source", map("system", "crm-customer-service", "boundedContext", "customer-profile-context"),
                                "targets", List.of(map("system", "notification-provider"))
                        )
                )),
                List.of(),
                List.of(),
                List.of(map(
                        "id", "customer-profile-context",
                        "references", map(
                                "systems", List.of("crm-customer-service"),
                                "integrations", List.of("crm-customer-to-notification-sync")
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "BOUNDED_CONTEXT_SYSTEM_REFERENCE_DERIVED");
        assertHasError(findings, "BOUNDED_CONTEXT_INTEGRATION_REFERENCE_DERIVED");
    }

    @Test
    void shouldReportProcessParticipantSystemsDuplicatedInReferences() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "customer-support-process",
                        "participants", map(
                                "primarySystems", List.of("crm-customer-service"),
                                "supportingSystems", List.of("crm-support-service")
                        ),
                        "references", map("systems", List.of("crm-customer-service", "crm-support-service"))
                )),
                List.of(map("id", "crm-customer-service"), map("id", "crm-support-service")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "PROCESS_PARTICIPANT_REFERENCE_SYSTEM");
    }

    @Test
    void shouldAllowSemanticCodeSearchTargetEvenWhenRepositoryReferencesOverlap() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "references", map(
                                "systems", List.of("crm-customer-service"),
                                "boundedContexts", List.of("customer-profile-context"),
                                "terms", List.of("customer-profile-term")
                        )
                )),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "target", map("type", "system", "id", "crm-customer-service"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "searchMode", "whole-repository"
                        ))
                )),
                List.of(map("id", "customer-profile-context")),
                List.of(new OperationalContextDtos.OperationalContextGlossaryTerm(
                        "customer-profile-term",
                        "Customer profile term",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                List.of(),
                List.of(),
                "index"
        ));

        assertFalse(findings.stream().anyMatch(finding -> finding.code().startsWith("CODE_SEARCH_TARGET_")));
    }

    @Test
    void shouldReportBidirectionalReferences() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "references", map("repositories", List.of("crm-customer-service-repo"))
                )),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "references", map("systems", List.of("crm-customer-service"))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "BIDIRECTIONAL_REFERENCE");
    }

    @Test
    void shouldReportCodeSearchScopeShapeProblems() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(
                        map(
                                "id", "empty-scope",
                                "target", map("type", "system", "id", "crm-customer-service"),
                                "repositories", List.of()
                        ),
                        map(
                                "id", "no-primary-scope",
                                "target", map("type", "system", "id", "crm-customer-service"),
                                "repositories", List.of(map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "supporting-library",
                                        "priority", 2,
                                        "searchMode", "whole-repository"
                                ))
                        ),
                        map(
                                "id", "no-target-scope",
                                "repositories", List.of(map(
                                        "repoId", "missing-repo",
                                        "role", "primary",
                                        "searchMode", "whole-repository"
                                ))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "CODE_SEARCH_SCOPE_WITHOUT_INCLUDED_REPOSITORY");
        assertHasWarning(findings, "CODE_SEARCH_SCOPE_WITHOUT_PRIMARY_REPOSITORY");
        assertHasError(findings, "CODE_SEARCH_SCOPE_WITHOUT_TARGET");
        assertHasError(findings, "UNKNOWN_CODE_SEARCH_REPOSITORY");
    }

    @Test
    void shouldAcceptCodeSearchScopeTargetingCrmBoundedContext() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(map(
                        "id", "customer-profile-context-scope",
                        "target", map("type", "bounded-context", "id", "customer-profile-context"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "searchMode", "whole-repository"
                        ))
                )),
                List.of(map("id", "customer-profile-context")),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertFalse(findings.stream().anyMatch(finding ->
                finding.code().equals("CODE_SEARCH_SCOPE_TARGET_UNSUPPORTED")));
    }

    @Test
    void shouldWarnWhenInternalSystemHasNoCodeSearchScope() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(
                        map("id", "crm-customer-service", "systemType", "internal-service", "systemSubtype", "backend"),
                        map("id", "crm-api-gateway", "systemType", "api-gateway"),
                        map("id", "crm-service", "systemType", "internal-service", "systemSubtype", "backend"),
                        map("id", "crm-external-saas", "systemType", "external-saas")
                ),
                List.of(),
                List.of(map("id", "crm-service-repo")),
                List.of(map(
                        "id", "crm-service-scope",
                        "target", map("type", "system", "id", "crm-service"),
                        "repositories", List.of(map(
                                "repoId", "crm-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "searchMode", "whole-repository"
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasWarning(findings, "INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE");
        assertTrue(
                findings.stream()
                        .filter(finding -> finding.code().equals("INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE"))
                        .anyMatch(finding -> finding.message().contains("crm-customer-service"))
                        && findings.stream()
                        .filter(finding -> finding.code().equals("INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE"))
                        .anyMatch(finding -> finding.message().contains("crm-api-gateway")),
                () -> "Expected missing-scope warnings for internal system and api-gateway in " + findings
        );
        assertFalse(findings.stream()
                .filter(finding -> finding.code().equals("INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE"))
                .anyMatch(finding -> finding.message().contains("crm-service")
                        || finding.message().contains("crm-external-saas")));
    }

    @Test
    void shouldRejectRepositoryReferencesDeclaredOnSystem() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "references", map("repositories", List.of("crm-customer-service-repo"))
                )),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "SYSTEM_REPOSITORY_REFERENCE_NOT_ALLOWED");
    }

    @Test
    void shouldRejectOwnershipOutsideSystemAndBoundedContext() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "support-team",
                        "ownership", map("ownerLabel", "support team")
                )),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "ownership", map(
                                "ownerTeamIds", List.of("support-team"),
                                "ownershipStatus", "explicit"
                        )
                )),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "ownership", map("ownerTeamIds", List.of("support-team"))
                )),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "target", map("type", "system", "id", "crm-customer-service"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "searchMode", "whole-repository"
                        ))
                )),
                List.of(map(
                        "id", "customer-profile-context",
                        "ownership", map(
                                "ownerTeamIds", List.of("support-team"),
                                "ownershipStatus", "explicit"
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "OWNERSHIP_OUTSIDE_SYSTEM_OR_BOUNDED_CONTEXT");
    }

    @Test
    void shouldWarnWhenCatalogStoresInferredOwnership() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "salesforce",
                        "ownership", map(
                                "ownerLabel", "wlasciciel systemu Salesforce",
                                "ownershipStatus", "unknown",
                                "confidence", "low",
                                "source", "inferred backlog"
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasWarning(findings, "INFERRED_OWNERSHIP_IN_CATALOG");
    }

    @Test
    void shouldKeepCleanCatalogWarningFreeForValidatorRules() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "target", map("type", "system", "id", "crm-customer-service"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "searchMode", "whole-repository"
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertFalse(findings.stream().anyMatch(finding -> finding.code().startsWith("CODE_SEARCH_SCOPE_")));
        assertFalse(findings.stream().anyMatch(finding -> finding.code().startsWith("DUPLICATED_")));
        assertFalse(findings.stream().anyMatch(finding -> finding.code().equals("BIDIRECTIONAL_REFERENCE")));
    }

    @Test
    void shouldValidateCanonicalAnonymousCrmSystemClassification() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(
                        map("id", "crm-case-api", "systemType", "internal-service"),
                        map(
                                "id", "crm-customer-sync",
                                "systemType", "internal-service",
                                "systemSubtype", "batch"
                        ),
                        map(
                                "id", "crm-contact-indexer",
                                "systemType", "internal-service",
                                "systemSubtype", "unknown"
                        ),
                        map(
                                "id", "crm-provider-gateway",
                                "systemType", "external-system",
                                "systemSubtype", "frontend"
                        ),
                        map("id", "crm-unclassified-system")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "INTERNAL_SERVICE_SUBTYPE_REQUIRED");
        assertHasError(findings, "INTERNAL_SERVICE_SUBTYPE_UNSUPPORTED");
        assertHasWarning(findings, "INTERNAL_SERVICE_SUBTYPE_UNKNOWN");
        assertHasError(findings, "SYSTEM_SUBTYPE_OUTSIDE_INTERNAL_SERVICE");
        assertHasError(findings, "SYSTEM_TYPE_REQUIRED");
    }

    @Test
    void shouldAcceptExplicitAnonymousCrmFrontendRegistration() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-agent-portal",
                        "systemType", "internal-service",
                        "systemSubtype", "frontend"
                )),
                List.of(),
                List.of(map(
                        "id", "crm-agent-portal-repository",
                        "repositoryType", "frontend"
                )),
                List.of(map(
                        "id", "crm-agent-portal-code-scope",
                        "target", map("type", "system", "id", "crm-agent-portal"),
                        "repositories", List.of(map(
                                "repoId", "crm-agent-portal-repository",
                                "role", "primary",
                                "priority", 1,
                                "searchMode", "whole-repository"
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertFalse(findings.stream().anyMatch(finding -> finding.code().startsWith("FRONTEND_")));
        assertFalse(findings.stream().anyMatch(finding -> finding.code().equals("FRONTEND_WITH_MULTIPLE_CODE_SEARCH_SCOPES")));
        assertFalse(findings.stream().anyMatch(finding -> finding.code().equals("INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE")));
    }

    @Test
    void shouldRejectAmbiguousAnonymousCrmFrontendRegistration() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(
                        frontendSystem("crm-agent-portal-no-primary"),
                        frontendSystem("crm-agent-console-multiple-primary"),
                        frontendSystem("crm-advisor-workspace-wrong-type"),
                        frontendSystem("crm-service-desk-multiple-scopes"),
                        frontendSystem("crm-agent-inbox-no-scope")
                ),
                List.of(),
                List.of(
                        repository("crm-agent-portal-repository", "frontend"),
                        repository("crm-agent-console-shell", "frontend"),
                        repository("crm-agent-console-widgets", "frontend"),
                        repository("crm-advisor-workspace-repository", "backend"),
                        repository("crm-service-desk-shell", "frontend"),
                        repository("crm-service-desk-widgets", "frontend")
                ),
                List.of(
                        scope("crm-agent-portal-scope", "crm-agent-portal-no-primary", List.of(
                                scopedRepository("crm-agent-portal-repository", "supporting")
                        )),
                        scope("crm-agent-console-scope", "crm-agent-console-multiple-primary", List.of(
                                scopedRepository("crm-agent-console-shell", "primary"),
                                scopedRepository("crm-agent-console-widgets", "primary")
                        )),
                        scope("crm-advisor-workspace-scope", "crm-advisor-workspace-wrong-type", List.of(
                                scopedRepository("crm-advisor-workspace-repository", "primary")
                        )),
                        scope("crm-service-desk-shell-scope", "crm-service-desk-multiple-scopes", List.of(
                                scopedRepository("crm-service-desk-shell", "primary")
                        )),
                        scope("crm-service-desk-widgets-scope", "crm-service-desk-multiple-scopes", List.of(
                                scopedRepository("crm-service-desk-widgets", "primary")
                        ))
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "FRONTEND_SCOPE_WITHOUT_PRIMARY_REPOSITORY");
        assertHasError(findings, "FRONTEND_SCOPE_WITH_MULTIPLE_PRIMARY_REPOSITORIES");
        assertHasError(findings, "FRONTEND_PRIMARY_REPOSITORY_TYPE_MISMATCH");
        assertHasError(findings, "FRONTEND_WITHOUT_CODE_SEARCH_SCOPE");
        assertHasError(findings, "FRONTEND_WITH_MULTIPLE_CODE_SEARCH_SCOPES");
    }

    @Test
    void shouldReportCodeSearchRepositorySearchBoundaryProblems() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(
                        scopeWithRepository(
                                "missing-mode-scope",
                                map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary",
                                        "priority", 1
                                )
                        ),
                        scopeWithRepository(
                                "unknown-mode-scope",
                                map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "searchMode", "module-list"
                                )
                        ),
                        scopeWithRepository(
                                "empty-prefix-scope",
                                map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "searchMode", "path-prefixes"
                                )
                        ),
                        scopeWithRepository(
                                "whole-repo-with-prefix-scope",
                                map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "searchMode", "whole-repository",
                                        "pathPrefixes", List.of("src/main/java")
                                )
                        ),
                        scopeWithRepository(
                                "invalid-prefix-scope",
                                map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "searchMode", "path-prefixes",
                                        "pathPrefixes", List.of("/src/main/java", "src\\main\\java")
                                )
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "CODE_SEARCH_REPOSITORY_WITHOUT_SEARCH_MODE");
        assertHasError(findings, "CODE_SEARCH_REPOSITORY_UNKNOWN_SEARCH_MODE");
        assertHasError(findings, "CODE_SEARCH_REPOSITORY_PATH_PREFIXES_EMPTY");
        assertHasError(findings, "CODE_SEARCH_REPOSITORY_WHOLE_REPOSITORY_WITH_PATH_PREFIXES");
        assertHasError(findings, "CODE_SEARCH_REPOSITORY_INVALID_PATH_PREFIX");
    }

    private void assertHas(List<OperationalContextRelationIndex.ValidationFinding> findings, String code) {
        assertTrue(
                findings.stream().anyMatch(finding -> finding.code().equals(code)),
                () -> "Expected finding " + code + " in " + findings
        );
        assertTrue(
                findings.stream()
                        .filter(finding -> finding.code().equals(code))
                        .allMatch(finding -> !finding.sourceRefs().isEmpty()),
                () -> "Expected source refs for " + code
        );
    }

    private static Map<String, Object> scopeWithRepository(String id, Map<String, Object> repository) {
        return map(
                "id", id,
                "target", map("type", "system", "id", "crm-customer-service"),
                "repositories", List.of(repository)
        );
    }

    private static Map<String, Object> frontendSystem(String id) {
        return map("id", id, "systemType", "internal-service", "systemSubtype", "frontend");
    }

    private static Map<String, Object> repository(String id, String repositoryType) {
        return map("id", id, "repositoryType", repositoryType);
    }

    private static Map<String, Object> scope(
            String id,
            String systemId,
            List<Map<String, Object>> repositories
    ) {
        return map(
                "id", id,
                "target", map("type", "system", "id", systemId),
                "repositories", repositories
        );
    }

    private static Map<String, Object> scopedRepository(String repositoryId, String role) {
        return map(
                "repoId", repositoryId,
                "role", role,
                "priority", 1,
                "searchMode", "whole-repository"
        );
    }

    private void assertHasError(List<OperationalContextRelationIndex.ValidationFinding> findings, String code) {
        assertHasSeverity(findings, code, "error");
    }

    private void assertHasWarning(List<OperationalContextRelationIndex.ValidationFinding> findings, String code) {
        assertHasSeverity(findings, code, "warning");
    }

    private void assertHasSeverity(
            List<OperationalContextRelationIndex.ValidationFinding> findings,
            String code,
            String severity
    ) {
        assertHas(findings, code);
        assertTrue(
                findings.stream()
                        .filter(finding -> finding.code().equals(code))
                        .allMatch(finding -> finding.severity().equals(severity)),
                () -> "Expected finding " + code + " to have severity " + severity + " in " + findings
        );
    }

    private static Map<String, Object> map(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
