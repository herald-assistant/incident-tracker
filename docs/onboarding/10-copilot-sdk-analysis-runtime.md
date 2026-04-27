# Copilot SDK Analysis Runtime

Ten onboarding opisuje aktualny runtime providera
`CopilotSdkAnalysisAiProvider`.

## Najwazniejszy kontrakt

Copilot dostaje przygotowany request przez generyczne
`AnalysisAiPreparedAnalysis`.

Flow:

1. `AnalysisAiProvider.prepare(request)` buduje `CopilotSdkPreparedRequest`,
2. orchestrator zapisuje `prepared.prompt()`,
3. `AnalysisAiProvider.analyze(prepared, listener)` uruchamia sesje SDK,
4. prepared request jest zamykany po execution.

Ownership prepared requestu:

- kod, ktory wywolal `prepare(request)`, zamyka returned
  `AnalysisAiPreparedAnalysis`,
- `analyze(request)` zamyka tylko prepared request utworzony przez siebie,
- `analyze(prepared, listener)` nie zamyka obiektu przekazanego przez caller,
- `CopilotSdkExecutionGateway` nie zamyka prepared requestu.

Nie ma juz podwojnego budowania promptu przez produkcyjne
`preparePrompt(...)` i `analyze(request)`. Preview promptu ma pochodzic z
realnego `prepare(request).prompt()`.

## Preparation

`CopilotSdkPreparationService`:

- ocenia evidence coverage,
- buduje coverage-aware tool policy,
- renderuje artefakty inline,
- laduje runtime skills,
- generuje prompt JSON-only,
- rejestruje metryki preparation.

Po refaktorze `CopilotSdkPreparationService` jest kompozytorem zaleznosci:

- `CopilotToolAccessPolicyFactory` buduje policy z coverage reportu i listy
  registered tools,
- `CopilotPromptRenderer` zawiera tekst promptu, JSON response contract,
  rendering capability groups i embedded artifact contents,
- `CopilotSessionConfigFactory` buduje `CopilotClientOptions`,
  `SessionConfig`, permission handler, hooks, safe lists, skill directories i
  disabled skills.
- `AnalysisAiOptions` z requestu jobowego moze nadpisac skonfigurowany
  `model` i `reasoningEffort` dla pojedynczej sesji; brak wyboru oznacza
  fallback do properties albo domyslow SDK.

Artefakty nie sa SDK attachments. Prompt zawiera logiczne pliki w kolejnosci:

```text
00-incident-manifest.json
01-incident-digest.md
02-... raw evidence
```

Manifest deklaruje `deliveryMode=embedded-prompt` i zawiera evidence coverage,
tool policy oraz indeks `itemIds`.

## Incident digest i itemId

`01-incident-digest.md` jest skompresowana warstwa kontekstu dla modelu:

- session facts,
- coverage summary,
- strongest log signals,
- deployment facts,
- runtime highlights,
- code highlights,
- known evidence gaps.

`itemId` sa generowane tylko w Copilot artifact rendering. Markdown artifacts
dostaja naglowki `## itemId: ...`, a JSON artifacts pole `itemId`.

## Tools

Runtime rejestruje tylko tools dozwolone przez `CopilotToolAccessPolicy`.
Policy uzywa `CopilotEvidenceCoverageReport`, a nie prostego sprawdzenia, czy
sekcja evidence istnieje.

Gdy GitLab scope jest resolved, manifest dostaje
`AFFECTED_FUNCTION_GITLAB_RECOMMENDED`. To zostawia focused GitLab tools do
malego lookupu przed finalna odpowiedzia, zeby `affectedFunction` bylo
szczegolowe i opisane jezykiem techniczno-funkcjonalnym, nie jako lista klas.

Gdy coverage wykryje DB-related symptom bez ugruntowanej encji/repozytorium w
GitLab evidence, manifest dostaje `DB_CODE_GROUNDING_NEEDED`. Wtedy policy
zostawia focused GitLab tools, zeby model mogl sprobowac znalezc mapowanie
entity/table/relations przed DB discovery; jesli to sie nie uda albo GitLab
tools nie ma w sesji, DB discovery jest jawnym fallbackiem.

Policy powstaje przez `CopilotToolAccessPolicyFactory`, aby ukryta zaleznosc
od coverage evaluatora nie byla zaszyta w samym recordzie policy.

`SessionHooks.onPreToolUse` blokuje lokalny workspace/filesystem/shell/terminal.
GitLab, Elasticsearch i DB tools pracuja przez hidden `ToolContext`.

`CopilotToolDescriptionDecorator` dodaje Copilot-facing guidance do opisow
drogich albo ryzykownych tools.

`CopilotSdkToolBridge` odpowiada za rejestracje definicji tools: zbiera Spring
callbacks, sortuje je, dekoruje description, parsuje input schema i tworzy
`ToolDefinition`. `CopilotToolInvocationHandler` odpowiada za wykonanie:
waliduje session id, buduje hidden context, pilnuje budgetu before/after,
wywoluje callback, zapisuje metryki, publikuje evidence capture i parsuje
result preview.

## Budget

`CopilotToolBudgetRegistry` tworzy state per `copilotSessionId`.
`CopilotToolBudgetGuard` jest wywolywany w bridge przed i po tool callbacku.

Domyslnie:

```properties
analysis.ai.copilot.tool-budget.enabled=true
analysis.ai.copilot.tool-budget.mode=soft
```

Soft mode nie blokuje. Hard mode zwraca kontrolowany result
`denied_by_tool_budget`.

## Tool evidence

Invocation handler publikuje tool evidence przez listener:

- `gitlab/tool-fetched-code`,
- `database/tool-results`.

GitLab user-facing evidence pokazuje tylko plik/chunk, sciezke pliku, tresc
kodu i `reason` podany przez model. Search, outline, flow context i class
references pozostaja pomocniczymi toolami dla modelu, bez osobnych sekcji UI.

DB user-facing evidence pokazuje `reason` podany przez model oraz wynik toola
jako `result`. Nie utrzymujemy juz diagnostycznego pytania, parametrow ani
dodatkowego streszczenia wyniku w payloadzie dla operatora.

Registry capture zarzadza sesjami i routingiem. Mapowanie GitLab/DB wynikow
jest przeniesione do mapperow, zeby lifecycle sesji nie mieszal sie z
formatem payloadow poszczegolnych tools.

## Response parsing

Prompt wymaga odpowiedzi JSON-only. Parser akceptuje caly content jako JSON
albo fenced JSON block. Legacy labeled parser nie istnieje.

Wymagane pola:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `affectedFunction`

Dodatkowe pola:

- `rationale`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`
- `confidence`
- `evidenceReferences`
- `visibilityLimits`

Fallback zachowuje czesciowo sparsowane pola i ustawia
`AI_UNSTRUCTURED_RESPONSE` tylko wtedy, gdy brakuje `detectedProblem`.

## Quality gate

`CopilotResponseQualityGate` dziala domyslnie w `REPORT_ONLY`.
Findings sa widoczne w telemetryce/logach, ale nie zmieniaja runtime result.

## Telemetry

`CopilotSessionMetricsRegistry` agreguje metryki sesji, a
`CopilotMetricsLogger` emituje structured summary log.

Metryki obejmuja:

- rozmiary promptu i artefaktow,
- duration preparation/client/create session/sendAndWait/total,
- tool calls wedlug grup,
- drogie tool counters i returned characters,
- parser/fallback state,
- detected problem/confidence,
- quality findings,
- budget warnings/denials.

Mutable stan licznikow jest oddzielony od registry, ale pola
`CopilotAnalysisMetrics` i JSON summary log pozostaja bez zmian.

## Publiczny kontrakt produktu

Refaktory runtime Copilota nie zmieniaja kontraktow zewnetrznych:

- `POST /analysis` przyjmuje tylko `correlationId`,
- `POST /analysis/jobs` przyjmuje `correlationId` oraz opcjonalne generyczne
  preferencje AI: `model` i `reasoningEffort`,
- `gitLabGroup` pochodzi z konfiguracji,
- `environment` i `gitLabBranch` sa wyprowadzane z evidence,
- artefakty Copilota sa embedded inline w promptcie, nie SDK attachments,
- response aplikacji pozostaje mapowany z JSON-only odpowiedzi AI do
  dotychczasowych pol.

## Properties

```properties
analysis.ai.copilot.working-directory=${user.dir}
analysis.ai.copilot.permission-mode=approve-all
analysis.ai.copilot.send-and-wait-timeout=5m
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
