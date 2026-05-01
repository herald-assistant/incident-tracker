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

Historyczny katalog po ekstrakcji adapterow. Nowe capability nie powinny tu
trafiac.

### `integrations`

Docelowa reusable warstwa capability adapters. Przeniesione pakiety to
`integrations/dynatrace`, `integrations/elasticsearch`, `integrations/gitlab`,
`integrations/operationalcontext` i `integrations/database`.

### `analysis/mcp`

Historyczny katalog po ekstrakcji tools wystawianych przez Spring AI.
Nie ma tu juz aktywnych wrapperow MCP.

### `agenttools`

Reusable tools/capability uzywane przez MCP wrappers i Copilot runtime: hidden
tool context keys, nazwy tools/prefixy capability oraz wrappery MCP.
Wrappery mieszkaja w `agenttools.elasticsearch.mcp`, `agenttools.gitlab.mcp`
i `agenttools.database.mcp`.

### `analysis/ai`

Generyczne kontrakty AI i implementacja oparta o Copilot SDK.

Root `analysis/ai` nie powinien trzymac klas kontraktu bezposrednio. Klasy sa
pogrupowane wedlug funkcji:

- `initial` - poczatkowa analiza joba: `InitialAnalysisProvider`,
  `InitialAnalysisRequest`, `InitialAnalysisPreparation`,
  `InitialAnalysisResponse`,
- `chat` - follow-up chat po zakonczonym jobie,
- `evidence` - AI-side tool evidence listener; generyczne DTO
  `AnalysisEvidenceSection`, item i attribute mieszkaja w `shared.evidence`,
- `usage` - generyczny usage/token/cost contract dla job UI.

Najwazniejsze podpakiety Copilota:

- `copilot` - root aktualnego providera: `CopilotInitialAnalysisProvider`,
  `CopilotSdkAnalysisChatProvider` i `CopilotSdkModelOptionsProvider`,
- `copilot/preparation` - przygotowanie promptu, artefaktow, policy tools,
  skilli i initial/follow-up run assembly,
- `copilot/runtime` - techniczna konfiguracja SDK: properties, model listing,
  `CopilotClientOptions`, `SessionConfig`, `MessageOptions` i prepared session,
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
- `src/main/java/pl/mkn/incidenttracker/integrations`
- `src/main/java/pl/mkn/incidenttracker/agenttools`
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
