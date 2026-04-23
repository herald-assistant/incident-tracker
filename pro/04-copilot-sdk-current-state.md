# Copilot SDK Current State

## Glowny obraz

Warstwa AI jest dzisiaj zorganizowana poprawnie modulowo i ma wyrazna granice:

- `AnalysisOrchestrator` zna tylko `AnalysisAiProvider`,
- implementacja runtime to `CopilotSdkAnalysisAiProvider`,
- szczegoly SDK sa zamkniete w `analysis.ai.copilot.*`.

To jest mocny punkt obecnej architektury.

## Aktualny flow Copilot SDK

### 1. Orchestrator buduje request do AI

Po evidence collection powstaje `AnalysisAiAnalysisRequest` z:

- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`
- `evidenceSections`

### 2. `preparePrompt(...)`

Orchestrator wola osobno:

- `analysisAiProvider.preparePrompt(aiRequest)`

Ten wynik sluzy glownie do debugowania i job snapshotu.

### 3. `analyze(...)`

Potem provider AI robi juz pelne:

- preparation
- execution
- parsing odpowiedzi

## Preparation layer

Klasa glowna:

- `CopilotSdkPreparationService`

Odpowiada za:

- zbudowanie `CopilotClientOptions`
- zbudowanie `SessionConfig`
- zbudowanie `MessageOptions`
- zbudowanie promptu
- dolaczenie `ToolDefinition`
- dolaczenie skill directories
- dolaczenie attachment artifacts
- zbudowanie `CopilotToolSessionContext`
- ustawienie jawnego `SessionConfig.sessionId`

To oznacza, ze backend zna session id zanim wywola runtime Copilota.

## Prompt

Prompt jest request-specific i zawiera:

- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`
- instrukcje, zeby czytac attachments jako primary source of truth,
- instrukcje, zeby najpierw czytac `00-incident-manifest.json`,
- instrukcje o tym, kiedy wolno uzyc tools,
- instrukcje o rozdzieleniu:
  - confirmed signals,
  - best-supported hypothesis,
  - visibility limits,
  - broader functional context,
  - next action,
- wymuszenie odpowiedzi po polsku,
- wymuszenie dokladnej listy pol wyjsciowych,
- liste capability groups:
  - Elasticsearch logs,
  - GitLab code,
  - Database diagnostics, jesli capability jest wlaczona.

Wazny detal:

prompt nie niesie surowego evidence inline.
Evidence jest przerzucone do attachment artifacts.

## Attachment strategy

Klasa:

- `CopilotAttachmentArtifactService`

Strategia:

1. z requestu AI budowany jest manifest,
2. kazda sekcja evidence jest zapisywana do osobnego pliku JSON,
3. wszystkie pliki sa dolaczane jako attachments do `MessageOptions`.

Powstaja:

- `00-incident-manifest.json`
- `01-*.json`
- `02-*.json`
- ...

Manifest zawiera:

- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`
- `generatedAt`
- `readFirst`
- `artifactPolicy`
- liste artefaktow

To jest bardzo dobra baza do dalszej optymalizacji, bo zmniejsza presje na
gigantyczny prompt i pozwala lepiej separowac role promptu od danych.

## Skill strategy

Klasa:

- `CopilotSkillRuntimeLoader`

Aktualny model:

- skille zrodlowe leza w `src/main/resources/copilot/skills`,
- loader wypakowuje classpath resources do runtime directory,
- runtime directory jest cache'owany po pierwszym resolve,
- mozna dopiac dodatkowe `skillDirectories` z filesystemu,
- mozna wylaczyc wybrane skille przez `disabledSkills`.

Aktualny zestaw skilli:

- `incident-analysis-core`
- `incident-analysis-gitlab-tools`
- `incident-data-diagnostics`

To jest juz sensowny podzial:

- core diagnozy i styl odpowiedzi,
- eksploracja GitLaba,
- diagnostyka danych i DB capability.

## Tool bridge

Klasa:

- `CopilotSdkToolBridge`

Model:

1. Spring `@Tool` sa rejestrowane przez `ToolCallbackProvider`,
2. bridge zbiera callbacki po nazwie,
3. mapuje je do Copilot `ToolDefinition`,
4. przy wywolaniu serializuje args do JSON,
5. buduje hidden Spring `ToolContext`,
6. odpala oryginalny Spring callback przez `call(inputJson, toolContext)`,
7. loguje request i preview resultu,
8. waliduje `sessionId` miedzy backendowym kontekstem i realna sesja SDK.

W hidden `ToolContext` laduja m.in.:

- `analysisRunId`
- `copilotSessionId`
- `actualCopilotSessionId`
- `toolCallId`
- `toolName`
- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`

Wazny detal:

bridge nadal uzywa `ToolDefinition.createSkipPermission(...)`.

To oznacza, ze permission handling dla tych tooli jest de facto pomijany na
poziomie definicji toola, nawet jesli sesja ma ustawiony handler permissions.

## Session-bound tools

Aktualny stan:

- GitLab tools sa juz session-bound,
- Database tools sa od startu session-bound,
- model nie podaje `gitLabGroup`, `gitLabBranch`, `correlationId` ani
  `environment` do tych capability.

To zmniejsza przestrzen bledow i pozwala utrzymac lepszy audit trail po
`analysisRunId`, `copilotSessionId` i `toolCallId`.

## Execution layer

Klasa:

- `CopilotSdkExecutionGateway`

Aktualne zachowanie:

- tworzy `CopilotClient`,
- startuje klienta,
- tworzy sesje,
- podpina lifecycle logger i session event logger,
- wysyla `sendAndWait(...)`,
- czeka do timeoutu z properties,
- zamyka klienta i temp artifacts.

Parametry runtime:

- streaming jest `false`,
- timeout domyslnie `5 minut`,
- auth:
  - logged-in user, jesli brak PAT,
  - PAT, jesli `github-token` jest ustawiony.

W job flow execution dodatkowo rejestruje listener do
`CopilotToolEvidenceCaptureRegistry`, ale tylko dla wybranych GitLab read tools.

## Response parsing

Klasa:

- `CopilotSdkAnalysisAiProvider`

Parser:

- czyta labeled fields linia po linii,
- toleruje markdown wrappers typu `**summary:**`,
- normalizuje starszy format z pipe separators,
- oczekuje pol:
  - `detectedProblem`
  - `summary`
  - `recommendedAction`
  - `rationale`
  - `affectedFunction`
  - `affectedProcess`
  - `affectedBoundedContext`
  - `affectedTeam`
- twardo wymaga:
  - `detectedProblem`
  - `summary`
  - `recommendedAction`
  - `affectedFunction`

To jest rozsadne, ale nadal oparte o parse tekstu, nie o twardy JSON schema
response.

## Obserwowalnosc

Aktualnie sa cztery poziomy logowania:

1. lifecycle klienta Copilota,
2. eventy sesji,
3. request/result tool invocations w bridge,
4. request/result samych MCP tools.

Dodatkowo provider AI loguje:

- czy odpowiedz byla structured,
- jakie pola parser rozpoznal,
- jaki byl `detectedProblem`,
- czy `affectedFunction`, `affectedProcess`, `affectedBoundedContext` i
  `affectedTeam` byly obecne.

## Co jest dzisiaj dobre

- mocny podzial preparation / execution / tools,
- jawny backendowy `sessionId`,
- hidden `ToolContext` dla session-bound capability,
- skille jako runtime resources,
- attachments zamiast dumpowania calego evidence do promptu,
- zachowywanie prepared prompt dla UI i debugowania,
- reuse Spring tools zamiast duplikacji implementacji,
- sensowne testy pokrywajace prompt, parser, bridge, tool schemas i skill
  loader.

## Co jest dzisiaj ograniczeniem

### 1. Prompt budowany jest dwa razy

Przed analiza:

- raz przez `preparePrompt(...)`,
- drugi raz wewnatrz `prepare(...)`.

To nie jest blad funkcjonalny, ale to oznacza:

- powtorna budowe tool list,
- powtorne opisy attachments,
- duplikacje pracy preparation layer.

### 2. Parser nadal bazuje na tekstowym formacie

To zwieksza ryzyko `AI_UNSTRUCTURED_RESPONSE`.

### 3. Brakuje session-level telemetry dla kosztu eksploracji

Projekt loguje eventy i tool calls, ale nie buduje jeszcze uporzadkowanego
zestawu metryk typu:

- ile razy uzyto tooli,
- ile plikow przeczytano,
- ile zapytan DB wykonano,
- jaki byl czas przygotowania,
- jaki byl czas AI,
- jaki byl rozmiar attachment artifacts,
- ile bylo sekcji evidence i itemow.

### 4. Brakuje jawnego exploration budget

Skill instruuje model, zeby byl oszczedny, ale backend nie ma jeszcze twardych
ograniczen sesji typu:

- max tool calls,
- max total chars z GitLaba,
- max query count albo max result size dla DB,
- max read file calls.

### 5. Permission model jest dzisiaj bardzo liberalny

Domyslnie:

- `permissionMode = APPROVE_ALL`
- tool definitions skipuja permission requests

To upraszcza runtime, ale moze byc zbyt liberalne w twardszym srodowisku.

### 6. Dynatrace jest tylko attachmentem, nie runtime capability

To jest swiadoma decyzja, ale ogranicza mozliwosc dopytania modelu o dodatkowe
sygnaly runtime podczas sesji.

### 7. Tool evidence capture jest waskie

W job flow do `toolEvidenceSections` trafiaja tylko wybrane GitLab read tools.

To oznacza, ze:

- GitLab search/outline i DB tools sa logowane,
- ale nie sa jeszcze projektowane do osobnych sekcji evidence dla UI.

## Najwazniejsze klasy dla optymalizacji Copilota

- `CopilotSdkPreparationService`
- `CopilotAttachmentArtifactService`
- `CopilotSkillRuntimeLoader`
- `CopilotSdkToolBridge`
- `CopilotSdkExecutionGateway`
- `CopilotSdkAnalysisAiProvider`
- `CopilotToolEvidenceCaptureRegistry`
- `ElasticMcpTools`
- `GitLabMcpTools`
- `DatabaseMcpTools`
- `DatabaseToolService`
- `GitLabDeterministicEvidenceProvider`
- `SKILL.md`
