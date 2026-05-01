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

### `features/incidentanalysis/flow`

Orkiestracja runtime analizy incydentu, request/response i listenery progresu.

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
wewnetrzna czescia providera AI. Implementacja provider'a w tym pakiecie jest
fasada nad platformowym katalogiem modeli Copilota.

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

### `features/incidentanalysis/ai`

Incidentowe kontrakty AI i implementacja Copilota dla obecnego flow:

- `initial` - poczatkowa analiza incydentu: `InitialAnalysisProvider`,
  `InitialAnalysisRequest`, `InitialAnalysisPreparation`,
  `InitialAnalysisResponse`,
- `chat` - follow-up chat po zakonczonej analizie,
- `copilot` - incidentowe providery, prompt/artifacts, coverage, response
  parser, quality gate, tool policy i tool evidence capture.

Neutralne kontrakty wspolne dla AI, joba i feature'ow sa poza feature'em:

- `shared.evidence` - `AnalysisEvidenceSection`, item/attribute oraz
  `AnalysisAiToolEvidenceListener`,
- `shared.ai` - generyczny usage/token/cost contract dla job UI i telemetryki.

Nie dodawaj nowych elementow runtime Copilota do feature'a; platformowe
mechanizmy SDK mieszkaja w `aiplatform.copilot`.

### `aiplatform`

Neutralna platforma uruchamiania AI. Pierwsze wydzielone slice'y:

- `aiplatform.copilot.runtime` - properties, model listing, skill runtime
  loader, `CopilotRunRequest`, `CopilotPreparedSession`,
  `CopilotSessionConfigRequest`, rendered artifacts oraz factory konfiguracji
  sesji SDK,
- `aiplatform.copilot.runtime.options` - platformowy katalog modeli Copilota
  oraz neutralne DTO opcji modeli mapowane przez fasade `analysis/options`,
- `aiplatform.copilot.runtime.execution` - lifecycle klienta/sesji SDK,
  execution gateway i neutralny port metryk execution,
- `aiplatform.copilot.runtime.telemetry` - neutralny port telemetry sesji:
  preparation metrics, response state, quality report i usage snapshot,
- `aiplatform.copilot.runtime.telemetry/session` - platformowa implementacja
  metryk preparation/execution/tools, summary log i budget telemetry,
- `aiplatform.copilot.runtime.quality` - neutralny payload raportu jakosci
  odpowiedzi uzywany przez telemetryke,
- `aiplatform.copilot.tools/context` - hidden `ToolContext` i session-bound
  scope jako neutralna mechanika platformy,
- `aiplatform.copilot.tools/CopilotSdkToolFactory` - rejestracja Spring tools
  jako Copilot `ToolDefinition`,
- `aiplatform.copilot.tools/CopilotToolInvocationHandler` - neutralna granica
  wykonania callbacka toola,
- `aiplatform.copilot.tools/events` - eventy `Started`/`Finished` dla
  invocation,
- `aiplatform.copilot.tools/policy` - neutralne policy contracts, kontrolowany
  rejection i session validation,
- `aiplatform.copilot.tools/policy/budget` - neutralna decyzja budzetu i
  kontrakt listenera telemetryki budzetu,
- `aiplatform.copilot.tools/logging` - listener logujacy invocation,
- `aiplatform.copilot.tools/telemetry` - neutralna klasyfikacja tool invocation,
- `aiplatform.copilot.tools/description` - neutralny kontrakt customizacji
  opisow tools,
- `aiplatform.copilot.tools/evidence` - session-bound store publikujacy
  neutralne evidence z wynikow tool invocation.

Ten pakiet nie zna incident promptu, coverage ani flow jobow.

### `features/incidentanalysis`

Dedykowany feature analizy incydentow. `features.incidentanalysis.ai.initial`
i `features.incidentanalysis.ai.chat` zawieraja kontrakty AI obecnego
incident flow. `features.incidentanalysis.ai.copilot` zawiera incident
initial/chat providery, `preparation` dla promptu, artefaktow, tool policy,
hidden contextu i initial/follow-up run assembly, `response` dla JSON-only
parsera odpowiedzi, `quality` dla report-only quality gate oraz `coverage` dla
incident-specific coverage report i evidence gaps. Podpakiet `tools` zawiera
GitLab/DB listener + mapper user-facing tool evidence dla analizy incydentow
oraz `tools.description` z incident-specific guidance opisow tools.

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

- `src/main/java/pl/mkn/incidenttracker/analysis/job`
- `src/main/java/pl/mkn/incidenttracker/analysis/options`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/flow`
- `src/main/java/pl/mkn/incidenttracker/integrations`
- `src/main/java/pl/mkn/incidenttracker/agenttools`
- `src/main/java/pl/mkn/incidenttracker/aiplatform`
- `src/main/java/pl/mkn/incidenttracker/features`
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
