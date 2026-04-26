# Runtime Contracts And Configuration

Ten plik opisuje aktualny kontrakt runtime po optymalizacjach Copilota. Jest
to context pack dla pracy w `/pro`, wiec celowo skupia sie na granicach,
konfiguracji i rzeczach, ktorych nie nalezy pomylic z historycznym stanem.

## Publiczne API analizy

Endpointy:

```http
POST /analysis
POST /analysis/jobs
GET /analysis/jobs/{analysisId}
```

Request wejscia niesie tylko:

```json
{
  "correlationId": "..."
}
```

Backend wyprowadza pozostale scope'y:

- `environment` z evidence,
- `gitLabBranch` z deployment/runtime evidence,
- `gitLabGroup` z konfiguracji,
- DB scope z resolved environment i konfiguracji datasource.

Nie przywracac `branch`, `environment` ani `gitLabGroup` do publicznego
requestu.

## Granica AI

Generyczny kontrakt AI:

```java
public interface AnalysisAiProvider {
    default String preparePrompt(AnalysisAiAnalysisRequest request) { ... }
    default AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request) { ... }
    default AnalysisAiAnalysisResponse analyze(
            AnalysisAiPreparedAnalysis preparedAnalysis,
            AnalysisAiToolEvidenceListener listener
    ) { ... }
    AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request);
}
```

`AnalysisAiPreparedAnalysis` jest generyczne i nie ujawnia Copilot SDK:

```java
public interface AnalysisAiPreparedAnalysis extends AutoCloseable {
    String providerName();
    String correlationId();
    String prompt();
}
```

`CopilotSdkPreparedRequest` implementuje ten interfejs wewnatrz
`analysis.ai.copilot`.

## Prepared analysis flow

Orchestrator:

1. zbiera deterministic evidence,
2. buduje `AnalysisAiAnalysisRequest`,
3. wywoluje `provider.prepare(request)`,
4. zapisuje `prepared.prompt()` w job state,
5. wywoluje `provider.analyze(prepared, listener)`,
6. zamyka prepared request.

Dzieki temu prompt widoczny w job UI jest dokladnie promptem uzytym przez
Copilota.

## Evidence contract

AI layer dostaje generyczne:

- `AnalysisAiAnalysisRequest`,
- `AnalysisEvidenceSection`,
- `AnalysisEvidenceItem`,
- `AnalysisEvidenceAttribute`.

Nie wciskac DTO adapterow do prompt buildera ani publicznych kontraktow AI.
Jesli potrzebny jest typowany widok, uzyc helperow widoku zbudowanych nad
generycznymi sekcjami.

## Artifact delivery contract

Aktualny runtime uzywa embedded prompt artifacts, nie SDK attachments.

`CopilotArtifactService` renderuje logiczne pliki:

```text
00-incident-manifest.json
01-incident-digest.md
02-... evidence artifact
```

Artifact contents sa osadzane inline w promptcie. `MessageOptions` dostaje
tylko finalny prompt przez `setPrompt(prompt)`.

Nie zakladac lokalnych sciezek plikowych dla artifactow. Zmiana na SDK
attachments to jawna zmiana runtime.

## Artifact manifest

Manifest zawiera:

- `correlationId`,
- `environment`,
- `gitLabBranch`,
- `gitLabGroup`,
- `artifactFormatVersion`,
- `readFirst`,
- `readNext`,
- `artifactPolicy.deliveryMode=embedded-prompt`,
- `toolPolicy`,
- `evidenceCoverage`,
- indeks artifactow i `itemIds`.

`AnalysisEvidenceItem` nie ma publicznego `itemId`. Stable IDs sa generowane
artifact-only podczas renderowania Copilota.

## Incident digest

`01-incident-digest.md` jest kontekstowa kompresja przed raw evidence:

- session facts,
- evidence coverage,
- strongest log signals,
- deployment facts,
- runtime signals,
- code evidence highlights,
- known evidence gaps.

Prompt instruuje model, aby czytal manifest, potem digest, a dopiero pozniej
raw artifacts.

## JSON response contract

Copilot ma zwracac tylko valid JSON.

Minimalny wymagany shape:

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

Parser:

1. probuje caly content jako JSON,
2. probuje fenced JSON block,
3. buduje fallback strukturalny.

Legacy labeled parser zostal usuniety. Nie dokumentowac juz
`detectedProblem: ...` jako wspieranego kontraktu.

Fallback zachowuje pola czesciowo sparsowane z JSON. `AI_UNSTRUCTURED_RESPONSE`
pojawia sie, gdy nie ma parseable `detectedProblem`.

## Quality gate

`CopilotResponseQualityGate` dziala po parserze. Domyslnie jest
`REPORT_ONLY`, wiec nie zmienia runtime response.

Quality findings trafiaja do logow i telemetryki.

## Coverage report

`CopilotEvidenceCoverageReport` zawiera:

- `elastic`,
- `gitLab`,
- `runtime`,
- `operationalContext`,
- `dataDiagnosticNeed`,
- `environmentResolved`,
- `gaps`.

Coverage enum values:

```text
Elastic: NONE, LOGS_PRESENT_NO_EXCEPTION, EXCEPTION_PRESENT,
STACKTRACE_PRESENT, TRUNCATED, SUFFICIENT

GitLab: NONE, SYMBOL_ONLY, STACK_FRAME_ONLY, FAILING_METHOD_ONLY,
DIRECT_COLLABORATOR_ATTACHED, FLOW_CONTEXT_ATTACHED, SUFFICIENT

DataDiagnosticNeed: NONE, POSSIBLE, LIKELY, REQUIRED

OperationalContext: NONE, PARTIAL, MATCHED
```

Coverage report jest w manifest/prompt i steruje allowlista tools.

## Tool policy

`CopilotToolAccessPolicy` filtruje tools wedlug coverage:

- Elastic tools tylko dla brakujacych/niepelnych/obcietych logow.
- GitLab tools dla brakujacego code/flow context.
- Przy znanym GitLab evidence wlaczany jest focused GitLab toolset.
- DB tools tylko przy resolved environment i realnej potrzebie data
  diagnostics.
- `db_execute_readonly_sql` jest domyslnie disabled.

## Tool contracts

Elasticsearch:

```text
elastic_search_logs_by_correlation_id
```

GitLab:

```text
gitlab_search_repository_candidates
gitlab_read_repository_file
gitlab_read_repository_file_chunk
gitlab_read_repository_file_outline
gitlab_read_repository_file_chunks
gitlab_find_class_references
gitlab_find_flow_context
```

Database:

```text
db_get_scope
db_find_tables
db_find_columns
db_describe_table
db_exists_by_key
db_count_rows
db_group_count
db_sample_rows
db_check_orphans
db_find_relationships
db_join_count
db_join_sample
db_compare_table_to_expected_mapping
db_execute_readonly_sql
```

Tools sa session-bound przez hidden `ToolContext`.

## Tool descriptions

`CopilotToolDescriptionDecorator` dokleja guidance do opisow tools. Guidance
jest Copilot-facing i nie zmienia Spring tool implementation.

Catalog obejmuje m.in.:

- `gitlab_read_repository_file`,
- `gitlab_search_repository_candidates`,
- `gitlab_read_repository_file_outline`,
- `gitlab_read_repository_file_chunk`,
- `gitlab_read_repository_file_chunks`,
- `gitlab_find_flow_context`,
- `gitlab_find_class_references`,
- `db_sample_rows`,
- `db_join_sample`,
- `db_execute_readonly_sql`.

## Tool evidence sections

`CopilotToolEvidenceCaptureRegistry` publikuje:

```text
gitlab/tool-fetched-code
gitlab/tool-search-results
gitlab/tool-flow-context
database/tool-results
```

DB tool evidence zawiera:

- `toolName`,
- `diagnosticQuestion`,
- `environment`,
- `databaseAlias`,
- `parameters`,
- `resultSummary`,
- `result`.

## Telemetry contract

`CopilotAnalysisMetrics` agreguje per analiza:

- section/item/artifact counts,
- artifact/prompt characters,
- preparation/client/create session/sendAndWait/total durations,
- total tool calls,
- Elastic/GitLab/DB calls,
- GitLab read file/chunk calls,
- GitLab returned characters,
- DB query/raw SQL calls,
- DB returned characters,
- structured/fallback/parser state,
- detected problem/confidence,
- quality findings,
- budget warnings/denials.

`CopilotMetricsLogger` emituje summary log i opcjonalne tool events.

## Runtime properties

Copilot SDK:

```properties
analysis.ai.copilot.cli-path=copilot
analysis.ai.copilot.working-directory=${user.dir}
analysis.ai.copilot.model=
analysis.ai.copilot.reasoning-effort=
analysis.ai.copilot.client-name=incidenttracker
analysis.ai.copilot.send-and-wait-timeout=5m
analysis.ai.copilot.github-token=
analysis.ai.copilot.permission-mode=approve-all
analysis.ai.copilot.skill-resource-roots=copilot/skills
analysis.ai.copilot.skill-runtime-directory=${java.io.tmpdir}/incident-tracker/copilot-skills
analysis.ai.copilot.skill-directories=
analysis.ai.copilot.disabled-skills=
```

Metrics:

```properties
analysis.ai.copilot.metrics.enabled=true
analysis.ai.copilot.metrics.log-summary=true
analysis.ai.copilot.metrics.log-tool-events=true
```

Quality:

```properties
analysis.ai.copilot.quality-gate.enabled=true
analysis.ai.copilot.quality-gate.mode=report-only
analysis.ai.copilot.quality-gate.min-affected-function-characters=80
analysis.ai.copilot.quality-gate.high-confidence-visibility-limit-threshold=2
```

Tool budget:

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

Nie ma aktywnej flagi `analysis.ai.copilot.response-format`. Response format
jest JSON-only w aktualnym runtime.

## Safety defaults

- Local workspace/filesystem/shell/terminal blocked by session hooks.
- Raw SQL blocked by policy and default budget.
- Tool budget soft by default.
- Quality gate report-only by default.
- Artifacts embedded in prompt, not local attachments.
