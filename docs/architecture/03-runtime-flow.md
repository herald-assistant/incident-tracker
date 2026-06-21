# Runtime Flow

Ten dokument opisuje aktualny przeplyw pierwszego feature'a platformy:
analizy incydentu dla job API, evidence pipeline i providera AI opartego o
Copilot SDK.

Nie opisuje calego docelowego produktu. Platforma ma obslugiwac takze kolejne
feature'y, np. flow explorer, pytania o logike funkcjonalna use case'ow oraz
natural-language data diagnostics. Te feature'y powinny reuse'owac
`aiplatform`, `agenttools`, `integrations` i shared/operator API, ale miec
wlasny runtime flow i result contract.

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

Operational context tools nie dostaja `correlationId`, `environment`,
`gitLabGroup` ani `gitLabBranch` jako model-facing input. Czytaja neutralny
katalog z `integrations.operationalcontext`; jedynym argumentem operatorskim
poza parametrami browse/search/detail jest opcjonalny `reason`.

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

Pozostale shared/operator wejscia, np. `/api/database/*`,
`/api/operational-context/*`, GitLab/Elasticsearch helper endpoints i route'y
Tool Workbench (`/database`, `/operational-context`, `/elastic`, `/gitlab`),
nie sa krokami incident runtime flow. To osobne fasady nad reusable capability
albo widoki utrzymaniowe dla operatora.

## 2. Orkiestracja wysokiego poziomu

`AnalysisOrchestrator` wykonuje flow:

1. tworzy `AnalysisContext`,
2. uruchamia deterministic evidence collector,
3. buduje `InitialAnalysisRequest`,
4. wywoluje `InitialAnalysisProvider.prepare(request)`,
5. zapisuje `prepared.prompt()` w stanie joba,
6. uruchamia `InitialAnalysisProvider.analyze(prepared, listener)`,
7. mapuje odpowiedz AI, tool evidence, tool feedback i activity trace do
   response/job state,
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
- zaladowanie runtime skills, z `incident-analysis-orchestrator` jako
  preferowanym starterem diagnostyki oraz dedykowanymi skillami kontraktu
  wyniku i narzedzi,
- zbudowanie platformowego requestu sesji,
- dekorowanie opisow tools,
- wyrenderowanie manifestu, digestu i evidence artifacts z efektywna lista
  tools oraz sekcja `runtimeSkills`,
- osadzenie artifact contents inline w promptcie,
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
  initial, available capability groups, centralna instrukcje uzycia feedbacku
  tooli, gdy `record_tool_feedback` jest dostepny, i embedded artifacts,
- `CopilotIncidentToolSessionContextFactory` tworzy incidentowy
  `CopilotToolSessionContext`: run id, session id i hidden tool context dla
  initial/follow-up,
- `CopilotIncidentSessionConfigRequestFactory` sklada incidentowe parametry
  sesji: enabled tools, available tool names, skill directories, model
  selection i komunikat odmowy tooli,
- `CopilotSessionConfigRequest` wylicza effective available tools; gdy
  skonfigurowano skill directories, dodaje built-in tool `skill`, zeby model
  mogl ladowac runtime skille przez mechanike Copilot SDK,
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
   - Operational Context zawsze, jesli capability jest zarejestrowana,
   - Tool quality feedback zawsze, jesli platformowy callback jest
     zarejestrowany,
6. publikuje GitLab/DB tool evidence i tool feedback do aktualnej odpowiedzi
   chatu,
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
- `toolPolicy.enabledToolNames`, czyli efektywna allowlista sesji, wlacznie z
  built-in `skill`, gdy skill directories sa skonfigurowane,
- `runtimeSkills`, czyli nazwe built-in toola `skill`, flage dostepnosci
  skilli i preferowane nazwy runtime skilli bez lokalnych sciezek plikowych,
- `evidenceCoverage`,
- indeks artefaktow i ich `itemIds`.

Digest zawiera skompresowane fakty sesji, coverage, log signals, deployment,
operational code search scope, runtime, code highlights i znane luki evidence.
Operational code search scope pokazuje `codeSearchScopes`, projekty GitLaba,
role repozytoriow, pakiety i class hints z operational context. Te dane
okreslaja kod dopasowanego systemu, w tym biblioteki i shared modules, ktore
trzeba przeszukiwac razem z glownym repo.

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
- `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED`,
- `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED`,
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
- wlaczone przy luce `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED`, zeby model zrobil
  focused GitLab lookup i napisal `technicalAnalysis` jako Technical Handoff v1,
- wlaczone w focused toolset przy luce `DB_CODE_GROUNDING_NEEDED`, zeby model
  sprobowal znalezc encje/repozytorium/tabele/relacje w kodzie przed DB
  discovery,
- dla znanego projektu/pliku zostaje focused toolset:
  `gitlab_list_available_repositories`, `gitlab_find_class_references`,
  `gitlab_find_flow_context`, `gitlab_read_repository_file_outline`,
  `gitlab_read_repository_file_chunk`,
  `gitlab_read_repository_file_chunks`,
- `gitlab_list_available_repositories` zwraca repozytoria GitLaba dostepne w
  biezacym session group z operational context: `projectName`, `gitLabPath`,
  krotki summary oraz sygnaly dopasowania, m.in. aliases, systems,
  boundedContexts, processes, integrations, packagePrefixes, endpointPrefixes
  i modulePaths. Zwraca tez
  `codeSearchScopes`, czyli gotowe grupy repozytoriow z rolami, priorytetami i
  lista `projectName` do wspolnego przeszukania; `projectName` z odpowiedzi
  jest inputem dla pozostalych GitLab tools,
- broad search zostaje dla braku deterministic GitLab evidence,
- `gitlab_find_flow_context` przyjmuje focused `keywords`, bez osobnych
  parametrow klasy/metody/pliku.
- gdy operational context wskazuje `codeSearchScopes`, `codeSearchProjects`
  albo kilka repo dopasowanego systemu, Copilot ma traktowac te projekty jako
  jeden scope kodu systemu i wykonac focused probe takze po
  bibliotekach/shared repozytoriach zanim uzna klase za niedostepna.

DB tools:

- wlaczone przy resolved environment i `IncidentDataDiagnosticNeed=LIKELY/REQUIRED`,
- discovery-only przy `POSSIBLE`,
- wylaczone przy braku resolved environment,
- `db_execute_readonly_sql` pozostaje domyslnie disabled.

Operational Context tools:

- neutralna capability `opctx_` z katalogowym browse/search/detail:
  `opctx_get_scope`, `opctx_list_entities`, `opctx_search`,
  `opctx_get_entity`,
- wlaczone w initial session, gdy operational context evidence jest
  `NONE`/`PARTIAL` albo coverage ma luki `MISSING_FLOW_CONTEXT`,
  `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED`,
  `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` lub `DB_CODE_GROUNDING_NEEDED`,
- wlaczone w follow-up session zawsze, jesli Spring tool callbacki sa
  zarejestrowane,
- sluza do kontekstu, ownershipu, scope GitLaba/DB i handoffu; prompt i skill
  zabraniaja traktowania samego katalogu jako dowodu root cause.

Tool quality feedback:

- platformowy tool `record_tool_feedback` mieszka w
  `aiplatform.copilot.tools.feedback`,
- jest wlaczany w initial i follow-up niezaleznie od GitLab/DB/Elastic/opctx
  policy, jesli callback jest zarejestrowany w runtime,
- nie przyjmuje `analysisId`, `correlationId`, `environment`, `gitLabGroup`
  ani `gitLabBranch`; scope sesji pochodzi z hidden `ToolContext`,
- nie zuzywa exploration budgetu i nie jest targetem feedbacku dla samego
  siebie,
- nie jest deterministic evidence, root-cause inputem ani quality gate'em.

Prompt instruuje model, zeby uzywal tools tylko dla luk z
`evidenceCoverage.gaps`. Dla `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED` model
ma oprzec `functionalAnalysis` na dolaczonym operational context albo wykonac
focused lookup katalogu. Dla `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` model ma
wykonac mala, focused probe GitLab tools przed finalna odpowiedzia, jesli
GitLab tools sa wlaczone. Dla `DB_CODE_GROUNDING_NEEDED` model ma przed
pierwsza proba DB table/column/schema-table query uzyc operational context do
targetowania systemu/repozytorium oraz deterministic GitLab evidence albo
wykonac focused GitLab tool call; gdy to niemozliwe, DB discovery jest jawnym
fallbackiem z limitation w `reason`.

## 8. Session config i blokady lokalne

`CopilotPreparedSession` niesie `SessionConfig` utworzony przez
`CopilotSessionConfigFactory`. Gateway wykonuje przygotowana konfiguracje
sesji i nie przebudowuje policy ani promptu. Do logowania runtime uzywa
neutralnego `runReference`; incident assembler przekazuje tam obecny
`correlationId`.

Effective available tools sa wyliczane z `CopilotSessionConfigRequest`.
Jezeli session request ma runtime skill directories, allowlista dostaje
built-in `skill`. Ten sam effective zestaw trafia do `SessionConfig`, hooks
`onPreToolUse` i manifestu, zeby prompt/debug/export nie rozjezdzal sie z tym,
co realnie widzi Copilot SDK.

Execution gateway subskrybuje `session.on(...)` i mapuje SDK events na
neutralne `shared.ai.AnalysisAiActivityEvent`. Te eventy sa user-visible:
pokazuja turny, usage/context/cache snapshots, tool execution lifecycle,
compaction/truncation i bledy sesji bez uzalezniania UI od typow Copilot SDK.

`SessionHooks.onPreToolUse` blokuje lokalny workspace/filesystem/shell/terminal
w glownym flow analizy. Integracyjne tools sa wywolywane przez Spring tool
callbacks. GitLab i DB dostaja scope przez hidden `ToolContext`; Elastic nadal
ma zastany model-facing `correlationId`, mimo ze jest ograniczany policy sesji.

Model nie powinien podawac jawnie scope'ow takich jak `correlationId`,
`gitLabGroup`, `gitLabBranch` czy `environment`. Aktualnie GitLab i DB tools
spelniaja to przez hidden `ToolContext`; Elastic MCP tool nadal ma jawny
parametr `correlationId` i powinien byc traktowany jako znany drift do
migracji, a nie wzorzec dla nowych tools. Operational context tools sa nowym
wzorcem neutralnym: nie przyjmuja incident scope'u jako input, tylko katalogowe
parametry typu `type`, `query`, `id`, `include` i prosty `reason`.

`record_tool_feedback` jest podobnym session-bound narzedziem platformowym:
model nie podaje publicznego scope'u analizy, tylko wskazuje target tool/call i
jawna ocene wyniku dla operatora.

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
- `aiplatform.copilot.tools.feedback.CopilotToolFeedbackInvocationListener`
  utrzymuje krotka historie zakonczonych wywolan i przechwytuje zakonczone
  wywolania `record_tool_feedback`, zeby zapis feedbacku szedl przez ten sam
  lifecycle eventow co capture GitLab/DB,
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

Budzet ma osobne liczniki capability dla Elasticsearch, GitLaba, Database i
Operational Context. Domyslny Operational Context limit to 4 wywolania i
32 000 zwroconych znakow na sesje.

`record_tool_feedback` jest zwolniony z budzetu exploration, bo sluzy
operator-facing ocenie wyniku tools, a nie dociaganiu kolejnych danych.

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
- `gitlab/tool-discovery` dla available repositories, search candidates, file
  outline, flow context i class references,
- `database/tool-results` dla DB tools.

Operational Context tools w V1 nie maja osobnej user-facing evidence capture
category. Sa widoczne w runtime activity trace jako tool calls, ale ich wynik
jest traktowany jako katalogowy grounding/scope guidance, nie jako dowod root
cause.

GitLab capture jest celowo prosty: user-facing evidence trzyma `reason` podany
przez model jako naglowek wpisu. Dla code reads zawiera plik/chunk, sciezke
pliku, tresc kodu i opcjonalny numer linii startowej. Dla discovery reads
zawiera uporzadkowane szczegoly lookupu: kandydatow plikow, grupy flow/class
references, outline pliku i rekomendowane dalsze odczyty. Techniczne pola
`toolName`, `toolCallId` i `toolArguments` sa utrzymywane jako atrybuty evidence,
ale glowny widok traktuje je jako szczegoly do JSON tooltipa.

DB capture jest rowniez celowo prosty: user-facing evidence zawiera `reason`
podany przez model oraz wynik toola jako `result`. Techniczne pola toola sa
widoczne w JSON tooltipie, a nie jako osobne badge'e/operator-facing pola. Nie
publikujemy juz osobnych pytan diagnostycznych, srodowiska, aliasu bazy ani
streszczen wyniku jako pol dla operatora.

## 11a. Tool quality feedback

`aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` zarzadza
session-bound evidence capture oraz krotka historia zakonczonych tool
invocation potrzebna do rozwiazania targetu feedbacku. Gateway rejestruje ten
store raz z sinkiem z `CopilotPreparedSession`, razem z budget store, a usuwa
go w `finally` po zakonczeniu sesji.

Sam Spring tool `record_tool_feedback` jest zwyklym callbackiem bez zapisu do
job state. Zwraca zaakceptowany kontrakt wyniku, a listener po
`CopilotToolInvocationFinishedEvent` publikuje neutralna sekcje
`ai/tool-feedback` przez ten sam `AnalysisAiToolEvidenceListener`, ktory
obsluguje GitLab/DB tool evidence. `AnalysisJobState` rozpoznaje ta sekcje i
projektuje ja do `shared.ai.AnalysisAiToolFeedback` dla API/UI. Model moze
wskazac `targetToolCallId`, `targetToolName` albo pominac target; store wtedy
probuje rozwiazac target z krotkiej historii zakonczonych non-feedback
invocation w biezacej sesji.

Feedback trafia do:

- `toolFeedback` snapshotu joba dla initial analysis,
- `toolFeedback` konkretnej odpowiedzi assistant w follow-up chat,
- eksportu JSON analizy.

Nie ma w V1 trwalej historii ani automatycznej agregacji miedzy analizami.
Prompt renderer dodaje centralna instrukcje tylko wtedy, gdy feedback tool jest
dostepny. Nie duplikujemy tej wzmianki w kazdym skillu, bo tool moze byc
zarejestrowany platformowo i powinien dzialac niezaleznie od incidentowych
playbookow.

## 12. Response contract

Prompt wymaga JSON-only response. Publiczny response aplikacji nie utrzymuje
wstecznej kompatybilnosci ze starymi polami wyniku; kontrakt jest rozdzielony
na wynik funkcjonalny dla analityka oraz techniczny handoff dla wykonawcy:

```json
{
  "detectedProblem": "string",
  "affectedProcess": "string or nieustalone",
  "affectedBoundedContext": "string or nieustalone",
  "affectedTeam": "string or nieustalone",
  "functionalAnalysis": "markdown string in Polish, Functional Analysis v1",
  "technicalAnalysis": "markdown string in Polish, Technical Handoff v1",
  "confidence": "high|medium|low",
  "visibilityLimits": ["string"]
}
```

`functionalAnalysis` ma tlumaczyc, gdzie incydent dzieje sie od strony systemu,
procesu, bounded contextu, reguly biznesowej i wysokopoziomowej architektury.
`technicalAnalysis` ma byc zgodne z runtime skillem
`incident-technical-handoff` i zawierac konkretne punkty wejscia, flow,
obserwacje techniczne, rekomendowana poprawke albo material do handoffu poza
analizowany system.

Parser probuje caly content jako JSON, potem fenced JSON block, a potem
kompletny obiekt JSON osadzony w tresci. Ta ostatnia tolerancja obsluguje
przypadki, gdy model doda krotkie zdanie przed finalnym JSON-em. Jesli nie ma
kompletnego osadzonego obiektu, pierwszy parsowalny obiekt nadal moze posluzyc
do fallbacku czesciowo sparsowanych pol. Legacy labeled parser nie istnieje.
Jesli brakuje wymaganych pol, fallback zachowuje czesciowo sparsowane pola i
ustawia `AI_UNSTRUCTURED_RESPONSE` tylko wtedy, gdy brakuje `detectedProblem`.

## 13. Response quality

Nie ma obecnie osobnego quality gate po parsingu odpowiedzi. Runtime zachowuje
prosty kontrakt: prompt wymaga JSON, parser mapuje wynik na publiczny response,
a fallback obsluguje brak wymaganych pol. Dodatkowe oceny jakosci nie sa
liczone w tle, bo operator nie ma do nich dostepu.

## 14. User-visible usage i activity

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

Oprocz agregowanego usage runtime publikuje `AnalysisAiActivityEvent`. To jest
jawny productized trace dla operatora: pokazuje komunikaty/rozumowanie AI,
lifecycle tools, context/cache snapshots i bledy sesji. UI nie pokazuje tych
eventow jako osobnej listy technicznej; merge'uje je z `toolEvidenceSections`
w jeden tok pracy analizy. `details` pozostaje JSON-ready payloadem w
rozwijanych szczegolach wpisu.

## 15. Job state i UI

Async flow zapisuje prepared prompt przed wykonaniem AI. Dzieki temu prompt
jest dostepny nawet wtedy, gdy execution failuje.

UI jest osadzony w shellu `Team Delivery Workspace`. `GET /` jest startowym
overview workspace'u z szybkim wejsciem do aktywnych feature'ow.
`Incident Analysis` dziala pod `/incident-analysis` jako feature w grupie
`Analysis Features`; ekrany `/elastic`, `/gitlab`, `/database` i
`/operational-context` sa `Tool Workbench`, czyli analysis-independent
widokami helper capability. Te route'y moga pomagac operatorowi w recznym
debugowaniu albo zbieraniu inputu, ale nie zmieniaja publicznego requestu
`POST /analysis/jobs` ani hidden scope'u sesji AI.

`toolEvidenceSections` oraz `aiActivityEvents` sa osobnymi polami job response
i moga byc aktualizowane podczas sesji AI przez listenery. Sa jednak jednym
mechanizmem prezentacji w UI: frontend buduje z nich plaska liste pracy
`AI_ANALYSIS`. UI nie zalezy od typow Copilot SDK.

`toolFeedback` jest osobnym user-visible polem job response. Dla initial
analysis UI pokazuje panel "Feedback jakosci tooli" po analizie, a dla
follow-up chat kompaktowy blok przy konkretnej odpowiedzi assistant.
Import/export analizy zachowuje te dane; starsze eksporty bez `toolFeedback`
sa normalizowane do pustych list.

Finalny krok `AI_ANALYSIS` moze niesc `usage` z generycznym
`shared.ai.AnalysisAiUsage`. UI pokazuje tam sumaryczne zuzycie tokenow oraz tooltip ze
szczegolami zebranymi z eventow Copilota.

Lista pracy AI nie zagniezdza turnow. `assistant.message` i
`assistant.reasoning` sa wpisami opisujacymi tok myslenia AI, a powiazane
wywolania tools sa renderowane jako kolejne, rownorzedne wiersze z tym samym
statusem wykonania: loader, OK, blad albo info. `assistant.usage`,
`session.usage_info`, `session.truncation`, `session.compaction_complete`,
`session.context_changed` i `session.error` sa wstawiane zgodnie z timestampem.
Techniczne wejscie toola (`toolArguments`, `toolCallId`, `toolName`) oraz raw
event details trafiaja do rozwijanych szczegolow wiersza. Follow-up chat moze
miec wlasne `toolEvidenceSections` i `aiActivityEvents` przypisane do
wiadomosci asystenta.

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
analysis.database.enabled=false
analysis.database.connection-defaults.username=INCIDENT_TRACKER_RO
analysis.database.connection-defaults.password=${INCIDENT_TRACKER_DB_PASSWORD}
analysis.database.connections.dev.jdbc-url=jdbc:oracle:thin:@//db-dev.example.internal:1521/service
analysis.database.applications.crm-service.database-user=CRM_APP
analysis.database.applications.crm-service.application-patterns=crm-service,CRM_APP
analysis.database.environments.sandbox-1.connection=dev
analysis.database.environments.sandbox-1.application-user-suffix=_1

analysis.ai.copilot.working-directory=${user.dir}
analysis.ai.copilot.permission-mode=approve-all
analysis.ai.copilot.send-and-wait-timeout=5m
analysis.ai.copilot.model-options-timeout=20s
analysis.ai.copilot.model-options-cache-ttl=10m
analysis.ai.copilot.skill-resource-roots=copilot/skills
analysis.ai.copilot.skill-runtime-directory=${java.io.tmpdir}/incident-tracker/copilot-runtime

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
analysis.ai.copilot.tool-budget.max-operational-context-calls=4
analysis.ai.copilot.tool-budget.max-operational-context-returned-characters=32000
```

Database config uzywa shared connections + globalnego katalogu aplikacji z
`database-user`, jednym `application-patterns` i per-environment
`application-user-suffix`. Per-environment application mappings nie sa juz
osobnym kontraktem konfiguracji.

Nie ma runtime flagi wybierajacej legacy response labels. Aktualny kontrakt
Copilota jest JSON-only.
