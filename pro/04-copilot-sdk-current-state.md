# Copilot SDK Current State

Ten dokument opisuje aktualny stan integracji Copilot SDK po serii
optymalizacji PR-1..PR-8.

## Status w skrocie

Wdrozone:

- telemetry per analiza i per tool,
- JSON-only response contract,
- report-only response quality gate,
- evidence coverage evaluator,
- coverage-aware tool policy,
- incident digest i stable artifact item IDs,
- backend tool budget,
- tool description decorators,
- rozszerzony capture tool evidence,
- single prepared analysis flow.

Nieaktualne historyczne zalozenia:

- prompt nie jest juz budowany dwa razy,
- response nie jest juz parserem legacy labeli,
- tool policy nie wylacza GitLaba tylko dlatego, ze istnieje jedna sekcja
  GitLab evidence,
- runtime nie uzywa SDK attachments dla evidence.

## Glowny flow

1. Evidence collector tworzy `AnalysisEvidenceSection`.
2. Orchestrator buduje `AnalysisAiAnalysisRequest`.
3. Provider wykonuje `prepare(request)` i zwraca `AnalysisAiPreparedAnalysis`.
4. Job state zapisuje `prepared.prompt()`.
5. Provider wykonuje `analyze(prepared, listener)`.
6. Gateway uruchamia sesje Copilot SDK.
7. Bridge obsluguje Spring tool callbacks, telemetry, budget i capture.
8. Parser mapuje JSON response na publiczny `AnalysisAiAnalysisResponse`.
9. Quality gate loguje findings w trybie report-only.

## Preparation

`CopilotSdkPreparationService` sklada wszystkie elementy potrzebne do sesji:

- coverage report,
- tool access policy,
- decorated tool definitions,
- artifacts inline,
- runtime skills,
- final prompt,
- preparation metrics.

`CopilotSdkPreparedRequest` przechowuje prompt, artifacts, tools, context,
skills i metryki. Jest zamykany po execution.

## Artifact model

Artifacts sa logicznymi plikami w promptcie:

```text
00-incident-manifest.json
01-incident-digest.md
02-... evidence
```

Manifest zawiera indeks, tool policy, coverage i delivery mode. Digest jest
kompresja najwazniejszych faktow dla modelu. Raw evidence nadal jest dostepne
w kolejnych artifacts.

Wazne: current code uses embedded prompt artifacts, not SDK attachments.

## Response model

Prompt wymaga JSON only. Parser probuje:

1. full JSON,
2. fenced JSON,
3. structured fallback.

Legacy labeled fallback zostal usuniety.

Publiczny response nadal uzywa istniejacych pol, ale wewnetrzny JSON zawiera
tez rationale, process/context/team, confidence, evidence references i
visibility limits.

## Quality

`CopilotResponseQualityGate` sprawdza, czy wynik jest uzyteczny i ugruntowany.
Aktualny tryb to `REPORT_ONLY`.

Findings nie naprawiaja ani nie nadpisuja wyniku. Sa materialem do telemetryki
i przyszlego soft repair.

## Coverage i policy

Coverage evaluator czyta generyczne evidence i okresla:

- stan log evidence,
- stan GitLab/code/flow evidence,
- runtime coverage,
- operational context coverage,
- potrzebe DB diagnostics,
- evidence gaps.

Tool policy korzysta z coverage:

- Elastic only for log gaps,
- GitLab only for code/flow gaps,
- DB only for resolved environment plus data diagnostic need,
- raw SQL disabled by default.

## Tools

GitLab tools obejmuja search, full file read, chunk/chunks, outline,
class references i flow context.

DB tools obejmuja discovery, typed checks, joins, samples, mapping comparison
i raw SQL, ale raw SQL jest domyslnie blokowany przez policy/budget.

Tool descriptions sa dekorowane przez `CopilotToolDescriptionDecorator`.

## Tool budget

Budget jest session-bound. Soft mode loguje przekroczenia, hard mode zwraca
controlled denied result.

Budzet mierzy:

- total calls,
- calls per group,
- GitLab search/read/chunk,
- DB raw SQL attempts,
- returned characters.

## Tool evidence capture

Captured sections:

- `gitlab/tool-fetched-code`,
- `gitlab/tool-search-results`,
- `gitlab/tool-flow-context`,
- `database/tool-results`.

To daje job API material audytowy poza finalnym tekstem AI.

## Telemetry

Session metrics lacza preparation, execution, parser, tools, budget i quality.
Summary log powinien powstac raz dla analizy, a tool events moga byc logowane
oddzielnie.

## Properties

Aktywne grupy Copilota:

```text
analysis.ai.copilot.*
analysis.ai.copilot.metrics.*
analysis.ai.copilot.quality-gate.*
analysis.ai.copilot.tool-budget.*
```

Nie dokumentowac juz properties dla attachment artifacts ani legacy response
formatu, jesli nie istnieja w kodzie.

## Aktualne ograniczenia

- Quality gate jest report-only; nie ma jeszcze soft repair.
- Nie ma golden evaluation suite do mierzenia trafnosci wynikow.
- Tool budget thresholds sa baseline i wymagaja strojenia na realnych
  analizach.
- Permission mode property nadal domyslnie ma `approve-all`, przy czym glowny
  analysis flow blokuje lokalne tools przez session hooks.
- UI moze dalej rozwinac prezentacje tool traces, budget warnings i quality
  findings.
- SDK attachments pozostaja poza runtime.
