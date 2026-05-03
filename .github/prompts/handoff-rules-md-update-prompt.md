# handoff-rules.md update prompt

Update only `handoff-rules.md`.

This prompt is schema-authoritative for `handoff-rules.md`. If a parent operational-context builder prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy, merge policy, quality gate and output contract for `handoff-rules.md`.

Do not preserve legacy fields, legacy tables or legacy sections unless they are explicitly represented in this schema.

## Purpose

Maintain `handoff-rules.md` as an enterprise-grade, evidence-backed, AI-friendly coordination and routing overlay for a reusable operational context catalog.

`handoff-rules.md` is not a core graph file. It is not an ownership registry, integration catalog, process model, architecture document, repository map, system topology map, glossary, CMDB or runbook. It is a derived incident-analysis and coordination view over the core operational context.

The file answers a narrow operational question:

> Given this incident evidence, what should happen next operationally?

The file supports:

- deciding the next responsible operational action during incident triage;
- preventing premature or incorrect handoff when evidence is ambiguous;
- identifying candidate teams, partner teams, platform groups, external parties or the current handler to involve;
- defining the evidence required before handoff;
- documenting the first diagnostic action expected from the recipient;
- explaining why a handoff is recommended, delayed, broadened or rejected;
- exposing structured routing guidance to an LLM without turning the system into a central rule-based diagnosis engine;
- supporting follow-up questions about routing, responsibility, coordination and evidence thresholds;
- supporting future AI analysis features such as impact analysis, dependency analysis, operational Q&A, onboarding and change-risk review, but only as a routing overlay over the core catalog.

## Core principles

### 1. Handoff rules are overlays, not source of truth

Default operational facts come from other operational-context files:

- `systems.yml` for logical systems, runtime components, runtime recognition and code-search scope links;
- `repo-map.yml` for repositories, modules, source layout, generated clients, shared libraries and code-search scopes;
- `integrations.yml` for operational contracts between systems, mediators, external systems and data stores;
- `processes.yml` for business, operational and technical flows;
- `bounded-contexts.yml` for semantic boundaries and neighboring local languages;
- `teams.yml` for operational actors, responsibility relations, roles and source-backed responsibility evidence;
- `glossary.md` or `terms.yml` for local vocabulary and disambiguation;
- `BUILD MEMORY` for temporary cross-repository uncertainty during catalog construction.

Add a handoff rule only when it changes, refines or explains coordination beyond those defaults.

Do not use this file to invent ownership. Do not use it to document architecture. Do not use it to duplicate every system, endpoint, integration, queue, topic, module, class, table, team or term already present in the core catalog.

### 2. Handoff is not ownership

Do not collapse responsibility into one owner unless the evidence explicitly proves a single role.

A handoff rule may identify multiple role-specific actors:

- `first-responder` — the team, role or external function that should start the next action;
- `current-handler` — the team currently diagnosing the issue when no handoff is justified yet;
- `domain-expert` — the team that understands the business or domain semantics;
- `code-maintainer` — the team maintaining a repository, module, library or generated client;
- `runtime-operator` — the team or function operating the deployed component;
- `integration-partner` — the team responsible for one side of an integration contract or mediator;
- `platform-support` — runtime, database, messaging, observability, identity, network, scheduler or infrastructure support;
- `data-owner` — actor responsible for data quality, schema semantics or data access policy;
- `external-owner` — external vendor, partner system owner or external support function.

If the source only proves participation, contribution, worker status or code maintenance, do not convert it into ownership. Preserve uncertainty through role-specific wording, lower confidence or durable `Gaps`.

### 3. AI-first, not rule-engine-first

Rules in this file are operational guardrails. They must not replace AI analysis, root-cause reasoning or business diagnosis.

A rule may:

- suggest a candidate next actor;
- instruct the model or operator to retain the current scope;
- require one more concrete evidence signal before handoff;
- involve partner teams when cross-boundary evidence is present;
- route platform-level failures to platform support;
- coordinate with an external party when evidence clearly points outside the internal organization;
- prevent routing to a shared library maintainer when the failing behavior belongs to the consuming runtime component;
- explain why a weak match is not enough for handoff.

A rule must not:

- assert root cause without evidence;
- conclude business diagnosis;
- replace investigation;
- route by guessing;
- turn a weak string match into a confident owner;
- make ownership true just because the rule mentions a team;
- route only from generic words such as `backend`, `timeout`, `failure`, `integration`, `database` or `service unavailable` unless the rule explicitly says this evidence is insufficient and must remain with the current handler.

### 4. Evidence first, prose second

Every rule must be grounded in concrete, queryable evidence predicates.

Prefer structured evidence signals such as:

- service, application, container, deployment, namespace or artifact names;
- endpoint prefixes, path templates, HTTP methods and gateway routes;
- host/base URL property keys, service discovery names and external target labels;
- queue, exchange, topic, routing-key, binding, consumer-group and DLQ names;
- event, command, schema, OpenAPI, AsyncAPI, WSDL or GraphQL operation names;
- package prefixes, classes, interfaces, enums, annotations and exception classes;
- shared library names, generated client packages and dependency coordinates;
- Hikari pool names, DB schemas, tables, entities, repositories and migration paths;
- scheduler, job, lock, workflow, BPMN or state-machine markers;
- log markers, span names, metric names, tags, alert labels and error codes;
- local terms or aliases from `glossary.md`.

Descriptions should help humans and LLMs understand the coordination decision. They are not a substitute for deterministic signals.

### 5. Multi-repository and partial-source safety

The current input may come from one repository, one library, one generated client, one deployment manifest, one documentation fragment or one existing catalog slice. Treat it as partial.

Rules:

- Merge; do not regenerate from the current source alone.
- Do not delete, narrow or downgrade existing rules only because they are not visible in the current repository.
- Do not infer global absence from local absence.
- Do not create final handoff rules from repo-local evidence alone unless the source explicitly documents routing or the rule is clearly derivable from confirmed operational-context links.
- If a source reveals a symptom but not the responder, add the symptom to the appropriate core catalog file outside this prompt; do not create a handoff rule just to store uncertainty.
- Temporary uncertainty belongs in `BUILD MEMORY`, discovery artifacts or sidecar outputs such as `deferredCandidates`, `pendingCrossRepoValidation`, `localAbsences` or `conflicts`, not in the final `handoff-rules.md` file.
- Promote a durable `Gap` only when the uncertainty is catalog-level, affects routing or responsibility coordination, and remains unresolved after checking the appropriate source scope.

### 6. Monotonic merge

The update must be safe regardless of the order in which repository agents run.

Allowed:

- add a confirmed rule;
- add evidence signals to an existing rule;
- add role-specific routing detail;
- add operational-context links;
- add or refine LLM tool hints;
- raise confidence with stronger evidence;
- replace vague wording with grounded wording;
- split a rule when routing decisions materially differ;
- merge duplicate rules when they clearly represent the same coordination decision.

Forbidden without explicit contradictory evidence:

- deleting existing active rules;
- replacing a multi-role route with a single owner;
- changing a concrete team, role or external party to `unknown`;
- removing required evidence;
- shortening evidence predicates only because the current repository does not contain them;
- converting a catalog-level gap into a guessed rule;
- creating a high-confidence route from one weak or generic signal.

If the prompt is explicitly asked to rebuild from scratch, still preserve these principles and do not invent missing routing.

## Non-goals

Do not turn `handoff-rules.md` into:

- an ownership matrix;
- a team directory;
- a complete support rota;
- a complete runbook;
- a process catalog;
- an integration catalog;
- an API, queue, topic or endpoint inventory;
- a repository, package, class or code-search index;
- a system topology map;
- a database schema catalog;
- a full diagnostic decision tree;
- a scratchpad for temporary agent uncertainty;
- a replacement for AI reasoning.

Do not add content that belongs elsewhere:

- system or runtime-component description -> `systems.yml`;
- repository, module, package, class or code-search scope -> `repo-map.yml`;
- integration contract, host, queue, topic, endpoint, schema or channel detail -> `integrations.yml`, unless only referenced as a predicate;
- business, operational or technical flow -> `processes.yml`;
- semantic/domain boundary -> `bounded-contexts.yml`;
- team responsibility map -> `teams.yml`;
- local term definition -> `glossary.md` or `terms.yml`;
- temporary cross-repo uncertainty -> `BUILD MEMORY`;
- durable missing routing knowledge -> `## Gaps`, only if it affects handoff behavior.

Avoid adding a rule just because an endpoint, class, host, queue, topic, table or team label exists. Add a rule only if the evidence changes what the operator or model should do next.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `handoff-rules.md`. It may be empty, legacy or already version 1.
- `NEW FACTS`: repository scan results, documentation fragments, runtime evidence, logs, deployment/config evidence, build reports, operational context update fragments, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, branch, commit, generated client, shared library, deployment/config repository, documentation fragment, runtime environment, deployment manifest, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, especially `systems.yml`, runtime components inside `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` or `terms.yml`, and any incident-analysis view files.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate rules, unresolved references, sources not yet scanned and known local absences. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

If some inputs are missing, do the best possible update using available evidence. Do not invent missing facts.

## Output

Return the full updated `handoff-rules.md` Markdown only.

Do not include explanations.
Do not include diffs.
Do not include partial snippets.
Do not include Markdown fences around the whole output.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `handoff-rules.md`.
Do not output `BUILD MEMORY` inside `handoff-rules.md`.

The final document must:

- start with `# Handoff Rules`;
- use the version-1 structured Markdown model described below;
- contain only final curated routing rules and durable gaps;
- avoid legacy `## Open Questions` sections;
- avoid large unstructured architecture essays;
- avoid copying entire operational context entries into rules;
- be concise enough for runtime LLM consumption but explicit enough for operator trust;
- keep each rule evidence-backed and operationally actionable.

## Required target document structure

The updated `handoff-rules.md` must use this top-level structure and order:

````md
# Handoff Rules

```yaml
schemaVersion: 1
kind: operational-context.handoff-rules
```

## Catalog Metadata

...

## Purpose

...

## Default Routing Model

...

## Routing Principles

...

## Rule Priority Model

...

## Rule Index

...

## Rules

### `rule-id`

...

## Gaps

### `gap-id`

...
````

The metadata YAML code block is part of the target Markdown file. Keep it exactly near the top. Do not wrap the entire output in a code fence.

Do not use legacy `## Open Questions`. Durable unresolved issues must be represented under `## Gaps`.

Additional sections are allowed only when they make the file more readable and do not break the rule block format. Avoid large symptom tables as the primary representation. A short `Rule Index` table is allowed as a summary, but every actionable rule must be represented as a structured rule block under `## Rules`.

## Catalog Metadata section

Use a compact metadata section:

```md
## Catalog Metadata

- **Format version:** `1`
- **Catalog role:** `incident-analysis-routing-view`
- **Primary use case:** `incident-analysis`
- **Downstream uses:** `handoff-routing`, `incident-triage`, `follow-up-qa`, `coordination-explanation`, `impact-analysis-support`, `dependency-analysis-support`, `change-risk-review-support`
- **Core fact sources:** `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md`
- **Temporary build state:** `BUILD MEMORY`, when provided
```

Do not include timestamps unless the parent prompt explicitly asks for generated-at metadata.

## Purpose section

The `## Purpose` section should explain that the file defines coordination rules that refine default routing from the operational-context catalog. It should say that rules are used when runtime, repository, integration, process, platform, external, data or semantic evidence requires more than a default lookup.

Keep it short. Do not restate the entire architecture.

## Default Routing Model section

Use this model unless the current catalog has a stronger documented model:

```md
## Default Routing Model

1. Use `systems.yml` to identify the affected logical system and runtime component.
2. Use `repo-map.yml` to resolve code scope, including shared libraries and generated clients.
3. Use `integrations.yml` when evidence points to a specific contract, mediator, provider, consumer, external system, broker, data source or channel.
4. Use `processes.yml` and `bounded-contexts.yml` when evidence crosses process steps, semantic boundaries or local languages.
5. Use `teams.yml` to map responsibility roles, not a single owner.
6. Use `glossary.md` to disambiguate local terms, acronyms and error markers.
7. Use this file only when the default routing needs refinement, escalation, collaboration, evidence-threshold guidance, anti-misrouting guidance, external coordination or no-handoff guidance.
```

## Routing Principles section

Keep global principles short. They are guardrails, not an architecture essay.

Recommended principles:

1. Route by evidence, not by guessing.
2. Responsibility may be shared or scoped; do not invent a single owner.
3. A shared library stacktrace is not enough to route to the shared library maintainer.
4. The consuming runtime component, bounded context, process step, integration side, platform signal, data-store signal or external contract determines the candidate route.
5. If evidence is ambiguous, retain the current scope or request more evidence.
6. Platform, infrastructure, data access and broker failures route differently from application contract failures.
7. Handoff requires required evidence.
8. Handoff rules do not decide root cause.
9. Handoff rules must not duplicate core catalog entries.
10. The current handler remains responsible for collecting the next evidence when no route threshold is met.

## Rule Priority Model section

Use priority to resolve conflicts between matching rules.

Recommended ranges:

- `90-100`: safety, security, data exposure, severe compliance risk or major platform outage coordination;
- `80-89`: clear external, mediated integration or vendor coordination rules;
- `70-79`: cross-context, process-boundary or multi-team collaboration rules;
- `60-69`: platform, database, messaging, scheduler, workflow, observability or runtime-infrastructure rules;
- `50-59`: shared-library, generated-client, repository-scope or runtime-mapping refinement rules;
- `30-49`: evidence-threshold, anti-misrouting or weak-signal guidance;
- `0-29`: no-handoff and retain-with-current-handler defaults.

Higher priority rules override lower priority rules only when their `Applies when` conditions are satisfied. A high-priority rule with unmet required evidence must not override a lower-priority rule that is fully supported.

## Rule Index section

Use a compact table for discovery. Keep it short enough for humans and LLMs.

Recommended columns:

```md
| Rule ID | Type | Primary action | Priority | Candidate recipients | Key evidence |
| --- | --- | --- | ---: | --- | --- |
```

The table is a summary only. The authoritative rule definition is the structured rule block under `## Rules`.

Use `None`, `Current handler`, `Platform support`, `External owner`, or canonical IDs when no concrete team ID is known.

## Rule entry schema

Every rule under `## Rules` must use this structure and field order.

```md
### `rule-id`

**Rule id:** `rule-id`

**Title:** Human-readable rule title.

**Lifecycle status:** `active | candidate | deprecated | retired`

**Rule type:** `integration-failure | mediated-integration-failure | platform-failure | data-access-failure | cross-context | shared-library | external-system | process-breakpoint | runtime-mapping | evidence-threshold | no-handoff | escalation | anti-misrouting | collect-evidence`

**Primary action:** `route-to-candidate | retain-current-scope | involve-partners | escalate-to-platform | coordinate-with-external-party | request-more-evidence | no-handoff`

**Priority:** `integer from 0 to 100`

**Confidence:** `high | medium | low`

**Summary**

Short explanation of the coordination decision this rule makes.

**Applies when**

- Concrete evidence condition.
- Concrete evidence condition.
- Minimum match: short statement explaining the required combination of signals.

**Does not apply when**

- Concrete counter-condition.
- Concrete counter-condition.

**Routing roles**

| Role | Target | Use when | Reason |
| --- | --- | --- | --- |
| `first-responder` | `team-or-role-id` | ... | ... |
| `domain-expert` | `team-or-role-id` | ... | ... |
| `integration-partner` | `team-or-role-id` | ... | ... |
| `platform-support` | `team-or-role-id` | ... | ... |
| `current-handler` | `current-handler` | ... | ... |

**Route decision**

- **Responsibility basis:** `runtime-component | bounded-context | process-step | integration-source-side | integration-target-side | mediator | consuming-system | provider-system | data-store | platform-infrastructure | external-contract | evidence-threshold | current-scope | unknown`
- **Candidate teams:** `team-id`, `team-id`, or `None`
- **Partner teams:** `team-id`, `team-id`, or `None`
- **External parties:** `external-party-id`, `external-party-id`, or `None`
- **Retain current scope until:** condition, or `None`

**Required evidence**

- `correlationId`, trace identifier or equivalent incident identifier when available.
- Environment or deployment scope when available.
- Concrete rule-specific evidence.

**Expected first actions**

1. Action grounded in the evidence.
2. Action grounded in the evidence.

**Related operational context**

- **Systems:** `system-id`, or `None`
- **Runtime components:** `runtime-component-id`, or `None`
- **Repositories:** `repo-id`, or `None`
- **Processes:** `process-id`, or `None`
- **Integrations:** `integration-id`, or `None`
- **Bounded contexts:** `context-id`, or `None`
- **Teams:** `team-id`, or `None`
- **External parties:** `external-party-id`, or `None`
- **Glossary terms:** `term-id`, or `None`

**LLM tool hints**

- Use this rule when the user asks ...
- Do not answer that ... unless evidence includes ...
- Prefer tools or catalog lookups ...

**Evidence**

- `source-type:source-id/path` — observation — confidence: `high | medium | low`

**Source coverage**

- **Status:** `complete | partial | unknown`
- **Checked sources:** `source`, `source`, or `None`
- **Missing sources:** `source`, `source`, or `None`

**Limitations**

- Known limitation, or `None`.
```

All active rules must define at least one `first-responder` or `current-handler` role. If no valid handoff can be made, use `current-handler` and a `retain-current-scope`, `request-more-evidence` or `no-handoff` primary action.

## Rule ID and naming conventions

Use stable kebab-case IDs.

Good examples:

- `email-provider-timeout-via-crm-core`
- `shared-customer-library-stacktrace`
- `customer-segmentation-db-pool-exhaustion`
- `campaign-segment-cross-context-mismatch`
- `retain-with-current-handler-until-runtime-match`
- `mediated-payment-gateway-vs-final-bank-target`

Bad examples:

- `rule1`
- `backend-error`
- `handoff-to-team-a`
- `timeout`
- `unknown-owner`

A rule ID should describe the routing situation, not only the target team.

Do not rename existing rule IDs unless the current ID is clearly wrong, duplicated or misleading. If a rename is necessary, preserve the old ID as evidence or mention the cleanup in a durable gap if references may remain stale.

## Rule type semantics

Use one of these rule types:

- `integration-failure` — sync, async, file, webhook, batch or data integration evidence changes routing;
- `mediated-integration-failure` — mediator/gateway/broker and final target must be separated;
- `platform-failure` — runtime, network, scheduler, broker, observability, identity or infrastructure evidence dominates;
- `data-access-failure` — DB, persistence, cache, schema, pool or data-quality symptoms require data/platform support plus domain context;
- `cross-context` — evidence crosses bounded contexts and requires collaboration;
- `shared-library` — stacktrace or code evidence is in shared code and should not route by library alone;
- `external-system` — issue likely belongs to an external vendor/system after internal contract checks;
- `process-breakpoint` — route changes at a specific process step, state transition, job or scheduler;
- `runtime-mapping` — rule helps map deployment/runtime evidence to a coordination target;
- `evidence-threshold` — rule explains when evidence is too weak for handoff;
- `no-handoff` — rule prevents premature escalation;
- `escalation` — rule defines when to involve additional roles after first checks;
- `anti-misrouting` — rule prevents routing to a misleading team, library, repo or external party;
- `collect-evidence` — rule tells the current handler which evidence is required before a routing decision.

## Primary action semantics

Use one of these primary actions:

- `route-to-candidate` — hand off first action to a concrete candidate team, role or external party;
- `retain-current-scope` — keep investigation with the current handler until a threshold is met;
- `involve-partners` — keep or route primary action while involving one or more partner teams;
- `escalate-to-platform` — involve platform support because runtime, data, broker, identity, network, observability or scheduler evidence dominates;
- `coordinate-with-external-party` — coordinate with an external owner/vendor/partner after internal evidence is checked;
- `request-more-evidence` — no route yet; collect specific evidence first;
- `no-handoff` — explicitly avoid handoff for this evidence pattern.

## Routing role requirements

Every active rule must define at least one `first-responder` or `current-handler` role.

Use explicit target IDs when available:

- team IDs from `teams.yml`;
- external party IDs from `teams.yml`, `systems.yml` or `integrations.yml`;
- platform support role labels if no team ID exists;
- `current-handler` when evidence is insufficient for handoff.

Do not write only:

```md
Route to the owner of the integration.
```

Instead write a concrete role and explain the evidence requirement:

```md
| `integration-partner` | `team:crm-integration-platform` | When the failing span is inside the integration gateway and target response is missing. | Gateway evidence points to mediator behavior, not the consumer runtime. |
```

If a concrete target is not known but the rule is still useful, use an explicit role label and create a durable `Gap`:

```md
| `external-owner` | `external:email-platform-owner-unknown` | When provider-side failure is confirmed. | External support path is not yet documented. |
```

## Evidence predicate quality

### Strong predicates

Strong predicates can support routing when combined with the rule's minimum match:

- canonical IDs from operational context files;
- exact service, runtime component, deployment, container, artifact or application names;
- exact integration IDs or contract names;
- exact queue, topic, exchange, routing key, endpoint template, host property key or gateway route;
- exact package prefix, class name, exception class, generated client package or dependency coordinate;
- exact DB schema, table, Hikari pool, datasource or migration path;
- exact job, scheduler, workflow, BPMN or state-machine name;
- explicit support/routing/ownership documentation;
- runtime evidence tied to a correlation ID, trace ID, span ID, log marker or alert label.

### Medium predicates

Medium predicates can support routing when combined with at least one strong predicate:

- endpoint prefixes;
- repeated log markers;
- recurring operator labels;
- README or documentation references without formal responsibility mapping;
- consistent package/module names across multiple sources;
- repeated runtime symptoms pointing to the same system or integration.

### Weak predicates

Weak predicates must not route alone:

- generic terms such as `timeout`, `backend`, `integration`, `database`, `service`, `processor`, `worker`, `client`, `adapter`;
- one arbitrary class, method, DTO, variable or package name;
- historical commit author, branch name, team nickname or one-off comment;
- generic framework exceptions with no system or integration context;
- incomplete host or queue names without source/target context.

If only weak predicates are available, create or update a `collect-evidence`, `evidence-threshold`, `no-handoff` or `anti-misrouting` rule only when it changes operator behavior. Otherwise put temporary uncertainty in `BUILD MEMORY`.

## Required evidence policy

Every active rule must state required evidence.

Default required evidence:

- incident identifier: `correlationId`, trace ID, span ID, log group, ticket ID or equivalent when available;
- runtime/deployment scope: environment, service name, component name, deployment name, namespace or artifact name when available;
- concrete rule-specific evidence: endpoint, class, queue, topic, event, schema, table, Hikari pool, job, error marker, host property, integration ID, process step or bounded context;
- evidence timestamp or time range when relevant;
- evidence source: logs, telemetry, code, config, operational context, docs or operator note.

For handoff to an external party, require evidence that internal application, mediator and platform checks are either completed or not applicable.

For platform escalation, require at least one platform-specific signal unless the rule is explicitly about evidence collection.

For shared-library anti-misrouting, require evidence that the class is from shared code and that the consuming runtime or caller can be identified or must still be identified.

## Expected first action policy

Expected first actions must be short and operationally useful.

Good first actions:

- Check the failing integration span and verify whether the target responded.
- Confirm whether the message reached the broker and whether the consumer lag increased.
- Inspect Hikari pool and datasource metrics for the matched runtime component.
- Use GitLab code search scope from `repo-map.yml` to inspect the consuming service before routing to a shared library maintainer.
- Check the process step state and the event that should have advanced it.
- Ask current handler to collect service name, environment, endpoint, exception and integration ID before handoff.

Bad first actions:

- Fix the bug.
- Contact the owner.
- Investigate.
- Check everything.
- Root cause the issue.

## LLM tool readiness

Each rule must be useful when returned as a standalone retrieved context item.

A retrieved rule should tell the model:

- why the rule exists;
- when it applies;
- when it does not apply;
- who should act first;
- who else should be involved;
- what evidence is required;
- what the recipient should check first;
- which systems, runtime components, integrations, repos, processes, contexts, teams and glossary terms are related;
- what the model must not conclude without additional evidence;
- which tools or catalog lookups are appropriate before answering.

Avoid references such as `as above`, `same as previous`, `known owner`, `normal process` or `see catalog`. Use canonical IDs and compact explanations.

## Special routing patterns

### Shared library and generated client evidence

A stacktrace in a shared library, generated client or integration adapter does not prove that the library maintainer owns the failing runtime behavior.

When the failing class belongs to shared code:

1. identify the consuming runtime component when possible;
2. identify the caller repository/code-search scope through `repo-map.yml`;
3. keep the current handler or consuming runtime actor as first responder until evidence proves a reusable library defect;
4. involve the library maintainer as `code-maintainer` or partner only when evidence points to library behavior shared across consumers;
5. do not route solely because a stacktrace frame contains the library package.

Recommended reusable rule ID:

```md
### `shared-library-stacktrace-requires-consuming-runtime`
```

### Mediated integrations

For gateway, broker, ESB, API gateway, identity proxy, file relay or other mediated contracts:

- separate immediate mediator failure from final target failure;
- route to mediator/platform support only when mediator evidence dominates;
- involve final target or external owner only when target-side evidence exists;
- retain current scope when the only evidence is an outbound call with no response classification;
- use `integrations.yml` for contract structure and this file only for routing refinement.

### Platform and infrastructure routing

Escalate or involve platform support only when evidence points to platform behavior, such as:

- broker connection failure, partition outage, DLQ/retry backlog or consumer lag unrelated to one business contract;
- database connection pool exhaustion, global datasource failure, migration platform issue or cross-service DB outage;
- network, DNS, TLS, gateway, identity, observability or deployment platform failure;
- scheduler, worker orchestration or workflow engine outage;
- multiple unrelated systems showing the same platform symptom.

Do not use platform support as a default fallback for unknown application behavior.

### Data access and DB/code grounding

When DB evidence appears, do not route only from a table name.

First connect the evidence to:

- runtime component or service;
- datasource, Hikari pool, schema or migration path;
- repository/entity/repository class when available;
- process, bounded context or integration when data meaning matters;
- platform/data support when the symptom is infrastructure-level;
- domain/data owner when the symptom is data-quality or semantic.

A table or schema name alone may be a grounding signal, but it is not necessarily a handoff target.

### External coordination

Coordinate with an external party only when evidence points outside the internal organization after internal contract checks.

Useful external coordination evidence includes:

- target-side HTTP status, provider error code, vendor error marker or external SLA alert;
- external webhook failure confirmed after internal ingress/gateway checks;
- documented external owner in `systems.yml`, `integrations.yml` or `teams.yml`;
- known external support path or vendor route label;
- internal mediator or consumer evidence showing request left the organization.

If the external owner or support path is unknown and it affects handoff, create a durable `Gap`.

### No-handoff and evidence-threshold rules

Use these rules to prevent premature routing.

A no-handoff or evidence-threshold rule is appropriate when:

- the evidence is generic and not tied to a system/component;
- runtime identity is missing;
- only a shared library frame is known;
- an integration name is mentioned but source/target side is unknown;
- DB evidence appears without service/datasource/process grounding;
- a team name appears in code but no responsibility evidence exists;
- a log message uses a business term but no context or process is grounded.

The rule should tell the current handler exactly what evidence to collect next.

## Gap schema

Use `## Gaps` for durable, final catalog gaps only. Do not use `## Open Questions`.

Each gap must use this structure and field order:

```md
### `gap-id`

**Gap id:** `gap-id`

**Type:** `routing-ambiguity | missing-team | missing-external-party | missing-on-call-target | missing-evidence | ambiguous-responsibility | conflicting-routing | unresolved-external-owner | missing-catalog-reference | human-confirmation-required | schema-reference-gap`

**Severity:** `low | medium | high | critical`

**Status:** `open | in-review | blocked | resolved | superseded`

**Affects**

- `rule-id`, `system-id`, `runtime-component-id`, `integration-id`, `process-id`, `context-id`, `team-id`, `term-id`, or `external-party-id`

**Description**

Durable routing issue that remains unresolved.

**Why it matters**

Operational impact of not knowing this.

**Evidence checked**

- source that was checked, or `None`

**Required sources**

- source that should resolve it

**Suggested next action**

- next curation or validation action
```

Use final gaps only for:

- unresolved responsibility after support or ownership evidence was checked;
- unclear route after enough evidence was checked;
- missing external owner or vendor support path;
- missing on-call, queue or escalation target when required for handoff;
- missing referenced catalog node after expected sources were checked;
- conflicting routing evidence across sources;
- unclear process/bounded-context/integration side that affects routing;
- ambiguous shared-library versus consuming-runtime responsibility;
- human/domain confirmation required for a durable routing decision.

Do not use final gaps for temporary cross-repo joins that are still pending in build memory.

## Internal workflow

Before editing the file, perform these internal steps:

1. Identify whether the new facts actually change incident handoff behavior.
2. Classify each candidate as a route rule, retain/no-handoff rule, partner-involvement rule, escalation rule, evidence-collection rule, anti-misrouting rule, external-coordination rule or durable routing gap.
3. Check whether the candidate fact belongs in another operational context file instead of `handoff-rules.md`.
4. Check whether uncertainty is temporary cross-repository uncertainty or a durable final gap.
5. Merge confirmed routing rules into the existing rule set.
6. Preserve active confirmed rules unless explicit contradictory evidence exists.
7. Remove or rewrite legacy ownership-only rules that conflict with the version-1 model.
8. Normalize legacy `Open Questions` into `## Gaps` only when they are durable routing gaps; otherwise leave them out of final output and rely on `BUILD MEMORY` or sidecar discovery artifacts.
9. Validate rule IDs, references, evidence predicates, routing roles, routing decisions, priorities, confidence and gaps.
10. Return the full updated Markdown only.

Do not expose this internal workflow in the final output.

## Discovery checklist

When the input is a repository, documentation fragment, deployment manifest, support matrix, operational context update or runtime evidence, inspect the available sources below.

### Routing policy and support docs

- README, runbooks, support pages and on-call docs;
- support queue labels;
- escalation docs;
- ownership/responsibility matrices;
- team handoff notes;
- incident postmortems that document routing decisions;
- CODEOWNERS or repository metadata only as supporting evidence, not ownership by itself;
- external vendor support docs and contract labels.

### Runtime and deployment evidence

- service/application/container/deployment names;
- namespace, cluster, environment and region labels;
- artifact and image names;
- process names;
- runtime tags;
- health endpoints;
- telemetry service names;
- alert labels and dashboards.

### HTTP and synchronous integration evidence

- endpoint prefixes and templates;
- gateway routes;
- base URL property keys;
- host labels;
- client/controller classes;
- generated client packages;
- HTTP status and provider error codes;
- timeout/retry/circuit-breaker markers.

### Messaging and async evidence

- queue, topic, exchange, routing-key and binding names;
- DLQ names;
- consumer groups;
- event and schema names;
- listener/publisher classes;
- lag, retry, dead-letter and broker connection symptoms.

### Repository and shared code evidence

- repository IDs and project paths;
- module paths;
- package prefixes;
- shared library or generated client coordinates;
- stacktrace frames;
- generated code paths;
- common adapter packages;
- source layout pointing to consumer versus library ownership.

### Data and DB evidence

- datasource names;
- Hikari pool names;
- schemas, tables and entities;
- repository/DAO classes;
- migration paths;
- data-quality labels;
- DB platform alerts;
- cache names and persistence-specific exceptions.

### Process and context evidence

- process IDs and step IDs;
- workflow/BPMN/state-machine definitions;
- scheduler and job names;
- status transitions;
- bounded context IDs;
- domain terms and aliases;
- event sequences across contexts.

## Cross-file reference rules

Use canonical IDs from related operational context files whenever available.

Allowed reference forms in rule text:

- `systems.yml:<system-id>`;
- `systems.yml:runtimeComponents:<runtime-component-id>`;
- `repo-map.yml:<repository-id>`;
- `repo-map.yml:modules:<module-id>`;
- `integrations.yml:<integration-id>`;
- `processes.yml:<process-id>`;
- `processes.yml:steps:<step-id>`;
- `bounded-contexts.yml:<context-id>`;
- `teams.yml:<team-id>`;
- `teams.yml:externalParties:<external-party-id>`;
- `glossary.md:<term-id>`.

Use IDs, not display names, when the ID is known.

Do not create a final rule referencing unknown IDs unless the rule is still operationally valid and the missing reference is captured as a durable `Gap`.

If the unknown ID may be resolved by another repository scan, write to `BUILD MEMORY` instead of creating a final gap.

## Normalization from legacy content

If `CURRENT FILE` contains legacy sections such as:

- `General Rules`;
- `Default Routing`;
- large symptom tables;
- `Internal System Failures`;
- `External Integration Failures`;
- `Database Failures`;
- `RabbitMQ Failures`;
- `Messaging Failures`;
- `Cross-Context Routing`;
- `Shared Library Routing`;
- `Ownership Rules`;
- `Open Questions`;

normalize them into the version-1 structure.

Recommended mapping:

- generic route-to-owner principle -> routing principle rewritten to avoid single-owner assumption;
- table row that changes next actor -> structured rule block;
- table row that merely repeats an integration, system or process signal -> omit from this file or keep in the relevant core catalog;
- shared library routing section -> one or more `shared-library` or `anti-misrouting` rules;
- database/broker platform rows -> `data-access-failure` or `platform-failure` rules;
- cross-context bullets -> `cross-context` or `involve-partners` rules;
- external vendor bullets -> `external-system` or `mediated-integration-failure` rules;
- evidence prerequisites -> `collect-evidence` or `evidence-threshold` rules;
- `Open Questions` that are durable routing gaps -> `## Gaps`;
- `Open Questions` caused by scan order -> `BUILD MEMORY` or sidecar discovery artifacts, not final `handoff-rules.md`.

## Rule count and granularity

Prefer fewer, higher-quality rules.

Do not create one rule for every endpoint, queue, class, table or error code. Group rules by operational routing decision when the first action, evidence threshold and recipient set are the same.

Create separate rules when:

- the primary action differs;
- the rule type differs materially;
- the candidate recipient set differs;
- the required evidence differs materially;
- platform versus application responsibility differs;
- external coordination differs;
- shared-library anti-misrouting is needed;
- priority or confidence differs significantly;
- a process step or bounded-context boundary changes coordination behavior.

Merge rules when:

- they have the same evidence threshold;
- they route to the same role set;
- they differ only by environment-specific host names or aliases;
- they duplicate a default catalog fact without changing coordination.

## Security and privacy

Do not include:

- secrets, credentials, tokens, API keys, passwords or private certificates;
- personal customer data;
- private individual contact details;
- full production payloads;
- sensitive business records;
- raw stacktraces longer than necessary for stable class/exception markers;
- internal URLs containing credentials or tokens.

Prefer stable IDs, labels, class names, package prefixes, route templates, config key names and redacted evidence.

## Quality gates before final output

Before returning the final Markdown, verify:

- The file starts with `# Handoff Rules`.
- The metadata block has `schemaVersion: 1` and `kind: operational-context.handoff-rules`.
- The file contains `## Catalog Metadata`, `## Purpose`, `## Default Routing Model`, `## Routing Principles`, `## Rule Priority Model`, `## Rule Index`, `## Rules`, and `## Gaps`.
- No legacy `## Open Questions` section remains.
- Every rule has a stable heading ``### `rule-id` ``.
- Every active rule has `Rule id`, `Title`, `Lifecycle status`, `Rule type`, `Primary action`, `Priority`, `Confidence`, `Summary`, `Applies when`, `Does not apply when`, `Routing roles`, `Route decision`, `Required evidence`, `Expected first actions`, `Related operational context`, `LLM tool hints`, `Evidence`, `Source coverage`, and `Limitations`.
- Rule type values are from the allowed set.
- Primary action values are from the allowed set.
- Every active rule has at least one `first-responder` or `current-handler` role.
- No rule relies on a single owner when multiple roles are known.
- No rule routes to `owner of X` without a concrete ID or clearly defined role.
- No rule stores repo-local uncertainty as final guidance.
- No duplicate rules exist under different IDs.
- No rule duplicates a normal integration/system/process/team entry without changing coordination behavior.
- Candidate teams and external parties are referenced by canonical IDs when known.
- Weak evidence is not used as high-confidence routing.
- Temporary cross-repository uncertainty is not in final `## Gaps`.
- Durable gaps are typed, actionable and tied to affected nodes.
- No sensitive data, credentials, tokens, private individual contacts or personal customer data are present.
- Markdown is complete and ready to be used as the final `handoff-rules.md` file.

## Generic example

This example illustrates the version-1 structure. Do not copy example values into a real catalog unless evidence supports them.

````md
# Handoff Rules

```yaml
schemaVersion: 1
kind: operational-context.handoff-rules
```

## Catalog Metadata

- **Format version:** `1`
- **Catalog role:** `incident-analysis-routing-view`
- **Primary use case:** `incident-analysis`
- **Downstream uses:** `handoff-routing`, `incident-triage`, `follow-up-qa`, `coordination-explanation`, `impact-analysis-support`, `dependency-analysis-support`, `change-risk-review-support`
- **Core fact sources:** `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md`
- **Temporary build state:** `BUILD MEMORY`, when provided

## Purpose

This file defines evidence-backed coordination rules that refine default routing from the operational-context catalog. It is used when runtime, repository, integration, process, platform, external, data or semantic evidence requires more than a default responsibility lookup.

## Default Routing Model

1. Use `systems.yml` to identify the affected logical system and runtime component.
2. Use `repo-map.yml` to resolve code scope, including shared libraries and generated clients.
3. Use `integrations.yml` when evidence points to a specific contract, mediator, provider, consumer, external system, broker, data source or channel.
4. Use `processes.yml` and `bounded-contexts.yml` when evidence crosses process steps, semantic boundaries or local languages.
5. Use `teams.yml` to map responsibility roles, not a single owner.
6. Use `glossary.md` to disambiguate local terms, acronyms and error markers.
7. Use this file only when the default routing needs refinement, escalation, collaboration, evidence-threshold guidance, anti-misrouting guidance, external coordination or no-handoff guidance.

## Routing Principles

1. Route by evidence, not by guessing.
2. Responsibility may be shared or scoped; do not invent a single owner.
3. A shared library stacktrace is not enough to route to the shared library maintainer.
4. If evidence is ambiguous, retain the current scope or request more evidence.
5. Handoff rules do not decide root cause.

## Rule Priority Model

- `80-89`: external or mediated integration rules.
- `70-79`: cross-context and process-boundary rules.
- `60-69`: platform, database, broker, scheduler, observability and runtime rules.
- `50-59`: shared-library or repository-scope routing refinement rules.
- `0-49`: evidence-threshold and no-handoff rules.

## Rule Index

| Rule ID | Type | Primary action | Priority | Candidate recipients | Key evidence |
| --- | --- | --- | ---: | --- | --- |
| `email-provider-timeout-via-crm-core` | `integration-failure` | `coordinate-with-external-party` | 82 | `external:email-platform-provider`, `team:crm-integrations` | `crm-core-api`, `EmailProviderClient`, `EMAIL_PROVIDER_CALL_FAILED` |
| `shared-customer-library-stacktrace` | `shared-library` | `retain-current-scope` | 54 | `current-handler`, `team:customer-core` as partner when consumer is known | shared package plus unknown consuming runtime |

## Rules

### `email-provider-timeout-via-crm-core`

**Rule id:** `email-provider-timeout-via-crm-core`

**Title:** Email provider timeout from CRM Core

**Lifecycle status:** `active`

**Rule type:** `integration-failure`

**Primary action:** `coordinate-with-external-party`

**Priority:** `82`

**Confidence:** `high`

**Summary**

Coordinate with the email platform provider when CRM Core reaches the email provider contract and provider-side timeout or gateway symptoms are present. Involve CRM integrations first if mediator or request construction evidence is still ambiguous.

**Applies when**

- Runtime evidence points to service `crm-core-api`.
- Endpoint or operation contains `/api/notifications/email` or client class `EmailProviderClient`.
- Error evidence contains `EmailProviderTimeoutException`, HTTP `502`, HTTP `504` or marker `EMAIL_PROVIDER_CALL_FAILED`.
- Minimum match: runtime component plus provider contract signal plus provider-side error signal.

**Does not apply when**

- The email request is rejected before calling `EmailProviderClient`.
- Evidence contains only template validation errors or missing recipient preferences.

**Routing roles**

| Role | Target | Use when | Reason |
| --- | --- | --- | --- |
| `first-responder` | `team:crm-integrations` | When provider-side evidence is not yet separated from mediator/client behavior. | Internal contract evidence must be verified before external coordination. |
| `external-owner` | `external:email-platform-provider` | When target-side timeout/gateway evidence is confirmed. | Provider-side symptoms point outside the internal runtime component. |
| `domain-expert` | `team:campaign-domain` | Only when the failing request belongs to campaign sending logic. | Campaign semantics may be needed, but they are not the default first responder. |

**Route decision**

- **Responsibility basis:** `external-contract`
- **Candidate teams:** `team:crm-integrations`
- **Partner teams:** `team:campaign-domain`
- **External parties:** `external:email-platform-provider`
- **Retain current scope until:** target-side timeout or gateway evidence is confirmed

**Required evidence**

- `correlationId` or trace ID.
- Environment and `crm-core-api` runtime scope.
- `EmailProviderClient` call, `/api/notifications/email` endpoint or integration ID.
- Provider-side error marker, status code or timeout classification.

**Expected first actions**

1. Check the failing integration span and confirm whether the provider responded.
2. If the request left CRM Core and provider-side timeout is confirmed, coordinate with `external:email-platform-provider` using the trace and timestamp.

**Related operational context**

- **Systems:** `systems.yml:crm-core`, `systems.yml:email-platform`
- **Runtime components:** `systems.yml:runtimeComponents:crm-core-api`
- **Repositories:** `repo-map.yml:crm-core-api-repo`
- **Processes:** `processes.yml:email-notification-delivery`
- **Integrations:** `integrations.yml:crm-core-to-email-platform-send-message`
- **Bounded contexts:** `bounded-contexts.yml:notification-delivery`
- **Teams:** `teams.yml:crm-integrations`, `teams.yml:campaign-domain`
- **External parties:** `teams.yml:externalParties:email-platform-provider`
- **Glossary terms:** `glossary.md:email-notification`

**LLM tool hints**

- Use GitLab/code tools only to verify internal request construction or mediator behavior; do not use them to infer provider ownership.
- Do not say the campaign team owns the incident unless campaign-specific validation or content evidence is present.
- When provider-side evidence is missing, ask for trace/span or gateway evidence before external coordination.

**Evidence**

- `config:application.yml` — email provider base URL property exists — confidence: `medium`
- `code:EmailProviderClient` — client class used by CRM Core — confidence: `high`
- `runtime-log:EMAIL_PROVIDER_CALL_FAILED` — provider call failed with timeout marker — confidence: `high`

**Source coverage**

- **Status:** `partial`
- **Checked sources:** `crm-core-api-repo`, `runtime logs`
- **Missing sources:** `email provider support contract`, `gateway telemetry`

**Limitations**

- External support path must be confirmed if `external:email-platform-provider` is not present in `teams.yml`.

### `shared-customer-library-stacktrace`

**Rule id:** `shared-customer-library-stacktrace`

**Title:** Shared customer library stacktrace requires consuming runtime evidence

**Lifecycle status:** `active`

**Rule type:** `shared-library`

**Primary action:** `retain-current-scope`

**Priority:** `54`

**Confidence:** `high`

**Summary**

Do not route to the shared library maintainer only because a stacktrace contains shared customer package frames. Identify the consuming runtime component first and involve the library maintainer only if evidence points to reusable library behavior.

**Applies when**

- Stacktrace contains package prefix `com.example.customer.shared` or generated client package `com.example.customer.client`.
- The consuming service or runtime component is missing, ambiguous or different from the library repository.
- Minimum match: shared package frame plus missing or ambiguous consuming runtime.

**Does not apply when**

- Evidence proves the defect is in shared library behavior across multiple consumers.
- The consuming runtime component and application-level failing operation are already confirmed.

**Routing roles**

| Role | Target | Use when | Reason |
| --- | --- | --- | --- |
| `current-handler` | `current-handler` | Until consuming runtime component is identified. | Shared library frames alone do not identify the owner of runtime behavior. |
| `code-maintainer` | `team:customer-shared-library` | Only when reusable library defect evidence exists. | Library maintainer may be a partner, not default incident owner. |
| `runtime-operator` | `team:resolved-consuming-runtime-team` | When consuming runtime is identified from deployment/log evidence. | Consuming component owns the runtime behavior being executed. |

**Route decision**

- **Responsibility basis:** `consuming-system`
- **Candidate teams:** `None`
- **Partner teams:** `team:customer-shared-library`
- **External parties:** `None`
- **Retain current scope until:** consuming runtime component, service name, deployment name or caller repository is identified

**Required evidence**

- Stacktrace frame with shared package or generated client package.
- Runtime service/deployment/application name, caller repository or trace span identifying the consumer.
- Evidence of repeated failure across consumers before routing to shared library maintainer.

**Expected first actions**

1. Resolve the consuming runtime component from logs, deployment context or GitLab code-search scope.
2. Inspect the caller path in the consuming repository before involving the shared library maintainer.

**Related operational context**

- **Systems:** `None`
- **Runtime components:** `None`
- **Repositories:** `repo-map.yml:customer-shared-library-repo`
- **Processes:** `None`
- **Integrations:** `None`
- **Bounded contexts:** `bounded-contexts.yml:customer-profile`
- **Teams:** `teams.yml:customer-shared-library`
- **External parties:** `None`
- **Glossary terms:** `glossary.md:customer-profile`

**LLM tool hints**

- Use operational context and GitLab code-search scope to find the consuming repository before recommending a team.
- Do not answer that the shared library team owns the incident from stacktrace evidence alone.
- Ask for service name, deployment name or caller class when consumer evidence is missing.

**Evidence**

- `code:customer-shared-library-repo` — package prefix `com.example.customer.shared` exists — confidence: `high`
- `catalog:repo-map.yml` — repository is a shared library — confidence: `high`

**Source coverage**

- **Status:** `partial`
- **Checked sources:** `customer-shared-library-repo`, `repo-map.yml`
- **Missing sources:** `consuming runtime logs`, `deployment context`

**Limitations**

- Candidate runtime team cannot be selected until consuming runtime evidence is available.

## Gaps

### `gap-missing-email-platform-external-owner`

**Gap id:** `gap-missing-email-platform-external-owner`

**Type:** `unresolved-external-owner`

**Severity:** `medium`

**Status:** `open`

**Affects**

- `email-provider-timeout-via-crm-core`
- `integrations.yml:crm-core-to-email-platform-send-message`
- `systems.yml:email-platform`

**Description**

The external support owner or route label for the email platform is not confirmed.

**Why it matters**

Provider-side timeout evidence may be clear, but the operator still needs a stable external coordination target.

**Evidence checked**

- `crm-core-api-repo`
- `runtime logs`

**Required sources**

- vendor support documentation
- integration ownership documentation
- `teams.yml` external party entry

**Suggested next action**

- Confirm the external owner and add it to `teams.yml:externalParties`, then reference it in this rule.
````

## Input

The parent prompt or caller should provide input in this form when possible:

```md
## CURRENT FILE

Existing `handoff-rules.md` content.

## NEW FACTS

New evidence, repository scan facts, documentation fragments, runtime evidence, operational-context updates or operator-provided facts.

## SCAN SCOPE

Repository, branch, commit, module, deployment/config source, documentation fragment or runtime scope actually analyzed.

## FULL OPERATIONAL CONTEXT

Relevant IDs and summaries from `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` and related views.

## BUILD MEMORY

Temporary cross-repository uncertainty and pending joins. Use during reasoning only; do not emit inside final `handoff-rules.md`.
```
