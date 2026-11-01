# Release summary — jeden nagłówek i treść raportu

Status: in-progress

Source need: [Release summary](../needs/release-summary.md)

## Potrzeba / dlaczego

Użytkownik zlecił usunięcie zakładek wyniku, całego widoku kompletności i źródeł
oraz elementów nad wspólnym nagłówkiem wyniku. Nagłówek ma podawać porównywane
refy. Polecenie zatwierdza poniższy zakres; wcześniejsze polecenie obejmuje
weryfikację i commit każdego kroku.

## Baseline i conformance delta

L2, wyłącznie frontend: wspólny renderer otrzyma opcję osadzenia samej treści
w istniejącej karcie wyniku. Domyślny widok Config Drift Viewer zachowuje swój
nagłówek. Baseline panelu i Release summary: 15 testów zaliczonych,
`tdw-data/logs/release-summary-single-report-baseline.log`.

Wynik Release summary obecnie dubluje nagłówki i wymaga zakładki do pokazania
treści. Usuwamy kod tej zakładki, jej stan, status/direction przed nagłówkiem
i nieużywane style. Tytuł wynika z requestu zapisanego joba, także przy imporcie
i odczycie historii; nie z formularza kolejnego porównania. Share ma ten sam
tytuł. Zachowujemy edytor, meta/appendix, błędy i informacje o imporcie poniżej
nagłówka oraz wspólny aside. Bez zmiany API, JSON V1, evidence, walidacji
raportu i mechanizmu wyliczania jego statusu.

## Zakres, reuse i ryzyka

Reuse AnalysisResultHeader, AnalysisReportPanel, meta, editor i Share.
Alternatywa duplikowania sekcji raportu w feature zwiększa drift; ukrywanie
nagłówka przez CSS pozostawiałoby zbędną interakcję details.
Konsumenci: Release summary (initial/historia/import/chat/edycja/Share),
Config Drift Viewer i testy wspólnego renderera. Ryzyko: zmiana nagłówka po
zmianie formularza albo utrata treści przy osadzeniu. Testy obejmują te granice,
read-only import, blocked/NO_CHANGES i domyślny wspólny panel. Działające API
i zapis pozostają w tym samym kontrakcie; nie ma artefaktów do migracji.

## Kroki i kryteria akceptacji

- [x] Usunąć zakładki i nadmiarowy nagłówek, osadzić treść pod jednym nagłówkiem
  z refami i ujednolicić Share. Zaktualizować testy zachowania, wykonać pełne
  testy frontendu i build oraz sprawdzić rzeczywisty widok w przeglądarce.
  Commit implementacji z wygenerowanym bundlem. 700 testów frontendu i build
  produkcyjny zaliczone; logi `release-summary-single-report-tests.log` oraz
  `release-summary-single-report-build.log` w `tdw-data/logs`. Przeglądarka
  potwierdza jeden nagłówek z refami i treść bez zakładek, statusu i kierunku
  przed nagłówkiem. Zmiana nie dotyka API ani routingu zasobów backendu.
- [ ] Zaktualizować potrzebę, runtime i lokalne instrukcje, sprawdzić linki
  oraz diff; zamknąć i usunąć plan, zapisać commit dokumentacji.
