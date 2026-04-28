# Runtime Flow

Ten dokument opisuje aktualny przeplyw analizy incydentu dla sync API,
job API, evidence pipeline i providera AI opartego o Copilot SDK.

## 1. Publiczne wejscia

Sync:

```http
POST /analysis
```

Async:

```http
POST /analysis/jobs
GET /analysis/jobs/{analysisId}
POST /analysis/jobs/{analysisId}/chat/messages
GET /analysis/ai/options
```

Sync request publiczny niesie tylko `correlationId`. Job request dla UI niesie
`correlationId` oraz opcjonalne preferencje wykonania AI: `model` i
`reasoningEffort`. Pozostale scope'y sa ustalane przez backend:

- `environment` z evidence runtime/deployment,
- `gitLabBranch` z deployment context,
- `gitLabGroup` z konfiguracji,
- DB scope z resolved environment i konfiguracji database tools.

Wybor modelu i `reasoningEffort` trafia tylko do konfiguracji sesji AI. Nie
jest evidence, nie zmienia `environment`, `gitLabBranch`, `gitLabGroup` ani
hidden `ToolContext`.

`GET /analysis/ai/options` jest osobnym kontraktem UI do pobrania katalogu
modeli. Backend mapuje `CopilotClient.listModels()` na generyczne DTO aplikacji
i cache'uje wynik; jesli SDK jest chwilowo niedostepne, zwraca tylko
skonfigurowane domysly.

`POST /analysis/jobs/{analysisId}/chat/messages` jest dostepny dopiero po
`COMPLETED`. Request niesie tylko tresc wiadomosci operatora. Scope follow-up
(`correlationId`, `environment`, `gitLabBranch`, `gitLabGroup`) pochodzi z
zakonczonego joba i ukrytego requestu AI zapisanego po finalnej analizie.

## 2. Orkiestracja wysokiego poziomu

`AnalysisOrchestrator` wykonuje flow:

1. tworzy `AnalysisContext`,
2. uruchamia deterministic evidence collector,
3. buduje `AnalysisAiAnalysisRequest`,
4. wywoluje `AnalysisAiProvider.prepare(request)`,
5. zapisuje `prepared.prompt()` w stanie joba,
6. uruchamia `AnalysisAiProvider.analyze(prepared, listener)`,
7. mapuje odpowiedz AI i tool evidence do response/job state,
8. zamyka prepared analysis.

Prepared analysis gwarantuje, ze prompt widoczny w UI/debug jest tym samym
promptem, ktory poszedl do Copilota.

Ownership jest po stronie kodu, ktory wywolal `prepare(request)`.
`AnalysisOrchestrator` uzywa try-with-resources i zamyka prepared analysis po
zakonczeniu kroku AI. Provider zamyka tylko prepared analysis utworzone przez
wlasne `analyze(request)`. `analyze(prepared, listener)` oraz gateway SDK nie
zamykaja obiektu przekazanego przez caller.

## 3. Evidence pipeline

Evidence collector pracuje na `AnalysisContext` i zwraca liste
`AnalysisEvidenceSection`.

Typowy przebieg:

1. Elasticsearch log evidence po `correlationId`,
2. deployment context resolution,
3. po deployment context mozliwy rownolegly fan-out:
   - Dynatrace runtime signals,
   - GitLab deterministic resolved code evidence,
4. operational context matching,
5. opcjonalne sekcje uzupelniajace.

Provider evidence powinien izolowac adapter-specific modele. Na granicy AI
zostaja tylko generyczne `AnalysisEvidenceSection`, `AnalysisEvidenceItem` i
`AnalysisEvidenceAttribute`.

## 4. Przygotowanie Copilota

`CopilotSdkPreparationService` buduje `CopilotSdkPreparedRequest`.
Prepared request implementuje `AnalysisAiPreparedAnalysis`.

Preparation obejmuje:

- wyliczenie `CopilotEvidenceCoverageReport`,
- zbudowanie `CopilotToolAccessPolicy`,
- dekorowanie opisow tools,
- wyrenderowanie manifestu, digestu i evidence artifacts,
- osadzenie artifact contents inline w promptcie,
- zaladowanie runtime skills,
- zbudowanie `CopilotClientOptions` i `SessionConfig`,
- zastosowanie requestowych preferencji AI (`model`, `reasoningEffort`) albo
  fallback do properties,
- zebranie metryk preparation.

Serwis preparation sklada zaleznosci, ale nie zawiera juz calej logiki
renderingu i konfiguracji SDK:

- `CopilotToolAccessPolicyFactory` laczy evaluator coverage z registered
  tools i zwraca coverage-aware policy,
- `CopilotPromptRenderer` renderuje prompt, JSON-only response contract,
  available capability groups i embedded artifacts,
- `CopilotSessionConfigFactory` buduje client options, session config,
  permission handler, hooks, skill directories i disabled skills.
- `CopilotSdkModelOptionsProvider` uzywa tego samego factory client options do
  pobrania katalogu modeli przez SDK, ale nie miesza tej metadanej z evidence
  ani promptem incydentu.

Runtime nie przekazuje evidence przez SDK attachments. Logical artifacts sa
fragmentami promptu.

## 4a. Follow-up chat po analizie

Po zakonczonej analizie `AnalysisJobService` moze dopisac pare wiadomosci
chatu: user message jako `COMPLETED` i assistant message jako `IN_PROGRESS`.
W tle uruchamiany jest osobny task, ktory wywoluje `AnalysisAiChatProvider`.

Follow-up runtime:

1. bierze `AnalysisAiAnalysisRequest` zapisany z finalnej analizy,
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
runtime, code highlights i znane luki evidence.

`itemId` sa stabilne tylko w renderingu Copilota. Nie zmieniaja publicznego
kontraktu `AnalysisEvidenceItem`.

## 6. Coverage evaluator

`CopilotEvidenceCoverageEvaluator` czyta generyczne evidence przez helpery
widoku i zwraca:

- `ElasticEvidenceCoverage`,
- `GitLabEvidenceCoverage`,
- `RuntimeEvidenceCoverage`,
- `OperationalContextCoverage`,
- `DataDiagnosticNeed`,
- `environmentResolved`,
- liste `EvidenceGap`.

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

`CopilotToolAccessPolicy` filtruje tools na podstawie coverage.
Produkcyjna sciezka tworzy ja przez `CopilotToolAccessPolicyFactory`, ktora
uzywa `CopilotEvidenceCoverageEvaluator` i przekazuje do policy gotowy
`CopilotEvidenceCoverageReport`.

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

DB tools:

- wlaczone przy resolved environment i `DataDiagnosticNeed=LIKELY/REQUIRED`,
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

`CopilotSdkPreparedRequest` niesie `SessionConfig` utworzony przez
`CopilotSessionConfigFactory`. Gateway wykonuje przygotowana konfiguracje
sesji i nie przebudowuje policy ani promptu.

`SessionHooks.onPreToolUse` blokuje lokalny workspace/filesystem/shell/terminal
w glownym flow analizy. Integracyjne tools GitLab/Elastic/DB sa wywolywane
przez Spring tool callbacks i hidden `ToolContext`.

Model nie powinien podawac jawnie scope'ow takich jak `correlationId`,
`gitLabGroup`, `gitLabBranch` czy `environment`. Aktualnie GitLab i DB tools
spelniaja to przez hidden `ToolContext`; Elastic MCP tool nadal ma jawny
parametr `correlationId` i powinien byc traktowany jako znany drift do
migracji, a nie wzorzec dla nowych tools.

## 9. Tool bridge

`CopilotSdkToolBridge` konwertuje Spring tools na definicje Copilota.

Po refaktorze bridge robi tylko rejestracje definicji:

- dekorowanie opisow przez `CopilotToolDescriptionDecorator`,
- parsowanie input schema,
- utworzenie `ToolDefinition` z handlerem wykonania.

`CopilotToolInvocationHandler` obsluguje runtime invocation:

- walidacje session id,
- budget guard przed i po callbacku,
- capture tool evidence,
- klasyfikacje tools dla telemetryki,
- logowanie request/result preview,
- parsowanie wyniku callbacka,
- normalizacje denied budget result w trybie hard.

`CopilotToolContextFactory` buduje hidden `ToolContext` na podstawie
`CopilotToolSessionContext` i invocation.

Decorated descriptions sa guidance dla modelu, nie zmieniaja implementacji
adapterow.

## 10. Tool budget

Budzet jest session-bound:

1. gateway rejestruje session state,
2. invocation handler pyta `CopilotToolBudgetGuard.beforeInvocation(...)`,
3. callback Spring tool jest wykonany albo zwrocony jest kontrolowany denied
   result,
4. invocation handler wywoluje `afterInvocation(...)` z raw result,
5. gateway usuwa state w `finally`.

Domyslny tryb to `soft`: przekroczenia sa logowane i metrykowane, ale nie
blokuja. `hard` zwraca:

```json
{
  "status": "denied_by_tool_budget",
  "toolName": "...",
  "reason": "...",
  "instruction": "Stop further exploration and return the best grounded analysis with visibility limits."
}
```

## 11. Tool evidence capture

`CopilotToolEvidenceCaptureRegistry` publikuje dodatkowe sekcje evidence do
job listenera.

Kategorie:

- `gitlab/tool-fetched-code` dla file/chunk/chunks,
- `database/tool-results` dla DB tools.

GitLab capture jest celowo prosty: user-facing evidence zawiera plik/chunk,
sciezke pliku, tresc kodu, opcjonalny numer linii startowej i `reason` podany
przez model. Search candidates, outline, flow context i class references nie
sa osobnymi sekcjami UI.

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

## 13. Quality gate

Po parsingu provider uruchamia `CopilotResponseQualityGate`.

W trybie domyslnym `REPORT_ONLY` findings sa logowane i podpinane pod
telemetryke, ale nie zmieniaja odpowiedzi zwracanej do uzytkownika.

Quality gate ocenia m.in. glebokosc `affectedFunction`, konkretnosc
`recommendedAction`, ugruntowanie ownership/process/context, spojnosci
confidence oraz strukture rationale.

## 14. Telemetry

Metryki sesji sa trzymane w `CopilotSessionMetricsRegistry` i logowane przez
`CopilotMetricsLogger`.

Zbierane sa:

- counts evidence sections/items/artifacts,
- artifact total characters,
- prompt characters,
- preparation/client/create session/sendAndWait/total durations,
- tool calls total i wedlug grup,
- drogie GitLab/DB tool counters,
- returned characters,
- structured/fallback/parser state,
- detected problem/confidence,
- quality findings,
- budget warnings i denials.

## 15. Job state i UI

Async flow zapisuje prepared prompt przed wykonaniem AI. Dzieki temu prompt
jest dostepny nawet wtedy, gdy execution failuje.

`toolEvidenceSections` sa osobnym polem job response i moga byc aktualizowane
podczas sesji AI przez listener. UI nie zalezy od typow Copilot SDK.

Publiczny sync request zawiera tylko `correlationId`. Job request UI zawiera
`correlationId` oraz opcjonalne preferencje AI (`model`, `reasoningEffort`).
`gitLabGroup` pochodzi z konfiguracji, a `environment` i `gitLabBranch` sa
wyprowadzane z evidence.

Job response zawiera tez `chatMessages`. UI pokazuje chat dopiero po
`COMPLETED`; dla wiadomosci chatu z `IN_PROGRESS` polluje ten sam endpoint
`GET /analysis/jobs/{analysisId}`. Importowany eksport analizy jest read-only,
bo lokalny backend nie ma odpowiadajacego mu stanu joba.

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

analysis.ai.copilot.metrics.enabled=true
analysis.ai.copilot.metrics.log-summary=true
analysis.ai.copilot.metrics.log-tool-events=true

analysis.ai.copilot.quality-gate.enabled=true
analysis.ai.copilot.quality-gate.mode=report-only

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
