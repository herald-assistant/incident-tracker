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
- `prompt`

## Kontrakt finalnego wyniku Copilota

Prompt i skill wymuszaja dokladnie te pola:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `rationale`
- `affectedFunction`

Jesli parser nie znajdzie `detectedProblem`, `summary`, `recommendedAction` lub
`affectedFunction`, provider wraca fallback:

- `detectedProblem = AI_UNSTRUCTURED_RESPONSE`
- `summary = raw assistant content`
- `recommendedAction = Review the raw Copilot response and improve response formatting in the prompt.`
- `affectedFunction = ""`

`rationale` jest opcjonalne i ma default, jesli go brakuje.

## Tools dostepne dla Copilota

### Elasticsearch

- `elastic_search_logs_by_correlation_id`

Wejscie:

- `correlationId`

### GitLab

- `gitlab_search_repository_candidates`
- `gitlab_read_repository_file`
- `gitlab_read_repository_file_chunk`

Wazne:

- tools przyjmuja jawnie `group` i `branch`,
- ale prompt i skill mowia modelowi, zeby traktowal group i branch z requestu
  jako fixed context.

To jest dzisiaj istotne miejsce do przemyslenia i potencjalnej optymalizacji.

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

### Skill Copilota

Classpath source:

- `src/main/resources/copilot/skills/incident-analysis-gitlab-tools/SKILL.md`

Runtime extraction:

- `${java.io.tmpdir}/incident-tracker/copilot-skills`

### Attachment artifacts

Session staging directory:

- `${java.io.tmpdir}/incident-tracker/copilot-attachments`

Kazda analiza generuje tymczasowo:

- `00-incident-manifest.json`
- po jednym pliku `.json` na sekcje evidence

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

- job state jest w pamieci procesu,
- frontend polluje co `1500 ms`,
- prepared prompt jest zachowywany takze przy bledzie AI,
- `AnalysisContext.withSection(...)` ignoruje puste sekcje,
- orchestrator rzuca `AnalysisDataNotFoundException`, jesli po collectorze nadal
  nie ma zadnego evidence.
