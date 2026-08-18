package pl.mkn.tdw.features.uiexplorer.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultUiExplorerResultReportAssemblerTest {

    private final DefaultUiExplorerResultReportAssembler assembler =
            new DefaultUiExplorerResultReportAssembler();

    @Test
    void shouldReturnExplicitUnavailableStateWithoutPlaceholderReport() {
        var assembly = assembler.assemble(null, null);

        assertThat(assembly.status()).isEqualTo(UiExplorerReportAssemblyStatus.UNAVAILABLE);
        assertThat(assembly.code()).isEqualTo("UI_EXPLORER_RESULT_NOT_AVAILABLE");
        assertThat(assembly.report()).isNull();
    }

    @Test
    void shouldAssembleTypedCrmResultIntoStableReportSections() {
        var assembly = assembler.assemble("crm-ui-report-123", result());

        assertThat(assembly.status()).isEqualTo(UiExplorerReportAssemblyStatus.AVAILABLE);
        assertThat(assembly.report().header()).isEqualTo("/crm/contacts/:contactId/preferences");
        assertThat(assembly.report().subHeader()).isEqualTo("CrmContactPreferencesComponent");
        assertThat(assembly.report().sections())
                .extracting(section -> section.id())
                .containsExactly("OVERVIEW", "FORMS_AND_RULES");
        assertThat(assembly.report().sections().get(1).markdown())
                .contains("Pole lub grupa")
                .contains("Kanal kontaktu")
                .doesNotContain("Powiazane warunki i zaleznosci")
                .doesNotContain("CONFIRMED")
                .doesNotContain("Ustalenia")
                .doesNotContain("CrmContactPreferencesComponent");
        assertThat(assembly.report().sections().get(1).meta().gaps())
                .containsExactly("Section coverage: PARTIAL");
        assertThat(assembly.report().meta().references())
                .extracting(reference -> reference.target())
                .contains("crm/agent-portal:apps/crm/contact-preferences.component.ts#L42-L58");
        assertThat(assembly.report().meta().visibilityLimits())
                .containsExactly("Backend validation rules were not inspected.");
        assertThat(assembly.report().meta().confidence()).isEqualTo("INFERRED");
    }

    @Test
    void shouldRejectAvailableResultWithoutReportId() {
        assertThatThrownBy(() -> assembler.assemble(" ", result()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportId");
    }

    private static UiExplorerResultResponse result() {
        var source = new UiExplorerSourceReference(
                "crm/agent-portal",
                "apps/crm/contact-preferences.component.ts",
                "CrmContactPreferencesComponent",
                42,
                58
        );
        var overview = new UiExplorerResultSection(
                UiExplorerSectionId.OVERVIEW,
                UiExplorerSectionMode.COMPACT,
                UiExplorerCoverageStatus.READY,
                UiExplorerClaimConfidence.CONFIRMED,
                "**Cel biznesowy**\n\nUtrzymanie preferencji kontaktu CRM.",
                List.of(source),
                List.of(),
                List.of()
        );
        var forms = new UiExplorerResultSection(
                UiExplorerSectionId.FORMS_AND_RULES,
                UiExplorerSectionMode.DEEP,
                UiExplorerCoverageStatus.PARTIAL,
                UiExplorerClaimConfidence.CONFIRMED,
                """
                        | Pole lub grupa | Znaczenie | Wymagalnosc i walidacja | Zachowanie dynamiczne | Wyliczenie lub zaleznosc |
                        | --- | --- | --- | --- | --- |
                        | Kanal kontaktu | Sposob komunikacji z kontaktem CRM | Wymagany po wyrazeniu zgody | Pojawia sie po wlaczeniu zgody | Brak wyliczenia |

                        **Reguly przekrojowe**

                        - Zapis jest dostepny po wskazaniu kanalu kontaktu.
                        """.trim(),
                List.of(source),
                List.of("The runtime form definition was not available."),
                List.of("Which CRM channel definitions are returned by the backend?")
        );
        var disabled = new UiExplorerResultSection(
                UiExplorerSectionId.STATE_AND_SYNCHRONIZATION,
                UiExplorerSectionMode.OFF,
                UiExplorerCoverageStatus.BLOCKED,
                UiExplorerClaimConfidence.UNKNOWN,
                "Disabled section.",
                List.of(),
                List.of(),
                List.of()
        );
        return new UiExplorerResultResponse(
                new UiExplorerScreenIdentity(
                        "crm-agent-portal",
                        "crm-contact-preferences",
                        "CrmContactPreferencesComponent",
                        "/crm/contacts/:contactId/preferences",
                        "CRM contact details"
                ),
                "Document a strongly anonymized CRM contact preference change.",
                new UiExplorerSourceRevision("main", "abc123"),
                "The screen maintains synthetic CRM contact preferences.",
                List.of(overview, forms, disabled),
                UiExplorerClaimConfidence.INFERRED,
                List.of("Backend validation rules were not inspected."),
                List.of("Does the backend enforce the same synthetic CRM rule?"),
                null
        );
    }
}
