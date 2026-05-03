# bounded-contexts.yml update prompt

Update only `bounded-contexts.yml`.

This prompt is schema-authoritative for `bounded-contexts.yml`. If a parent operational-context prompt is also provided, follow the parent prompt for workflow, source-scope handling, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy and merge policy for bounded contexts.

## Purpose

Maintain `bounded-contexts.yml` as an enterprise-grade, evidence-backed, queryable semantic and operational graph of bounded contexts.

A bounded context is not only an incident-routing hint. It is a semantic boundary and indexing node used for:

- deterministic mapping from runtime, code, data and documentation evidence to business or technical domain areas;
- GitLab/code search scope construction across main repositories, shared libraries, generated clients and integration modules;
- explaining what a function, class, endpoint, event, database table or domain term means;
- impact analysis across systems, runtime components, repositories, modules, processes, integrations, data models and teams;
- DB/code grounding before database diagnostics;
- incident triage as one downstream view, without reducing responsibility to a single owner;
- domain vocabulary disambiguation;
- follow-up analysis, documentation, onboarding, user-facing Q&A and future AI analysis features.

A bounded context entry should explain:

- where a local language starts and ends;
- what terms and entities mean inside the context;
- which deterministic signals reveal the context;
- which systems, runtime components, repositories, modules, processes and integrations implement or touch it;
- how it relates to neighboring contexts;
- which teams have documented responsibilities over which scope;
- what is still unknown after durable validation.

## Non-goals

Do not turn this file into full DDD documentation, long architecture prose, a process catalog, a repository catalog, an integration catalog, a glossary, a team ownership matrix or an incident-escalation playbook.

Store only facts that help at least one of these actions:

1. recognize a bounded context from logs, traces, spans, metrics, endpoints, events, queues, packages, classes, configuration, database markers or terms;
2. map runtime/code/data evidence to a semantic domain area;
3. explain local language to an LLM or user;
4. understand semantic boundaries and neighboring contexts;
5. construct focused code-search and DB-grounding scope;
6. guide incident or analysis coordination without inventing single ownership;
7. support deterministic cross-file links to systems, repositories, runtime components, processes, integrations, terms and teams.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `bounded-contexts.yml`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, documentation fragments, runtime evidence, database discovery, operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, library, documentation fragment, branch, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `systems.yml`, runtime components, `repo-map.yml`, `processes.yml`, `integrations.yml`, `teams.yml` and glossary/terms.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins and partial facts. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

## Output

Return the full updated `bounded-contexts.yml` content only.

Do not include Markdown fences.
Do not include explanations.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `bounded-contexts.yml`.
Do not output `BUILD MEMORY` inside `bounded-contexts.yml`.

## Required top-level schema

Use this exact top-level shape. Do not preserve legacy top-level fields.

```yaml
schemaVersion: 1
catalogKind: operational-context-bounded-contexts
boundedContexts: []
gaps: []
```

Top-level order must be:

1. `schemaVersion`
2. `catalogKind`
3. `boundedContexts`
4. `gaps`

Use `gaps`, not legacy untyped `openQuestions`. Durable unresolved questions are represented as typed gaps.

## Required bounded context schema

Each bounded context entry must use this structure. Keep required empty lists as `[]` when there is no confirmed value. Empty optional fields inside `matchSignals` buckets may be omitted.

```yaml
- id: stable-kebab-case-id
  name: Human Readable Name
  type: core-domain | supporting-domain | generic-subdomain | integration-context | platform-context | external-context | reporting-context | shared-kernel | unknown
  lifecycleStatus: active | planned | deprecated | retired | unknown
  aliases: []
  summary: Short operational description focused on local meaning and analytical value.
  localLanguageSummary: One-sentence explanation of what is special or ambiguous about language in this context.
  useFor: []
  scope:
    includes: []
    excludes: []
    businessCapabilities: []
    coreEntities: []
    keyDecisions: []
  semanticBoundary:
    ownsLanguage: []
    doesNotOwn: []
    invariants: []
    canonicalEntities: []
    localConcepts: []
  references:
    systems: []
    runtimeComponents: []
    repositories: []
    modules: []
    processes: []
    integrations: []
    terms: []
  responsibilityStatus: explicit-single | explicit-multiple | shared | unresolved | disputed | external | platform-shared | not-applicable
  responsibilities: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  relations: []
  operationalSignals:
    commonSymptoms: []
    expectedBusinessEvents: []
    observabilityHints: []
  analysisHints:
    codeSearch: []
    impactAnalysis: []
    functionDescription: []
    incidentTriage: []
    dbGrounding: []
    qa: []
  llmToolHints:
    answerWhenUserMentions: []
    disambiguateFrom: []
    usefulSearchKeywords: []
    explanationStyle: null
  evidence: []
  sourceCoverage:
    status: complete | partial | unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

## Field semantics

### `id`

Use stable kebab-case. Reuse an existing id when the context already exists. Do not create duplicate ids for synonyms.

Good ids:

- `customer-profile`
- `lead-management`
- `campaign-management`
- `notification-delivery`

Bad ids:

- `CustomerProfile`
- `crm_customer_profile`
- `context1`
- `service-api-context`

Do not rename existing ids unless the current id is clearly invalid, the replacement is obvious and no cross-file reference will be broken.

### `type`

Classify the context by role:

- `core-domain`: central business capability with its own language and rules.
- `supporting-domain`: business-supporting capability with domain language but lower strategic centrality.
- `generic-subdomain`: commodity capability with reusable semantics.
- `integration-context`: context that mainly translates or mediates contracts between systems.
- `platform-context`: technical or platform capability with operational semantics, such as identity, messaging, storage, runtime tools or database access.
- `external-context`: external system, partner, vendor or third-party semantic model represented in the catalog.
- `reporting-context`: analytics, reporting, read-model or observability context.
- `shared-kernel`: shared language or model intentionally reused across multiple contexts.
- `unknown`: use only when a boundary is observed but not yet classifiable.

Do not use `platform-context` for generic utilities unless they affect interpretation, code search, impact analysis or operational diagnostics.

### `lifecycleStatus`

Use:

- `active`: currently implemented or operationally relevant.
- `planned`: documented future context.
- `deprecated`: still present but intentionally being phased out.
- `retired`: no longer active but retained for historical mapping.
- `unknown`: evidence exists but lifecycle state is unclear.

### `summary`

Write one short sentence describing what this context means operationally. Do not hide concrete match keys in the summary.

### `localLanguageSummary`

Write one short sentence explaining what is special, local or easily confused about language in this context.

Example:

```yaml
localLanguageSummary: In this context, account means a CRM customer account, not an authentication account.
```

### `useFor`

Use a non-empty list when there is enough evidence. Allowed values:

- `deterministic-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `integration-impact-analysis`
- `db-grounding`
- `incident-analysis`
- `domain-vocabulary-explanation`
- `repository-onboarding`
- `qa`

Use only values that the context can actually support with evidence.

### `scope`

Use short operational phrases.

- `includes`: what belongs to this semantic boundary.
- `excludes`: nearby concepts that do not belong here.
- `businessCapabilities`: capabilities implemented or semantically owned by this context.
- `coreEntities`: compact names of important domain entities, aggregates or records.
- `keyDecisions`: business or technical decisions made inside this context.

Do not list every class, endpoint or table here. Put concrete machine-matchable signals in `matchSignals`.

### `semanticBoundary`

Use this section to describe local language and domain boundaries.

- `ownsLanguage`: terms that belong to this context and should usually map analysis here.
- `doesNotOwn`: nearby terms, concepts or capabilities that should not be routed here.
- `invariants`: business or technical rules that define the context boundary.
- `canonicalEntities`: detailed canonical entities or aggregates central to the context.
- `localConcepts`: concepts whose meaning is local and useful for LLM answers.

Recommended object format for `canonicalEntities`:

```yaml
canonicalEntities:
  - name: CustomerProfile
    meaning: Canonical customer identity and contact aggregate inside CRM.
    codeSignals:
      - CustomerProfile
      - CustomerProfileEntity
      - CustomerProfileAggregate
    dataSignals:
      - customer_profile
```

Recommended object format for `localConcepts`:

```yaml
localConcepts:
  - term: consent status
    aliases:
      - marketing consent
      - communication permission
    meaning: Whether the customer can be contacted through a specific channel for a specific purpose.
    evidenceSignals:
      - ConsentStatus
      - consent_status
      - MarketingConsentChanged
```

### `references`

Use only stable catalog ids when known.

```yaml
references:
  systems:
    - crm-api
  runtimeComponents:
    - crm-api-runtime
  repositories:
    - crm-api-repo
    - crm-shared-domain-repo
  modules:
    - crm-api-repo/customer-profile-module
  processes:
    - customer-onboarding
  integrations:
    - crm-api-to-email-platform
  terms:
    - customer-profile
    - consent-status
```

Rules:

- Prefer ids from `FULL OPERATIONAL CONTEXT`, `CURRENT FILE` or existing catalog summaries.
- Do not invent cross-file ids if no naming convention is available.
- Do not introduce dangling references as confirmed facts.
- If a referenced node is likely missing only because another repository or file has not been scanned, keep it in `BUILD MEMORY` if available.
- Add a durable `missing-reference` gap only when the missing node is not a temporary scan-order issue and affects mapping, explanation, impact analysis or coordination.

### `responsibilityStatus` and `responsibilities`

Responsibility is not the same as single ownership. Do not collapse multi-team responsibility into one owner.

Use `responsibilityStatus`:

- `explicit-single`: exactly one accountable semantic owner is documented.
- `explicit-multiple`: multiple documented responsibilities or accountable scopes exist.
- `shared`: responsibility is intentionally shared and cannot be reduced to one team.
- `unresolved`: responsibility is not documented and matters for analysis or coordination.
- `disputed`: evidence conflicts about responsibility.
- `external`: responsibility belongs to an external party or vendor.
- `platform-shared`: platform capability supported by multiple platform/runtime teams.
- `not-applicable`: responsibility does not apply to this context.

Use responsibility objects:

```yaml
responsibilities:
  - teamId: crm-customer-team
    externalOwner: null
    role: domain-steward
    scope: Customer profile language, profile persistence and profile APIs.
    evidence: explicit-doc
    confidence: high
```

Allowed `role` values:

- `accountable`
- `domain-steward`
- `runtime-operator`
- `repo-maintainer`
- `module-steward`
- `integration-contract-steward`
- `producer`
- `consumer`
- `participant`
- `first-responder`
- `platform-support`
- `business-owner`
- `worker`
- `external-owner`
- `unknown`

Allowed `evidence` values:

- `explicit-doc`
- `explicit-code`
- `explicit-config`
- `inferred-code`
- `inferred-runtime`
- `current-catalog`
- `human-input`
- `human-input-required`

Rules:

- Do not infer accountability from package names, repository names, author names, comments, directory names, CODEOWNERS or the fact that a team participates in a workflow.
- If only participation is documented, use `role: participant`, `role: worker`, `role: producer` or `role: consumer`, not `accountable` or `domain-steward`.
- If responsibility is unknown, add a responsibility object with `teamId: null`, `externalOwner: null`, `role: unknown`, `evidence: human-input-required`, `confidence: low` only when this uncertainty matters for analysis.
- Add a durable `responsibility-ambiguity` gap only when human/domain input is required or available evidence was sufficient to know that responsibility is unresolved.

### `matchSignals`

`matchSignals` contains deterministic mapping keys grouped by strength. Prefer concrete machine-matchable signals over prose.

Recommended fields:

```yaml
matchSignals:
  exact:
    contextIds: []
    explicitNames: []
  strong:
    deploymentComponents: []
    serviceNames: []
    applicationNames: []
    containerNames: []
    artifactNames: []
    projectNames: []
    repositoryNames: []
    endpointPrefixes: []
    queues: []
    exchanges: []
    topics: []
    routingKeys: []
    eventNames: []
    eventSchemas: []
    databasePools: []
    databaseSchemas: []
    databaseTables: []
    packagePrefixes: []
    classNames: []
    entityNames: []
    configKeys: []
    logMarkers: []
    exceptionClasses: []
  medium:
    methodNames: []
    fieldNames: []
    dtoNames: []
    metricNames: []
    spanNames: []
    filePathPatterns: []
  weak:
    terms: []
    aliases: []
```

Rules:

- Omit empty fields inside `exact`, `strong`, `medium` and `weak` when there is no confirmed value.
- Keep the four buckets even when they are empty.
- Put stable, unambiguous identifiers in `exact`.
- Put package prefixes, endpoints, events, tables, deployment/runtime names and class/entity names in `strong`.
- Put methods, fields, DTOs, metrics, spans and path patterns in `medium`.
- Put human terms and aliases in `weak` unless they are explicit catalog ids or names.
- Do not put prose into signal lists.
- Do not hide match keys inside `summary`, `scope` or `analysisHints`.

### `relations`

Relations describe semantic and operational dependencies between bounded contexts.

Use this object format:

```yaml
relations:
  - targetContextId: other-context-id
    relationType: upstream-downstream | customer-supplier | conformist | published-language | open-host-service | anti-corruption-layer | shared-kernel | partnership | separate-ways | supporting | peer | event-consumer | event-driven | data-dependency | runtime-dependency | orchestration | translation | semantic-overlap | unknown
    direction: inbound | outbound | bidirectional | unknown
    via:
      integrationIds: []
      processIds: []
      systemIds: []
      eventNames: []
      endpointPrefixes: []
      queueOrTopicNames: []
      codeSignals: []
      dataSignals: []
    coordination:
      analysisHint: Short hint for impact analysis.
      handoffHint: Short hint for coordination when evidence crosses this boundary.
    evidence: []
    confidence: high | medium | low
```

Rules:

- `targetContextId` must point to another bounded context id in this file or a known existing context.
- Prefer canonical ids in `integrationIds`, `processIds` and `systemIds`.
- Put non-id technical signals in `eventNames`, `endpointPrefixes`, `queueOrTopicNames`, `codeSignals` or `dataSignals`.
- Do not create a relation just because two contexts are in the same repository.
- Create a relation when evidence shows translation, dependency, event flow, API flow, shared model, process handoff, data dependency, runtime dependency or semantic coupling.
- If the relation is suspected but not grounded, keep it in `BUILD MEMORY` if available instead of finalizing it.

### `operationalSignals`

Use this for analysis-friendly symptoms and expected signals.

- `commonSymptoms`: symptoms that usually indicate this context is involved.
- `expectedBusinessEvents`: business events or state changes expected when the context behaves correctly.
- `observabilityHints`: useful metrics, spans, dashboards or log patterns, without secrets.

### `analysisHints`

Use short, reusable hints for downstream analysis. Do not include full incident handoff rules.

```yaml
analysisHints:
  codeSearch:
    - Search customer-profile packages before shared CRM utilities when customer lifecycle terms appear.
  impactAnalysis:
    - Customer segment changes may affect sales pipeline scoring and campaign targeting.
  functionDescription:
    - Describe failures here as customer profile or segmentation issues, not as generic account errors.
  incidentTriage:
    - For profile write failures, collect customerId, endpoint, exception class and repository method.
  dbGrounding:
    - Profile persistence symptoms should ground CustomerProfileEntity and customer_profile before database metadata discovery.
  qa:
    - Explain consent status as channel-specific permission, not a global customer flag.
```

Rules:

- `codeSearch`: how to scope code lookup across repositories, packages, classes and shared modules.
- `impactAnalysis`: neighboring contexts, processes, integrations or data models likely affected by changes.
- `functionDescription`: how to describe affected functions in non-code language.
- `incidentTriage`: compact incident-analysis hints only; detailed escalation belongs elsewhere.
- `dbGrounding`: entities, repositories, tables or relationships to ground before DB tools.
- `qa`: hints for user-facing explanations and follow-up questions.

### `llmToolHints`

Use this section to make the catalog useful when returned as an LLM tool result or retrieval result.

- `answerWhenUserMentions`: phrases, labels or local vocabulary that should retrieve this context.
- `disambiguateFrom`: nearby concepts that are easily confused.
- `usefulSearchKeywords`: focused keywords for code, documentation and runtime lookup.
- `explanationStyle`: short guidance for explaining this context to a user.

Recommended object format for `disambiguateFrom`:

```yaml
disambiguateFrom:
  - term: lead
    distinction: A lead is pre-conversion and belongs to lead-management; customer-profile owns the canonical customer record after conversion or direct creation.
```

### `evidence`

Store compact evidence references that justify the context and its strongest signals.

Recommended object format:

```yaml
evidence:
  - sourceType: code
    sourceId: crm-core-repo
    path: src/main/java/com/example/crm/customer/CustomerProfileController.java
    signal: endpointPrefix=/api/customer-profiles
    evidenceType: controller-scan
    confidence: high
```

Allowed `sourceType` values:

- `code`
- `config`
- `documentation`
- `runtime`
- `database`
- `current-catalog`
- `build-memory`
- `human`

Rules:

- Include enough evidence to audit why the context exists.
- Prefer file paths, package names, class names, config keys, endpoint names, table names, event names and documentation section names.
- Do not include long code snippets.
- Do not include credentials, tokens, secrets, personal customer data, sample business records or private values.
- Use `confidence: high` only when direct code/config/docs/runtime/database evidence supports the fact.
- Use `confidence: medium` for strong inference from naming, code structure or partial docs.
- Use `confidence: low` for weak inference or human confirmation needed. Do not add low-confidence guesses as confirmed relations or contexts unless the context already exists and must be marked incomplete.

### `sourceCoverage`

Describe whether the context is fully grounded or still partial.

```yaml
sourceCoverage:
  status: partial
  scannedSources:
    - repo:crm-api-repo
    - docs:docs/domain/customer-profile.md
  expectedSources:
    - repo:crm-api-repo
    - repo:crm-shared-domain-repo
    - docs:crm-domain-handbook
  limitations:
    - Shared customer value objects are referenced but the shared domain repository has not been scanned in this build cycle.
```

Rules:

- `complete`: expected sources for this context were scanned or evidence is clearly sufficient.
- `partial`: evidence is useful but known expected sources remain unscanned or some relationships are only partially grounded.
- `unknown`: source coverage cannot be assessed.
- Do not convert `partial` into a final gap unless the missing source creates a durable catalog problem.

## Top-level `gaps[]`

Use final durable gaps only. A gap is a persistent catalog issue, not agent scratchpad.

```yaml
gaps:
  - id: stable-gap-id
    type: responsibility-ambiguity | semantic-boundary-ambiguity | missing-reference | conflicting-language | unresolved-relation | insufficient-evidence | human-input-required
    status: open | resolved | superseded
    severity: high | medium | low
    affectedNodes: []
    description: Clear unresolved issue.
    impact: []
    suggestedEvidenceSources: []
```

Add a gap only when all are true:

- the missing or conflicting fact affects deterministic mapping, semantic interpretation, code search, DB grounding, impact analysis or coordination;
- the uncertainty is catalog-level, not merely caused by the current source being partial;
- the issue cannot be resolved from `CURRENT FILE`, `NEW FACTS`, `FULL OPERATIONAL CONTEXT` or available `BUILD MEMORY`;
- the issue names the affected context, relation or reference.

Do not add gaps for:

- values absent only from the current repository;
- counterpart systems or integrations likely implemented in another repository;
- ownership missing from a library repository when ownership is expected in a team matrix;
- temporary scan-order questions;
- low-value DDD theory questions that do not affect mapping, explanation, impact analysis, DB grounding or coordination.

If there are no durable gaps, use:

```yaml
gaps: []
```

## Partial-source and multi-repository safety

The current input may come from one repository, one library, one generated-client project, one module, one deployment/config repository or one documentation fragment.

Treat the current source as partial unless the input explicitly says it is authoritative for the affected facts.

Rules:

- Merge; do not regenerate from the current source alone.
- Add confirmed positive facts.
- Preserve existing facts that are not visible in the current source.
- Do not infer global absence from local absence.
- Do not remove, downgrade, null-out, shorten or contradict an existing value because it was not found in the current repository.
- Do not replace a non-empty list with a shorter list from the current source.
- Do not use `gaps` as temporary memory between repository-specific agents.
- Do not store `discoveryNotes`, `deferredCandidates`, `localAbsences`, `pendingCrossRepoValidation` or temporary build-memory content in `bounded-contexts.yml`.
- If a fact is split across repositories, update only the observed side with concrete evidence.
- If a library/shared module contains language for an existing context, add repository/module/package/class signals to that context; do not create a new runtime context solely because a library exists.
- If generated clients expose another context's published language, add relation or match signals only when the context boundary is clear; otherwise use `BUILD MEMORY`.
- The result must be safe regardless of the order in which repository agents are run.

Classify missing evidence internally:

- `notScanned`: the current source scope did not include the likely location. This must not change the final file.
- `notObserved`: relevant paths were searched but no value was found. This may affect confidence only if the current source is authoritative for that fact.
- `contradicted`: explicit evidence proves the existing value is wrong. Only this can justify removing or replacing a value.

## Discovery procedure

Perform this reasoning internally before producing the final YAML.

### 1. Parse and normalize

- Parse `CURRENT FILE` as YAML if present.
- Identify existing context ids and aliases.
- Identify existing top-level `gaps`.
- Normalize ids to stable kebab-case only for new entries.
- Convert legacy structures to this schema. Do not preserve legacy fields that are not represented here.

Legacy fields to compress or replace:

- `ownerTeamId`
- `partnerTeamIds`
- `handoff`
- legacy flat `signals`
- untyped `openQuestions`
- long prose sections

### 2. Extract candidate bounded contexts

From `NEW FACTS`, look for:

- recurring local vocabulary;
- packages/modules that encode domain language;
- aggregate/entity names;
- controllers, services, handlers, listeners, events, schemas, DTOs or entities clustered around a domain concept;
- endpoint groups or API surfaces dedicated to a capability;
- queues, topics, exchanges, routing keys or event contracts with stable vocabulary;
- persistence model, database schema/table/entity groupings dedicated to a capability;
- process steps or workflow definitions that use distinct language;
- README, domain documentation, OpenAPI/AsyncAPI specs or tests describing local rules;
- external systems with their own contract language;
- shared kernels intentionally reused by multiple contexts;
- repeated runtime evidence pointing to the same semantic area.

### 3. Require strong indicators

Create or update a bounded context only when at least one strong indicator exists:

- a stable local language cluster with terms that mean something specific in that area;
- a package/module/repository area dedicated to a domain capability;
- an endpoint group or API surface dedicated to that capability;
- an event/message contract with stable domain vocabulary;
- a persistence model/table group dedicated to the capability;
- a workflow/process step group with its own language;
- explicit documentation naming the context or domain area;
- repeated runtime evidence pointing to the same semantic area.

### 4. Reject false positives

Do not create a bounded context for:

- a single class, endpoint, queue, database table or package without semantic boundary evidence;
- a generic technical layer such as `controller`, `repository`, `config`, `common`, `util`, `client` or `adapter`;
- a deployment component that is better represented as a system or runtime component;
- a repository that is better represented in `repo-map.yml`;
- a workflow that is better represented in `processes.yml`;
- a single integration contract that is better represented in `integrations.yml`;
- a shared library package used by many domains without a clear shared-kernel boundary;
- a team ownership area that has no distinct domain language;
- an integration target alone.

If evidence is promising but incomplete, record a candidate in `BUILD MEMORY` when possible. Do not finalize it as a context unless the semantic boundary is sufficiently grounded.

### 5. Classify retained facts

For every retained fact, classify it mentally as one or more:

- `recognition`: helps deterministic matching;
- `semantic`: helps explain local language;
- `navigation`: helps target systems, runtime components, repositories, modules, processes or integrations;
- `coordination`: helps decide who to involve without inventing ownership;
- `analysis`: helps impact analysis, DB grounding, function description or future LLM Q&A.

Do not retain facts that support none of these categories.

### 6. Merge monotonically

Merge by `id`.

- Append new signals to existing lists.
- Deduplicate list values while preserving stable order.
- Prefer stronger direct evidence over weaker inferred evidence.
- Keep multiple responsibility relations rather than collapsing them.
- Update `evidence` with concise references when available.
- Update `sourceCoverage` based on actual scanned and expected sources.
- Keep summaries short; move concrete facts into structured fields.
- Resolve or supersede old gaps when new evidence clearly closes them.

Do not remove or weaken an existing confirmed fact unless:

- new evidence explicitly contradicts it;
- it is duplicated by a more canonical entry;
- it is invalid YAML/schema;
- the task explicitly asks for cleanup.

### 7. Validate

Before returning final YAML, verify:

- YAML parses.
- Top-level keys are exactly `schemaVersion`, `catalogKind`, `boundedContexts`, `gaps`.
- Every bounded context has all required sections.
- Every id is kebab-case.
- No list contains duplicate values.
- No string requiring quotes is unsafe in YAML flow style.
- `responsibilityStatus` matches `responsibilities`.
- Every referenced system id is expected to exist in `systems.yml` or known context summary.
- Every referenced runtime component id is expected to exist in the runtime component catalog.
- Every referenced repository id is expected to exist in `repo-map.yml` or repository catalog.
- Every referenced process id is expected to exist in `processes.yml`.
- Every referenced integration id is expected to exist in `integrations.yml`.
- Every referenced team id is expected to exist in `teams.yml` or is intentionally unresolved.
- Every referenced term id is expected to exist in glossary/terms or is intentionally introduced as a stable term id.
- Every relation target points to another bounded context in this file or a known existing context.
- `gaps` are durable catalog-level gaps only.
- No secrets, credentials, tokens, personal customer data, sample business records or private values are present.

## YAML formatting rules

- Use only spaces, never TAB characters.
- Use 2 spaces per indentation level.
- Use `null` for unknown scalar values only when the field is required by the schema.
- Prefer block lists for endpoints, paths, strings with spaces and values containing `{}`, `:`, `#`, `[ ]`, commas or URL templates.
- Quote endpoint paths, config keys and values with special characters.
- When in doubt, quote the value.
- Keep ids in kebab-case.
- Keep descriptions concise.
- Omit empty optional fields inside `matchSignals` buckets.
- Do not use comments in the output YAML.

## Compact example

This example is intentionally generic CRM-oriented and not domain-specific to any regulated industry.

```yaml
schemaVersion: 1
catalogKind: operational-context-bounded-contexts

boundedContexts:
  - id: customer-profile
    name: Customer Profile
    type: core-domain
    lifecycleStatus: active
    aliases:
      - customer identity
      - customer record
    summary: Canonical CRM context for customer identity, contact points, preferences and consent state.
    localLanguageSummary: In this context, account means a CRM customer account, not an authentication account.
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - db-grounding
      - incident-analysis
      - domain-vocabulary-explanation
      - qa
    scope:
      includes:
        - Customer identity inside the CRM product.
        - Contact details, consent flags and communication preferences.
      excludes:
        - User login credentials and access control.
        - Email delivery transport.
      businessCapabilities:
        - maintain customer identity
        - manage communication preferences
      coreEntities:
        - CustomerProfile
        - ContactPoint
        - ConsentStatus
      keyDecisions:
        - Determines whether a customer may be contacted through a specific channel.
    semanticBoundary:
      ownsLanguage:
        - customer profile
        - contact point
        - consent status
      doesNotOwn:
        - authentication account
        - notification delivery result
      invariants:
        - A customer profile must have exactly one canonical customer identifier.
        - Marketing consent is evaluated per channel, not globally for all communication.
      canonicalEntities:
        - name: CustomerProfile
          meaning: Canonical CRM customer aggregate used after direct creation or lead conversion.
          codeSignals:
            - CustomerProfile
            - CustomerProfileEntity
          dataSignals:
            - customer_profile
      localConcepts:
        - term: consent status
          aliases:
            - marketing consent
          meaning: Whether the customer can be contacted through a specific channel for a specific purpose.
          evidenceSignals:
            - ConsentStatus
            - CustomerConsentChanged
            - consent_status
    references:
      systems:
        - crm-core
      runtimeComponents:
        - crm-core-api
      repositories:
        - crm-core-repo
        - crm-domain-lib-repo
      modules:
        - crm-core-repo/customer-profile-module
      processes:
        - customer-onboarding
      integrations:
        - crm-core-customer-events
      terms:
        - customer-profile
        - consent-status
    responsibilityStatus: explicit-multiple
    responsibilities:
      - teamId: crm-customer-team
        externalOwner: null
        role: domain-steward
        scope: Customer profile language, profile persistence and profile APIs.
        evidence: explicit-doc
        confidence: high
      - teamId: crm-platform-team
        externalOwner: null
        role: platform-support
        scope: Shared CRM runtime and messaging support.
        evidence: explicit-config
        confidence: medium
    matchSignals:
      exact:
        explicitNames:
          - Customer Profile
      strong:
        deploymentComponents:
          - crm-core-api
        serviceNames:
          - crm-core
        endpointPrefixes:
          - "/api/customer-profiles"
          - "/api/customers"
        exchanges:
          - crm.customer.events
        routingKeys:
          - customer.profile.created
          - customer.profile.updated
        eventNames:
          - CustomerProfileCreated
          - CustomerProfileUpdated
          - CustomerConsentChanged
        databasePools:
          - crm-core-hikari
        databaseSchemas:
          - CRM
        databaseTables:
          - customer_profile
          - customer_contact_point
          - customer_consent
        packagePrefixes:
          - com.example.crm.customer
        classNames:
          - CustomerProfileController
          - CustomerProfileService
          - CustomerProfileEntity
        configKeys:
          - crm.customer-profile
        logMarkers:
          - CUSTOMER_PROFILE
        exceptionClasses:
          - CustomerProfileValidationException
      medium:
        methodNames:
          - updateCustomerProfile
        dtoNames:
          - CustomerProfileDto
        filePathPatterns:
          - src/main/java/com/example/crm/customer/**
      weak:
        terms:
          - customer
          - profile
        aliases:
          - account
    relations:
      - targetContextId: notification-delivery
        relationType: customer-supplier
        direction: outbound
        via:
          integrationIds:
            - crm-core-to-email-service
          processIds: []
          systemIds:
            - email-service
          eventNames:
            - CustomerConsentChanged
          endpointPrefixes: []
          queueOrTopicNames:
            - crm.customer.profile
          codeSignals:
            - NotificationPreferencePublisher
          dataSignals: []
        coordination:
          analysisHint: Customer Profile owns preference meaning; Notification Delivery owns message transport and delivery result.
          handoffHint: Keep preference validation in customer-profile; involve notification-delivery for send failures or transport symptoms.
        evidence:
          - eventName=CustomerConsentChanged
          - integrationId=crm-core-to-email-service
        confidence: high
    operationalSignals:
      commonSymptoms:
        - customer profile not found
        - duplicate customer identifier
        - consent update not propagated
      expectedBusinessEvents:
        - customer-profile-created
        - customer-profile-updated
        - customer-consent-changed
      observabilityHints:
        - Check CUSTOMER_PROFILE log marker for profile lifecycle errors.
    analysisHints:
      codeSearch:
        - Search customer-profile packages before shared CRM utilities when customer lifecycle terms appear.
      impactAnalysis:
        - Communication preference changes may affect notification integrations.
      functionDescription:
        - Describe failures here as customer identity, contact preference or consent issues.
      incidentTriage:
        - For profile write failures, collect customerId, endpoint, exception class and repository method.
      dbGrounding:
        - Ground CustomerProfileEntity and customer_profile before database metadata discovery.
      qa:
        - Explain consent status as channel-specific permission, not a global customer flag.
    llmToolHints:
      answerWhenUserMentions:
        - customer profile
        - customer identity
        - consent status
        - communication preference
      disambiguateFrom:
        - term: notification
          distinction: Customer-profile owns preference semantics; notification-delivery owns sending and delivery transport.
      usefulSearchKeywords:
        - CustomerProfileController
        - CustomerProfileEntity
        - CustomerConsentChanged
        - CUSTOMER_PROFILE
      explanationStyle: Explain as the canonical customer identity and contact-preference context, not as the full CRM sales process.
    evidence:
      - sourceType: code
        sourceId: crm-core-repo
        path: src/main/java/com/example/crm/customer/CustomerProfileController.java
        signal: endpointPrefix=/api/customer-profiles
        evidenceType: controller-scan
        confidence: high
      - sourceType: documentation
        sourceId: crm-domain-map
        path: docs/domain/customer-profile.md
        signal: contextName=Customer Profile
        evidenceType: domain-boundary-description
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - repo:crm-core-repo
        - docs:docs/domain/customer-profile.md
      expectedSources:
        - repo:crm-core-repo
        - repo:crm-domain-lib-repo
        - docs:crm-ownership-matrix
      limitations:
        - Shared customer value objects are referenced but crm-domain-lib-repo was not scanned in this build cycle.

gaps: []
```
