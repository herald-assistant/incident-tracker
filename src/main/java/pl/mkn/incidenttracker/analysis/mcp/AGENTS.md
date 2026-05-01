# AGENTS

## Zakres

Ten katalog jest historycznym miejscem po ekstrakcji MCP tools do
`agenttools.<capability>.mcp`.

Obejmuje:

- lokalne guardrails dla agentow, dopoki katalog historyczny istnieje.

Nie obejmuje:

- nowych implementacji MCP tools,
- implementacji adapterow REST z `../adapter`,
- providerow evidence z `../evidence`,
- budowy promptu i skilli z `../ai`.

Elasticsearch, GitLab i Database MCP sa juz przeniesione do
`pl.mkn.incidenttracker.agenttools.<capability>.mcp`. Nie przywracaj wrapperow
do `analysis.mcp`.

## Zasady modyfikacji

- Tool ma delegowac do najwyzszego sensownego use case'u: portu, serwisu albo
  orchestratora. Nie sklejaj calego flow bezposrednio w metodzie `@Tool`.
- Kontrakty tooli maja pozostac jawne i male: tylko dane potrzebne do
  wykonania jednego konkretnego kroku eksploracji.
- Neutralne kontrakty wielokrotnego uzycia, np. hidden tool context keys i
  nazwy tools, oraz przenoszone wrappery MCP trzymaj w
  `pl.mkn.incidenttracker.agenttools`. DB request/result/scope/operator DTO sa
  capability contract adaptera DB w `integrations.database`, a MCP mapuje
  hidden `ToolContext` na adapterowy scope.
- Jesli kontekst runtime jest juz znany po stronie backendu, np. `environment`,
  `gitLabGroup`, `gitLabBranch` albo `correlationId`, przekazuj go przez hidden
  `ToolContext`, a nie przez model-facing parametry.
- Uwaga na stan zastany: `agenttools.elasticsearch.mcp.ElasticMcpTools` nadal
  ma jawny parametr `correlationId`. Nie powielaj tego wzorca; przy zmianach
  kontraktu Elastic MCP migruj go do hidden `ToolContext` i zaktualizuj testy
  schema/factory.
- Dla GitLab i Database tools jedyny operator-facing powod wywolania to
  opcjonalny `reason`. Nie dodawaj model-facing parametrow eksploracyjnych,
  pytan diagnostycznych ani innych pol, ktore probuja zastapic prosty powod.
- Nie przenos tu heurystyk incidentowych typu logs -> deployment albo
  logs -> project hints. To nalezy do evidence pipeline.
- Nie odpalaj bezposrednio `RestClient` z warstwy MCP. Reuse'uj adaptery albo
  use case'i z innych pakietow.
- Logowanie tooli ma byc operacyjne: czytelne wejscie, skrot wyniku, bez
  dumpowania duzych payloadow.
- Jesli dodasz wysokopoziomowy tool, np. cala analize po `correlationId`,
  odseparuj go tak, aby nie wprowadzic rekurencyjnego self-invocation podczas
  sesji AI uruchamianej przez `AnalysisOrchestrator`.
- Rejestracja tooli w `agenttools.<capability>.mcp` powinna pozostac jawna i
  testowalna w kontekscie Spring AI.

## Testy

- Dla kazdego toola miej test klasy oraz test rejestracji w kontekscie.
- Gdy zmieniasz kontrakt DTO tooli, sprawdz tez tool factory Copilota w `../ai`.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/onboarding/09-spring-ai-and-mcp-tools.md`
- `docs/onboarding/10-copilot-sdk-analysis-runtime.md`
