# teams.yml update prompt

Update only `teams.yml`.

This prompt is schema-authoritative for `teams.yml`. If a parent operational-context builder prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy, merge policy, quality gate and output contract for `teams.yml`.

Do not preserve legacy fields or legacy structures unless they are explicitly represented in this schema.

## Purpose

Maintain `teams.yml` as an enterprise-grade, evidence-backed, queryable team, responsibility and coordination map for a reusable operational context catalog.

`teams.yml` is not a people directory, HR org chart, single-owner table or incident-routing-only document. It is the responsibility and coordination view of the operational context graph. It connects systems, runtime components, repositories, modules, bounded contexts, processes, integrations, terms, platform capabilities, external parties, recognition signals, responsibility roles, routing hints and durable gaps.

The file supports:

- deterministic mapping from runtime, code, repository, documentation, ticketing, monitoring and domain evidence to possible responsible or involved operational actors;
- GitLab/code-search scoping together with `repo-map.yml`, especially when multiple teams share a repository, generated client, library, module or deployed component;
- function description and user-facing explanations about who understands, maintains, supports, responds to or participates in a system area;
- impact analysis and change analysis across systems, runtime components, repositories, processes, integrations, bounded contexts, terms and teams;
- DB/code grounding when a datasource, schema, table, repository, entity, data domain or persistence symptom points to a responsibility area;
- incident analysis and triage as one downstream view, without reducing responsibility to a single owner;
- handoff coordination, escalation hints and partner-team involvement;
- QA, onboarding, LLM Q&A and future AI analysis features beyond incident tracking;
- identification of unresolved responsibility, routing, ownership, external-party, source-coverage or evidence gaps without inventing facts.

A team or operational actor entry should explain:

- what the team or actor is operationally relevant for;
- whether the actor is internal, external, platform, operations, data, security, QA, support, virtual or unknown;
- which graph nodes it is connected to;
- what kind of responsibility exists for each connected node;
- whether responsibility is explicit, shared, inferred, worker-only, disputed or unknown;
- which concrete runtime/code/repository/documentation/ticketing signals identify the actor area;
- how to avoid false positives and neighboring-team confusion;
- when the actor should be a candidate for deterministic mapping, investigation, handoff, impact analysis, DB/code grounding, QA or LLM answers;
- which collaboration and escalation relationships matter;
- what evidence supports each important fact;
- what remains unknown after durable validation.

## Non-goals

Do not turn `teams.yml` into:

- a people directory;
- an HR organization chart;
- a personal contact list;
- a complete enterprise RACI matrix;
- a full support rota or on-call schedule;
- a complete incident-routing rule engine;
- a full repository ownership matrix;
- a full architecture essay;
- a generic service catalog;
- a scratchpad for temporary agent uncertainty;
- a replacement for `systems.yml`, `runtime-components.yml`, `repo-map.yml`, `processes.yml`, `integrations.yml`, `bounded-contexts.yml`, `glossary.md`, `handoff-rules.md` or incident-specific routing views.

Do not add a team entry just because an individual name, commit author, branch name, ticket assignee, personal email, historical comment or one-off mention appears in evidence.

Do not store individual personal data unless the input explicitly identifies it as an approved shared operational alias. Prefer stable team names, group aliases, queue names, support labels, CODEOWNERS groups, on-call schedule references and escalation group identifiers.

Do not infer ownership from package names, directory names, commit authors, branch names, the repository currently being analyzed, or a single class/endpoint/event. These may be match or recognition signals, not ownership proof.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `teams.yml`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, documentation fragments, service catalog data, ticketing metadata, monitoring labels, runtime evidence, build reports, existing operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, branch, commit, documentation fragment, module, generated artifact, service catalog, ownership matrix, monitoring config, ticketing config or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `systems.yml`, `runtime-components.yml`, `repo-map.yml`, `processes.yml`, `integrations.yml`, `bounded-contexts.yml`, `glossary.md` or `terms.yml`, `handoff-rules.md` and `operational-context-index.md`.
- Optional `BUILD MEMORY`: temporary cross-source build memory for scan-order uncertainty, pending joins, candidate teams, unresolved references, source coverage and repositories/documents not yet scanned. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

If some inputs are missing, do the best possible update using available evidence. Do not invent missing facts.

## Output

Return the full updated `teams.yml` YAML only.

Do not include Markdown fences.
Do not include explanations.
Do not include diffs.
Do not include partial snippets.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `teams.yml`.
Do not output `BUILD MEMORY` inside `teams.yml`.

The final YAML must parse successfully.

## Required top-level schema

Use this exact top-level shape. Do not preserve legacy top-level fields.

```yaml
schemaVersion: 1
catalogKind: operational-context-team-map
teams: []
externalParties: []
gaps: []
```

Top-level order must be:

1. `schemaVersion`
2. `catalogKind`
3. `teams`
4. `externalParties`
5. `gaps`

Use `gaps`, not legacy untyped `openQuestions`. Durable unresolved questions are represented as typed gaps.

Use `externalParties` for vendors, partner teams, external system owners, external platform providers, regulators or other non-internal operational actors referenced by integrations, responsibilities, routing hints or gaps. Do not force non-internal actors into `teams`.

## Core principles

### 1. Teams are operational graph actors, not owner labels

Treat `teams.yml` as part of the operational graph.

The graph connects:

- internal teams and external parties;
- systems and runtime components;
- repositories, modules, generated clients, libraries and deployment/config repos;
- bounded contexts and local vocabulary terms;
- business, operational, technical and data processes;
- integration contracts and integration sides;
- platform capabilities such as databases, brokers, runtime platforms, observability, identity, security and networking;
- deterministic recognition signals;
- responsibilities with role, scope, side, status, confidence and evidence;
- routing, handoff and collaboration hints;
- durable knowledge gaps.

A single system, repository, module, process, bounded context, integration, runtime component or term may involve several teams with different roles. It may also have no known responsible team. Represent that explicitly.

### 2. Responsibility is role-based, not single ownership

Do not reduce responsibility to a single owner unless evidence explicitly proves it.

A graph node may have:

- accountable business or domain ownership;
- runtime operation responsibility;
- repository or module maintenance;
- domain stewardship without runtime ownership;
- first-responder responsibility without final fix ownership;
- platform support without application behavior ownership;
- producer-side and consumer-side integration responsibility;
- generated-client or shared-library maintenance;
- data ownership or data-quality stewardship;
- security, QA or support responsibility;
- worker/contributor participation without ownership;
- external owner responsibility;
- shared, disputed, inferred or unknown responsibility.

Use `responsibilities[]` with `targetType`, `targetId`, `role`, `scope`, `side`, `status`, `confidence` and `evidence`. Do not use legacy `ownerTeamId`-style fields.

### 3. Participation is not ownership

Never convert these into accountability, domain stewardship, runtime operation or repository maintenance unless a reliable source explicitly says so:

- worker/participant in a process;
- commit author, last editor or ticket assignee;
- package name, directory name or module name;
- team name in a branch, pipeline, deployment label or ticket comment;
- a single endpoint path;
- a single class, interface, enum, DTO or package prefix;
- the repository currently being analyzed;
- a Slack/Jira/comment mention without stable operational meaning;
- a generated client class found in a stacktrace.

These facts may become recognition signals, candidate responsibilities or build-memory joins, but not confirmed ownership.

### 4. Routing and handoff are downstream views

Do not encode incident routing as the core truth of a team.

Use `routingHints`, `handoffHints`, `analysisHints.incidentAnalysis` and cross-file handoff-rule references only for reusable coordination hints. Detailed escalation trees, paging rules and incident-specific routing overrides belong to an incident-specific routing view or `handoff-rules.md`, not to this file.

A team can be a first responder, domain expert, integration partner, platform support team, escalation target or not-the-final-owner. Preserve these distinctions.

### 5. Deterministic signals are more important than prose

Prefer concrete, searchable signals over long descriptions:

- team IDs, team names, aliases, CODEOWNERS groups, support queues and on-call schedule references;
- GitLab group/project paths, repository IDs, module IDs and project names;
- service, application, deployment, container and runtime component names;
- package prefixes, class/interface/enum names and generated-client hints;
- endpoint prefixes and templates;
- queues, topics, exchanges, routing keys, event names and schema names;
- datasource names, Hikari pool names, database schemas/tables/entities and data domains;
- workflow names, job names, scheduler names and process IDs;
- bounded context IDs, integration IDs and glossary terms;
- log markers, exception classes, error codes, metric names and trace/span names;
- ticketing labels, monitoring labels, support labels and recurring operator labels.

Descriptions help humans and LLMs interpret facts, but deterministic signals make the file usable as an index.

### 6. Match signals are not ownership proof

`matchSignals` and `recognition` connect evidence to likely team relevance. They do not prove ownership by themselves.

Use exact and strong signals to rank candidate teams or scope follow-up investigation. Use responsibility evidence to establish responsibility. Weak signals alone must never produce a confirmed responsibility, primary route or accountable owner.

### 7. Multi-source scans are partial by default

The current repository, documentation fragment or runtime evidence snapshot is only a partial source.

Responsibility facts may be split across:

- service repositories;
- shared library repositories;
- generated client repositories;
- integration module repositories;
- schema/message repositories;
- deployment/config repositories;
- workflow/config repositories;
- team ownership repositories;
- service catalogs;
- CODEOWNERS files;
- monitoring, alerting or ticketing configuration;
- runbooks and support matrices;
- upstream/downstream service repositories;
- domain or product documentation.

Never infer that a team, responsibility, on-call target, routing hint, external party or ownership relationship does not exist only because it is absent from the current source.

Do not remove, downgrade, overwrite or invalidate an existing evidence-backed team fact only because it is not visible in the current scan.

Temporary scan-order uncertainty must go to `BUILD MEMORY`, not final `gaps`, when it may be resolved by scanning another known source.

### 8. Durable gaps only

Final `gaps` represent durable catalog gaps after available evidence has been checked, or issues requiring human/domain/team input.

Use final gaps for:

- unresolved responsibility after ownership/support evidence was checked;
- unclear first responder or support route after expected support sources were checked;
- missing external party or vendor owner that cannot be inferred from code;
- conflicting responsibility sources;
- stale or deprecated responsibility data without clear replacement;
- missing referenced catalog node after expected sources were checked;
- ambiguous team split/merge decision affecting mapping, impact analysis or handoff;
- human confirmation required for responsibility, routing, team boundary or external owner.

Do not use final gaps for temporary cross-repo joins, candidates or scan-order uncertainty still pending in build memory.

### 9. Privacy and safety are mandatory

Do not include:

- personal phone numbers;
- personal email addresses;
- personal identifiers;
- private chat messages;
- credentials, secrets, tokens, API keys or passwords;
- private customer data;
- raw production payloads;
- confidential dashboard URLs exposing sensitive query parameters;
- unofficial guesses about who to contact.

Allowed non-sensitive operational references include stable team channels, group aliases, ticket queues, public support labels, CODEOWNERS groups, runbook IDs, on-call schedule IDs and documentation references.

## Required discovery procedure

Before editing, inspect the provided source material and classify candidate facts as:

- team identity;
- external party identity;
- responsibility relation;
- non-responsibility/disambiguation relation;
- deterministic match signal;
- recognition guidance;
- collaboration relation;
- routing or handoff hint;
- communication/contact hint;
- observability hint;
- cross-file reference;
- evidence;
- source coverage;
- temporary build-memory uncertainty;
- durable gap.

### Discover teams and responsibility from

- `CODEOWNERS` and equivalent owner metadata;
- `README.md`, support sections and repository docs;
- `ownership.md`, `owners.yml`, `teams.yml`, `support.md`, `oncall.md`, `runbook.md` and service catalog files;
- ownership matrices, support matrices, escalation guides and handoff rules;
- architecture decision records and bounded-context maps;
- GitLab project metadata, group paths, project descriptions and approved labels;
- deployment metadata, service labels, platform catalog descriptors and Helm/Kubernetes labels;
- monitoring dashboard labels, alert labels, support queue labels and on-call schedule references;
- ticketing configuration and stable queue/project labels;
- integration contract documentation and partner/vendor docs;
- domain documentation and product ownership notes;
- existing operational context files and build memory.

### Discover recognition signals from

- service, application, deployment, container and runtime component names;
- GitLab project paths, repository IDs, module IDs and artifact coordinates;
- package prefixes and important source directories;
- controllers, services, clients, listeners, publishers, repositories and generated client classes;
- endpoint prefixes and templates;
- queues, topics, exchanges, routing keys, event names and schema names;
- database schemas, tables, entities, repositories, Hikari pools and datasource names;
- scheduled jobs, workflow definitions, task types and state machines;
- config keys, marker names, log markers, metrics, spans and exception classes;
- local terms and aliases from glossary/terms;
- recurring labels in logs, traces, tickets and operator handoff notes.

Use code/config primarily for recognition and search scope. Use code/config as responsibility evidence only when it explicitly documents ownership or support.

### Strong indicators of a team or responsibility

Create or update a team/external-party entry when at least one strong indicator exists:

- explicit team/support/owner documentation;
- CODEOWNERS-like metadata;
- service catalog owner/support entry;
- runbook or support matrix mapping a component, process, context or integration to a team;
- stable ticket queue or support group label;
- monitoring/alert routing label tied to a team;
- approved group alias in repository metadata;
- explicit human-provided team responsibility statement;
- repeated cross-source evidence pointing to the same team area.

### Weak indicators are not enough alone

Do not create confirmed responsibility from only:

- one package prefix;
- one class name;
- one endpoint;
- one commit author or last editor;
- one branch or pipeline name;
- one generic domain term;
- one external integration target;
- one worker mention;
- a shared library package used by many teams;
- the current repository name without support/ownership evidence.

If evidence is promising but incomplete, record a candidate in `BUILD MEMORY` when possible. Promote to final `teams.yml` only when concrete evidence supports it.

## Schema

The full output must be valid YAML with this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-team-map
teams: []
externalParties: []
gaps: []
```

Use only the fields defined below. Include empty arrays where a field is part of the standard shape and no value is confirmed. Use `null` only for unknown scalar values.

### `teams[]`

Each team entry must use this structure:

```yaml
- id: string
  name: string
  kind: internal-product-team | internal-platform-team | integration-team | operations-team | sre-team | data-team | security-team | qa-team | support-team | architecture-team | business-operations-team | virtual-team | mixed-team | unknown
  lifecycleStatus: active | candidate | planned | deprecated | retired | unknown
  purpose: string
  aliases: []
  useFor: []
  organizationalContext:
    department: null
    tribe: null
    valueStream: null
    platformArea: null
    region: null
    notes: []
  responsibilityStatus: explicit | shared | inferred | worker-only | disputed | unknown
  references:
    systems: []
    runtimeComponents: []
    repositories: []
    modules: []
    processes: []
    processSteps: []
    boundedContexts: []
    integrations: []
    terms: []
    handoffRules: []
    externalParties: []
  responsibilities: []
  notResponsibleFor: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  recognition:
    strongSignals: []
    weakSignals: []
    falsePositiveSignals: []
    disambiguationRules: []
  collaboration:
    upstreamTeamIds: []
    downstreamTeamIds: []
    peerTeamIds: []
    platformTeamIds: []
    integrationPartnerTeamIds: []
    externalPartyIds: []
    sharedResponsibilityNotes: []
  routingHints: []
  handoffHints:
    defaultRouteLabel: null
    firstResponderTeamIds: []
    escalationTeamIds: []
    partnerTeamIds: []
    requiredEvidence: []
    preferredEvidence: []
    expectedFirstActions: []
    whenToRouteHere: []
    whenToInvolveAsPartner: []
    whenNotToRouteHere: []
    fallbackIfAmbiguous: null
    notes: []
  communication:
    contactHints:
      channels: []
      ticketQueues: []
      onCallReferences: []
      documentation: []
    escalationNotes: []
  observability:
    dashboards: []
    logIndexes: []
    alertLabels: []
    sliOrSloNames: []
    runbookRefs: []
    supportQueueLabels: []
  analysisHints:
    deterministicMapping: []
    codeSearch: []
    functionDescription: []
    impactAnalysis: []
    dbCodeGrounding: []
    incidentAnalysis: []
    qa: []
  llmToolHints:
    preferredWhen: []
    avoidWhen: []
    explanationHints: []
    usefulForQuestions: []
    answerStyleHints: []
  evidence: []
  sourceCoverage:
    status: complete | partial | unknown
    scannedSources: []
    expectedSources: []
    missingSources: []
    limitations: []
  limitations: []
```

### `externalParties[]`

Each external party entry must use this structure:

```yaml
- id: string
  name: string
  kind: vendor | partner-team | external-system-owner | external-platform-provider | regulatory-party | customer-operator | unknown
  lifecycleStatus: active | candidate | planned | deprecated | retired | unknown
  purpose: string
  aliases: []
  useFor: []
  responsibilityStatus: explicit | shared | inferred | disputed | unknown
  references:
    systems: []
    integrations: []
    boundedContexts: []
    processes: []
    terms: []
    teams: []
  responsibilities: []
  notResponsibleFor: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  recognition:
    strongSignals: []
    weakSignals: []
    falsePositiveSignals: []
    disambiguationRules: []
  communication:
    contactHints:
      channels: []
      ticketQueues: []
      onCallReferences: []
      documentation: []
    escalationNotes: []
  handoffHints:
    defaultRouteLabel: null
    requiredEvidence: []
    expectedFirstActions: []
    whenToInvolve: []
    whenNotToInvolve: []
    fallbackIfAmbiguous: null
    notes: []
  evidence: []
  sourceCoverage:
    status: complete | partial | unknown
    scannedSources: []
    expectedSources: []
    missingSources: []
    limitations: []
  limitations: []
```

Use `externalParties` to represent non-internal owners, vendors and partner operators without pretending they are internal teams.

### `gaps[]`

Use `gaps[]` for durable, final catalog gaps only.

```yaml
- id: string
  type: responsibility-ambiguity | routing-ambiguity | missing-team | missing-external-party | missing-on-call-target | missing-evidence | stale-responsibility | conflicting-evidence | human-confirmation-required | schema-reference-gap | source-coverage-gap | team-boundary-ambiguity | external-owner-ambiguity
  severity: low | medium | high | critical
  status: open | in-review | blocked | resolved | superseded
  affectedNodes: []
  description: string
  impact: []
  suggestedEvidenceSources: []
  evidence: []
```

Temporary uncertainties caused by repository/source scan order must go to `BUILD MEMORY`, not final `gaps`.

## Field rules

### `id`

Use stable lowercase kebab-case IDs.

Good IDs:

- `crm-customer-team`
- `crm-integration-team`
- `data-platform-team`
- `identity-platform-team`
- `external-email-provider`

Bad IDs:

- `Team1`
- `CRM Customer Team`
- `customer_team`
- `owners`
- personal names.

Do not rename an existing ID unless the task explicitly asks for normalization or the ID is clearly invalid. If a rename is necessary, update all references inside `teams.yml` and add a `gap` if references in other files must be updated.

Do not create duplicate teams for aliases. Put alternative names in `aliases`.

### `kind`

Use the most specific supported kind:

- `internal-product-team`: owns or stewards product/domain capability.
- `internal-platform-team`: owns or supports platform capability.
- `integration-team`: focuses on integration contracts, gateways, generated clients or partner connectivity.
- `operations-team`: operational support team.
- `sre-team`: reliability/runtime operations team.
- `data-team`: data platform, analytics, data quality or schema ownership.
- `security-team`: identity, authorization, secret handling or security controls.
- `qa-team`: test strategy, acceptance, quality or test automation responsibility.
- `support-team`: support desk or production-support operational actor.
- `architecture-team`: architectural governance or standards actor.
- `business-operations-team`: business process owner/operator without code ownership.
- `virtual-team`: named cross-functional area or rota without one organizational team.
- `mixed-team`: team spans several categories and the distinction is not useful.
- `unknown`: not enough evidence.

### `lifecycleStatus`

Use:

- `active`: currently active.
- `candidate`: referenced but not fully confirmed.
- `planned`: documented future team or support actor.
- `deprecated`: still referenced but no longer preferred.
- `retired`: no longer active, retained because evidence may still mention it.
- `unknown`: status cannot be confirmed.

### `purpose`

Use one concise operational sentence. Do not write an organization essay.

Good:

```yaml
purpose: Handles CRM customer-profile domain changes and first-line triage for customer-profile runtime signals.
```

Bad:

```yaml
purpose: This team is involved in many systems and owns various business areas across the enterprise.
```

### `useFor`

Use only these values:

```yaml
- deterministic-mapping
- code-search
- function-description
- impact-analysis
- db-grounding
- incident-analysis
- handoff-routing
- qa
```

Include only values supported by evidence in the entry.

### `organizationalContext`

Use broad organizational placement only. Do not use department, tribe, region or value stream to infer ownership.

### `references`

Use canonical IDs from other operational-context files when known.

- `references.systems` -> `systems.yml`
- `references.runtimeComponents` -> `systems.yml` or `runtime-components.yml`, if available
- `references.repositories` -> `repo-map.yml`
- `references.modules` -> modules in `repo-map.yml`
- `references.processes` -> `processes.yml`
- `references.processSteps` -> process step IDs in `processes.yml`
- `references.boundedContexts` -> `bounded-contexts.yml`
- `references.integrations` -> `integrations.yml`
- `references.terms` -> `glossary.md` or `terms.yml`
- `references.handoffRules` -> `handoff-rules.md` or equivalent routing view
- `references.externalParties` -> `externalParties[]`

Do not invent placeholder IDs to make the graph look complete. If a referenced entity is missing and cannot be created in the current task, use `BUILD MEMORY` for pending cross-source references or a durable `gap` if it is a true catalog issue.

### `responsibilityStatus`

Set `responsibilityStatus` as follows:

- `explicit`: evidence clearly states the team or party has responsibility for its listed areas.
- `shared`: evidence clearly shows multiple teams or parties share responsibility for important areas.
- `inferred`: there is strong operational evidence but no explicit responsibility source.
- `worker-only`: evidence shows implementation, contribution or participation, but no ownership/responsibility evidence exists.
- `disputed`: sources conflict or ownership changed without clear resolution.
- `unknown`: actor exists, but no reliable responsibility mapping is available.

## Responsibility model

A responsibility item represents a specific relationship between an actor and an operational graph node.

Use this shape inside `teams[].responsibilities`, `externalParties[].responsibilities`, and, where appropriate, `routingHints[].partnerResponsibilities`.

```yaml
- targetType: system | runtime-component | repository | repository-module | process | process-step | bounded-context | integration | integration-side | term | data-domain | data-store | platform-capability | external-party | handoff-rule
  targetId: string
  role: runtime-operator | domain-steward | repo-maintainer | module-steward | integration-contract-steward | producer | consumer | platform-support | business-owner | data-owner | security-owner | qa-owner | support-contact | first-responder | escalation-target | generated-client-maintainer | shared-library-maintainer | worker | contributor | unknown
  scope: string
  side: source | target | consumer | producer | intermediary | platform | not-applicable | unknown
  status: confirmed | candidate | inferred | disputed | unknown
  confidence: high | medium | low
  evidence: []
```

### Responsibility role semantics

Use these meanings consistently:

- `runtime-operator`: responsible for running or operating a deployed runtime component.
- `domain-steward`: responsible for local domain semantics or business rules.
- `repo-maintainer`: responsible for repository-level maintenance, build and shared conventions.
- `module-steward`: responsible for a concrete module, package area, generated client, source set or library submodule.
- `integration-contract-steward`: responsible for the functional or technical contract of an integration.
- `producer`: owns the producing side of an event, API, data feed, message or contract.
- `consumer`: owns the consuming side of an event, API, data feed, message or contract.
- `platform-support`: owns shared infrastructure or platform capability, not necessarily application behavior.
- `business-owner`: owns business outcome or policy, not necessarily code.
- `data-owner`: owns schemas, data quality, table semantics or data access model.
- `security-owner`: owns authentication, authorization, secret handling or security controls.
- `qa-owner`: owns test strategy, acceptance quality or test automation for an area.
- `support-contact`: known support contact or queue, but not necessarily owner.
- `first-responder`: documented initial triage actor, possibly not the final fix owner.
- `escalation-target`: actor involved after first-line triage.
- `generated-client-maintainer`: maintains generated client code or schema generation pipeline.
- `shared-library-maintainer`: maintains shared library code consumed by multiple teams.
- `worker` or `contributor`: evidence shows participation, but not ownership.
- `unknown`: the relationship exists but the role is not known.

Do not convert `worker`, `contributor`, commit author, ticket assignee or one-off documentation mention into `domain-steward`, `repo-maintainer`, `runtime-operator` or `business-owner` without explicit evidence.

### `notResponsibleFor`

Use `notResponsibleFor` when explicit evidence or strong disambiguation prevents wrong routing or ownership assumptions.

```yaml
- targetType: system | runtime-component | repository | repository-module | process | bounded-context | integration | term | data-domain | platform-capability | external-party
  targetId: string
  reason: string
  evidence: []
```

Good uses:

- a platform team maintains shared DTO serialization but does not own customer-domain behavior;
- a domain team knows the business term but does not operate the database platform;
- a support team is first responder but not final code owner;
- a repository name is misleading and belongs to multiple modules.

## Match signal model

`matchSignals` helps deterministic mapping and LLM tools connect observed evidence to a likely team or external party.

Use four buckets:

```yaml
matchSignals:
  exact: {}
  strong: {}
  medium: {}
  weak: {}
```

### Exact signals

Use `exact` for values that identify a team or operational actor directly.

Typical exact fields:

```yaml
exact:
  teamIds: []
  teamNames: []
  aliases: []
  ticketQueues: []
  onCallReferences: []
  ownershipLabels: []
  codeOwnerGroups: []
  supportGroupLabels: []
  serviceCatalogOwnerIds: []
```

### Strong signals

Use `strong` for values that strongly point to the team area when seen in runtime, code, logs, monitoring, repository metadata or docs.

Typical strong fields:

```yaml
strong:
  serviceNames: []
  applicationNames: []
  deploymentNames: []
  containerNames: []
  runtimeComponentIds: []
  repositoryIds: []
  projectNames: []
  projectPaths: []
  moduleIds: []
  packagePrefixes: []
  endpointPrefixes: []
  queueNames: []
  topicNames: []
  exchangeNames: []
  routingKeys: []
  eventNames: []
  schemaNames: []
  datasourceNames: []
  hikariPools: []
  tableNames: []
  boundedContextIds: []
  processIds: []
  integrationIds: []
  platformCapabilityIds: []
```

### Medium signals

Use `medium` for useful but ambiguous values that should be combined with other evidence.

Typical medium fields:

```yaml
medium:
  classHints: []
  interfaceHints: []
  enumHints: []
  generatedClientHints: []
  configPrefixes: []
  markerNames: []
  spanNames: []
  metricNames: []
  logMarkers: []
  exceptionClasses: []
  domainTerms: []
  documentSections: []
  workflowNames: []
  jobNames: []
```

### Weak signals

Use `weak` for hints that help ranking or disambiguation but are not enough for deterministic assignment.

Typical weak fields:

```yaml
weak:
  commitAuthorDomains: []
  historicalMentions: []
  informalNames: []
  genericTerms: []
  nearbyPackages: []
  branchNames: []
  ticketMentions: []
```

Never use weak signals alone to assign a team as owner, primary responsibility or primary routing target.

## Recognition guidance

Use `recognition` to explain how to interpret signals and avoid wrong mapping.

```yaml
recognition:
  strongSignals: []
  weakSignals: []
  falsePositiveSignals: []
  disambiguationRules: []
```

Good examples:

```yaml
recognition:
  strongSignals:
    - "serviceName `crm-customer-service`"
    - "package prefix `com.example.crm.customer`"
    - "event `customer.profile.changed`"
  weakSignals:
    - "generic `customerId` fields because many CRM modules use them"
  falsePositiveSignals:
    - "shared DTO package `com.example.crm.shared.customer`"
  disambiguationRules:
    - "Route shared DTO serialization issues to CRM Platform unless the stacktrace includes `CustomerProfileService`."
```

Do not put long architecture prose in `recognition`. Keep it actionable for deterministic mappers and LLM tools.

## Routing hints

Routing hints are use-case-specific suggestions, not core ownership truth.

Use `routingHints` when evidence supports likely next teams for a specific task or incident pattern. Do not force a routing hint when responsibility is shared or unknown.

Routing hint schema:

```yaml
- id: string
  intent: incident-analysis | code-investigation | impact-analysis | function-description | db-grounding | qa | handoff-routing
  when:
    signals: {}
    requiredEvidence: []
    exclusions: []
  routeTo: string | null
  candidateTeams: []
  partnerTeams: []
  externalParties: []
  partnerResponsibilities: []
  firstAction: string
  confidence: high | medium | low
  reason: string
```

Rules:

- `routeTo` may be `null` when there is no single first destination.
- `candidateTeams` may include the current team and related teams.
- `partnerTeams` should include teams that often need to be involved.
- `externalParties` should reference entries from `externalParties[]` when known.
- `requiredEvidence` should be concrete: service name, endpoint, class, package, queue, topic, host, exception, table, correlation ID, environment, repository, file path, branch, deployment name or alert label.
- Do not create a routing hint just because a team appears in docs.
- Do not use routing hints to override the responsibility model.

## Collaboration and handoff

Use `collaboration` to show team-to-team relationships without converting them into ownership.

Examples:

- a domain team is upstream of a notification team;
- an integration team is partner for external CRM APIs;
- a platform team supports DB/MQ/runtime issues;
- a data team shares analytics/data-quality responsibility with a domain team;
- an external vendor owns target-side contract behavior.

Use `handoffHints` to guide operational coordination:

- `whenToRouteHere`: when this team is a good first candidate.
- `whenToInvolveAsPartner`: when this team should participate but not be primary.
- `whenNotToRouteHere`: when similar evidence would be a false route.
- `requiredEvidence`: concrete evidence needed before routing.
- `preferredEvidence`: evidence that improves confidence but is not mandatory.
- `expectedFirstActions`: concise first checks or safe next steps.
- `fallbackIfAmbiguous`: what to do if evidence is insufficient.

`defaultRouteLabel` must be a stable team/support label, not a personal name.

## Communication and contact rules

Use `communication.contactHints` for stable, non-sensitive operational contact hints only.

Allowed examples:

- public team channel names;
- ticket queue names;
- documented on-call schedule references;
- support documentation links or IDs;
- escalation guide references.

Do not include:

- personal phone numbers;
- personal email addresses;
- credentials;
- secrets;
- tokens;
- private personal identifiers;
- unofficial guesses about who to contact.

If on-call target is missing, add a durable `gap` only when this affects routing or operational use. If it may be resolved by a known support document that has not been scanned yet, add it to `BUILD MEMORY` instead.

## Observability

Use `observability` for high-level, non-sensitive operational references:

- dashboard labels or IDs;
- log index names;
- alert labels;
- SLI/SLO names;
- runbook references;
- support queue labels.

Do not include secret dashboard URLs, customer data, tokens, private query URLs or credentials.

## Analysis hints

`analysisHints` should help downstream AI and deterministic features. Keep hints compact and reusable.

- `deterministicMapping`: how to map evidence to this team or actor.
- `codeSearch`: repository/module/package signals that should shape code search.
- `functionDescription`: how to explain this team's functional area.
- `impactAnalysis`: how changes or incidents in linked nodes may affect this team.
- `dbCodeGrounding`: data, schema, table or persistence clues that matter.
- `incidentAnalysis`: incident-specific but reusable triage hints.
- `qa`: testing, acceptance or quality implications.

Do not encode detailed incident runbooks or escalation trees here.

## LLM tool hints

Use `llmToolHints` to guide an LLM operational-context tool answer.

Good uses:

- what this team is usually relevant for;
- when not to over-route to this team;
- how to explain shared responsibility;
- how to distinguish first response from ownership;
- which safe follow-up evidence to ask for;
- which neighboring teams or external parties are often involved.

Do not include hidden chain-of-thought, personal contact data or secrets.

## Evidence model

Every important responsibility, team signal, routing hint, handoff hint, collaboration relation, external party and durable gap must be evidence-backed.

Use this evidence object shape:

```yaml
- sourceType: repository-file | documentation | codeowners | ownership-matrix | service-catalog | deployment-config | runtime-config | monitoring-config | ticketing-config | chatops-config | code-symbol | build-file | generated-client | operational-context | manual-input | unknown
  source: string
  detail: string
  supports: []
  confidence: high | medium | low
```

Examples:

```yaml
- sourceType: codeowners
  source: "crm-api-repo:CODEOWNERS"
  detail: "The `/src/main/java/com/example/crm/customer/**` path is assigned to `@crm/customer-team`."
  supports:
    - "responsibility:crm-customer-team:crm-customer-repo"
  confidence: high

- sourceType: documentation
  source: "crm-api-repo:docs/ownership.md#customer-profile"
  detail: "Customer Profile is documented as a shared area between CRM Customer Team and CRM Data Team."
  supports:
    - "responsibility:crm-customer-team:customer-profile"
    - "responsibility:crm-data-team:customer-profile-data-domain"
  confidence: high
```

### Evidence strength

Use `high` when the fact is explicitly stated in a reliable source:

- CODEOWNERS;
- ownership matrix;
- service catalog;
- team support document;
- official architecture or operations documentation;
- deployment ownership metadata;
- documented on-call/ticket queue mapping;
- explicit manual input from the user.

Use `medium` when the fact is supported by multiple consistent but indirect sources:

- package/module naming plus repository docs;
- endpoint ownership by module plus service documentation;
- integration client plus consuming process evidence;
- bounded context references across several files;
- repeated team labels in ticketing/configuration.

Use `low` for weak, historical or ambiguous signals:

- commit authors;
- old comments;
- informal naming;
- partial documentation;
- one-sided integration code;
- unconfirmed shared-library hints.

Low-confidence facts must not become confirmed primary responsibility or final routing targets without stronger evidence.

## Source coverage

Use `sourceCoverage` to show how complete the evidence base is.

```yaml
sourceCoverage:
  status: complete | partial | unknown
  scannedSources: []
  expectedSources: []
  missingSources: []
  limitations: []
```

- `complete`: all expected sources for this entry were checked.
- `partial`: useful evidence exists, but important expected sources are missing.
- `unknown`: the source scope is unclear or only legacy facts exist.

Do not lower confidence solely because the current scan is partial. Lower confidence only when the evidence itself is weak or conflicting.

## Build memory rules

If `BUILD MEMORY` is provided, read it before editing.

Use `BUILD MEMORY` to:

- track sources already scanned;
- track expected sources not yet scanned;
- track candidate teams and external parties;
- track unresolved team IDs, aliases and CODEOWNERS groups;
- track pending joins between repositories, modules, runtime components, integrations, processes, contexts and teams;
- track temporary questions caused by scan order;
- avoid duplicate unresolved questions;
- avoid converting temporary uncertainty into final gaps.

`BUILD MEMORY` is not final operational truth. Promote facts from `BUILD MEMORY` into `teams.yml` only when concrete evidence supports them.

Use build-memory temporary questions for uncertainties that may be resolved by scanning another known source. Use final `gaps` only for durable catalog gaps.

### Cross-source join keys

When a team responsibility appears incomplete, record join keys in `BUILD MEMORY` where appropriate:

- team ID, team name, alias, ticket queue, on-call reference;
- GitLab group, project path, CODEOWNERS group;
- repository ID, module ID, package prefix, class/interface name;
- service name, runtime component ID, deployment name, container name;
- endpoint prefix, host, base URL property;
- queue, topic, exchange, routing key, event name;
- datasource, schema, table, entity, repository class;
- process ID, step ID, workflow name, task type;
- bounded context ID, term ID, domain synonym;
- documentation section, ownership matrix row, ticketing label;
- monitoring label, dashboard label, alert route or runbook ID.

Do not create a final responsibility edge unless the join is supported by one high-confidence source or by multiple consistent medium-confidence sources.

## Merge policy

Updates are additive and conservative by default.

When updating existing entries:

- preserve existing evidence-backed facts;
- add new evidence, signals, responsibilities, non-responsibility relations, routing hints, handoff hints, collaboration links and references;
- merge arrays without duplicates;
- keep stable IDs;
- update lifecycle status only with evidence;
- mark outdated facts as `deprecated`, `retired`, `superseded` or a `gap` only when explicit evidence supports it;
- do not delete an entry solely because it was not observed in the current source;
- do not reduce shared responsibility to single ownership;
- do not promote worker/contributor evidence to ownership.

You may remove or replace a fact only when:

- new evidence explicitly contradicts the old value;
- the old entry is a duplicate of a more canonical entry;
- the old value is syntactically invalid;
- the old value contains unsafe data;
- the task explicitly asks for cleanup or normalization.

### Team split and merge rules

Split a team entry when:

- evidence shows two different operational actors behind one label;
- the same label represents different teams in different organizations or regions;
- internal and external responsibility were incorrectly merged;
- platform support and domain ownership were incorrectly collapsed and it affects analysis.

Merge entries when:

- they represent the same stable team and only differ by alias;
- duplicate entries have the same references, responsibilities and evidence;
- a historical name should become an alias of the canonical active team.

When split/merge affects references in other files that cannot be updated, add a durable `gap`.

## Cross-file consistency rules

When possible, read the full operational context before updating this file:

- `systems.yml`;
- `runtime-components.yml`, if present;
- `repo-map.yml`;
- `processes.yml`;
- `integrations.yml`;
- `bounded-contexts.yml`;
- `glossary.md` or `terms.yml`;
- `handoff-rules.md` or incident-analysis routing views;
- `operational-context-index.md`;
- `BUILD MEMORY`, if provided.

Every referenced ID should exist in the corresponding file, be created in the same build cycle, be represented as a durable `gap`, or be tracked as a pending build-memory reference.

Validate references from this file:

- `teams[].references.systems` -> `systems.yml`;
- `teams[].references.runtimeComponents` -> runtime components in `systems.yml` or `runtime-components.yml`;
- `teams[].references.repositories` -> `repo-map.yml`;
- `teams[].references.modules` -> modules in `repo-map.yml`;
- `teams[].references.processes` -> `processes.yml`;
- `teams[].references.processSteps` -> process steps in `processes.yml`;
- `teams[].references.boundedContexts` -> `bounded-contexts.yml`;
- `teams[].references.integrations` -> `integrations.yml`;
- `teams[].references.terms` -> `glossary.md` or `terms.yml`;
- `teams[].references.externalParties` -> `externalParties[]`;
- `externalParties[].references.teams` -> `teams[]`;
- `responsibilities[].targetId` -> corresponding target file based on `targetType`;
- `notResponsibleFor[].targetId` -> corresponding target file based on `targetType`;
- `routingHints.candidateTeams`, `routingHints.partnerTeams`, `handoffHints.*TeamIds`, `collaboration.*TeamIds` -> `teams[].id`;
- `routingHints.externalParties`, `collaboration.externalPartyIds` -> `externalParties[].id`.

If another file must be updated but the current task only updates `teams.yml`, add a precise durable `gap` or build-memory pending reference instead of editing another file.

## Shared repository and runtime rules

### Shared repository rule

If multiple teams work in one repository:

- keep the repository in `references.repositories` for all relevant teams when supported;
- express the split with `responsibilities[].targetType: repository-module`, `bounded-context`, `process`, `integration`, `term` or `data-domain`;
- do not assign the entire repository to one team unless explicitly documented;
- use `notResponsibleFor` and `recognition.falsePositiveSignals` to prevent wrong routing if the repository name is misleading.

### Shared runtime component rule

If one deployed runtime component contains multiple domains/modules:

- do not assign the entire runtime component to a domain team unless documented;
- use `responsibilities` with a scoped target such as bounded context, process, module or integration side;
- use `routingHints.when.requiredEvidence` for runtime signals that clearly indicate the team's area;
- link domain teams through contexts, processes, modules, integrations and glossary terms;
- keep platform/runtime support separate from domain accountability.

### Shared library and generated client rule

If evidence comes from a shared library or generated client:

- capture library/client signals as match signals;
- do not assume the library maintainer owns the consuming runtime behavior;
- use `shared-library-maintainer` or `generated-client-maintainer` only when evidence supports it;
- use `consumer` or `producer` roles for integration sides when known;
- keep consuming team joins in `BUILD MEMORY` until consuming evidence is confirmed.

## What not to do

Do not:

- force a single owner for shared systems, repositories, contexts or processes;
- infer ownership from a single class, package, commit author, branch, ticket assignee or personal name;
- turn `worker` or `contributor` evidence into ownership;
- add individual people as teams;
- include private contact details, secrets, credentials, tokens or customer data;
- create routing hints from partial evidence;
- turn temporary cross-repo uncertainty into final gaps;
- create a team for every GitLab group unless it has operational meaning;
- create a team for every external system unless it is a real operational actor;
- treat platform support as domain ownership;
- treat first response as final accountability;
- treat a match signal as ownership proof;
- write long organization essays;
- duplicate information better represented in `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml` or `glossary.md`.

## YAML style rules

- Return valid YAML only.
- Preserve the top-level wrapper: `schemaVersion`, `catalogKind`, `teams`, `externalParties`, `gaps`.
- Use two spaces for indentation.
- Never use TAB characters.
- Prefer block sequences over long flow sequences.
- Quote values that contain `{}`, `:`, `#`, `[]`, commas, or leading/trailing spaces.
- Quote endpoint paths with path-template placeholders, for example `"/api/customers/{customerId}"`.
- Quote values that could be parsed as booleans, dates, numbers or nulls when they are identifiers.
- Keep list values short and operational.
- Prefer structured objects over prose.
- Do not include Markdown fences in the final output.

## Quality gates

Before returning the final file, verify:

1. YAML parses successfully.
2. `schemaVersion` is `1`.
3. Top-level keys are exactly `schemaVersion`, `catalogKind`, `teams`, `externalParties`, `gaps`.
4. `catalogKind` is `operational-context-team-map`.
5. Team IDs are stable, kebab-case and unique.
6. External party IDs are stable, kebab-case and unique.
7. Gap IDs are stable, kebab-case and unique.
8. No duplicate teams exist under aliases.
9. Every team has the full required field structure.
10. Every external party has the full required field structure.
11. Lists are lists, not comma-separated strings.
12. Unknown scalar values are `null`, not empty strings.
13. No individual personal data, private contact details, credentials, secrets, tokens or customer data are present.
14. No single-owner assumption is invented.
15. Every responsibility has `targetType`, `targetId`, `role`, `scope`, `side`, `status`, `confidence` and `evidence`.
16. Every confirmed responsibility has at least one evidence object.
17. `worker` and `contributor` responsibilities are not promoted to ownership.
18. `platform-support`, `first-responder`, `support-contact`, `domain-steward`, `repo-maintainer` and `runtime-operator` roles remain separate.
19. Shared repositories and shared runtime components preserve multi-team responsibility.
20. Match signals are concrete and useful for deterministic mapping.
21. Match signals are not used as proof of ownership.
22. Weak signals are not used as sole basis for routing or responsibility.
23. `recognition.falsePositiveSignals` and `notResponsibleFor` are used where misleading signals exist.
24. `routingHints` are evidence-backed and do not replace the responsibility model.
25. Final `gaps` are durable catalog-level issues, not temporary scan-order uncertainty.
26. Existing useful facts are not removed just because they are absent from the current source.
27. Cross-file references are valid, created in the same build cycle, tracked in build memory or represented by a durable gap.
28. The example schema is not copied with fake placeholders into real data.

## Correctly filled generic example

The example below is generic CRM-style data. It demonstrates the expected structure, field order, multi-team responsibility, deterministic mapping, external-party handling, false-positive prevention and typed gaps. Do not copy example IDs into real catalogs unless they match real evidence.

```yaml
schemaVersion: 1
catalogKind: operational-context-team-map
teams:
  - id: crm-customer-team
    name: CRM Customer Team
    kind: internal-product-team
    lifecycleStatus: active
    purpose: Handles CRM customer-profile domain behavior and first-line investigation for customer-profile runtime signals.
    aliases:
      - customer-domain
      - crm-customer
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - incident-analysis
      - handoff-routing
      - qa
    organizationalContext:
      department: Product Engineering
      tribe: CRM
      valueStream: Customer Management
      platformArea: null
      region: null
      notes: []
    responsibilityStatus: shared
    references:
      systems:
        - crm-core
      runtimeComponents:
        - crm-customer-service
      repositories:
        - crm-api-repo
      modules:
        - crm-api-repo:customer-profile-module
      processes:
        - customer-profile-update-process
      processSteps:
        - customer-profile-update-process:validate-profile-change
      boundedContexts:
        - customer-profile
      integrations:
        - crm-customer-profile-changed-events
      terms:
        - customer-profile
        - consent-status
      handoffRules: []
      externalParties: []
    responsibilities:
      - targetType: bounded-context
        targetId: customer-profile
        role: domain-steward
        scope: Customer-profile local language, profile lifecycle rules and customer-facing profile semantics.
        side: not-applicable
        status: confirmed
        confidence: high
        evidence:
          - sourceType: documentation
            source: "crm-api-repo:docs/ownership.md#customer-profile"
            detail: "Customer Profile context is documented as CRM Customer Team's domain area."
            supports:
              - "responsibility:crm-customer-team:customer-profile"
            confidence: high
      - targetType: repository-module
        targetId: crm-api-repo:customer-profile-module
        role: module-steward
        scope: Customer-profile source module under `src/main/java/com/example/crm/customer/profile`.
        side: not-applicable
        status: confirmed
        confidence: high
        evidence:
          - sourceType: codeowners
            source: "crm-api-repo:CODEOWNERS"
            detail: "The customer-profile package path maps to `@crm/customer-team`."
            supports:
              - "responsibility:crm-customer-team:crm-api-repo:customer-profile-module"
            confidence: high
      - targetType: integration-side
        targetId: crm-customer-profile-changed-events:producer
        role: producer
        scope: Producer side of customer-profile changed events.
        side: producer
        status: inferred
        confidence: medium
        evidence:
          - sourceType: code-symbol
            source: "crm-api-repo:CustomerProfileChangedPublisher"
            detail: "Publisher class emits customer-profile changed events from the customer-profile package."
            supports:
              - "responsibility:crm-customer-team:crm-customer-profile-changed-events:producer"
            confidence: medium
    notResponsibleFor:
      - targetType: platform-capability
        targetId: rabbitmq-platform
        reason: Customer Team produces customer events but does not operate the RabbitMQ platform.
        evidence:
          - sourceType: documentation
            source: "platform-runbook:messaging-support"
            detail: "RabbitMQ operation is documented as Messaging Platform support."
            supports:
              - "not-responsible:crm-customer-team:rabbitmq-platform"
            confidence: high
    matchSignals:
      exact:
        teamIds:
          - crm-customer-team
        teamNames:
          - CRM Customer Team
        aliases:
          - crm-customer
        ticketQueues:
          - CRM-CUSTOMER
        onCallReferences: []
        ownershipLabels:
          - owner:crm-customer
        codeOwnerGroups:
          - "@crm/customer-team"
        supportGroupLabels: []
        serviceCatalogOwnerIds: []
      strong:
        serviceNames:
          - crm-customer-service
        applicationNames:
          - crm-customer
        deploymentNames:
          - crm-customer-service
        containerNames: []
        runtimeComponentIds:
          - crm-customer-service
        repositoryIds:
          - crm-api-repo
        projectNames:
          - crm-api
        projectPaths:
          - crm/crm-api
        moduleIds:
          - crm-api-repo:customer-profile-module
        packagePrefixes:
          - com.example.crm.customer.profile
        endpointPrefixes:
          - /api/customers
        queueNames: []
        topicNames:
          - crm.customer.profile
        exchangeNames: []
        routingKeys:
          - customer.profile.changed
        eventNames:
          - CustomerProfileChanged
        schemaNames:
          - CustomerProfileChangedEvent
        datasourceNames: []
        hikariPools: []
        tableNames:
          - customer_profile
        boundedContextIds:
          - customer-profile
        processIds:
          - customer-profile-update-process
        integrationIds:
          - crm-customer-profile-changed-events
        platformCapabilityIds: []
      medium:
        classHints:
          - CustomerProfileService
          - CustomerProfileController
        interfaceHints: []
        enumHints:
          - ConsentStatus
        generatedClientHints: []
        configPrefixes:
          - crm.customer
        markerNames: []
        spanNames:
          - customer.profile.update
        metricNames: []
        logMarkers:
          - CUSTOMER_PROFILE_UPDATE
        exceptionClasses:
          - CustomerProfileValidationException
        domainTerms:
          - customer profile
          - consent status
        documentSections:
          - docs/ownership.md#customer-profile
        workflowNames: []
        jobNames: []
      weak:
        commitAuthorDomains: []
        historicalMentions: []
        informalNames:
          - customer team
        genericTerms:
          - customer
        nearbyPackages:
          - com.example.crm.shared.customer
        branchNames: []
        ticketMentions: []
    recognition:
      strongSignals:
        - "Package prefix `com.example.crm.customer.profile` with CustomerProfile classes."
        - "Event `CustomerProfileChanged` produced by crm-customer-service."
      weakSignals:
        - "Generic `customerId` fields appear in many CRM modules."
      falsePositiveSignals:
        - "Shared DTO package `com.example.crm.shared.customer` is not enough to route to CRM Customer Team."
      disambiguationRules:
        - "Route RabbitMQ broker failures to Messaging Platform unless the failing stacktrace includes customer-profile publisher code."
    collaboration:
      upstreamTeamIds: []
      downstreamTeamIds:
        - crm-notification-team
      peerTeamIds:
        - crm-data-team
      platformTeamIds:
        - messaging-platform-team
      integrationPartnerTeamIds: []
      externalPartyIds: []
      sharedResponsibilityNotes:
        - CRM Data Team shares data-quality analysis for customer_profile read models.
    routingHints:
      - id: route-customer-profile-validation-failures
        intent: incident-analysis
        when:
          signals:
            exceptionClasses:
              - CustomerProfileValidationException
            packagePrefixes:
              - com.example.crm.customer.profile
          requiredEvidence:
            - Stacktrace or log marker from customer-profile package.
          exclusions:
            - Broker-level RabbitMQ outage without customer-profile application stacktrace.
        routeTo: crm-customer-team
        candidateTeams:
          - crm-customer-team
        partnerTeams:
          - crm-data-team
        externalParties: []
        partnerResponsibilities:
          - targetType: data-domain
            targetId: customer-profile-data
            role: data-owner
            scope: Data-quality validation of customer profile attributes.
            side: not-applicable
            status: candidate
            confidence: medium
            evidence: []
        firstAction: Check customer-profile validation logs, recent profile update changes and related data-quality signals.
        confidence: medium
        reason: Customer-profile validation errors usually map to customer-profile domain logic, with CRM Data Team as partner when data quality is involved.
    handoffHints:
      defaultRouteLabel: CRM-CUSTOMER
      firstResponderTeamIds:
        - crm-customer-team
      escalationTeamIds: []
      partnerTeamIds:
        - crm-data-team
        - messaging-platform-team
      requiredEvidence:
        - correlationId
        - runtime component or service name
        - stacktrace class or log marker
      preferredEvidence:
        - affected endpoint or event name
        - customer-profile table/entity hint
      expectedFirstActions:
        - Check customer-profile package stacktrace and recent module changes.
        - Verify whether the symptom is application validation or platform messaging.
      whenToRouteHere:
        - Customer-profile domain classes, endpoint prefixes or events are present.
      whenToInvolveAsPartner:
        - Customer-profile events are consumed downstream by notification or analytics flows.
      whenNotToRouteHere:
        - Only shared customer DTO serialization is visible without customer-profile runtime evidence.
      fallbackIfAmbiguous: Involve CRM Customer Team as domain partner and use repo-map/code-search to confirm module ownership.
      notes: []
    communication:
      contactHints:
        channels:
          - "#crm-customer-support"
        ticketQueues:
          - CRM-CUSTOMER
        onCallReferences:
          - crm-customer-oncall
        documentation:
          - docs/ownership.md#customer-profile
      escalationNotes: []
    observability:
      dashboards:
        - crm-customer-service-dashboard
      logIndexes:
        - crm-customer-logs
      alertLabels:
        - owner:crm-customer
      sliOrSloNames: []
      runbookRefs:
        - customer-profile-runbook
      supportQueueLabels:
        - CRM-CUSTOMER
    analysisHints:
      deterministicMapping:
        - Prefer exact CODEOWNERS group or ticket queue when available.
        - Combine customer-profile package prefix with runtime component for high-confidence team relevance.
      codeSearch:
        - Search crm-api-repo customer-profile module before broad repository search.
      functionDescription:
        - Explain this area as customer-profile business/domain behavior, not generic CRM code.
      impactAnalysis:
        - Changes to CustomerProfileChanged events may affect notification and analytics consumers.
      dbCodeGrounding:
        - Customer-profile symptoms may map to `customer_profile` table and `CustomerProfileRepository` code.
      incidentAnalysis:
        - Distinguish application validation from broker/platform delivery failures.
      qa:
        - Include customer-profile validation and event publication acceptance checks.
    llmToolHints:
      preferredWhen:
        - User asks who understands customer-profile behavior.
        - Evidence contains customer-profile package, event or endpoint signals.
      avoidWhen:
        - Evidence only contains shared customer DTOs or broker infrastructure errors.
      explanationHints:
        - Separate domain stewardship from RabbitMQ platform support.
      usefulForQuestions:
        - Who should explain customer-profile validation failures?
        - Which team should review CustomerProfileChanged impact?
      answerStyleHints:
        - State confidence and cite evidence type when explaining responsibility.
    evidence:
      - sourceType: codeowners
        source: "crm-api-repo:CODEOWNERS"
        detail: "Customer-profile package path maps to `@crm/customer-team`."
        supports:
          - "responsibility:crm-customer-team:crm-api-repo:customer-profile-module"
        confidence: high
      - sourceType: documentation
        source: "crm-api-repo:docs/ownership.md#customer-profile"
        detail: "Customer Profile is documented as CRM Customer Team domain area."
        supports:
          - "responsibility:crm-customer-team:customer-profile"
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - crm-api-repo:CODEOWNERS
        - crm-api-repo:docs/ownership.md
      expectedSources:
        - service-catalog:crm-customer-service
        - support-matrix:crm
      missingSources:
        - support-matrix:crm
      limitations:
        - First-responder route is supported by docs but support matrix was not scanned.
    limitations:
      - Runtime on-call schedule was referenced but not validated from support matrix.
externalParties:
  - id: external-email-provider
    name: External Email Provider
    kind: vendor
    lifecycleStatus: active
    purpose: Operates external email delivery API used by CRM notification flows.
    aliases:
      - email-vendor
    useFor:
      - deterministic-mapping
      - impact-analysis
      - incident-analysis
      - handoff-routing
    responsibilityStatus: explicit
    references:
      systems:
        - external-email-platform
      integrations:
        - crm-notification-to-email-provider-send-message
      boundedContexts: []
      processes:
        - customer-notification-process
      terms:
        - email-bounce
      teams:
        - crm-notification-team
    responsibilities:
      - targetType: integration-side
        targetId: crm-notification-to-email-provider-send-message:target
        role: integration-contract-steward
        scope: Target-side email provider API availability and response semantics.
        side: target
        status: confirmed
        confidence: high
        evidence:
          - sourceType: documentation
            source: "docs/integrations/email-provider.md#support"
            detail: "Vendor support owns target-side API availability and bounce response semantics."
            supports:
              - "responsibility:external-email-provider:crm-notification-to-email-provider-send-message:target"
            confidence: high
    notResponsibleFor: []
    matchSignals:
      exact:
        teamIds: []
        teamNames:
          - External Email Provider
        aliases:
          - email-vendor
        ticketQueues: []
        onCallReferences: []
        ownershipLabels: []
        codeOwnerGroups: []
        supportGroupLabels: []
        serviceCatalogOwnerIds: []
      strong:
        serviceNames: []
        applicationNames: []
        deploymentNames: []
        containerNames: []
        runtimeComponentIds: []
        repositoryIds: []
        projectNames: []
        projectPaths: []
        moduleIds: []
        packagePrefixes: []
        endpointPrefixes: []
        queueNames: []
        topicNames: []
        exchangeNames: []
        routingKeys: []
        eventNames:
          - EmailBounceReceived
        schemaNames: []
        datasourceNames: []
        hikariPools: []
        tableNames: []
        boundedContextIds: []
        processIds:
          - customer-notification-process
        integrationIds:
          - crm-notification-to-email-provider-send-message
        platformCapabilityIds: []
      medium: {}
      weak: {}
    recognition:
      strongSignals:
        - "Integration ID `crm-notification-to-email-provider-send-message`."
      weakSignals: []
      falsePositiveSignals: []
      disambiguationRules:
        - "CRM Notification Team owns source-side send behavior; external provider owns target-side API behavior."
    communication:
      contactHints:
        channels: []
        ticketQueues:
          - EMAIL-VENDOR-SUPPORT
        onCallReferences: []
        documentation:
          - docs/integrations/email-provider.md#support
      escalationNotes:
        - Use vendor support only after source-side CRM Notification behavior is checked.
    handoffHints:
      defaultRouteLabel: EMAIL-VENDOR-SUPPORT
      requiredEvidence:
        - provider request ID or non-sensitive provider error code
      expectedFirstActions:
        - Confirm CRM source-side request and provider response classification.
      whenToInvolve:
        - Provider API returns documented provider-side failure or outage signal.
      whenNotToInvolve:
        - CRM source-side validation prevents the request from being sent.
      fallbackIfAmbiguous: Ask CRM Notification Team to validate source-side evidence first.
      notes: []
    evidence:
      - sourceType: documentation
        source: "docs/integrations/email-provider.md#support"
        detail: "Vendor support route is documented for provider-side API failures."
        supports:
          - "external-party:external-email-provider"
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - docs/integrations/email-provider.md
      expectedSources:
        - vendor-support-contract
      missingSources:
        - vendor-support-contract
      limitations:
        - Vendor contract details were not scanned.
    limitations: []
gaps:
  - id: gap-crm-customer-support-matrix-not-scanned
    type: source-coverage-gap
    severity: medium
    status: open
    affectedNodes:
      - team:crm-customer-team
      - runtime-component:crm-customer-service
    description: CRM Customer Team first-responder mapping is documented in repository docs, but the central support matrix was not scanned.
    impact:
      - Incident handoff may be less certain when customer-profile evidence is ambiguous.
    suggestedEvidenceSources:
      - support-matrix:crm
      - service-catalog:crm-customer-service
    evidence:
      - sourceType: documentation
        source: "crm-api-repo:docs/ownership.md#customer-profile"
        detail: "Repository docs name the team, but do not prove central on-call coverage."
        supports:
          - "gap:gap-crm-customer-support-matrix-not-scanned"
        confidence: medium
```

## Input

The update task should provide the current file, new facts, source scope and related context in sections such as:

```text
CURRENT FILE:
...

NEW FACTS:
...

SCAN SCOPE:
...

FULL OPERATIONAL CONTEXT:
...

BUILD MEMORY:
...
```

## Final answer

Return only the full updated `teams.yml` YAML content.
