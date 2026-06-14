package pl.mkn.incidenttracker.integrations.operationalcontext;

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
                                "role", "primary-implementation",
                                "priority", 1
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
    void shouldReportTeamReferencesDuplicatedByResponsibilities() {
        var findings = validator.validate(OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "crm-team",
                        "references", map(
                                "systems", List.of("crm-customer-service"),
                                "repositories", List.of("crm-customer-service-repo")
                        ),
                        "responsibilities", List.of(
                                map(
                                        "targetType", "system",
                                        "targetId", "crm-customer-service"
                                ),
                                map(
                                        "targetType", "repository",
                                        "targetId", "crm-customer-service-repo"
                                )
                        )
                )),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertHasError(findings, "TEAM_REFERENCE_DERIVED_FROM_RESPONSIBILITY");
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
                                        "priority", 2
                                ))
                        ),
                        map(
                                "id", "no-target-scope",
                                "repositories", List.of(map(
                                        "repoId", "missing-repo",
                                        "role", "primary-implementation"
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
                                "role", "primary-implementation",
                                "priority", 1
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
