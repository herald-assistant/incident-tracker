# AGENTS

## Zakres

Ten katalog odpowiada za MCP tools i ich rejestracje po stronie Spring AI.

Obejmuje:

- `elasticsearch/`
  tools do dodatkowego dogrywania logow po `correlationId`,
- `gitlab/`
  tools do wyszukiwania repozytoriow i czytania plikow,
- przyszle wysokopoziomowe tools analityczne, jesli beda potrzebne.

Nie obejmuje:

- implementacji adapterow REST z `../adapter`,
- providerow evidence z `../evidence`,
- budowy promptu i skilli z `../ai`.

## Zasady modyfikacji

- Tool ma delegowac do najwyzszego sensownego use case'u: portu, serwisu albo
  orchestratora. Nie sklejaj calego flow bezposrednio w metodzie `@Tool`.
- Kontrakty tooli maja pozostac jawne i male: tylko dane potrzebne do
  wykonania jednego konkretnego kroku eksploracji.
- Nie przenos tu heurystyk incidentowych typu logs -> deployment albo
  logs -> project hints. To nalezy do evidence pipeline.
- Nie odpalaj bezposrednio `RestClient` z warstwy MCP. Reuse'uj adaptery albo
  use case'i z innych pakietow.
- Logowanie tooli ma byc operacyjne: czytelne wejscie, skrot wyniku, bez
  dumpowania duzych payloadow.
- Jesli dodasz wysokopoziomowy tool, np. cala analize po `correlationId`,
  odseparuj go tak, aby nie wprowadzic rekurencyjnego self-invocation podczas
  sesji AI uruchamianej przez `AnalysisOrchestrator`.
- Rejestracja tooli powinna pozostac jawna i testowalna w kontekscie Spring AI.

## Testy

- Dla kazdego toola miej test klasy oraz test rejestracji w kontekscie.
- Gdy zmieniasz kontrakt DTO tooli, sprawdz tez bridge Copilota w `../ai`.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/16-copilot-sdk-gitlab-tools.md`
- `docs/17-copilot-incident-analysis-skill.md`
