# Change Verification - Implementation Plan

## Cel dokumentu

Ten dokument opisuje plan techniczny dla feature'u `Change Verification`,
wynikajacy z potrzeby biznesowej opisanej w
`12-change-verification-business-need.md`.

Plan zaklada, ze `Change Verification` bedzie osobnym feature'em platformy, a
nie rozszerzeniem `Incident Analysis` ani `Flow Explorer`. Feature ma
reuse'owac obecne warstwy `aiplatform`, `agenttools`, `integrations`,
`shared` i wspolne komponenty UI, ale ma miec wlasny request, job state,
prompt, runtime skille, tool policy, result contract i widok Angular.

## Wnioski z obecnej implementacji

### Co mozemy reuse'owac

Obecna architektura jest juz przygotowana na trzeci feature:

- `features.flowexplorer` pokazuje aktualny wzorzec pionowego feature'u:
  feature-specific job API pod `/api/flow-explorer/jobs`, job state,
  deterministic context, prompt preparation, Copilot run assembler,
  report-first result, local workspace, follow-up chat i Angular screen.
- `aiplatform.copilot.runtime` przyjmuje neutralny `CopilotRunRequest`:
  prompt, auth, target sesji, session config, artifacts, initial report oraz
  sinks dla evidence/activity. To jest wlasciwa granica dla nowego feature'u.
- `aiplatform.copilot.tools` daje wspolna mechanike rejestracji tools,
  hidden `ToolContext`, policies, budget, activity events, report tools,
  feedback tool i session-bound evidence store.
- `shared.ai.report.AnalysisReport` jest juz kanonicznym sposobem zwrotu
  wyniku przez AI. `Change Verification` powinien uzyc report-first result
  tak jak Incident Analysis i Flow Explorer.
- `shared.ai` i `shared.evidence` niosa wspolne modele dla AI activity,
  usage, tool feedback i evidence sections. Nowy feature powinien je
  wykorzystac zamiast tworzyc lokalne odpowiedniki.
- `integrations.gitlab` ma juz duzo reusable capability do czytania kodu:
  repository search, file read/chunk, endpoint inventory, OpenAPI endpoint
  slice, Java method slice oraz endpoint use-case context.
- `agenttools.gitlab.mcp` wystawia AI narzedzia nad tymi capability:
  `gitlab_list_available_repositories`,
  `gitlab_list_repository_endpoints`,
  `gitlab_build_endpoint_use_case_context`,
  `gitlab_read_repository_file*`,
  `gitlab_read_java_method_slice`,
  `gitlab_read_openapi_endpoint_slice` i inne.
- `integrations.database` ma readonly DB capability z typed queries, SQL
  guardem, maskingiem i limitami wynikow.
- Angular ma wspolne komponenty dla przebiegu pracy, AI timeline,
  report/meta, follow-up chat, usage/cost i evidence paneli. Nowy ekran
  powinien mapowac swoje dane do tych wzorcow.

### Czego brakuje

`Change Verification` wymaga capability, ktorych dzisiaj nie ma:

- integracji Jira do pobrania issue, opisu, acceptance criteria, komentarzy,
  linkow i relacji do MR-ow,
- integracji Confluence do pobrania stron podlinkowanych ze story,
- rozszerzenia GitLaba o merge requesty: search po Jira key, MR metadata,
  changed files, diff, commits i linki do branchy,
- discovery instrukcji repozytorium: globalne i lokalne `AGENTS.md`,
  Copilot instructions oraz pliki wskazane przez te instrukcje,
- modelu smoke packa i generatora eksportu Postman collection/environment,
- controlled HTTP execution dla zaakceptowanych testow,
- neutralnego sposobu wykonywania readonly DB assertions bez incidentowego
  `correlationId`,
- cleanup policy: allowlisted HTTP cleanup endpointy oraz manual cleanup SQL
  tylko jako artefakt dla czlowieka.

### Istotne ograniczenia obecnego kodu

- Obecne DB MCP tools sa opisane i scoped pod incident flow. Hidden scope
  wymaga `correlationId` i `environment`. Dla Change Verification nie wolno
  kopiowac tego wzorca jako nowego model-facing albo feature-facing scope'u.
  Trzeba wydzielic neutralny DB execution scope albo adaptowac
  `integrations.database` tak, zeby correlationId byl opcjonalnym kontekstem
  audytu, nie wymogiem capability.
- `agenttools.*` nie moze importowac `features.*` ani `aiplatform.*`.
  Nowe MCP tools dla Jira, Confluence, MR-ow, instructions albo smoke
  execution musza mieszkac w `agenttools.<capability>.mcp` i delegowac do
  `integrations.*`.
- `integrations.*` nie moze znac feature'a. Jira, Confluence, GitLab MR,
  instructions discovery i HTTP execution musza byc czystymi capability.
- Nowy feature nie moze importowac `features.incidentanalysis` ani
  `features.flowexplorer`. Reuse moze isc przez shared/platform/tools albo
  przez przeniesienie stabilnego kontraktu do `shared`.
- Runtime skille Copilota pakowane w `src/main/resources/copilot/skills`
  powinny byc po polsku, z zachowaniem technicznych identyfikatorow.

## Docelowa architektura feature'u

Docelowy runtime ownership:

```text
frontend Change Verification screen
  -> features.changeverification.job.api
  -> features.changeverification.job
  -> features.changeverification.context/source gathering
  -> integrations.jira / integrations.confluence / integrations.gitlab
  -> features.changeverification.ai.copilot
  -> aiplatform.copilot.runtime + aiplatform.copilot.tools
  -> agenttools.*.mcp
  -> integrations.*
```

Docelowe pakiety:

```text
pl.mkn.tdw.features.changeverification
pl.mkn.tdw.features.changeverification.job
pl.mkn.tdw.features.changeverification.job.api
pl.mkn.tdw.features.changeverification.job.state
pl.mkn.tdw.features.changeverification.job.localworkspace
pl.mkn.tdw.features.changeverification.context
pl.mkn.tdw.features.changeverification.ai
pl.mkn.tdw.features.changeverification.ai.copilot
pl.mkn.tdw.features.changeverification.ai.copilot.preparation
pl.mkn.tdw.features.changeverification.ai.copilot.report
pl.mkn.tdw.features.changeverification.ai.copilot.tools.description
pl.mkn.tdw.features.changeverification.smoke
pl.mkn.tdw.features.changeverification.execution

pl.mkn.tdw.integrations.jira
pl.mkn.tdw.integrations.confluence
pl.mkn.tdw.integrations.gitlab.instructions
pl.mkn.tdw.integrations.httpexecution

pl.mkn.tdw.agenttools.jira
pl.mkn.tdw.agenttools.jira.mcp
pl.mkn.tdw.agenttools.confluence
pl.mkn.tdw.agenttools.confluence.mcp
pl.mkn.tdw.agenttools.instructions
pl.mkn.tdw.agenttools.instructions.mcp
pl.mkn.tdw.agenttools.smoke
pl.mkn.tdw.agenttools.smoke.mcp
```

GitLab MR support powinien wejsc do istniejacego
`integrations.gitlab` / `agenttools.gitlab`, bo jest rozszerzeniem tej samej
integracji.

## Publiczne API feature'u

Proponowane endpointy feature-specific:

```http
POST /api/change-verification/jobs
GET  /api/change-verification/jobs/{jobId}
POST /api/change-verification/jobs/{jobId}/chat/messages
POST /api/change-verification/jobs/{jobId}/smoke-pack
PUT  /api/change-verification/jobs/{jobId}/smoke-pack
GET  /api/change-verification/jobs/{jobId}/postman/collection
GET  /api/change-verification/jobs/{jobId}/postman/environment
POST /api/change-verification/jobs/{jobId}/executions
GET  /api/change-verification/jobs/{jobId}/executions/{executionId}
```

Pierwszy request:

```json
{
  "source": {
    "jiraUrl": "https://jira.example/browse/ABC-123",
    "jiraKey": "ABC-123",
    "mergeRequestUrls": []
  },
  "mode": "VERIFY_CHANGE",
  "verificationOptions": {
    "storyCompliance": true,
    "instructionCompliance": true,
    "generateSmokePack": true,
    "runSmokePack": false
  },
  "gitRef": "target branch albo source branch, opcjonalnie",
  "environment": "uat, opcjonalnie dopiero dla execution",
  "model": "opcjonalnie",
  "reasoningEffort": "opcjonalnie"
}
```

Uwagi:

- `jiraUrl` albo `jiraKey` jest podstawowym wejściem MVP.
- Bezposredni MR link powinien byc wspierany jako fallback albo tryb bez Jiry,
  ale nie powinien zastapic Jira-first flow.
- `environment` nie jest wymagane dla compliance ani smoke pack generation.
  Jest wymagane dopiero dla execution/DB assertions.
- Sekrety auth do wykonywania HTTP requestow nie ida w prompt ani publiczny
  payload AI. Musza pochodzic z backendowej konfiguracji albo jawnego
  operator-facing secret ref.

## Model joba i statusy

Proponowane kroki job state:

| Step code | Faza | Znaczenie |
| --- | --- | --- |
| `SOURCE_DISCOVERY` | CONTEXT | Jira, linked docs, MR discovery, MR diff |
| `INSTRUCTION_CONTEXT` | CONTEXT | globalne/lokalne AGENTS, Copilot instructions, linked instruction files |
| `CODE_RECONNAISSANCE` | CONTEXT | endpointy, DTO, testy, changed files, source slices |
| `AI_VERIFICATION` | AI | Story Compliance i Instruction Compliance |
| `SMOKE_PACK_GENERATION` | AI | draft smoke packa i Postman export |
| `SMOKE_PACK_REVIEW` | USER | stan oczekiwania na akceptacje/edycje |
| `EXECUTION` | EXECUTION | HTTP requesty, assertions, readonly DB checks |
| `CLEANUP` | EXECUTION | cleanup endpoint, readonly confirmation albo manual SQL |

Glowny status joba:

```text
QUEUED
COLLECTING_CONTEXT
ANALYZING
SMOKE_PACK_READY
WAITING_FOR_APPROVAL
EXECUTING
COMPLETED
FAILED
```

Statusy rezultatu merytorycznego powinny byc zgodne z dokumentem biznesowym:
`Compliant`, `Discrepancies found`, `Insufficient story`,
`Instructions incomplete`, `Smoke pack generated`, `Ready to run`, `Passed`,
`Failed`, `Cleanup required`, `Cannot verify`.

## Integracje

### 1. Jira

Nowy pakiet:

```text
integrations.jira
```

Zakres adaptera:

- properties: base URL, auth header/token, project allowlist, response limits,
- `JiraIssuePort`,
- REST adapter do Jira issue API,
- modele:
  - `JiraIssue`,
  - `JiraIssueLink`,
  - `JiraIssueComment`,
  - `JiraIssueRemoteLink`,
  - `JiraAcceptanceCriterion`,
  - `JiraIssueMaterial`.

Minimalne capability:

- pobranie issue po key,
- normalizacja opisu i komentarzy,
- ekstrakcja acceptance criteria best-effort,
- ekstrakcja linkow do Confluence,
- ekstrakcja linkow do GitLaba/MR-ow,
- zwrot visibility limits, gdy pola albo linki sa niedostepne.

Shared/operator API nie jest wymagane w MVP, chyba ze potrzebny bedzie
Workbench do testowania polaczenia. Stabilny helper moze pozniej trafic do
`api.jira`.

### 2. Confluence

Nowy pakiet:

```text
integrations.confluence
```

Zakres adaptera:

- properties: base URL, auth, allowed spaces/domains, page size/content limit,
- `ConfluenceContentPort`,
- REST adapter do odczytu strony,
- modele:
  - `ConfluencePage`,
  - `ConfluencePageExcerpt`,
  - `ConfluenceContentReference`.

Minimalne capability:

- pobranie strony po URL albo page id,
- konwersja storage/html do czystego markdown/plain text,
- limit i sanitizacja tresci,
- ignorowanie linkow poza allowlista,
- visibility limit, gdy strona jest niedostepna.

Confluence jest opcjonalnym wzbogaceniem MVP. Brak dostepu nie powinien
blokowac verification, tylko oznaczac `visibilityLimits`.

### 3. GitLab MR

Rozszerzyc:

```text
integrations.gitlab
```

Nowe capability:

- `GitLabMergeRequestPort`,
- `GitLabMergeRequestService`,
- modele:
  - `GitLabMergeRequest`,
  - `GitLabMergeRequestDiff`,
  - `GitLabMergeRequestChangedFile`,
  - `GitLabMergeRequestCommit`,
  - `GitLabMergeRequestReference`.

Minimalne operacje:

- znalezienie MR-ow po Jira key w tytule, branchu albo commitach,
- pobranie MR po URL/project/iid,
- pobranie changed files i diff summary,
- pobranie source branch, target branch, statusu, autora, linku,
- opcjonalnie pobranie commits dla MR.

GitLab MR discovery ma dwa tryby:

1. MR-y podlinkowane w Jira albo Confluence.
2. Fallback search po Jira key zgodnie z konwencja organizacji.

Istniejace GitLab code tools pozostaja zrodlem source reads. Nowe MR
capability ma dostarczyc zakres zmiany: ktore repo, branch, pliki i diff.

### 4. Instructions

Pakiet:

```text
integrations.gitlab.instructions
```

To nie jest osobny zewnetrzny system, tylko czesc GitLab code source capability
odpowiedzialna za repozytoryjne instrukcje migrujace razem z kodem,
endpointami, use case'ami i plikami. Adapter nie powinien importowac feature'a.

Zakres:

- `InstructionContextService`,
- `InstructionSourceResolver`,
- `InstructionLinkExtractor`,
- modele:
  - `InstructionContext`,
  - `InstructionSource`,
  - `InstructionRuleCandidate`,
  - `InstructionVisibilityLimit`.

Input:

- changed files z MR-ow,
- GitLab group/project/ref,
- opcjonalne repo-root instruction paths,
- opcjonalne lista dodatkowych standardow.

Reguly discovery:

- root `AGENTS.md` zawsze, jezeli istnieje,
- lokalne `AGENTS.md` od katalogu changed file w gore do root repo,
- `.github/copilot-instructions.md`, jezeli istnieje,
- pliki wskazane przez Copilot instructions, tylko repo-relative i w
  allowlistowanych prefixach,
- wskazane dokumenty architektury albo repo docs, bez glebokiego crawlera.

Wazna granica:

- `InstructionContextService` tylko zbiera i normalizuje zrodla.
- Ocena zgodnosci pozostaje feature-owned w prompt/skillu Change Verification.

### 5. HTTP execution

Nowy pakiet:

```text
integrations.httpexecution
```

Zakres:

- controlled RestClient execution dla zaakceptowanych requestow,
- environment registry: base URLs, auth refs, allowed hosts,
- timeouty, retry policy, response limits,
- request/response capture z maskowaniem sekretow,
- brak AI-driven dowolnego URL-a poza allowlista.

Modele:

- `HttpExecutionEnvironment`,
- `HttpExecutionRequest`,
- `HttpExecutionResponse`,
- `HttpExecutionAssertionResult`,
- `HttpExecutionSafetyViolation`.

Ta integracja nie zna Postmana. Przyjmuje neutralny request/scenario step i
zwraca wynik wykonania.

### 6. Database assertions

`integrations.database` zostaje zrodlem readonly capability, ale trzeba
doprecyzowac neutralny scope dla Change Verification.

Planowany kierunek:

1. Wprowadzic neutralny `DbExecutionScope` albo rozszerzyc obecny
   `DbCapabilityScope` tak, aby `environment` bylo wymagane, a
   `correlationId` bylo opcjonalnym polem audytu.
2. Zostawic incidentowy `DatabaseMcpToolScopeResolver` jako feature/session
   resolver wymagajacy correlationId dla incident flow.
3. Dla Change Verification execution budowac scope z:
   - `environment`,
   - `runReference`,
   - `executionId`,
   - `toolCallId` albo step id,
   - bez correlationId.
4. Wykonywac tylko typed DB assertions z zaakceptowanego smoke packa:
   `existsByKey`, `countRows`, `sampleRows`, `joinCount`, ewentualnie
   readonly SQL tylko jesli globalna polityka na to pozwala.

AI moze proponowac DB assertion, ale runtime wykonuje tylko zaakceptowana,
strukturalna definicje.

## MCP tools

Nowe tools powinny byc neutralne i rejestrowane przez
`ToolCallbackProvider` w `agenttools.<capability>.mcp`.

### Jira tools

Pakiety:

```text
agenttools.jira
agenttools.jira.mcp
```

Nazwy:

```text
jira_get_issue
jira_list_issue_links
jira_get_issue_material
```

Rola:

- pozwolic AI doczytac material story, gdy deterministic source gathering
  jest niekompletne albo uzytkownik pyta w follow-up,
- nie wykonywac zmian w Jira,
- zwracac source refs i visibility limits.

### Confluence tools

Pakiety:

```text
agenttools.confluence
agenttools.confluence.mcp
```

Nazwy:

```text
confluence_get_page
confluence_extract_page_requirements
```

Rola:

- doczytac linked page,
- zwrocic skompresowany material wymagan,
- nie crawlowac dowolnych linkow.

### GitLab MR tools

Rozszerzyc:

```text
agenttools.gitlab
agenttools.gitlab.mcp
```

Nazwy:

```text
gitlab_find_merge_requests_by_issue_key
gitlab_get_merge_request
gitlab_get_merge_request_diff
gitlab_list_merge_request_changed_files
```

Rola:

- uzupelnic MR discovery po Jira key,
- pokazac changed files i diff summary,
- przekazac `projectName`, `sourceBranch`, `targetBranch` i changed file paths
  do istniejacych code read tools.

### Instructions tools

Pakiety:

```text
agenttools.instructions
agenttools.instructions.mcp
```

Nazwy:

```text
instructions_get_context
instructions_read_source
instructions_list_applicable_sources
```

Rola:

- dac AI jawny instruction context,
- pozwolic doczytac konkretne source file wskazane w context,
- nie dawac modelowi lokalnego filesystemu ani shell access.

Tool input nie powinien przyjmowac dowolnej lokalnej sciezki. Scope ma
pochodzic z hidden `ToolContext`: projekty, refy i changed files.

### Smoke tools

Pakiety:

```text
agenttools.smoke
agenttools.smoke.mcp
```

Proponowane nazwy:

```text
smoke_validate_pack
smoke_render_postman_collection
smoke_render_postman_environment
```

Execution requestow nie musi byc narzedziem AI w MVP. Bezpieczniej wykonac go
deterministycznie po akceptacji uzytkownika przez feature-owned service.

AI moze generowac smoke pack przez report/structured output, a backendowy
validator/postman renderer sprawdza i eksportuje artefakt. Tool
`smoke_render_postman_*` ma sens dopiero wtedy, gdy chcemy, aby model mogl
sam zwalidowac ksztalt artefaktu przed finalnym raportem.

## Tool policy Change Verification

Nowy feature powinien miec wlasna policy:

```text
features.changeverification.ai.copilot.preparation.ChangeVerificationCopilotToolAccessPolicy
```

Allowlisty per etap:

### Compliance

- Jira tools,
- Confluence tools,
- GitLab MR tools,
- GitLab read/search/slice tools,
- Instructions tools,
- Operational Context tools, gdy trzeba rozpoznac system/repo/scope,
- report tools,
- feedback tool.

### Smoke pack generation

- GitLab endpoint/OpenAPI/use-case context tools,
- DB discovery tools tylko jako proposal support, bez execution,
- smoke validation/render tools, jezeli beda dodane,
- report tools,
- feedback tool.

### Execution

Execution nie powinien byc autonomicznym AI tool call w MVP. Po akceptacji
smoke packa runtime wykonuje deterministycznie:

- HTTP execution service,
- DB readonly assertions,
- cleanup endpoint execution,
- cleanup confirmation.

AI moze po execution przygotowac interpretacje wyniku i manual cleanup
instructions, ale nie dostaje prawa do samodzielnego odpalania requestow bez
akceptacji.

## Runtime skille Copilota

Nowe skille w:

```text
src/main/resources/copilot/skills
```

Proponowana lista:

```text
change-verification-orchestrator
change-verification-story-compliance
change-verification-instruction-compliance
change-verification-smoke-pack-design
change-verification-execution-result-review
change-verification-follow-up-chat
```

Zasady:

- skille pisane po polsku,
- techniczne identyfikatory zostaja po angielsku,
- skill `change-verification-orchestrator` mowi, kiedy uzyc 1a, 1b, smoke
  pack i execution,
- skill story compliance wymusza macierz requirement -> implementation
  evidence -> assessment -> suggested action,
- skill instruction compliance wymusza source-backed findings i zakaz
  violation bez source,
- skill smoke pack design wymusza Postman-ready, editable, minimal smoke, a
  nie pelna regresje,
- skill execution result review wymusza bezpieczna interpretacje failed tests
  i cleanup required.

## Result contract

`Change Verification` powinien uzyc report-first result. Proponowane sekcje
raportu:

```text
REQUIREMENT_COMPLIANCE
INSTRUCTION_COMPLIANCE
DISCREPANCIES
SUGGESTED_ACTIONS
SMOKE_PACK
EXECUTION_RESULT
CLEANUP
VISIBILITY_LIMITS
```

Feature-specific DTO moze mapowac report na strukturalny wynik:

```text
ChangeVerificationResult
- status
- jiraKey
- mode
- complianceSummary
- requirementFindings[]
- instructionFindings[]
- discrepancies[]
- suggestedActions[]
- smokePack
- postmanArtifacts
- executionSummary
- cleanupSummary
- visibilityLimits[]
- prompt
- usage
```

Finding:

```text
ChangeVerificationFinding
- id
- type: STORY | INSTRUCTION | SMOKE | EXECUTION | CLEANUP
- severity: BLOCKER | HIGH | MEDIUM | LOW | INFO
- assessment
- sourceRefs[]
- implementationEvidence[]
- suggestedAction
- requiresHumanDecision
```

Smoke pack:

```text
SmokePack
- version
- source
- variables[]
- scenarios[]
- postmanCollectionJson
- postmanEnvironmentJson
- assumptions[]
- missingInputs[]
```

Scenario:

```text
SmokeScenario
- id
- name
- sourceRequirementRefs[]
- steps[]
- cleanup
```

Steps:

```text
HttpStep
DbAssertionStep
ManualStep
```

## Smoke pack i Postman export

Smoke pack powinien miec wewnetrzny strukturalny format niezalezny od
Postmana. Postman collection jest eksportem.

Powody:

- execution backendu nie powinien parsowac Postmana jako source of truth,
- UI potrzebuje edytowac scenariusze w domenowym modelu,
- Postman ma byc kompatybilnym artefaktem dla ludzi,
- backend moze latwiej walidowac allowlisty URL, auth refs, DB assertions i
  cleanup policy.

Generator Postmana:

```text
features.changeverification.smoke.PostmanCollectionRenderer
features.changeverification.smoke.PostmanEnvironmentRenderer
```

W MVP Postman export powinien obslugiwac:

- collection v2.1,
- variables,
- request method/path/body/headers,
- test scripts dla status code i prostych JSONPath-like assertions,
- pre-request script tylko dla prostego przekazania ID miedzy krokami.

## Execution i cleanup

Execution powinien byc osobnym krokiem po akceptacji uzytkownika.

Wymagane guardraile:

- request musi pochodzic z zaakceptowanego smoke packa,
- environment musi miec skonfigurowane allowed base URLs,
- auth id/secret ref musi byc rozwiazany po stronie backendu,
- request body i headers z sekretami sa maskowane w wyniku,
- DB assertions sa readonly i strukturalne,
- cleanup przez HTTP wymaga allowlisty endpointow albo regexow,
- manual cleanup SQL nigdy nie jest wykonywany przez platforme.

Execution result:

```text
SmokeExecutionResult
- executionId
- status: PASSED | FAILED | CLEANUP_REQUIRED | CANNOT_RUN
- environment
- startedAt/completedAt
- stepResults[]
- dbEvidence[]
- cleanupResult
- manualCleanupSql
- visibilityLimits[]
```

Manual cleanup SQL:

- generowany jako artefakt tekstowy,
- zawiera bind placeholders albo komentarze dla wartosci,
- pokazuje zrodlo wartosci, np. response field albo DB evidence,
- ma ostrzezenie, ze uruchamia go czlowiek w organizacyjnym procesie,
- nie jest przekazywany do DB clienta.

## Frontend UI

Nowy route:

```text
/change-verification
```

Route metadata:

```text
section: Analysis Features
title: Change Verification
```

Widok powinien byc roboczy, podobny gestoscia do Flow Explorera, bez landing
page i bez hero.

### Layout

Proponowany uklad:

- lewy panel startu:
  - Jira key/link,
  - opcjonalny MR link,
  - mode selector,
  - checkboxes: Story Compliance, Instruction Compliance, Generate Smoke Pack,
    Run Smoke Pack,
  - model/reasoning effort,
  - environment tylko dla execution,
  - primary action `Verify change`.
- glowna przestrzen:
  - run context bar,
  - compliance tabs albo segmented view:
    `Story`, `Instructions`, `Smoke Pack`, `Execution`, `Activity`,
  - findings table jako najwazniejszy element,
  - smoke pack editor/export,
  - execution result/cleanup panel.
- prawa/pomocnicza przestrzen tylko jezeli nie zagesci widoku:
  - source materials,
  - visibility limits,
  - selected finding details.

### Reuse komponentow

Reuse:

- `AnalysisStepsPanelComponent` dla krokow i AI activity,
- `AnalysisReportMetaComponent`,
- `AnalysisReportSectionContentComponent`,
- `AnalysisFollowUpChatComponent`,
- wspolne modele z `core/models/analysis.models.ts`,
- utils import/export z wzorca Flow Explorera, ale bez kopiowania tam, gdzie
  lepszy jest shared helper.

Nowe komponenty feature-local:

```text
features/change-verification/pages/change-verification-page
features/change-verification/components/change-verification-findings-table
features/change-verification/components/smoke-pack-editor
features/change-verification/components/smoke-execution-result
features/change-verification/services/change-verification-api.service.ts
features/change-verification/models/change-verification.models.ts
```

### UX akceptacji

`Run Smoke Pack` powinien byc disabled, dopoki:

- smoke pack nie zostal wygenerowany,
- brakuje wymaganych variables,
- uzytkownik nie zaakceptowal scenariuszy,
- environment nie jest wybrane,
- execution guardrails nie potwierdza allowed base URL/cleanup endpoints.

Cleanup required musi byc widocznym stanem, nie ukrytym warningiem.

## Local workspace i export

Tak jak Flow Explorer, nowy feature powinien zapisywac snapshot runu w local
workspace.

Feature id:

```text
change-verification
```

Export powinien miec dwa poziomy docelowe:

- user-facing export: findings, smoke pack, postman artifacts, execution
  summary, cleanup instructions, bez pelnego promptu i surowego kodu,
- diagnostic/local snapshot: prompt, artifacts, source materials, activity,
  tool evidence i technical details.

W MVP mozna zaczac od jednego export envelope, ale trzeba od razu oznaczyc
sekcje diagnostyczne, zeby pozniej latwo rozdzielic eksporty.

## Plan implementacji etapami

### Etap 0 - kontrakty i granice

Cel: przygotowac kontrakty bez runtime execution.

Prace:

- dodac `features.changeverification/AGENTS.md`,
- dodac szkielety DTO:
  - job start request,
  - job state snapshot,
  - result contract,
  - finding,
  - source material,
  - smoke pack,
  - execution result,
- dodac route Angular placeholder `/change-verification`,
- dodac PackageDependencyGuardTest rules:
  - `features.changeverification` nie importuje `features.incidentanalysis`,
  - `features.changeverification` nie importuje `features.flowexplorer`,
  - reusable warstwy nie importuja `features.changeverification`.

Done:

- projekt sie kompiluje,
- route jest widoczny w sidebarze jako Analysis Feature,
- nie ma jeszcze zewnetrznych calli.

### Etap 1 - Jira + GitLab MR discovery

Cel: z Jira key zbudowac deterministic source bundle.

Prace:

- dodac `integrations.jira`,
- rozszerzyc `integrations.gitlab` o MR API,
- dodac fallback search MR po Jira key,
- dodac source bundle w `features.changeverification.context`:
  `ChangeVerificationSourceSnapshot`,
- dodac evidence sections dla:
  - Jira material,
  - linked docs,
  - merge requests,
  - changed files,
  - visibility limits.

MCP:

- `jira_get_issue`,
- `jira_get_issue_material`,
- `gitlab_find_merge_requests_by_issue_key`,
- `gitlab_get_merge_request`,
- `gitlab_list_merge_request_changed_files`.

Testy:

- adaptery REST przez `MockRestServiceServer`,
- MR fallback regex,
- job source discovery bez AI.

### Etap 2 - Instruction context discovery

Cel: z changed files zbudowac source-backed instruction context.

Prace:

- dodac `integrations.gitlab.instructions`,
- odczyt `AGENTS.md`, lokalnych `AGENTS.md`, Copilot instructions i linked
  instruction files przez GitLab file reads,
- ograniczyc followowanie linkow do repo-relative paths i allowlistowanych
  docs,
- dolaczyc instruction context do artifacts/promptu.

MCP:

- `instructions_get_context`,
- `instructions_list_applicable_sources`,
- `instructions_read_source`.

Testy:

- lokalne AGENTS discovery per changed file,
- Copilot instructions linked files,
- brak pliku jako visibility limit, nie blad calego joba.

### Etap 3 - Check Compliance AI

Cel: pierwszy uzyteczny MVP bez smoke execution.

Prace:

- dodac `features.changeverification.ai.copilot`,
- dodac skille:
  - `change-verification-orchestrator`,
  - `change-verification-story-compliance`,
  - `change-verification-instruction-compliance`,
  - `change-verification-follow-up-chat`,
- dodac report factory i mapper dla sekcji:
  - `REQUIREMENT_COMPLIANCE`,
  - `INSTRUCTION_COMPLIANCE`,
  - `DISCREPANCIES`,
  - `SUGGESTED_ACTIONS`,
  - `VISIBILITY_LIMITS`,
- dodac prompt preparation z artifacts:
  - Jira material,
  - Confluence excerpts,
  - MR list/diff summary,
  - changed files,
  - instruction context,
  - code reconnaissance summary,
- dodac tool policy dla compliance,
- dodac UI findings table.

Done:

- tryb `Check Compliance` dziala od Jira key,
- wynik pokazuje source-backed findings,
- finding bez source trafia jako suggestion, nie violation.

Testy:

- prompt zawiera wymagane artifacts,
- report mapper mapuje sekcje na DTO,
- policy wlacza tylko dozwolone tools,
- frontend renderuje findings i visibility limits.

### Etap 4 - Smoke Pack generation

Cel: wygenerowac edytowalny smoke pack i eksport Postman.

Prace:

- dodac feature-owned model smoke packa,
- dodac validator smoke packa,
- dodac Postman collection/environment renderer,
- rozszerzyc prompt i skille o `change-verification-smoke-pack-design`,
- dodac sekcje reportu `SMOKE_PACK`,
- dodac endpointy exportu Postman,
- dodac UI smoke pack editor.

MCP opcjonalnie:

- `smoke_validate_pack`,
- `smoke_render_postman_collection`,
- `smoke_render_postman_environment`.

Done:

- tryb `Generate Smoke Pack` dziala nawet przy slabym story, jezeli MR/kod
  pozwala znalezc endpointy,
- uzytkownik moze edytowac variables i scenariusze,
- Postman export jest poprawnym JSON-em.

Testy:

- renderer Postmana snapshot/contract tests,
- walidacja brakujacych variables,
- frontend editor i download actions.

### Etap 5 - Controlled execution bez DB cleanup write

Cel: uruchomic zaakceptowane testy i cleanup endpoint.

Prace:

- dodac `integrations.httpexecution`,
- dodac environment registry i allowlisty base URL,
- dodac execution service w `features.changeverification.execution`,
- dopiac readonly DB assertions przez neutralny DB scope,
- dodac cleanup endpoint policy,
- dodac manual cleanup SQL artifact jako fallback,
- dodac UI execution result i cleanup required state.

Done:

- execution odpala tylko zaakceptowany smoke pack,
- cleanup przez HTTP jest allowlisted,
- DB jest readonly,
- manual SQL nie jest wykonywany przez platforme,
- `Cleanup required` jest widocznym terminalnym/partial-terminal stanem.

Testy:

- HTTP execution allowlist violation,
- secret/header masking,
- DB assertion success/failure,
- cleanup endpoint success/failure,
- manual cleanup SQL generated but never executed.

### Etap 6 - Confluence enrichment

Cel: wzbogacic story compliance o linked docs.

Prace:

- dodac `integrations.confluence`,
- ekstrakcja linked Confluence pages z Jira,
- page excerpt compression,
- visibility limits dla braku dostepu.

MCP:

- `confluence_get_page`,
- `confluence_extract_page_requirements`.

Done:

- brak Confluence nie blokuje joba,
- dostepne strony zasilaja requirement extraction.

Ten etap moze wejsc wczesniej, jezeli material Confluence jest krytyczny dla
MVP w organizacji.

### Etap 7 - biblioteka smoke packow i release reuse

Cel: nie tracic zaakceptowanych scenariuszy.

Prace:

- zapis zaakceptowanych smoke packow per Jira/MR/system,
- ponowne uruchomienie smoke packa bez regeneracji AI,
- lista zapisanych packow w UI,
- opcjonalne grupowanie per release.

To nie jest wymagane dla pierwszego MVP.

## Weryfikacja techniczna

Backend:

- `mvn -q -Dtest=PackageDependencyGuardTest test`,
- testy adapterow Jira/Confluence/GitLab MR z `MockRestServiceServer`,
- testy MCP context/registration dla nowych tools,
- testy Change Verification job service i controller przez MockMvc,
- testy prompt preparation i report mapper,
- testy smoke pack validator/Postman renderer,
- testy HTTP execution i cleanup policy.

Frontend:

- test route/nav w `app.spec.ts`,
- test `change-verification-api.service`,
- test findings table,
- test smoke pack editor,
- test execution result/cleanup state,
- `cd frontend && npm test`.

End-to-end/manual smoke:

- Jira key z podlinkowanym MR,
- Jira key bez MR linku, fallback po regexie,
- story puste, generate smoke pack only,
- instruction violation w lokalnym `AGENTS.md`,
- execution passed,
- execution failed,
- cleanup failed -> manual SQL.

## Ryzyka i decyzje do podjecia

Najwazniejsze decyzje przed implementacja:

- Czy Confluence wchodzi do MVP, czy dopiero po Jira + GitLab MR?
- Czy pierwsza wersja execution obsluguje DB assertions, czy tylko HTTP +
  cleanup endpoint?
- Czy bezposredni MR link jest rownorzednym inputem, czy tylko fallbackiem?
- Jak skonfigurowac auth do wykonywanych endpointow: shared env auth refs,
  user-provided token ref, czy tylko placeholder w MVP?
- Jak reprezentowac manual cleanup SQL w UI: jako zwykly tekst, zatwierdzany
  artefakt, czy osobny panel z values/source refs?
- Czy raw readonly SQL dla DB assertions pozostaje wylaczony w pierwszej
  wersji, a dozwolone sa tylko typed assertions?
- Czy smoke packi zapisujemy od razu w local workspace jako reusable library,
  czy tylko w ramach runu?

Najwieksze ryzyka:

- zbyt szeroki scope AI: ograniczamy go etapami i source-backed findings,
- zbyt szybkie wlaczenie execution: najpierw compliance + smoke pack,
- DB scope skopiowany z incydentow: trzeba go zneutralizowac,
- Postman jako source of truth runtime: unikamy tego przez wewnetrzny smoke
  pack model,
- findingi bez zrodel: trafiaja jako suggestions albo visibility limits,
- cleanup jako write DB: zabronione; tylko HTTP allowlist albo manual SQL dla
  czlowieka.

## Proponowany MVP

Najrozsadniejszy MVP:

1. Jira key/link.
2. GitLab MR discovery z Jiry albo regex fallback po Jira key.
3. Instruction context discovery z `AGENTS.md` i Copilot instructions.
4. `Check Compliance`:
   - Story Compliance,
   - Instruction Compliance,
   - discrepancies,
   - suggested actions.
5. `Generate Smoke Pack`:
   - edytowalny smoke pack,
   - Postman collection/environment export.
6. Bez execution w pierwszym releasie MVP albo execution tylko jako feature
   flag po akceptacji smoke packa.

To daje uzyteczny rezultat bez najwiekszego ryzyka runtime side effectow.
Execution i cleanup powinny wejsc jako kolejny etap, gdy model smoke packa,
allowlisty srodowisk i DB readonly scope beda stabilne.
