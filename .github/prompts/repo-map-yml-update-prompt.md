# repo-map.yml update prompt

Update only `repo-map.yml`.

This prompt is schema-authoritative for `repo-map.yml`. If a parent operational-context prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy, merge policy, quality gate and output contract for `repo-map.yml`.

Do not preserve legacy fields or legacy structures unless they are explicitly represented in this schema.

## Purpose

Maintain `repo-map.yml` as an enterprise-grade, evidence-backed, queryable repository, module and code-search-scope map for a reusable operational context catalog.

`repo-map.yml` is not a simple repository list and not an incident-routing file. It is the primary deterministic bridge between runtime/deployment/log evidence and source code. It connects deployed components, application names, service names, container names, build artifacts, package prefixes, stacktrace classes, endpoint groups, message channels, database markers, workflow/job names, generated clients, shared libraries and modules to the GitLab repositories and code areas that should be searched together.

The file supports:

- deterministic mapping from runtime, deployment, log, code, configuration, documentation and domain evidence to repositories and modules;
- GitLab/code search scope construction across main service repositories, shared libraries, shared modules, generated clients, integration libraries, schema repositories and deployment/config repositories;
- focused LLM tool usage, especially GitLab search, source resolve, outline, flow context, class references and small file/chunk reads;
- function description and user-facing explanations of affected behavior, code areas, module boundaries and dependencies;
- impact analysis and change analysis across systems, runtime components, repositories, modules, integrations, processes, bounded contexts, terms and teams;
- DB/code grounding when a datasource, table, entity, repository, migration, schema, pool or persistence symptom appears in evidence;
- incident analysis and triage as one downstream view, without reducing responsibility to a single owner;
- repository onboarding, dependency analysis, Q&A and future AI analysis features beyond incident tracking;
- identification of missing repository identity, module boundaries, code-search scope, deterministic signals or responsibility documentation without inventing facts.

A repository entry should explain:

- what the repository contains operationally;
- whether it is a deployable service, shared library, generated client, integration library, deployment/config repo, workflow/config repo, frontend, documentation repo or mixed repository;
- where the repository appears in GitLab, build metadata, deployment, runtime evidence, code, configuration and documentation;
- which systems, runtime components, processes, integrations, bounded contexts, terms and teams it connects to;
- which modules or source areas matter for mapping, search, grounding and explanation;
- which deterministic signals identify the repository and its modules;
- which other repositories should be searched with it as one code scope;
- which responsibilities are explicit, shared, external, unknown or disputed;
- how an LLM should prefer, avoid, explain and search this repository;
- what remains unknown after durable validation.

## Non-goals

Do not turn `repo-map.yml` into:

- a complete enterprise GitLab project catalog;
- a full source-code inventory;
- a full package/class/method index;
- a dependency lockfile;
- a complete Maven/Gradle/npm dependency graph;
- an API specification;
- a database schema catalog;
- a process catalog;
- an integration catalog;
- a team ownership matrix;
- a long architecture essay;
- an incident escalation playbook;
- a scratchpad for temporary agent uncertainty.

Do not create one repository entry per module unless the module is a separate GitLab repository. Use `modules` for meaningful modules, source sets, packages, libraries or subdirectories inside one repository.

Do not model every class, endpoint, DTO, table, enum or config key. Keep representative, stable, operational and deterministic mapping signals.

Create or keep a repository entry only when it helps at least one of:

- mapping evidence to a source repository or module;
- constructing a focused code-search scope;
- explaining what a deployed system or library does;
- finding source for stacktrace classes, runtime components, generated clients or shared library code;
- grounding DB/data symptoms in code or migrations;
- understanding cross-system dependencies;
- impact analysis, change analysis or dependency analysis;
- routing or coordinating work without inventing ownership;
- distinguishing neighboring bounded contexts, processes or integrations;
- future AI analysis features that need repository and code-scope context.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `repo-map.yml`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, build reports, documentation fragments, runtime evidence, deployment/config evidence, database discovery, existing operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, branch, commit, generated client, shared library, schema repository, deployment/config repository, documentation fragment, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `systems.yml`, `runtime-components.yml`, `processes.yml`, `integrations.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` or `terms.yml`, `handoff-rules.md` and `operational-context-index.md`.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate repositories, candidate modules, partial facts, unresolved references and repositories not yet scanned. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

If some inputs are missing, do the best possible update using available evidence. Do not invent missing facts.

## Output

Return the full updated `repo-map.yml` YAML only.

Do not include Markdown fences.
Do not include explanations.
Do not include diffs.
Do not include partial snippets.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `repo-map.yml`.
Do not output `BUILD MEMORY` inside `repo-map.yml`.

The final YAML must parse successfully.

## Required top-level schema

Use this exact top-level shape. Do not preserve legacy top-level fields.

```yaml
schemaVersion: 1
catalogKind: operational-context-repository-map
repositories: []
codeSearchScopes: []
gaps: []
```

Top-level order must be:

1. `schemaVersion`
2. `catalogKind`
3. `repositories`
4. `codeSearchScopes`
5. `gaps`

Use `gaps`, not legacy untyped `openQuestions`. Durable unresolved questions are represented as typed gaps.

## Core principles

### 1. Repository map is a code-scope graph, not a repo list

Treat `repo-map.yml` as part of the operational graph.

The graph connects:

- systems and runtime components;
- GitLab repositories and internal modules;
- main services, shared libraries, shared modules, generated clients, integration libraries, schema repositories and deployment/config repositories;
- deterministic recognition signals;
- code-search scopes;
- bounded contexts and glossary terms;
- business/operational/technical processes;
- integration contracts;
- DB/data grounding hints;
- responsibility roles and evidence;
- durable knowledge gaps.

A deployed runtime component may map to several repositories. A repository may be used by many systems. Do not force one-to-one mappings between systems and repositories.

### 2. Code-search scope is first-class

A stacktrace, class name, generated DTO, exception, package prefix or runtime marker may originate from:

- the main service repository;
- a shared domain or technical library;
- a shared module inside a monorepo;
- a generated client repository;
- an integration adapter library;
- a schema/message-contract repository;
- a deployment/config repository;
- a workflow/config repository.

Represent this explicitly through top-level `codeSearchScopes`. Also keep repository-local code-search hints in repository entries. A code-search scope should make it clear which repositories and modules should be searched together and in what priority.

### 3. Responsibilities are role-based, not single ownership

Do not collapse multi-team responsibility into one `ownerTeamId` equivalent.

A repository or module may have:

- repository maintainers;
- module stewards;
- domain stewards;
- runtime operators;
- integration contract stewards;
- generated client maintainers;
- data stewards;
- platform support;
- consumer teams;
- producer teams;
- workers or contributors without ownership;
- external owners;
- unresolved, disputed or not-applicable responsibility.

If a source only shows that a team contributes to, consumes or participates in a repository, do not turn that into accountable ownership.

### 4. Routing is only a downstream view

Do not model incident routing as the core truth of a repository.

Use `analysisHints.incidentAnalysis`, `handoffHints` and cross-file references only for reusable hints. Detailed escalation, on-call rules and routing overrides belong to incident-specific handoff views, not to this file.

### 5. Deterministic signals are more important than prose

Prefer concrete, searchable signals over long descriptions:

- GitLab group, project, project path, aliases and repository URL;
- Maven/Gradle/npm/dotnet/go/python coordinates and artifact names;
- service, application, deployment, container and image names;
- package prefixes and source roots;
- class/interface/enum/annotation/client/listener/controller/repository/entity/config names;
- endpoint prefixes and templates;
- queue/topic/exchange/routing-key names;
- event/message/schema names;
- datasource names, Hikari pool names, database schemas/tables/entities and migration paths;
- workflow, job, lock and scheduler names;
- log markers, exception classes, error codes, span names and metrics;
- local terms and aliases from glossary/terms.

Descriptions help humans and LLMs interpret facts, but deterministic signals make the file usable as an index.

### 6. Multi-repository scans are partial by default

The current repository, documentation fragment or build report is only a partial evidence source.

Repository and code-scope facts may be split across:

- deployed service repositories;
- shared libraries;
- generated clients;
- integration libraries;
- workflow/config repositories;
- deployment/config repositories;
- schema/message repositories;
- frontend repositories;
- upstream and downstream service repositories;
- product, operations or team-owned documentation;
- platform/infrastructure repositories.

Never infer that a repository, module, runtime mapping, code-search scope, responsibility, integration, process or deterministic signal does not exist only because it is absent from the current source.

Do not remove, downgrade or overwrite existing confirmed catalog facts only because they are not visible in the current scan.

Temporary scan-order uncertainty must go to `BUILD MEMORY`, not to final `gaps`, when it may be resolved by scanning another known source.

### 7. Shared library evidence is not consuming behavior ownership

A shared library, generated client, integration library or schema repository may contain the class that appears in a stacktrace without owning the consuming runtime behavior.

When the current repository is a shared library or generated client:

- capture package prefixes, classes, module names, artifact coordinates, generated-source paths and repository identity;
- represent consumers only when consuming evidence exists;
- do not assume the library repository owns the runtime behavior of consuming services;
- record consuming systems, runtime components, processes or contexts as pending cross-repo joins in `BUILD MEMORY` unless explicitly known.

When the current repository is a consuming service:

- capture runtime identity, package usage, dependency coordinates, generated client usage, integration calls, process/context evidence and deployment signals;
- link to existing shared library/generated client repositories when evidence supports the join;
- do not invent library repository identity if it is not known.

### 8. Durable gaps only

Final `gaps` represent durable catalog gaps after available evidence has been checked, or issues requiring human/team/domain input.

Use final `gaps` for:

- unresolved GitLab project identity after expected evidence was checked;
- unclear module boundary that affects code search or impact analysis;
- missing code-search scope after available sources were scanned;
- unresolved responsibility after ownership/support evidence was checked;
- missing referenced catalog node after expected sources were checked;
- conflicting repository/module definitions across sources;
- ambiguous library versus service classification that affects search or routing;
- ambiguous split/merge decision requiring human confirmation;
- missing DB/code grounding information after available sources were checked.

Do not use final `gaps` for temporary cross-repo joins that are still pending in build memory.

## Required discovery procedure

Before editing, inspect the provided repository/documentation and classify candidate facts.

### Discover repository identity from

- Git remote URL;
- GitLab group, project, project path, namespace and default branch;
- repository name and root directory;
- README, architecture docs, ownership docs, support docs and ADRs;
- CODEOWNERS or equivalent ownership files;
- `.gitlab-ci.yml`, CI pipeline names, artifact names, image names and deployment jobs;
- Maven/Gradle/npm/dotnet/go/python build metadata;
- generated client metadata and schema-generation config.

### Discover build and module structure from

- `pom.xml`, parent POM, Maven modules, `groupId`, `artifactId`, packaging and dependency management;
- `build.gradle`, `settings.gradle`, Gradle subprojects and plugin config;
- package managers such as `package.json`, `pnpm-lock.yaml`, `yarn.lock`, `.csproj`, `go.mod`, `pyproject.toml`, `setup.py`, `requirements.txt`;
- module paths and source sets;
- generated client modules;
- shared libraries and integration libraries;
- test-support modules;
- dependency declarations pointing to internal libraries.

### Discover runtime identity from

- `spring.application.name` and equivalent application/service identifiers;
- `application.yml`, `application.properties` and profile-specific configuration;
- Dockerfiles, Helm charts, Kubernetes manifests, deployment descriptors, Terraform or environment config;
- container names, deployment names, service names, image names and packaged artifact names;
- logging, tracing and metrics identifiers.

### Discover source layout from

- source roots;
- test roots;
- resource roots;
- package prefixes;
- domain modules;
- application modules;
- adapters/infrastructure modules;
- integration-client modules;
- generated-code folders;
- database migration folders;
- workflow/config folders;
- documentation folders.

### Discover APIs and clients from

- REST controllers and route annotations;
- GraphQL resolvers;
- OpenAPI/Swagger files;
- Feign clients, WebClient clients, RestTemplate clients and HTTP wrappers;
- SOAP clients, generated stubs, WSDL references and XML marshalling classes;
- gRPC clients/stubs and proto files;
- inbound webhooks and callback endpoints;
- base URL properties;
- endpoint prefixes and templates.

### Discover messaging from

- Rabbit/Kafka listeners;
- Spring Cloud Stream bindings;
- StreamBridge usage;
- KafkaTemplate/RabbitTemplate usage;
- queue, exchange, topic, routing-key, binding, channel, consumer-group and DLQ names;
- event classes, command classes and schema names;
- message schema repositories and generated message clients.

### Discover persistence and DB/code grounding from

- datasource configuration;
- Hikari pool names;
- JPA entities, repositories, DAOs and query classes;
- migration files and migration paths;
- database schemas, tables, columns and entity names;
- read models and audit tables;
- data-access package prefixes;
- DB-specific exception classes and log markers.

### Discover jobs and workflows from

- `@Scheduled`, batch jobs, locks and scheduler config;
- workflow definitions, BPMN files, Temporal/Camunda/Conductor/Airflow config;
- job names, lock names and process definitions;
- CLI or batch entrypoints;
- workflow orchestration packages.

### Discover observability and operational signals from

- log markers;
- span names;
- metric names;
- exception classes;
- error markers;
- health endpoints;
- correlation/tracing filters;
- operator labels and support docs.

### Discover responsibilities from

- explicit owner/steward/maintainer docs;
- CODEOWNERS entries;
- team labels;
- support and on-call docs;
- module-level responsibility notes;
- contribution evidence;
- platform/runtime/database/message-broker support docs.

Do not infer a primary owner from weak evidence such as a team name in a package, a historical commit, an author, a worker reference or a single support ticket.

## Strong indicators

Create or update a repository entry when at least one strong indicator exists:

- confirmed GitLab project path or repository URL;
- build metadata with stable artifact/project coordinates;
- runtime/deployment evidence mapping to a project;
- source layout and package prefixes from an actual repository scan;
- generated client or shared library coordinates used by another repository;
- explicit documentation naming the repository;
- code-search scope that requires the repository to resolve stacktrace or source evidence;
- existing cross-file reference to this repository that is supported by new evidence.

Create or update a module entry when at least one strong indicator exists:

- build submodule or source-set boundary;
- package/module area dedicated to a capability, bounded context, process, integration or data access concern;
- generated client module;
- shared library module;
- workflow/config module;
- persistence module with DB/code grounding value;
- distinct responsibility or code-search scope;
- repeated runtime or stacktrace evidence pointing to the module.

Create or update a code-search scope when at least one strong indicator exists:

- one runtime component maps to multiple repositories;
- a main service depends on generated clients or shared libraries whose classes can appear in logs or stacktraces;
- DB/code grounding requires service code plus migration/data-access/library repositories;
- integration analysis requires both consumer and provider/generated-client repositories;
- a known flow spans service, workflow/config, schema and integration repositories;
- operational context or existing evidence already identifies a multi-repository source scope.

## Weak indicators are not enough alone

Do not create repository, module or code-search-scope entries from only:

- one arbitrary class name without repository identity;
- one generic package such as `common`, `util`, `config`, `security`, `client` or `adapter`;
- one dependency artifact with no internal repository identity and no operational relevance;
- one host name with no code or deployment evidence;
- one team name without repository evidence;
- a folder name with no source or build evidence;
- a generated DTO with no client or contract context;
- a vague README keyword without corroborating build/runtime/source evidence.

If evidence is promising but incomplete, record a candidate in `BUILD MEMORY` when possible. Create a final `gap` only when the issue is durable and affects deterministic mapping, code search scope, impact analysis, DB/code grounding, semantic interpretation or incident triage.

## Schema

The full output must be valid YAML with this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-repository-map
repositories: []
codeSearchScopes: []
gaps: []
```

Use only the fields defined below.

### `repositories[]`

Each repository entry must use this structure and field order. Keep required empty lists as `[]` when there is no confirmed value. Use `null` for unknown scalar values. Empty optional fields inside `matchSignals` buckets may be omitted.

```yaml
- id: stable-repository-id
  name: Human readable repository name
  repositoryType: service | shared-library | generated-client | integration-library | data-access-library | deployment-config | workflow-config | schema-repository | frontend | documentation | test-support | monorepo | aggregator | infrastructure | mixed | unknown
  lifecycleStatus: active | planned | deprecated | retired | candidate | external | unknown
  criticality: critical | high | medium | low | unknown
  git:
    provider: gitlab | github | bitbucket | internal | unknown
    group: string | null
    project: string | null
    projectPath: string | null
    defaultBranch: string | null
    url: string | null
    aliases: []
    inferred: false
  purpose: Short operational description of what this repository contains.
  useFor: []
  responsibilityStatus: explicit-single | explicit-multiple | shared | unresolved | disputed | external | platform-shared | not-applicable | unknown
  responsibilities: []
  references:
    systems: []
    runtimeComponents: []
    deploymentComponents: []
    repositories: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
    handoffRules: []
  sourceLayout:
    repositoryRoot: null
    buildTool: unknown
    buildFiles: []
    sourceRoots: []
    testRoots: []
    resourceRoots: []
    modulePaths: []
    generatedSourcePaths: []
    importantPaths: []
    configurationFiles: []
    deploymentFiles: []
    databaseMigrationPaths: []
    workflowDefinitionPaths: []
    documentationPaths: []
  modules: []
  runtimeMappings: []
  codeSearch:
    scopeRole: primary
    searchTogetherWith: []
    consumedBySystems: []
    consumedByRuntimeComponents: []
    consumedByRepositories: []
    consumesRepositories: []
    exportedPackages: []
    importedPackageHints: []
    dependencyCoordinates: []
    generatedClientCoordinates: []
    schemaCoordinates: []
    notes: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  lookupHints:
    likelyEntryClasses: []
    likelyControllerClasses: []
    likelyServiceClasses: []
    likelyClientClasses: []
    likelyListenerClasses: []
    likelyPublisherClasses: []
    likelyRepositoryClasses: []
    likelyEntityClasses: []
    likelyConfigClasses: []
    likelyGeneratedClientClasses: []
    likelyWorkflowClasses: []
    likelyFiles: []
    likelyDirectories: []
    stacktraceHotspots: []
    searchKeywords: []
    searchAntiPatterns: []
  analysisHints:
    deterministicMapping: []
    codeSearch: []
    functionDescription: []
    impactAnalysis: []
    changeAnalysis: []
    dependencyAnalysis: []
    dbGrounding: []
    incidentAnalysis: []
    qa: []
  handoffHints:
    defaultRoute: []
    requiredEvidence: []
    firstActions: []
    escalationTriggers: []
  llmToolHints:
    preferredWhen: []
    avoidWhen: []
    explanationHints: []
    disambiguationHints: []
    commonMisreads: []
    usefulForQuestions: []
  evidence: []
  sourceCoverage:
    status: complete | partial | single-source | unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

#### `id`

Stable kebab-case identifier used by other operational-context files.

Examples:

- `crm-api-repo`
- `crm-shared-kernel-repo`
- `marketing-client-repo`
- `crm-ui-repo`
- `crm-deployment-config-repo`

Rules:

- use lowercase letters, digits and hyphens only;
- do not use spaces, slashes or underscores;
- prefer repository identity over vague purpose names;
- do not rename an existing id unless the current id is clearly invalid or the task explicitly asks for normalization;
- if a duplicate exists, keep the best canonical id and merge facts when safe;
- if duplicate cleanup cannot be safely resolved, add a durable gap.

#### `name`

Human-readable repository name. Use a name that engineers, operators and LLM tools can recognize.

#### `repositoryType`

Use the most specific supported type:

- `service`: deployable application or runtime service;
- `shared-library`: library consumed by multiple services;
- `generated-client`: generated API, schema, SOAP, gRPC, OpenAPI or message client;
- `integration-library`: reusable integration client/binding/adapter module;
- `data-access-library`: reusable persistence or data-access package;
- `deployment-config`: manifests, Helm charts, Kubernetes, environment or deployment config;
- `workflow-config`: workflow/process definitions or orchestration config;
- `schema-repository`: message/API/database schema repository;
- `frontend`: frontend application;
- `documentation`: documentation-only repository;
- `test-support`: fixtures, mocks, test utilities or test harnesses;
- `monorepo`: repository containing multiple independently meaningful areas;
- `aggregator`: aggregator/build parent repository without primary runtime code;
- `infrastructure`: infrastructure or platform repository;
- `mixed`: repository contains several major categories and cannot be classified more specifically;
- `unknown`: not enough evidence.

#### `lifecycleStatus`

Use:

- `active`: current and used;
- `planned`: documented but not active yet;
- `deprecated`: still visible but no longer preferred;
- `retired`: no longer active, kept only for historical evidence;
- `candidate`: referenced but not fully confirmed;
- `external`: owned outside the organization but represented for operational analysis;
- `unknown`: status unclear.

#### `criticality`

Use documented operational/business criticality only. Do not infer high criticality merely because the repository exists.

#### `git`

Use `git.projectPath` when known; it is the most useful GitLab identifier.

Rules:

- set unknown scalar values to `null` rather than inventing them;
- set `inferred: true` only when a Git identity is inferred from partial evidence;
- use `aliases` for project aliases, historical names, short names, image labels or operator labels that point to the same repository;
- if a repository is operationally important but GitLab identity is missing, keep it as `candidate` only when useful for mapping or code search; otherwise keep the candidate in `BUILD MEMORY` until stronger evidence appears.

#### `purpose`

One concise operational sentence. Do not write architecture essays.

Good:

```yaml
purpose: Hosts the CRM API service and customer/lead modules used by the deployed crm-api runtime.
```

Bad:

```yaml
purpose: This repository implements a large distributed CRM platform with many architectural layers...
```

#### `useFor`

List downstream use cases supported by the repository entry.

Allowed values:

- `deterministic-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `change-analysis`
- `dependency-analysis`
- `db-grounding`
- `incident-analysis`
- `integration-analysis`
- `process-analysis`
- `domain-understanding`
- `onboarding`
- `qa`

Use at least `deterministic-mapping` and `code-search` for any code repository.

#### `responsibilityStatus`

Do not force a single owner.

Use:

- `explicit-single`: one accountable team is explicitly documented;
- `explicit-multiple`: several explicit responsible teams/scopes are documented;
- `shared`: several teams/scopes are involved, but accountability is shared or mixed;
- `unresolved`: responsibility matters but is not known;
- `disputed`: reliable evidence conflicts;
- `external`: external party owns or maintains it;
- `platform-shared`: responsibility is shared with platform/runtime/DB/MQ/infrastructure support;
- `not-applicable`: responsibility is not relevant for this repository;
- `unknown`: no reliable evidence.

#### `responsibilities`

Use role-based responsibility objects instead of a single owner.

Preferred shape:

```yaml
responsibilities:
  - teamId: crm-platform-team
    role: repo-maintainer
    scope: repository
    evidence: codeowners
    confidence: high
    source: CODEOWNERS
```

Allowed `role` values:

- `repo-maintainer`
- `module-steward`
- `domain-steward`
- `runtime-operator`
- `integration-contract-steward`
- `generated-client-maintainer`
- `data-steward`
- `workflow-steward`
- `platform-support`
- `consumer`
- `producer`
- `worker`
- `contributor`
- `external-owner`
- `unknown`

Allowed `evidence` values:

- `explicit`
- `codeowners`
- `documentation`
- `runtime-config`
- `build-config`
- `deployment-config`
- `inferred-from-module`
- `inferred-from-integration`
- `inferred-from-usage`
- `human-confirmed`
- `unknown`

Allowed `confidence` values:

- `high`
- `medium`
- `low`

Rules:

- never convert `worker`, `contributor` or `consumer` evidence into `repo-maintainer` or `domain-steward` unless explicit evidence exists;
- use `scope` for module id, package prefix, repository, runtime component, process or integration when responsibility is not global;
- record unresolved responsibility as a durable `gap` only when it affects analysis, mapping or coordination and expected sources were checked.

#### `references`

Cross-file graph references. Use stable IDs from the corresponding operational-context files when known.

Rules:

- `systems`: ids from `systems.yml`;
- `runtimeComponents`: ids from `runtime-components.yml`, if present;
- `deploymentComponents`: deployment component ids/names when modeled separately or used by evidence;
- `repositories`: related repository ids from this file;
- `processes`: ids from `processes.yml`;
- `boundedContexts`: ids from `bounded-contexts.yml`;
- `integrations`: ids from `integrations.yml`;
- `terms`: ids from `glossary.md` or `terms.yml`;
- `teams`: ids from `teams.yml`;
- `handoffRules`: ids from handoff/routing views, if present.

Do not create dangling references unless the corresponding node is expected to be created in the same build cycle. If a reference is likely but unconfirmed because another source has not been scanned, record it in `BUILD MEMORY`, not final `gaps`.

#### `sourceLayout`

Use `sourceLayout` to help code-search tools and LLMs choose focused reads.

Rules:

- keep paths repository-relative;
- include representative paths, not every folder;
- include generated source paths when generated clients/classes can appear in stacktraces;
- include DB migration paths when DB/code grounding is useful;
- include workflow definition paths when the repo contains flow definitions;
- include documentation paths only when they contain operational context, ownership, support, architecture or domain facts.

Allowed `buildTool` values:

- `maven`
- `gradle`
- `npm`
- `yarn`
- `pnpm`
- `dotnet`
- `go`
- `python`
- `mixed`
- `none`
- `unknown`

#### `modules`

A module is a meaningful code-search or responsibility unit inside a repository. Do not list every package.

Add a module only when it helps deterministic mapping, code search, impact analysis, DB/code grounding, function description, dependency analysis, integration analysis, process analysis or routing.

Use module entry schema below.

#### `runtimeMappings`

Use `runtimeMappings` to connect deployed/runtime identity to repository and module identity.

Preferred shape:

```yaml
runtimeMappings:
  - runtimeComponent: crm-api-runtime
    system: crm-api
    environment: null
    deploymentNames: []
    serviceNames: []
    applicationNames: []
    containerNames: []
    imageNames: []
    artifactNames: []
    modules: []
    evidence: []
    confidence: high
```

Rules:

- do not expose secrets or environment-specific sensitive values;
- use `environment: null` if environment is unknown or should remain hidden;
- if mapping is inferred from deployment context, set confidence accordingly and include evidence;
- do not conflate runtime ownership with repository maintenance.

#### `codeSearch`

Use repository-local `codeSearch` to explain how this repository participates in broader scopes.

Allowed `scopeRole` values:

- `primary`
- `supporting-library`
- `shared-library`
- `generated-client`
- `integration-adapter`
- `schema-source`
- `deployment-config`
- `workflow-config`
- `configuration-only`
- `documentation-only`
- `infrastructure-only`
- `unknown`

Rules:

- `searchTogetherWith` should reference repository ids that are commonly searched with this repository;
- `consumedBySystems` and `consumedByRuntimeComponents` are useful for shared libraries and generated clients;
- `consumedByRepositories` identifies repositories that depend on this repository;
- `consumesRepositories` identifies repositories this repository depends on for code search;
- `dependencyCoordinates` should include Maven/Gradle/npm/etc. coordinates that help join repositories;
- `generatedClientCoordinates` should include generated client identifiers;
- `schemaCoordinates` should include schema/proto/OpenAPI/AsyncAPI/WSDL coordinates or repository references when useful.

Do not hide multi-repository code scopes only in prose. Also represent them through top-level `codeSearchScopes`.

#### `matchSignals`

Signals are the most important part of this file. Prefer deterministic values over prose.

Use this shape:

```yaml
matchSignals:
  exact:
    projectNames: []
    projectPaths: []
    repositoryUrls: []
    artifactIds: []
    groupIds: []
    serviceNames: []
    deploymentNames: []
    containerNames: []
    imageNames: []
  strong:
    moduleNames: []
    packagePrefixes: []
    sourceRoots: []
    endpointPrefixes: []
    endpointTemplates: []
    queues: []
    topics: []
    exchanges: []
    routingKeys: []
    datasourceNames: []
    hikariPools: []
    schemas: []
    tables: []
    configPrefixes: []
    buildCoordinates: []
  medium:
    classHints: []
    interfaceHints: []
    enumHints: []
    annotationHints: []
    methodHints: []
    beanNames: []
    exceptionClasses: []
    eventNames: []
    schemaNames: []
    jobNames: []
    workflowNames: []
    spanNames: []
    metricNames: []
    logMarkers: []
  weak:
    terms: []
    labels: []
    readmeKeywords: []
```

Signal strength guidance:

- `exact` signals identify the repository or runtime component almost directly;
- `strong` signals strongly point to this repository/module but may require one join;
- `medium` signals are useful for code search and source resolution;
- `weak` signals help semantic matching but must not be used alone for strong deterministic mapping.

Rules:

- keep values searchable;
- do not put long descriptions into signals;
- avoid environment-specific secret values;
- do not include credentials, tokens, authorization headers, connection strings, raw customer data or sample payloads containing personal data;
- every repository should have at least one exact or strong signal, unless a durable gap explains why not.

#### `lookupHints`

Use `lookupHints` to help LLM tools choose focused code reads.

Rules:

- include representative class names and files, not exhaustive inventories;
- include stacktrace hotspots when they repeatedly appear in operational evidence;
- include `searchAntiPatterns` for misleading terms, generic folders or generated code that should not be used as the first lookup;
- keep hints practical and short.

#### `analysisHints`

Use `analysisHints` for reusable, non-incident-specific analysis guidance.

Examples:

```yaml
analysisHints:
  deterministicMapping:
    - Prefer projectPath and spring.application.name before package keywords.
  codeSearch:
    - Search this repo with crm-shared-kernel-repo when CustomerProfile classes are missing.
  dbGrounding:
    - Use migrations under src/main/resources/db/migration to map customer_profile table to entities.
```

Do not write long runbooks here.

#### `handoffHints`

Handoff hints are not ownership. Keep them compact and evidence-oriented.

Use only reusable coordination hints such as:

- which evidence should be gathered before contacting a team;
- which role to involve first when evidence points to a shared library, generated client, DB, MQ or runtime platform;
- what should escalate to integration partner, platform support or external owner.

Detailed incident routing belongs to handoff/routing views.

#### `llmToolHints`

Use `llmToolHints` to make a single repository entry useful when returned to an LLM tool.

- `preferredWhen`: when this repository is a good candidate for answering a user question;
- `avoidWhen`: when this repository is a misleading candidate;
- `explanationHints`: how to describe the repository in user-facing terms;
- `disambiguationHints`: how to distinguish it from neighboring repositories;
- `commonMisreads`: common mistakes, such as treating a generated client as the owner of behavior;
- `usefulForQuestions`: examples of question types this repository helps answer.

#### `evidence`

Every important added or changed fact should be backed by evidence.

Preferred shape:

```yaml
evidence:
  - sourceType: code | config | build | deployment | runtime | doc | test | human | build-memory
    source: repo:path/to/file
    detail: "spring.application.name=crm-api"
    supports:
      - matchSignals.exact.serviceNames
    confidence: high
```

Allowed `sourceType` values:

- `code`
- `config`
- `build`
- `deployment`
- `runtime`
- `doc`
- `test`
- `human`
- `build-memory`

Allowed `confidence` values:

- `high`
- `medium`
- `low`

Rules:

- use evidence references, not long copied source excerpts;
- avoid secrets, tokens, customer data and full production values;
- `build-memory` evidence may help explain promotion, but do not promote a fact to final YAML unless concrete evidence supports it;
- use `human` only when the input explicitly contains human-provided facts.

#### `sourceCoverage`

Use `sourceCoverage` to avoid overclaiming.

Allowed `status` values:

- `complete`: expected sources for this entry were checked;
- `partial`: some expected sources were checked, but not all;
- `single-source`: only one source type or repository was checked;
- `unknown`: coverage cannot be assessed.

Rules:

- `scannedSources` should describe actual sources scanned;
- `expectedSources` should list important expected sources when coverage is partial;
- `limitations` should state practical limitations, not temporary agent thoughts;
- do not lower confidence of existing confirmed facts merely because the current scan is partial.

## Module entry schema

Use `modules` to represent meaningful modules, source sets, bounded areas, packages, libraries or subdirectories inside a repository.

Every module entry must use this structure and field order:

```yaml
- id: stable-module-id
  name: Human readable module name
  moduleType: application-module | domain-module | api-module | integration-client | messaging-module | generated-client | shared-kernel | shared-library-module | data-access | persistence-module | workflow-module | batch-module | frontend-module | security | observability | configuration | deployment-config | schema-module | test-support | documentation | unknown
  lifecycleStatus: active | planned | deprecated | retired | generated | unknown
  path: null
  purpose: Short operational description of the module.
  references:
    systems: []
    runtimeComponents: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
  responsibilities: []
  source:
    paths: []
    packages: []
    generated: false
    build:
      groupId: null
      artifactId: null
      packaging: null
    dependencies: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  lookupHints:
    likelyEntryClasses: []
    likelyFiles: []
    likelyDirectories: []
    stacktraceHotspots: []
    searchKeywords: []
    searchAntiPatterns: []
  persistenceHints:
    entities: []
    repositories: []
    schemas: []
    tables: []
    migrations: []
    datasourceNames: []
    hikariPools: []
  integrationHints:
    clientClasses: []
    listenerClasses: []
    publisherClasses: []
    endpointTemplates: []
    queues: []
    topics: []
    exchanges: []
    routingKeys: []
    configKeys: []
  evidence: []
  sourceCoverage:
    status: complete | partial | single-source | unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

Rules:

- module ids must be stable kebab-case and unique within the repository;
- do not list every package; add modules only when they have operational or search value;
- use `generated: true` when the source is generated;
- do not treat generated modules as semantic owners unless explicit evidence supports it;
- module responsibilities should be role-based and may differ from repository-level responsibilities;
- module `matchSignals` should be narrower than repository `matchSignals`.

## Code search scope schema

Top-level `codeSearchScopes` make multi-repository search explicit and order-independent.

Each code-search scope must use this structure and field order:

```yaml
- id: stable-code-search-scope-id
  name: Human readable scope name
  lifecycleStatus: active | planned | deprecated | retired | candidate | unknown
  target:
    systems: []
    runtimeComponents: []
    deploymentComponents: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
  useFor: []
  repositories: []
  packagePrefixes: []
  classHints: []
  endpointHints: []
  queueTopicHints: []
  databaseHints:
    datasourceNames: []
    hikariPools: []
    schemas: []
    tables: []
    entities: []
    migrations: []
  workflowHints:
    jobNames: []
    workflowNames: []
    definitionPaths: []
  searchStrategy:
    priorityOrder: []
    includeGeneratedClients: true
    includeSharedLibraries: true
    includeDeploymentConfig: false
    includeDocumentation: false
    notes: []
  limitations: []
  evidence: []
  sourceCoverage:
    status: complete | partial | single-source | unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

Repository references inside `repositories` should use objects:

```yaml
repositories:
  - repoId: crm-api-repo
    role: primary
    priority: 1
    include: true
    moduleIds: []
    reason: Main deployed service repository.
```

Allowed repository `role` values in a code search scope:

- `primary`
- `shared-library`
- `generated-client`
- `integration-library`
- `data-access-library`
- `schema-repository`
- `deployment-config`
- `workflow-config`
- `frontend`
- `documentation`
- `test-support`
- `supporting`
- `unknown`

Rules:

- use code-search scopes when one runtime/component/process/integration/domain context requires searching several repositories together;
- prioritize main service repositories before libraries unless the evidence specifically points into a library;
- include generated clients when generated DTOs, exceptions or client classes may appear in logs or stack traces;
- include shared libraries when package prefixes or dependencies show that they are packaged with the runtime component;
- include deployment/config repositories when runtime identity, environment mapping, feature flags or deployment values are necessary for analysis;
- include documentation repositories only when they contain operational context needed for search/explanation;
- do not include every repository in the organization; keep scopes focused;
- if a repository is likely part of a scope but not confirmed, do not include it as confirmed. Record a temporary pending join in `BUILD MEMORY`, or a durable `gap` only after expected sources have been scanned.

## Gap schema

Top-level `gaps` are durable catalog-level unresolved knowledge. They are not temporary memory for agents.

Every gap must use this structure and field order:

```yaml
- id: stable-gap-id
  type: repository-identity | module-boundary | code-search-scope | deterministic-mapping | responsibility | cross-file-reference | runtime-mapping | dependency | db-grounding | topology | validation | split-merge | other
  severity: low | medium | high
  status: open | resolved | superseded
  affectedNodes: []
  description: Clear unresolved catalog gap.
  impact: []
  neededEvidence: []
  suggestedSources: []
  evidence: []
```

Rules:

- use stable kebab-case ids;
- `affectedNodes` should use graph-style references such as `repository:crm-api-repo`, `module:crm-api-repo/customer-profile-module`, `system:crm-api`, `code-search-scope:crm-api-runtime-code`, `integration:crm-api-to-billing`;
- `impact` should explain why the gap matters for mapping, code search, grounding, analysis, routing or LLM tool answers;
- `neededEvidence` should state what evidence would resolve the gap;
- `suggestedSources` should list concrete expected sources, such as `CODEOWNERS`, `README.md`, `pom.xml`, `application.yml`, `deployment manifests`, `systems.yml`, `teams.yml`;
- do not add a gap just because the currently analyzed repository does not contain a value;
- do not add repo-local scratchpad questions to final `gaps`;
- use final gaps only when the issue remains catalog-level and durable.

## Cross-file consistency

Before returning YAML, validate references against available operational context files.

Rules:

- reference only stable ids when known;
- do not invent systems, runtime components, repositories, modules, processes, integrations, bounded contexts, terms or teams only to make the graph complete;
- if a reference is supported by evidence but the target node is missing and can be created in the same build cycle, use the expected id consistently;
- if the missing target cannot be created now, use a durable `gap` only when expected sources were checked or human input is required;
- otherwise keep the unresolved reference in `BUILD MEMORY` if available;
- keep `repo-map.yml` focused on repositories, modules and code-search scopes. Do not copy full process, integration, bounded-context or team definitions into this file.

## Build memory rules

If `BUILD MEMORY` is provided, read it before editing.

Use `BUILD MEMORY` to:

- resolve pending repository candidates;
- connect facts discovered in different repository scans;
- avoid duplicate temporary questions;
- track repositories already scanned and expected repositories not scanned yet;
- track pending joins between service code, workflows, shared libraries, generated clients, schema repositories, integrations and deployment config;
- avoid turning scan-order uncertainty into final catalog gaps.

Do not output `BUILD MEMORY` in `repo-map.yml`.

Do not treat `BUILD MEMORY` as final operational truth. Promote build-memory facts to `repo-map.yml` only when concrete evidence supports them.

Temporary uncertainty should stay in build memory when it may be resolved by scanning another known source. Examples:

- current repo contains a generated client but the consuming service repo has not been scanned;
- current repo contains an internal dependency but the library repository identity is unknown;
- current repo contains a process/event name but the process catalog has not been scanned;
- current repo contains a runtime deployment marker but deployment config repository has not been scanned;
- current repo contains DB entity names but migrations live in another repository not yet scanned.

Promote a temporary question to final `gaps` only when:

- all expected sources were scanned and the fact is still unresolved;
- the missing information requires human/team/domain input;
- the uncertainty affects deterministic mapping, code search scope, impact analysis, DB/code grounding, semantic interpretation or incident triage.

## Cross-repo join keys

When a repository/module fact is incomplete, reason using join keys.

Useful join keys include:

- GitLab group/project/project path;
- repository URL or alias;
- Maven `groupId`, `artifactId` and module name;
- Gradle project name;
- npm package name;
- package prefix;
- class/interface/client/listener/controller/entity/config name;
- generated client artifact;
- OpenAPI/AsyncAPI/WSDL/proto schema name;
- `spring.application.name` or equivalent;
- service/deployment/container/image name;
- endpoint prefix or template;
- base URL property;
- queue/topic/exchange/routing key;
- datasource/schema/table/entity/repository name;
- event name;
- workflow/process/job name;
- local term or alias from glossary/terms.

Do not create a final edge unless it is supported by at least one strong signal or two independent medium signals.

## Merge rules

Updates are additive by default.

Allowed operations:

- add new repositories, modules, runtime mappings, code-search scopes, signals, responsibilities, references, hints, evidence and gaps;
- enrich existing entries with new evidence-backed facts;
- merge duplicate entries when they clearly refer to the same repository/module/scope;
- split an entry only when evidence shows materially different repository identities, module boundaries or code-search scopes;
- update confidence or coverage when new evidence changes it;
- mark old entries as `deprecated`, `retired` or `superseded` when supported by evidence.

Disallowed operations unless explicitly supported by evidence:

- delete confirmed repositories, modules or code-search scopes because they are not visible in the current scan;
- remove confirmed signals, references or responsibilities;
- replace role-based responsibility with a single owner;
- downgrade existing facts because the current source is partial;
- merge service repositories with shared libraries because they share package names;
- treat generated clients or libraries as owners of consuming runtime behavior;
- create broad code-search scopes that include unrelated repositories;
- turn temporary scan-order uncertainty into final gaps.

### Merge duplicates safely

Two repository entries may be merged when at least one of these is true:

- same GitLab project path;
- same repository URL;
- same stable artifact coordinates and evidence says they are the same repository;
- same existing id from cross-file references;
- human-provided evidence explicitly says they are aliases.

Do not merge only because entries share a generic name, package prefix, team, business term or runtime label.

### Split entries safely

Split one entry when evidence shows that it represents materially different repositories, modules or scopes, such as:

- service repository and generated client repository were collapsed;
- library repository and consuming runtime repository were collapsed;
- monorepo contains distinct modules with different runtime/code-search scope or responsibilities;
- deployment/config repository was collapsed with application code repository;
- two GitLab project paths were mixed under one id.

When splitting, preserve old ids if already referenced unless the parent prompt explicitly allows id cleanup. If an old id must be retired, add a relation/hint or durable gap so references can be corrected.

## Privacy and security

Never include:

- secrets;
- credentials;
- tokens;
- private keys;
- authorization headers;
- connection strings with secrets;
- raw production customer records;
- personal data;
- full payload samples containing sensitive data;
- production-only sensitive configuration values;
- internal URLs that are not needed for deterministic mapping.

Allowed safe representations:

- configuration key names without values;
- host patterns when operationally necessary and not sensitive;
- path templates without real identifiers;
- schema/table/entity names;
- repository/project paths when already part of operational evidence;
- redacted or generalized values such as `customer-api.base-url` instead of an actual secret-bearing URL.

When in doubt, keep the key name and omit the value.

## YAML style rules

- Use YAML, not JSON.
- Use spaces only, never TAB characters.
- Use 2 spaces per indentation level.
- Keep top-level field order exactly as specified.
- Keep entry field order exactly as shown in the schema.
- Prefer block sequences over flow sequences for long lists.
- Quote values containing `{}`, `:`, `#`, `[]`, commas, leading zeros, leading `*`, leading `&`, wildcard characters or path parameters.
- Quote endpoint paths with path parameters, for example `"/api/customers/{customerId}"`.
- Quote glob-like paths when needed, for example `"src/main/java/com/example/**"`.
- Use empty lists `[]` for absent lists.
- Use `null` for unknown scalar values.
- Do not use comments in the final YAML.
- The final YAML must parse successfully.

## Quality gates

Before returning the YAML, verify:

1. The top-level wrapper is exactly `schemaVersion`, `catalogKind`, `repositories`, `codeSearchScopes`, `gaps`.
2. `catalogKind` is exactly `operational-context-repository-map`.
3. Every repository id is stable kebab-case and unique.
4. Every module id is stable kebab-case and unique within its repository.
5. Every code-search scope id is stable kebab-case and unique.
6. Every repository has a useful `repositoryType`, `lifecycleStatus`, `purpose`, `useFor`, `matchSignals`, `evidence` and `sourceCoverage`.
7. Every service repository has runtime identity or deployment match keys when available.
8. Every library/shared/generated-client repository has package, class, dependency, schema or generated-client match keys when available.
9. Multi-repository code-search scope is represented through top-level `codeSearchScopes`, not hidden only in prose.
10. Repository-local code-search relationships are also represented in `repositories[].codeSearch` where useful.
11. Responsibilities are role-based and do not invent a single owner.
12. Cross-file links use stable ids and do not invent entities.
13. Runtime evidence, deployment evidence, GitLab evidence, build evidence and source-layout evidence are not conflated.
14. Generated clients and shared libraries are not treated as owners of consuming behavior unless explicit evidence supports it.
15. Repo-local uncertainty is not stored as persistent `gaps`.
16. Final `gaps` are durable, typed and analysis-relevant.
17. No secrets, credentials, tokens, personal data, raw production records or sensitive config values are included.
18. The file remains compact enough to be used as a queryable catalog.
19. The YAML parses successfully.

## Example

A correctly filled `repo-map.yml` can look like this generic CRM example. The example is illustrative and must not be copied as real facts unless the analyzed source actually matches it.

```yaml
schemaVersion: 1
catalogKind: operational-context-repository-map

repositories:
  - id: crm-api-repo
    name: CRM API Repository
    repositoryType: service
    lifecycleStatus: active
    criticality: high
    git:
      provider: gitlab
      group: example/crm
      project: crm-api
      projectPath: example/crm/crm-api
      defaultBranch: main
      url: null
      aliases:
        - crm-core
        - crm-api
      inferred: false
    purpose: Hosts the CRM API service and customer, lead and opportunity modules used by the deployed CRM runtime.
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - change-analysis
      - db-grounding
      - incident-analysis
      - qa
    responsibilityStatus: explicit-multiple
    responsibilities:
      - teamId: crm-platform-team
        role: repo-maintainer
        scope: repository
        evidence: codeowners
        confidence: high
        source: CODEOWNERS
      - teamId: customer-experience-team
        role: domain-steward
        scope: customer-profile-module
        evidence: documentation
        confidence: high
        source: docs/ownership.md
    references:
      systems:
        - crm-api
      runtimeComponents:
        - crm-api-runtime
      deploymentComponents:
        - crm-api
      repositories:
        - crm-shared-kernel-repo
        - crm-marketing-client-repo
      processes:
        - customer-onboarding
        - lead-qualification
      boundedContexts:
        - customer-profile
        - lead-management
      integrations:
        - crm-api-to-marketing-platform
        - crm-api-customer-events
      terms:
        - customer-profile
        - lead-score
      teams:
        - crm-platform-team
        - customer-experience-team
      handoffRules: []
    sourceLayout:
      repositoryRoot: .
      buildTool: maven
      buildFiles:
        - pom.xml
      sourceRoots:
        - src/main/java
      testRoots:
        - src/test/java
      resourceRoots:
        - src/main/resources
      modulePaths:
        - customer-profile
        - lead-management
        - marketing-integration
      generatedSourcePaths:
        - target/generated-sources/openapi
      importantPaths:
        - src/main/java/com/example/crm/customer
        - src/main/java/com/example/crm/lead
      configurationFiles:
        - src/main/resources/application.yml
      deploymentFiles:
        - helm/crm-api/values.yaml
      databaseMigrationPaths:
        - src/main/resources/db/migration
      workflowDefinitionPaths: []
      documentationPaths:
        - README.md
        - docs/ownership.md
    modules:
      - id: customer-profile-module
        name: Customer Profile Module
        moduleType: domain-module
        lifecycleStatus: active
        path: src/main/java/com/example/crm/customer
        purpose: Manages customer profile data used by CRM workflows.
        references:
          systems:
            - crm-api
          runtimeComponents:
            - crm-api-runtime
          processes:
            - customer-onboarding
          boundedContexts:
            - customer-profile
          integrations:
            - crm-api-customer-events
          terms:
            - customer-profile
          teams:
            - customer-experience-team
        responsibilities:
          - teamId: customer-experience-team
            role: domain-steward
            scope: customer-profile-module
            evidence: documentation
            confidence: high
            source: docs/ownership.md
        source:
          paths:
            - src/main/java/com/example/crm/customer
          packages:
            - com.example.crm.customer
          generated: false
          build:
            groupId: com.example.crm
            artifactId: crm-api
            packaging: jar
          dependencies: []
        matchSignals:
          exact: {}
          strong:
            packagePrefixes:
              - com.example.crm.customer
            tables:
              - customer_profile
          medium:
            classHints:
              - CustomerProfileService
              - CustomerProfileEntity
            eventNames:
              - CustomerProfileUpdated
          weak:
            terms:
              - customer-profile
        lookupHints:
          likelyEntryClasses:
            - CustomerProfileController
          likelyFiles:
            - src/main/java/com/example/crm/customer/CustomerProfileService.java
          likelyDirectories:
            - src/main/java/com/example/crm/customer
          stacktraceHotspots:
            - CustomerProfileService
          searchKeywords:
            - CustomerProfileUpdated
          searchAntiPatterns:
            - generic customer DTOs in generated clients are not the domain module
        persistenceHints:
          entities:
            - CustomerProfileEntity
          repositories:
            - CustomerProfileRepository
          schemas:
            - crm
          tables:
            - customer_profile
          migrations:
            - src/main/resources/db/migration
          datasourceNames:
            - crmDatasource
          hikariPools:
            - HikariPool-crm
        integrationHints:
          clientClasses: []
          listenerClasses: []
          publisherClasses:
            - CustomerProfileEventPublisher
          endpointTemplates:
            - "/api/customers/{customerId}/profile"
          queues: []
          topics:
            - crm.customer.profile.events
          exchanges: []
          routingKeys: []
          configKeys: []
        evidence:
          - sourceType: code
            source: repo:src/main/java/com/example/crm/customer/CustomerProfileService.java
            detail: CustomerProfileService implements customer profile behavior.
            supports:
              - modules.customer-profile-module
            confidence: high
        sourceCoverage:
          status: partial
          scannedSources:
            - repo:crm-api
          expectedSources:
            - docs/ownership.md
          limitations:
            - Downstream event consumers were not scanned.
    runtimeMappings:
      - runtimeComponent: crm-api-runtime
        system: crm-api
        environment: null
        deploymentNames:
          - crm-api
        serviceNames:
          - crm-api
        applicationNames:
          - crm-api
        containerNames:
          - crm-api
        imageNames:
          - registry.example.com/crm/crm-api
        artifactNames:
          - crm-api.jar
        modules:
          - customer-profile-module
          - lead-management-module
        evidence:
          - sourceType: config
            source: repo:src/main/resources/application.yml
            detail: spring.application.name=crm-api
            supports:
              - runtimeMappings
            confidence: high
        confidence: high
    codeSearch:
      scopeRole: primary
      searchTogetherWith:
        - crm-shared-kernel-repo
        - crm-marketing-client-repo
      consumedBySystems: []
      consumedByRuntimeComponents: []
      consumedByRepositories: []
      consumesRepositories:
        - crm-shared-kernel-repo
        - crm-marketing-client-repo
      exportedPackages:
        - com.example.crm.customer
        - com.example.crm.lead
      importedPackageHints:
        - com.example.crm.shared
        - com.example.marketing.client
      dependencyCoordinates:
        - com.example.crm:crm-shared-kernel
      generatedClientCoordinates:
        - com.example.marketing:marketing-openapi-client
      schemaCoordinates: []
      notes:
        - Search shared kernel and generated marketing client when stack traces include shared customer or marketing classes.
    matchSignals:
      exact:
        projectNames:
          - crm-api
        projectPaths:
          - example/crm/crm-api
        repositoryUrls: []
        artifactIds:
          - crm-api
        groupIds:
          - com.example.crm
        serviceNames:
          - crm-api
        deploymentNames:
          - crm-api
        containerNames:
          - crm-api
        imageNames:
          - registry.example.com/crm/crm-api
      strong:
        moduleNames:
          - customer-profile
          - lead-management
          - marketing-integration
        packagePrefixes:
          - com.example.crm.customer
          - com.example.crm.lead
        sourceRoots:
          - src/main/java
        endpointPrefixes:
          - /api/customers
          - /api/leads
        endpointTemplates:
          - "/api/customers/{customerId}/profile"
        queues: []
        topics:
          - crm.customer.profile.events
        exchanges: []
        routingKeys: []
        datasourceNames:
          - crmDatasource
        hikariPools:
          - HikariPool-crm
        schemas:
          - crm
        tables:
          - customer_profile
          - lead
        configPrefixes:
          - crm.marketing
        buildCoordinates:
          - com.example.crm:crm-api
      medium:
        classHints:
          - CustomerProfileController
          - LeadQualificationService
          - MarketingPlatformClient
        interfaceHints: []
        enumHints:
          - LeadStatus
        annotationHints: []
        methodHints: []
        beanNames: []
        exceptionClasses:
          - CustomerProfileNotFoundException
        eventNames:
          - CustomerProfileUpdated
          - LeadQualified
        schemaNames: []
        jobNames: []
        workflowNames: []
        spanNames:
          - crm.customer.profile.update
        metricNames:
          - crm_customer_profile_update_total
        logMarkers:
          - CUSTOMER_PROFILE_UPDATED
      weak:
        terms:
          - customer-profile
          - lead-score
        labels:
          - CRM Core
        readmeKeywords:
          - customer profile
          - lead qualification
    lookupHints:
      likelyEntryClasses:
        - CrmApiApplication
      likelyControllerClasses:
        - CustomerProfileController
        - LeadController
      likelyServiceClasses:
        - CustomerProfileService
        - LeadQualificationService
      likelyClientClasses:
        - MarketingPlatformClient
      likelyListenerClasses: []
      likelyPublisherClasses:
        - CustomerProfileEventPublisher
      likelyRepositoryClasses:
        - CustomerProfileRepository
      likelyEntityClasses:
        - CustomerProfileEntity
      likelyConfigClasses:
        - MarketingClientConfiguration
      likelyGeneratedClientClasses:
        - MarketingApiClient
      likelyWorkflowClasses: []
      likelyFiles:
        - src/main/java/com/example/crm/customer/CustomerProfileService.java
        - src/main/java/com/example/crm/lead/LeadQualificationService.java
      likelyDirectories:
        - src/main/java/com/example/crm/customer
        - src/main/java/com/example/crm/lead
      stacktraceHotspots:
        - CustomerProfileService
        - MarketingPlatformClient
      searchKeywords:
        - CustomerProfileUpdated
        - LeadQualified
      searchAntiPatterns:
        - Do not stop at generated MarketingApiClient; inspect consuming service code as well.
    analysisHints:
      deterministicMapping:
        - Prefer exact projectPath and spring.application.name before README keywords.
      codeSearch:
        - Search crm-shared-kernel-repo and crm-marketing-client-repo with this repository for customer or marketing stack traces.
      functionDescription:
        - Explain customer profile and lead flows in business language before listing implementation classes.
      impactAnalysis:
        - Changes to customer profile module may affect onboarding and customer event consumers.
      changeAnalysis:
        - Check generated marketing client compatibility when marketing API dependency changes.
      dependencyAnalysis:
        - Shared customer model dependency is used by customer and lead modules.
      dbGrounding:
        - Map customer_profile table through CustomerProfileEntity and migrations.
      incidentAnalysis:
        - For marketing timeout stack traces, distinguish generated client failures from CRM business logic failures.
      qa:
        - Use customer profile tests and lead qualification tests for behavioral checks.
    handoffHints:
      defaultRoute:
        - role: first-responder
          target: crm-platform-team
          condition: Runtime evidence points to crm-api service code.
      requiredEvidence:
        - correlationId logs
        - stacktrace class
        - resolved deployment component
      firstActions:
        - Resolve stacktrace class before contacting generated client maintainers.
      escalationTriggers:
        - Evidence points to message broker, DB platform or external marketing API.
    llmToolHints:
      preferredWhen:
        - Evidence mentions crm-api runtime, CRM customer endpoints or customer profile stacktrace classes.
      avoidWhen:
        - Evidence mentions only generic marketing generated client classes without CRM consuming context.
      explanationHints:
        - Describe this as the main CRM backend service repository.
      disambiguationHints:
        - Distinguish CRM customer account from identity account terms.
      commonMisreads:
        - Generated marketing client classes do not mean marketing team owns CRM behavior.
      usefulForQuestions:
        - Which repository implements customer onboarding?
        - Which code should be searched for CustomerProfileService stack traces?
    evidence:
      - sourceType: build
        source: repo:pom.xml
        detail: artifactId=crm-api and groupId=com.example.crm
        supports:
          - git.project
          - matchSignals.exact.artifactIds
        confidence: high
      - sourceType: config
        source: repo:src/main/resources/application.yml
        detail: spring.application.name=crm-api
        supports:
          - runtimeMappings
          - matchSignals.exact.serviceNames
        confidence: high
      - sourceType: doc
        source: repo:docs/ownership.md
        detail: crm-platform-team maintains CRM API repository; customer-experience-team stewards customer profile module.
        supports:
          - responsibilities
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - repo:crm-api@main
        - repo:docs/ownership.md
      expectedSources:
        - crm-shared-kernel-repo
        - crm-marketing-client-repo
        - deployment manifests
      limitations:
        - Downstream event consumers were not scanned in this pass.

  - id: crm-shared-kernel-repo
    name: CRM Shared Kernel Repository
    repositoryType: shared-library
    lifecycleStatus: active
    criticality: medium
    git:
      provider: gitlab
      group: example/crm
      project: crm-shared-kernel
      projectPath: example/crm/crm-shared-kernel
      defaultBranch: main
      url: null
      aliases:
        - crm-shared
      inferred: false
    purpose: Provides shared customer model classes used by CRM services.
    useFor:
      - deterministic-mapping
      - code-search
      - dependency-analysis
      - function-description
    responsibilityStatus: explicit-single
    responsibilities:
      - teamId: crm-platform-team
        role: repo-maintainer
        scope: repository
        evidence: codeowners
        confidence: high
        source: CODEOWNERS
    references:
      systems:
        - crm-api
      runtimeComponents:
        - crm-api-runtime
      deploymentComponents: []
      repositories:
        - crm-api-repo
      processes:
        - customer-onboarding
      boundedContexts:
        - customer-profile
      integrations: []
      terms:
        - customer-profile
      teams:
        - crm-platform-team
      handoffRules: []
    sourceLayout:
      repositoryRoot: .
      buildTool: maven
      buildFiles:
        - pom.xml
      sourceRoots:
        - src/main/java
      testRoots:
        - src/test/java
      resourceRoots: []
      modulePaths: []
      generatedSourcePaths: []
      importantPaths:
        - src/main/java/com/example/crm/shared/customer
      configurationFiles: []
      deploymentFiles: []
      databaseMigrationPaths: []
      workflowDefinitionPaths: []
      documentationPaths:
        - README.md
    modules: []
    runtimeMappings: []
    codeSearch:
      scopeRole: shared-library
      searchTogetherWith:
        - crm-api-repo
      consumedBySystems:
        - crm-api
      consumedByRuntimeComponents:
        - crm-api-runtime
      consumedByRepositories:
        - crm-api-repo
      consumesRepositories: []
      exportedPackages:
        - com.example.crm.shared.customer
      importedPackageHints: []
      dependencyCoordinates:
        - com.example.crm:crm-shared-kernel
      generatedClientCoordinates: []
      schemaCoordinates: []
      notes:
        - Search with crm-api-repo when stack traces include shared customer model classes.
    matchSignals:
      exact:
        projectNames:
          - crm-shared-kernel
        projectPaths:
          - example/crm/crm-shared-kernel
        repositoryUrls: []
        artifactIds:
          - crm-shared-kernel
        groupIds:
          - com.example.crm
      strong:
        packagePrefixes:
          - com.example.crm.shared.customer
        buildCoordinates:
          - com.example.crm:crm-shared-kernel
      medium:
        classHints:
          - CustomerRef
          - CustomerSnapshot
      weak:
        terms:
          - customer-profile
    lookupHints:
      likelyEntryClasses: []
      likelyControllerClasses: []
      likelyServiceClasses: []
      likelyClientClasses: []
      likelyListenerClasses: []
      likelyPublisherClasses: []
      likelyRepositoryClasses: []
      likelyEntityClasses: []
      likelyConfigClasses: []
      likelyGeneratedClientClasses: []
      likelyWorkflowClasses: []
      likelyFiles:
        - src/main/java/com/example/crm/shared/customer/CustomerSnapshot.java
      likelyDirectories:
        - src/main/java/com/example/crm/shared/customer
      stacktraceHotspots:
        - CustomerSnapshot
      searchKeywords:
        - CustomerSnapshot
      searchAntiPatterns:
        - Do not treat shared model class as process owner.
    analysisHints:
      deterministicMapping:
        - Shared package prefix maps to this library, but consuming behavior belongs to service repositories.
      codeSearch:
        - Search with consuming repositories when analyzing runtime behavior.
      functionDescription: []
      impactAnalysis:
        - Shared model changes may affect CRM API and generated event payloads.
      changeAnalysis: []
      dependencyAnalysis:
        - Check consumers before changing shared customer classes.
      dbGrounding: []
      incidentAnalysis:
        - Shared model stack traces require follow-up lookup in consuming service context.
      qa: []
    handoffHints:
      defaultRoute: []
      requiredEvidence: []
      firstActions:
        - Identify consuming runtime component before assigning ownership.
      escalationTriggers: []
    llmToolHints:
      preferredWhen:
        - Stack trace class is under com.example.crm.shared.customer.
      avoidWhen:
        - User asks who owns CRM runtime behavior; inspect consuming service first.
      explanationHints:
        - Explain this as a shared model library, not a deployed service.
      disambiguationHints: []
      commonMisreads:
        - Library maintainer is not automatically business behavior owner.
      usefulForQuestions:
        - Which library contains CustomerSnapshot?
    evidence:
      - sourceType: build
        source: repo:pom.xml
        detail: artifactId=crm-shared-kernel
        supports:
          - matchSignals.exact.artifactIds
        confidence: high
    sourceCoverage:
      status: single-source
      scannedSources:
        - repo:crm-shared-kernel@main
      expectedSources:
        - consuming service repositories
      limitations:
        - Consumers are represented only where dependency evidence is available.

codeSearchScopes:
  - id: crm-api-runtime-code
    name: CRM API Runtime Code Search Scope
    lifecycleStatus: active
    target:
      systems:
        - crm-api
      runtimeComponents:
        - crm-api-runtime
      deploymentComponents:
        - crm-api
      processes:
        - customer-onboarding
        - lead-qualification
      boundedContexts:
        - customer-profile
        - lead-management
      integrations:
        - crm-api-to-marketing-platform
      terms:
        - customer-profile
        - lead-score
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - db-grounding
      - incident-analysis
    repositories:
      - repoId: crm-api-repo
        role: primary
        priority: 1
        include: true
        moduleIds:
          - customer-profile-module
          - lead-management-module
        reason: Main deployed CRM API service repository.
      - repoId: crm-shared-kernel-repo
        role: shared-library
        priority: 2
        include: true
        moduleIds: []
        reason: Shared customer model classes can appear in CRM API stack traces.
      - repoId: crm-marketing-client-repo
        role: generated-client
        priority: 3
        include: true
        moduleIds: []
        reason: Generated marketing client classes appear in outbound marketing calls.
    packagePrefixes:
      - com.example.crm
      - com.example.crm.shared
      - com.example.marketing.client
    classHints:
      - CustomerProfileService
      - CustomerSnapshot
      - MarketingApiClient
    endpointHints:
      - /api/customers
      - /api/leads
    queueTopicHints:
      - crm.customer.profile.events
    databaseHints:
      datasourceNames:
        - crmDatasource
      hikariPools:
        - HikariPool-crm
      schemas:
        - crm
      tables:
        - customer_profile
      entities:
        - CustomerProfileEntity
      migrations:
        - src/main/resources/db/migration
    workflowHints:
      jobNames: []
      workflowNames: []
      definitionPaths: []
    searchStrategy:
      priorityOrder:
        - crm-api-repo
        - crm-shared-kernel-repo
        - crm-marketing-client-repo
      includeGeneratedClients: true
      includeSharedLibraries: true
      includeDeploymentConfig: false
      includeDocumentation: false
      notes:
        - Start with main repo for business behavior; inspect generated clients only for contract or error mapping.
    limitations:
      - Deployment config repository was not scanned.
    evidence:
      - sourceType: build
        source: repo:pom.xml
        detail: CRM API depends on crm-shared-kernel and marketing-openapi-client.
        supports:
          - codeSearchScopes.crm-api-runtime-code.repositories
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - repo:crm-api@main
        - repo:crm-shared-kernel@main
      expectedSources:
        - repo:crm-marketing-client@main
        - deployment manifests
      limitations:
        - Generated marketing client repository identity is assumed from dependency coordinate and should be confirmed.

gaps:
  - id: confirm-crm-marketing-client-repo-identity
    type: repository-identity
    severity: medium
    status: open
    affectedNodes:
      - code-search-scope:crm-api-runtime-code
      - repository:crm-api-repo
    description: CRM API depends on a generated marketing client, but the GitLab repository identity for that client has not been confirmed after scanning available CRM API sources.
    impact:
      - GitLab code search for marketing client exceptions may miss generated classes.
      - Integration impact analysis may not include generated client code.
    neededEvidence:
      - GitLab project path for marketing generated client.
      - Generated client build metadata or repository README.
    suggestedSources:
      - crm-api pom.xml dependency management
      - GitLab search for artifactId marketing-openapi-client
      - Marketing integration documentation
    evidence:
      - sourceType: build
        source: repo:pom.xml
        detail: Dependency coordinate com.example.marketing:marketing-openapi-client is present, but repository path is not confirmed.
        supports:
          - gaps.confirm-crm-marketing-client-repo-identity
        confidence: medium
```
