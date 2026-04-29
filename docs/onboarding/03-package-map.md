# Krok 3: Mapa Pakietow

## Cel

Nauczyc sie czytac repo po granicach odpowiedzialnosci, a nie po nazwach klas.

## Po tym kroku rozumiesz

- za co odpowiada kazdy glowny podkatalog `analysis/*`,
- gdzie szukac zmian przy konkretnym typie feature'a,
- jak rozpoznac, ze klasa wyladowala za wysoko albo za nisko.

## Glowna mapa

### `analysis/flow`

Orkiestracja runtime analizy, request/response i listenery progresu.

### `analysis/sync`

Synchroniczny endpoint `POST /analysis`.

### `analysis/job`

Asynchroniczny flow z jobami, follow-up chatem po zakonczonej analizie i
projekcja stanu dla UI.

### `analysis/evidence`

`AnalysisContext`, collector, kontrakt providera i jawne metadata krokow.

### `analysis/evidence/provider`

Konkretne kroki pipeline:

- Elasticsearch,
- deployment context,
- Dynatrace,
- GitLab deterministic,
- operational context.

### `analysis/adapter`

Integracje zewnetrzne, reuse'owalne capability adapters i helper endpointy
testowe.
Sa tu dzisiaj adaptery Elasticsearch, Dynatrace, GitLaba, Database capability
i operational context.

### `analysis/mcp`

Warstwa tools wystawianych przez Spring AI.
Sa tu dzisiaj tools Elastica, GitLaba i warunkowo Database.

### `analysis/ai`

Generyczne kontrakty finalnej analizy, follow-up chatu, katalogu modeli i
implementacja oparta o Copilot SDK.

Najwazniejsze podpakiety Copilota:

- `copilot/preparation` - przygotowanie promptu, artefaktow, policy tools,
  skilli, client options i `SessionConfig`,
- `copilot/execution` - lifecycle klienta/sesji SDK i usage events SDK,
- `copilot/coverage` - coverage report i evidence gaps sterujace tool policy,
- `copilot/response` - JSON-only parser odpowiedzi finalnej analizy,
- `copilot/quality` - report-only quality gate,
- `copilot/telemetry` - metryki preparation/execution/tools i summary log,
- `copilot/tools` - root z klasami wejsciowymi runtime tools:
  `CopilotSdkToolFactory`, `CopilotToolInvocationHandler`,
  `CopilotToolEvidenceSessionStore` i helper JSON,
- `copilot/tools/context` - hidden `ToolContext` i session-bound scope,
- `copilot/tools/description` - Copilot-facing guidance doklejane do opisow
  Spring tools,
- `copilot/tools/events` - eventy `Started`/`Finished` dla invocation,
- `copilot/tools/policy` - generyczne policies invocation, session validation
  i budget,
- `copilot/tools/logging` - listener logujacy invocation,
- `copilot/tools/gitlab` i `copilot/tools/database` - listener + mapper
  user-facing tool evidence dla konkretnych capability.

### `api`

Wspolny kontrakt bledow HTTP i walidacji dla endpointow backendu.

### `ui`

Cienki routing Spring MVC dla frontendowych route'ow Angulara.

### `frontend/`

Zrodlowa aplikacja Angular operatora: widok analizy, widok `/evidence`,
komponenty, modele i testy UI.

### `src/main/resources/copilot/skills`

Runtime skille ladowane do sesji Copilota.

### `src/main/resources/operational-context`

Katalog systemow, procesow, repozytoriow i regul routingu uzywany przez
operational context enrichment.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/flow`
- `src/main/java/pl/mkn/incidenttracker/analysis/sync`
- `src/main/java/pl/mkn/incidenttracker/analysis/job`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`
- `src/main/java/pl/mkn/incidenttracker/api`
- `src/main/java/pl/mkn/incidenttracker/ui`
- `frontend/src/app`
- `src/main/resources/copilot/skills`
- `src/main/resources/operational-context`

## Checkpoint

- Gdzie powinien trafic nowy krok evidence?
- Gdzie powinien trafic nowy helper endpoint do recznego testowania integracji?
- Gdzie powinien trafic nowy tool dla modelu?
- Gdzie zmienisz stale zasady pracy modelu i gdzie runtime katalog routingu?
