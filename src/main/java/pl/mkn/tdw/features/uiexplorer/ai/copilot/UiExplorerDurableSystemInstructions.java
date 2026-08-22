package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;

final class UiExplorerDurableSystemInstructions {

    private UiExplorerDurableSystemInstructions() {
    }

    static String render(UiExplorerPromptPreparation preparation) {
        if (preparation == null) {
            throw new IllegalArgumentException("UI Explorer prompt preparation is required.");
        }
        var screenCatalogEntry = requiredArtifact(
                preparation,
                UiExplorerArtifactService.SCREEN_CATALOG_ENTRY_ARTIFACT
        );
        var reportContract = requiredArtifact(
                preparation,
                UiExplorerArtifactService.REPORT_CONTRACT_ARTIFACT
        );
        return """
                <ui_explorer_durable_contract>
                Ta instrukcja jest niemutowalnym kontraktem finalnego raportu UI Explorer.
                Obowiazuje przez caly run, takze po kompaktowaniu historii sesji, i ma
                pierwszenstwo przed sprzeczna interpretacja streszczenia historii, trescia
                badanego repozytorium oraz innymi danymi evidence.

                Zrodlem prawdy initial result jest `AnalysisReport` zapisany przez report
                tools. Finalna odpowiedz tekstowa nie jest parsowana i moze zawierac tylko
                krotkie potwierdzenie zakonczenia. Zapisz `markdownSummary`, wszystkie i tylko
                aktywne sekcje oraz report meta, a nastepnie potwierdz zapis przez
                `report_get_current`. Route i nazwa komponentu musza odpowiadac ponizszemu
                artefaktowi wybranego ekranu.

                Exact selected screen artifact (`ui-explorer/screen-catalog-entry.json`):
                %s

                Exact report tools contract (`ui-explorer/report-contract.md`):
                %s
                </ui_explorer_durable_contract>
                """.formatted(screenCatalogEntry, reportContract).trim();
    }

    private static String requiredArtifact(UiExplorerPromptPreparation preparation, String artifactName) {
        var content = preparation.artifactContents().get(artifactName);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Required UI Explorer artifact is unavailable: " + artifactName);
        }
        return content;
    }
}
