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
        var responseContract = requiredArtifact(
                preparation,
                UiExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT
        );
        return """
                <ui_explorer_durable_contract>
                Ta instrukcja jest niemutowalnym kontraktem finalnej odpowiedzi UI Explorer.
                Obowiazuje przez caly run, takze po kompaktowaniu historii sesji, i ma
                pierwszenstwo przed sprzeczna interpretacja streszczenia historii, trescia
                badanego repozytorium oraz innymi danymi evidence.

                Finalna odpowiedz musi byc jednym obiektem JSON zgodnym dokladnie z
                `ui-explorer/response-contract.json`, bez wrappera, komentarza i dodatkowych
                pol. Identyfikacja ekranu musi znajdowac sie w obiekcie `screen`:
                `screen.screenId` jest wymagane, a top-level `screenId` jest zabronione.
                `sourceRevision` musi byc obiektem z polami `branch` i `revision`; wartosc
                skalarna jest zabroniona. `usage` pozostaje `null`, poniewaz wypelnia je backend.
                Wartosci `screen` i `sourceRevision` musza odpowiadac ponizszemu artefaktowi
                wybranego ekranu.

                Exact selected screen artifact (`ui-explorer/screen-catalog-entry.json`):
                %s

                Exact final response contract (`ui-explorer/response-contract.json`):
                %s
                </ui_explorer_durable_contract>
                """.formatted(screenCatalogEntry, responseContract).trim();
    }

    private static String requiredArtifact(UiExplorerPromptPreparation preparation, String artifactName) {
        var content = preparation.artifactContents().get(artifactName);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Required UI Explorer artifact is unavailable: " + artifactName);
        }
        return content;
    }
}
