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

Orchestrator woa osobno:

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
- wymuszenie dokladnej listy pol wyjsciowych.

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

- skill zrodlowy lezy w `src/main/resources/copilot/skills`,
- loader wypakowuje classpath resource do runtime directory,
- runtime directory jest cache'owany po pierwszym resolve,
- mozna dopiac dodatkowe `skillDirectories` z filesystemu,
- mozna wylaczyc wybrane skille przez `disabledSkills`.

Aktualny skill:

- `incident-analysis-gitlab-tools`

Skill robi kilka waznych rzeczy dobrze:

- ustawia evidence-first workflow,
- wymusza czytanie manifestu,
- promuje chunk-first reading z GitLaba,
- rozdziela confirmed facts od hypothesis,
- wymusza pole `affectedFunction`,
- pilnuje, zeby model tlumaczyl szerszy flow, a nie tylko lokalna linie bledu.

## Tool bridge

Klasa:

- `CopilotSdkToolBridge`

Model:

1. Spring `@Tool` sa rejestrowane przez `ToolCallbackProvider`,
2. bridge zbiera callbacki po nazwie,
3. mapuje je do Copilot `ToolDefinition`,
4. przy wywolaniu serializuje args do JSON,
5. odpala oryginalny Spring callback,
6. loguje request i preview resultu.

Wazny detal:

bridge uzywa `ToolDefinition.createSkipPermission(...)`.

To oznacza, ze permission handling dla tych tooli jest de facto pomijany na
poziomie definicji toola, nawet jesli sesja ma ustawiony handler permissions.

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

## Response parsing

Klasa:

- `CopilotSdkAnalysisAiProvider`

Parser:

- czyta labeled fields linia po linii,
- toleruje markdown wrappers typu `**summary:**`,
- normalizuje starszy format z pipe separators,
- wymaga:
  - `detectedProblem`
  - `summary`
  - `recommendedAction`
  - `affectedFunction`
- `rationale` jest optional.

To jest rozsadne, ale nadal oparte o parse tekstu, nie o twardy JSON schema
response.

## Obserwowalnosc

Aktualnie sa trzy poziomy logowania:

1. lifecycle klienta Copilota,
2. eventy sesji,
3. request/result tool invocations.

Dodatkowo provider AI loguje:

- czy odpowiedz byla structured,
- jakie pola parser rozpoznal,
- jaki byl `detectedProblem`,
- czy `affectedFunction` bylo obecne.

## Co jest dzisiaj dobre

- mocny podzial preparation / execution / tools,
- skill jako runtime resource,
- attachments zamiast dumpowania calego evidence do promptu,
- zachowywanie prepared prompt dla UI i debugowania,
- reuse Spring tools zamiast duplikacji implementacji,
- sensowne testy pokrywajace prompt, parser, bridge i skill loader.

## Co jest dzisiaj ograniczeniem

### 1. Prompt budowany jest dwa razy

Przed analiza:

- raz przez `preparePrompt(...)`,
- drugi raz wewnatrz `prepare(...)`.

To nie jest blad funkcjonalny, ale to oznacza:

- powtorna budowe tool list,
- powtorne opisy attachments,
- duplikacje pracy preparation layer.

### 2. Tools maja za szeroki kontrakt kontekstowy

GitLab tools przyjmuja `group` i `branch` jako parametry.
Jednoczesnie prompt i skill mowia modelowi, zeby tych wartosci nie zmienial.

To zostawia modelowi zbedna swobode i zwieksza ryzyko:

- pomylki,
- readow z niewlasciwej galezi,
- kosztownej eksploracji poza docelowym kontekstem.

### 3. Parser nadal bazuje na tekstowym formacie

To zwieksza ryzyko `AI_UNSTRUCTURED_RESPONSE`.

### 4. Brakuje session-level telemetry dla kosztu eksploracji

Projekt loguje eventy i tool calls, ale nie buduje jeszcze uporzadkowanego
zestawu metryk typu:

- ile razy uzyto tooli,
- ile plikow przeczytano,
- jaki byl czas przygotowania,
- jaki byl czas AI,
- jaki byl rozmiar attachment artifacts,
- ile bylo sekcji evidence i itemow.

### 5. Brakuje jawnego exploration budget

Skill instruuje model, zeby byl oszczedny, ale backend nie ma jeszcze twardych
ograniczen sesji typu:

- max tool calls,
- max read file calls,
- max total bytes/chars z GitLaba.

### 6. Permission model jest dzisiaj bardzo liberalny

Domyslnie:

- `permissionMode = APPROVE_ALL`
- tool definitions skipuja permission requests

To upraszcza runtime, ale moze byc zbyt liberalne w twardszym srodowisku.

### 7. Dynatrace jest tylko attachmentem, nie runtime capability

To jest swiadoma decyzja, ale ogranicza mozliwosc dopytania modelu o dodatkowe
sygnaly runtime podczas sesji.

## Najwazniejsze klasy dla optymalizacji Copilota

- `CopilotSdkPreparationService`
- `CopilotAttachmentArtifactService`
- `CopilotSkillRuntimeLoader`
- `CopilotSdkToolBridge`
- `CopilotSdkExecutionGateway`
- `CopilotSdkAnalysisAiProvider`
- `ElasticMcpTools`
- `GitLabMcpTools`
- `GitLabDeterministicEvidenceProvider`
- `SKILL.md`
