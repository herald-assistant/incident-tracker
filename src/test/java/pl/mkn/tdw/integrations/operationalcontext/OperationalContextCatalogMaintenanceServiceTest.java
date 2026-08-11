package pl.mkn.tdw.integrations.operationalcontext;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextCatalogMaintenanceServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldCreateAndCompletePutEveryYamlEntityTypeWithAnonymousCrmFixtures() {
        try (var harness = harness("crm-all-types")) {
            var cases = List.of(
                    entity("system", "crm-case-service", map(
                            "id", "crm-case-service",
                            "name", "CRM Case Service",
                            "systemType", "internal-service",
                            "aliases", List.of("crm-case")
                    )),
                    entity("repository", "crm-case-repository", map(
                            "id", "crm-case-repository",
                            "name", "CRM Case Repository",
                            "repositoryType", "service",
                            "git", map("provider", "gitlab", "projectPath", "crm/case-service")
                    )),
                    entity("code-search-scope", "crm-case-search", map(
                            "id", "crm-case-search",
                            "name", "CRM Case Search",
                            "scopeType", "system",
                            "target", map("type", "system", "id", "crm-source-system"),
                            "repositories", List.of(map(
                                    "repoId", "crm-source-repository",
                                    "role", "primary",
                                    "priority", 1,
                                    "searchMode", "whole-repository",
                                    "pathPrefixes", List.of()
                            ))
                    )),
                    entity("process", "crm-case-routing", map(
                            "id", "crm-case-routing",
                            "name", "CRM Case Routing",
                            "type", "business-process",
                            "steps", List.of()
                    )),
                    entity("integration", "crm-source-to-target", map(
                            "id", "crm-source-to-target",
                            "name", "CRM Source to Target",
                            "participants", map(
                                    "source", map("system", "crm-source-system", "role", "producer"),
                                    "targets", List.of(map("system", "crm-target-system", "role", "consumer"))
                            )
                    )),
                    entity("bounded-context", "crm-case-management", map(
                            "id", "crm-case-management",
                            "name", "CRM Case Management",
                            "type", "core-domain"
                    )),
                    entity("team", "crm-case-operations", map(
                            "id", "crm-case-operations",
                            "name", "CRM Case Operations",
                            "type", "internal-development"
                    )),
                    entity("glossary-term", "crm-customer-profile", map(
                            "id", "crm-customer-profile",
                            "term", "CRM Customer Profile",
                            "category", "domain-term",
                            "definition", "An anonymized CRM customer profile.",
                            "canonicalReferences", List.of("system:crm-source-system")
                    )),
                    entity("handoff-rule", "crm-customer-sync-delayed", map(
                            "id", "crm-customer-sync-delayed",
                            "title", "CRM customer synchronization is delayed",
                            "confidence", "medium",
                            "useWhen", List.of("An anonymized CRM customer update is delayed."),
                            "requiredEvidence", List.of("An anonymized CRM correlation key."),
                            "expectedFirstAction", List.of("Verify the CRM synchronization boundary."),
                            "references", map("systems", List.of("crm-source-system"))
                    ))
            );

            for (var current : cases) {
                var created = harness.service().create(new OperationalContextCatalogMutationCommand(
                        current.type(), current.id(), current.payload()
                ));

                assertEquals(current.id(), created.entity().id(), current.type());
                assertEquals(current.type(), created.entity().type(), current.type());
                assertEquals(logicalSource(current.type()), created.entity().sourceFile(), current.type());

                var replacement = new LinkedHashMap<>(current.payload());
                var updatedField = switch (current.type()) {
                    case "glossary-term" -> "definition";
                    case "handoff-rule" -> "title";
                    default -> "summary";
                };
                replacement.put(updatedField, "Anonymous CRM maintenance update");
                var updated = harness.service().update(new OperationalContextCatalogMutationCommand(
                        current.type(), current.id(), replacement
                ));

                assertEquals(
                        "Anonymous CRM maintenance update",
                        harness.service().entity(current.type(), current.id()).payload().get(updatedField),
                        current.type()
                );
            }
        }
    }

    @Test
    void shouldApplyCompletePutAndPreserveOnlyServerOwnedExtensions() {
        try (var harness = harness("crm-preserve")) {
            var current = harness.service().entity("system", "crm-source-system");
            var replacement = map(
                    "id", "crm-source-system",
                    "name", "CRM Source System Updated",
                    "systemType", "internal-service",
                    "aliases", List.of()
            );

            var result = harness.service().update(new OperationalContextCatalogMutationCommand(
                    "system", "crm-source-system", replacement
            ));

            assertEquals(List.of(), result.entity().payload().get("aliases"));
            assertFalse(result.entity().payload().containsKey("summary"));
            assertEquals(
                    Map.of("label", "anonymous-crm-extension"),
                    result.entity().payload().get("xCrmExtension")
            );
            assertFalse(result.entity().payload().containsKey("kind"));
            assertEquals("internal-service", result.entity().payload().get("systemType"));
        }
    }

    @Test
    void shouldPreserveAnonymousCrmRootMetadataAndUntouchedDocumentsByteForByte() {
        try (var harness = harness("crm-document-boundary")) {
            var before = harness.snapshotStore().currentStoredSnapshot();
            var beforeDocuments = before.rawDocuments().contents();

            harness.service().create(new OperationalContextCatalogMutationCommand(
                    "team",
                    "crm-customer-care",
                    map("id", "crm-customer-care", "name", "CRM Customer Care")
            ));

            var after = harness.snapshotStore().currentStoredSnapshot();
            for (var entry : beforeDocuments.entrySet()) {
                if (!entry.getKey().equals("teams.yml")) {
                    assertEquals(entry.getValue(), after.rawDocuments().content(entry.getKey()), entry.getKey());
                }
            }
            assertEquals(
                    Map.of("label", "anonymous-crm-root"),
                    after.decodedDocuments().get("teams.yml").get("xCrmRoot")
            );
        }
    }

    @Test
    void shouldRejectAmbiguousOrDerivedPayloadWithoutChangingLocalCopy() {
        try (var harness = harness("crm-invalid-payload")) {
            var digest = harness.digest();

            var nullError = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().update(new OperationalContextCatalogMutationCommand(
                            "team", "crm-operations-team",
                            map("id", "crm-operations-team", "name", null)
                    ))
            );
            assertEquals("/payload/name", nullError.fieldErrors().get(0).pointer());

            var emptyObject = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().update(new OperationalContextCatalogMutationCommand(
                            "team", "crm-operations-team",
                            map("id", "crm-operations-team", "name", "CRM Operations", "matchSignals", Map.of())
                    ))
            );
            assertTrue(emptyObject.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/matchSignals")));

            var projection = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().update(new OperationalContextCatalogMutationCommand(
                            "team", "crm-operations-team",
                            map(
                                    "id", "crm-operations-team",
                                    "name", "CRM Operations",
                                    "rawSourcePreview", "not-a-write-contract"
                            )
                    ))
            );
            assertTrue(projection.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/rawSourcePreview")));
            assertEquals(digest, harness.digest());
        }
    }

    @Test
    void shouldRejectDuplicateMissingMismatchAndDanglingReferenceWithJsonPointers() {
        try (var harness = harness("crm-command-errors")) {
            var digest = harness.digest();
            var duplicate = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "team", "crm-operations-team",
                            map("id", "crm-operations-team", "name", "CRM Duplicate Team")
                    ))
            );
            assertEquals(OperationalContextCatalogMaintenanceException.Code.DUPLICATE_ID, duplicate.code());

            var mismatch = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().update(new OperationalContextCatalogMutationCommand(
                            "team", "crm-operations-team",
                            map("id", "crm-renamed-team", "name", "CRM Renamed Team")
                    ))
            );
            assertTrue(mismatch.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/id")));

            var missing = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().entity("team", "crm-missing-team")
            );
            assertEquals(OperationalContextCatalogMaintenanceException.Code.ENTITY_NOT_FOUND, missing.code());

            var dangling = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "system", "crm-orphan-service",
                            map(
                                    "id", "crm-orphan-service",
                                    "name", "CRM Orphan Service",
                                    "systemType", "internal-service",
                                    "references", map("processes", List.of("crm-missing-process"))
                            )
                    ))
            );
            assertTrue(dangling.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/references/processes/0")
            ));
            assertEquals(digest, harness.digest());
        }
    }

    @Test
    void shouldValidateCanonicalAnonymousCrmProcessStepsAndReferences() {
        try (var harness = harness("crm-process-steps")) {
            var valid = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "process",
                    "crm-contact-update",
                    map(
                            "id", "crm-contact-update",
                            "name", "CRM Contact Update",
                            "steps", List.of(map(
                                    "id", "accept-contact-update",
                                    "name", "Accept CRM contact update",
                                    "type", "business-step",
                                    "summary", "Validate the anonymized CRM contact change.",
                                    "references", map(
                                            "systems", List.of("crm-source-system"),
                                            "repositories", List.of("crm-source-repository"),
                                            "boundedContexts", List.of("crm-customer-context")
                                    ),
                                    "matchSignals", map("strong", map("terms", List.of("accept CRM contact update")))
                            ))
                    )
            ));
            var storedSteps = (List<?>) valid.entity().payload().get("steps");
            assertEquals("accept-contact-update", ((Map<?, ?>) storedSteps.get(0)).get("id"));

            var invalid = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "process",
                            "crm-contact-duplicate-flow",
                            map(
                                    "id", "crm-contact-duplicate-flow",
                                    "name", "CRM Contact Duplicate Flow",
                                    "steps", List.of(
                                            map("id", "route-contact", "name", "Route CRM contact"),
                                            map(
                                                    "id", "route-contact",
                                                    "name", "",
                                                    "references", map("systems", List.of("crm-missing-system"))
                                            )
                                    )
                            )
                    ))
            );
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/steps/1/id")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/steps/1/name")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/steps/1/references/systems/0")));
        }
    }

    @Test
    void shouldValidateGuidedAnonymousCrmMatchSignalsAndCanonicalRelations() {
        try (var harness = harness("crm-signals-relations")) {
            harness.service().create(new OperationalContextCatalogMutationCommand(
                    "process",
                    "crm-contact-update",
                    map("id", "crm-contact-update", "name", "CRM Contact Update")
            ));

            var valid = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "system",
                    "crm-contact-routing",
                    map(
                            "id", "crm-contact-routing",
                            "name", "CRM Contact Routing",
                            "systemType", "internal-service",
                            "matchSignals", map(
                                    "exact", map("serviceNames", List.of("crm-contact-routing")),
                                    "strong", map("routes", List.of("/crm/contacts"))
                            ),
                            "relations", List.of(map(
                                    "type", "supports",
                                    "targetType", "process",
                                    "target", "crm-contact-update",
                                    "evidence", "Anonymized CRM process support"
                            ))
                    )
            ));
            assertEquals("crm-contact-update", ((Map<?, ?>) ((List<?>) valid.entity().payload().get("relations")).get(0)).get("target"));

            var invalidSignals = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().update(new OperationalContextCatalogMutationCommand(
                            "team",
                            "crm-operations-team",
                            map(
                                    "id", "crm-operations-team",
                                    "name", "CRM Operations Team",
                                    "matchSignals", map("strong", map("teamNames", "CRM Operations Team"))
                            )
                    ))
            );
            assertTrue(invalidSignals.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/matchSignals/strong/teamNames")
            ));

            var invalidRelations = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "system",
                            "crm-invalid-relations",
                            map(
                                    "id", "crm-invalid-relations",
                                    "name", "CRM Invalid Relations",
                                    "systemType", "internal-service",
                                    "relations", List.of(
                                            map("type", "supports", "targetType", "process", "target", "crm-missing-process"),
                                            map("type", "uses", "targetType", "unsupported-crm-type", "target", "crm-contact-update")
                                    )
                            )
                    ))
            );
            assertTrue(invalidRelations.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/relations/0/target")
            ));
            assertTrue(invalidRelations.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/relations/1/targetType")
            ));
        }
    }

    @Test
    void shouldValidateGuidedAnonymousCrmFailureArtifactsCoverageAndGaps() {
        try (var harness = harness("crm-guided-knowledge-limits")) {
            var process = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "process",
                    "crm-contact-update",
                    map(
                            "id", "crm-contact-update",
                            "name", "CRM Contact Update",
                            "steps", List.of(map("id", "validate-contact", "name", "Validate CRM contact")),
                            "failureModes", List.of(map(
                                    "id", "crm-contact-rejected",
                                    "name", "CRM contact rejected",
                                    "summary", "The anonymized CRM contact update is rejected.",
                                    "affectedStep", "validate-contact",
                                    "signals", List.of("CRM validation rejection")
                            )),
                            "dataAndArtifacts", map(
                                    "primaryObjects", List.of("ContactPreference"),
                                    "inputArtifacts", List.of("Anonymized CRM contact change request"),
                                    "outputArtifacts", List.of("CRM contact update confirmation")
                            )
                    )
            ));
            assertEquals(
                    "crm-contact-rejected",
                    ((Map<?, ?>) ((List<?>) process.entity().payload().get("failureModes")).get(0)).get("id")
            );

            var context = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "bounded-context",
                    "crm-engagement-context",
                    map(
                            "id", "crm-engagement-context",
                            "name", "CRM Engagement Context",
                            "sourceCoverage", map(
                                    "status", "partial",
                                    "scannedSources", List.of("Anonymized CRM domain notes"),
                                    "expectedSources", List.of("CRM consent model"),
                                    "limitations", List.of("CRM engagement boundary not reviewed")
                            ),
                            "gaps", List.of(map(
                                    "id", "crm-consent-boundary",
                                    "type", "unresolved-boundary",
                                    "summary", "Confirm the CRM consent boundary.",
                                    "severity", "warning",
                                    "status", "open",
                                    "suggestedNextSources", List.of("Anonymized CRM glossary review")
                            ))
                    )
            ));
            assertTrue(context.entity().payload().get("sourceCoverage") instanceof Map<?, ?>);

            var invalid = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "process",
                            "crm-invalid-guided-process",
                            map(
                                    "id", "crm-invalid-guided-process",
                                    "name", "CRM Invalid Guided Process",
                                    "steps", List.of(map("id", "known-step", "name", "Known CRM step")),
                                    "failureModes", List.of(
                                            map(
                                                    "id", "CRM invalid",
                                                    "name", "",
                                                    "summary", "",
                                                    "affectedStep", "missing-crm-step",
                                                    "signals", "not-a-list"
                                            )
                                    ),
                                    "dataAndArtifacts", map("inputArtifacts", "not-a-list")
                            )
                    ))
            );
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/failureModes/0/id")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/failureModes/0/affectedStep")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/dataAndArtifacts/inputArtifacts")));

            var invalidCoverage = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "bounded-context",
                            "crm-invalid-coverage-context",
                            map(
                                    "id", "crm-invalid-coverage-context",
                                    "name", "CRM Invalid Coverage Context",
                                    "sourceCoverage", map("status", "invented-crm-status"),
                                    "gaps", List.of(map(
                                            "id", "CRM invalid gap",
                                            "summary", "",
                                            "severity", "critical",
                                            "status", "pending"
                                    ))
                            )
                    ))
            );
            assertTrue(invalidCoverage.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/sourceCoverage/status")));
            assertTrue(invalidCoverage.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/gaps/0/summary")));
            assertTrue(invalidCoverage.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/gaps/0/severity")));
        }
    }

    @Test
    void shouldValidateGuidedAnonymousCrmSystemRuntimeAndRepositoryExplorationMetadata() {
        try (var harness = harness("crm-guided-system-repository-metadata")) {
            var system = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "system",
                    "crm-contact-platform",
                    map(
                            "id", "crm-contact-platform",
                            "name", "CRM Contact Platform",
                            "systemType", "internal-service",
                            "participants", map(
                                    "externalOwner", "CRM managed platform provider",
                                    "futureCrmParticipantHint", map("reviewed", true)
                            ),
                            "runtime", map(
                                    "configurationDirectory", "crm/contact-platform",
                                    "futureCrmRuntimeHint", map("reviewed", true)
                            )
                    )
            ));
            assertEquals(
                    "crm/contact-platform",
                    ((Map<?, ?>) system.entity().payload().get("runtime")).get("configurationDirectory")
            );

            var repository = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "repository",
                    "crm-contact-platform-repository",
                    map(
                            "id", "crm-contact-platform-repository",
                            "name", "CRM Contact Platform Repository",
                            "git", map("projectPath", "crm/contact-platform"),
                            "evidence", List.of(map(
                                    "sourceRef", "crm/contact-platform/pom.xml",
                                    "evidenceType", "build-definition",
                                    "note", "Anonymized CRM service module.",
                                    "futureCrmEvidenceHint", true
                            )),
                            "llmToolHints", map(
                                    "answerWhenUserMentions", List.of("CRM contact validation"),
                                    "disambiguateFrom", List.of("CRM authentication account service"),
                                    "futureCrmToolHint", map("reviewed", true)
                            )
                    )
            ));
            assertEquals(
                    "build-definition",
                    ((Map<?, ?>) ((List<?>) repository.entity().payload().get("evidence")).get(0)).get("evidenceType")
            );

            var invalidSystem = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "system",
                            "crm-invalid-runtime",
                            map(
                                    "id", "crm-invalid-runtime",
                                    "name", "CRM Invalid Runtime",
                                    "systemType", "internal-service",
                                    "participants", map("externalOwner", List.of("CRM provider")),
                                    "runtime", map("configurationDirectory", "../crm/contact-platform")
                            )
                    ))
            );
            assertTrue(invalidSystem.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/participants/externalOwner")
            ));
            assertTrue(invalidSystem.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/runtime/configurationDirectory")
            ));

            var invalidRepository = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "repository",
                            "crm-invalid-repository-metadata",
                            map(
                                    "id", "crm-invalid-repository-metadata",
                                    "name", "CRM Invalid Repository Metadata",
                                    "git", map("projectPath", "crm/invalid-repository"),
                                    "evidence", List.of(map("sourceRef", "", "evidenceType", "")),
                                    "llmToolHints", map("answerWhenUserMentions", "CRM contact validation")
                            )
                    ))
            );
            assertTrue(invalidRepository.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/evidence/0/sourceRef")
            ));
            assertTrue(invalidRepository.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/evidence/0/evidenceType")
            ));
            assertTrue(invalidRepository.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/llmToolHints/answerWhenUserMentions")
            ));
        }
    }

    @Test
    void shouldValidateGuidedAnonymousCrmBoundedContextSemanticsAndPreserveExtensions() {
        try (var harness = harness("crm-guided-bounded-context-semantics")) {
            var created = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "bounded-context",
                    "crm-contact-preferences",
                    map(
                            "id", "crm-contact-preferences",
                            "name", "CRM Contact Preferences",
                            "type", "core-domain",
                            "localLanguageSummary", List.of(
                                    "In CRM, contact means a communication profile, not an authentication account."
                            ),
                            "scope", map(
                                    "includes", List.of("CRM contact preference validation"),
                                    "excludes", List.of("Authentication credential lifecycle"),
                                    "businessCapabilities", List.of("CRM Contact Preference Management"),
                                    "coreEntities", List.of("ContactPreference"),
                                    "keyDecisions", List.of("Whether an anonymized CRM preference is valid"),
                                    "futureCrmScopeHint", map("reviewed", true)
                            ),
                            "semanticBoundary", map(
                                    "coreConcepts", List.of("Contact preference"),
                                    "localConcepts", List.of("CRM contact profile"),
                                    "canonicalEntities", List.of("ContactPreference"),
                                    "commands", List.of("UpdateContactPreference"),
                                    "events", List.of("ContactPreferenceUpdated"),
                                    "invariants", List.of("A preference belongs to one anonymized CRM contact profile"),
                                    "ownsLanguage", List.of("CRM contact preference"),
                                    "doesNotOwn", List.of("Authentication account credential"),
                                    "futureCrmSemanticHint", map("reviewed", true)
                            ),
                            "evidence", List.of(map(
                                    "sourceRef", "Anonymized CRM domain glossary",
                                    "evidenceType", "domain-documentation",
                                    "note", "CRM semantic boundary review.",
                                    "futureCrmEvidenceHint", true
                            )),
                            "llmToolHints", map(
                                    "answerWhenUserMentions", List.of("CRM contact preference"),
                                    "disambiguateFrom", List.of("Authentication account"),
                                    "usefulSearchKeywords", List.of("ContactPreference"),
                                    "explanationStyle", "Explain as the CRM contact-preference boundary.",
                                    "futureCrmToolHint", map("reviewed", true)
                            )
                    )
            ));

            assertTrue(((Map<?, ?>) created.entity().payload().get("scope")).containsKey("futureCrmScopeHint"));
            assertTrue(((Map<?, ?>) created.entity().payload().get("semanticBoundary")).containsKey("futureCrmSemanticHint"));
            assertTrue(((Map<?, ?>) ((List<?>) created.entity().payload().get("evidence")).get(0)).containsKey("futureCrmEvidenceHint"));
            assertTrue(((Map<?, ?>) created.entity().payload().get("llmToolHints")).containsKey("futureCrmToolHint"));

            var invalid = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "bounded-context",
                            "crm-invalid-semantics",
                            map(
                                    "id", "crm-invalid-semantics",
                                    "name", "CRM Invalid Semantics",
                                    "localLanguageSummary", List.of(""),
                                    "scope", map("includes", "not-a-list"),
                                    "semanticBoundary", map("invariants", List.of("")),
                                    "evidence", List.of(map("sourceRef", "", "evidenceType", "domain-documentation")),
                                    "llmToolHints", map(
                                            "usefulSearchKeywords", "not-a-list",
                                            "explanationStyle", ""
                                    )
                            )
                    ))
            );
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/localLanguageSummary/0")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/scope/includes")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/semanticBoundary/invariants/0")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/evidence/0/sourceRef")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/llmToolHints/usefulSearchKeywords")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/llmToolHints/explanationStyle")));
        }
    }

    @Test
    void shouldValidateGuidedAnonymousCrmProcessBoundaryLifecycleAndCompletionSignals() {
        try (var harness = harness("crm-guided-process-semantics")) {
            var created = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "process",
                    "crm-contact-preference-update",
                    map(
                            "id", "crm-contact-preference-update",
                            "name", "CRM Contact Preference Update",
                            "processBoundary", map(
                                    "businessCapability", "CRM Contact Preference Management",
                                    "startsWhen", List.of("An anonymized CRM contact update is accepted."),
                                    "endsWhen", List.of("The CRM contact view confirms the update."),
                                    "includes", List.of("CRM contact preference validation"),
                                    "excludes", List.of("Authentication credential lifecycle"),
                                    "assumptions", List.of("CRM contact identity is already resolved."),
                                    "futureCrmBoundaryHint", true
                            ),
                            "lifecycle", map(
                                    "triggers", List.of(map(
                                            "type", "api",
                                            "name", "CRM contact update",
                                            "exchange", "crm.contact.update",
                                            "futureCrmTriggerHint", true
                                    )),
                                    "entryCriteria", List.of("CRM contact identity is available."),
                                    "statuses", List.of("requested", "applied"),
                                    "transitions", List.of(map(
                                            "from", "requested",
                                            "to", "applied",
                                            "trigger", "CRM validation succeeds.",
                                            "futureCrmTransitionHint", true
                                    )),
                                    "terminalStates", List.of("applied"),
                                    "successOutcomes", List.of("CRM contact preference is applied."),
                                    "partialOutcomes", List.of("CRM projection remains pending."),
                                    "failedOutcomes", List.of("CRM validation rejects the update."),
                                    "cancellationOutcomes", List.of("CRM agent cancels the update."),
                                    "futureCrmLifecycleHint", true
                            ),
                            "completionSignals", map(
                                    "successful", List.of("CRM contact confirmation is recorded."),
                                    "partial", List.of("CRM projection remains pending."),
                                    "failed", List.of("CRM validation rejection is recorded."),
                                    "cancelled", List.of("CRM cancellation is recorded."),
                                    "futureCrmCompletionHint", true
                            )
                    )
            ));
            assertEquals(
                    true,
                    ((Map<?, ?>) created.entity().payload().get("processBoundary")).get("futureCrmBoundaryHint")
            );
            assertEquals(
                    true,
                    ((Map<?, ?>) created.entity().payload().get("lifecycle")).get("futureCrmLifecycleHint")
            );

            var legacy = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "process",
                    "crm-legacy-contact-process",
                    map(
                            "id", "crm-legacy-contact-process",
                            "name", "CRM Legacy Contact Process",
                            "processBoundary", List.of("CRM contact confirmation is visible."),
                            "lifecycle", List.of("requested", "applied"),
                            "completionSignals", "CRM contact confirmation is recorded."
                    )
            ));
            assertEquals(List.of("requested", "applied"), legacy.entity().payload().get("lifecycle"));

            var invalid = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().create(new OperationalContextCatalogMutationCommand(
                            "process",
                            "crm-invalid-process-semantics",
                            map(
                                    "id", "crm-invalid-process-semantics",
                                    "name", "CRM Invalid Process Semantics",
                                    "processBoundary", map(
                                            "businessCapability", "",
                                            "endsWhen", "not-a-crm-list"
                                    ),
                                    "lifecycle", map(
                                            "triggers", List.of(map("type", "", "name", "")),
                                            "statuses", "not-a-crm-list",
                                            "transitions", List.of(map("to", "", "trigger", ""))
                                    ),
                                    "completionSignals", map("successful", "not-a-crm-list")
                            )
                    ))
            );
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/processBoundary/businessCapability")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/processBoundary/endsWhen")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/lifecycle/triggers/0/type")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/lifecycle/transitions/0/to")));
            assertTrue(invalid.fieldErrors().stream().anyMatch(error -> error.pointer().equals("/payload/completionSignals/successful")));
        }
    }

    @Test
    void shouldKeepParticipantRepositoriesServerOwnedForAnonymousCrmIntegration() {
        try (var harness = harness("crm-participant-repositories")) {
            var replacement = map(
                    "id", "crm-existing-integration",
                    "name", "CRM Existing Integration Updated",
                    "participants", map(
                            "source", map("system", "crm-source-system", "role", "producer"),
                            "targets", List.of(map("system", "crm-target-system", "role", "consumer"))
                    )
            );

            var updated = harness.service().update(new OperationalContextCatalogMutationCommand(
                    "integration", "crm-existing-integration", replacement
            ));
            var participants = (Map<?, ?>) updated.entity().payload().get("participants");
            assertEquals(List.of("crm-source-repository"), ((Map<?, ?>) participants.get("source")).get("repositories"));
            assertEquals(
                    List.of("crm-source-repository"),
                    ((Map<?, ?>) ((List<?>) participants.get("targets")).get(0)).get("repositories")
            );

            var rejected = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().update(new OperationalContextCatalogMutationCommand(
                            "integration",
                            "crm-existing-integration",
                            map(
                                    "id", "crm-existing-integration",
                                    "name", "CRM Existing Integration",
                                    "participants", map(
                                            "source", map(
                                                    "system", "crm-source-system",
                                                    "repositories", List.of("crm-source-repository")
                                            ),
                                            "targets", List.of(map("system", "crm-target-system"))
                                    )
                            )
                    ))
            );
            assertTrue(rejected.fieldErrors().stream().anyMatch(
                    error -> error.pointer().equals("/payload/participants/source/repositories")
            ));
        }
    }

    @Test
    void shouldRestrictDeleteWithInboundReferencesAndAllowUnreferencedDelete() {
        try (var harness = harness("crm-delete")) {
            harness.service().create(new OperationalContextCatalogMutationCommand(
                    "integration", "crm-customer-sync",
                    map(
                            "id", "crm-customer-sync",
                            "name", "CRM Customer Sync",
                            "participants", map(
                                    "source", map("system", "crm-source-system", "role", "producer"),
                                    "targets", List.of(map("system", "crm-target-system", "role", "consumer"))
                            )
                    )
            ));

            var blocked = harness.service().deleteImpact("system", "crm-target-system");
            assertFalse(blocked.allowed());
            assertTrue(blocked.inboundReferences().stream().allMatch(reference ->
                    reference.sourceFile() == null || !reference.sourceFile().contains("/")
            ));
            var blockedDelete = assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().delete("system", "crm-target-system")
            );
            assertEquals(OperationalContextCatalogMaintenanceException.Code.DELETE_RESTRICTED, blockedDelete.code());

            var allowed = harness.service().deleteImpact("team", "crm-operations-team");
            assertTrue(allowed.allowed());
            harness.service().delete("team", "crm-operations-team");
            assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().entity("team", "crm-operations-team")
            );
        }
    }

    @Test
    void shouldApplyRestrictDeleteAcrossAnonymousCrmGlossaryAndHandoffGraph() {
        try (var harness = harness("crm-glossary-handoff-delete")) {
            var term = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "glossary-term",
                    "crm-customer-profile",
                    map(
                            "id", "crm-customer-profile",
                            "term", "CRM Customer Profile",
                            "category", "domain-term",
                            "definition", "An anonymized CRM customer profile."
                    )
            ));
            var rule = harness.service().create(new OperationalContextCatalogMutationCommand(
                    "handoff-rule",
                    "crm-contact-sync-delayed",
                    map(
                            "id", "crm-contact-sync-delayed",
                            "title", "CRM contact synchronization is delayed",
                            "references", map("terms", List.of("crm-customer-profile")),
                            "requiredEvidence", List.of("An anonymized CRM correlation key.")
                    )
            ));

            var blockedTerm = harness.service().deleteImpact("glossary-term", "crm-customer-profile");
            assertFalse(blockedTerm.allowed());
            assertTrue(blockedTerm.inboundReferences().stream().anyMatch(reference ->
                    reference.sourceType().equals("handoff-rule")
                            && reference.sourceId().equals("crm-contact-sync-delayed")
                            && "handoff-rules.yml".equals(reference.sourceFile())
            ));
            assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().delete("glossary-term", "crm-customer-profile")
            );

            harness.service().delete("handoff-rule", "crm-contact-sync-delayed");
            assertTrue(harness.service().deleteImpact("glossary-term", "crm-customer-profile").allowed());
            harness.service().delete("glossary-term", "crm-customer-profile");
            assertThrows(
                    OperationalContextCatalogMaintenanceException.class,
                    () -> harness.service().entity("glossary-term", "crm-customer-profile")
            );
        }
    }

    private Harness harness(String directory) {
        var properties = new OperationalContextProperties();
        properties.setStorageDirectory(temporaryDirectory.resolve(directory).toString());
        var source = (OperationalContextDocumentSource) () -> new OperationalContextRawDocuments("classpath", crmDocuments());
        var codec = new OperationalContextCatalogCodec();
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var validationService = new OperationalContextCatalogValidationService(
                new OperationalContextValidationBaselineLoader(mapper, new DefaultResourceLoader())
        );
        var localStore = new LocalOperationalContextStore(
                properties,
                source,
                codec,
                new OperationalContextAtomicMover(),
                validationService
        );
        var snapshotStore = new DefaultOperationalContextSnapshotStore(localStore);
        var service = new OperationalContextCatalogMaintenanceService(
                snapshotStore,
                new OperationalContextYamlWriter()
        );
        snapshotStore.currentStoredSnapshot();
        return new Harness(service, snapshotStore);
    }

    private Map<String, String> crmDocuments() {
        var documents = new LinkedHashMap<String, String>();
        documents.put("teams.yml", """
                schemaVersion: 1
                catalogKind: operational-context-teams
                xCrmRoot:
                  label: anonymous-crm-root
                teams:
                  - id: crm-operations-team
                    name: CRM Operations Team
                    type: internal-development
                """);
        documents.put("systems.yml", """
                schemaVersion: 1
                catalogKind: operational-context-systems
                systems:
                  - id: crm-source-system
                    name: CRM Source System
                    kind: internal-service
                    xCrmExtension:
                      label: anonymous-crm-extension
                  - id: crm-target-system
                    name: CRM Target System
                    systemType: internal-service
                """);
        documents.put("repo-map.yml", """
                schemaVersion: 1
                catalogKind: operational-context-repositories
                repositories:
                  - id: crm-source-repository
                    name: CRM Source Repository
                    repositoryType: service
                    git:
                      provider: gitlab
                      projectPath: crm/source-service
                """);
        documents.put("processes.yml", yaml("operational-context-processes", "processes"));
        documents.put("integrations.yml", """
                schemaVersion: 1
                catalogKind: operational-context-integrations
                integrations:
                  - id: crm-existing-integration
                    name: CRM Existing Integration
                    participants:
                      source:
                        system: crm-source-system
                        role: producer
                        repositories:
                          - crm-source-repository
                      targets:
                        - system: crm-target-system
                          role: consumer
                          repositories:
                            - crm-source-repository
                """);
        documents.put("code-search-scopes.yml", yaml("operational-context-code-search-scopes", "codeSearchScopes"));
        documents.put("bounded-contexts.yml", """
                schemaVersion: 1
                catalogKind: operational-context-bounded-contexts
                boundedContexts:
                  - id: crm-customer-context
                    name: CRM Customer Context
                    type: core-domain
                """);
        documents.put("glossary.yml", yaml("operational-context-glossary", "terms"));
        documents.put("handoff-rules.yml", yaml("operational-context-handoff-rules", "handoffRules"));
        documents.put("operational-context-index.md", "# Anonymous CRM Operational Context\n");
        return Map.copyOf(documents);
    }

    private String yaml(String catalogKind, String collection) {
        return """
                schemaVersion: 1
                catalogKind: %s
                %s: []
                """.formatted(catalogKind, collection);
    }

    private EntityCase entity(String type, String id, Map<String, Object> payload) {
        return new EntityCase(type, id, payload);
    }

    private static Map<String, Object> map(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (var index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String logicalSource(String type) {
        return OperationalContextCatalogEntityType.fromExternalName(type).logicalDocument();
    }

    private record EntityCase(String type, String id, Map<String, Object> payload) {
    }

    private record Harness(
            OperationalContextCatalogMaintenanceService service,
            DefaultOperationalContextSnapshotStore snapshotStore
    ) implements AutoCloseable {

        String digest() {
            return snapshotStore.currentStoredSnapshot().readSnapshot().contentDigest();
        }

        @Override
        public void close() {
        }
    }
}
