package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;

public final class UiExplorerDurableSystemInstructions {

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
                `report_get_current`. Ten tool zwraca zwarty manifest struktury, rozmiarow,
                metadata i digestow, a nie ponowna kopie pelnych body. Kontrole merytoryczna
                calej tresci wykonaj przed zapisem. Route i nazwa komponentu musza odpowiadac
                ponizszemu artefaktowi wybranego ekranu.

                Exact selected screen artifact (`ui-explorer/screen-catalog-entry.json`):
                %s

                Exact report tools contract (`ui-explorer/report-contract.md`):
                %s
                </ui_explorer_durable_contract>
                """.formatted(screenCatalogEntry, reportContract).trim();
    }

    public static String followUp() {
        return """
                <ui_explorer_follow_up_contract>
                To jest rozmowa po zapisaniu raportu UI Explorera. Odpowiadaj na konkretne pytanie
                analityka po polsku, w czytelnym Markdown. Wyjasniaj warunek, zachowanie systemu
                i skutek dla uzytkownika. Nazwy klas, metod i plikow sa tylko dowodem pomocniczym.
                Gdy analityk pyta o konkretny kontrakt API, schemat bazy danych albo nazwe
                systemu zewnetrznego, podaj potwierdzone identyfikatory i wyjasnij ich znaczenie.
                Nie dodawaj szczegolow implementacji, o ktore uzytkownik nie pytal.

                Raport zmieniaj tylko gdy najnowsza wiadomosc analityka jawnie prosi o aktualizacje
                dokumentu. Zwykle pytanie lub dodatkowy research nie upowaznia do zapisu.
                Przed edycja odczytaj potrzebna sekcje przez report_get_current(sectionId).
                Dla malej korekty uzyj report_patch_section z aktualnym digestem i dokladnym
                fragmentem, dla calej sekcji report_upsert_section, dla naglowka report_update_header,
                a dla globalnych metadata report_update_meta. Zachowaj reszte raportu i po zapisie
                sprawdz manifest przez report_get_current. Gdy zapis sie nie powiedzie, nie deklaruj
                zmiany dokumentu. Dla nowych lub kwestionowanych ustalen uzyj dostepnych scoped GitLab tools. Oddziel fakty,
                wnioski i ograniczenia widocznosci. Nie uruchamiaj ponownie initial workflow.
                </ui_explorer_follow_up_contract>
                """.trim();
    }

    private static String requiredArtifact(UiExplorerPromptPreparation preparation, String artifactName) {
        var content = preparation.artifactContents().get(artifactName);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Required UI Explorer artifact is unavailable: " + artifactName);
        }
        return content;
    }
}
