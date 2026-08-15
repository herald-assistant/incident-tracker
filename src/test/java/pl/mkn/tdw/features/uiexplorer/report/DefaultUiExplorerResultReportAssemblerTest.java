package pl.mkn.tdw.features.uiexplorer.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerChangePreparationSummary;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCrossSectionDependency;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerFinding;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerProfile;
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
        assertThat(assembly.report().header()).isEqualTo("UI Explorer: CRM Contact Preferences");
        assertThat(assembly.report().subHeader()).isEqualTo("main @ abc123");
        assertThat(assembly.report().sections())
                .extracting(section -> section.id())
                .containsExactly("OVERVIEW", "FORMS_AND_RULES");
        assertThat(assembly.report().sections().get(1).markdown())
                .contains("Dynamic contact preference validation")
                .contains("`CONFIRMED`")
                .contains("Wplyw zmiany");
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
                "The screen supports synthetic CRM contact preference maintenance.",
                List.of(),
                List.of(),
                List.of(source),
                List.of(),
                List.of()
        );
        var forms = new UiExplorerResultSection(
                UiExplorerSectionId.FORMS_AND_RULES,
                UiExplorerSectionMode.DEEP,
                UiExplorerCoverageStatus.PARTIAL,
                "The synthetic CRM form contains dynamic validation.",
                List.of(new UiExplorerFinding(
                        "Dynamic contact preference validation",
                        "A contact channel becomes required after the synthetic opt-in action.",
                        UiExplorerClaimConfidence.CONFIRMED,
                        List.of("The CRM contact is marked as opted in."),
                        List.of("Review both visibility and validation when adding another contact channel."),
                        List.of(source)
                )),
                List.of("The form depends on the selected synthetic CRM contact."),
                List.of(),
                List.of("The runtime form definition was not available."),
                List.of("Which CRM channel definitions are returned by the backend?")
        );
        var disabled = new UiExplorerResultSection(
                UiExplorerSectionId.STATE_AND_SYNCHRONIZATION,
                UiExplorerSectionMode.OFF,
                UiExplorerCoverageStatus.BLOCKED,
                "Disabled section.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        return new UiExplorerResultResponse(
                new UiExplorerScreenIdentity(
                        "crm-agent-portal",
                        "crm-contact-preferences",
                        "CRM Contact Preferences",
                        "/crm/contacts/:contactId/preferences",
                        "CRM contact details"
                ),
                "Document a strongly anonymized CRM contact preference change.",
                UiExplorerProfile.CHANGE_PREPARATION,
                new UiExplorerSourceRevision("main", "abc123"),
                "The screen maintains synthetic CRM contact preferences.",
                List.of(overview, forms, disabled),
                List.of(new UiExplorerCrossSectionDependency(
                        UiExplorerSectionId.ACTIONS_AND_OUTCOMES,
                        UiExplorerSectionId.FORMS_AND_RULES,
                        "Submit is enabled only when the synthetic CRM form is valid."
                )),
                new UiExplorerChangePreparationSummary(
                        "Add another synthetic CRM contact channel.",
                        List.of("Form visibility and validation"),
                        List.of("Confirm the runtime CRM channel definition")
                ),
                UiExplorerClaimConfidence.INFERRED,
                List.of("Backend validation rules were not inspected."),
                List.of("Does the backend enforce the same synthetic CRM rule?"),
                null
        );
    }
}
