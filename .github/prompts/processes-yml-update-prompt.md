# processes.yml update prompt

Update only `processes.yml`.

This prompt is schema-authoritative for `processes.yml`. If a parent operational-context prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy, merge policy, quality gate and output contract for `processes.yml`.

Do not preserve legacy fields or legacy structures unless they are explicitly represented in this schema.

## Purpose

Maintain `processes.yml` as an enterprise-grade, evidence-backed, queryable map of business, operational, technical, scheduled, event-driven, data and cross-system processes.

`processes.yml` is part of a reusable operational context catalog. It is not only for incident routing. It is a process and operational-flow graph layer used for:

- deterministic mapping from runtime, code, configuration, deployment, data and documentation evidence to process areas or process steps;
- GitLab/code search scope construction across service repositories, orchestration repositories, workflow definitions, shared libraries, generated clients, schema repositories and deployment/config repositories;
- explaining end-to-end behavior to users and LLM tools;
- function description and user-facing answers about affected capabilities, workflows, state transitions, tasks, jobs, messages, data artifacts and system behavior;
- dependency, impact and change analysis across systems, runtime components, repositories, modules, integrations, bounded contexts, terms, data models and teams;
- DB/code grounding when data-source, persistence, datasource, schema, table, entity, state or reconciliation symptoms are involved;
- incident triage and coordination as one downstream view, without reducing responsibility to a single owner;
- repository onboarding, follow-up investigation, Q&A and future AI analysis features that need process and flow context.

A process entry should explain:

- what operational outcome the process produces;
- how the process starts, progresses, completes, partially completes, fails or cancels;
- which actors, systems, runtime components, repositories, modules, bounded contexts, integrations, teams and terms participate;
- which process steps are useful operational breakpoints for analysis, search and explanation;
- where the process appears in code, configuration, deployment, workflow definitions, APIs, messaging, files, databases, runtime evidence or documentation;
- which deterministic signals identify the process and each important step;
- which data artifacts, persisted entities, read models and audit artifacts matter for DB/code grounding;
- which responsibilities are explicit, shared, external, unknown or disputed;
- which completion, failure and observability signals are useful;
- how an LLM should explain, search for and disambiguate the process;
- what remains unknown after durable validation.

## Non-goals

Do not turn `processes.yml` into:

- a complete BPMN specification;
- a full workflow-engine export;
- a full product requirements document;
- a full user journey map;
- a CRUD endpoint inventory;
- a full integration catalog;
- a full database schema catalog;
- a repository map;
- a team ownership matrix;
- a long architecture essay;
- an incident escalation playbook;
- a scratchpad for temporary agent uncertainty.

Do not model every method, handler, controller endpoint, queue, status, service call, entity or enum as a separate process.

Create a separate process entry only when the difference changes at least one of:

- operational outcome;
- start, trigger, completion or terminal condition;
- lifecycle or workflow definition;
- event sequence or state model;
- process boundary or bounded context boundary;
- participants, systems or external dependencies;
- deterministic mapping signals;
- repository/code search scope;
- DB/code grounding scope;
- failure mode or runtime behavior;
- coordination or handoff behavior;
- responsibility model;
- downstream analysis behavior.

Use process steps, relations or references instead of separate process entries when the difference is only an internal method, a minor implementation detail, a helper class, an endpoint variant, an environment-specific configuration value, or one stage that has no independent operational meaning.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `processes.yml`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, documentation fragments, runtime evidence, database discovery, deployment/config evidence, workflow/BPMN definitions, existing operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, workflow repository, generated client, shared library, schema repository, deployment/config repository, documentation fragment, branch, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `systems.yml`, `runtime-components.yml`, `repo-map.yml`, `integrations.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` or `terms.yml`, `handoff-rules.md` and `operational-context-index.md`.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate processes, candidate steps, partial facts, unresolved references and repositories not yet scanned. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

## Output

Return the full updated `processes.yml` YAML only.

Do not include Markdown fences.
Do not include explanations.
Do not include diffs.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `processes.yml`.
Do not output `BUILD MEMORY` inside `processes.yml`.

The final YAML must parse successfully.

## Required top-level schema

Use this exact top-level shape. Do not preserve legacy top-level fields.

```yaml
schemaVersion: 1
catalogKind: operational-context-processes
processes: []
gaps: []
```

Top-level order must be:

1. `schemaVersion`
2. `catalogKind`
3. `processes`
4. `gaps`

Use `gaps`, not legacy untyped `openQuestions`. Durable unresolved questions are represented as typed gaps.

## Required process entry schema

Each process entry must use this structure and field order. Keep required empty lists as `[]` when there is no confirmed value. Use `null` for unknown scalar values. Empty optional fields inside `matchSignals` buckets may be omitted.

```yaml
- id: stable-process-id
  name: Human readable process name
  type: business-process | operational-process | technical-process | data-process | integration-process | workflow | scheduled-process | event-driven-process | batch-process | manual-operational-process | state-machine-process | external-process | unknown
  lifecycleStatus: active | planned | deprecated | retired | external | unknown
  criticality: critical | high | medium | low | unknown
  summary: Short local description of the process.
  operationalOutcome: What this process produces or changes operationally.
  responsibilityStatus: explicit-single | explicit-multiple | shared | unresolved | disputed | external | platform-shared | not-applicable | unknown
  useFor: []
  processBoundary:
    businessCapability: string | null
    startsWhen: []
    endsWhen: []
    includes: []
    excludes: []
    assumptions: []
  participants:
    actors: []
    primarySystems: []
    supportingSystems: []
    externalSystems: []
    platformComponents: []
  references:
    systems: []
    runtimeComponents: []
    repositories: []
    modules: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
    handoffRules: []
  dataAndArtifacts:
    primaryObjects: []
    inputArtifacts: []
    outputArtifacts: []
    persistedEntities: []
    readModels: []
    auditArtifacts: []
    dataStores: []
    notes: []
  lifecycle:
    triggers: []
    entryCriteria: []
    statuses: []
    transitions: []
    terminalStates: []
    successOutcomes: []
    partialOutcomes: []
    failedOutcomes: []
    cancellationOutcomes: []
  steps: []
  responsibilities: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  completionSignals:
    successful: []
    partial: []
    failed: []
    cancelled: []
  failureModes: []
  relations: []
  observability:
    dashboards: []
    alerts: []
    metrics: []
    traces: []
    logs: []
    healthChecks: []
    auditEvents: []
  analysisHints:
    deterministicMapping: []
    codeSearch: []
    functionDescription: []
    impactAnalysis: []
    dbGrounding: []
    incidentAnalysis: []
    qa: []
  handoffHints:
    defaultRoute: []
    requiredEvidence: []
    firstActions: []
    escalationTriggers: []
  llmToolHints:
    answerWhenUserMentions: []
    disambiguationHints: []
    commonMisreads: []
    usefulSearchKeywords: []
    explanationStyle: null
  evidence: []
  sourceCoverage:
    status: complete | partial | single-source | unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

## Required process step schema

Each process step in `steps` must use this structure and field order. A step should be an operational breakpoint that can be recognized, explained, tested, searched or coordinated. Do not include steps that merely restate every private method in code.

```yaml
- id: stable-step-id
  name: Human readable step name
  order: 10
  type: command | validation | state-transition | persistence | integration-call | message-publish | message-consume | scheduler | workflow-task | manual-task | calculation | document-generation | notification | audit | compensation | reconciliation | unknown
  required: true | false | conditional | unknown
  summary: Short description of this operational breakpoint.
  startsWhen: []
  endsWhen: []
  references:
    systems: []
    runtimeComponents: []
    repositories: []
    modules: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
  input:
    artifacts: []
    events: []
    commands: []
    dataObjects: []
  output:
    artifacts: []
    events: []
    commands: []
    dataObjects: []
    completionSignals: []
  stateTransitions: []
  responsibilities: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  completionSignals:
    successful: []
    partial: []
    failed: []
    cancelled: []
  failureModes: []
  observability:
    dashboards: []
    alerts: []
    metrics: []
    traces: []
    logs: []
    healthChecks: []
    auditEvents: []
  handoffHints:
    defaultRoute: []
    requiredEvidence: []
    firstActions: []
  evidence: []
```

## Required gap schema

Durable gaps must use this structure and field order.

```yaml
- id: stable-gap-id
  type: responsibility-ambiguity | boundary-ambiguity | start-end-ambiguity | lifecycle-ambiguity | completion-signal-missing | missing-reference | conflicting-evidence | process-split-merge-ambiguity | relation-ambiguity | data-grounding-ambiguity | topology-ambiguity | human-confirmation-required
  severity: high | medium | low
  status: open | needs-human | resolved | superseded
  summary: Short description of the durable unresolved catalog gap.
  affectedNodes: []
  impact: []
  requiredEvidence: []
  suggestedEvidenceSources: []
  evidence: []
```

A final `gap` is not a scratchpad note. Use a final gap only when available evidence was checked and the missing fact still affects deterministic mapping, code search scope, impact analysis, DB/code grounding, semantic interpretation, incident triage or user-facing explanation.

## Field semantics

### `id`

Use stable kebab-case identifiers.

Rules:

- use lowercase letters, digits and hyphens only;
- do not use spaces, slashes or underscores;
- prefer domain/process language over implementation names;
- include context when a generic process name is ambiguous;
- do not rename an existing ID unless the current ID is clearly invalid or the task explicitly asks for normalization;
- do not create duplicate entries for the same process.

Good examples:

```text
lead-qualification
customer-onboarding
subscription-renewal
invoice-settlement
catalog-synchronization
nightly-customer-segmentation-refresh
```

Bad examples:

```text
process1
backend-flow
save-customer
handler-process
customer
```

### `name`

Use a human-readable process name that an engineer, analyst, operator or LLM user can recognize.

### `type`

Use the closest process type:

- `business-process`: business-facing process with a domain outcome;
- `operational-process`: internal operational flow with business support value;
- `technical-process`: technical flow that materially affects analysis;
- `data-process`: import, export, reconciliation, enrichment or data synchronization flow;
- `integration-process`: process centered on cross-system integration behavior;
- `workflow`: explicit BPM/workflow-engine-driven flow;
- `scheduled-process`: scheduled or periodically triggered process;
- `event-driven-process`: flow primarily started or advanced by domain events or messages;
- `batch-process`: batch flow that processes many items or files;
- `manual-operational-process`: human/operator-driven flow with operational consequences;
- `state-machine-process`: flow represented by explicit state-machine transitions;
- `external-process`: process mostly owned outside the platform but operationally visible;
- `unknown`: evidence shows a process, but the type is not yet clear.

Choose the most specific type supported by evidence.

### `lifecycleStatus`

Allowed values:

- `active`: current production or active development process;
- `planned`: documented future process;
- `deprecated`: obsolete or legacy process still visible in code, logs or documentation;
- `retired`: no longer active, but retained because historical evidence may still refer to it;
- `external`: process mostly controlled by an external system or party;
- `unknown`: evidence shows the process, but lifecycle status is not confirmed.

Do not infer deprecation or retirement without explicit evidence.

### `criticality`

Allowed values:

- `critical`
- `high`
- `medium`
- `low`
- `unknown`

Use documented business or operational criticality only. Do not infer high criticality merely because a process exists or appears in production code.

### `summary` and `operationalOutcome`

`summary` is one short local description of the process.

`operationalOutcome` explains what the process produces, changes, settles, synchronizes, publishes, reconciles or makes visible.

Good:

```yaml
summary: CRM Core qualifies a submitted lead and decides whether it can become a sales opportunity.
operationalOutcome: A lead is accepted for conversion, rejected with a reason, or left pending manual review.
```

Bad:

```yaml
summary: Handles leads.
operationalOutcome: Things happen.
```

### `responsibilityStatus`

Use:

- `explicit-single`: exactly one accountable team is documented;
- `explicit-multiple`: multiple accountable or role-specific teams are documented;
- `shared`: responsibility is intentionally shared across teams or layers;
- `unresolved`: responsibility matters but no accountable team is confirmed;
- `disputed`: sources conflict or ownership is contested;
- `external`: responsibility sits outside the organization/platform;
- `platform-shared`: platform, DB, messaging, runtime or infrastructure support participates materially;
- `not-applicable`: process has no meaningful team responsibility in this catalog;
- `unknown`: evidence is insufficient.

Do not force a single owner. Do not infer accountability from repository location, package names, author names, comments, contribution history or the fact that one side of the process is visible in the current repository.

### `useFor`

List only relevant analysis uses.

Allowed values:

```text
deterministic-mapping
code-search
function-description
impact-analysis
db-grounding
incident-analysis
qa
future-analysis
```

Every process should usually include at least `deterministic-mapping`, `code-search`, `function-description` and `impact-analysis` unless it is intentionally narrow.

### `processBoundary`

Use `processBoundary` to define process scope compactly and operationally.

- `businessCapability`: high-level capability if explicit.
- `startsWhen`: request, command, event, schedule, UI action, workflow signal, manual action or external trigger that starts the process.
- `endsWhen`: completion, terminal state, emitted event, persisted result, external notification or user-visible state.
- `includes`: behaviors that belong to this process.
- `excludes`: nearby behaviors that should not be confused with this process.
- `assumptions`: short notes only when evidence implies but does not fully prove a boundary.

Do not invent process boundaries from package names alone.

### `participants`

Use `participants` to show who and what participates in the process.

- `actors`: human roles, user roles, operators or external actors.
- `primarySystems`: systems that drive or orchestrate the process.
- `supportingSystems`: internal systems that participate but do not drive the process.
- `externalSystems`: external systems participating in the process.
- `platformComponents`: brokers, gateways, workflow engines, schedulers, DB platforms or infrastructure components that materially affect the process.

Prefer canonical IDs when known. If an ID is not yet defined in the corresponding catalog, use `BUILD MEMORY` for temporary references and final `gaps` only for durable missing references.

### `references`

Use `references` to connect the process to other operational graph nodes.

- `systems`: IDs from `systems.yml`.
- `runtimeComponents`: IDs from runtime component catalog or system runtime component fields.
- `repositories`: IDs from `repo-map.yml`.
- `modules`: module IDs from `repo-map.yml`.
- `boundedContexts`: IDs from `bounded-contexts.yml`.
- `integrations`: IDs from `integrations.yml`.
- `terms`: glossary term IDs.
- `teams`: IDs from `teams.yml`.
- `handoffRules`: IDs from incident-specific routing or handoff views, only when such rules already exist.

Do not invent references. Do not create fake IDs to make the graph look complete. If a useful reference is missing, record it in `BUILD MEMORY` when it is likely to be resolved by another source, or add a durable `missing-reference` gap only after expected sources were checked.

### `dataAndArtifacts`

Use `dataAndArtifacts` to identify process artifacts and data anchors, not to describe a full data model.

- `primaryObjects`: business or operational objects central to the process.
- `inputArtifacts`: commands, DTOs, schemas, files, forms, requests, messages or documents that start or feed the process.
- `outputArtifacts`: events, DTOs, files, documents, notifications, reports or persisted outputs produced by the process.
- `persistedEntities`: entities/tables/models that store process state or process-relevant domain state.
- `readModels`: read-side views, projections or reports that make process state visible.
- `auditArtifacts`: audit records, history tables, log records or compliance evidence.
- `dataStores`: database schemas, datasources, Hikari pools, buckets, file shares or other stores relevant to grounding.
- `notes`: short caveats, especially when data ownership differs from process ownership.

Do not include personal data, raw customer records, sample payloads, secrets or production-only confidential values.

### `lifecycle`

Use `lifecycle` to capture process-level triggers, criteria, states, transitions and outcomes.

`triggers` should include concrete evidence-bearing triggers such as:

- endpoint templates;
- UI actions if documented;
- command names;
- event names;
- queue/topic/routing key names;
- scheduled job names;
- workflow/BPMN process definitions;
- state-machine events;
- external notifications.

`statuses` should include only states useful for analysis, mapping or explanation. Do not copy a full enum unless the enum values appear in logs, events, UI support language, routing rules, audit records or failure analysis.

Recommended `statuses` shape:

```yaml
statuses:
  - value: QUALIFIED
    meaning: Lead passed automated qualification and can be converted.
    evidenceSignals: [LeadStatus.QUALIFIED, lead_status]
```

Recommended `transitions` shape:

```yaml
transitions:
  - from: [SUBMITTED]
    to: QUALIFIED
    trigger: LeadQualificationCompleted
    conditions: []
    evidenceSignals: [LeadQualificationCompleted, qualifyLead]
```

### `steps`

Include steps only when they are operationally meaningful.

A useful step usually has at least one of:

- a trigger or event;
- endpoint, queue, topic, routing key or job evidence;
- a recognizable controller, handler, listener, service, workflow task or scheduler class;
- a state transition;
- a persistence checkpoint;
- an integration call;
- a completion signal;
- a routing or responsibility implication;
- a known failure mode;
- an observability signal;
- a data artifact that helps DB/code grounding.

Use stable step IDs scoped to the process. Recommended examples:

```text
receive-onboarding-request
validate-customer-profile
reserve-customer-number
publish-customer-onboarded
reconcile-segment-batch
```

Do not model every private method, DTO conversion, framework callback or repository call as a step.

### `responsibilities[]`

Use responsibility roles instead of a single owner.

Required responsibility object shape:

```yaml
- teamId: string | null
  role: process-steward | step-steward | domain-steward | runtime-operator | repo-maintainer | module-steward | integration-partner | producer | consumer | platform-support | business-owner | first-responder | worker | external-owner | unknown
  scope: string
  appliesToSteps: []
  evidence: explicit-doc | explicit-code | explicit-config | inferred-code | inferred-runtime | human-input-required
  confidence: high | medium | low
```

Rules:

- Do not infer ownership from contribution alone.
- If only participation is documented, use `role: worker`, `producer`, `consumer`, `integration-partner` or `runtime-operator`, not `process-steward`.
- If domain expertise is documented but runtime ownership is not, use `domain-steward` and explain scope.
- If runtime support is documented but domain ownership is not, use `runtime-operator` or `platform-support`.
- If responsibility is unknown and matters, set `responsibilityStatus: unresolved` and add a durable gap only after expected sources were checked.
- Avoid encoding full on-call escalation logic in this file.

Step-level `responsibilities` use the same object shape, with `appliesToSteps` usually omitted or empty because the enclosing step is the scope.

### `matchSignals`

`matchSignals` is the deterministic mapping layer. Do not hide match keys in prose.

Use four buckets:

- `exact`: signals that should usually map directly to this process or step.
- `strong`: signals that are highly indicative but may need a second signal.
- `medium`: useful search and ranking signals that are not unique.
- `weak`: aliases, broad labels or hints that should never be enough alone.

Recommended keys inside buckets:

```yaml
matchSignals:
  exact:
    workflowDefinitions: []
    stateMachineNames: []
    scheduledJobs: []
    taskTypes: []
    endpointTemplates: []
    queues: []
    topics: []
    exchanges: []
    routingKeys: []
    eventNames: []
    commandNames: []
    processInstanceKeys: []
    logMarkers: []
    metricNames: []
    spanNames: []
    exceptionClasses: []
    errorCodes: []
  strong:
    endpointPrefixes: []
    packagePrefixes: []
    modulePaths: []
    classNames: []
    controllerClasses: []
    serviceClasses: []
    handlerClasses: []
    listenerClasses: []
    publisherClasses: []
    repositoryClasses: []
    dbSchemas: []
    dbTables: []
    hikariPools: []
    configKeys: []
    serviceNames: []
    applicationNames: []
    deploymentComponentNames: []
  medium:
    artifactNames: []
    projectNames: []
    glossaryTerms: []
    statusValues: []
    dataObjects: []
    operationNames: []
    documentLabels: []
  weak:
    aliases: []
    userFacingLabels: []
    teamLabels: []
    broadKeywords: []
```

Rules:

- At least one `exact` or `strong` signal should usually exist for a process, unless a durable gap explains why the process is known but not yet directly mappable.
- Use exact signal values as they appear in code/config/logs/specs when safe.
- Do not add secrets, credentials, tokens, full authorization headers, customer data, raw payloads or production-only sensitive values.
- Do not promote broad words such as `customer`, `process`, `sync`, `job`, `handler` or `service` to strong signals unless they are locally unique and evidence-backed.
- Do not treat absence of a signal in one scan as evidence that the signal does not exist globally.

### `completionSignals`

Use `completionSignals` for evidence that indicates process outcomes.

- `successful`: terminal state, event, persisted record, notification, audit, workflow completion or log marker showing success.
- `partial`: partial success, retry scheduled, item-level failures, deferred manual review or incomplete batch.
- `failed`: terminal failure, rejection event, exception, DLQ, compensation or failure state.
- `cancelled`: cancellation, abandonment, timeout or manual stop.

Keep signals concrete and searchable.

### `failureModes[]`

Use failure modes only when they improve diagnosis, mapping, impact analysis or coordination.

Recommended shape:

```yaml
failureModes:
  - id: stable-failure-mode-id
    description: Short evidence-facing description.
    likelyStage: process | step:<step-id> | integration | persistence | validation | workflow-engine | scheduler | external | platform | unknown
    symptoms: []
    signals: []
    likelyImpact: []
    requiredEvidence: []
    firstChecks: []
```

Do not copy long troubleshooting runbooks. Put routing overrides in `handoff-rules.md` and link them through `references.handoffRules` only when canonical rules exist.

### `relations[]`

Use relations to model process-to-process and process-to-graph dependencies.

Recommended shape:

```yaml
relations:
  - target: process:other-process-id
    relationType: parent | subprocess | predecessor | successor | triggers | triggered-by | compensates | retries | enriches | consumes-output | produces-input | shares-state | supports | blocks | alternative-flow | unknown
    direction: inbound | outbound | bidirectional | unknown
    via:
      integrations: []
      systems: []
      boundedContexts: []
      events: []
      endpointTemplates: []
      queuesOrTopics: []
      dataObjects: []
      codeSignals: []
    analysisHint: Short impact or sequencing hint.
    confidence: high | medium | low
```

Rules:

- Prefer `target` values with explicit prefixes, such as `process:<id>`, `system:<id>`, `integration:<id>`, `bounded-context:<id>`, `repository:<id>`, `team:<id>` or `term:<id>`.
- Do not create relations just because two processes appear in the same repository.
- Create a relation when evidence shows trigger flow, event flow, workflow composition, subprocess composition, dependency, state sharing, output/input dependency, compensation, retry, operational coordination or semantic coupling.

### `observability`

Capture only observability facts that help mapping, diagnosis, explanation or handoff.

Examples:

- dashboard names or IDs;
- alert names;
- metric names;
- trace span names;
- log markers;
- health checks;
- audit events;
- workflow/task IDs in logs;
- correlation or trace fields when documented and safe.

Do not invent dashboard, alert or metric names. Do not store raw log excerpts containing sensitive data.

### `analysisHints`

Use compact reusable hints for downstream analysis.

- `deterministicMapping`: which signals are most discriminating and how to map evidence to the process.
- `codeSearch`: where and how to search code.
- `functionDescription`: how to describe the process or affected function in user-facing language.
- `impactAnalysis`: what may be affected when the process fails or changes.
- `dbGrounding`: entity/table/schema/datasource hints when process state or persistence is involved.
- `incidentAnalysis`: evidence useful for incident triage, without full handoff rules.
- `qa`: how to explain the process plainly.

Do not encode deterministic routing policy here.

### `handoffHints`

`handoffHints` is not ownership and not a full incident routing view.

Use it only for compact reusable coordination hints:

- `defaultRoute`: role labels or canonical teams to consider first when evidence supports them.
- `requiredEvidence`: concrete evidence needed before useful escalation.
- `firstActions`: short checks that are safe and generally useful.
- `escalationTriggers`: when to involve partner, platform, workflow, scheduler, broker, DB, identity provider, external owner or mediator owner.

Detailed handoff, on-call logic and escalation policy belong to incident-specific routing views, not to `processes.yml`.

### `llmToolHints`

Use short hints that help an LLM answer questions, explain the process or decide what to search next.

- `answerWhenUserMentions`: terms, questions or evidence that should cause this process to be retrieved.
- `disambiguationHints`: how to distinguish neighboring processes, contexts or integrations.
- `commonMisreads`: common wrong interpretations.
- `usefulSearchKeywords`: concise keywords for follow-up code search.
- `explanationStyle`: how to explain this process, for example `business-first`, `technical-first`, `operator-focused` or `data-grounding-first`.

Good hints:

- explain that a scheduler starts the process but does not own the business outcome;
- distinguish customer onboarding from customer profile update;
- warn that generated client classes live in a shared client repository;
- identify which exact workflow ID, event, endpoint or status is most discriminating;
- explain that a table stores process state, not the canonical domain aggregate.

Do not put long diagnostic playbooks here.

### `evidence[]`

Every added or updated fact must be grounded in evidence.

Evidence shape:

```yaml
evidence:
  - source: code:repo-id:path/to/File.java
    type: code | config | deployment | runtime | doc | test | spec | database | build-memory | human
    observation: Short observation.
    supports: []
    confidence: high | medium | low
```

Rules:

- `source` should use a concise prefix such as `code:`, `config:`, `deployment:`, `runtime:`, `doc:`, `test:`, `spec:`, `database:`, `build-memory:` or `human:`.
- `observation` should explain what was seen, not repeat a long file excerpt.
- `supports` should list fields or facts supported by the evidence, for example `matchSignals.exact.endpointTemplates`, `lifecycle.transitions`, `steps.validate-customer-profile`, `responsibilities.runtime-operator`.
- Use `confidence: high` for explicit code/config/workflow/documentation evidence.
- Use `confidence: medium` when multiple signals strongly imply the fact but no single source says it directly.
- Use `confidence: low` only for candidate facts that remain useful but should be treated carefully.
- Do not include secrets, raw confidential payloads, personal data or long copyrighted excerpts.

### `sourceCoverage`

Use `sourceCoverage` to prevent false certainty.

- `status: complete` means expected sources for this process were scanned and evidence is sufficient for the current catalog purpose.
- `status: partial` means some expected sources are not scanned or certain dimensions remain unverified.
- `status: single-source` means only one repository/document/source currently supports the entry.
- `status: unknown` means scan scope is unclear.

`scannedSources` should include repositories, docs, configs, workflow definitions or runtime evidence actually inspected.

`expectedSources` should list sources that would improve confidence, such as consuming service repository, workflow definition repository, external system docs, deployment/config repository, ownership docs or message schema repository.

`limitations` should be concise and should not become a scratchpad.

### `gaps[]`

Final `gaps` represent durable catalog gaps after available evidence has been checked, or issues requiring human/domain input.

Use final gaps for:

- unresolved responsibility after support/ownership evidence was checked;
- unclear process boundary after sufficient evidence;
- unclear start/end condition that affects analysis;
- unclear lifecycle/status semantics that affect mapping or explanation;
- missing completion signal after available sources were checked;
- missing referenced catalog node after expected sources were checked;
- conflicting process definitions across repositories or documentation;
- ambiguous process split/merge decision requiring human/domain confirmation;
- unclear relationship between processes that affects impact analysis;
- unclear DB/code grounding for process state after expected sources were scanned.

Do not use final gaps for temporary cross-repo joins that are still pending in `BUILD MEMORY`.

## Core principles

### 1. Processes are operational graph nodes

Treat each process as a first-class node in the operational graph.

A process may connect to:

- systems;
- runtime components;
- repositories and modules;
- bounded contexts;
- integrations;
- terms;
- teams and responsibilities;
- other processes;
- database entities, schemas or tables;
- events, queues, topics, workflow definitions and state machines;
- documents, files, reports and audit artifacts.

Do not write broad documentation. Keep facts compact, structured, evidence-backed and useful for analysis.

### 2. Model operationally meaningful flows, not every code path

A process should represent a stable flow or operational capability that matters for analysis.

Good process examples:

- lead qualification;
- order fulfillment;
- customer onboarding;
- invoice settlement;
- subscription renewal;
- product catalog synchronization;
- support case escalation;
- nightly data reconciliation;
- partner data import;
- payment retry compensation.

Bad process entries:

- one arbitrary service method;
- one isolated controller endpoint with no process semantics;
- generic `save entity` behavior;
- framework initialization;
- generic authentication middleware;
- a purely technical helper used by many unrelated flows;
- one generated DTO or mapper class;
- a queue binding with no process or contract meaning.

Steps should be operational triage and analysis points, not every method call.

### 3. Responsibilities are not single ownership

Do not force a single owner.

A process may have:

- multiple responsible teams;
- different teams for different steps;
- a domain steward without runtime ownership;
- a runtime operator without domain ownership;
- integration producers and consumers;
- platform support for DB, MQ, workflow engine or scheduler;
- external participants;
- worker/contributor evidence without clear ownership;
- unknown, shared or disputed responsibility.

Use `responsibilityStatus` and `responsibilities` instead of legacy `ownerTeamId`.

### 4. Routing is only a downstream view

Do not model incident routing as the core truth of a process.

Use `analysisHints.incidentAnalysis` and `handoffHints` only for compact reusable hints that downstream incident analysis may use. Detailed handoff, escalation and on-call logic belongs to an incident-specific routing view, not to this file.

### 5. Deterministic mapping is more important than prose

Prefer concrete match signals over descriptions:

- workflow IDs;
- BPMN process definitions;
- state-machine names;
- task types;
- endpoint prefixes and templates;
- queue/topic/exchange/routing-key names;
- event names;
- command names;
- package prefixes;
- class/interface/entity names;
- controller/service/handler/listener/publisher names;
- scheduler/job names;
- database schemas/tables/entities;
- service/container/deployment names;
- configuration keys;
- log markers;
- metric/span names;
- exception classes;
- local terms and aliases.

Descriptions should help humans and LLMs interpret the process, but deterministic signals should make the process findable.

### 6. Multi-repository scans are partial by default

The current repository or documentation fragment is only a partial evidence source.

Process facts may be split across:

- orchestration service repositories;
- domain service repositories;
- workflow/BPMN repositories;
- shared library repositories;
- generated client repositories;
- integration module repositories;
- message schema repositories;
- deployment/config repositories;
- upstream or downstream service repositories;
- product, operations or team-owned documentation.

Never infer that a process, step, participant, integration, repository, module, responsibility, signal, state, transition or completion event does not exist only because it is absent from the current repository.

Do not remove, downgrade or overwrite existing confirmed catalog facts only because they are not visible in the current scan.

Temporary scan-order uncertainty must go to `BUILD MEMORY`, not to final `gaps`, when it may be resolved by scanning another known repository.

### 7. Shared library evidence is not process ownership

A shared library, generated client or integration module may contain classes used by a process without owning the process.

When the current repository is a shared library or generated client:

- capture package prefixes, classes, module names and Maven/Gradle coordinates;
- connect the library to process implementation only when consuming evidence exists;
- do not assume the shared library owns the process or step;
- record consuming systems, contexts and process joins as pending cross-repo joins in `BUILD MEMORY` unless explicitly known.

When the current repository is a consuming service:

- capture process usage, orchestration classes, controllers, events, workflows and configuration keys;
- link to existing shared library entries when evidence supports the join;
- do not invent library repository identity if it is not known.

### 8. Durable gaps only

Final `gaps` represent durable catalog gaps after available evidence has been checked, or issues requiring human/domain input.

Do not use final `gaps` for temporary cross-repo joins that are still pending in build memory.

### 9. Stable structure over compact YAML

The schema is intentionally explicit. Downstream code and LLM tools may parse this file structurally.

Keep required sections present. Use empty lists, `null`, or concise `unknown` values when facts are not confirmed.

### 10. Security and privacy

Never store:

- secrets;
- tokens;
- passwords;
- full authorization headers;
- production-only confidential values;
- personal data;
- raw customer records;
- long request/response payloads;
- raw SQL containing sensitive literals;
- raw log excerpts that include confidential data.

Store stable structural signals instead: config key names, endpoint templates, schema/table names, class names, sanitized error markers, metric names, span names and documented labels.

## Required discovery procedure

Before editing, inspect the provided repository/documentation and classify candidate facts.

### Discover process candidates from

- README, product, workflow or operations documentation;
- controller names and endpoint groups;
- service/orchestrator/facade classes;
- command handlers and application services;
- BPMN, Camunda, Temporal, Conductor, Airflow or other workflow definitions;
- explicit state machines;
- status enums and lifecycle transition logic;
- queue/topic/exchange/routing-key names;
- event producer and consumer classes;
- scheduled jobs, batch jobs and reconciliation tasks;
- database tables/entities that store process state;
- task types, work-item types and user task definitions;
- OpenAPI/AsyncAPI specs;
- generated clients and shared integration libraries;
- tests that describe end-to-end process behavior;
- logs, exception classes, metric names and span names;
- existing operational context files and build memory.

### Strong indicators of a process

Create or update a process only when at least one strong indicator exists:

- explicit process/workflow name in code or documentation;
- stable start and completion conditions;
- lifecycle/status transitions around a business or operational outcome;
- endpoint group dedicated to a stable flow;
- event sequence or message contract that represents a flow;
- BPMN/workflow/state-machine definition;
- task/user-work queue associated with a process;
- scheduled or batch flow with observable operational outcome;
- orchestration class coordinating multiple steps/systems;
- persistence model dedicated to process state;
- repeated runtime evidence pointing to the same flow.

### Weak indicators are not enough alone

Do not create a process from only:

- one generic endpoint;
- one generic service class;
- a technical folder such as `common`, `util`, `config`, `security`, `client` or `adapter`;
- a single table with no flow semantics;
- a shared library package used by many unrelated flows;
- one external integration target;
- a team name without process evidence;
- a vague user-facing noun without start/end or lifecycle evidence.

If evidence is promising but incomplete, record a candidate in `BUILD MEMORY` when possible.

### Process granularity rules

Choose the smallest process boundary that remains operationally meaningful.

Use a separate process when:

- it has a distinct start and completion condition;
- it has a distinct lifecycle or workflow definition;
- it has a different operational outcome;
- it is triggered independently;
- it has distinct deterministic signals;
- it is analyzed, routed, explained or tested separately by operators;
- it crosses a different bounded context or integration boundary in a way that affects analysis.

Use a process step instead of a separate process when:

- the behavior is meaningful only as part of the parent flow;
- it has no independent lifecycle;
- it has no separate trigger;
- it is one stage of a broader process;
- it is a technical action inside an existing process;
- it shares the same deterministic signals and responsibility model as the parent process.

Use `relations` to represent parent/subprocess, predecessor/successor, trigger, compensation or retry relationships instead of duplicating the same flow.

### Source inspection checklist

When the input is a repository, inspect at least the available sources below.

Build and modules:

- `pom.xml`, `build.gradle`, `settings.gradle`, lock files;
- Maven modules, Gradle subprojects;
- shared libraries and generated clients;
- package names and module boundaries.

Runtime identity:

- `application.yml`, `application.properties`, environment config;
- `spring.application.name`, service/application names;
- Dockerfiles, Helm charts, Kubernetes manifests;
- deployment/service/container/image names.

Entrypoints:

- REST controllers and route mappings;
- GraphQL resolvers;
- message listeners;
- scheduled jobs;
- CLI or batch entrypoints;
- public API specs.

Orchestration and flow:

- application services;
- command handlers;
- workflow definitions;
- BPMN/state-machine files;
- saga/process manager classes;
- event handlers;
- retry/compensation logic.

State and persistence:

- process state entities;
- status enums;
- transition methods;
- repositories/DAOs;
- migration files;
- database schemas/tables/entities;
- audit/history tables;
- datasource and HikariPool markers.

Messaging and integrations:

- Rabbit/Kafka listeners;
- Spring Cloud Stream bindings;
- StreamBridge usage;
- queue/exchange/topic/routing-key config;
- Feign/REST/WebClient/SOAP clients;
- generated clients;
- target hosts and base URL config keys.

Observability:

- exception classes;
- log markers;
- metric names;
- span names;
- workflow/task IDs in logs;
- correlation/trace fields.

Responsibilities:

- CODEOWNERS;
- README/support docs;
- ownership matrix;
- team labels;
- on-call or support docs;
- domain documentation.

## Build memory rules

If `BUILD MEMORY` is provided, read it before editing.

Use `BUILD MEMORY` to:

- resolve pending process candidates;
- connect process steps found in different repositories;
- avoid duplicate temporary questions;
- track repositories already scanned and expected repositories not scanned yet;
- track pending joins between service code, workflows, shared libraries, generated clients and integrations;
- track candidate relations between processes;
- avoid turning scan-order uncertainty into final catalog gaps.

Do not output `BUILD MEMORY` in `processes.yml`.

Do not treat `BUILD MEMORY` as final operational truth. Promote build-memory facts to `processes.yml` only when concrete evidence supports them.

Temporary uncertainty should stay in build memory when it may be resolved by scanning another known source. Examples:

- current repo contains a process event but the consumer repo has not been scanned;
- current repo contains a shared client but the consuming process is unknown;
- current repo references a workflow ID but the workflow definition repo has not been scanned;
- current repo contains a process step but the parent process boundary is not yet known;
- current repo contains a status enum but the process completion signal is confirmed in another repo;
- current repo shows a scheduled job but the downstream process impact is in an external system repo.

Promote a temporary question to final `gaps` only when:

- all expected sources were scanned and the fact is still unresolved;
- the missing information requires human/team/domain input;
- the uncertainty affects deterministic mapping, code search scope, impact analysis, DB/code grounding, semantic interpretation, incident triage or user-facing explanation.

## Cross-file consistency rules

Before editing, read all available operational context files, if provided:

- `systems.yml`;
- `runtime-components.yml`, if present;
- `repo-map.yml`;
- `integrations.yml`;
- `bounded-contexts.yml`;
- `teams.yml`;
- `glossary.md` or `terms.yml`;
- `handoff-rules.md` or incident routing views;
- `operational-context-index.md`;
- `BUILD MEMORY`, if provided.

Update only `processes.yml`, but keep IDs and references consistent with the full catalog.

Do not introduce a reference to a system, runtime component, repository, module, integration, bounded context, term, team or handoff rule unless:

1. that ID already exists in the corresponding file;
2. the evidence is strong enough to create a durable catalog gap; or
3. the missing reference is recorded as a pending cross-repo reference in `BUILD MEMORY`.

Relationship to other operational context files:

- `systems.yml`: runtime systems that participate in or execute the process.
- `repo-map.yml`: repositories/modules that implement, orchestrate, test or configure the process.
- `integrations.yml`: cross-system contracts used by the process.
- `bounded-contexts.yml`: semantic boundaries and local language touched by the process.
- `teams.yml`: role-based responsibility and expertise, not a single owner forced into `processes.yml`.
- `glossary.md`: local terms, aliases, statuses, events, errors and signals used to explain the process.
- `handoff-rules.md`: incident-specific routing overrides; link only when canonical rules already exist.

## Merge and update rules

### Monotonic merge rule

When updating an existing file:

- preserve confirmed facts unless contradicted by stronger evidence;
- merge new evidence into existing process entries when it refers to the same operational process;
- add new signals to the correct `matchSignals` bucket instead of replacing existing buckets;
- append evidence when a new source supports an existing fact;
- update `sourceCoverage` to reflect the actual scan scope;
- do not downgrade confidence only because the current scan is partial;
- do not delete steps, references, responsibilities or signals merely because they are absent from the current scan.

### Deduplication rules

Deduplicate by process meaning, not by exact label.

Merge entries when they represent the same process and share enough of:

- operational outcome;
- start/end conditions;
- lifecycle/state model;
- workflow definition;
- endpoint group;
- event sequence;
- orchestration classes;
- core data artifacts;
- participant systems;
- canonical terms.

Keep entries separate when they differ materially in process boundary, outcome, responsibility model, runtime behavior, analysis use, deterministic signals, or external/system dependency.

### Split and merge decisions

When evidence suggests a process should be split or merged:

- perform the split/merge only when evidence is strong;
- preserve stable IDs where possible;
- use `relations` to preserve parent/subprocess or predecessor/successor semantics;
- add evidence explaining why the split/merge is correct;
- add a `process-split-merge-ambiguity` gap when a human/domain decision is required.

### Normalization rules

Normalize:

- IDs to kebab-case;
- duplicate aliases and signal lists;
- references to canonical IDs when available;
- empty unknown lists to `[]`;
- scalar unknown values to `null` or `unknown` according to the schema;
- source prefixes in evidence.

Do not normalize away local domain language. Keep local names if they are the names used by code, operators, logs or documentation.

## YAML formatting rules

- The output must be valid YAML.
- Use `schemaVersion: 1` and `catalogKind: operational-context-processes`.
- Keep the top-level order exactly as specified.
- Keep process entry field order exactly as specified.
- Keep step field order exactly as specified.
- Use lists for plural fields even when there is one value.
- Use empty lists `[]` instead of omitting required list fields.
- Use `null` for unknown scalar references.
- Prefer concise strings over long paragraphs.
- Do not use anchors, aliases or custom YAML tags.
- Do not include Markdown fences in the final output.
- Do not include comments unless the current project explicitly allows comments in operational context YAML.

## Quality gates

Before returning the final YAML, verify:

1. The file parses as YAML.
2. Top-level shape is exactly `schemaVersion`, `catalogKind`, `processes`, `gaps`.
3. Every process has stable `id`, `name`, `type`, `lifecycleStatus`, `summary`, `operationalOutcome`, `responsibilityStatus`, `useFor`, `processBoundary`, `participants`, `references`, `dataAndArtifacts`, `lifecycle`, `steps`, `responsibilities`, `matchSignals`, `completionSignals`, `failureModes`, `relations`, `observability`, `analysisHints`, `handoffHints`, `llmToolHints`, `evidence`, and `sourceCoverage`.
4. Every step has the required step fields in order.
5. Each process has at least one strong process indicator or a durable gap explaining why it is retained.
6. Deterministic signals are concrete and bucketed into `exact`, `strong`, `medium`, `weak`.
7. No broad prose hides endpoint, workflow, event, table, class, package, job or status signals.
8. Responsibilities are role-based and do not force a single owner.
9. Incident routing is not encoded as core truth.
10. No confirmed facts were removed because the current scan is partial.
11. Temporary scan-order uncertainty stays in `BUILD MEMORY`, not in final `gaps`.
12. Final `gaps` are durable, typed and evidence-aware.
13. Cross-file references use canonical IDs or are represented as durable gaps only when appropriate.
14. No secrets, tokens, personal data, raw confidential payloads or production-only sensitive values are present.
15. The result is useful when a single process entry is retrieved by an LLM tool.

## Example

The following example illustrates shape and level of detail. Do not copy the example into a real catalog unless the facts are actually supported.

```yaml
schemaVersion: 1
catalogKind: operational-context-processes
processes:
  - id: customer-onboarding
    name: Customer Onboarding
    type: business-process
    lifecycleStatus: active
    criticality: high
    summary: CRM Core creates and validates a customer profile before downstream systems receive onboarding events.
    operationalOutcome: A submitted customer is accepted, rejected, or left pending manual review, and downstream systems are notified when onboarding succeeds.
    responsibilityStatus: explicit-multiple
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - db-grounding
      - incident-analysis
      - qa
    processBoundary:
      businessCapability: Customer onboarding
      startsWhen:
        - POST /api/customers/onboarding is received.
        - CustomerOnboardingRequested event is consumed.
      endsWhen:
        - CustomerOnboarded event is published.
        - Onboarding status reaches ONBOARDED, REJECTED, or MANUAL_REVIEW.
      includes:
        - Customer profile validation.
        - Customer identity reservation.
        - Persistence of onboarding status.
        - Publication of onboarding completion event.
      excludes:
        - Later customer profile edits.
        - Marketing campaign enrollment.
      assumptions: []
    participants:
      actors:
        - sales-operator
      primarySystems:
        - crm-core
      supportingSystems:
        - identity-service
        - notification-service
      externalSystems: []
      platformComponents:
        - kafka-broker
    references:
      systems:
        - crm-core
        - identity-service
        - notification-service
      runtimeComponents:
        - crm-core-api
      repositories:
        - crm-core-repo
        - crm-shared-domain-repo
      modules:
        - crm-core-repo/customer-onboarding
      boundedContexts:
        - customer-profile
      integrations:
        - crm-core-to-identity-service-customer-reservation
        - crm-core-customer-onboarded-events
      terms:
        - customer-profile
        - onboarding-status
        - customer-onboarded
      teams:
        - crm-core-team
        - identity-platform-team
      handoffRules: []
    dataAndArtifacts:
      primaryObjects:
        - CustomerProfile
        - OnboardingRequest
      inputArtifacts:
        - CustomerOnboardingRequest
        - CustomerOnboardingRequested
      outputArtifacts:
        - CustomerOnboarded
        - CustomerOnboardingRejected
      persistedEntities:
        - CustomerProfileEntity
        - customer_profile
      readModels:
        - customer_onboarding_view
      auditArtifacts:
        - CustomerOnboardingAudit
      dataStores:
        - crm_primary_datasource
      notes: []
    lifecycle:
      triggers:
        - POST /api/customers/onboarding
        - CustomerOnboardingRequested
      entryCriteria:
        - Customer payload contains required identity attributes.
      statuses:
        - value: REQUESTED
          meaning: Onboarding request was accepted for processing.
          evidenceSignals:
            - OnboardingStatus.REQUESTED
        - value: ONBOARDED
          meaning: Customer profile is created and downstream notification was emitted.
          evidenceSignals:
            - OnboardingStatus.ONBOARDED
            - CustomerOnboarded
        - value: MANUAL_REVIEW
          meaning: Automated validation could not decide the outcome.
          evidenceSignals:
            - OnboardingStatus.MANUAL_REVIEW
      transitions:
        - from:
            - REQUESTED
          to: ONBOARDED
          trigger: CustomerValidationPassed
          conditions: []
          evidenceSignals:
            - CustomerOnboarded
        - from:
            - REQUESTED
          to: MANUAL_REVIEW
          trigger: CustomerValidationInconclusive
          conditions: []
          evidenceSignals:
            - CUSTOMER_ONBOARDING_MANUAL_REVIEW
      terminalStates:
        - ONBOARDED
        - REJECTED
        - MANUAL_REVIEW
      successOutcomes:
        - CustomerOnboarded event published.
      partialOutcomes:
        - Manual review status persisted.
      failedOutcomes:
        - CustomerOnboardingRejected event published.
      cancellationOutcomes: []
    steps:
      - id: receive-onboarding-request
        name: Receive onboarding request
        order: 10
        type: command
        required: true
        summary: CRM Core receives an onboarding command through API or event input.
        startsWhen:
          - POST /api/customers/onboarding is called.
        endsWhen:
          - Onboarding command is accepted for validation.
        references:
          systems:
            - crm-core
          runtimeComponents:
            - crm-core-api
          repositories:
            - crm-core-repo
          modules:
            - crm-core-repo/customer-onboarding
          boundedContexts:
            - customer-profile
          integrations: []
          terms:
            - customer-profile
          teams:
            - crm-core-team
        input:
          artifacts:
            - CustomerOnboardingRequest
          events: []
          commands:
            - StartCustomerOnboarding
          dataObjects:
            - CustomerProfileDraft
        output:
          artifacts: []
          events: []
          commands:
            - ValidateCustomerProfile
          dataObjects:
            - CustomerProfileDraft
          completionSignals:
            - CustomerOnboardingController accepts request.
        stateTransitions: []
        responsibilities:
          - teamId: crm-core-team
            role: runtime-operator
            scope: Receives and validates CRM Core onboarding command input.
            appliesToSteps: []
            evidence: explicit-doc
            confidence: high
        matchSignals:
          exact:
            endpointTemplates:
              - /api/customers/onboarding
            commandNames:
              - StartCustomerOnboarding
          strong:
            controllerClasses:
              - CustomerOnboardingController
            packagePrefixes:
              - com.example.crm.customer.onboarding
          medium:
            glossaryTerms:
              - customer-profile
          weak:
            aliases:
              - customer onboarding
        completionSignals:
          successful:
            - Customer onboarding command accepted.
          partial: []
          failed:
            - CustomerOnboardingRequestRejected
          cancelled: []
        failureModes:
          - id: onboarding-request-validation-failed
            description: The onboarding request is rejected before process state is created.
            likelyStage: step:receive-onboarding-request
            symptoms:
              - HTTP 400 on onboarding endpoint.
            signals:
              - CustomerOnboardingValidationException
            likelyImpact:
              - No customer profile is created.
            requiredEvidence:
              - Request validation error marker.
            firstChecks:
              - Check request validation error and schema version.
        observability:
          dashboards: []
          alerts: []
          metrics:
            - customer_onboarding_requests_total
          traces:
            - CustomerOnboardingController.start
          logs:
            - CUSTOMER_ONBOARDING_REQUESTED
          healthChecks: []
          auditEvents: []
        handoffHints:
          defaultRoute:
            - role: first-responder
              target: crm-core-team
              condition: Evidence points to request handling or validation before integrations are called.
          requiredEvidence:
            - Endpoint, exception class, and request correlation id.
          firstActions:
            - Check validation marker before escalating to integration partners.
        evidence:
          - source: code:crm-core-repo:src/main/java/com/example/crm/customer/onboarding/CustomerOnboardingController.java
            type: code
            observation: Controller exposes POST /api/customers/onboarding and creates StartCustomerOnboarding command.
            supports:
              - steps.receive-onboarding-request.matchSignals.exact.endpointTemplates
              - steps.receive-onboarding-request.input.commands
            confidence: high
      - id: validate-and-persist-customer-profile
        name: Validate and persist customer profile
        order: 20
        type: persistence
        required: true
        summary: Customer data is validated, status is updated, and process state is persisted.
        startsWhen:
          - ValidateCustomerProfile command is handled.
        endsWhen:
          - Onboarding status is persisted as ONBOARDED, REJECTED, or MANUAL_REVIEW.
        references:
          systems:
            - crm-core
          runtimeComponents:
            - crm-core-api
          repositories:
            - crm-core-repo
          modules:
            - crm-core-repo/customer-onboarding
          boundedContexts:
            - customer-profile
          integrations: []
          terms:
            - onboarding-status
          teams:
            - crm-core-team
        input:
          artifacts:
            - CustomerProfileDraft
          events: []
          commands:
            - ValidateCustomerProfile
          dataObjects:
            - CustomerProfile
        output:
          artifacts:
            - CustomerProfileEntity
          events: []
          commands: []
          dataObjects:
            - CustomerProfile
          completionSignals:
            - Onboarding status persisted.
        stateTransitions:
          - from:
              - REQUESTED
            to: ONBOARDED
            trigger: CustomerValidationPassed
            conditions: []
            evidenceSignals:
              - OnboardingStatus.ONBOARDED
        responsibilities:
          - teamId: crm-core-team
            role: process-steward
            scope: Maintains customer onboarding state and validation behavior.
            appliesToSteps: []
            evidence: explicit-doc
            confidence: high
        matchSignals:
          exact:
            classNames:
              - CustomerOnboardingService
            dbTables:
              - customer_profile
          strong:
            repositoryClasses:
              - CustomerProfileRepository
            exceptionClasses:
              - CustomerValidationException
          medium:
            statusValues:
              - REQUESTED
              - ONBOARDED
              - MANUAL_REVIEW
          weak:
            broadKeywords:
              - validation
        completionSignals:
          successful:
            - OnboardingStatus.ONBOARDED persisted.
          partial:
            - OnboardingStatus.MANUAL_REVIEW persisted.
          failed:
            - CustomerValidationException
          cancelled: []
        failureModes: []
        observability:
          dashboards: []
          alerts: []
          metrics: []
          traces:
            - CustomerOnboardingService.validate
          logs:
            - CUSTOMER_ONBOARDING_STATUS_CHANGED
          healthChecks: []
          auditEvents:
            - CustomerOnboardingAudit
        handoffHints:
          defaultRoute: []
          requiredEvidence:
            - Persisted onboarding status and validation exception.
          firstActions:
            - Check whether the status is terminal or pending manual review.
        evidence:
          - source: code:crm-core-repo:src/main/java/com/example/crm/customer/onboarding/CustomerOnboardingService.java
            type: code
            observation: Service persists onboarding status and raises validation exceptions.
            supports:
              - lifecycle.statuses
              - steps.validate-and-persist-customer-profile.matchSignals
            confidence: high
    responsibilities:
      - teamId: crm-core-team
        role: process-steward
        scope: Owns CRM Core process behavior for customer onboarding.
        appliesToSteps:
          - receive-onboarding-request
          - validate-and-persist-customer-profile
        evidence: explicit-doc
        confidence: high
      - teamId: identity-platform-team
        role: integration-partner
        scope: Supports customer identity reservation integration used during onboarding.
        appliesToSteps: []
        evidence: explicit-doc
        confidence: medium
    matchSignals:
      exact:
        endpointTemplates:
          - /api/customers/onboarding
        eventNames:
          - CustomerOnboardingRequested
          - CustomerOnboarded
        logMarkers:
          - CUSTOMER_ONBOARDING_REQUESTED
          - CUSTOMER_ONBOARDING_STATUS_CHANGED
      strong:
        serviceNames:
          - crm-core
        packagePrefixes:
          - com.example.crm.customer.onboarding
        classNames:
          - CustomerOnboardingController
          - CustomerOnboardingService
        dbTables:
          - customer_profile
      medium:
        projectNames:
          - crm-core-repo
        glossaryTerms:
          - customer-profile
          - onboarding-status
        statusValues:
          - REQUESTED
          - ONBOARDED
          - REJECTED
          - MANUAL_REVIEW
      weak:
        aliases:
          - onboarding
          - new customer
    completionSignals:
      successful:
        - CustomerOnboarded event published.
        - OnboardingStatus.ONBOARDED persisted.
      partial:
        - OnboardingStatus.MANUAL_REVIEW persisted.
      failed:
        - CustomerOnboardingRejected event published.
        - CustomerValidationException raised.
      cancelled: []
    failureModes:
      - id: identity-reservation-timeout
        description: Customer identity reservation does not respond before onboarding timeout.
        likelyStage: integration
        symptoms:
          - Timeout while reserving customer identity.
        signals:
          - IdentityReservationTimeoutException
          - identity_reservation_duration_seconds
        likelyImpact:
          - Onboarding may remain pending or move to manual review.
        requiredEvidence:
          - Timeout exception and identity integration trace.
        firstChecks:
          - Check identity service health and retry markers.
    relations:
      - target: integration:crm-core-to-identity-service-customer-reservation
        relationType: supports
        direction: outbound
        via:
          integrations:
            - crm-core-to-identity-service-customer-reservation
          systems:
            - identity-service
          boundedContexts:
            - customer-profile
          events: []
          endpointTemplates:
            - /api/identity/customers/reservations
          queuesOrTopics: []
          dataObjects:
            - CustomerIdentityReservation
          codeSignals:
            - IdentityReservationClient
        analysisHint: Identity reservation failures can block successful onboarding but do not necessarily imply CRM profile persistence failure.
        confidence: high
    observability:
      dashboards:
        - Customer Onboarding Overview
      alerts:
        - Customer onboarding failure rate high
      metrics:
        - customer_onboarding_requests_total
        - customer_onboarding_failures_total
      traces:
        - CustomerOnboardingService.validate
      logs:
        - CUSTOMER_ONBOARDING_REQUESTED
        - CUSTOMER_ONBOARDING_STATUS_CHANGED
      healthChecks: []
      auditEvents:
        - CustomerOnboardingAudit
    analysisHints:
      deterministicMapping:
        - Prefer endpoint /api/customers/onboarding, CustomerOnboarded event, and CUSTOMER_ONBOARDING_* markers over the broad word customer.
      codeSearch:
        - Search crm-core-repo for CustomerOnboardingController, CustomerOnboardingService, and OnboardingStatus.
      functionDescription:
        - Explain as creating and validating a customer profile before downstream onboarding notifications.
      impactAnalysis:
        - Failure can block new customer onboarding and downstream notifications.
      dbGrounding:
        - Check customer_profile and onboarding status fields before querying downstream systems.
      incidentAnalysis:
        - Distinguish validation failure from identity reservation timeout.
      qa:
        - This process turns an onboarding request into a validated customer profile or a documented rejection/manual review state.
    handoffHints:
      defaultRoute:
        - role: first-responder
          target: crm-core-team
          condition: Evidence points to CRM request handling, validation, or persistence.
        - role: integration-partner
          target: identity-platform-team
          condition: Evidence points to identity reservation timeout or identity service trace.
      requiredEvidence:
        - Endpoint or event signal.
        - Onboarding status.
        - Relevant exception or trace span.
      firstActions:
        - Identify whether failure occurred before or after persistence.
        - Check whether CustomerOnboarded event was published.
      escalationTriggers:
        - IdentityReservationTimeoutException or identity-service unavailable.
    llmToolHints:
      answerWhenUserMentions:
        - onboarding
        - new customer
        - CustomerOnboarded
        - OnboardingStatus
      disambiguationHints:
        - Do not confuse customer onboarding with later customer profile updates.
        - Do not treat identity reservation as owning the whole onboarding process.
      commonMisreads:
        - A validation failure is a business outcome, not always a platform incident.
      usefulSearchKeywords:
        - CustomerOnboardingController
        - CustomerOnboardingService
        - CustomerOnboarded
        - OnboardingStatus
      explanationStyle: business-first
    evidence:
      - source: doc:crm-core-repo:docs/customer-onboarding.md
        type: doc
        observation: Documentation names Customer Onboarding as the flow that validates customers and publishes CustomerOnboarded.
        supports:
          - summary
          - operationalOutcome
          - processBoundary
          - responsibilities
        confidence: high
      - source: code:crm-core-repo:src/main/java/com/example/crm/customer/onboarding/CustomerOnboardingController.java
        type: code
        observation: Controller exposes onboarding endpoint and creates onboarding command.
        supports:
          - matchSignals.exact.endpointTemplates
          - steps.receive-onboarding-request
        confidence: high
      - source: code:crm-core-repo:src/main/java/com/example/crm/customer/onboarding/CustomerOnboardingService.java
        type: code
        observation: Service validates customer profile, persists onboarding status, and emits onboarding events.
        supports:
          - lifecycle
          - completionSignals
          - dataAndArtifacts.persistedEntities
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - crm-core-repo
        - crm-core-repo/docs/customer-onboarding.md
      expectedSources:
        - identity-service-repo
        - deployment-config-repo
      limitations:
        - Identity reservation implementation was not scanned in this update.
  - id: nightly-customer-segmentation-refresh
    name: Nightly Customer Segmentation Refresh
    type: scheduled-process
    lifecycleStatus: active
    criticality: medium
    summary: A scheduled data process refreshes customer segment read models used by CRM reporting and campaigns.
    operationalOutcome: Customer segment projections are recalculated and audit records show whether the refresh completed or partially failed.
    responsibilityStatus: shared
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - db-grounding
      - incident-analysis
      - qa
    processBoundary:
      businessCapability: Customer segmentation
      startsWhen:
        - customerSegmentationRefreshJob runs nightly.
      endsWhen:
        - SegmentRefreshCompleted audit event is recorded.
        - SegmentRefreshPartialFailure audit event is recorded.
      includes:
        - Reading active customer profiles.
        - Recalculating segment projection.
        - Writing refresh audit result.
      excludes:
        - Real-time customer profile updates.
        - Campaign delivery.
      assumptions: []
    participants:
      actors: []
      primarySystems:
        - crm-analytics
      supportingSystems:
        - crm-core
      externalSystems: []
      platformComponents:
        - postgres-primary
    references:
      systems:
        - crm-analytics
        - crm-core
      runtimeComponents:
        - crm-analytics-worker
      repositories:
        - crm-analytics-repo
      modules:
        - crm-analytics-repo/segmentation
      boundedContexts:
        - customer-segmentation
      integrations: []
      terms:
        - customer-segment
        - segment-refresh
      teams:
        - analytics-platform-team
        - crm-core-team
      handoffRules: []
    dataAndArtifacts:
      primaryObjects:
        - CustomerSegment
      inputArtifacts:
        - active customer profile rows
      outputArtifacts:
        - SegmentRefreshCompleted
        - SegmentRefreshPartialFailure
      persistedEntities:
        - customer_segment_projection
        - customer_segment_refresh_audit
      readModels:
        - customer_segment_projection
      auditArtifacts:
        - customer_segment_refresh_audit
      dataStores:
        - analytics_reporting_datasource
      notes: []
    lifecycle:
      triggers:
        - customerSegmentationRefreshJob
      entryCriteria:
        - Scheduler starts nightly refresh window.
      statuses:
        - value: COMPLETED
          meaning: Segment projection refresh finished successfully.
          evidenceSignals:
            - SegmentRefreshCompleted
        - value: PARTIAL_FAILURE
          meaning: Some customer segment records failed while the batch completed.
          evidenceSignals:
            - SegmentRefreshPartialFailure
      transitions: []
      terminalStates:
        - COMPLETED
        - PARTIAL_FAILURE
        - FAILED
      successOutcomes:
        - Customer segment projections refreshed.
      partialOutcomes:
        - Partial failure audit is written.
      failedOutcomes:
        - Refresh job fails before audit completion.
      cancellationOutcomes: []
    steps: []
    responsibilities:
      - teamId: analytics-platform-team
        role: runtime-operator
        scope: Maintains scheduled worker and segmentation refresh job.
        appliesToSteps: []
        evidence: explicit-doc
        confidence: high
      - teamId: crm-core-team
        role: domain-steward
        scope: Owns customer profile semantics consumed by segmentation.
        appliesToSteps: []
        evidence: explicit-doc
        confidence: medium
    matchSignals:
      exact:
        scheduledJobs:
          - customerSegmentationRefreshJob
        eventNames:
          - SegmentRefreshCompleted
          - SegmentRefreshPartialFailure
        dbTables:
          - customer_segment_refresh_audit
      strong:
        serviceNames:
          - crm-analytics
        classNames:
          - CustomerSegmentationRefreshJob
          - CustomerSegmentProjectionWriter
        dbTables:
          - customer_segment_projection
      medium:
        projectNames:
          - crm-analytics-repo
        glossaryTerms:
          - customer-segment
      weak:
        aliases:
          - segmentation refresh
          - nightly segments
    completionSignals:
      successful:
        - SegmentRefreshCompleted audit event recorded.
      partial:
        - SegmentRefreshPartialFailure audit event recorded.
      failed:
        - CustomerSegmentationRefreshException
      cancelled: []
    failureModes:
      - id: segment-refresh-partial-failure
        description: Nightly refresh completes with item-level failures.
        likelyStage: process
        symptoms:
          - SegmentRefreshPartialFailure audit event.
        signals:
          - SegmentRefreshPartialFailure
          - CustomerSegmentationRefreshException
        likelyImpact:
          - Some reporting or campaign segment views may be stale.
        requiredEvidence:
          - Audit event and affected segment counts.
        firstChecks:
          - Check segment refresh audit table and job logs.
    relations:
      - target: bounded-context:customer-profile
        relationType: consumes-output
        direction: inbound
        via:
          integrations: []
          systems:
            - crm-core
          boundedContexts:
            - customer-profile
          events: []
          endpointTemplates: []
          queuesOrTopics: []
          dataObjects:
            - CustomerProfile
          codeSignals:
            - CustomerProfileReadRepository
        analysisHint: Segment refresh reads customer profile data but does not own customer profile semantics.
        confidence: medium
    observability:
      dashboards:
        - Customer Segmentation Jobs
      alerts:
        - Nightly segmentation refresh failed
      metrics:
        - customer_segmentation_refresh_duration_seconds
        - customer_segmentation_refresh_failures_total
      traces: []
      logs:
        - CUSTOMER_SEGMENT_REFRESH_STARTED
        - CUSTOMER_SEGMENT_REFRESH_COMPLETED
      healthChecks: []
      auditEvents:
        - SegmentRefreshCompleted
        - SegmentRefreshPartialFailure
    analysisHints:
      deterministicMapping:
        - The scheduled job name and segment refresh audit table are the strongest signals.
      codeSearch:
        - Search crm-analytics-repo for CustomerSegmentationRefreshJob and customer_segment_refresh_audit.
      functionDescription:
        - Explain as nightly recalculation of customer segment projections.
      impactAnalysis:
        - Failure may make reporting or campaign segmentation stale.
      dbGrounding:
        - Check customer_segment_refresh_audit before inspecting customer_segment_projection rows.
      incidentAnalysis:
        - Distinguish full job failure from partial item-level failure.
      qa:
        - This process refreshes segment views used by analytics and campaigns.
    handoffHints:
      defaultRoute:
        - role: first-responder
          target: analytics-platform-team
          condition: Evidence points to scheduler, refresh worker, or projection writes.
      requiredEvidence:
        - Job name, audit event, and latest refresh status.
      firstActions:
        - Check whether the last refresh completed, partially failed, or failed before audit.
      escalationTriggers:
        - Customer profile read errors indicate CRM Core or source data involvement.
    llmToolHints:
      answerWhenUserMentions:
        - nightly segmentation
        - customer segment refresh
        - SegmentRefreshPartialFailure
      disambiguationHints:
        - Do not confuse segment refresh with campaign delivery.
      commonMisreads:
        - A stale segment projection may be caused by refresh failure even if CRM Core is healthy.
      usefulSearchKeywords:
        - CustomerSegmentationRefreshJob
        - customer_segment_refresh_audit
        - SegmentRefreshCompleted
      explanationStyle: data-grounding-first
    evidence:
      - source: code:crm-analytics-repo:src/main/java/com/example/analytics/segmentation/CustomerSegmentationRefreshJob.java
        type: code
        observation: Scheduled job recalculates customer segment projections and records refresh audit events.
        supports:
          - matchSignals.exact.scheduledJobs
          - dataAndArtifacts
          - completionSignals
        confidence: high
    sourceCoverage:
      status: single-source
      scannedSources:
        - crm-analytics-repo
      expectedSources:
        - analytics-ops-docs
        - deployment-config-repo
      limitations:
        - Operational ownership documentation was not scanned.
gaps:
  - id: customer-segmentation-refresh-ownership-ambiguity
    type: responsibility-ambiguity
    severity: medium
    status: open
    summary: It is not yet confirmed whether analytics-platform-team or crm-core-team is accountable for stale segment business impact.
    affectedNodes:
      - process:nightly-customer-segmentation-refresh
      - team:analytics-platform-team
      - team:crm-core-team
    impact:
      - Incident triage may need human confirmation when the job succeeds but source customer data is stale.
    requiredEvidence:
      - Ownership or support documentation for customer segmentation refresh.
    suggestedEvidenceSources:
      - analytics-ops-docs
      - teams.yml
      - handoff-rules.md
    evidence:
      - source: code:crm-analytics-repo:src/main/java/com/example/analytics/segmentation/CustomerSegmentationRefreshJob.java
        type: code
        observation: Code confirms scheduled job implementation but does not confirm business accountability.
        supports:
          - gaps.customer-segmentation-refresh-ownership-ambiguity
        confidence: high
```

## Input

The parent prompt or caller should provide the current file and new evidence in this form when possible:

```text
CURRENT FILE:
<current processes.yml>

NEW FACTS:
<repository scan, documentation, runtime evidence, database discovery or human facts>

SCAN SCOPE:
<what was actually inspected>

FULL OPERATIONAL CONTEXT:
<known ids and relevant summaries from other catalog files>

BUILD MEMORY:
<temporary cross-repo candidates and pending joins, if any>
```

## Output contract

Return only the full updated `processes.yml` YAML.
