# Runtime Contracts And Configuration

## Glowny kontrakt API

### `POST /analysis`

Request:

```json
{
  "correlationId": "..."
}
```

Response domainowy to `AnalysisResultResponse` z polami:

- `status`
- `correlationId`
- `environment`
- `gitLabBranch`
- `summary`
- `detectedProblem`
- `recommendedAction`
- `rationale`
- `affectedFunction`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`
- `prompt`

### `POST /analysis/jobs`

Request:

```json
{
  "correlationId": "..."
}
```

### `GET /analysis/jobs/{analysisId}`

Zwraca `AnalysisJobResponse`.

Najwazniejsze pola dla UI i diagnostyki:

- `status`
- `currentStepCode`
- `currentStepLabel`
- `environment`
- `gitLabBranch`
- `errorCode`
- `errorMessage`
- `steps`
- `evidenceSections`
- `toolEvidenceSections`
- `preparedPrompt`
- `result`

## Statusy joba

Status calego joba:

- `QUEUED`
- `COLLECTING_EVIDENCE`
- `ANALYZING`
- `COMPLETED`
- `FAILED`
- `NOT_FOUND`

Status kroku:

- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `SKIPPED`

## Kontrakt evidence

Granica backend -> AI i backend -> UI uzywa:

- `AnalysisEvidenceSection`
- `AnalysisEvidenceItem`
- `AnalysisEvidenceAttribute`

Shape sekcji:

```json
{
  "provider": "gitlab",
  "category": "resolved-code",
  "items": [
    {
      "title": "backend file src/main/java/...",
      "attributes": [
        { "name": "projectName", "value": "backend" },
        { "name": "filePath", "value": "src/main/java/..." }
      ]
    }
  ]
}
```

## Kontrakt AI request

`AnalysisAiAnalysisRequest` zawiera:

- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`
- `evidenceSections`

To jest jedyna rzecz, jaka przechodzi z orchestratora do AI providera.

## Kontrakt AI response

`AnalysisAiAnalysisResponse` zawiera:

- `providerName`
- `summary`
- `detectedProblem`
- `recommendedAction`
- `rationale`
- `affectedFunction`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`
- `prompt`

## Kontrakt finalnego wyniku Copilota

Prompt i skills wymuszaja dzisiaj te pola:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `rationale`
- `affectedFunction`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`

Parser wymaga twardo:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `affectedFunction`

Jesli parser nie znajdzie jednego z tych czterech pol, provider wraca fallback:

- `detectedProblem = AI_UNSTRUCTURED_RESPONSE`
- `summary = raw assistant content`
- `recommendedAction = Review the raw Copilot response and improve response formatting in the prompt.`
- `affectedFunction = ""`
- `affectedProcess = ""`
- `affectedBoundedContext = ""`
- `affectedTeam = ""`

`rationale` ma default, jesli go brakuje.

## Hidden session context dla tooli

Session-bound dane runtime nie sa model-facing parametrami.
Bridge Copilota przekazuje je do Spring tools przez hidden `ToolContext`.

Najwazniejsze klucze to:

- `analysisRunId`
- `copilotSessionId`
- `actualCopilotSessionId`
- `toolCallId`
- `toolName`
- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`

W praktyce oznacza to:

- GitLab tools nie przyjmuja juz `group`, `branch` ani `correlationId`,
- Database tools nie przyjmuja `environment`,
- model operuje tylko na parametrach eksploracji, a nie na ukrytym runtime
  scope.

## Tools dostepne dla Copilota

### Elasticsearch

- `elastic_search_logs_by_correlation_id`

Wejscie:

- `correlationId`

### GitLab

- `gitlab_search_repository_candidates`
- `gitlab_find_flow_context`
- `gitlab_read_repository_file_outline`
- `gitlab_read_repository_file`
- `gitlab_read_repository_file_chunk`
- `gitlab_read_repository_file_chunks`

Najwazniejsze reguly:

- tools sa session-bound i biora `group`, `branch` i `correlationId` z hidden
  `ToolContext`,
- model podaje tylko parametry eksploracji, np. `projectName`, `filePath`,
  `keywords`, `seedClass`, `chunks`,
- `gitlab_read_repository_file`, `gitlab_read_repository_file_chunk` i
  `gitlab_read_repository_file_chunks` sa przechwytywane do
  `toolEvidenceSections` w job flow.

### Database

- `db_get_scope`
- `db_find_tables`
- `db_find_columns`
- `db_describe_table`
- `db_exists_by_key`
- `db_count_rows`
- `db_group_count`
- `db_sample_rows`
- `db_check_orphans`
- `db_find_relationships`
- `db_join_count`
- `db_join_sample`
- `db_compare_table_to_expected_mapping`
- `db_execute_readonly_sql`

Najwazniejsze reguly:

- capability jest opcjonalna i domyslnie wylaczona,
- `environment` i `correlationId` pochodza z hidden `ToolContext`,
- discovery jest application-scoped przez `applicationNamePattern`, a nie przez
  `schemaPattern`,
- exact data tools pracuja dopiero na dokladnym `schema.table`,
- raw SQL jest last resort i domyslnie pozostaje wylaczony.

## Properties runtime

### AI / Copilot SDK

`analysis.ai.copilot.*`

- `cli-path`
- `working-directory`
- `model`
- `reasoning-effort`
- `client-name`
- `send-and-wait-timeout`
- `github-token`
- `permission-mode`
- `skill-resource-roots`
- `skill-runtime-directory`
- `attachment-artifact-directory`
- `skill-directories`
- `disabled-skills`

Domyslne zachowania z kodu:

- `cliPath = copilot`
- `clientName = incidenttracker`
- `permissionMode = APPROVE_ALL`
- `sendAndWaitTimeout = 5m`
- `skillResourceRoots = [copilot/skills]`

### GitLab

`analysis.gitlab.*`

- `base-url`
- `group`
- `token`
- `ignore-ssl-errors`
- `search-results-per-term`
- `max-candidate-count`

Domyslne z kodu:

- `searchResultsPerTerm = 20`
- `maxCandidateCount = 10`

### Database

`analysis.database.*`

- `enabled`
- `max-rows`
- `max-columns`
- `max-tables-per-search`
- `max-columns-per-search`
- `max-tables-to-describe`
- `max-result-characters`
- `query-timeout`
- `connection-timeout`
- `raw-sql-enabled`
- `allow-all-schemas`
- `environments.<environment>.jdbc-url`
- `environments.<environment>.username`
- `environments.<environment>.password`
- `environments.<environment>.database-alias`
- `environments.<environment>.description`
- `environments.<environment>.allowed-schemas`
- `environments.<environment>.applications.<application>.schema`
- `environments.<environment>.applications.<application>.*patterns`
- `environments.<environment>.applications.<application>.related-schemas`

Domyslne z kodu:

- `enabled = false`
- `maxRows = 50`
- `maxColumns = 40`
- `maxTablesPerSearch = 30`
- `maxColumnsPerSearch = 50`
- `maxTablesToDescribe = 5`
- `maxResultCharacters = 64000`
- `queryTimeout = 5s`
- `connectionTimeout = 5s`
- `rawSqlEnabled = false`
- `allowAllSchemas = false`

Wazny detal implementacyjny:

- capability nie wymaga globalnego `spring.datasource`,
- DataSource'y sa tworzone recznie per environment przez
  `DatabaseConnectionRouter`,
- aplikacja wyklucza globalne `DataSourceAutoConfiguration`.

### Elasticsearch

`analysis.elasticsearch.*`

- `base-url`
- `kibana-space-id`
- `index-pattern`
- `authorization-header`
- `evidence-size`
- `evidence-max-message-characters`
- `evidence-max-exception-characters`
- `search-size`
- `search-max-message-characters`
- `search-max-exception-characters`
- `tool-size`
- `tool-max-message-characters`
- `tool-max-exception-characters`

Domyslne z kodu:

- `kibanaSpaceId = default`
- `indexPattern = logs-*`
- `evidenceSize = 20`
- `searchSize = 200`
- `toolSize = 200`

### Dynatrace

`analysis.dynatrace.*`

- `base-url`
- `api-token`
- `entity-page-size`
- `entity-fetch-max-pages`
- `entity-candidate-limit`
- `problem-page-size`
- `problem-limit`
- `problem-evidence-limit`
- `metric-entity-limit`
- `metric-resolution`
- `query-padding-before`
- `query-padding-after`

Domyslne z kodu:

- `entityPageSize = 200`
- `entityFetchMaxPages = 5`
- `entityCandidateLimit = 3`
- `problemLimit = 3`
- `problemEvidenceLimit = 3`
- `metricEntityLimit = 2`
- `metricResolution = 1m`
- padding przed i po incydencie: `15m`

### Operational context

`analysis.operational-context.*`

- `enabled`
- `resource-root`
- `max-items-per-type`
- `max-glossary-terms`
- `max-handoff-rules`

Domyslnie:

- `enabled = false`

## Runtime resources

### Runtime skills Copilota

Classpath source:

- `src/main/resources/copilot/skills/incident-analysis-core/SKILL.md`
- `src/main/resources/copilot/skills/incident-analysis-gitlab-tools/SKILL.md`
- `src/main/resources/copilot/skills/incident-data-diagnostics/SKILL.md`

Runtime extraction:

- `${java.io.tmpdir}/incident-tracker/copilot-skills`

### Attachment artifacts

Session staging directory:

- `${java.io.tmpdir}/incident-tracker/copilot-attachments`

Kazda analiza generuje tymczasowo:

- `00-incident-manifest.json`
- po jednym pliku `.json` na sekcje evidence

Manifest zawiera:

- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`
- `generatedAt`
- `readFirst`
- `artifactPolicy`
- liste artefaktow

### Operational context

Classpath source:

- `src/main/resources/operational-context/*`

## Build i test

Podstawowe komendy:

- `mvn -q clean test`
- `mvn -q -DskipTests package`
- `cd frontend && npm start`
- `cd frontend && npm run build`

## Wazne runtime fakty

- `SessionConfig` dostaje jawny `sessionId` generowany po stronie backendu,
- job state jest w pamieci procesu,
- frontend polluje co `1500 ms`,
- prepared prompt jest zachowywany takze przy bledzie AI,
- `AnalysisContext.withSection(...)` ignoruje puste sekcje,
- orchestrator rzuca `AnalysisDataNotFoundException`, jesli po collectorze nadal
  nie ma zadnego evidence,
- Database tool results nie sa dzisiaj mapowane do `toolEvidenceSections`;
  ten mechanizm jest utrzymywany tylko dla wybranych GitLab read tools.
