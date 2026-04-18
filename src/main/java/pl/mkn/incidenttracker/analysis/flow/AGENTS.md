# AGENTS

## Zakres

Ten katalog odpowiada za glowna orkiestracje runtime analizy, kontrakt flow i
obiekty przechodzace miedzy sync/job a evidence/AI.

Obejmuje:

- `AnalysisOrchestrator`,
- `AnalysisExecution` i listenery progresu,
- request, response i wyjatki domenowe glownego flow.

Nie obejmuje:

- kontrolerow HTTP z `../sync` i `../job`,
- integracji z systemami zewnetrznymi z `../adapter`,
- implementacji providerow evidence i AI.

## Zasady modyfikacji

- `AnalysisOrchestrator` jest source of truth dla calego runtime flow:
  evidence collection -> AI request -> prompt -> AI response -> final result.
- Sync i job maja reuse'owac to samo flow. Nie duplikuj tu alternatywnej
  orkiestracji dla roznych endpointow.
- `AnalysisRequest` przyjmuje tylko `correlationId`. Nie przywracaj `branch`,
  `environment` ani `gitLabGroup` do requestu glownego `/analysis`.
- `AnalysisResultResponse` ma zwracac fakty rozwiazane z evidence, glownie
  `environment` i `gitLabBranch`, a nie dane dostarczane przez klienta.
- Collector jest wlascicielem lifecycle krokow evidence. Flow powinno tylko
  adaptowac albo przekazywac te zdarzenia dalej do wyzszych warstw.
- Orchestrator nie powinien znac szczegolow adapterow. Pracuje na `AnalysisContext`,
  descriptorach providerow i kontrakcie `AnalysisAiProvider`.
- Wyjatki domenowe zwiazane z brakiem danych albo przebiegiem analizy trzymaj
  tutaj, a nie w kontrolerach.

## Testy

- Zmiany w orkiestracji powinny miec testy jednostkowe albo integracyjne,
  ktore potwierdzaja kolejnosc flow i mapowanie wyniku.
- Gdy zmienia sie kontrakt request/response, sprawdz downstream w `../sync`,
  `../job` i `src/main/java/pl/mkn/incidenttracker/api`.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
