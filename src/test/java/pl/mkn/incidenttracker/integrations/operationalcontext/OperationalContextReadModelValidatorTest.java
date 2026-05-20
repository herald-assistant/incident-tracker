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
                        "id", "app-core",
                        "references", map(
                                "systems", List.of("app-core", "missing-system"),
                                "repositories", List.of("app-repo", "app-repo")
                        )
                )),
                List.of(),
                List.of(map("id", "app-repo")),
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
                List.of(map("id", "app-core"), map("id", "partner-system")),
                List.of(map(
                        "id", "partner-sync",
                        "participants", map(
                                "source", map("system", "partner-system", "boundedContext", "partner-context"),
                                "targets", List.of(map("system", "app-core", "boundedContext", "core-context"))
                        ),
                        "references", map(
                                "systems", List.of("partner-system", "app-core"),
                                "boundedContexts", List.of("partner-context", "core-context")
                        )
                )),
                List.of(),
                List.of(),
                List.of(map("id", "core-context"), map("id", "partner-context")),
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
                                "id", "app-core",
                                "dependencies", map(
                                        "upstream", List.of("caller-system"),
                                        "downstream", List.of("partner-system")
                                )
                        ),
                        map("id", "caller-system"),
                        map("id", "partner-system")
                ),
                List.of(
                        map(
                                "id", "caller-to-core",
                                "participants", map(
                                        "source", map("system", "caller-system"),
                                        "targets", List.of(map("system", "app-core"))
                                )
                        ),
                        map(
                                "id", "core-to-partner",
                                "participants", map(
                                        "source", map("system", "app-core"),
                                        "targets", List.of(map("system", "partner-system"))
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
                List.of(map("id", "app-core"), map("id", "partner-system")),
                List.of(map(
                        "id", "core-to-partner",
                        "participants", map(
                                "source", map("system", "app-core", "boundedContext", "core-context"),
                                "targets", List.of(map("system", "partner-system"))
                        )
                )),
                List.of(),
                List.of(),
                List.of(map(
                        "id", "core-context",
                        "references", map(
                                "systems", List.of("app-core"),
                                "integrations", List.of("core-to-partner")
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
                        "id", "core-process",
                        "participants", map(
                                "primarySystems", List.of("app-core"),
                                "supportingSystems", List.of("support-service")
                        ),
                        "references", map("systems", List.of("app-core", "support-service"))
                )),
                List.of(map("id", "app-core"), map("id", "support-service")),
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
                List.of(map("id", "app-core")),
                List.of(),
                List.of(map(
                        "id", "app-repo",
                        "references", map(
                                "systems", List.of("app-core"),
                                "boundedContexts", List.of("core-context"),
                                "terms", List.of("core-term")
                        )
                )),
                List.of(map(
                        "id", "app-scope",
                        "target", map("type", "system", "id", "app-core"),
                        "repositories", List.of(map(
                                "repoId", "app-repo",
                                "role", "primary-implementation",
                                "priority", 1
                        ))
                )),
                List.of(map("id", "core-context")),
                List.of(new OperationalContextDtos.OperationalContextGlossaryTerm(
                        "core-term",
                        "Core term",
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
                        "id", "core-team",
                        "references", map(
                                "systems", List.of("app-core"),
                                "repositories", List.of("app-repo")
                        ),
                        "responsibilities", List.of(
                                map(
                                        "targetType", "system",
                                        "targetId", "app-core"
                                ),
                                map(
                                        "targetType", "repository",
                                        "targetId", "app-repo"
                                )
                        )
                )),
                List.of(),
                List.of(map("id", "app-core")),
                List.of(),
                List.of(map("id", "app-repo")),
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
                        "id", "app-core",
                        "references", map("repositories", List.of("app-repo"))
                )),
                List.of(),
                List.of(map(
                        "id", "app-repo",
                        "references", map("systems", List.of("app-core"))
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
                List.of(map("id", "app-core")),
                List.of(),
                List.of(map("id", "app-repo")),
                List.of(
                        map(
                                "id", "empty-scope",
                                "target", map("type", "system", "id", "app-core"),
                                "repositories", List.of()
                        ),
                        map(
                                "id", "no-primary-scope",
                                "target", map("type", "system", "id", "app-core"),
                                "repositories", List.of(map(
                                        "repoId", "app-repo",
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
                List.of(map("id", "app-core")),
                List.of(),
                List.of(map("id", "app-repo")),
                List.of(map(
                        "id", "app-scope",
                        "target", map("type", "system", "id", "app-core"),
                        "repositories", List.of(map(
                                "repoId", "app-repo",
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
