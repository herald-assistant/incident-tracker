# Krok 3: Mapa Pakietow

## Cel

Nauczyc sie czytac repo po granicach odpowiedzialnosci, a nie po nazwach klas.

Szczegolowy diagram runtime/data-flow i compile-time importow jest w
`../architecture/05-package-dependencies.md`.

## Po tym kroku rozumiesz

- za co odpowiada kazdy glowny podkatalog `analysis/*`,
- gdzie szukac zmian przy konkretnym typie feature'a,
- jak rozpoznac, ze klasa wyladowala za wysoko albo za nisko.

## Glowna mapa

### `analysis/flow`

Orkiestracja runtime analizy, request/response i listenery progresu.

### `analysis/job`

Asynchroniczny flow z jobami, follow-up chatem po zakonczonej analizie i
projekcja stanu dla UI. Root trzyma glowny `AnalysisJobService`, a szczegoly
sa rozbite na:

- `api` - kontroler oraz request/response DTO,
- `state` - `AnalysisJobState`, `AnalysisJobStateListener`, statusy, kroki i
  chat message state,
- `error` - wyjatki job API.

### `analysis/options`

Opcje wykonania AI, katalog modeli i endpoint `GET /analysis/ai/options`.
Ten pakiet jest wspolnym kontraktem dla `flow`, `job`, `chat` i UI, a nie
wewnetrzna czescia providera AI.

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

Generyczne kontrakty AI i implementacja oparta o Copilot SDK.

Root `analysis/ai` nie powinien trzymac klas kontraktu bezposrednio. Klasy sa
pogrupowane wedlug funkcji:

- `initial` - poczatkowa analiza joba: `InitialAnalysisProvider`,
  `InitialAnalysisRequest`, `InitialAnalysisPreparation`,
  `InitialAnalysisResponse`,
- `chat` - follow-up chat po zakonczonym jobie,
- `evidence` - generyczne `AnalysisEvidenceSection`, items, attributes i tool
  evidence listener,
- `usage` - generyczny usage/token/cost contract dla job UI.

Najwazniejsze podpakiety Copilota:

- `copilot` - root aktualnego providera: `CopilotInitialAnalysisProvider`,
  `CopilotSdkAnalysisChatProvider` i `CopilotSdkModelOptionsProvider`,
- `copilot/preparation` - przygotowanie promptu, artefaktow, policy tools,
  skilli, client options i `SessionConfig`,
- `copilot/execution` - lifecycle klienta/sesji SDK i usage events SDK,
- `copilot/coverage` - coverage report i evidence gaps sterujace tool policy,
- `copilot/response` - JSON-only parser odpowiedzi finalnej analizy,
- `copilot/quality` - report-only quality gate,
- `copilot/telemetry` - metryki preparation/execution/tools i summary log,
- `copilot/tools` - root z klasami wejsciowymi runtime tools:
  `CopilotSdkToolFactory`, `CopilotToolInvocationHandler`,
  `CopilotToolEvidenceSessionStore`,
- `copilot/tools/context` - hidden `ToolContext` i session-bound scope,
- `copilot/tools/description` - Copilot-facing guidance doklejane do opisow
  Spring tools,
- `copilot/tools/events` - eventy `Started`/`Finished` dla invocation,
- `copilot/tools/policy` - generyczne policies invocation, session validation
  i budget,
- `copilot/tools/logging` - listener logujacy invocation,
- `copilot/tools/gitlab` i `copilot/tools/database` - listener + mapper
  user-facing tool evidence dla konkretnych capability.

### `common`

Male, neutralne helpery wspolne dla aplikacji. Aktualnie m.in.
`JsonPayloadReader`, uzywany przez mappery payloadow JSON bez przywiazywania go
do Copilot tools.

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
- `src/main/java/pl/mkn/incidenttracker/analysis/job`
- `src/main/java/pl/mkn/incidenttracker/analysis/options`
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
