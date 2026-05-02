# Runtime Flow

Ten dokument opisuje aktualny przeplyw analizy incydentu dla job API, evidence
pipeline i providera AI opartego o Copilot SDK.

## 1. Publiczne wejscia

```http
POST /analysis/jobs
GET /analysis/jobs/{analysisId}
POST /analysis/jobs/{analysisId}/chat/messages
GET /analysis/ai/options
```

Job request dla UI niesie `correlationId` oraz opcjonalne preferencje wykonania
AI: `model` i `reasoningEffort`. Pozostale scope'y sa ustalane przez backend:

- `environment` z evidence runtime/deployment,
- `gitLabBranch` z deployment context,
- `gitLabGroup` z konfiguracji,
- DB scope z resolved environment i konfiguracji database tools.

Wybor modelu i `reasoningEffort` trafia tylko do konfiguracji sesji AI. Nie
jest evidence, nie zmienia `environment`, `gitLabBranch`, `gitLabGroup` ani
hidden `ToolContext`.

`GET /analysis/ai/options` jest osobnym shared/operator API do pobrania
katalogu modeli dla UI. Nie jest krokiem incident job flow. Fasada w
`api.aioptions` mapuje platformowy katalog modeli z
`aiplatform.copilot.runtime.options` na generyczne DTO aplikacji. Platformowy
provider uzywa `CopilotClient.listModels()` i cache'uje wynik; jesli SDK jest
chwilowo niedostepne, zwraca tylko skonfigurowane domysly. Neutralne
preferencje requestu mieszkaja w `shared.ai`.

`POST /analysis/jobs/{analysisId}/chat/messages` jest dostepny dopiero po
`COMPLETED`. Request niesie tylko tresc wiadomosci operatora. Scope follow-up
(`correlationId`, `environment`, `gitLabBranch`, `gitLabGroup`) pochodzi z
zakonczonego joba i ukrytego requestu AI zapisanego po finalnej analizie.

Copilot auth jest osobnym shared/operator flow:

```http
GET /api/auth/github/status
GET /api/auth/github/start?returnUrl=/...
GET /api/auth/github/callback?code=...&state=...
POST /api/auth/github/logout
```

W `LOCAL_TOKEN` backend uzywa skonfigurowanego tokena lokalnego. W
`GITHUB_APP` UI laczy konto przez GitHub App OAuth, a backend zapisuje
zaszyfrowany GitHub App user access token dla backendowej operator session.
Frontend nigdy nie dostaje tokena, OAuth code ani SDK-specific typu.

`AnalysisJobService` przed utworzeniem joba rozwiązuje non-secret
`AnalysisAiAuthRef` dla aktualnego requestu i sprawdza, czy token da sie
uzyskac. Do background flow trafia tylko ta referencja, nie token. Follow-up
chat reuse'uje `authRef` zapisany w `InitialAnalysisRequest` zakonczonego joba.

## 2. Orkiestracja wysokiego poziomu

`AnalysisOrchestrator` wykonuje flow:

1. tworzy `AnalysisContext`,
2. uruchamia deterministic evidence collector,
3. buduje `InitialAnalysisRequest`,
4. wywoluje `InitialAnalysisProvider.prepare(request)`,
5. zapisuje `prepared.prompt()` w stanie joba,
6. uruchamia `InitialAnalysisProvider.analyze(prepared, listener)`,
7. mapuje odpowiedz AI i tool evidence do response/job state,
8. zamyka prepared analysis.

Prepared analysis gwarantuje, ze prompt widoczny w UI/debug jest tym samym
promptem, ktory poszedl do Copilota.

Ownership jest po stronie kodu, ktory wywolal `prepare(request)`.
`AnalysisOrchestrator` uzywa try-with-resources i zamyka prepared analysis po
zakonczeniu kroku AI. `analyze(prepared, listener)` oraz gateway SDK nie
zamykaja obiektu przekazanego przez caller.

## 3. Evidence pipeline

Evidence collector pracuje na `AnalysisContext` i zwraca liste
`shared.evidence.AnalysisEvidenceSection`.

Typowy przebieg:

1. Elasticsearch log evidence po `correlationId`,
2. deployment context resolution,
3. po deployment context mozliwy rownolegly fan-out:
   - Dynatrace runtime signals,
   - GitLab deterministic resolved code evidence,
4. operational context matching,
5. opcjonalne sekcje uzupelniajace.

Provider evidence powinien izolowac adapter-specific modele. Na granicy AI
zostaja tylko generyczne DTO z `shared.evidence`: `AnalysisEvidenceSection`,
`AnalysisEvidenceItem` i `AnalysisEvidenceAttribute`.

## 4. Przygotowanie Copilota

`CopilotIncidentInitialPreparationService` buduje
`CopilotInitialAnalysisPreparation`. Ten adapter implementuje
`InitialAnalysisPreparation` i zawiera neutralna techniczna sesje
`CopilotPreparedSession`.

Preparation obejmuje:

- wyliczenie `CopilotIncidentEvidenceCoverageReport`,
- zbudowanie `CopilotIncidentToolAccessPolicy`,
- dekorowanie opisow tools,
- wyrenderowanie manifestu, digestu i evidence artifacts,
- osadzenie artifact contents inline w promptcie,
- zaladowanie runtime skills,
- zbudowanie platformowego requestu sesji,
- dolaczenie platformowego `CopilotRunAuth` z non-secret auth reference,
- zastosowanie requestowych preferencji AI (`model`, `reasoningEffort`) albo
  fallback do properties,
- zebranie metryk preparation.

Serwis preparation sklada zaleznosci, ale nie zawiera juz calej logiki
renderingu i konfiguracji SDK:

- `CopilotIncidentToolAccessPolicyFactory` buduje initial coverage-aware policy oraz
  follow-up policy ze scope'u zakonczonej analizy,
- `CopilotIncidentPromptRenderer` i `CopilotIncidentFollowUpPromptRenderer`
  renderuja incident prompt initial/follow-up, JSON-only response contract dla
  initial, available capability groups i embedded artifacts,
- `CopilotIncidentToolSessionContextFactory` tworzy incidentowy
  `CopilotToolSessionContext`: run id, session id i hidden tool context dla
  initial/follow-up,
- `CopilotIncidentSessionConfigRequestFactory` sklada incidentowe parametry
  sesji: enabled tools, available tool names, skill directories, model
  selection i komunikat odmowy tooli,
- `CopilotFollowUpArtifactRequestFactory` mapuje follow-up snapshot na request
  artefaktow: deterministic evidence plus tool evidence z poprzednich sesji,
- `CopilotIncidentRunRequestFactory` tworzy neutralny `CopilotRunRequest` z
  run reference, promptu, session config request i artifact contents,
- `CopilotIncidentInitialRunAssembler` sklada
  `CopilotIncidentInitialRunAssembly`, ktory niesie neutralny
  `CopilotRunRequest` oraz osobny snapshot metryk preparation,
- `CopilotIncidentFollowUpRunAssembler` zwraca juz bezposrednio neutralny
  `CopilotRunRequest`,
- `CopilotRunPreparationService` jest neutralnym wejsciem runtime:
  `CopilotRunRequest -> CopilotPreparedSession`,
- `CopilotPreparedSessionFactory` mapuje request na techniczna sesje, a
  `CopilotSessionConfigFactory` buduje client options, session config,
  permission handler, hooks i disabled skills.

`CopilotSessionConfigFactory` rozwiązuje access token przez
`aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver` dopiero podczas
tworzenia `CopilotClientOptions`. Client options zawsze dostaja jawne
`githubToken` oraz `useLoggedInUser=false`; runtime nie korzysta z lokalnych
credentiali GitHub CLI/Copilot CLI.

`CopilotSdkModelOptionsProvider` mieszka w
`aiplatform.copilot.runtime.options`. Uzywa zaleznosci runtime do pobrania
katalogu modeli przez SDK, ale nie miesza tej metadanej z evidence ani
promptem incydentu. Provider jest auth-aware: w `LOCAL_TOKEN` moze cache'owac
globalnie, a w `GITHUB_APP` cache key zawiera auth principal, zeby nie mieszac
katalogow miedzy operatorami. Endpoint `GET /analysis/ai/options` jest
shared/operator API w `api.aioptions`, mapujacym platformowe DTO na kontrakt
UI.

Runtime nie przekazuje evidence przez SDK attachments. Logical artifacts sa
fragmentami promptu.

## 4a. Follow-up chat po analizie

Po zakonczonej analizie `AnalysisJobService` moze dopisac pare wiadomosci
chatu: user message jako `COMPLETED` i assistant message jako `IN_PROGRESS`.
W tle uruchamiany jest osobny task, ktory wywoluje `AnalysisAiChatProvider`.

Follow-up runtime:

1. bierze `InitialAnalysisRequest` zapisany z poczatkowej analizy,
2. dolacza deterministyczne evidence, finalny wynik, historie chatu i tool
   evidence z wczesniejszych sesji,
3. buduje nowy prompt kontynuacyjny,
4. tworzy nowa sesje Copilota z tym samym hidden scope,
5. wystawia tylko session-bound tools sensowne dla rozwiazanego scope'u:
   - Elasticsearch po aktualnym `correlationId`,
   - GitLab gdy jest `gitLabGroup` i `gitLabBranch`,
   - Database gdy jest resolved `environment`,
6. publikuje GitLab/DB tool evidence do aktualnej odpowiedzi chatu,
7. zapisuje odpowiedz albo blad w `chatMessages` joba.

Chat nie dodaje nowego providerowego kroku evidence i nie uruchamia ponownie
deterministycznego collectora. To jest warstwa operatorskiej kontynuacji nad
zakonczonym snapshotem analizy.

## 5. Artefakty incydentu

Kolejnosc artefaktow:

```text
00-incident-manifest.json
01-incident-digest.md
02-... evidence artifact
03-... evidence artifact
```

Manifest zawiera:

- `correlationId`, `environment`, `gitLabBranch`, `gitLabGroup`,
- `artifactFormatVersion`,
- `readFirst=00-incident-manifest.json`,
- `readNext=01-incident-digest.md`,
- `artifactPolicy.deliveryMode=embedded-prompt`,
- enabled/disabled tool groups,
- `evidenceCoverage`,
- indeks artefaktow i ich `itemIds`.

Digest zawiera skompresowane fakty sesji, coverage, log signals, deployment,
operational code search scope, runtime, code highlights i znane luki evidence.
Operational code search scope pokazuje projekty GitLaba z operational context,
ktore razem skladaja sie na kod dopasowanego komponentu wdrozeniowego, w tym
biblioteki i shared modules.

`itemId` sa stabilne tylko w renderingu Copilota. Nie zmieniaja publicznego
kontraktu `AnalysisEvidenceItem`.

## 6. Coverage evaluator

`CopilotIncidentEvidenceCoverageEvaluator` czyta generyczne evidence przez helpery
widoku i zwraca:

- `IncidentElasticEvidenceCoverage`,
- `IncidentGitLabEvidenceCoverage`,
- `IncidentRuntimeEvidenceCoverage`,
- `IncidentOperationalContextCoverage`,
- `IncidentDataDiagnosticNeed`,
- `environmentResolved`,
- liste `IncidentEvidenceGap`.

Przyklady luk:

- `MISSING_LOGS`,
- `MISSING_STACKTRACE`,
- `TRUNCATED_LOGS`,
- `MISSING_CODE_CONTEXT`,
- `MISSING_FLOW_CONTEXT`,
- `DB_ENVIRONMENT_UNRESOLVED`,
- `AFFECTED_FUNCTION_GITLAB_RECOMMENDED`,
- `DB_CODE_GROUNDING_NEEDED`,
- `DB_DIAGNOSTIC_NEEDED`.

Coverage nie diagnozuje root cause. Jego rola to powiedziec, czy AI ma
wystarczajacy material i ktore tools mozna uzasadnic.

## 7. Coverage-aware tool policy

`CopilotIncidentToolAccessPolicy` filtruje tools na podstawie coverage.
Produkcyjna sciezka tworzy ja przez `CopilotIncidentToolAccessPolicyFactory`, ktora
uzywa `CopilotIncidentEvidenceCoverageEvaluator` i przekazuje do policy gotowy
`CopilotIncidentEvidenceCoverageReport`.

Elasticsearch tools:

- wlaczone, gdy brakuje logow, stacktrace albo logi sa obciete,
- wylaczone, gdy log evidence jest wystarczajace.

GitLab tools:

- wlaczone, gdy brakuje code evidence albo flow context,
- wlaczone przy luce `AFFECTED_FUNCTION_GITLAB_RECOMMENDED`, zeby model zrobil
  focused GitLab lookup i opisal `affectedFunction` szczegolowo, ale jezykiem
  techniczno-funkcjonalnym zamiast code walkthrough,
- wlaczone w focused toolset przy luce `DB_CODE_GROUNDING_NEEDED`, zeby model
  sprobowal znalezc encje/repozytorium/tabele/relacje w kodzie przed DB
  discovery,
- dla znanego projektu/pliku zostaje focused toolset:
  `gitlab_find_class_references`, `gitlab_find_flow_context`,
  `gitlab_read_repository_file_outline`,
  `gitlab_read_repository_file_chunk`,
  `gitlab_read_repository_file_chunks`,
- broad search zostaje dla braku deterministic GitLab evidence,
- `gitlab_find_flow_context` przyjmuje focused `keywords`, bez osobnych
  parametrow klasy/metody/pliku.
- gdy operational context wskazuje `codeSearchProjects` albo kilka repo
  dopasowanego systemu, Copilot ma traktowac te projekty jako jeden scope kodu
  komponentu wdrozeniowego i wykonac focused probe takze po bibliotekach/shared
  repozytoriach zanim uzna klase za niedostepna.

DB tools:

- wlaczone przy resolved environment i `IncidentDataDiagnosticNeed=LIKELY/REQUIRED`,
- discovery-only przy `POSSIBLE`,
- wylaczone przy braku resolved environment,
- `db_execute_readonly_sql` pozostaje domyslnie disabled.

Prompt instruuje model, zeby uzywal tools tylko dla luk z
`evidenceCoverage.gaps`. Dla `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` model ma
wykonac mala, focused probe GitLab tools przed finalna odpowiedzia, jesli
GitLab tools sa wlaczone. Dla `DB_CODE_GROUNDING_NEEDED` model ma przed
pierwsza proba DB table/column/schema-table query uzyc deterministic GitLab
evidence albo wykonac focused GitLab tool call; gdy to niemozliwe, DB discovery
jest jawnym fallbackiem z limitation w `reason`.

## 8. Session config i blokady lokalne

`CopilotPreparedSession` niesie `SessionConfig` utworzony przez
`CopilotSessionConfigFactory`. Gateway wykonuje przygotowana konfiguracje
sesji i nie przebudowuje policy ani promptu. Do logowania runtime uzywa
neutralnego `runReference`; incident assembler przekazuje tam obecny
`correlationId`.

`SessionHooks.onPreToolUse` blokuje lokalny workspace/filesystem/shell/terminal
w glownym flow analizy. Integracyjne tools sa wywolywane przez Spring tool
callbacks. GitLab i DB dostaja scope przez hidden `ToolContext`; Elastic nadal
ma zastany model-facing `correlationId`, mimo ze jest ograniczany policy sesji.

Model nie powinien podawac jawnie scope'ow takich jak `correlationId`,
`gitLabGroup`, `gitLabBranch` czy `environment`. Aktualnie GitLab i DB tools
spelniaja to przez hidden `ToolContext`; Elastic MCP tool nadal ma jawny
parametr `correlationId` i powinien byc traktowany jako znany drift do
migracji, a nie wzorzec dla nowych tools.

## 9. Tool factory

`aiplatform.copilot.tools.CopilotSdkToolFactory` konwertuje Spring tools na
definicje Copilota.

Po refaktorze factory robi tylko rejestracje definicji i jest glowna klasa
wejsciowa platformowego pakietu `aiplatform.copilot.tools` dla preparation:

- customizacja opisow przez platformowy
  `aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer`,
- parsowanie input schema,
- utworzenie `ToolDefinition` z handlerem wykonania.

`aiplatform.copilot.tools.CopilotToolInvocationHandler` obsluguje runtime
invocation:

- generyczne `CopilotToolInvocationPolicy` przed i po callbacku,
- budowe hidden `ToolContext`,
- wywolanie Spring `ToolCallback`,
- publikacje eventu `Started` i terminalnego `Finished` z outcome
  `COMPLETED`, `REJECTED` albo `FAILED`,
- parsowanie wyniku callbacka,
- normalizacje kontrolowanego rejection na wynik zwracany do SDK.

Walidacje session id robi `CopilotToolSessionValidationPolicy` w
`aiplatform.copilot.tools.policy.session`; budzet robi
`CopilotToolBudgetPolicy` w `aiplatform.copilot.tools.policy.budget`.

Side-effecty tool invocation sa subskrybowane przez Spring event listeners:

- `aiplatform.copilot.tools.logging.CopilotToolInvocationLoggingListener`
  loguje request/result preview,
- `tools.gitlab.GitLabToolEvidenceCaptureListener` mapuje wyniki GitLaba do
  tool evidence,
- `tools.database.DatabaseToolEvidenceCaptureListener` mapuje wyniki DB do
  tool evidence.

`aiplatform.copilot.tools.context.CopilotToolContextFactory` buduje hidden
`ToolContext` na podstawie `CopilotToolSessionContext` i invocation.

`aiplatform.copilot.tools.events.CopilotToolInvocationEventPublisher` lapie
wyjatki listenerow i loguje ostrzezenie. Eventy sa wiec warstwa
obserwowalnosci/audytu, a nie druga sciezka wykonania toola.

Decorated descriptions sa guidance dla modelu, nie zmieniaja implementacji
adapterow.

## 10. Tool budget

Budzet jest session-bound:

1. gateway rejestruje session state,
2. invocation handler uruchamia `CopilotToolInvocationPolicy.beforeInvocation(...)`,
3. `CopilotToolBudgetPolicy` jako policy moze rzucic kontrolowany
   `CopilotToolInvocationRejectedException`,
4. callback Spring tool jest wykonany albo handler zwraca kontrolowany denied
   result do SDK,
5. po udanym callbacku handler uruchamia
   `CopilotToolInvocationPolicy.afterInvocation(...)` z raw result i publikuje
   terminalny event `Finished(COMPLETED)`,
6. gateway usuwa state w `finally`.

Domyslny tryb to `soft`: przekroczenia sa logowane, ale nie blokuja. `hard`
zwraca:

```json
{
  "status": "denied_by_tool_budget",
  "toolName": "...",
  "reason": "...",
  "instruction": "Stop further exploration and return the best grounded analysis with visibility limits."
}
```

`Finished(REJECTED)` jest traktowany jako odmowa przed faktycznym wykonaniem
toola. Budget state nadal zachowuje informacje o denialu do konca sesji.

## 11. Tool evidence capture

`aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` zarzadza
sesja capture i publikuje dodatkowe sekcje evidence do job listenera. Samo
mapowanie wynikow jest subskrybowane z eventow tool invocation przez pakiety
capability:
`tools.gitlab` i `tools.database`.

Store nie zna JSON payloadow konkretnych tools. Udostepnia per-session
`SessionToolEvidence`, pilnuje kolejnosci `toolCaptureOrder`, scala albo
dopisuje itemy i publikuje zaktualizowana sekcje przez neutralny session-bound
sink. Copilot provider mapuje `shared.evidence.AnalysisAiToolEvidenceListener`
na ten sink przed wejsciem do execution gatewaya.

Kategorie:

- `gitlab/tool-fetched-code` dla file/chunk/chunks,
- `gitlab/tool-discovery` dla search candidates, file outline, flow context i
  class references,
- `database/tool-results` dla DB tools.

GitLab capture jest celowo prosty: user-facing evidence trzyma `reason` podany
przez model jako naglowek wpisu. Dla code reads zawiera plik/chunk, sciezke
pliku, tresc kodu i opcjonalny numer linii startowej. Dla discovery reads
zawiera uporzadkowane szczegoly lookupu: kandydatow plikow, grupy flow/class
references, outline pliku i rekomendowane dalsze odczyty.

DB capture jest rowniez celowo prosty: user-facing evidence zawiera `reason`
podany przez model oraz wynik toola jako `result`. Nie publikujemy juz osobnych
pytan diagnostycznych, parametrow, srodowiska, aliasu bazy ani streszczen
wyniku jako pol dla operatora.

## 12. Response contract

Prompt wymaga JSON-only response. Publiczny response aplikacji nadal mapuje
do obecnych pol, ale JSON AI zawiera bogatszy kontrakt:

```json
{
  "detectedProblem": "string",
  "summary": "markdown string in Polish",
  "recommendedAction": "markdown string in Polish",
  "rationale": "markdown string in Polish",
  "affectedFunction": "markdown string in Polish",
  "affectedProcess": "string or nieustalone",
  "affectedBoundedContext": "string or nieustalone",
  "affectedTeam": "string or nieustalone",
  "confidence": "high|medium|low",
  "evidenceReferences": [
    {
      "field": "detectedProblem|summary|recommendedAction|rationale|affectedFunction|affectedProcess|affectedBoundedContext|affectedTeam",
      "artifactId": "string",
      "itemId": "string",
      "claim": "short Polish explanation"
    }
  ],
  "visibilityLimits": ["string"]
}
```

Parser probuje caly content jako JSON, potem fenced JSON block. Legacy
labeled parser nie istnieje. Jesli brakuje wymaganych pol, fallback zachowuje
czesciowo sparsowane pola i ustawia `AI_UNSTRUCTURED_RESPONSE` tylko wtedy,
gdy brakuje `detectedProblem`.

## 13. Response quality

Nie ma obecnie osobnego quality gate po parsingu odpowiedzi. Runtime zachowuje
prosty kontrakt: prompt wymaga JSON, parser mapuje wynik na publiczny response,
a fallback obsluguje brak wymaganych pol. Dodatkowe oceny jakosci nie sa
liczone w tle, bo operator nie ma do nich dostepu.

## 14. User-visible usage

Nie ma obecnie osobnego registry niewidocznej dla operatora telemetryki sesji.
Execution gateway agreguje tylko zdarzenia SDK potrzebne do publicznego
`shared.ai.AnalysisAiUsage`:

- token usage z eventow `assistant.usage`: input/output/cache read/cache write,
  liczba wywolan API, model, koszt i czas API,
- ostatni snapshot `session.usage_info`, czyli context token limit/current
  tokens/messages length.

Ten usage trafia do finalnego kroku `AI_ANALYSIS` i job state, a UI pokazuje go
jako zuzycie tokenow/kosztu. Dane, ktorych operator nie widzi, nie sa obecnie
utrzymywane jako osobny feature runtime.

## 15. Job state i UI

Async flow zapisuje prepared prompt przed wykonaniem AI. Dzieki temu prompt
jest dostepny nawet wtedy, gdy execution failuje.

`toolEvidenceSections` sa osobnym polem job response i moga byc aktualizowane
podczas sesji AI przez listener. UI nie zalezy od typow Copilot SDK.

Finalny krok `AI_ANALYSIS` moze niesc `usage` z generycznym
`shared.ai.AnalysisAiUsage`. UI pokazuje tam sumaryczne zuzycie tokenow oraz tooltip ze
szczegolami zebranymi z eventow Copilota.

Job request UI zawiera `correlationId` oraz opcjonalne preferencje AI
(`model`, `reasoningEffort`). `gitLabGroup` pochodzi z konfiguracji, a
`environment` i `gitLabBranch` sa wyprowadzane z evidence.

Job response zawiera tez `chatMessages`. UI pokazuje chat dopiero po
`COMPLETED`; dla wiadomosci chatu z `IN_PROGRESS` polluje ten sam endpoint
`GET /analysis/jobs/{analysisId}`. Importowany eksport analizy jest read-only,
bo lokalny backend nie ma odpowiadajacego mu stanu joba.

Job state przechowuje `InitialAnalysisRequest` z zakonczonej analizy, zeby
follow-up chat reuse'owal resolved scope i evidence bez publicznego requestu
scope'u od operatora.

Przed startem joba UI pobiera `GET /analysis/ai/options`. Select modelu i
`reasoningEffort` sa budowane z odpowiedzi backendu; jesli wybrany model nie
ma dostepnych effortow w SDK, UI nie wysyla `reasoningEffort`.

## 16. Najwazniejsze properties

```properties
analysis.ai.copilot.working-directory=${user.dir}
analysis.ai.copilot.permission-mode=approve-all
analysis.ai.copilot.send-and-wait-timeout=5m
analysis.ai.copilot.model-options-timeout=20s
analysis.ai.copilot.model-options-cache-ttl=10m
analysis.ai.copilot.skill-resource-roots=copilot/skills
analysis.ai.copilot.skill-runtime-directory=${java.io.tmpdir}/incident-tracker/copilot-skills

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

Nie ma runtime flagi wybierajacej legacy response labels. Aktualny kontrakt
Copilota jest JSON-only.
