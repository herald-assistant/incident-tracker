# Spring AI And MCP Tools

Ten onboarding opisuje, jak aplikacja rejestruje tools uzywane przez Copilota
i jak backend pilnuje scope, budgetu oraz audytu.

## Warstwy

Tools sa implementowane w pakiecie `analysis.mcp` i deleguja do adapterow albo
use case'ow aplikacji. Copilot nie wywoluje adapterow bezposrednio.

Najwazniejsze grupy:

- `elastic_*` - dodatkowe log evidence po `correlationId`,
- `gitlab_*` - search, outline, flow context, class references i focused file
  reads,
- `db_*` - read-only database diagnostics w resolved environment.

## Hidden ToolContext

Zakres narzedzi jest session-bound. `ToolContext` niesie ukryte dane:

- `correlationId` dla Elasticsearch,
- `gitLabGroup` i `gitLabBranch` dla GitLaba,
- `environment` i database scope dla DB.

Model nie powinien podawac tych wartosci jako argumentow. Publiczny request
analizy nadal ma tylko `correlationId`.

## Coverage-aware allowlista

`CopilotToolAccessPolicy` nie wlacza tools tylko dlatego, ze sa
zarejestrowane. Najpierw `CopilotEvidenceCoverageEvaluator` ocenia generyczne
evidence i tworzy `CopilotEvidenceCoverageReport`.

W runtime policy jest tworzona przez `CopilotToolAccessPolicyFactory`.
Fabryka dostaje `AnalysisAiAnalysisRequest` oraz zarejestrowane
`ToolDefinition`, uruchamia evaluator coverage i przekazuje gotowy report do
`CopilotToolAccessPolicy.fromCoverage(...)`.

Reguly:

- Elastic tools sa aktywne przy brakujacych, obcietych albo niepelnych logach.
- GitLab tools sa aktywne przy brakujacym code/flow context.
- Gdy GitLab evidence zna projekt/plik, zostaje focused toolset zamiast
  szerokiego browse.
- DB tools wymagaja resolved environment i uzasadnionej potrzeby data
  diagnostics.
- Raw SQL jest domyslnie zablokowany.

Coverage i evidence gaps sa widoczne w `00-incident-manifest.json`.

## Opisy tools dla Copilota

`CopilotToolDescriptionDecorator` dokleja do Spring tool descriptions krotkie
guidance dla modelu. To nie zmienia implementacji tools.

Przyklady guidance:

- full file read jest expensive i powinien byc uzywany dopiero po outline/chunk,
- GitLab search powinien uzywac seedow ze stacktrace, exception, klasy,
  repozytorium albo service name,
- DB sample rows nie sluzy do przegladania danych biznesowych,
- raw SQL jest last resort i moze byc zablokowany przez policy/budget.

## Budget guard

`CopilotToolBudgetGuard` pilnuje limitow na sesje. Domyslnie dziala w trybie
`soft`, czyli ostrzega i metrykuje, ale nie blokuje. Tryb `hard` zwraca
kontrolowany wynik `denied_by_tool_budget`.

Limity obejmuja:

- total tool calls,
- Elastic/GitLab/DB calls,
- GitLab search calls,
- GitLab read file/chunk calls,
- returned characters,
- DB raw SQL calls.

## Bridge i invocation handler

`CopilotSdkToolBridge` jest waska warstwa adaptera Spring tools -> Copilot
SDK. Zbiera `ToolCallback`, deduplikuje po nazwie, sortuje, dekoruje opis,
parsuje input schema i tworzy `ToolDefinition`.

Wykonanie toola jest w `CopilotToolInvocationHandler`. Handler:

- sprawdza session id,
- wywoluje `CopilotToolBudgetGuard` przed i po callbacku,
- buduje hidden `ToolContext` przez `CopilotToolContextFactory`,
- wywoluje Spring `ToolCallback`,
- zapisuje metryki i loguje tool event,
- publikuje tool evidence,
- parsuje result do obiektu zwracanego SDK.

Blad callbacka pozostaje failed future dla SDK. Bridge nie ukrywa takiego
bledu jako pustego wyniku.

## Capture tool evidence

`CopilotToolEvidenceCaptureRegistry` przeksztalca wybrane wyniki tools w
`AnalysisEvidenceSection`, ktore job flow publikuje jako `toolEvidenceSections`.

Capture obejmuje:

- GitLab fetched code,
- GitLab search results,
- GitLab outline/flow/class reference context,
- DB diagnostic summaries.

Dzieki temu audyt sesji pokazuje nie tylko finalna odpowiedz AI, ale tez
material dociagniety przez tools.

Registry zarzadza lifecycle sesji i routingiem capture. Mapowanie szczegolow
GitLab i DB jest w oddzielnych mapperach, dzieki czemu registry nie zna
formatu kazdego toola.

## Zasady przy dodawaniu tools

- Tool powinien miec waski, typed contract.
- Scope incydentu ma pochodzic z `ToolContext`, nie od modelu.
- Dodaj test dla rejestracji i zachowania toola.
- Jesli tool jest drogi albo ryzykowny, dodaj guidance w
  `CopilotToolGuidanceCatalog`.
- Jesli wynik toola jest diagnostycznie wazny, dodaj capture do
  odpowiedniego mappera evidence capture.
- Nie eksportuj DTO adapterow jako publicznego kontraktu AI.
- Nie zmieniaj publicznego requestu analizy: `/analysis` i `/analysis/jobs`
  nadal przyjmuja tylko `correlationId`.

## Najwazniejsze properties

```properties
analysis.ai.copilot.tool-budget.enabled=true
analysis.ai.copilot.tool-budget.mode=soft
analysis.ai.copilot.tool-budget.max-total-calls=16
analysis.ai.copilot.tool-budget.max-elastic-calls=1
analysis.ai.copilot.tool-budget.max-gitlab-calls=8
analysis.ai.copilot.tool-budget.max-gitlab-search-calls=3
analysis.ai.copilot.tool-budget.max-gitlab-read-file-calls=1
analysis.ai.copilot.tool-budget.max-gitlab-read-chunk-calls=6
analysis.ai.copilot.tool-budget.max-gitlab-returned-characters=80000
analysis.ai.copilot.tool-budget.max-db-calls=8
analysis.ai.copilot.tool-budget.max-db-raw-sql-calls=0
analysis.ai.copilot.tool-budget.max-db-returned-characters=64000
```
