# glossary.md update prompt

Update only `glossary.md`.

This prompt is schema-authoritative for `glossary.md`. If a parent operational-context prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy and merge policy for the glossary.

## Purpose

Maintain `glossary.md` as an enterprise-grade, evidence-backed, queryable vocabulary and semantic-grounding layer of the operational context catalog.

The glossary is not only for incident routing. It is an index layer used for:

- deterministic mapping from runtime, code, data and documentation evidence to operational graph nodes;
- GitLab/code search scope construction across main repositories, shared libraries, generated clients and integration modules;
- explaining local business, technical, process, integration and data language to users and LLM tools;
- function description and user-facing answers about classes, endpoints, events, database objects, processes and system behavior;
- impact analysis across bounded contexts, systems, runtime components, repositories, modules, processes, integrations, data models and teams;
- DB/code grounding before database diagnostics;
- incident triage as one downstream view;
- repository onboarding, documentation, follow-up investigation, Q&A and future AI analysis features.

A glossary entry should explain:

- what the term means locally;
- what the term does not mean locally;
- which aliases, acronyms, UI labels, code names or operator labels point to the same concept;
- where the term appears in runtime, code, data, APIs, events, logs, metrics, traces or documentation;
- which operational graph nodes the term connects to;
- how to avoid confusing it with neighboring local terms;
- how an LLM should use the term when answering or constructing search scope;
- what durable catalog gaps remain after available evidence has been checked.

## Non-goals

Do not turn `glossary.md` into:

- a generic dictionary;
- a full domain encyclopedia;
- long architecture prose;
- a product taxonomy dump;
- a full data dictionary;
- a process catalog;
- an integration catalog;
- a repository map;
- an ownership matrix;
- an incident escalation playbook;
- a scratchpad for temporary agent uncertainty.

Do not define common terms unless the local system uses them in a special, overloaded or operationally important way.

Good glossary terms:

- a business object with local semantics;
- a workflow, lifecycle, status or enum name used in events, logs, UI or support work;
- a local acronym or overloaded word;
- an integration-specific contract term, error marker or partner label;
- a data completeness/status marker with routing, grounding or analysis value;
- a runtime marker whose local meaning helps map evidence to a system or context;
- a class, package, module, event, queue, topic or table label with business meaning;
- a term that is easy to confuse across neighboring contexts, repositories, systems or teams.

Bad glossary terms:

- `REST`, `JSON`, `database`, `service`, `controller`, `repository`, `DTO`, unless the system uses the term with a special local meaning;
- every enum value in a taxonomy when only a few values appear in evidence, routing or user-facing analysis;
- generic architecture descriptions;
- full integration contracts that belong in `integrations.yml`;
- full processes that belong in `processes.yml`;
- full code/module maps that belong in `repo-map.yml`;
- team ownership facts that belong in `teams.yml` or a routing view;
- definitions copied from public documentation without local operational meaning.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `glossary.md`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, documentation fragments, runtime evidence, database discovery, existing operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, library, generated client, documentation fragment, branch, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `systems.yml`, runtime components, `repo-map.yml`, `processes.yml`, `integrations.yml`, `bounded-contexts.yml`, `teams.yml`, handoff rules and existing glossary/terms.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate terms, unresolved references and partial facts. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

## Output

Return the full updated `glossary.md` content only.

Do not include Markdown fences.
Do not include explanations.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `glossary.md`.
Do not output `BUILD MEMORY` inside `glossary.md`.
Do not output a diff.

## Required top-level document structure

Use this exact top-level shape. Do not preserve legacy top-level fields.

```md
# Glossary

**Schema version:** `1`
**Catalog kind:** `operational-context-glossary`

## Terms

### `stable-term-id`
...

## Gaps

### `stable-gap-id`
...
```

Top-level order must be:

1. `# Glossary`
2. `**Schema version:** \`1\``
3. `**Catalog kind:** \`operational-context-glossary\``
4. `## Terms`
5. `## Gaps`

Use `## Gaps`, not legacy untyped `## Open Questions`. Durable unresolved questions are represented as typed gaps.

All term entries must live directly under `## Terms`.
All durable gap entries must live directly under `## Gaps`.
Do not create arbitrary grouping headings such as `Product Terms`, `Integration Terms`, `Runtime Terms` or `Shared Library Terms`.

## Required term entry schema

Each term entry must use this structure and field order:

```md
### `stable-term-id`

**Term:** Human-readable term label

**Category:** `business-term`

**Lifecycle status:** `active`

**Definition:** One or two concise sentences.

**Local meaning and boundaries**

- What this term means in this product, platform, repository, process or context.
- What this term does not mean here.

**Aliases**

- alias-one
- alias-two

**Use for**

- `deterministic-mapping`
- `code-search`
- `function-description`

**Match signals**

Exact:

- `event:CustomerProfileUpdated`

Strong:

- `endpoint:/api/customer-profiles`
- `class:CustomerProfileController`

Medium:

- `method:updateCustomerProfile`
- `metric:crm.customer.profile.update.count`

Weak:

- `alias:customer profile`

**Canonical references**

- `bounded-context:customer-profile`
- `system:crm-api`

**Related terms**

- `customer-segment`

**Not to confuse with**

- `user-account` — Authentication account used for login and access control, not CRM customer data.

**Responsibility hints**

- Responsibility is resolved through `bounded-context:customer-profile` and `system:crm-api`, not directly in this glossary entry.

**LLM tool hints**

- Explain using local system language, not a generic industry definition.
- When evidence contains `CustomerProfileUpdated`, map it to `bounded-context:customer-profile`.

**Evidence**

- `code:src/main/java/com/example/crm/customer/CustomerProfileController.java` — `endpoint:/api/customer-profiles` — confidence: `high`

**Source coverage:** `partial`

**Limitations**

- None
```

Every term must include all fields from the schema above.

Use `- None` for empty list fields.
Use all four `Match signals` buckets. If a bucket has no confirmed values, put `- None` under that bucket.
Do not omit fields. Stable field names make the Markdown usable as an LLM tool input and parser input.

## Term field semantics

### `term-id`

Use stable kebab-case IDs. The ID should represent the canonical local meaning, not only the display label.

Good IDs:

- `customer-profile`
- `customer-segment`
- `sales-opportunity`
- `support-ticket`
- `email-bounce`
- `partner-api-timeout`
- `account-crm-company`
- `account-identity-user`
- `case-support-ticket`

Bad IDs:

- `CustomerProfile`
- `customer_profile`
- `term1`
- `crm customer profile`
- `account` when the term is ambiguous and should be scoped.

Rules:

- Reuse an existing ID when the same local meaning already exists.
- Do not rename existing IDs unless the current ID is clearly invalid, the replacement is obvious and no cross-file reference will be broken.
- Use context-qualified IDs when the same label has different local meanings.
- Do not merge two terms only because they share the same display label.
- When splitting a term, keep cross-links in `Not to confuse with`.

### `Term`

Use the human-facing label used in explanations. It may include capitalization, acronyms and common display variants.

Examples:

```md
**Term:** CRM Account / Company Account
```

```md
**Term:** CustomerProfileUpdated
```

### `Category`

Use exactly one allowed value:

- `business-term`: business object, domain concept, business status or business rule.
- `process-term`: workflow, process stage, lifecycle step or process-specific artifact.
- `bounded-context-term`: term that primarily defines, identifies or disambiguates a semantic boundary.
- `integration-term`: external contract, event, queue/topic meaning, integration artifact or partner-facing label.
- `system-term`: system/application name with local meaning.
- `external-system-term`: local name or representation of an external system, partner platform or vendor model.
- `runtime-signal`: operational marker used mainly to identify runtime components, telemetry or diagnostics.
- `data-term`: entity, table, schema, column, enum, data state or data quality marker used in DB/code grounding.
- `domain-event`: event name or semantic event concept.
- `status-or-enum`: user-visible state or operationally important enum/status value.
- `error-term`: recurring exception, fault, failure label, DLQ marker, timeout class or known error phrase.
- `code-term`: package, class, interface, method family, module label or shared code construct with semantic value.
- `technical-term`: technical concept with local operational meaning.
- `platform-term`: infrastructure/platform concept used locally, such as tenant, workspace, routing domain, feature flag or runtime scope.
- `shared-library-term`: shared library concept that can appear in stack traces across systems.
- `acronym`: abbreviation whose expansion or local meaning matters.
- `team-local-term`: operator/team language that is not yet canonical product vocabulary but appears in support, handoff or analysis.
- `ambiguous-term`: term intentionally documented because its meaning differs across contexts, repositories, systems or teams.

Do not use `technical-term` or `platform-term` for generic utilities unless local usage affects interpretation, code search, impact analysis, DB grounding, tool use or operational diagnostics.

### `Lifecycle status`

Use exactly one allowed value:

- `active`: currently implemented or operationally relevant.
- `planned`: documented future term or concept.
- `deprecated`: still visible in code, logs, data or documentation but should not be used for new work.
- `retired`: no longer active but may appear in historical data, logs or old documentation.
- `external`: controlled by an external system, partner or vendor but represented in this catalog.
- `unknown`: observed but lifecycle status is not confirmed.

### `Definition`

Write one or two concise sentences describing the local concept.

Rules:

- Explain the local meaning inside this system.
- Avoid generic textbook definitions.
- Do not copy long documentation passages.
- Do not include full product taxonomies.
- Do not include implementation walkthroughs.
- Include only enough detail to understand the term during analysis.

Bad:

```md
**Definition:** A lead is a potential customer.
```

Better:

```md
**Definition:** A lead is the pre-qualified sales record used by CRM Core before it is converted into a customer account and sales opportunity.
```

### `Local meaning and boundaries`

Use bullets, not long paragraphs.

This section answers:

- what the term means in this platform, product, repository, process or context;
- which bounded context, system, integration, process or team-local vocabulary uses the term;
- what neighboring meaning it does not cover;
- whether the term is local, shared, external, overloaded, deprecated or ambiguous;
- whether the same label needs scoped terms elsewhere.

Good:

```md
**Local meaning and boundaries**

- In CRM, `account` means a customer company/account record managed by the customer profile context.
- It does not mean an authentication login account.
- It does not mean a billing or financial account unless evidence explicitly points to billing.
```

### `Aliases`

Include only confirmed variants that point to the same local meaning:

- acronyms;
- UI labels;
- localized names;
- operator shorthand;
- historical names;
- plural forms;
- event names that represent the same concept;
- code labels users might search for.

Do not include unrelated search keywords. Put deterministic evidence in `Match signals` and answer guidance in `LLM tool hints`.

If two aliases have different local meanings, create separate terms instead of merging them.

### `Use for`

Use a non-empty list. Allowed values:

- `deterministic-mapping`
- `runtime-to-domain-mapping`
- `runtime-to-repository-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `integration-dependency-analysis`
- `db-grounding`
- `incident-analysis`
- `repository-onboarding`
- `cross-context-disambiguation`
- `qa`

Use only values that the term can actually support with evidence.

Guidance:

- Use `deterministic-mapping` when the term has concrete signals that can map evidence to graph nodes.
- Use `code-search` when the term narrows repositories, packages, classes, endpoints, events or modules.
- Use `function-description` when the term helps explain affected functionality in user-facing language.
- Use `impact-analysis` when the term helps identify downstream systems, processes, integrations or terms.
- Use `db-grounding` when the term helps map code/runtime evidence to tables, schemas, entities or data states.
- Use `incident-analysis` when the term helps interpret operational symptoms, errors, logs or handoff labels.
- Use `qa` when the term helps answer user questions about the system.

### `Match signals`

`Match signals` are deterministic or semi-deterministic strings useful for mapping runtime/code/data evidence to glossary terms and operational graph nodes.

Use grouped buckets exactly as shown:

```md
**Match signals**

Exact:

- `...`

Strong:

- `...`

Medium:

- `...`

Weak:

- `...`
```

Use a `type:value` convention inside backticks.

Recommended signal types:

- `term-id:<id>`
- `alias:<text>`
- `bounded-context:<id>`
- `system:<id>`
- `runtime-component:<id>`
- `deployment:<name>`
- `service:<name>`
- `application:<name>`
- `container:<name>`
- `artifact:<name>`
- `repository:<id-or-name>`
- `module:<repo-id>/<module-id>`
- `package:<prefix>`
- `class:<name>`
- `interface:<name>`
- `enum:<name>`
- `method:<name>`
- `field:<name>`
- `dto:<name>`
- `endpoint:<path-or-prefix>`
- `host:<host-or-host-pattern>`
- `queue:<name>`
- `topic:<name>`
- `exchange:<name>`
- `routing-key:<key>`
- `event:<name>`
- `event-schema:<name>`
- `schema:<name>`
- `table:<name>`
- `column:<name>`
- `entity:<name>`
- `repository-class:<name>`
- `config:<key>`
- `job:<name>`
- `metric:<name>`
- `span:<name>`
- `marker:<log-marker>`
- `error:<exception-or-error-marker>`
- `doc:<section-or-document>`

Bucket guidance:

- `Exact`: explicit term IDs, canonical event names, exact error markers, exact table/entity names, exact class names or exact contract names that almost always identify this term.
- `Strong`: endpoint prefixes, package prefixes, controller/listener/client names, topic/queue/routing keys, service names, repository/module names and database object groups that strongly suggest this term.
- `Medium`: method names, DTO names, metric/span names, field names, file path patterns, config keys and partial labels that need confirmation from surrounding evidence.
- `Weak`: aliases, loose user labels, common words, short acronyms and operator shorthand that should help retrieval but must not decide mapping alone.

Rules:

- Prefer stable prefixes or canonical names.
- Do not put prose into signal lists.
- Do not include secrets, tokens, credentials, sample customer data, personal data or full payloads.
- For endpoint paths with `{id}` placeholders, keep them inside backticks.
- Do not put weak generic words into `Exact` or `Strong`.
- If no concrete signal is known but the term is semantically important, keep the term only with honest low/medium evidence, `Source coverage: unknown|partial`, and a limitation or durable gap.

### `Canonical references`

List graph references using explicit prefixes:

- `bounded-context:<id>`
- `system:<id>`
- `runtime-component:<id>`
- `repository:<id>`
- `module:<repo-id>/<module-id>`
- `process:<id>`
- `integration:<id>`
- `team:<id>`
- `term:<id>`
- `external-system:<id>`
- `database:<schema-or-db-id>`
- `handoff-rule:<id>`

Rules:

- Prefer references to stable catalog IDs from `FULL OPERATIONAL CONTEXT`, `CURRENT FILE` or `NEW FACTS`.
- Do not invent IDs only to fill the field.
- If a reference is likely but not confirmed, keep it out of final references and record it in `BUILD MEMORY` when possible.
- A term should have at least one canonical reference unless it is intentionally a vocabulary-only term or a durable gap explains why the reference is missing.
- Use `team:<id>` only as a graph reference or responsibility navigation hint; do not encode ownership in the glossary.

### `Related terms`

List semantically related terms that are useful for navigation or impact analysis but are not necessarily confusing.

Examples:

- `customer-segment`
- `customer-profile-updated`
- `sales-opportunity`

Use term IDs, not prose labels.
Use `- None` when no related terms are confirmed.

### `Not to confuse with`

Use this section aggressively when a term can be confused across contexts.

Use this format:

```md
- `other-term-id` — Short reason.
```

This field is especially important for:

- acronyms;
- overloaded words such as account, case, order, ticket, segment, lead, customer, user, profile, policy, job, event;
- shared library terms used by multiple runtime components;
- external system labels that resemble internal context names;
- similarly named events, statuses, DTOs or tables;
- terms that differ between product, operator, code and database language.

Use `- None` when there is nothing confirmed to disambiguate.

### `Responsibility hints`

The glossary does not encode ownership. Do not add `owner`, `ownerTeamId`, `primaryOwner`, `routingTeam` or team assignment fields to term entries.

Use this section only to guide a reader or tool toward canonical entities where responsibility is modeled.

Good:

```md
- Responsibility is resolved through `bounded-context:lead-management` and `team:sales-platform-team`, not directly in glossary.
```

Bad:

```md
- Owner: Sales Platform Team.
```

Rules:

- Do not collapse multi-team responsibility into glossary entries.
- Do not infer accountability from package names, repository names, comments, author names, directory names or the fact that a team participates in a workflow.
- If responsibility is ambiguous, do not solve it in `glossary.md`. Add a durable gap only if the ambiguity affects term meaning, graph mapping, routing/coordination or LLM answers.

### `LLM tool hints`

This section tells an LLM how to use the term when answering questions or constructing tool/search scope.

Good hints:

- explain in local platform language;
- mention important disambiguation;
- avoid generic industry meaning;
- map classes, events, endpoints and tables to canonical graph references;
- when evidence contains a marker, suggest the referenced system, integration, process or bounded context;
- use exact/strong match signals before weak aliases;
- describe affected functions in user-facing language, not as a code walkthrough.

Keep hints short and operational.
Use `- None` only when there is no useful guidance beyond the term definition.

### `Evidence`

List compact evidence items proving why the term exists and why the definition is local.

Use this exact shape:

```md
- `source-type:source` — `signal-type:signal` — confidence: `high|medium|low`
```

Allowed source types:

- `code:<path>`
- `config:<path>`
- `documentation:<path-or-section>`
- `runtime:<signal-source>`
- `database:<schema-or-table>`
- `current-catalog:<file-or-entry>`
- `build-memory:<id>`
- `human:<note-or-reference>`

Rules:

- Include at least one evidence item for every term.
- Prefer file paths, package names, class names, config keys, endpoint names, table names, event names and documentation section names.
- Do not include long code snippets.
- Do not copy customer data, personal data, tokens, secrets or raw payloads.
- Evidence from `BUILD MEMORY` is not final truth. Promote it only when concrete evidence or human confirmation supports it.
- Do not fabricate exact paths. Approximate source references are acceptable only when clearly honest, e.g. `documentation:CRM lifecycle overview`.

Confidence guidance:

- `high`: explicit code, config, docs, runtime, database or human-confirmed evidence supports the term and local meaning.
- `medium`: meaning is supported but incomplete, inferred from multiple signals or split across repositories.
- `low`: observed label with limited context; keep definition narrow and add limitations or a durable gap when needed.

### `Source coverage`

Use one allowed value:

- `complete`
- `partial`
- `unknown`

Rules:

- Use `complete` only when expected sources for this term were scanned or evidence is clearly sufficient.
- Use `partial` when the term is confirmed but additional repositories, docs, downstream consumers or ownership sources may refine references or signals.
- Use `unknown` when the term is known from one weak source and needs more grounding.
- Do not lower coverage only because a current partial scan did not observe existing confirmed facts.

### `Limitations`

List concise caveats for the term.

Use `- None` when there are no known limitations.

Rules:

- Use this field for durable or currently relevant limitations about interpretation, evidence, source coverage or references.
- Do not use this field for temporary scan-order questions that may be resolved by scanning another known repository. Put those in `BUILD MEMORY` when possible.
- Do not turn limitations into long open-ended questions. Use `## Gaps` for durable unresolved catalog issues.

## Required gap entry schema

Final durable gaps must use this structure and field order:

```md
### `stable-gap-id`

**Type:** `semantic-ambiguity`

**Status:** `open`

**Severity:** `medium`

**Affected nodes**

- `term:example-term`
- `bounded-context:example-context`

**Description:** One or two concise sentences.

**Impact**

- `function-description`
- `qa`

**Evidence checked**

- `repository:crm-api-repo`
- `documentation:crm-domain-handbook`

**Suggested evidence sources**

- ownership matrix
- domain handbook
- CODEOWNERS
```

Every gap must include all fields from the schema above.
Use `- None` for empty list fields.

### Gap `Type`

Use exactly one allowed value:

- `semantic-ambiguity`: local meaning remains unclear.
- `acronym-ambiguity`: acronym has multiple possible expansions or meanings.
- `conflicting-definition`: sources define the same term differently.
- `missing-canonical-reference`: term is confirmed but the canonical graph node is missing or unresolved after expected sources were checked.
- `unresolved-term-split`: evidence suggests one term should split into scoped terms, but the split is not confirmed.
- `unresolved-term-merge`: evidence suggests terms may be aliases, but merge is not confirmed.
- `insufficient-evidence`: available evidence is too weak for a reliable final term.
- `cross-file-reference-gap`: related catalog file is expected to contain the referenced node but does not or was confirmed incomplete.
- `human-input-required`: domain or operator decision is needed.

### Gap `Status`

Use exactly one allowed value:

- `open`
- `resolved`
- `superseded`

Resolve or supersede old gaps when new evidence clearly closes or replaces them.

### Gap `Severity`

Use exactly one allowed value:

- `high`
- `medium`
- `low`

Use `high` only when the gap can materially mislead deterministic mapping, code search, function description, impact analysis, DB grounding, incident analysis or routing/coordination.

### Gap `Impact`

Use allowed `Use for` values:

- `deterministic-mapping`
- `runtime-to-domain-mapping`
- `runtime-to-repository-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `integration-dependency-analysis`
- `db-grounding`
- `incident-analysis`
- `repository-onboarding`
- `cross-context-disambiguation`
- `qa`

### `Evidence checked`

List sources already checked before the gap was declared durable.

Use source references such as:

- `repository:<id-or-name>`
- `module:<repo-id>/<module-id>`
- `documentation:<path-or-section>`
- `runtime:<source>`
- `database:<schema-or-table>`
- `current-catalog:<file>`
- `human:<note-or-reference>`

If no source has been checked and the question is only temporary, do not create a final gap.

### `Suggested evidence sources`

List sources likely to resolve the gap:

- domain handbook;
- product documentation;
- support/operator notes;
- CODEOWNERS or team matrix;
- bounded context catalog;
- integration catalog;
- process catalog;
- repository or module source;
- generated clients;
- database schema docs;
- runtime logs/traces/metrics;
- human domain expert.

## Discovery procedure

Perform this reasoning internally before producing the final Markdown.

### 1. Parse and normalize current content

- Parse the current glossary structure if present.
- Identify existing term IDs, aliases, references, gaps and legacy open questions.
- Move useful legacy facts into schema fields.
- Convert durable legacy `Open Questions` into typed `## Gaps` entries.
- Do not preserve legacy grouping headings, arbitrary fields, long essays, duplicate `Open Questions` sections or unstructured term lists.

### 2. Discover candidate terms

Look for glossary terms in:

- README and domain documentation;
- package/module names containing domain language;
- aggregates, entities and value objects;
- controller names and endpoint groups;
- DTO, event and schema names;
- queues, topics, exchanges and routing keys;
- OpenAPI/AsyncAPI specs;
- generated clients and shared libraries;
- service, application, container and deployment names;
- database schemas, tables, columns and entity groups;
- process names and workflow definitions;
- enum/status names that appear in workflows or user-visible states;
- exception/error names and log markers;
- metric names, span names and recurring observability labels;
- tests that describe local business rules;
- recurring labels in logs, traces, support tickets and operators' handoff notes.

### 3. Add or update a term when at least one is true

- It has a local meaning that differs from generic meaning.
- It appears in logs, traces, spans, stack traces, events, queues, topics, endpoints, database objects, config keys or class names.
- It is used by operators, users or teams during handoff, analysis or support.
- It maps an acronym, synonym, UI label or code label to a canonical concept.
- It helps route from evidence to a bounded context, system, runtime component, process, integration, repository, module, table or team responsibility area.
- It helps describe an affected function in non-code language.
- It supports impact analysis, DB/code grounding, code search or repository onboarding.
- It prevents confusion between similar local concepts.

### 4. Reject false positives

Do not add a term when:

- it is a generic word with no local meaning;
- it is only one arbitrary class, method, variable or DTO with no semantic value;
- it is a generic framework or infrastructure term already widely understood;
- it is a complete enum/list/taxonomy dump that should live in domain documentation;
- it is a full integration contract better represented in `integrations.yml`;
- it is a full process description better represented in `processes.yml`;
- it is a code/module map better represented in `repo-map.yml`;
- it is a responsibility or routing rule better represented in `teams.yml`, `handoff-rules.md` or an incident-specific view.

### 5. Classify candidate value

For every retained fact, classify it mentally as one or more:

- `vocabulary`: explains local language;
- `recognition`: helps deterministic matching;
- `navigation`: helps target graph nodes, repositories, modules, integrations or tables;
- `disambiguation`: prevents confusing neighboring terms;
- `analysis`: helps impact analysis, function description, DB grounding, incident analysis or Q&A;
- `coordination`: helps find where responsibility is modeled without encoding ownership in the glossary.

Do not retain facts that support none of these categories.

### 6. Merge

Merge by stable term ID and local meaning.

- Append new aliases, match signals, references, related terms, disambiguation links, evidence and LLM hints.
- Deduplicate list values while preserving stable order.
- Prefer exact/strong signals over weak aliases.
- Prefer stronger direct evidence over weaker inferred evidence.
- Keep definitions concise and move concrete facts into structured fields.
- Preserve existing confirmed facts that are not visible in the current source.

## Multi-repository and build-memory rules

### Current scan is partial

The current repository, generated client, library, documentation fragment, module or path range is only one evidence slice.

Do not infer global absence from local absence.
Do not delete, narrow, weaken, downgrade or overwrite existing confirmed glossary facts only because they are not visible in the current scan.

Do not remove or weaken:

- existing terms;
- aliases;
- match signals;
- canonical references;
- related terms;
- not-to-confuse links;
- responsibility hints;
- LLM tool hints;
- evidence;
- source coverage limitations;
- durable gaps;

only because they were not found in the current repository.

### Use build memory for temporary uncertainty

If `BUILD MEMORY` is provided, read it before editing.

Use it to understand:

- repositories already scanned;
- repositories expected but not scanned yet;
- candidate terms;
- pending cross-repo joins;
- unresolved references;
- temporary questions caused by scan order;
- facts that require human confirmation.

`BUILD MEMORY` is not final operational truth.
Promote build-memory facts to `glossary.md` only when concrete evidence or human confirmation supports them.
Do not output build memory inside `glossary.md`.

### Temporary vs final uncertainty

Do not add final gaps for questions that may be resolved by scanning another known repository or expected documentation source.

Temporary uncertainty examples:

- a shared library exposes `CustomerSegmentChanged`, but the consuming service repository has not been scanned yet;
- a generated client contains `PartnerContactDto`, but the owning integration catalog entry is not yet known;
- a term appears in tests but the domain documentation has not been scanned yet;
- a repository lacks team ownership but responsibility is expected in `teams.yml`.

Durable gap examples:

- two scanned sources define the same acronym differently;
- a term is used by operators but no canonical context can be confirmed after expected sources were checked;
- a business/domain decision is required to split or merge two terms;
- all expected sources were scanned and the canonical reference remains unknown;
- the term meaning directly affects mapping, function description, impact analysis or DB grounding and cannot be resolved from available evidence.

## Cross-file consistency rules

Before returning the file, verify:

- every `bounded-context:<id>` reference is expected to exist in `bounded-contexts.yml`;
- every `system:<id>` reference is expected to exist in `systems.yml`;
- every `runtime-component:<id>` reference is expected to exist in the system/runtime component catalog;
- every `repository:<id>` reference is expected to exist in `repo-map.yml` or the repository catalog;
- every `module:<repo-id>/<module-id>` reference is expected to exist in the repository/module catalog;
- every `process:<id>` reference is expected to exist in `processes.yml`;
- every `integration:<id>` reference is expected to exist in `integrations.yml`;
- every `team:<id>` reference is expected to exist in `teams.yml`;
- every `term:<id>` reference points to another glossary term or is intentionally introduced in the same update;
- every `external-system:<id>` reference is expected to exist in the system/integration catalog;
- every `handoff-rule:<id>` reference is expected to exist in `handoff-rules.md`.

If a reference is missing because another repository or file has not been scanned yet, record it in `BUILD MEMORY` when possible. Do not add a final gap unless the issue is durable.

## Merge and update policy

### Normalization

This schema is authoritative.

Do not preserve legacy patterns such as:

- arbitrary domain grouping headings;
- unstructured bullets;
- long domain essays;
- duplicate `## Open Questions` sections;
- fields not defined in this prompt;
- raw product taxonomies;
- full implementation walkthroughs;
- legacy unweighted evidence signals;
- direct owner fields.

Compress useful legacy content into the new term fields.
Move durable unresolved issues into `## Gaps`.
Do not carry temporary scan-order questions into final gaps.

### Monotonic merge

Updates are additive by default.

Allowed:

- add a new term with confirmed local meaning;
- add an alias to an existing term;
- add match signals;
- add canonical references;
- add related terms and not-to-confuse links;
- add responsibility hints and LLM tool hints;
- add evidence;
- raise confidence when stronger evidence is found;
- improve source coverage when expected sources were checked;
- split an ambiguous term into scoped terms when evidence proves different meanings;
- mark a term deprecated or retired when explicit evidence confirms it;
- resolve or supersede gaps when new evidence clearly closes them.

Forbidden without explicit contradictory evidence:

- deleting existing terms;
- replacing a rich entry with a shorter one;
- removing aliases or match signals;
- removing canonical references;
- removing disambiguation links;
- lowering evidence confidence only because the current source is incomplete;
- changing a local definition into a generic definition;
- collapsing two distinct scoped meanings into one term;
- converting participant/team mentions into ownership facts;
- promoting temporary scan-order uncertainty into final gaps.

If a term appears to conflict with new evidence, prefer one of these safe actions:

1. add a `Not to confuse with` relation;
2. split into two scoped term IDs;
3. add a concise limitation;
4. add a durable gap if the conflict cannot be resolved from available evidence.

### Term split and merge rules

Merge entries when:

- aliases clearly point to the same local concept;
- evidence maps to the same bounded context, process, integration or system;
- differences are only spelling, casing, pluralization or common shorthand;
- the same event/code label and same local meaning appear in multiple repositories.

Split entries when:

- the same label has different local meanings in different contexts;
- an acronym has different expansions;
- a term refers to both an internal concept and an external system;
- a status/event/table/class name is reused with different semantics;
- one team-local usage conflicts with a canonical product meaning.

When splitting, use context-qualified IDs and keep cross-links in `Not to confuse with`.

### Term normalization and deduplication

Before adding a term, check whether it is already represented by:

- existing term ID;
- alias;
- canonical reference;
- exact or strong match signal;
- related term;
- not-to-confuse link.

Normalize only when safe:

- lowercase kebab-case IDs;
- preserve human-facing capitalization in `Term`;
- keep code/event/table names exactly as they appear in evidence;
- deduplicate aliases and signals without changing their meaning;
- keep old aliases when renaming a display label.

## Security and privacy rules

Never include:

- credentials;
- tokens;
- secrets;
- API keys;
- passwords;
- private certificates;
- personal customer data;
- personal employee data;
- full business payloads;
- sample records with real identifiers;
- confidential message bodies not needed for mapping.

Allowed when operationally useful:

- endpoint patterns;
- service/application/container names;
- queue/topic/exchange/routing-key names;
- class/interface/enum names;
- package prefixes;
- table/schema/column names;
- sanitized log markers;
- sanitized error names;
- metric/span names;
- config key names without values;
- host patterns when they are operational signals and not secrets.

Mask sensitive fragments if needed.

## Markdown formatting rules

- Return valid Markdown only.
- Preserve the top-level order exactly.
- Every term must be a heading of the form `### <term-id-in-backticks>` under `## Terms`.
- Every gap must be a heading of the form `### <gap-id-in-backticks>` under `## Gaps`.
- Use the exact field labels from this prompt.
- Use backticks for IDs, match signal values, graph references and status/use-case/category values.
- Use `- None` for empty lists.
- Keep definitions concise.
- Avoid tables; structured bullet lists are easier to maintain and parse.
- Do not use Markdown fences in the returned `glossary.md` content.
- Do not output a diff.
- Keep one blank line between sections.
- Do not create duplicate headings.

## Quality checklist before final output

Before returning the final `glossary.md`, verify:

1. The file starts with exactly one `# Glossary` heading.
2. `Schema version` is `1` and `Catalog kind` is `operational-context-glossary`.
3. There is exactly one `## Terms` section.
4. There is exactly one `## Gaps` section.
5. There is no legacy `## Open Questions` section.
6. Every term heading has a stable kebab-case ID in backticks.
7. Every term has all required fields in the required order.
8. Every category is one of the allowed category values.
9. Every lifecycle status is one of the allowed lifecycle values.
10. Every term has at least one `Use for` value.
11. Every term has all four `Match signals` buckets.
12. Every term has at least one evidence item.
13. Every term has at least one canonical reference or a durable reason why not.
14. Related terms and not-to-confuse IDs exist or are introduced in the same file.
15. Canonical references use allowed graph-reference prefixes.
16. Gaps are durable catalog issues, not repo-local scratchpad notes.
17. Temporary scan-order uncertainty was not promoted to final gaps.
18. No direct ownership fields are present in term entries.
19. No secrets, credentials, personal data or full payloads are included.
20. The update is monotonic unless explicit contradictory evidence supports a change.

## Output rules

Return the full updated `glossary.md` content only.
Do not include commentary, explanations, Markdown fences or diff format.
Do not include `BUILD MEMORY` unless the parent prompt explicitly requests it as a separate sidecar output.

## Example of a correctly filled result

The example is generic CRM-oriented and intentionally not tied to any regulated or banking domain.

```md
# Glossary

**Schema version:** `1`
**Catalog kind:** `operational-context-glossary`

## Terms

### `customer-profile`

**Term:** Customer Profile

**Category:** `business-term`

**Lifecycle status:** `active`

**Definition:** Canonical CRM record that stores customer identity, contact details, preferences and segmentation attributes.

**Local meaning and boundaries**

- In this CRM platform, customer profile is the operational source for customer-facing data used by onboarding, sales and support.
- It is not the same as an authentication user account.
- It may be referenced by sales and support objects, but it does not own their lifecycle.

**Aliases**

- customer record
- customer master
- CRM profile
- `CustomerProfile`

**Use for**

- `deterministic-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `db-grounding`
- `incident-analysis`
- `qa`

**Match signals**

Exact:

- `term-id:customer-profile`
- `event:CustomerProfileUpdated`
- `entity:CustomerProfileEntity`
- `table:crm_customer_profile`

Strong:

- `bounded-context:customer-profile`
- `service:crm-api`
- `runtime-component:crm-api-runtime`
- `package:com.example.crm.customer`
- `endpoint:/api/customer-profiles`
- `endpoint:/api/customers/{customerId}`
- `class:CustomerProfileController`
- `topic:crm.customer.profile-events`
- `marker:CUSTOMER_PROFILE`

Medium:

- `method:updateCustomerProfile`
- `metric:crm.customer.profile.update.count`
- `span:CustomerProfileService.updateProfile`

Weak:

- `alias:customer profile`
- `alias:customer record`
- `alias:CRM profile`

**Canonical references**

- `bounded-context:customer-profile`
- `system:crm-api`
- `runtime-component:crm-api-runtime`
- `repository:crm-api-repo`
- `module:crm-api-repo/customer-profile-module`
- `process:customer-profile-update`

**Related terms**

- `customer-segment`
- `communication-preference`
- `customer-profile-updated`

**Not to confuse with**

- `user-account` — Authentication account used for login and access control, not CRM customer data.
- `sales-opportunity` — Sales pipeline object that may reference a customer profile but has its own lifecycle.

**Responsibility hints**

- Responsibility is resolved through `bounded-context:customer-profile`, `system:crm-api` and the referenced repository/module records.

**LLM tool hints**

- Explain this as the CRM customer data concept, not as a login account.
- When evidence contains `CustomerProfileUpdated` or `crm_customer_profile`, map it to `bounded-context:customer-profile`.
- Prefer customer-profile repositories and modules before shared CRM utilities when constructing code search scope.

**Evidence**

- `code:src/main/java/com/example/crm/customer/CustomerProfileController.java` — `endpoint:/api/customer-profiles` — confidence: `high`
- `code:src/main/java/com/example/crm/customer/CustomerProfileEntity.java` — `entity:CustomerProfileEntity` — confidence: `high`
- `config:src/main/resources/application.yml` — `topic:crm.customer.profile-events` — confidence: `high`
- `documentation:docs/domain/customer-profile.md` — `bounded-context:customer-profile` — confidence: `high`

**Source coverage:** `partial`

**Limitations**

- Shared customer value objects are referenced but the shared CRM domain library has not been fully scanned in this build cycle.

### `account-crm-company`

**Term:** CRM Account / Company Account

**Category:** `ambiguous-term`

**Lifecycle status:** `active`

**Definition:** CRM-facing account label used by sales and support users when referring to a customer organization or customer record.

**Local meaning and boundaries**

- In CRM conversations, `account` usually means a customer company/account record connected to customer profile.
- It does not mean an authentication login account.
- It does not mean a billing or financial account unless evidence explicitly points to billing.

**Aliases**

- account
- customer account
- CRM account
- company account

**Use for**

- `deterministic-mapping`
- `function-description`
- `impact-analysis`
- `incident-analysis`
- `cross-context-disambiguation`
- `qa`

**Match signals**

Exact:

- `entity:CustomerAccountEntity`
- `table:crm_customer_account`

Strong:

- `endpoint:/api/accounts`
- `class:CustomerAccountController`
- `doc:docs/domain/customer-profile.md#account-language`

Medium:

- `field:accountId`
- `dto:CustomerAccountDto`

Weak:

- `alias:account`
- `alias:customer account`

**Canonical references**

- `bounded-context:customer-profile`
- `system:crm-api`
- `repository:crm-api-repo`
- `module:crm-api-repo/customer-profile-module`
- `term:customer-profile`

**Related terms**

- `customer-profile`
- `customer-segment`

**Not to confuse with**

- `user-account` — Login identity owned by the identity context.
- `billing-account` — Billing account owned by the billing context.

**Responsibility hints**

- Responsibility is resolved through `bounded-context:customer-profile`; do not infer ownership from the word `account` alone.

**LLM tool hints**

- When a user says `account`, inspect surrounding evidence before choosing CRM, identity or billing meaning.
- Treat weak alias `account` as insufficient for deterministic mapping without exact or strong signals.

**Evidence**

- `code:src/main/java/com/example/crm/customer/CustomerAccountEntity.java` — `entity:CustomerAccountEntity` — confidence: `high`
- `documentation:docs/domain/customer-profile.md#account-language` — `alias:account` — confidence: `high`

**Source coverage:** `complete`

**Limitations**

- None

### `partner-api-timeout`

**Term:** Partner API Timeout

**Category:** `error-term`

**Lifecycle status:** `active`

**Definition:** Timeout symptom observed when the CRM API cannot complete a synchronous request to an external partner service within the configured deadline.

**Local meaning and boundaries**

- This term is used for partner integration symptoms, not generic internal service latency.
- It should point analysis toward the integration contract, host, endpoint, client class and retry behavior.
- It does not prove partner ownership without integration or runtime evidence.

**Aliases**

- partner timeout
- external API timeout
- partner read timeout

**Use for**

- `deterministic-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `integration-dependency-analysis`
- `incident-analysis`
- `qa`

**Match signals**

Exact:

- `error:PartnerClientTimeoutException`
- `span:PartnerContactClient.getContact`

Strong:

- `error:Read timed out`
- `class:PartnerContactClient`
- `endpoint:/partner/contacts`
- `integration:crm-api-to-partner-contact-api`

Medium:

- `host:api.partner.example`
- `config:partner.contact.timeout`

Weak:

- `alias:partner timeout`
- `alias:external API timeout`

**Canonical references**

- `integration:crm-api-to-partner-contact-api`
- `external-system:partner-contact-api`
- `system:crm-api`
- `repository:crm-api-repo`
- `module:crm-api-repo/partner-integration-module`

**Related terms**

- `partner-contact`
- `customer-profile`

**Not to confuse with**

- `crm-api-latency` — Internal CRM API latency without evidence of a partner call.

**Responsibility hints**

- Responsibility depends on the integration and external-system records; do not assign ownership from the timeout phrase alone.

**LLM tool hints**

- Use this term to ground analysis in the partner integration path before describing the affected user-facing function.
- Search client classes, endpoint config and integration records before treating it as generic CRM latency.

**Evidence**

- `code:src/main/java/com/example/crm/integration/PartnerContactClient.java` — `class:PartnerContactClient` — confidence: `high`
- `config:src/main/resources/application.yml` — `config:partner.contact.timeout` — confidence: `high`
- `runtime:trace-span` — `span:PartnerContactClient.getContact` — confidence: `medium`

**Source coverage:** `partial`

**Limitations**

- External partner ownership details are not part of the scanned CRM repository.

## Gaps

### `gap-support-ticket-local-meaning`

**Type:** `semantic-ambiguity`

**Status:** `open`

**Severity:** `medium`

**Affected nodes**

- `term:support-ticket`
- `bounded-context:support-case`

**Description:** The scanned support API uses both `case` and `ticket`, but available evidence does not confirm whether they are exact synonyms or represent different support workflow stages.

**Impact**

- `function-description`
- `impact-analysis`
- `incident-analysis`
- `qa`

**Evidence checked**

- `repository:support-api-repo`
- `documentation:docs/domain/support-overview.md`

**Suggested evidence sources**

- support domain handbook
- support API workflow tests
- support team terminology notes
```
