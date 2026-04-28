# AGENTS

## Zakres

Ten katalog odpowiada za asynchroniczny feature analizy i projekcje postepu dla
UI operatorskiego.

Obejmuje:

- `POST /analysis/jobs` i `GET /analysis/jobs/{analysisId}`,
- `POST /analysis/jobs/{analysisId}/chat/messages`,
- `AnalysisJobService` i uruchamianie analizy w tle,
- `AnalysisJobState`, statusy, kroki, `chatMessages` i snapshot odpowiedzi dla
  frontendu.

Nie obejmuje:

- glownej orkiestracji analizy z `../flow`,
- adapterow i providerow evidence,
- promptu i wykonania AI.

## Zasady modyfikacji

- Job flow ma reuse'owac `AnalysisOrchestrator`. Nie skladaj analizy osobno w
  `AnalysisJobService`.
- `AnalysisJobState` jest projekcja stanu dla UI, a nie drugim orchestratem.
  Nie dodawaj tu heurystyk incidentowych ani logiki adapterow.
- Lista krokow evidence ma wynikac z descriptorow providerow. Lokalnie w job
  mozna dodawac tylko krok AI i metadata potrzebne frontendowi.
- Snapshot joba powinien pozostac uzyteczny operacyjnie: status, current step,
  evidence sections, tool evidence, prepared prompt, environment, branch,
  wynik, chat messages i blad.
- Follow-up chat jest dostepny tylko po `COMPLETED`. Nie dodawaj do jego
  requestu recznego `environment`, branch, GitLab group, DB scope'u ani nowego
  `correlationId`; reuse'uj zakonczony request AI i hidden tool context.
- Tool evidence z follow-up chatu zapisuj przy konkretnej odpowiedzi assistant,
  nie w deterministycznych `evidenceSections`.
- Obsluga bledow ma pozostac czytelna dla UI i mapowac znane przypadki, np.
  brak danych vs. ogolna awaria analizy.
- Dopoki nie ma osobnej decyzji architektonicznej, stan jobow pozostaje prosty
  i lokalny dla procesu aplikacji. Nie wprowadzaj persistence "przy okazji".

## Testy

- Dla kontrolera preferuj `MockMvc`.
- Dla serwisu sprawdzaj kolejnosc progresu, statusy terminalne, chat
  `IN_PROGRESS/COMPLETED/FAILED` i mapowanie bledow.
- Gdy zmieniasz ksztalt snapshotu, sprawdz tez frontend i kontrakty UI.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
