package pl.mkn.tdw.features.uiexplorer.ai;

import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

public final class UiExplorerAiRuntimeTestFixture {

    public static final String EMBEDDED_COMPONENT_PATH =
            "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts";
    public static final String FETCHED_VALIDATOR_PATH =
            "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.validator.ts";

    private UiExplorerAiRuntimeTestFixture() {
    }

    public static AnalysisReport completeReport(String reportId, String sourcePath) {
        var reference = new AnalysisReportReference(
                "source",
                "CrmContactPreferencesComponent",
                sourcePath + "#L1-L18",
                "Synthetic CRM UI source"
        );
        var meta = new AnalysisReportMeta(List.of(reference), List.of(), List.of(), List.of(), "high", List.of());
        return new AnalysisReport(
                reportId,
                "/contacts/:contactId/preferences",
                "CRM Contact Preferences",
                "Doradca CRM utrzymuje preferencje kontaktu, aby kolejne interakcje wykorzystywaly aktualny kanal komunikacji.",
                List.of(
                        new AnalysisReportSection(
                                "OVERVIEW",
                                "Cel i kontekst widoku",
                                0,
                                "**Cel biznesowy**\\n\\nUtrzymanie dozwolonego kanalu kontaktu.\\n\\n**Uzytkownicy i kontekst**\\n\\nDoradca pracuje na jednym kontakcie CRM.\\n\\n**Przebieg w skrocie**\\n\\n1. Doradca otwiera preferencje.\\n\\n**Rezultat**\\n\\nPreferencje dotycza wybranego kontaktu.",
                                meta
                        ),
                        new AnalysisReportSection(
                                "FORMS_AND_RULES",
                                "Formularze i reguly",
                                4,
                                "| Pole lub grupa | Znaczenie | Wymagalnosc i walidacja | Zachowanie dynamiczne | Wyliczenie lub zaleznosc |\\n| --- | --- | --- | --- | --- |\\n| Kod preferencji | Kanal kontaktu | Wymagany | Brak potwierdzonej dynamiki | Brak wyliczenia |\\n\\n**Reguly przekrojowe**\\n\\n- Zapis wymaga kodu.\\n\\n**Edycja reczna a ponowne wyliczenie**\\n\\n- Pole jest wybierane recznie.",
                                meta
                        )
                ),
                meta
        );
    }

    public static AnalysisEvidenceSection fetchedCodeEvidence(String path) {
        return new AnalysisEvidenceSection(
                "gitlab",
                "tool-fetched-code",
                List.of(new AnalysisEvidenceItem(
                        "Synthetic CRM validator",
                        List.of(new AnalysisEvidenceAttribute("filePath", path))
                ))
        );
    }
}
