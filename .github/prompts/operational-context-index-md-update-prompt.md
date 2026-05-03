# operational-context-index.md update prompt

Update only `operational-context-index.md`.

This prompt is contract-authoritative for `operational-context-index.md`. If a parent operational-context builder prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative output contract, merge policy, quality gate and document model for `operational-context-index.md`.

Do not preserve legacy README-style, incident-only, ownership-only, `Open Questions`-based or schema-dump content unless it is explicitly represented in the version-1 model below.

## Purpose

Maintain `operational-context-index.md` as the enterprise-grade contract and orientation document for a reusable operational context catalog.

`operational-context-index.md` is not a data dump, not a full architecture document, not a CMDB, not a runbook, and not only an incident-routing description. It is the compact operating guide that explains what the catalog is, which file owns which slice of the graph, how the files relate to each other, and how humans, builder agents, validators, deterministic mapping code, evidence providers, operational-context adapters and LLM tools should use the catalog.

The index must make clear that the operational context catalog supports multiple downstream analysis capabilities, including:

- deterministic mapping from runtime, deployment, log, telemetry, repository, documentation, data, integration and domain evidence to operational graph nodes;
- GitLab/code-search scope construction across main repositories, shared libraries, generated clients, integration libraries, schema repositories and deployment/config repositories;
- function description and user-facing explanation;
- impact analysis and change-risk analysis;
- DB/code grounding before database discovery;
- integration dependency analysis;
- process and bounded-context understanding;
- local vocabulary explanation and disambiguation;
- incident analysis, triage and handoff as one downstream view;
- QA, onboarding, operational Q&A and future AI analysis features;
- focused query-based access by an operational-context adapter or LLM tool.

Incident analysis is the first consumer, not the catalog's only purpose.

## Core principles

### 1. The index describes an operational graph

Treat `operational-context-index.md` as the contract-level overview of an evidence-backed operational graph.

The index should explain the graph and how to navigate it. It must not duplicate detailed schemas from individual YAML/Markdown files.

The graph connects:

- logical systems;
- deployed runtime components;
- repositories, modules, source layouts and code-search scopes;
- generated clients, shared libraries, integration libraries and schema/config repositories;
- business, operational and technical processes and process steps;
- integrations and operational contracts;
- bounded contexts and semantic boundaries;
- glossary terms, aliases, acronyms and local vocabulary;
- teams, external parties and scoped responsibility relations;
- deterministic match signals and recognition signals;
- routing/handoff overlays;
- durable catalog gaps;
- use-case views such as code search, function explanation, impact analysis, DB/code grounding, incident triage, QA and operational Q&A.

Do not describe the catalog as a simple ownership table. A single runtime component, repository, module, bounded context, process or integration may have several responsible teams, no single owner, or different teams per scope, module, side of a contract, data area, process step or operational task.

### 2. The index is a guide, not the data itself

The index must orient people and tools. It should not contain detailed catalog facts that belong in `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` or `handoff-rules.md`.

Use the index to explain:

- the purpose of the catalog;
- the mental model of the operational graph;
- the responsibility of each catalog file;
- how evidence is matched to graph nodes;
- how code-search scope is constructed;
- how responsibilities differ from ownership and routing;
- how durable `gaps` differ from temporary `BUILD MEMORY` uncertainty;
- how an operational-context adapter or LLM tool should query the catalog;
- how a human should navigate the catalog during analysis;
- which quality gates every update must preserve.

Do not use the index for:

- exhaustive endpoint lists;
- exhaustive queue/topic/class/package/table lists;
- full schemas of every catalog file;
- long architecture essays;
- incident-only routing tables;
- support rotas or personal contact details;
- individual ticket, run, trace or correlation identifiers;
- generated build-state details;
- temporary pending joins from a single scan;
- unverifiable assumptions;
- secrets, tokens, credentials, private contact values, sample production records or sensitive environment-specific values.

### 3. File-specific prompts own schemas; the index owns shared principles

File-specific prompts govern exact schema details, allowed values, extraction rules and file-level output contracts.

This index governs shared principles:

- what the catalog is for;
- how files compose into one graph;
- how deterministic matching works at a high level;
- how code-search scope should be interpreted;
- how responsibilities, routing and gaps are distinguished;
- how repository-specific discovery should merge safely;
- how tools and LLM agents should consume focused graph slices.

If file-specific prompts evolve, update the index only at the level of file responsibilities, shared rules and navigation. Do not paste full updated schemas into the index.

### 4. Deterministic signals must be explicit

The index must state that operational-context files should preserve stable, queryable match keys. These signals must not be hidden only in prose.

Important signals include:

- service, application, deployment, container, namespace, image, artifact and process names;
- GitLab project paths, repository ids, repository names and module paths;
- Maven/Gradle coordinates and internal dependency coordinates;
- package prefixes and class/interface/enum/annotation names;
- controller, client, listener, publisher, repository, entity, generated client and config class names;
- endpoint prefixes, endpoint templates, HTTP methods, hosts, base URL properties, gateway routes and service discovery names;
- queues, exchanges, topics, routing keys, bindings, consumer groups, DLQs and channel names;
- event names, command names, schema names, OpenAPI/AsyncAPI/WSDL/GraphQL operation names;
- scheduler, job, lock, workflow, BPMN and state-machine identifiers;
- datasource names, Hikari pool names, DB schemas, tables, entities, repositories and migration paths;
- file shares, object storage buckets, path prefixes and transfer markers;
- configuration property prefixes, feature flags and runtime toggle names;
- log markers, exception classes, error codes, alert labels, metric names and trace/span names;
- local terms, acronyms, aliases and UI/operator labels.

The index should explain that exact or strong signals are required for high-confidence deterministic mapping. Weak or generic words such as `backend`, `service`, `timeout`, `failure`, `database`, `integration` or `queue` are not enough to assert system, responsibility or route unless combined with stronger evidence.

### 5. Query-based tool access is preferred

Runtime features and LLM agents should query an operational-context adapter or tool for focused graph slices instead of loading the entire catalog into every prompt by default.

Typical query results may include:

- matched graph candidates;
- matched deterministic signals and confidence;
- system/runtime component summaries;
- code-search projects, repositories, modules, package prefixes and class hints;
- related systems, repositories, processes, integrations, bounded contexts, teams and terms;
- responsibility and routing views when relevant;
- source coverage, limitations and durable gaps.

The index should not prescribe a single adapter implementation, but it should make the consumption model clear: focused query first, graph slice second, broad catalog load only when explicitly justified.

### 6. Multi-repository scans are partial by default

Repository-specific agents usually see only part of the platform. A source may be one service repo, shared library, generated client, deployment repository, schema repository, documentation fragment, module or branch.

The index must preserve these rules:

- Merge confirmed positive facts; do not regenerate global truth from one source.
- Preserve existing confirmed facts that are not visible in the current source.
- Treat absence in one repository as `not observed`, not global absence.
- Do not replace a global list with a local subset.
- Do not delete, null out, shorten or downgrade existing links only because the current scan does not contain them.
- If a fact is split across repositories, update only the confirmed side and keep unresolved joins in `BUILD MEMORY` or sidecar discovery output.
- Shared libraries and generated clients may be part of code-search scope without being runtime systems or business owners.
- Do not infer team ownership from package names, commit authors, branch names, current repository, module names or one-off mentions.
- Do not use final catalog gaps as scratchpad memory between repository agents.

The update process must be safe regardless of the order in which repository agents analyze repositories, libraries, generated clients, deployment/config sources or documentation fragments.

### 7. Durable gaps are not temporary build memory

Use `gaps` for durable catalog-level unresolved issues only.

A durable gap is a final catalog issue that affects at least one of:

- deterministic mapping;
- code-search scope;
- system/runtime recognition;
- process or context interpretation;
- integration dependency analysis;
- responsibility, collaboration or coordination;
- handoff behavior;
- DB/code grounding;
- LLM tool answers;
- cross-file validation.

Temporary discovery artifacts are different. They may contain:

- candidate facts;
- deferred cross-repo candidates;
- pending joins;
- repo-local absences;
- conflicts requiring reconciliation;
- suggested next sources;
- facts awaiting another repository or documentation source.

Temporary uncertainty belongs in `BUILD MEMORY`, discovery reports or sidecar outputs. It must not be promoted to final `gaps` while it can still be resolved by scanning another known source.

Do not use legacy `Open Questions` in the final index. If a current file uses `Open Questions`, migrate the concept to durable `gaps` wording unless the parent prompt explicitly requires legacy compatibility.

### 8. Routing is a derived view

Routing and handoff are use-case-specific coordination views over the core graph. They must not overwrite system facts, repository facts, integration facts, process facts, bounded-context facts, glossary definitions or team responsibility evidence.

`handoff-rules.md` should be described as an overlay that helps answer:

> Given this evidence, what should happen next operationally?

It must not be presented as the source of truth for ownership, topology, integrations, processes, bounded contexts, repository mapping or local vocabulary.

### 9. Responsibilities are relations, not forced owners

The index must explain that responsibility may be:

- explicit, shared, inferred, candidate, disputed, external, worker-only or unknown;
- scoped by system, runtime component, repository, module, bounded context, process, process step, integration, integration side, term, data area, platform capability or operational task;
- role-specific, such as runtime operator, repo maintainer, module steward, domain steward, integration contract steward, producer, consumer, platform support, data owner, security owner, QA owner, first responder, business owner, support contact, worker, contributor or external owner.

Do not ask the index or any catalog file to invent a single owner for every node.

## Non-goals

Do not turn `operational-context-index.md` into:

- a complete architecture document;
- a complete DDD document;
- a complete CMDB;
- a complete API, queue, topic, table, class or package inventory;
- a repository catalog;
- a data dictionary;
- a process catalog;
- an integration catalog;
- a team directory;
- an ownership matrix;
- a support rota;
- a historical changelog;
- a scratchpad for temporary agent uncertainty;
- a replacement for file-specific prompts;
- a central rule engine for diagnosis or routing.

Do not include project-specific systems, repositories, teams, domains or runtime behavior unless they are explicitly present in `CURRENT FILE`, `NEW FACTS` or related catalog files.

## Inputs

The agent may receive:

- `CURRENT FILE`: current `operational-context-index.md` content. It may be empty, legacy, incident-only, README-style or already version 1.
- `NEW FACTS`: new decisions, conventions, updated prompt rules, repository discovery results, validation findings, documentation fragments, architecture decisions or operator-provided context.
- `RELATED OPERATIONAL CONTEXT FILES`: current catalog files such as `systems.yml`, `repo-map.yml`, `integrations.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md`, `handoff-rules.md`, optional `terms.yml`, optional view files and optional catalog staging files.
- `PROMPT FILES`: update prompts for individual catalog files.
- `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate facts, unresolved references, sources not yet scanned and known local absences.
- `DISCOVERY REPORTS`: optional repository-specific or documentation-specific discovery outputs.
- `VALIDATION REPORTS`: optional cross-file validation or reconciliation findings.

Use only the provided evidence. Do not invent missing files, schemas, systems, repositories, teams, domains, processes, integrations, runtime components, tools or source behavior.

If inputs are incomplete, do the best possible update using available evidence and generic catalog rules. Preserve uncertainty in wording or durable `gaps` only when it is truly catalog-level and cannot be resolved from available source scope.

## Output

Return the full updated `operational-context-index.md` Markdown only.

Do not include explanations.
Do not include diffs.
Do not include partial snippets.
Do not include Markdown fences around the whole output.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `operational-context-index.md`.
Do not output `BUILD MEMORY` inside `operational-context-index.md`.
Do not output legacy `## Open Questions` unless the parent prompt explicitly requires legacy compatibility.

The final document must:

- start with `# Operational Context Index` unless `CURRENT FILE` uses a clearly intentional project-specific title;
- be concise but complete enough to orient a new human, builder agent, validator or LLM tool;
- explain the catalog as a reusable operational graph;
- describe each canonical catalog file by responsibility and downstream use;
- explain that incident routing/handoff is one derived view, not the core model;
- mention deterministic mapping and structured match signals;
- mention code-search scope across repositories, shared libraries, generated clients and config/schema repositories;
- mention query-based adapter/tool access;
- mention multi-repository, shared-library, generated-client and partial-source behavior;
- distinguish durable catalog `gaps` from temporary `BUILD MEMORY` uncertainty;
- explain responsibility relations without forcing a single owner;
- avoid detailed catalog data that belongs in a specific catalog file;
- avoid secrets, credentials, personal data, raw production payloads and sensitive environment-specific values.

## Target document structure

Prefer this structure for the updated `operational-context-index.md`:

```md
# Operational Context Index

## Purpose

## What this catalog is used for

## Catalog model

## File map

## How the catalog is built

## How the catalog is used

## Deterministic mapping and code-search scope

## Responsibilities, routing, and gaps

## Multi-repository and partial-source safety

## Operational context tool usage

## Navigation guide

## Quality gates

## Maintenance rules

## Gaps
```

`## Gaps` is optional. Include it only when there are durable index-level or catalog-contract-level unresolved issues. Do not include it merely as an empty placeholder unless the project convention requires every Markdown catalog file to contain a `Gaps` section.

You may omit or merge sections if `CURRENT FILE` explicitly uses a shorter intentional format, but the resulting document must still cover purpose, use cases, graph model, file responsibilities, build/update rules, deterministic mapping, responsibility/routing/gap semantics, tool access, navigation and maintenance rules.

Do not use `## Open Questions` in the final document. If legacy content has `Open Questions`, migrate durable catalog-level items into `## Gaps` using concise wording.

## Section guidance

### `## Purpose`

State that the directory stores a curated, evidence-backed operational context catalog used by multiple analysis capabilities.

The purpose should mention that the catalog helps:

- identify affected logical systems and deployed runtime components;
- map evidence to repositories, modules, packages, classes, endpoints, queues, topics, schemas, tables and local terms;
- build GitLab/code-search scope;
- understand processes, integrations, bounded contexts and vocabulary;
- explain affected functionality in user-facing language;
- support impact analysis, DB/code grounding, QA, incident triage and future AI analysis features.

Do not state that the catalog exists only for incident enrichment or handoff to an owner.

### `## What this catalog is used for`

List downstream use cases compactly. Include at least:

- deterministic evidence-to-graph mapping;
- code-search scoping;
- function description and user-facing explanation;
- impact and change analysis;
- DB/code grounding;
- process and integration dependency analysis;
- vocabulary and bounded-context disambiguation;
- responsibility and coordination analysis;
- incident analysis and handoff as a downstream view;
- QA, onboarding, documentation analysis and future AI features;
- query-based adapter/tool access.

Keep this section generic. Do not add domain-specific examples unless they are already in the target catalog.

### `## Catalog model`

Describe the catalog as a compact operational graph, not prose documentation and not an ownership table.

Use wording similar to:

```text
The catalog is a compact operational graph. It stores curated nodes, edges, deterministic recognition signals, responsibilities, source coverage and durable gaps that can be queried by evidence providers, tools, builder agents and future analysis features.
```

Mention the main graph node and edge categories:

- systems and runtime components;
- repositories, modules and code-search scopes;
- shared libraries, generated clients, integration libraries and config/schema repositories;
- processes and process steps;
- integrations and operational contracts;
- bounded contexts and local vocabulary;
- teams, external parties and responsibility relations;
- handoff/routing overlays;
- match signals, evidence, source coverage and gaps.

### `## File map`

Describe each canonical file exactly once. Keep descriptions short but specific.

Use this v1 meaning unless `NEW FACTS` explicitly define a different current catalog:

| File | Primary responsibility | Typical questions |
| --- | --- | --- |
| `systems.yml` | Logical systems and deployed runtime components, recognition signals, dependencies, runtime behavior, system-level responsibilities, runtime-to-code entry points and system/runtime gaps. | What system or runtime component does this service, container, endpoint, queue, host, datasource, deployment marker or runtime signal belong to? |
| `repo-map.yml` | Repositories, modules, source layout, generated clients, shared libraries, integration libraries, runtime-to-code mappings, package/class/path hints, code-search scopes and repository/code gaps. | Which GitLab projects, modules, packages, classes and related libraries should be searched for this runtime evidence or stacktrace? |
| `integrations.yml` | Operational contracts between systems or parties: synchronous APIs, async messaging, streams, webhooks, file transfers, database-facing contracts, gateway-mediated calls, participants, failure modes and integration gaps. | What contract does this endpoint, host, queue, topic, event, schema, client, listener, datasource or external target represent? |
| `processes.yml` | Business, operational and technical flows, process steps, lifecycle states, triggers, completion signals, data artifacts, failure modes, participants and process gaps. | Which flow or process step does this evidence affect and what upstream/downstream behavior matters? |
| `bounded-contexts.yml` | Semantic boundaries, local language, neighboring contexts, context-level runtime/code signals, canonical concepts, relations and semantic gaps. | What local domain or technical context does this code, endpoint, event, table or term belong to? |
| `teams.yml` | Operational actors, external parties, scoped responsibility relations, collaboration hints, recognition signals and responsibility gaps. | Which team, role or external party is responsible for which scope, and with what confidence? |
| `glossary.md` | Local vocabulary, aliases, acronyms, semantic disambiguation, evidence signals, canonical references and vocabulary gaps. | What does this local term mean here, what evidence points to it, and what should it not be confused with? |
| `handoff-rules.md` | Incident-specific routing and coordination overlay that refines default responsibility when evidence is ambiguous, cross-boundary, platform-related, external or split across roles. | Given this incident evidence, what should happen next operationally? |
| `operational-context-index.md` | This contract and usage guide for the catalog. | How should humans, agents, validators and tools interpret and maintain the catalog? |

If `terms.yml`, discovery files, staging files or view files are present, explain whether they are canonical, optional, generated, temporary or future-facing. Do not invent them when they are not in the inputs.

### `## How the catalog is built`

Describe the preferred update model:

1. Discover repo-local or source-local facts from code, config, deployment files, generated clients, schema files, documentation, runtime evidence or explicit ownership/support sources.
2. Classify each fact as runtime recognition, code-scope, integration, process, semantic, vocabulary, responsibility, routing, provenance, limitation, temporary uncertainty or durable gap.
3. Merge confirmed facts into the correct catalog file.
4. Validate cross-file references and canonical ids.
5. Reconcile discoveries from multiple repositories, generated clients, shared libraries, deployment/config repositories and documentation sources.
6. Promote only durable catalog-level unresolved issues into final `gaps`.
7. Keep temporary scan-order uncertainty in `BUILD MEMORY` or sidecar discovery output.

Mention monotonic merge:

- add confirmed facts;
- enrich existing entries;
- preserve existing confirmed facts;
- do not narrow global facts from local scans;
- do not invent missing graph nodes or ownership;
- do not delete or downgrade without explicit contradictory evidence.

### `## How the catalog is used`

Explain common analysis paths.

For deterministic mapping:

1. Start with the strongest available evidence: service, deployment, container, artifact, endpoint, queue/topic, event, DB marker, log marker, exception, package or class.
2. Resolve candidate systems/runtime components in `systems.yml`.
3. Use `repo-map.yml` to determine full code-search scope, including main repositories, shared libraries, generated clients and config/schema repositories when relevant.
4. Use `integrations.yml`, `processes.yml` and `bounded-contexts.yml` to understand contract, flow and semantic context.
5. Use `glossary.md` when local vocabulary, aliases or ambiguous terms need grounding.
6. Use `teams.yml` to understand role-specific responsibility after the affected graph area is grounded.
7. Use `handoff-rules.md` only when the task is incident-specific routing or coordination.
8. Check relevant `gaps`, source coverage and limitations before making high-confidence claims.

For LLM tools:

- query by stable ids, aliases, match keys and canonical references;
- return compact entries that are understandable in isolation;
- include confidence, source coverage, limitations and relevant gaps;
- prefer evidence-backed fields over broad summaries;
- preserve uncertainty instead of inventing ownership, topology or process boundaries.

### `## Deterministic mapping and code-search scope`

State that structured signals are preferred over prose. Include representative signal categories from the core principles.

Explain code-search scope:

- A deployed runtime component may map to several repositories.
- The relevant code scope may include a main service repo, shared library repos, generated client repos, integration libraries, schema repositories and deployment/config repositories.
- Shared libraries and generated clients can be part of search scope without owning the consuming runtime behavior.
- Use `repo-map.yml` and system/runtime references to construct focused GitLab searches.
- Treat code-search scope as a graph relation, not a string search across every repository.

Do not include a project-specific code-search example unless it appears in `CURRENT FILE` or `NEW FACTS`.

### `## Responsibilities, routing, and gaps`

Explain three separate concepts:

1. **Responsibility** — durable role-specific relationship between an actor and a graph node.
2. **Routing/handoff** — use-case-specific next-action view, especially for incident triage.
3. **Gaps** — durable unresolved catalog issues that affect mapping, analysis, responsibility, coordination or LLM answers.

Make clear that:

- responsibility does not require a single owner;
- participation is not ownership;
- code maintenance is not necessarily domain responsibility;
- platform support is not necessarily application behavior ownership;
- external ownership must be represented without forcing an internal team;
- handoff rules can refine coordination but must not rewrite core graph facts;
- final `gaps` are not temporary repository-agent memory.

### `## Multi-repository and partial-source safety`

Include concise rules:

- current input may be partial;
- merge positive facts;
- do not infer global absence from local absence;
- do not delete confirmed facts only because they are not visible in one source;
- do not replace a global list with a local subset;
- keep pending cross-repo joins in `BUILD MEMORY`;
- preserve shared-library/generated-client safety;
- do not infer ownership from authors, package names or current repository;
- validate cross-file references;
- use durable `gaps` only after available authoritative sources were checked.

### `## Operational context tool usage`

Explain how runtime features and LLM tools should consume the catalog:

- query focused graph slices, not full files by default;
- support queries by runtime evidence, repository evidence, class/package evidence, endpoint/queue/event evidence, DB marker, term, process, integration, system, team or known id;
- return compact, evidence-backed results;
- include matched signals, related nodes, code-search scope, responsibility roles, routing hints when relevant, source coverage, limitations and gaps;
- make tool results suitable for deterministic evidence providers, AI prompts, follow-up chat, QA and future analysis features.

The index may mention adapter/tool access conceptually, but it must not depend on a specific implementation unless that implementation is explicitly provided in `NEW FACTS`.

### `## Navigation guide`

Provide a short human navigation path. Prefer this order:

1. Start with `systems.yml` for system/runtime recognition.
2. Use `repo-map.yml` for code-search scope.
3. Use `integrations.yml` and `processes.yml` to understand contracts and flows.
4. Use `bounded-contexts.yml` and `glossary.md` for semantic meaning and local vocabulary.
5. Use `teams.yml` for scoped responsibility and collaboration.
6. Use `handoff-rules.md` only for incident-specific routing decisions.
7. Review relevant `gaps`, source coverage and limitations before making high-confidence claims.

If the project has additional current files, include them in the navigation only when they are provided by input evidence.

### `## Quality gates`

List concise gates that every index update must satisfy:

- Markdown is valid and readable.
- The document starts with `# Operational Context Index` unless an intentional project-specific title is already established.
- The index is not incident-only.
- The index describes the catalog as an operational graph.
- All canonical catalog files are listed exactly once.
- File responsibilities do not overlap ambiguously.
- The index does not contain detailed domain data that belongs in a specific catalog file.
- The index explains deterministic mapping and structured match signals.
- The index explains code-search scope across multiple repositories, libraries, generated clients and config/schema sources.
- The index explains LLM tool or adapter usage as focused query-based access.
- The index explains multi-repository and partial-source safety.
- The index explains responsibility relations without forcing a single owner.
- The index distinguishes durable `gaps` from temporary `BUILD MEMORY` or discovery artifacts.
- The index treats handoff/routing as a derived view.
- The index avoids secrets, credentials, tokens, private contact details, personal data, raw production records and sensitive environment-specific values.
- The index avoids copying generic example content that is not true for the target catalog.

### `## Maintenance rules`

Include operational maintenance rules such as:

- Keep the index concise and high-level.
- Update the index when canonical catalog files, use-case views, build workflow, schema families, adapter/tool consumption rules or quality gates change.
- Prefer stable file names and generic catalog language.
- Keep detailed schemas in file-specific prompts.
- Keep actual facts in their owning catalog files.
- Keep temporary build-state outside the final runtime catalog.
- Preserve confirmed facts unless explicit contradictory evidence exists.
- Use durable `gaps` for catalog-level unresolved issues only.

### `## Gaps`

Include this section only for durable index-level or catalog-contract-level gaps.

Use a compact Markdown structure if needed:

```md
## Gaps

### `gap-id`

**Type:** `missing-catalog-file | unclear-file-responsibility | conflicting-catalog-convention | missing-tool-consumption-rule | schema-alignment-gap | validation-gap | human-confirmation-required`

**Severity:** `low | medium | high | critical`

**Status:** `open | in-review | blocked | resolved | superseded`

**Affects**

- `operational-context-index.md`
- `systems.yml`

**Description**

...

**Impact**

- ...

**Suggested evidence sources**

- ...
```

Do not use `Gaps` for normal repo-local unknowns or future scanning work.

## Merge policy

When updating an existing `operational-context-index.md`:

- Preserve useful current guidance that matches the version-1 model.
- Replace incident-only wording with reusable analysis wording.
- Replace ownership-table wording with responsibility-relation wording.
- Replace broad architecture essays with compact catalog-contract guidance.
- Replace legacy `Open Questions` with durable `Gaps` when the issue is truly catalog-level.
- Remove temporary scan notes, generated discovery state and one-repository pending joins from the final index.
- Do not delete current file references unless the inputs explicitly show the file is removed, deprecated or non-canonical.
- Do not add files that are not present or explicitly planned in the inputs.
- Do not preserve old section names only for compatibility when they conflict with the version-1 model.

### Allowed changes

- Add missing downstream use cases.
- Add or refine file responsibility descriptions.
- Add query-based adapter/tool access guidance.
- Add deterministic mapping and code-search scope guidance.
- Add multi-repository and partial-source safety rules.
- Add quality gates.
- Normalize `Open Questions` wording to `Gaps`.
- Remove copied example content that is not true for the target catalog.
- Tighten security/privacy guidance.

### Forbidden changes without explicit evidence

- Inventing systems, repositories, teams, processes, integrations or contexts.
- Claiming a file is canonical when inputs do not mention it.
- Claiming a schema has changed when only a generic prompt example says so.
- Deleting a real current catalog file from the file map because it is absent from one source.
- Turning routing/handoff into the source of truth for ownership.
- Turning `BUILD MEMORY` or discovery artifacts into final catalog facts.
- Adding secrets, credentials, private contacts, customer data or raw production records.

## Internal workflow

Before updating the file, perform these internal steps:

1. Read `CURRENT FILE`.
2. Read related updated prompt files and catalog files when provided.
3. Identify current canonical files, optional files, generated/staging files and missing files.
4. Identify legacy concepts that must be normalized to the version-1 operational graph model.
5. Identify current use cases, consumers and tool/adapter access rules supported by evidence.
6. Identify durable catalog-level gaps, if any, and separate them from temporary `BUILD MEMORY` uncertainty.
7. Produce a concise, complete index that explains purpose, use cases, graph model, file map, build/update rules, deterministic mapping, code-search scope, responsibilities, gaps, tool access, navigation, quality gates and maintenance rules.
8. Validate the result against the quality gates.
9. Return the full updated Markdown only.

Do not expose this internal workflow in the final output.

## Validation checklist

Before returning the updated index, verify:

- the output is Markdown only;
- there are no Markdown fences around the whole answer;
- the document starts with `# Operational Context Index` unless an intentional alternative is established;
- the index is not incident-only;
- the index is not an ownership table;
- the index is not a schema dump;
- the index explains the operational graph model;
- the index includes the current canonical file map;
- the index explains deterministic mapping;
- the index explains code-search scope across multiple repositories, shared libraries, generated clients and config/schema sources;
- the index explains query-based adapter/tool usage;
- the index explains partial-source and multi-repository safety;
- the index explains responsibility relations without requiring a single owner;
- the index distinguishes durable `gaps` from temporary `BUILD MEMORY` uncertainty;
- the index treats routing/handoff as a derived view;
- no project-specific examples were invented;
- no generic example content was copied into the real output by accident;
- no secrets, credentials, tokens, private contact details, personal data, raw production payloads or sensitive environment values are present.

## Writing style

Use direct, operational language.

Prefer:

- compact sections;
- short paragraphs;
- bullets and small tables over long essays;
- stable file names in backticks;
- generic language that applies across domains;
- “responsibility” over “ownership” unless explicit ownership is being discussed;
- “runtime component” for deployed/deployable artifacts;
- “system” for logical operational actors or capabilities;
- “gaps” for durable unresolved catalog issues;
- “BUILD MEMORY” for temporary cross-repository build state.

Avoid:

- marketing language;
- long DDD explanations;
- full file schemas;
- detailed endpoint/queue/class/table inventories;
- project-specific examples unless the target catalog actually contains them;
- generic CRM/banking/order-management examples copied from prompts;
- ungrounded claims about current adapter implementation if not provided in inputs;
- raw ids from unrelated examples.

## Legacy migration guidance

If the current index uses legacy concepts, normalize them as follows:

| Legacy wording | Version-1 wording                                                         |
| --- |---------------------------------------------------------------------------|
| Incident context only | Reusable operational context catalog                                      |
| Ownership table | Scoped responsibility relations                                           |
| Owner team | Responsibility role, with confidence and scope                            |
| Runtime service only | Logical system and/or runtime component                                   |
| Repo lookup | Code-search scope across repositories/modules/libraries/generated clients |
| Open Questions | Durable `gaps`                                                            |
| Temporary todo | `BUILD MEMORY` or discovery sidecar                                       |
| Handoff source of truth | Handoff/routing overlay                                                   |
| Long architecture notes | Compact graph/file-map guidance                                           |
| Load whole catalog into prompt | Query focused graph slice through adapter/tool                            |

Do not preserve legacy wording merely because it exists in `CURRENT FILE`.
