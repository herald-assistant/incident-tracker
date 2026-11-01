# Release summary — historia i podgląd przygotowania

Status: in-progress

Source need: [Release summary](../needs/release-summary.md)

## Potrzeba / dlaczego

Operator chce wrócić z historii do trwającej analizy oraz obejrzeć zebrane
zmiany Liquibase per plik i pełny początkowy prompt przed wywołaniem AI.
Zakres zatwierdzony bezpośrednim poleceniem użytkownika z 2026-11-01.

## Baseline i conformance delta

Poziom L2: feature job/evidence oraz neutralny panel kroków i diff tekstowy.
Start już zapisuje QUEUED przed executorem; historia odczytuje wspólny indeks,
a ekran Release summary wznawia polling przez localRunId. Początkowy snapshot
nie ma kroków, nazwa wpisu jest ogólna, kroki nie wskazują evidence.
Evidence UI pokazuje tylko liczniki jednostek, mimo dostępnych pełnych źródeł.
Prompt jest zapisany przed AI, ale panel wybiera go przez listę kodów innych
feature'ów. Testy baseline FE: 68/68; baseline BE zatrzymany ograniczeniem
środowiska, powtórzony poprawnie razem z weryfikacją pierwszego kroku.

Zachowujemy endpointy, request, raport, V1, źródła i manifest, pełne encje
Operational Context, prompt i skille, GOAL_DRIVEN, scope, checkpoint i chat.
Nie dodajemy innego magazynu historii ani osobnego promptu do wyświetlania.
Nie zmieniamy formatów zapisu; nie dodajemy migracji, aliasów ani fallbacków.
Zastane lokalne zmiany formatowania w controller/catalog/job pozostają poza
zakresem commitów tej pracy.

## Rozwiązanie i konsumenci

- QUEUED z czytelną nazwą porównania i planowanymi krokami przed dispatch.
  Dowód przez rzeczywisty zapis historii i odczyt przed wykonaniem zadania.
- COLLECT_LIQUIBASE publikuje evidence pogrupowane repozytorium + ścieżka,
  pełną treść obu stron, refy/SHA, rodzaj zmiany i luki; jednostki parsera
  nie powielają kart plików. Brak strony odróżniamy od pustego pliku i błędu.
- PREPARE_PROMPT kończy się i zapisuje pełny prompt przed AI_REPORT oraz
  wywołaniem modelu. Błąd AI nie usuwa ukończonych kroków ani ich materiałów.
- Panel kroków dostaje jawny kod kroku promptu od konsumenta; usuwamy
  hardcoded wybór kodów promptu i opisy feature'ów ze wspólnego komponentu.
  Konsumenci: Incident, Flow/UI Explorer, Change Verification, Config Drift,
  oba Delivery assessmenty, Operational Context assistance, Release summary.
- Reuse diffu liniowego z follow-up po przeniesieniu do core/utils; shared
  panel pokazuje dodania/usunięcia z pełnego beforeContent/afterContent.
  Chat zachowuje dotychczasową prezentację. Bez nowej biblioteki diff.

Non-goals: tryb API, wykonanie migracji, zmiana raportu/promptu i sesji AI,
przebudowa historii. Ryzyka: rozmiar źródeł, błędy zapisu, kolejność publikacji,
regresja promptów pozostałych ekranów. Pełne dane nie są przycinane.

## Kroki i weryfikacja

- [x] Historia od startu: kolejka z planem kroków i rozpoznawalną nazwą;
  test trwałego indeksu przed dispatch, awarii dispatch/zapisu, odczytu
  aktywnego runu z historii i pollingu UI. Testy FE 39/39; celowane testy
  ReleaseSummary* i LiquibaseEvidenceCollector zakończone poprawnie
  (pilot live pominięty). Logi: tdw-data/logs/release-summary-progress-step1-*.
- [ ] Podgląd plików i promptu: evidence per plik, osobny ukończony krok
  promptu przed AI, neutralny renderer i aktualizacja jego konsumentów.
  Testy zmian/dodania/usunięcia/zależności/wielu repo, błędów AI, dokładnej
  zgodności promptu i widoku; regresja shared panelu i chatu. Commit.
- [ ] Weryfikacja całości: pełne testy Angulara, build UI, Maven backend-dev
  clean package, przegląd manualny historii/przebiegu, aktualizacja
  kanonicznej dokumentacji i uruchomienie aktualnej aplikacji. Commit.
