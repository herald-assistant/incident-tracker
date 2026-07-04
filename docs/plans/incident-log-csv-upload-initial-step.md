# Incident log CSV upload as initial log source - implementation plan

## Goal

Dodac alternatywny mechanizm poczatkowego kroku Incident Analysis:
operator moze zaczac analize z logow pobranych z Elasticsearch po
`correlationId` albo z zalaczonego pliku CSV wyeksportowanego z
Kibana/Elastic Discover.

Po walidacji i mapowaniu CSV dalszy pipeline ma isc ta sama sciezka co dzis:
sekcja evidence pozostaje `elasticsearch/logs`, a deployment context,
Dynatrace, GitLab deterministic, operational context, prompt i Copilot coverage
czytaja te same atrybuty logow.

## Working rules for this plan

- Przed wykonaniem kazdego kroku Codex opisuje proponowana zmiane i dlaczego
  wybrana jest ta sciezka.
- Krok jest wykonywany dopiero po zatwierdzeniu przez uzytkownika.
- Po wykonaniu kroku status w tym pliku jest aktualizowany na `[x]`.
- Jezeli w trakcie pracy zmieni sie podejscie, ten plik jest aktualizowany
  przed kolejnym krokiem implementacji.
- Brak kompatybilnosci wstecznej dotyczy kontraktu startu joba i UI. Stary
  JSON-only start nie musi pozostac obslugiwany. Kanoniczny URL
  `POST /api/analysis/jobs` zostaje publicznym startem analizy; ewentualny
  legacy alias moze wystawiac ten sam nowy kontrakt.

## Key decisions

- CSV import jest deterministyczny. AI nie mapuje kolumn ani nie zgaduje
  struktury pliku.
- V1 obsluguje CSV z Kibana/Elastic Discover. Text/JSON log upload zostaje poza
  zakresem tego planu.
- Jezeli konfiguracja Elasticsearch/Kibana nie jest kompletna w efektywnych
  ustawieniach z `application.properties` plus `settings.json`, start po
  `correlationId` jest zablokowany w UI i odrzucany na backendzie.
- Zalaczenie CSV jest dostepne zawsze jako zrodlo logow. Uruchomienie analizy
  nadal wymaga poprawnej autoryzacji Copilota, bo AI pozostaje etapem
  diagnostycznym.
- Elasticsearch tools nie sa udostepniane Copilotowi, gdy konfiguracja
  Elasticsearch/Kibana nie jest kompletna. Dotyczy initial analysis i follow-up
  chat.
- `environment` i `gitLabBranch` nadal sa wyprowadzane z evidence logow, a nie
  z requestu.
- `gitLabGroup` nadal pochodzi z konfiguracji aplikacji.

## Effective Elasticsearch configuration

Konfiguracja jest traktowana jako kompletna, gdy po nalozeniu lokalnego
`settings.json` na `application.properties` istnieja:

- `analysis.elasticsearch.base-url`
- `analysis.elasticsearch.kibana-space-id`
- `analysis.elasticsearch.index-pattern`
- `analysis.elasticsearch.authorization-header`

Uzasadnienie: aktualny REST flow potrzebuje endpointu Kibana, space, index
pattern i danych autoryzacyjnych. Brak `authorization-header` traktujemy jako
brak danych logowania zgodnie z wymaganiem.

## Required CSV columns

V1 waliduje dokladne kolumny z eksportu Kibana/Elastic Discover:

| CSV column | Target field |
| --- | --- |
| `@timestamp` | `ElasticLogEntry.timestamp` after ISO normalization |
| `fields.correlationId` | derived incident `correlationId` |
| `fields.type` | `level` |
| `fields.microservice` | `serviceName` |
| `fields.class` | `className` |
| `fields.message` | `message` |
| `fields.exception` | `exception` |
| `fields.thread` | `thread` |
| `fields.spanId` | `spanId` |
| `kubernetes.namespace` | `namespace` |
| `kubernetes.pod.name` | `podName` |
| `kubernetes.container.name` | `containerName` |
| `container.image.name` | `containerImage` |

Kolumny opcjonalne:

- `_index` -> `ElasticLogEntry.indexName`
- `_id` -> `ElasticLogEntry.documentId`
- `_ignored` -> diagnostyka importu, np. mozliwy `exceptionTruncated`

Reguly walidacji CSV:

- plik musi byc poprawnym CSV parsowanym parserem obslugujacym quoted fields i
  wieloliniowe stacktrace,
- plik musi miec naglowek,
- wszystkie wymagane kolumny musza istniec,
- plik musi miec przynajmniej jeden rekord,
- wszystkie niepuste `fields.correlationId` musza wskazywac jeden incident,
- timestamp musi dac sie znormalizowac do ISO instant,
- wartosci puste, `(blank)` i `-` sa mapowane na `null`.

## Planned steps

### [x] 0. Prepare and save this implementation plan

Proposed change:
Create this markdown plan under `docs/plans`.

Why:
The user asked for an explicit implementation plan with per-step approval and
status tracking before code changes.

Done:
This file exists and defines the implementation sequence.

### [x] 1. Add backend input contract and start-source validation

Proposed change:
Replace JSON-only job start with a new start contract for
`POST /api/analysis/jobs`, most likely `multipart/form-data`:

- `source`: `ELASTICSEARCH` or `CSV_UPLOAD`
- `correlationId`: required only for `ELASTICSEARCH`
- `logFile`: required only for `CSV_UPLOAD`
- `model`
- `reasoningEffort`

Add a feature-owned input options endpoint, for example
`GET /api/analysis/jobs/input-options`, returning whether Elasticsearch start
is available and why it is disabled.

Why:
File upload cannot be represented cleanly in the current JSON DTO. A source
selector makes backend validation explicit and lets UI disable only the
`correlationId` path while keeping CSV upload available.

Likely files:

- `features/incidentanalysis/job/api/AnalysisJobController.java`
- `features/incidentanalysis/job/api/AnalysisJobStartRequest.java`
- new DTOs under `features/incidentanalysis/job/api`
- `features/incidentanalysis/job/AnalysisJobFacade.java`
- frontend `AnalysisApiService`

Done when:

- backend has a multipart/form-data start contract with explicit log source,
- backend can report Elasticsearch start availability through input options,
- full config completeness is delegated to step 2 helper,
- backend rejects missing CSV file for `CSV_UPLOAD`,
- valid `CSV_UPLOAD` is allowed through the request boundary but still returns a
  controlled "CSV import not ready" error until steps 3-4 add parser and routing,
- old JSON-only request is no longer required by tests or UI.

Done:

- `POST /api/analysis/jobs` now consumes `multipart/form-data` with explicit
  `source`.
- Existing UI start path sends `source=ELASTICSEARCH` as `FormData`.
- `GET /api/analysis/jobs/input-options` exposes current source availability.
- Basic source validation and controlled request errors are in place.
- CSV file presence is validated; actual CSV parsing/routing remains in
  steps 3-4.
- Focused backend tests and frontend tests pass.

### [x] 2. Add reusable Elasticsearch configuration availability helper

Proposed change:
Add a small helper in `integrations.elasticsearch`, for example
`ElasticConnectionAvailability`, that checks effective `ElasticProperties` and
returns:

- `configured`
- missing property keys
- operator-facing disabled reason

Why:
The same check is needed by job validation, UI input options and Copilot tool
policy. Keeping it in `integrations.elasticsearch` avoids duplicating property
rules in feature, UI-facing DTOs and AI policy.

Likely files:

- `integrations/elasticsearch/ElasticProperties.java`
- new helper/model under `integrations/elasticsearch`
- tests under `src/test/java/pl/mkn/tdw/integrations/elasticsearch`

Done when:

- readiness reflects effective values already applied from `settings.json`,
- no feature-specific code is imported into `integrations.elasticsearch`.

Done:

- `ElasticConnectionAvailabilityService` in `integrations.elasticsearch`
  centralizes the required Elasticsearch/Kibana property check.
- `ElasticConnectionAvailability` exposes `configured`, missing property keys
  and an operator-facing disabled reason.
- `AnalysisJobInputOptionsService` now consumes the reusable helper instead of
  duplicating property checks in the incident job layer.
- Focused backend tests pass:
  `mvn -q "-Dtest=ElasticConnectionAvailabilityServiceTest,AnalysisJobControllerTest,AnalysisJobFacadeTest" test`.

### [x] 3. Implement deterministic Kibana CSV parser

Proposed change:
Add an import service under `integrations.elasticsearch`, for example
`ElasticLogCsvImportService`, using a real CSV parser. Prefer
`jackson-dataformat-csv` without a hardcoded version, managed by Spring Boot.

Parser output should contain:

- derived `correlationId`,
- normalized `List<ElasticLogEntry>`,
- import metadata or validation details.

Timestamp handling:

- support ISO timestamps,
- support Kibana display format like `Jul 4, 2026 @ 10:57:36.853`,
- normalize to ISO instant strings used by downstream Dynatrace logic,
- use system default zone for timezone-less Kibana exports unless we decide to
  add a dedicated property.

Why:
The sample CSV has multiline stacktrace and quoted fields. Manual splitting is
too fragile; the parser must preserve stacktrace and commas inside values.

Likely files:

- `pom.xml`
- new classes under `integrations/elasticsearch`
- tests with inline sample CSV fixture

Done when:

- multiline stacktrace parses correctly,
- missing required columns return structured validation errors,
- invalid CSV returns a controlled API error path,
- single correlation id is derived from the file.

Done:

- Added `jackson-dataformat-csv` managed by Spring Boot.
- Added `ElasticLogCsvImportService` in `integrations.elasticsearch`.
- Added neutral parser contracts:
  `ElasticLogCsvImportResult` and `ElasticLogCsvImportException`.
- Parser validates header, required Kibana Discover columns, non-empty records,
  single non-empty `fields.correlationId`, valid timestamps and basic CSV row
  shape.
- Parser maps Kibana Discover rows to `ElasticLogEntry`, including optional
  `_index`, `_id` and `_ignored`-based truncation flags.
- Timestamp normalization supports ISO instants, offset/zoned timestamps and
  Kibana display timestamps like `Jul 4, 2026 @ 10:57:36.853`.
- Focused backend tests pass:
  `mvn -q "-Dtest=ElasticLogCsvImportServiceTest,ElasticConnectionAvailabilityServiceTest,AnalysisJobControllerTest,AnalysisJobFacadeTest" test`.

### [x] 4. Route uploaded logs through the existing `elasticsearch/logs` evidence step

Proposed change:
Introduce a feature-owned log source input passed from job -> flow -> evidence
collector. The `ElasticLogEvidenceProvider` remains the single producer of
`elasticsearch/logs` and chooses:

- REST search through `ElasticLogPort` for `ELASTICSEARCH`,
- preloaded `ElasticLogEntry` list for `CSV_UPLOAD`.

Why:
Downstream code already understands `elasticsearch/logs`. Keeping the same
provider/category avoids branching in deployment context, Dynatrace, GitLab,
operational context, artifact rendering and coverage.

Likely files:

- `features/incidentanalysis/flow/AnalysisOrchestrator.java`
- `features/incidentanalysis/evidence/AnalysisEvidenceCollector.java`
- `features/incidentanalysis/evidence/AnalysisContext.java`
- `features/incidentanalysis/evidence/provider/elasticsearch/ElasticLogEvidenceProvider.java`

Done when:

- CSV-imported logs produce the same evidence attributes as REST logs,
- deployment context resolves `environment` and `gitLabBranch` from CSV logs,
- REST is not called for CSV source.

Done:

- Added feature-owned `AnalysisLogInput` carrying log source, derived
  `correlationId` and uploaded `ElasticLogEntry` values.
- `AnalysisContext`, `AnalysisEvidenceCollector` and `AnalysisOrchestrator`
  now accept `AnalysisLogInput`, while existing `correlationId` methods
  delegate to the Elasticsearch source.
- `ElasticLogEvidenceProvider` remains the only producer of
  `elasticsearch/logs`; it uses uploaded CSV entries for `CSV_UPLOAD` and
  `ElasticLogPort` for `ELASTICSEARCH`.
- `AnalysisJobStartValidationService` validates the selected source, parses CSV
  before job creation, derives the `correlationId` from the CSV import result
  and returns `AnalysisLogInput` for the worker flow.
- The temporary `INCIDENT_LOG_FILE_IMPORT_NOT_READY` rejection was removed.
- Parser validation exceptions are mapped to user-facing job input errors so
  upload failures do not become background job failures.
- Focused backend tests and compile pass:
  `mvn -q "-Dtest=ElasticLogCsvImportServiceTest,AnalysisEvidenceCollectorTest,AnalysisJobControllerTest,AnalysisJobFacadeTest" test`
  and `mvn -q -DskipTests compile`.

### [x] 5. Add backend API errors for upload validation and disabled start modes

Proposed change:
Add controlled user-facing errors for:

- `INCIDENT_LOG_SOURCE_REQUIRED`
- `ELASTICSEARCH_LOG_SOURCE_NOT_CONFIGURED`
- `INCIDENT_LOG_FILE_MISSING`
- `INCIDENT_LOG_FILE_INVALID_CSV`
- `INCIDENT_LOG_FILE_MISSING_COLUMNS`
- `INCIDENT_LOG_FILE_EMPTY`
- `INCIDENT_LOG_FILE_MULTIPLE_CORRELATION_IDS`
- `INCIDENT_LOG_FILE_INVALID_TIMESTAMP`

Why:
The UI should show actionable feedback before a background job is created when
the input itself is invalid. These are request errors, not failed analysis jobs.

Likely files:

- `features/incidentanalysis/job/error`
- `api/ApiExceptionHandler.java`
- controller/service tests

Done when:

- bad CSV returns HTTP 400 with useful message and field errors,
- disabled Elasticsearch source returns a controlled error before job creation.

Current status:

- Service-level mapping for CSV parser validation reasons was introduced in
  step 4.
- Remaining work is to pin the HTTP contract with controller tests and add any
  missing field-level details expected by the UI.

Done:

- `AnalysisJobFacadeTest` covers that input validation rejects bad starts before
  queueing for: missing file, missing required columns, empty CSV, multiple
  correlation ids, invalid timestamp and invalid CSV syntax.
- `AnalysisJobControllerTest` pins the HTTP `400 BAD_REQUEST` contract for job
  input errors, including disabled Elasticsearch start and all CSV upload error
  codes needed by the UI.
- Bad input remains a request error and does not create a background job.
- Focused backend tests and compile pass:
  `mvn -q "-Dtest=ElasticLogCsvImportServiceTest,AnalysisJobControllerTest,AnalysisJobFacadeTest" test`
  and `mvn -q -DskipTests compile`.

### [x] 6. Disable Elasticsearch Copilot tools when config is missing

Proposed change:
Inject the Elasticsearch availability helper into
`CopilotIncidentToolAccessPolicyFactory` and make the policy remove all tools
with `elastic_` prefix when Elasticsearch is not configured. Update disabled
capability reasons in manifest/prompt.

Why:
The current tools are always registered and only coverage decides if they are
enabled. The new requirement is stronger: missing Kibana/Elastic credentials
must make Elastic tools unavailable to Copilot regardless of coverage gaps.

Likely files:

- `features/incidentanalysis/ai/copilot/preparation/CopilotIncidentToolAccessPolicyFactory.java`
- `features/incidentanalysis/ai/copilot/preparation/CopilotIncidentToolAccessPolicy.java`
- policy tests under `features/incidentanalysis/ai/copilot/preparation`

Done when:

- initial analysis does not expose any `elastic_*` tools when config is missing,
- follow-up chat also does not expose any `elastic_*` tools when config is
  missing,
- GitLab, DB, Operational Context and report/feedback tools keep their current
  rules.

Done:

- `CopilotIncidentToolAccessPolicyFactory` now uses
  `ElasticConnectionAvailabilityService` for initial analysis and follow-up
  chat.
- `CopilotIncidentToolAccessPolicy` removes all `elastic_*` tools from the
  Copilot allowlist when Elasticsearch/Kibana configuration is incomplete,
  while keeping existing GitLab, DB, Operational Context, report and feedback
  rules.
- Tests use an explicit factory test support for configured/missing Elastic,
  so the production factory has a single Spring-wired constructor with real
  availability checking.
- Disabled capability metadata now reports the Elasticsearch/Kibana
  configuration reason, including missing property keys.
- Focused backend tests and compile pass:
  `mvn -q "-Dtest=CopilotIncidentToolAccessPolicyCoverageTest,CopilotIncidentInitialPreparationServiceTest,CopilotIncidentInitialPreparationServiceCoveragePromptTest,CopilotIncidentInitialPreparationServiceEvidenceReferencePromptTest,CopilotIncidentArtifactServiceDigestOrderTest,CopilotIncidentArtifactServiceItemIdTest,CopilotIncidentFollowUpRunAssemblerTest" test`
  and `mvn -q -DskipTests compile`.

### [x] 7. Update Incident Analysis UI for source selection and CSV upload

Proposed change:
Update the start panel to:

- load input options,
- show source choice for Elasticsearch vs CSV upload,
- disable the `Correlation ID` field and Elasticsearch source when config is
  missing,
- keep CSV upload action available,
- submit `FormData` to `POST /api/analysis/jobs`,
- display CSV validation errors from the backend.

Why:
The UI must guide the operator to the valid source without exposing config
details or secrets. Source selection also prevents ambiguous submissions with
both correlation id and file.

Likely files:

- `frontend/src/app/core/models/analysis.models.ts`
- `frontend/src/app/core/services/analysis-api.service.ts`
- `frontend/src/app/features/analysis-console/analysis-console.ts`
- `frontend/src/app/features/analysis-console/analysis-console.html`
- `frontend/src/app/features/analysis-console/analysis-console.scss`
- related specs

Done when:

- no Elastic config means correlation start is visibly blocked,
- CSV file can still be selected,
- run context shows derived correlation id after backend accepts the CSV.

Done:

- Incident Analysis start panel now loads `GET /api/analysis/jobs/input-options`.
- The start form exposes a compact source selector for `ELASTICSEARCH` and
  `CSV_UPLOAD`.
- When Elasticsearch/Kibana is not configured, the Elasticsearch option and
  `Correlation ID` path are blocked and the UI switches to CSV upload.
- CSV upload remains selectable, stores the chosen file in the start request and
  submits it through existing `multipart/form-data`.
- Start validation errors from the backend, including CSV validation errors, are
  rendered next to the start form instead of as failed analysis workspace state.
- After a CSV start is accepted, the returned job state supplies the derived
  `correlationId` in the normal run context.
- Frontend tests cover Elasticsearch default start, disabled Elasticsearch
  input, CSV upload submission and CSV validation error rendering.
- Verification passed:
  `npm test -- --watch=false` and `npm run build`.
  The production build refreshed `src/main/resources/static`.

### [x] 8. Update tests for backend flow

Proposed change:
Add and update tests for:

- multipart job controller,
- job service source validation,
- CSV parser,
- evidence collector/provider with uploaded logs,
- orchestrator mapping from CSV-derived logs,
- Copilot tool policy with missing Elasticsearch config.

Why:
This change touches the public start contract, evidence source selection and
tool allowlist. Unit coverage should pin the behavior before manual UI testing.

Target commands:

- `mvn -q -Dtest=AnalysisJobControllerTest,AnalysisJobFacadeTest test`
- `mvn -q -Dtest=AnalysisEvidenceCollectorTest test`
- focused parser/policy tests
- optionally `mvn -q -DskipTests compile`

Done when:

- focused backend tests pass,
- compile verifies all Java wiring.

Done:

- Audited backend coverage against the planned behavior. No additional backend
  test was needed in this step because the required cases were already covered
  by tests added during steps 1-6.
- Coverage now explicitly includes:
  - multipart job start and CSV upload forwarding in
    `AnalysisJobControllerTest`,
  - validation-level rejection before queueing for missing/invalid CSV and
    missing Elasticsearch configuration, exercised through
    `AnalysisJobFacadeTest`,
  - Kibana Discover CSV parsing, multiline stacktraces, missing columns,
    invalid CSV, empty file, multiple correlation ids and invalid timestamps in
    `ElasticLogCsvImportServiceTest`,
  - reusable Elasticsearch/Kibana availability checks in
    `ElasticConnectionAvailabilityServiceTest`,
  - CSV-uploaded logs flowing through `elasticsearch/logs` without calling
    `ElasticLogPort` in `AnalysisEvidenceCollectorTest`,
  - initial and follow-up Copilot policy removing `elastic_*` tools when
    Elasticsearch/Kibana configuration is incomplete in
    `CopilotIncidentToolAccessPolicyCoverageTest`.
- Focused backend tests and compile pass:
  `mvn -q "-Dtest=ElasticLogCsvImportServiceTest,ElasticConnectionAvailabilityServiceTest,AnalysisEvidenceCollectorTest,AnalysisJobControllerTest,AnalysisJobFacadeTest,CopilotIncidentToolAccessPolicyCoverageTest" test`
  and `mvn -q -DskipTests compile`.

### [x] 9. Update frontend tests and build

Current status:

- The planned Angular test updates and production build were completed during
  step 7 because the frontend instructions require test/build verification after
  UI changes.
- Step 9 is retained as an explicit review checkpoint and is now closed without
  additional code changes.

Proposed change:
Update Angular tests for:

- source selection,
- disabled correlation start,
- CSV FormData submission,
- backend validation error rendering.

Why:
The start panel changes user workflow and HTTP payload shape.

Target commands:

- `npm test -- --watch=false`
- `npm run build`

Done when:

- focused frontend tests pass,
- production build updates static resources through the normal build path.

Done:

- Confirmed no new frontend implementation changes were introduced after step 7.
- Frontend behavior is covered by `AnalysisConsoleComponent` tests for source
  selection, disabled Elasticsearch/correlation start, CSV upload submission and
  backend validation error rendering.
- Verification was already completed during step 7:
  `npm test -- --watch=false` and `npm run build`.
- Production build output in `src/main/resources/static` remains the expected
  generated bundle for the current frontend sources.

### [x] 10. Update architecture docs and local work plan references

Proposed change:
Update architecture documentation to reflect:

- `POST /api/analysis/jobs` supports two log sources,
- CSV upload is deterministic input gathering, not AI mapping,
- `correlationId` can be derived from uploaded logs,
- Elastic tools require configured Elasticsearch/Kibana credentials,
- downstream evidence remains `elasticsearch/logs`.

Likely files:

- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/architecture/07-open-work-plan.md` if this becomes active backlog

Why:
This changes the public start flow and tool availability rules, so future work
should not assume only REST Elasticsearch input exists.

Done when:

- docs and this plan agree with implemented behavior.

Done:

- Updated `docs/architecture/01-system-overview.md` to describe Incident
  Analysis as a log-source based flow: Elasticsearch by `correlationId` or
  uploaded Kibana/Elastic CSV, with shared downstream `elasticsearch/logs`
  evidence.
- Updated `docs/architecture/02-key-decisions.md` to document
  `POST /api/analysis/jobs` as the canonical start endpoint with
  `ELASTICSEARCH` and `CSV_UPLOAD` sources, plus config-gated `elastic_*`
  Copilot tools.
- Updated `docs/architecture/03-runtime-flow.md` to show the multipart job
  input, `GET /api/analysis/jobs/input-options`, CSV validation before job
  execution and unchanged downstream evidence pipeline.
- Updated `docs/architecture/04-codex-continuation-guide.md` so future changes
  keep the source selector, CSV-derived `correlationId` and Elastic tool
  gating rules.
- Updated `docs/architecture/07-open-work-plan.md` to include both log sources
  in future Incident Analysis smoke coverage.
- Updated `docs/architecture/08-operational-context-model-tools-and-usage.md`
  because its incident analysis consumer flow still described a
  correlation-only start.
- Verified the touched architecture docs no longer describe job start as
  `POST /analysis/jobs` with only `correlationId`.

### [ ] 11. Final end-to-end verification

Proposed change:
Run a manual or automated smoke with:

- Elasticsearch configured and start by `correlationId`,
- Elasticsearch missing and correlation field blocked,
- CSV upload using the provided Kibana sample,
- CSV with missing required column,
- CSV with invalid timestamp,
- initial Copilot prompt/manifest showing no Elastic tools when config missing.

Why:
The most important product guarantee is that upload replaces only the log fetch
step and the rest of the incident tracker behaves the same.

Done when:

- smoke results are recorded in final implementation summary,
- this plan has all implemented steps marked `[x]`.

