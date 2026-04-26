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

Nie ma juz podwojnego budowania promptu przez `preparePrompt(...)` i
`analyze(request)`.

## Preparation

`CopilotSdkPreparationService`:

- ocenia evidence coverage,
- buduje coverage-aware tool policy,
- renderuje artefakty inline,
- laduje runtime skills,
- generuje prompt JSON-only,
- rejestruje metryki preparation.

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

`SessionHooks.onPreToolUse` blokuje lokalny workspace/filesystem/shell/terminal.
GitLab, Elasticsearch i DB tools pracuja przez hidden `ToolContext`.

`CopilotToolDescriptionDecorator` dodaje Copilot-facing guidance do opisow
drogich albo ryzykownych tools.

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

Bridge publikuje tool evidence przez listener:

- `gitlab/tool-fetched-code`,
- `gitlab/tool-search-results`,
- `gitlab/tool-flow-context`,
- `database/tool-results`.

DB summaries zawieraja pytanie diagnostyczne, parametry i summary wyniku, aby
audyt nie byl tylko raw JSON dump.

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
