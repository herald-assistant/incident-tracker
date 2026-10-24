package pl.mkn.tdw.integrations.operationalcontext.adapter.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogQueryService;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextFilter;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextQuery;
import pl.mkn.tdw.integrations.operationalcontext.internal.OperationalContextRawDocuments;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextReadModelValidator;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalContextGapContractTest {

    private final OperationalContextCatalogCodec codec = new OperationalContextCatalogCodec();
    private final OperationalContextReadModelValidator validator = new OperationalContextReadModelValidator();

    @Test
    void shouldUseAuthoredIdentitySummaryAndDefaultsWithoutContentHeuristics() {
        var catalog = decode("""
                gaps:
                  - id: crm-retry-owner
                    summary: "Critical error: missing owner blocks CRM retries."
                    description: Additional context, not the question.
                """);

        assertThat(catalog.sourceFindings()).isEmpty();
        var question = catalog.openQuestions().get(0);
        assertThat(question.id()).isEqualTo("crm-retry-owner");
        assertThat(question.question()).isEqualTo("Critical error: missing owner blocks CRM retries.");
        assertThat(question.severity()).isEqualTo("info");
        assertThat(question.status()).isEqualTo("open");
        assertThat(question.sourcePath()).isEqualTo("/gaps/0");
        assertThat(question.global()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"error", "warning", "info"})
    void shouldHonorEveryExplicitSeverity(String severity) {
        var catalog = decode("""
                gaps:
                  - id: crm-source-check
                    summary: Confirm CRM source.
                    severity: %s
                """.formatted(severity));

        assertThat(catalog.openQuestions()).singleElement()
                .satisfies(question -> assertThat(question.severity()).isEqualTo(severity));
        assertThat(catalog.sourceFindings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"medium", "critical", "INFO", "''", "false", "7"})
    void shouldReportInvalidSeverityInsteadOfInferringOrDefaulting(String severity) {
        var catalog = decode("""
                gaps:
                  - id: crm-source-check
                    summary: Confirm CRM source.
                    severity: %s
                """.formatted(severity));

        assertThat(catalog.openQuestions()).isEmpty();
        assertThat(validator.validateCatalogContract(catalog)).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("INVALID_GAP");
            assertThat(finding.sourceRefs().get(0).fieldPath()).isEqualTo("/gaps/0/severity");
        });
        assertThat(validator.validate(catalog)).containsAll(catalog.sourceFindings());
    }

    @Test
    void shouldKeepIdsWhenQuestionsAreReorderedOrEdited() {
        var before = decode("""
                gaps:
                  - {id: crm-first, summary: Confirm first CRM source.}
                  - {id: crm-second, summary: Confirm second CRM source.}
                """);
        var after = decode("""
                gaps:
                  - {id: crm-second, summary: Updated second CRM question.}
                  - {id: crm-first, summary: Updated first CRM question.}
                """);

        assertThat(after.openQuestions()).extracting(question -> question.id())
                .containsExactlyInAnyOrderElementsOf(before.openQuestions().stream().map(question -> question.id()).toList());
        assertThat(after.openQuestions().get(0).sourcePath()).isEqualTo("/gaps/0");
        assertThat(after.openQuestions().get(0).id()).isEqualTo("crm-second");
    }

    @Test
    void shouldRejectMissingIdentityAndTextAliasesWithoutManufacturingQuestions() {
        var catalog = decode("""
                gaps:
                  - {summary: Confirm CRM ownership.}
                  - {id: crm-description, description: Confirm CRM retry source.}
                  - {id: crm-alias, question: Confirm CRM support source.}
                  - null
                  - {id: crm-payload, summary: Confirm CRM source., payload: unsupported}
                """);

        assertThat(catalog.openQuestions()).isEmpty();
        assertThat(catalog.sourceFindings()).allSatisfy(finding -> assertThat(finding.code()).isEqualTo("INVALID_GAP"));
        assertThat(catalog.sourceFindings().stream().flatMap(finding -> finding.sourceRefs().stream()))
                .extracting(ref -> ref.fieldPath())
                .contains("/gaps/0/id", "/gaps/1/summary", "/gaps/2/question", "/gaps/2/summary", "/gaps/3", "/gaps/4/payload");
    }

    @Test
    void shouldDiagnoseCrossDocumentDuplicateIdentityWithBothSourcePointers() {
        var catalog = codec.decode(new OperationalContextRawDocuments("test", Map.of(
                "systems.yml", "gaps: [{id: crm-source, summary: Confirm CRM ownership.}]",
                "glossary.yml", "gaps: [{id: crm-source, summary: Confirm CRM terminology.}]"
        ))).catalog();

        assertThat(validator.validateCatalogContract(catalog)).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("DUPLICATE_GAP_ID");
            assertThat(finding.sourceRefs()).hasSize(2);
            assertThat(finding.sourceRefs()).extracting(ref -> ref.file()).containsExactly("systems.yml", "glossary.yml");
            assertThat(finding.sourceRefs()).allSatisfy(ref -> assertThat(ref.fieldPath()).isEqualTo("/gaps/0"));
        });
    }

    @Test
    void shouldKeepGlobalQuestionsAtCatalogLevelAndScopeOnlyActiveLocalQuestions() {
        var catalog = decode("""
                systems:
                  - id: crm-customer-api
                    gaps:
                      - {id: crm-local, summary: Confirm CRM API owner.}
                      - {id: crm-resolved, summary: Confirm CRM retry source., status: resolved}
                  - id: crm-support-api
                    gaps:
                      - {id: crm-other, summary: Confirm CRM support owner.}
                gaps:
                  - {id: crm-global, summary: Confirm CRM catalog coverage.}
                  - id: crm-targeted
                    summary: Confirm CRM API boundary.
                    entityType: system
                    entityId: crm-customer-api
                """);
        var selected = new OperationalContextCatalogQueryService().query(catalog, new OperationalContextQuery(
                Set.of(OperationalContextEntryType.SYSTEM),
                List.of(OperationalContextFilter.exact(OperationalContextEntryType.SYSTEM, "id", "crm-customer-api"))
        ));

        assertThat(catalog.openQuestions()).hasSize(5);
        assertThat(selected.openQuestions()).extracting(question -> question.id()).containsExactly("crm-targeted", "crm-local");
        assertThat(selected.sourceFindings()).isEqualTo(catalog.sourceFindings());
        assertThat(new OperationalContextCatalogQueryService().query(catalog, OperationalContextQuery.all()))
                .isSameAs(catalog);
    }

    @Test
    void shouldReportUnknownAndMalformedTargetsAndNeverConvertMissingParentsIntoGlobalQuestions() {
        var catalog = decode("""
                systems:
                  - gaps: [{id: crm-parent, summary: Confirm CRM owner.}]
                gaps:
                  - {id: crm-unknown, summary: Confirm CRM source., entityType: system, entityId: crm-missing}
                  - {id: crm-partial, summary: Confirm CRM source., entityId: crm-customer-api}
                """);

        assertThat(catalog.openQuestions()).extracting(question -> question.id()).containsExactly("crm-unknown");
        assertThat(catalog.sourceFindings()).extracting(finding -> finding.code())
                .contains("INVALID_GAP_TARGET", "INVALID_GAP");
        assertThat(validator.validateCatalogContract(catalog)).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("UNKNOWN_GAP_TARGET");
            assertThat(finding.message()).contains("system:crm-missing");
            assertThat(finding.sourceRefs().get(0).fieldPath()).isEqualTo("/gaps/0");
        });
    }

    @Test
    void shouldDiagnoseStructuralUnresolvedSignalsWithoutInferringQuestionsFromProse() {
        var catalog = decode("""
                systems:
                  - id: crm-customer-api
                    summary: "Unresolved missing owner error."
                    matchSignals:
                      strong:
                        unresolvedGlossaryReferences: [crm-contact-state]
                """);

        assertThat(catalog.openQuestions()).isEmpty();
        assertThat(catalog.sourceFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("UNREGISTERED_GAP_SIGNAL");
            assertThat(finding.sourceRefs().get(0).fieldPath())
                    .isEqualTo("/systems/0/matchSignals/strong/unresolvedGlossaryReferences");
        });
    }

    @Test
    void shouldPreserveOriginalArrayOffsetsAfterMalformedEntities() {
        var catalog = decode("""
                systems:
                  - malformed
                  - id: crm-customer-api
                    gaps: [{id: crm-source, summary: Confirm CRM source.}]
                """);

        assertThat(catalog.openQuestions()).singleElement()
                .satisfies(question -> assertThat(question.sourcePath()).isEqualTo("/systems/1/gaps/0"));
        assertThat(catalog.sourceFindings()).singleElement()
                .satisfies(finding -> assertThat(finding.code()).isEqualTo("INVALID_CATALOG_ENTITY"));
    }

    private OperationalContextCatalog decode(String yaml) {
        return codec.decode(new OperationalContextRawDocuments("test", Map.of("systems.yml", yaml))).catalog();
    }
}
