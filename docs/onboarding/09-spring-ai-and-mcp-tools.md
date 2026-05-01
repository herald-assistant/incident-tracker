# Spring AI And MCP Tools

Ten onboarding opisuje, jak aplikacja rejestruje tools uzywane przez Copilota
i jak backend pilnuje scope, budgetu oraz audytu.

## Warstwy

Tools sa implementowane w pakiecie `analysis.mcp` i deleguja do adapterow albo
use case'ow aplikacji. Copilot nie wywoluje adapterow bezposrednio.
Neutralne nazwy tools i prefixy capability mieszkaja w `agenttools`, zeby MCP
wrappers, policy Copilota, telemetryka i dekoratory opisow nie importowaly ich
z warstwy implementacji `analysis.mcp`.

Najwazniejsze grupy:

- `elastic_*` - dodatkowe log evidence po `correlationId`,
- `gitlab_*` - search, outline, flow context, class references i focused file
  reads,
- `db_*` - read-only database diagnostics w resolved environment.

## Hidden ToolContext

Zakres narzedzi jest session-bound. Session context zna dane potrzebne
integracyjnym tools:

- `correlationId` dla Elasticsearch,
- `gitLabGroup` i `gitLabBranch` dla GitLaba,
- `environment` i database scope dla DB.

Model docelowo nie powinien podawac tych wartosci jako argumentow. Publiczny
start analizy przyjmuje `correlationId`, ale scope integracji nadal jest
rozwiazywany i ukrywany po stronie backendu.

Aktualny kod jest juz w pelni hidden-scope dla GitLab i Database tools.
`ElasticMcpTools.searchLogsByCorrelationId(...)` nadal ma jawny parametr
`correlationId`, mimo ze policy sesji ogranicza dostep do toola. Traktuj to
jako znany drift wzgledem docelowego invariantu MCP: przy najblizszej zmianie
Elastic tool powinien przejsc na `ToolContext`, a model-facing schema nie
powinna wymagac `correlationId`.

## Coverage-aware allowlista

`CopilotIncidentToolAccessPolicy` nie wlacza tools tylko dlatego, ze sa
zarejestrowane. Najpierw `CopilotIncidentEvidenceCoverageEvaluator` ocenia generyczne
evidence i tworzy `CopilotIncidentEvidenceCoverageReport`.

W runtime policy jest tworzona przez `CopilotIncidentToolAccessPolicyFactory`.
Fabryka dostaje `InitialAnalysisRequest` oraz zarejestrowane
`ToolDefinition`, uruchamia evaluator coverage i przekazuje gotowy report do
`CopilotIncidentToolAccessPolicy.fromCoverage(...)`.

Reguly:

- Elastic tools sa aktywne przy brakujacych, obcietych albo niepelnych logach.
- GitLab tools sa aktywne przy brakujacym code/flow context.
- Przy `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` focused GitLab tools sa aktywne,
  zeby Copilot zrobil male przeszukanie kodu i opisal `affectedFunction`
  jezykiem techniczno-funkcjonalnym, nie jako code walkthrough.
- Gdy GitLab evidence zna projekt/plik, zostaje focused toolset zamiast
  szerokiego browse.
- Przy `DB_CODE_GROUNDING_NEEDED` focused GitLab tools sa aktywne takze po to,
  zeby model sprobowal znalezc encje/repozytorium/tabele/relacje w kodzie
  przed przeszukiwaniem DB metadata.
- DB tools wymagaja resolved environment i uzasadnionej potrzeby data
  diagnostics.
- Raw SQL jest domyslnie zablokowany.

Coverage i evidence gaps sa widoczne w `00-incident-manifest.json`.

Follow-up chat po zakonczonym jobie nie uzywa coverage jako glownego powodu
wlaczenia tools. `CopilotIncidentToolAccessPolicyFactory.createForFollowUp(...)` wystawia
targeted tools na podstawie resolved scope'u z zakonczonej analizy:

- Elasticsearch dla aktualnego `correlationId`,
- GitLab tylko przy resolved `gitLabGroup` i `gitLabBranch`,
- Database tylko przy resolved `environment`,
- raw SQL nadal pozostaje zablokowany.

## Opisy tools dla Copilota

`CopilotToolDescriptionDecorator` w `tools.description` dokleja do Spring tool
descriptions krotkie guidance dla modelu. To nie zmienia implementacji tools.

Przyklady guidance:

- full file read jest expensive i powinien byc uzywany dopiero po outline/chunk,
- GitLab search/flow context powinien uzywac konkretnych keywordow ze
  stacktrace, exception, klasy, repozytorium albo service name,
- GitLab flow/search guidance wspiera `AFFECTED_FUNCTION_GITLAB_RECOMMENDED`,
  czyli focused lookup pod szczegolowy opis funkcji,
- GitLab i DB tools powinny przekazywac krotki powod po polsku w `reason`,
- DB tools przypominaja, ze DB discovery dla JPA/repository/data-access
  symptomow jest fallbackiem po deterministic GitLab evidence albo probie
  focused GitLab tool call,
- DB sample rows nie sluzy do przegladania danych biznesowych,
- raw SQL jest last resort i moze byc zablokowany przez policy/budget.

## Budget policy

`CopilotToolBudgetPolicy` w `tools.policy.budget` pilnuje limitow na sesje jako
`CopilotToolInvocationPolicy`. Domyslnie dziala w trybie `soft`, czyli
ostrzega i metrykuje, ale nie blokuje. Tryb `hard` rzuca kontrolowany
`CopilotToolInvocationRejectedException`; handler zamienia go na wynik
`denied_by_tool_budget` dla SDK.

Limity obejmuja:

- total tool calls,
- Elastic/GitLab/DB calls,
- GitLab search calls,
- GitLab read file/chunk calls,
- returned characters,
- DB raw SQL calls.

## Tool factory i invocation handler

`CopilotSdkToolFactory` jest waska warstwa adaptera Spring tools -> Copilot
SDK. Zbiera `ToolCallback`, deduplikuje po nazwie, sortuje, dekoruje opis,
parsuje input schema i tworzy `ToolDefinition`.

Factory nie jest miejscem na side-effecty invocation. Jej odpowiedzialnosc
konczy sie na definicji toola i podpietym handlerze wykonania.

Wykonanie toola jest w `CopilotToolInvocationHandler`. Handler:

- wywoluje generyczne `CopilotToolInvocationPolicy` przed i po callbacku,
- buduje hidden `ToolContext` przez `tools.context.CopilotToolContextFactory`,
- wywoluje Spring `ToolCallback`,
- publikuje `Started` oraz terminalny `Finished` z outcome
  `COMPLETED`, `REJECTED` albo `FAILED`,
- parsuje result do obiektu zwracanego SDK.

Walidacje session id robi `CopilotToolSessionValidationPolicy` w
`tools.policy.session`, przed publikacja eventu `Started`.

Logowanie, telemetryka i capture evidence sa listenerami tych eventow.
Dzieki temu definicja i invocation toola zostaja czyste, a GitLab/DB maja
wlasne pakiety odpowiedzialne za interpretacje wynikow.

`CopilotToolInvocationEventPublisher` izoluje bledy listenerow. Awaria
logowania, metryk albo capture evidence nie powinna zamieniac poprawnego
wyniku toola w blad SDK.

Blad callbacka pozostaje failed future dla SDK. Handler nie ukrywa takiego
bledu jako pustego wyniku.

## Capture tool evidence

`CopilotToolEvidenceSessionStore` zarzadza sesja capture i publikuje
zaktualizowane `AnalysisEvidenceSection`. Dla poczatkowej analizy job flow
publikuje je jako top-level `toolEvidenceSections`, a dla follow-up chatu
zapisuje przy konkretnej odpowiedzi assistant w
`chatMessages[].toolEvidenceSections`. Store dostaje neutralny session-bound
sink, a provider AI adaptuje `AnalysisAiToolEvidenceListener` przed wywolaniem
execution gatewaya.

Capture obejmuje:

- GitLab fetched code jako nazwa/sciezka pliku, tresc kodu i `reason`,
- GitLab discovery jako kandydaci, grupy flow/class references, outline i
  rekomendowane dalsze odczyty z `reason`,
- DB tool results jako prosty wynik i `reason`.

Dzieki temu audyt sesji pokazuje nie tylko finalna odpowiedz AI, ale tez
material dociagniety przez tools bez dodatkowego szumu technicznego. `reason`
pozostaje operatorskim naglowkiem wpisu, a szczegoly ida do tresci accordionu.

GitLab i DB subskrybuja terminalny event `Finished(COMPLETED)` w odpowiednich
pakietach: `tools.gitlab` oraz `tools.database`. Mapowanie szczegolow GitLab i
DB jest w oddzielnych mapperach, dzieki czemu handler nie zna formatu kazdego
toola.

Root `tools` powinien pozostac czytelny: klasy pomocnicze trzymaj w
`context`, `description`, `events`, `logging` albo `policy`, a logike
konkretnej capability w `tools.<capability>`.

## Zasady przy dodawaniu tools

- Tool powinien miec waski, typed contract.
- Scope incydentu ma pochodzic z `ToolContext`, nie od modelu.
- Dodaj test dla rejestracji i zachowania toola.
- Jesli tool jest drogi albo ryzykowny, dodaj guidance w
  `tools.description.CopilotToolGuidanceCatalog`.
- Jesli wynik toola jest diagnostycznie wazny, dodaj capture do
  odpowiedniego listenera i mappera w `tools.<capability>`.
- Jesli tool potrzebuje blokady, limitu albo walidacji, dodaj
  `CopilotToolInvocationPolicy`, zamiast dopisywac warunek do handlera.
- Jesli tool potrzebuje tylko logowania albo telemetryki, subskrybuj eventy
  invocation, zamiast zmieniac factory lub handler.
- Nie eksportuj DTO adapterow jako publicznego kontraktu AI.
- Nie zmieniaj publicznego scope'u analizy: `/analysis/jobs` przyjmuje tylko
  `correlationId` oraz generyczne preferencje AI (`model`, `reasoningEffort`),
  a follow-up chat tylko `message`.

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
