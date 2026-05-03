# systems.yml update prompt

Update only `systems.yml`.

This prompt is schema-authoritative for `systems.yml`. If a parent operational-context builder prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy, merge policy, quality gate and output contract for `systems.yml`.

Do not preserve legacy fields or legacy structures unless they are explicitly represented in this schema.

## Purpose

Maintain `systems.yml` as an enterprise-grade, evidence-backed, queryable system and runtime-component map for a reusable operational context catalog.

`systems.yml` is not a simple application inventory, not a CMDB, and not an incident-routing file. It is the deterministic mapping layer that connects runtime, deployment, log, telemetry, code, repository, integration, data, documentation and domain evidence to logical systems and deployed runtime components.

The file supports:

- deterministic mapping from runtime, deployment, log, telemetry, code, configuration, documentation and domain evidence to logical systems and runtime components;
- mapping from runtime components to GitLab/code-search scope through `repo-map.yml`;
- focused LLM tool usage, especially GitLab search, source resolve, outline, flow context, class references and small file/chunk reads;
- function description and user-facing explanations of affected systems, components, capabilities, dependencies and behavior;
- impact analysis and change analysis across systems, runtime components, repositories, modules, integrations, processes, bounded contexts, terms and teams;
- DB/code grounding when a datasource, table, entity, schema, repository, migration, connection pool or persistence symptom appears in evidence;
- dependency analysis across upstream systems, downstream systems, gateways, brokers, platform services, data stores, security systems and external systems;
- incident analysis and triage as one downstream view, without reducing responsibility to a single owner or turning the file into a routing matrix;
- onboarding, Q&A and future AI analysis features beyond incident tracking;
- identification of missing runtime identity, system boundaries, code-search scope, topology, dependency, responsibility or documentation without inventing facts.

A system or runtime-component entry should explain:

- what logical operational capability or deployed artifact is being described;
- whether it is internal, external, platform, data-store, message-broker, gateway, workflow, security, observability or unknown;
- where it appears in logs, deployment context, telemetry, source code, configuration, repositories and documentation;
- which runtime components implement which logical systems;
- which repositories, modules, shared libraries, generated clients and deployment/config sources belong to its code-search scope;
- which processes, bounded contexts, integrations, data stores, terms, teams and external parties it connects to;
- which deterministic signals identify it and how strong those signals are;
- which runtime behavior, interfaces, dependencies, topology, observability and failure modes matter for analysis;
- which responsibilities are explicit, shared, inferred, worker-only, disputed or unknown;
- how an LLM should recognize, explain, search, disambiguate and avoid over-routing the system/component;
- what remains unknown after durable validation.

## Non-goals

Do not turn `systems.yml` into:

- a full architecture document;
- a complete CMDB;
- a complete Kubernetes, Helm, Terraform, Docker, serverless or deployment inventory;
- a full API catalog;
- a full message schema catalog;
- a full database schema catalog;
- a full repository map;
- a full dependency lockfile;
- a full ownership matrix;
- an incident escalation playbook;
- a runbook;
- a scratchpad for temporary agent uncertainty.

Do not add:

- every Java interface, DTO, helper, utility, enum, method or package;
- every endpoint as a separate system;
- every queue/topic/exchange as a separate system unless it is an operationally addressable broker or platform system;
- every database table as a separate system unless the data store or dataset is an operationally addressable system;
- every repository module as a separate system;
- every bounded context as a system when there is no distinct operational actor or runtime capability;
- every integration contract as a system;
- every team, vendor or external party as a system;
- every code-only library, generated client or shared module as a system unless it is also explicitly deployed or operated as a runtime capability;
- pure architecture prose without deterministic signals;
- temporary scan-order uncertainty as final `gaps`;
- secrets, credentials, tokens, personal data, private contact details, full production payloads or sensitive business records.

Use:

- `integrations.yml` for contracts between systems;
- `repo-map.yml` for repositories, modules, source layout and code-search scopes;
- `bounded-contexts.yml` for semantic/domain boundaries;
- `processes.yml` for business, operational and technical flows;
- `teams.yml` for responsibility actors and responsibility relations;
- `glossary.md` or `terms.yml` for local vocabulary;
- incident-specific handoff/routing views for escalation rules and on-call behavior.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `systems.yml`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, build reports, documentation fragments, runtime evidence, deployment/config evidence, telemetry discovery, database discovery, existing operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, branch, commit, generated client, shared library, deployment/config repository, documentation fragment, runtime environment, deployment manifest, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `repo-map.yml`, `processes.yml`, `integrations.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` or `terms.yml`, `handoff-rules.md` and `operational-context-index.md`.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate runtime components, candidate systems, unresolved references, expected repositories/config sources and sources not yet scanned. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

If some inputs are missing, do the best possible update using available evidence. Do not invent missing facts.

## Output

Return the full updated `systems.yml` YAML only.

Do not include Markdown fences.
Do not include explanations.
Do not include diffs.
Do not include partial snippets.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `systems.yml`.
Do not output `BUILD MEMORY` inside `systems.yml`.

The final YAML must parse successfully.

## Required top-level schema

Use this exact top-level shape. Do not preserve legacy top-level fields.

```yaml
schemaVersion: 1
catalogKind: operational-context-system-map
systems: []
runtimeComponents: []
gaps: []
```

Top-level order must be:

1. `schemaVersion`
2. `catalogKind`
3. `systems`
4. `runtimeComponents`
5. `gaps`

Use `gaps`, not legacy untyped `openQuestions`. Durable unresolved questions are represented as typed gaps.

## Core principles

### 1. Systems map is an operational graph, not an application list

Treat `systems.yml` as part of the operational graph.

The graph connects:

- logical systems;
- deployed runtime components;
- repositories, modules, shared libraries, generated clients, integration libraries, schema repositories and deployment/config sources;
- deterministic recognition signals;
- code-search scopes;
- bounded contexts and glossary terms;
- business, operational and technical processes;
- integration contracts;
- data stores and DB/code grounding hints;
- upstream, downstream, mediated, platform, security and observability dependencies;
- responsibility roles and evidence;
- durable knowledge gaps.

A deployed runtime component may map to several logical systems. A logical system may have no deployed runtime component in the current organization. A runtime component may draw code from several repositories. Do not force one-to-one mappings between systems, components and repositories.

### 2. Separate logical systems from runtime components

Use this distinction consistently.

A `system` is a logical operational actor, capability or externally visible system that appears in flows, integrations, runtime evidence, impact analysis or user-facing explanation.

Examples:

- internal application system;
- external SaaS or partner system;
- operationally relevant data store;
- message broker or platform service;
- API gateway;
- workflow engine;
- scheduled-processing system;
- observability-visible service;
- security/identity provider;
- legacy system accessed by one or more components.

A `runtimeComponent` is a deployed or deployable technical artifact that can be recognized from logs, deployment context, telemetry, process lists, Kubernetes/Docker metadata, artifacts, jobs, workers, functions or service names.

Examples:

- Spring Boot service;
- frontend/web application;
- worker or consumer process;
- scheduled job application;
- API gateway deployment;
- workflow server;
- Docker container;
- Kubernetes deployment;
- serverless function;
- batch processor;
- database instance;
- packaged artifact using shared libraries.

A logical system may be external and have no internal runtime component. A runtime component may implement several systems, contexts or processes. Keep the mapping explicit through `runtimeComponents[].systemIds` and `systems[].references.runtimeComponents`.

### 3. Runtime component code-search scope is first-class

Runtime evidence often points to an artifact, class, package, generated DTO, exception or stacktrace line rather than a logical system.

A runtime component's code may be distributed across:

- the main service repository;
- a shared domain or technical library;
- a shared module inside a monorepo;
- a generated client repository;
- an integration adapter library;
- a schema/message-contract repository;
- a deployment/config repository;
- a workflow/config repository;
- a frontend repository;
- a test-support repository used in diagnostics.

Represent this explicitly through `runtimeComponents[].codeSearchScope`. Do not list only the main service repository when stacktraces or packaged code can point into shared libraries, generated clients or integration modules.

### 4. Responsibilities are role-based, not single ownership

Do not collapse multi-team responsibility into one `ownerTeamId` equivalent.

A system or runtime component may have:

- runtime operators;
- domain stewards;
- repository maintainers;
- module stewards;
- integration contract stewards;
- producer teams;
- consumer teams;
- platform support;
- data owners;
- security owners;
- QA owners;
- support contacts;
- external owners;
- workers or contributors without ownership;
- unknown, inferred, shared, disputed or not-applicable responsibility.

If a source only shows that a team contributes, consumes, calls, operates a worker, appears in a ticket, has a commit author, or participates in a workflow, do not turn that into accountable ownership.

### 5. Routing is only a downstream view

Do not model incident routing as the core truth of a system.

Use `analysisHints.incidentAnalysis`, `routingHints`, `handoffHints` and cross-file references only for reusable hints. Detailed escalation, on-call rules and routing overrides belong to incident-specific handoff views, not to this file.

### 6. Deterministic signals are more important than prose

Prefer concrete, searchable signals over long descriptions:

- service, application, deployment, container, namespace, artifact, image and process names;
- GitLab group, project, project path, repository id/name and build coordinates;
- package prefixes and source roots;
- class/interface/enum/annotation/controller/client/listener/publisher/repository/entity/config names;
- endpoint prefixes and templates;
- queue/topic/exchange/routing-key/channel/binding names;
- event/message/schema names;
- datasource names, Hikari pool names, database schemas/tables/entities and migration paths;
- workflow, job, lock, scheduler and worker names;
- log markers, exception classes, error codes, span names, metric names and alert labels;
- service discovery names, hosts, gateway route labels and ingress routes;
- local terms and aliases from glossary/terms.

Descriptions help humans and LLMs interpret facts, but deterministic signals make the file usable as an index.

### 7. Multi-repository scans are partial by default

The current repository, documentation fragment, telemetry sample or deployment manifest is only a partial evidence source.

System and runtime facts may be split across:

- service repositories;
- shared libraries;
- generated clients;
- integration libraries;
- workflow/config repositories;
- deployment/config repositories;
- schema/message repositories;
- frontend repositories;
- infrastructure repositories;
- upstream or downstream service repositories;
- external vendor documentation;
- product, operations or team-owned documentation;
- runtime observability systems.

Never infer that a system, runtime component, dependency, relation, repository, module, integration, process, bounded context, team, signal or responsibility does not exist only because it is absent from the current source.

Do not remove, downgrade or overwrite existing confirmed catalog facts only because they are not visible in the current scan.

Temporary scan-order uncertainty must go to `BUILD MEMORY`, not to final `gaps`, when it may be resolved by scanning another known repository, generated client, shared library, deployment/config source, telemetry source or documentation fragment.

### 8. Shared library evidence is not runtime ownership

A shared library, generated client or integration module may contain a failing class without owning the deployed runtime behavior.

When the current source is a shared library, generated client or integration library:

- capture package prefixes, classes, module names, Maven/Gradle coordinates and exported signals;
- link the library to runtime components only when consuming evidence exists;
- do not assume the library repository owns or operates the consuming runtime component;
- record consuming systems/components/processes/contexts as pending cross-repo joins in `BUILD MEMORY` unless explicitly known.

When the current source is a consuming service or deployed component:

- capture application names, service names, controllers, listeners, clients, jobs, persistence markers, deployment metadata and configuration keys;
- link to existing shared library/generated client entries when evidence supports the join;
- do not invent library repository identity if it is not known.

## Required discovery procedure

Before editing, inspect the provided repository, documentation, deployment manifest, build report, runtime evidence or telemetry sample and classify candidate facts.

### Discover systems and runtime components from

Runtime identity:

- `spring.application.name`;
- service names;
- application names;
- container names;
- deployment names;
- namespace names;
- image names;
- artifact names;
- executable JAR/WAR names;
- process names;
- Kubernetes manifests;
- Helm charts;
- Docker Compose;
- Dockerfile;
- environment-specific aliases;
- application labels and tags;
- CI/CD deployment metadata;
- service discovery names;
- gateway route labels;
- ingress routes.

Code and build identity:

- Git remote URL;
- GitLab group, project and project path;
- repository name;
- branch and commit when available;
- `pom.xml`, parent POM, Maven modules, `groupId`, `artifactId`, packaging;
- `build.gradle`, `settings.gradle`, Gradle subprojects;
- npm/yarn/pnpm package names;
- generated client modules;
- shared libraries and integration libraries;
- dependency declarations pointing to other internal libraries;
- artifact coordinates and image tags.

Application configuration:

- `application.yml`, `application.properties` and profile-specific configs;
- config property prefixes;
- base URLs and host properties;
- datasource properties;
- messaging bindings;
- security/JWT/OAuth/OIDC/Keycloak config names;
- feature flags and runtime toggles;
- scheduled job config;
- workflow/process config;
- gateway routes;
- health and management endpoints.

HTTP APIs and clients:

- `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`;
- GraphQL resolvers;
- OpenAPI/Swagger files;
- gateway route config;
- Feign clients;
- WebClient/RestTemplate clients;
- SOAP clients and WSDL references;
- gRPC stubs;
- generated API clients;
- endpoint prefixes;
- host/base URL properties.

Messaging:

- Rabbit/Kafka listeners;
- Spring Cloud Stream bindings;
- StreamBridge usage;
- queue names;
- topic names;
- exchange names;
- routing keys;
- channel names;
- consumer groups;
- DLQ names;
- event classes and schema names;
- AsyncAPI specs.

Persistence and DB/code grounding:

- datasource config;
- Hikari pool names;
- schema names;
- table names;
- JPA entities;
- repositories/DAOs;
- migrations;
- database-specific packages;
- read models;
- audit tables;
- data-access markers;
- secondary datasources;
- cross-system data access.

Jobs and workflows:

- `@Scheduled`;
- ShedLock names;
- Quartz/batch jobs;
- workflow definitions;
- BPMN/process definitions;
- Camunda/Temporal/Conductor/Airflow process names;
- job names;
- lock names;
- async workers and consumers;
- batch processors;
- workflow server deployments.

Observability and runtime diagnostics:

- log markers;
- exception classes;
- error codes;
- span names;
- metric names;
- alert labels;
- health indicators;
- tracing/correlation fields;
- MDC keys;
- audit markers;
- common incident symptoms;
- runtime tags or entity names from observability tools;
- dashboards and runbook references.

Ownership, responsibility and support evidence:

- README;
- architecture docs;
- CODEOWNERS;
- team ownership matrices;
- support/on-call docs;
- runbooks;
- ticket queues;
- team labels;
- deployment ownership docs;
- product/domain responsibility docs;
- support matrix;
- platform responsibility docs.

### Strong indicators of a system

Create or update a `systems[]` entry when at least one strong indicator exists:

- explicit application/system name in documentation, runtime, flows, integrations or topology;
- stable external system or platform capability consumed by several flows or components;
- stable data store, broker, gateway, workflow engine, identity provider or observability system visible in operations;
- logical actor that participates in processes or integrations and is useful for user-facing explanation;
- repeated runtime/deployment evidence pointing to a named operational capability;
- canonical cross-file reference from processes, integrations, bounded contexts, teams or repo map;
- external SaaS or partner system with operationally relevant failure or impact behavior.

### Strong indicators of a runtime component

Create or update a `runtimeComponents[]` entry when at least one strong indicator exists:

- explicit deployed service/application/deployment/container/job/function name;
- `spring.application.name` or equivalent runtime identity;
- artifact/image/process name used in deployment or logs;
- Kubernetes/Helm/Docker/CI/CD metadata naming the deployed component;
- telemetry/log/trace/metric service name;
- scheduled job, worker, consumer or batch application deployment;
- API gateway, workflow server, broker component or database instance deployed/operated as a component;
- code-search scope that can be mapped to an operationally addressable deployed artifact.

### Weak indicators are not enough alone

Do not create a system or runtime component from only:

- one generic class name;
- one isolated endpoint;
- one generic DTO, enum or helper;
- a technical folder such as `common`, `util`, `config`, `security`, `client` or `adapter`;
- one shared library package with no consuming runtime evidence;
- one host name without contract or topology context;
- one database table with no operational system meaning;
- a team name without runtime/system evidence;
- a single ticket or commit author;
- a vague product noun without runtime, flow or integration evidence.

If evidence is promising but incomplete, record a candidate in `BUILD MEMORY` when possible. Create a final `candidate` entry only when it is useful for deterministic mapping and has at least one evidence object.

## What belongs in systems.yml

Add or update entries that help answer at least one of these questions:

- Which logical system or runtime component does this evidence point to?
- Which deployed artifact produced or consumed this runtime signal?
- Which logical systems are implemented by a runtime component?
- Which repositories, modules, shared libraries and generated clients should be searched when this system/component is matched?
- Which bounded contexts, processes, integrations, terms and teams are connected to this system/component?
- Which systems are upstream, downstream, called, consumed, hosted, mediated, authenticated, persisted to, routed through or depended on?
- Which teams have explicit, shared, inferred, scoped, worker-only, disputed or unknown responsibility?
- What signals allow deterministic matching from logs, stacktraces, deployment context, telemetry, GitLab, DB/code evidence or user questions?
- What operator-facing or LLM-facing hints help explain or disambiguate this system/component?
- What durable system-level or runtime-component-level gaps remain after available sources were scanned?

## Schema

The full output must be valid YAML with this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-system-map
systems: []
runtimeComponents: []
gaps: []
```

Use only the fields defined below.

### `systems[]`

Each logical system entry must use this structure and field order:

```yaml
- id: string
  name: string
  kind: internal-application | external-system | platform-service | data-store | message-broker | api-gateway | workflow-engine | observability-system | security-system | generated-client-target | shared-runtime | legacy-system | unknown
  lifecycleStatus: active | candidate | deprecated | retired | planned | unknown
  operationalStatus: production | non-production | planned | retired | unknown
  criticality: critical | high | medium | low | unknown
  purpose: string
  aliases: []
  useFor: []
  responsibilityStatus: explicit | shared | inferred | worker-only | disputed | external | not-applicable | unknown
  capabilities:
    businessCapabilities: []
    technicalCapabilities: []
    exposedFunctions: []
    internalFunctions: []
    excludedCapabilities: []
    notes: []
  references:
    runtimeComponents: []
    repositories: []
    modules: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
    externalParties: []
    dataStores: []
    handoffRules: []
  responsibilities: []
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
  relations: []
  runtimeBehavior:
    inbound: []
    outbound: []
    persistence: []
    messaging: []
    scheduling: []
    security: []
  interfaces:
    http:
      inboundEndpointPrefixes: []
      inboundEndpointTemplates: []
      outboundEndpointPrefixes: []
      outboundEndpointTemplates: []
      publicApiLabels: []
      controllerClasses: []
      clientClasses: []
    messaging:
      inboundQueues: []
      inboundExchanges: []
      inboundTopics: []
      inboundRoutingKeys: []
      inboundEventNames: []
      outboundQueues: []
      outboundExchanges: []
      outboundTopics: []
      outboundRoutingKeys: []
      outboundEventNames: []
      listenerClasses: []
      publisherClasses: []
    batchAndJobs:
      scheduledJobNames: []
      lockNames: []
      workflowNames: []
      workerNames: []
      triggerEvents: []
      jobClasses: []
    persistence:
      dataStoreIds: []
      databaseType: unknown
      databaseSchemas: []
      databaseTables: []
      hikariPools: []
      entityClasses: []
      repositoryClasses: []
      migrationPaths: []
    observability:
      logIndexes: []
      traceServiceNames: []
      spanNames: []
      dashboardRefs: []
      alertLabels: []
      metricNames: []
  dependencies:
    internalSystems: []
    externalSystems: []
    integrationGateways: []
    platformServices: []
    messageBrokers: []
    dataStores: []
    securityServices: []
    observabilityServices: []
    notes: []
  deploymentAndTopology:
    deploymentModel: unknown
    environments: []
    regions: []
    clusters: []
    namespaces: []
    serviceDiscoveryNames: []
    gateways: []
    ingressRoutes: []
    networkZones: []
    notes: []
  observability:
    logIndexes: []
    dashboards: []
    alertLabels: []
    runbookRefs: []
    sliOrSloNames: []
    traceServiceNames: []
    metricNames: []
    healthCheckSignals: []
  failureModes: []
  routingHints: []
  handoffHints:
    defaultRouteLabel: null
    firstResponderTeamIds: []
    escalationTeamIds: []
    partnerTeamIds: []
    platformSupportTeamIds: []
    externalRouteLabels: []
    requiredEvidence: []
    preferredEvidence: []
    expectedFirstActions: []
    whenToRouteHere: []
    whenToInvolveAsPartner: []
    whenNotToRouteHere: []
    fallbackIfAmbiguous: null
    notes: []
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
    disambiguationHints: []
    commonMisreads: []
  evidence: []
  sourceCoverage:
    status: complete | partial | unknown
    scannedSources: []
    expectedSources: []
    missingSources: []
    notes: []
```

#### Required fields for systems

Every confirmed system entry must have:

- `id`;
- `name`;
- `kind`;
- `lifecycleStatus`;
- `operationalStatus`;
- `criticality`;
- `purpose`;
- `useFor`;
- `responsibilityStatus`;
- `capabilities`;
- `references`;
- `matchSignals`;
- `relations`;
- `runtimeBehavior`;
- `interfaces`;
- `dependencies`;
- `observability`;
- `analysisHints`;
- `llmToolHints`;
- `evidence`;
- `sourceCoverage`.

A candidate system may have less evidence, but it must be marked with `lifecycleStatus: candidate`, `sourceCoverage.status: partial`, and at least one evidence object.

### `runtimeComponents[]`

Each runtime component entry must use this structure and field order:

```yaml
- id: string
  name: string
  kind: spring-boot-service | web-application | frontend-application | worker | scheduled-job | batch-application | api-gateway | workflow-server | message-consumer | message-producer | serverless-function | database-instance | platform-component | external-runtime | unknown
  lifecycleStatus: active | candidate | deprecated | retired | planned | unknown
  operationalStatus: production | non-production | planned | retired | unknown
  criticality: critical | high | medium | low | unknown
  purpose: string
  aliases: []
  useFor: []
  systemIds: []
  responsibilityStatus: explicit | shared | inferred | worker-only | disputed | external | not-applicable | unknown
  deployment:
    serviceNames: []
    applicationNames: []
    containerNames: []
    deploymentNames: []
    namespaceNames: []
    imageNames: []
    artifactNames: []
    processNames: []
    ports: []
    environmentHints: []
    runtimePlatforms: []
    healthEndpoints: []
    managementEndpoints: []
    configKeys: []
    notes: []
  codeSearchScope:
    repositories: []
    packagePrefixes: []
    classHints: []
    configPrefixes: []
    generatedClients: []
    sharedLibraries: []
    searchTogetherWithRuntimeComponents: []
    searchNotes: []
  references:
    repositories: []
    modules: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
    externalParties: []
    dataStores: []
    handoffRules: []
  responsibilities: []
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
  relations: []
  runtimeBehavior:
    inbound: []
    outbound: []
    persistence: []
    messaging: []
    scheduling: []
    security: []
  interfaces:
    http:
      inboundEndpointPrefixes: []
      inboundEndpointTemplates: []
      outboundEndpointPrefixes: []
      outboundEndpointTemplates: []
      publicApiLabels: []
      controllerClasses: []
      clientClasses: []
    messaging:
      inboundQueues: []
      inboundExchanges: []
      inboundTopics: []
      inboundRoutingKeys: []
      inboundEventNames: []
      outboundQueues: []
      outboundExchanges: []
      outboundTopics: []
      outboundRoutingKeys: []
      outboundEventNames: []
      listenerClasses: []
      publisherClasses: []
    batchAndJobs:
      scheduledJobNames: []
      lockNames: []
      workflowNames: []
      workerNames: []
      triggerEvents: []
      jobClasses: []
    persistence:
      dataStoreIds: []
      databaseType: unknown
      databaseSchemas: []
      databaseTables: []
      hikariPools: []
      entityClasses: []
      repositoryClasses: []
      migrationPaths: []
    observability:
      logIndexes: []
      traceServiceNames: []
      spanNames: []
      dashboardRefs: []
      alertLabels: []
      metricNames: []
  dependencies:
    internalSystems: []
    externalSystems: []
    integrationGateways: []
    platformServices: []
    messageBrokers: []
    dataStores: []
    securityServices: []
    observabilityServices: []
    notes: []
  deploymentAndTopology:
    deploymentModel: unknown
    environments: []
    regions: []
    clusters: []
    namespaces: []
    serviceDiscoveryNames: []
    gateways: []
    ingressRoutes: []
    networkZones: []
    notes: []
  observability:
    logIndexes: []
    dashboards: []
    alertLabels: []
    runbookRefs: []
    sliOrSloNames: []
    traceServiceNames: []
    metricNames: []
    healthCheckSignals: []
  failureModes: []
  routingHints: []
  handoffHints:
    defaultRouteLabel: null
    firstResponderTeamIds: []
    escalationTeamIds: []
    partnerTeamIds: []
    platformSupportTeamIds: []
    externalRouteLabels: []
    requiredEvidence: []
    preferredEvidence: []
    expectedFirstActions: []
    whenToRouteHere: []
    whenToInvolveAsPartner: []
    whenNotToRouteHere: []
    fallbackIfAmbiguous: null
    notes: []
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
    disambiguationHints: []
    commonMisreads: []
  evidence: []
  sourceCoverage:
    status: complete | partial | unknown
    scannedSources: []
    expectedSources: []
    missingSources: []
    notes: []
```

#### Required fields for runtime components

Every confirmed runtime component must have:

- `id`;
- `name`;
- `kind`;
- `lifecycleStatus`;
- `operationalStatus`;
- `criticality`;
- `purpose`;
- `useFor`;
- `systemIds`;
- `deployment`;
- `codeSearchScope`;
- `references`;
- `matchSignals`;
- `relations`;
- `runtimeBehavior`;
- `interfaces`;
- `dependencies`;
- `observability`;
- `analysisHints`;
- `llmToolHints`;
- `evidence`;
- `sourceCoverage`.

A runtime component that is only inferred from partial logs, telemetry or docs may be `candidate`, but it must not be used as a confirmed deterministic mapping target unless supported by exact or strong evidence.

## Allowed values

### `useFor`

Use only these values in `useFor` arrays:

```yaml
- deterministic-mapping
- code-search
- function-description
- impact-analysis
- db-grounding
- incident-analysis
- qa
```

Include only values supported by the entry. For runtime components that expose code-search scope, include at least `deterministic-mapping` and `code-search`.

### System `kind`

Use the most specific supported kind:

- `internal-application`: logical internal application or product system.
- `external-system`: external SaaS, partner system, vendor system or externally owned business system.
- `platform-service`: shared platform capability such as feature flags, object storage, secrets, runtime platform or scheduler platform.
- `data-store`: operationally relevant database, data warehouse, data lake, replicated data source or datastore.
- `message-broker`: Kafka/Rabbit/JMS/event-bus/broker system.
- `api-gateway`: gateway, ingress, proxy, API management layer or mediated routing system.
- `workflow-engine`: workflow/process orchestration engine.
- `observability-system`: logging, tracing, monitoring, alerting or APM system.
- `security-system`: identity provider, auth gateway, key-management or security-control system.
- `generated-client-target`: external/API target represented primarily because generated clients or schemas point to it.
- `shared-runtime`: shared runtime capability operated as a deployed runtime but used by multiple systems.
- `legacy-system`: legacy operational system whose boundary is known but implementation may not be repository-local.
- `unknown`: observed operational actor whose type is not yet clear.

### Runtime component `kind`

Use the most specific supported kind:

- `spring-boot-service`: deployed Spring Boot service or application.
- `web-application`: backend web application not specifically Spring Boot.
- `frontend-application`: deployed UI/frontend application.
- `worker`: worker process that consumes, executes or polls work.
- `scheduled-job`: scheduled job runtime.
- `batch-application`: batch application or batch processor.
- `api-gateway`: deployed gateway/proxy component.
- `workflow-server`: deployed workflow engine/server.
- `message-consumer`: consumer component or listener application.
- `message-producer`: producer component where producer identity is operationally important.
- `serverless-function`: deployed function.
- `database-instance`: deployed database instance or schema endpoint when operationally addressable.
- `platform-component`: deployed platform component.
- `external-runtime`: runtime component operated outside the main organization but visible in evidence.
- `unknown`: runtime artifact observed but not yet classifiable.

### `lifecycleStatus`

Use:

- `active`: current and used.
- `candidate`: referenced or inferred but not fully confirmed.
- `planned`: documented as planned but not active.
- `deprecated`: still appears but should be replaced or no longer primary.
- `retired`: no longer active, kept only because evidence can still refer to it.
- `unknown`: status cannot be confirmed.

### `operationalStatus`

Use:

- `production`: production or production-like operation is explicitly supported by evidence.
- `non-production`: known to be dev/test/stage-only.
- `planned`: planned but not yet deployed.
- `retired`: retired operationally.
- `unknown`: operational state is not known.

### `criticality`

Use:

- `critical`
- `high`
- `medium`
- `low`
- `unknown`

Use documented business or operational criticality only. Do not infer high criticality only because a system exists or appears in logs.

### `responsibilityStatus`

Use:

- `explicit`: evidence clearly states responsibility for the listed areas.
- `shared`: evidence clearly shows more than one team/actor participates in responsibility for important areas.
- `inferred`: strong operational evidence exists, but no explicit ownership document was found.
- `worker-only`: evidence shows participation, workers, contributors, implementers or commit authors, but no ownership/responsibility evidence.
- `disputed`: sources conflict or responsibility changed without clear resolution.
- `external`: external party owns or operates it.
- `not-applicable`: responsibility is not meaningful for this entry.
- `unknown`: the entry exists, but no reliable responsibility mapping is available.

### `deploymentAndTopology.deploymentModel`

Use:

- `kubernetes`
- `docker`
- `serverless`
- `vm`
- `bare-metal`
- `managed-service`
- `external-saas`
- `hybrid`
- `unknown`

### `interfaces.persistence.databaseType`

Use:

- `postgresql`
- `mysql`
- `mariadb`
- `oracle`
- `sqlserver`
- `mongodb`
- `redis`
- `elasticsearch`
- `dynamodb`
- `snowflake`
- `bigquery`
- `mixed`
- `unknown`

## Responsibility model

A responsibility item represents a specific relationship between a team or external party and a system/runtime graph node.

Use this shape inside `systems[].responsibilities`, `runtimeComponents[].responsibilities`, and, where appropriate, `routingHints[].partnerResponsibilities`:

```yaml
- actorType: team | external-party | unknown
  actorId: string
  targetType: system | runtime-component | repository | repository-module | process | process-step | bounded-context | integration | integration-side | data-store | message-broker | platform-capability | external-party
  targetId: string
  role: runtime-operator | domain-steward | repo-maintainer | module-steward | integration-contract-steward | producer | consumer | platform-support | business-owner | data-owner | security-owner | qa-owner | support-contact | worker | contributor | external-owner | unknown
  scope: string
  side: source | target | consumer | producer | intermediary | platform | not-applicable | unknown
  status: confirmed | candidate | inferred | disputed | unknown
  confidence: high | medium | low
  evidence: []
```

### Responsibility semantics

Use these meanings consistently:

- `runtime-operator`: responsible for running or operating a deployed runtime component.
- `domain-steward`: responsible for local domain semantics or business rules.
- `repo-maintainer`: responsible for repository-level maintenance, build and shared conventions.
- `module-steward`: responsible for a concrete module, package area, generated client or library submodule.
- `integration-contract-steward`: responsible for the functional or technical contract of an integration.
- `producer`: owns the producing side of an event, API, data feed, message or contract.
- `consumer`: owns the consuming side of an event, API, data feed, message or contract.
- `platform-support`: owns shared infrastructure or platform capability, not necessarily application behavior.
- `business-owner`: owns business outcome or policy, not necessarily code.
- `data-owner`: owns schemas, data quality, table semantics or data access model.
- `security-owner`: owns authentication, authorization, secret handling or security controls.
- `qa-owner`: owns test strategy or acceptance quality for an area.
- `support-contact`: known first contact, but not necessarily owner.
- `worker` or `contributor`: evidence shows participation, but not ownership.
- `external-owner`: known non-internal owner/operator.
- `unknown`: the relationship exists but the role is not known.

Do not convert `worker`, `contributor`, commit author, ticket assignee, one-off documentation mention or package-name hint into `domain-steward`, `repo-maintainer` or `runtime-operator` without explicit evidence.

## Match signal model

`matchSignals` helps deterministic mapping and retrieval. It must stay queryable and concrete.

Use the same broad signal categories in `exact`, `strong`, `medium` and `weak` where relevant:

```yaml
matchSignals:
  exact:
    serviceNames: []
    applicationNames: []
    containerNames: []
    deploymentNames: []
    namespaceNames: []
    artifactNames: []
    imageNames: []
    processNames: []
    projectPaths: []
    repositoryIds: []
    endpointTemplates: []
    hosts: []
    serviceDiscoveryNames: []
    queueNames: []
    exchangeNames: []
    topicNames: []
    routingKeys: []
    eventNames: []
    schemaNames: []
    databaseSchemas: []
    databaseTables: []
    hikariPools: []
    configKeys: []
    logMarkers: []
    exceptionClasses: []
    traceSpanNames: []
    jobNames: []
    workflowNames: []
    metricNames: []
    alertLabels: []
  strong: {}
  medium: {}
  weak: {}
```

### `exact`

Use `exact` for signals that can identify the system/component with little ambiguity.

Examples:

- exact service/application/deployment/container names;
- exact `spring.application.name`;
- exact GitLab project path for the component's main repository;
- exact artifact/image/process name;
- exact endpoint template that belongs to only this component;
- exact queue/topic/exchange/routing key unique to this component;
- exact Hikari pool/schema/table marker unique to this system/component;
- exact telemetry service name;
- exact workflow/job name unique to this component;
- exact log marker or exception class specific to this system/component.

### `strong`

Use `strong` for signals that usually identify the system/component but may require corroboration.

Examples:

- package prefixes;
- project/repository names without full path;
- endpoint prefixes;
- controller/client/listener/repository/entity class groups;
- event names shared within a domain area;
- config prefixes;
- service discovery aliases;
- module names;
- common runtime aliases.

### `medium`

Use `medium` for supporting signals useful with other evidence.

Examples:

- business/domain labels;
- glossary aliases;
- dashboard names;
- runbook labels;
- metric families;
- non-unique package fragments;
- high-level capability labels;
- common data objects.

### `weak`

Use `weak` for hints that should not determine a mapping alone.

Examples:

- broad product words;
- old aliases;
- team labels;
- generic endpoints;
- generic exception names;
- generic database table names;
- ambiguous acronyms;
- natural-language documentation labels.

Weak signals must not be used as the sole basis for ownership, routing or confirmed deterministic mapping.

## Recognition model

Use `recognition` for LLM-friendly disambiguation and false-positive control.

```yaml
recognition:
  strongSignals: []
  weakSignals: []
  falsePositiveSignals: []
  disambiguationRules: []
```

Rules:

- `strongSignals` summarize the most reliable matching evidence in human-readable form.
- `weakSignals` summarize useful but insufficient hints.
- `falsePositiveSignals` list evidence that may look related but should not map here.
- `disambiguationRules` explain how to choose between neighboring systems/components.

Do not duplicate every `matchSignals` value. Use `recognition` to help a model avoid misclassification.

## Runtime behavior objects

Use compact objects inside `runtimeBehavior` arrays. Keep them stable and evidence-backed.

### Inbound behavior

```yaml
- type: http | messaging | webhook | scheduled-trigger | workflow-task | file | database | ui | unknown
  sourceSystemIds: []
  sourceRuntimeComponentIds: []
  integrationIds: []
  endpointPrefixes: []
  endpointTemplates: []
  queues: []
  topics: []
  exchanges: []
  routingKeys: []
  events: []
  triggerNames: []
  notes: []
  evidence: []
```

### Outbound behavior

```yaml
- type: http | messaging | webhook | file | database | email | sms | security | unknown
  targetSystemIds: []
  targetRuntimeComponentIds: []
  integrationIds: []
  endpointPrefixes: []
  endpointTemplates: []
  hosts: []
  queues: []
  topics: []
  exchanges: []
  routingKeys: []
  events: []
  configKeys: []
  notes: []
  evidence: []
```

### Persistence behavior

```yaml
- dataStoreId: string
  databaseType: postgresql | mysql | mariadb | oracle | sqlserver | mongodb | redis | elasticsearch | dynamodb | snowflake | bigquery | mixed | unknown
  datasourceNames: []
  hikariPools: []
  schemas: []
  tables: []
  entityClasses: []
  repositoryClasses: []
  migrationPaths: []
  accessPattern: read | write | read-write | migration | unknown
  notes: []
  evidence: []
```

### Messaging behavior

```yaml
- brokerSystemId: string
  role: producer | consumer | producer-consumer | broker | unknown
  queues: []
  topics: []
  exchanges: []
  routingKeys: []
  channels: []
  consumerGroups: []
  dlqNames: []
  eventNames: []
  listenerClasses: []
  publisherClasses: []
  notes: []
  evidence: []
```

### Scheduling behavior

```yaml
- type: scheduled | batch | workflow | lock | cron | manual-trigger | unknown
  jobNames: []
  workflowNames: []
  lockNames: []
  cronHints: []
  triggerEvents: []
  notes: []
  evidence: []
```

### Security behavior

```yaml
- securitySystemId: string
  role: identity-provider | resource-server | client | token-validator | secrets-provider | authorization-service | unknown
  configKeys: []
  issuerHints: []
  audienceHints: []
  endpointHints: []
  notes: []
  evidence: []
```

## Relations

Use `relations` to represent meaningful operational graph edges.

```yaml
- targetType: system | runtime-component | repository | bounded-context | process | integration | data-store | message-broker | external-party
  targetId: string
  type: implements | implemented-by | deployed-as | depends-on | calls | called-by | consumes | produces | persists-to | reads-from | read-by | publishes-to | subscribes-to | receives-from | routes-through | authenticates-with | orchestrates | hosted-by | hosts | mirrors | shares-code-with | monitors | observed-by | unknown
  direction: outbound | inbound | bidirectional | not-applicable | unknown
  via: []
  confidence: high | medium | low
  evidence: []
```

Rules:

- Use `depends-on` only for meaningful operational dependency, not every library dependency.
- Use `shares-code-with` for shared libraries and generated clients in code-search scope.
- Use `persists-to`, `reads-from` or `read-by` for data stores that are operationally relevant.
- Use `routes-through` for gateways, brokers, mediators or service mesh capabilities.
- Use `authenticates-with` for identity/security systems.
- Use `calls`, `consumes`, `produces` with integration references when known.
- Do not duplicate every relation already fully represented in `integrations.yml`; keep system-level relation summaries useful for mapping and impact analysis.

## Code-search scope

Runtime components must expose code-search scope for downstream GitLab tools.

Use `runtimeComponents[].codeSearchScope.repositories` with this structure:

```yaml
- repoId: string
  role: main-service | frontend | shared-library | generated-client | integration-library | schema-repository | workflow-config | deployment-config | test-support | documentation | unknown
  moduleIds: []
  packagePrefixes: []
  classHints: []
  reason: string
  confidence: high | medium | low
```

Rules:

- Include all repositories that can contain code relevant to the deployed component.
- Prefer canonical repository IDs from `repo-map.yml`.
- Do not list only the main service repo when stacktraces or packaged code can point into shared libraries, generated clients, integration modules or schema repositories.
- If a shared library is referenced but not yet scanned, record the pending repo/module in `BUILD MEMORY`.
- Add final `gaps` only when all expected sources were scanned or human input is needed.
- Do not invent GitLab group/project/path. Use `repo-map.yml` or build memory pending references when unresolved.

## Failure mode schema

Use `failureModes` for focused, reusable patterns that improve deterministic matching, diagnosis preparation, impact analysis, DB/code grounding or coordination.

```yaml
- id: string
  name: string
  category: runtime | dependency | persistence | messaging | http | security | deployment | workflow | data-quality | external-system | unknown
  symptoms: []
  matchSignals:
    endpointPrefixes: []
    endpointTemplates: []
    queueNames: []
    topicNames: []
    exchangeNames: []
    routingKeys: []
    eventNames: []
    databaseSchemas: []
    databaseTables: []
    hikariPools: []
    logMarkers: []
    exceptionClasses: []
    errorCodes: []
    jobNames: []
    workflowNames: []
    traceSpanNames: []
    metricNames: []
  relatedProcesses: []
  relatedBoundedContexts: []
  relatedIntegrations: []
  relatedRepositories: []
  likelyFirstActions: []
  handoffHints:
    firstResponderTeamIds: []
    partnerTeamIds: []
    platformSupportTeamIds: []
    requiredEvidence: []
  notes: []
  evidence: []
```

Rules:

- Keep `failureModes` focused.
- Add a failure mode only when it improves deterministic matching, diagnosis preparation, impact analysis, DB/code grounding or coordination.
- Do not copy every exception class into a failure mode.
- Do not turn failure modes into a runbook or escalation matrix.

## Routing and handoff hints

Use `routingHints` and `handoffHints` only as reusable coordination hints.

`routingHints` items may use this structure:

```yaml
- condition: string
  candidateTeamIds: []
  candidateExternalPartyIds: []
  partnerResponsibilities: []
  requiredEvidence: []
  avoidWhen: []
  confidence: high | medium | low
  evidence: []
```

Rules:

- Routing hints are not ownership facts.
- A first responder is not necessarily a domain owner or runtime operator.
- Do not create routing hints from weak match signals alone.
- Do not include personal contact details or private on-call data.
- Prefer references to `teams.yml`, external party IDs, support queue labels or handoff-rule IDs.

## Analysis hints

Use `analysisHints` to help AI and deterministic tools choose how to use the entry.

```yaml
analysisHints:
  deterministicMapping: []
  codeSearch: []
  functionDescription: []
  impactAnalysis: []
  dbCodeGrounding: []
  incidentAnalysis: []
  qa: []
```

Guidelines:

- `deterministicMapping`: how to match logs, telemetry, deployment and code evidence to this entry.
- `codeSearch`: where to search first, which repositories/libraries/generated clients matter, which package/class hints are useful.
- `functionDescription`: how to explain the system/component's role in user-facing language.
- `impactAnalysis`: what upstream/downstream systems, processes, contexts, integrations or data stores matter.
- `dbCodeGrounding`: how DB markers map to code, entities, repositories or data stores.
- `incidentAnalysis`: compact hints useful for incident triage, not full routing rules.
- `qa`: what QA or validation questions this entry helps answer.

## LLM tool hints

Use `llmToolHints` for retrieval-time guidance when one entry is returned to a model.

```yaml
llmToolHints:
  preferredWhen: []
  avoidWhen: []
  explanationHints: []
  usefulForQuestions: []
  answerStyleHints: []
  disambiguationHints: []
  commonMisreads: []
```

Rules:

- `preferredWhen`: when this entry is likely the right context.
- `avoidWhen`: when similarly named evidence should not map here.
- `explanationHints`: how to describe the system/component without overclaiming.
- `usefulForQuestions`: question types that benefit from this entry.
- `answerStyleHints`: language-level guidance for LLM answers.
- `disambiguationHints`: how to distinguish neighboring systems/components.
- `commonMisreads`: recurring false interpretations.

Do not use `llmToolHints` to store facts that belong in `matchSignals`, `relations`, `interfaces`, `runtimeBehavior`, `evidence` or `sourceCoverage`.

## Evidence model

Every confirmed fact must be backed by evidence.

Evidence objects should use this structure:

```yaml
- sourceType: repository | build-file | config | deployment-manifest | code-symbol | annotation | log | telemetry | documentation | codeowners | ci-pipeline | operator-input | existing-catalog | build-memory | unknown
  source: string
  detail: string
  confidence: high | medium | low
```

Rules:

- Prefer file path + symbol/config key when available.
- Use runtime/deployment/telemetry evidence for runtime identity.
- Use repository/build/config evidence for code-search scope.
- Use ownership/support docs for responsibility.
- Do not include secrets, tokens, credentials, personal data, private contact details or sensitive production values.
- Do not cite a repo scan as evidence for absence unless the scan scope is explicit and complete.
- `BUILD MEMORY` may support promotion, but final facts still need concrete evidence.

## Source coverage

Use `sourceCoverage` to communicate how complete the evidence is.

```yaml
sourceCoverage:
  status: complete | partial | unknown
  scannedSources: []
  expectedSources: []
  missingSources: []
  notes: []
```

Rules:

- `complete` means all known expected sources for this entry were scanned for the facts being asserted.
- `partial` means the entry is useful but more sources are expected.
- `unknown` means the source scope is unclear.
- `missingSources` should list source categories or known repositories/docs, not speculative guesses.
- Do not downgrade existing source coverage only because the current scan is partial.
- Do not claim completeness without explicit scan scope.

## Gap model

Use `gaps` only for durable catalog gaps.

A gap should use this structure:

```yaml
- id: string
  type: missing-runtime-component | missing-code-search-scope | missing-system-boundary | missing-system-reference | missing-deployment-identity | missing-topology | missing-dependency | missing-observability | responsibility-ambiguity | topology-ambiguity | external-owner-unknown | source-coverage-gap | conflicting-signals | cross-file-reference-missing | requires-human-input | other
  severity: critical | high | medium | low
  status: open | in-review | blocked | partially-resolved | resolved | superseded
  affectedNodes: []
  question: string
  impact: []
  requiredToResolve: []
  suggestedSources: []
  evidence: []
```

Rules:

- Do not use `openQuestions`.
- Do not create final gaps for uncertainty that may be resolved by scanning another known repository, shared library, generated client repo, deployment/config repo, telemetry source or documentation fragment.
- Put scan-order uncertainty in `BUILD MEMORY` as `temporaryQuestions`, `pendingJoins`, `unresolvedReferences`, `candidateRuntimeComponents`, `candidateSystems`, `expectedRepositories` or `expectedSources`.
- Promote a temporary question to final `gaps` only after the expected sources were scanned or human/domain input is required.
- A gap must explain why the unresolved fact matters for mapping, code search, impact analysis, DB/code grounding, incident analysis, QA or LLM answers.

## Multi-repository and partial-source rules

The operational context catalog is built from multiple repositories, shared libraries, generated clients, deployment/config sources, telemetry sources, external system docs and documentation fragments.

The current source is only a partial evidence source.

### Absence rule

Do not treat absence of evidence in the current source as evidence that a fact does not exist.

Examples:

- Do not remove a runtime component because the current repository is a shared library.
- Do not remove a shared library from code-search scope because the current service repository does not contain its source.
- Do not remove an external system because the current repository only contains a generated client.
- Do not remove a dependency because the current telemetry sample does not show it.
- Do not mark responsibility unknown because the current code scan does not include ownership docs.

### Cross-repo build memory

When `BUILD MEMORY` is available, use it to remember:

- candidate systems;
- candidate runtime components;
- pending system-to-runtime mappings;
- pending runtime-to-repository mappings;
- pending shared-library or generated-client joins;
- unresolved external systems;
- unresolved deployment identities;
- unresolved code-search scope;
- expected repositories or documentation sources;
- evidence fragments that are not durable enough for final YAML yet;
- temporary questions caused by scan order.

Do not output `BUILD MEMORY` inside `systems.yml` unless the parent prompt explicitly asks for a separate sidecar output.

### Temporary vs final uncertainty

Temporary uncertainty belongs to `BUILD MEMORY` when:

- another known repository or config source has not been scanned yet;
- a generated client points to a target system but the target docs are expected later;
- a shared library package appears but the consuming runtime component is not yet known;
- deployment identity is visible but repository mapping is expected from `repo-map.yml`;
- a system appears in an integration but the related `systems.yml` entry will be created by another source.

Final `gaps` are appropriate when:

- all expected sources have been scanned and identity remains unresolved;
- human/domain input is required;
- responsibility evidence conflicts;
- runtime and documentation disagree about system/component identity;
- code-search scope cannot be completed because repository identity is missing;
- missing topology, dependency or external owner affects impact analysis or LLM answers.

### Partial facts

Keep partial facts when they improve mapping or analysis, but mark them clearly:

- use `lifecycleStatus: candidate` for tentative systems/components;
- use `sourceCoverage.status: partial`;
- use `confidence: low` or `medium` in evidence/responsibilities/relations;
- record limitations in `sourceCoverage.notes`;
- avoid promoting candidates to confirmed deterministic mapping targets without exact or strong signals.

### Cross-repo join keys

Use these keys to connect facts across scans:

- `spring.application.name`;
- service/deployment/container names;
- image/artifact names;
- GitLab project path;
- Maven/Gradle/npm coordinates;
- package prefixes;
- generated client package names;
- endpoint prefixes/templates;
- queue/topic/exchange/routing-key names;
- event/schema names;
- database schema/table/Hikari pool names;
- config prefixes and base URL keys;
- trace service names;
- metric names;
- runtime labels and tags;
- canonical IDs from other operational-context files.

## Merge and normalization rules

### Monotonic merge

Preserve confirmed existing facts unless explicit contradictory evidence exists.

Do not:

- delete confirmed systems or runtime components because they are not visible in the current scan;
- remove aliases, match signals, relations, references, responsibilities or code-search scope unless contradicted;
- lower confidence because a new partial scan lacks evidence;
- overwrite explicit responsibility with inferred responsibility;
- collapse multiple runtime components into one system without evidence;
- collapse multiple logical systems into one runtime component without evidence;
- replace local/system-specific purpose with generic architecture prose;
- convert unknowns into invented facts.

### Merge duplicates

Merge entries when they clearly represent the same canonical system or runtime component:

- same stable runtime name and same deployment identity;
- same `spring.application.name` and same service/deployment context;
- same GitLab project path plus same deployment artifact;
- same external system documented under aliases;
- same logical system referenced by multiple integrations/processes.

Keep separate entries when:

- one is a logical system and the other is a deployed component;
- two runtime components deploy separately, fail separately or have different code-search scopes;
- two logical systems have different external owners, semantic roles or impact behavior;
- a broker/gateway/platform mediator can fail independently from the business system;
- an external SaaS target is distinct from the generated client or internal gateway used to access it.

### Normalize IDs

Use stable kebab-case IDs.

System ID examples:

- `crm-core`
- `email-platform`
- `customer-data-store`
- `message-broker`
- `identity-provider`
- `billing-partner`

Runtime component ID examples:

- `crm-core-api-runtime`
- `crm-email-worker-runtime`
- `customer-sync-job-runtime`
- `crm-api-gateway-runtime`
- `billing-webhook-consumer-runtime`

Rules:

- Do not rename an existing ID unless it is clearly wrong and the update task explicitly allows cleanup.
- Prefer IDs that identify the local meaning, not only a technical artifact.
- Use `-runtime` or another clear suffix for runtime component IDs when needed to distinguish them from logical system IDs.
- If a duplicate exists and cannot be safely resolved, keep the best canonical ID and create a durable gap only if the ambiguity affects mapping or analysis.

## Cross-file reference rules

Use canonical IDs from other operational-context files when available.

References may point to:

- `repo-map.yml` repository IDs and module IDs;
- `processes.yml` process IDs;
- `integrations.yml` integration IDs;
- `bounded-contexts.yml` bounded context IDs;
- `teams.yml` team IDs and external party IDs;
- `glossary.md` or `terms.yml` term IDs;
- handoff/routing view IDs.

Do not introduce a reference to a repository, module, process, integration, bounded context, term, team, external party or handoff rule unless:

1. that ID already exists in the corresponding file; or
2. the evidence is strong enough and the current prompt is allowed to create a durable gap; or
3. the missing reference is recorded as a pending cross-repo reference in `BUILD MEMORY`.

When an ID is missing and the fact still matters, prefer a `gaps[]` entry over inventing the ID.

## System modelling rules

### Internal application systems

Use `kind: internal-application` for logical application systems operated inside the organization.

Rules:

- Link deployed artifacts through `references.runtimeComponents`.
- Link code through `references.repositories` and runtime component `codeSearchScope`.
- Do not duplicate every component as a separate system unless the component is also an independent logical actor.
- Use capabilities to summarize what the system does.

### External systems

Use `kind: external-system` for SaaS, partner, vendor or external business systems.

Rules:

- Create entries only when they are operationally relevant to integrations, impact, mapping or explanations.
- Do not invent external owner or support path.
- Store external ownership in `responsibilities` or `references.externalParties` only when evidence supports it.
- Record missing external owner as a durable gap only after available docs/configs were checked.

### Platform services

Use `kind: platform-service`, `message-broker`, `api-gateway`, `workflow-engine`, `observability-system` or `security-system` for shared capabilities.

Rules:

- Add only platform systems that can appear in runtime evidence, dependencies, impacts, integrations or diagnostics.
- Do not list every Kubernetes service or infrastructure object.
- Distinguish platform support responsibility from application behavior responsibility.

### Data stores

Use `kind: data-store` for operationally relevant stores.

Rules:

- Add data stores when they appear in datasource config, DB/code grounding, integrations, impact analysis or failure modes.
- Do not model every table as a system.
- Use `interfaces.persistence` and `runtimeBehavior.persistence` to capture schemas/tables/connection pools.
- Link to teams/data owners only when evidence supports it.

### Generated client targets

Use `kind: generated-client-target` sparingly when generated clients or schemas reveal a target system that is operationally relevant but not otherwise documented.

Rules:

- Mark as `candidate` unless target identity is confirmed.
- Link generated client repositories through `references.repositories` and runtime component code-search scope when relevant.
- Do not treat the generated client itself as a system unless it is deployed/operated as a runtime capability.

### Shared runtimes and legacy systems

Use `shared-runtime` or `legacy-system` when the system is operationally visible but boundary or implementation is unusual.

Rules:

- Document boundary limitations in `sourceCoverage.notes`.
- Use relations to indicate hosted subsystems or dependent components.
- Do not over-split a monolith or legacy system without evidence of independently analyzable sub-systems.

## Runtime component modelling rules

### Deployed services

Use runtime component entries for deployable artifacts that can produce logs, telemetry, metrics, traces, deployment context or stacktraces.

Rules:

- Capture `deployment` identity from runtime/config/deployment sources.
- Capture code-search scope from `repo-map.yml`, build files, package prefixes and source evidence.
- Link to one or more logical systems through `systemIds`.
- Use exact/strong match signals for runtime names and code signals.

### Workers, consumers and scheduled jobs

Use a separate runtime component when a worker/job/consumer:

- deploys separately;
- fails separately;
- has separate logs or telemetry;
- has separate code-search scope;
- has distinct operational behavior;
- participates in different processes or integrations.

Otherwise represent the worker/job in `runtimeBehavior.scheduling`, `interfaces.batchAndJobs` or `runtimeBehavior.messaging` of the parent component.

### Gateways, brokers and platform components

Create runtime components for platform/gateway/broker deployments only when they are operationally addressable and useful for mapping or diagnosis.

Do not list every internal pod/service when it has no independent analysis value.

### Database instances

Use `kind: database-instance` for deployed/operated database instances only when the instance itself is a runtime component in evidence. Otherwise use logical system `kind: data-store`, `interfaces.persistence` and DB grounding hints.

## Signal quality rules

- Every confirmed system or runtime component should have at least one exact or strong match signal.
- Candidate entries may have only medium/weak signals, but they must not be used as confirmed deterministic targets.
- Do not add generic names like `api`, `service`, `backend`, `database`, `kafka`, `worker`, `controller` as strong signals unless they are exact local names.
- Use normalized signal values, not long prose.
- Keep environment-specific values when they help mapping, but avoid sensitive values.
- Do not include secrets, credentials, tokens, customer identifiers or full payloads.
- Prefer stable identifiers over ephemeral runtime IDs.
- Do not duplicate large lists of metrics/classes/tables. Keep representative deterministic signals.

## Security and privacy rules

Never include:

- secrets;
- credentials;
- tokens;
- API keys;
- passwords;
- private keys;
- session IDs;
- personal email addresses or personal phone numbers;
- private contact details;
- personal customer data;
- full production payloads;
- sensitive business records;
- raw connection strings with credentials;
- internal values that the parent prompt marks as non-exportable.

When evidence contains sensitive values, store only sanitized names or structural hints:

- config key name, not config value;
- host pattern, not sensitive full URL if prohibited;
- schema/table name, not row data;
- token issuer label, not token;
- support queue label, not personal contact.

## YAML style rules

- Output valid YAML only.
- Use `schemaVersion: 1`.
- Use `catalogKind: operational-context-system-map`.
- Use only the top-level keys `schemaVersion`, `catalogKind`, `systems`, `runtimeComponents`, `gaps`.
- Keep top-level and entry field order stable.
- Use arrays for collections, even if empty.
- Use `null` for unknown scalar values.
- Use kebab-case IDs.
- Do not use comments in final YAML unless the parent prompt explicitly allows comments.
- Do not use Markdown fences.
- Do not include discovery reports or explanations.
- Keep entries compact but complete.
- Prefer structured fields over prose.
- Preserve stable IDs.

## Legacy migration rules

If the current file uses a legacy schema:

- migrate concrete, useful, well-supported facts into the new schema;
- move old `openQuestions` into typed `gaps` only when they are durable catalog gaps;
- migrate old `systemType`, `status`, `summary`, `analysisUseCases`, `match`, `provenance`, `links`, `responsibility`, `handoff` and `codeScope` into the new fields when evidence supports them;
- split entries into `systems[]` and `runtimeComponents[]` when legacy entries mixed logical system and deployed artifact facts;
- keep logical application capability in `systems[]`;
- keep service/deployment/container/artifact/job identity in `runtimeComponents[]`;
- do not preserve legacy top-level `openQuestions`;
- do not preserve legacy field names unless explicitly represented in this schema;
- do not create new facts merely to fill fields.

## Validation before final output

Before returning the updated file, validate:

1. Top-level keys are exactly `schemaVersion`, `catalogKind`, `systems`, `runtimeComponents`, `gaps`.
2. `schemaVersion` is `1`.
3. `catalogKind` is `operational-context-system-map`.
4. YAML parses successfully.
5. Every system has required fields.
6. Every runtime component has required fields.
7. Every confirmed entry has evidence.
8. Candidate entries are marked with `lifecycleStatus: candidate` and partial source coverage.
9. Logical systems and runtime components are not collapsed into one concept.
10. Runtime components have `systemIds` when known.
11. Runtime components expose `codeSearchScope`.
12. Match signals are structured as `exact`, `strong`, `medium`, `weak`.
13. Weak signals are not the sole basis for ownership, routing or confirmed deterministic mapping.
14. Responsibilities are role-based and evidence-backed.
15. External systems and external parties are not forced into internal team ownership.
16. Relations use canonical IDs where possible.
17. Cross-file references are valid, pending in build memory, or represented as durable gaps.
18. Temporary scan-order uncertainty is not in final `gaps`.
19. Durable gaps have type, severity, status, affected nodes, question, impact and suggested sources.
20. No secrets, credentials, tokens, personal data, private contact details or sensitive payloads are present.
21. The file remains an index, not an architecture essay, CMDB, runbook, endpoint dump or ownership matrix.

## Correctly filled example

This example is intentionally small. It shows shape and modeling style, not a complete enterprise catalog.

```yaml
schemaVersion: 1
catalogKind: operational-context-system-map
systems:
  - id: crm-core
    name: CRM Core
    kind: internal-application
    lifecycleStatus: active
    operationalStatus: production
    criticality: unknown
    purpose: Hosts core CRM capabilities used by customer profile and lead management flows.
    aliases:
      - crm
      - crm-core
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - db-grounding
      - incident-analysis
      - qa
    responsibilityStatus: shared
    capabilities:
      businessCapabilities:
        - customer profile management
        - lead management
      technicalCapabilities:
        - REST API
        - event publishing
      exposedFunctions:
        - customer profile API
      internalFunctions:
        - customer persistence
      excludedCapabilities:
        - email delivery
      notes: []
    references:
      runtimeComponents:
        - crm-core-api-runtime
      repositories:
        - crm-core-api-repo
      modules:
        - crm-core-api-repo:customer-module
      processes:
        - customer-profile-update
      boundedContexts:
        - customer-profile
      integrations:
        - crm-core-customer-changed-events
      terms:
        - customer-profile
      teams:
        - crm-platform-team
      externalParties: []
      dataStores:
        - crm-database
      handoffRules: []
    responsibilities:
      - actorType: team
        actorId: crm-platform-team
        targetType: system
        targetId: crm-core
        role: domain-steward
        scope: customer profile semantics
        side: not-applicable
        status: confirmed
        confidence: high
        evidence:
          - sourceType: documentation
            source: docs/ownership/crm.md
            detail: CRM platform team documented as customer profile domain steward.
            confidence: high
    matchSignals:
      exact:
        serviceNames:
          - crm-core-api
        applicationNames:
          - crm-core-api
      strong:
        packagePrefixes:
          - com.example.crm.customer
        endpointPrefixes:
          - /api/customers
      medium:
        databaseTables:
          - customer_profile
      weak:
        aliases:
          - crm
    recognition:
      strongSignals:
        - spring.application.name crm-core-api with package prefix com.example.crm.customer.
      weakSignals:
        - Generic CRM wording is not enough without runtime or code evidence.
      falsePositiveSignals:
        - crm-email-worker points to notification delivery, not the CRM Core API.
      disambiguationRules:
        - Prefer crm-email-worker-runtime for email delivery failures.
    relations:
      - targetType: runtime-component
        targetId: crm-core-api-runtime
        type: implemented-by
        direction: not-applicable
        via: []
        confidence: high
        evidence:
          - sourceType: config
            source: src/main/resources/application.yml
            detail: spring.application.name=crm-core-api.
            confidence: high
    runtimeBehavior:
      inbound:
        - type: http
          sourceSystemIds: []
          sourceRuntimeComponentIds: []
          integrationIds: []
          endpointPrefixes:
            - /api/customers
          endpointTemplates: []
          queues: []
          topics: []
          exchanges: []
          routingKeys: []
          events: []
          triggerNames: []
          notes: []
          evidence: []
      outbound: []
      persistence:
        - dataStoreId: crm-database
          databaseType: postgresql
          datasourceNames:
            - crm
          hikariPools:
            - crm-pool
          schemas:
            - crm
          tables:
            - customer_profile
          entityClasses:
            - CustomerProfileEntity
          repositoryClasses:
            - CustomerProfileRepository
          migrationPaths:
            - src/main/resources/db/migration
          accessPattern: read-write
          notes: []
          evidence: []
      messaging: []
      scheduling: []
      security: []
    interfaces:
      http:
        inboundEndpointPrefixes:
          - /api/customers
        inboundEndpointTemplates: []
        outboundEndpointPrefixes: []
        outboundEndpointTemplates: []
        publicApiLabels: []
        controllerClasses:
          - CustomerProfileController
        clientClasses: []
      messaging:
        inboundQueues: []
        inboundExchanges: []
        inboundTopics: []
        inboundRoutingKeys: []
        inboundEventNames: []
        outboundQueues: []
        outboundExchanges: []
        outboundTopics:
          - customer.events
        outboundRoutingKeys: []
        outboundEventNames:
          - CustomerProfileChanged
        listenerClasses: []
        publisherClasses:
          - CustomerProfileEventPublisher
      batchAndJobs:
        scheduledJobNames: []
        lockNames: []
        workflowNames: []
        workerNames: []
        triggerEvents: []
        jobClasses: []
      persistence:
        dataStoreIds:
          - crm-database
        databaseType: postgresql
        databaseSchemas:
          - crm
        databaseTables:
          - customer_profile
        hikariPools:
          - crm-pool
        entityClasses:
          - CustomerProfileEntity
        repositoryClasses:
          - CustomerProfileRepository
        migrationPaths:
          - src/main/resources/db/migration
      observability:
        logIndexes: []
        traceServiceNames:
          - crm-core-api
        spanNames: []
        dashboardRefs: []
        alertLabels: []
        metricNames: []
    dependencies:
      internalSystems: []
      externalSystems: []
      integrationGateways: []
      platformServices: []
      messageBrokers:
        - crm-message-broker
      dataStores:
        - crm-database
      securityServices:
        - identity-provider
      observabilityServices: []
      notes: []
    deploymentAndTopology:
      deploymentModel: kubernetes
      environments:
        - prod
      regions: []
      clusters: []
      namespaces:
        - crm
      serviceDiscoveryNames:
        - crm-core-api
      gateways: []
      ingressRoutes: []
      networkZones: []
      notes: []
    observability:
      logIndexes: []
      dashboards: []
      alertLabels: []
      runbookRefs: []
      sliOrSloNames: []
      traceServiceNames:
        - crm-core-api
      metricNames: []
      healthCheckSignals:
        - /actuator/health
    failureModes: []
    routingHints: []
    handoffHints:
      defaultRouteLabel: null
      firstResponderTeamIds:
        - crm-platform-team
      escalationTeamIds: []
      partnerTeamIds: []
      platformSupportTeamIds: []
      externalRouteLabels: []
      requiredEvidence:
        - correlationId logs
        - crm-core-api trace service name
      preferredEvidence: []
      expectedFirstActions: []
      whenToRouteHere:
        - Runtime evidence matches crm-core-api exact service name.
      whenToInvolveAsPartner: []
      whenNotToRouteHere:
        - Evidence only mentions email delivery or bounce handling.
      fallbackIfAmbiguous: null
      notes: []
    analysisHints:
      deterministicMapping:
        - Prefer exact serviceName crm-core-api and package prefix com.example.crm.customer.
      codeSearch:
        - Search crm-core-api-repo first, then generated clients listed on the runtime component.
      functionDescription:
        - Describe as the CRM customer profile API and persistence capability.
      impactAnalysis:
        - Check customer profile process and customer changed event integration.
      dbCodeGrounding:
        - Table customer_profile maps to CustomerProfileEntity and CustomerProfileRepository.
      incidentAnalysis:
        - Do not infer email platform ownership from customer events alone.
      qa: []
    llmToolHints:
      preferredWhen:
        - User asks about crm-core-api customer profile behavior.
      avoidWhen:
        - User asks about email delivery worker behavior.
      explanationHints:
        - Explain the logical CRM Core system separately from the runtime component.
      usefulForQuestions:
        - Which system owns customer profile API behavior?
      answerStyleHints:
        - Use local system names and cite evidence fields.
      disambiguationHints:
        - crm-core is the logical system; crm-core-api-runtime is the deployed component.
      commonMisreads:
        - Do not treat all CRM-related packages as this system without matching runtime evidence.
    evidence:
      - sourceType: config
        source: src/main/resources/application.yml
        detail: spring.application.name=crm-core-api.
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - repository:crm-core-api-repo
      expectedSources:
        - deployment manifests
        - ownership documentation
      missingSources:
        - deployment manifests
      notes:
        - System is grounded in application config and source; deployment topology is partial.

runtimeComponents:
  - id: crm-core-api-runtime
    name: CRM Core API Runtime
    kind: spring-boot-service
    lifecycleStatus: active
    operationalStatus: production
    criticality: unknown
    purpose: Deployed Spring Boot API component for the CRM Core logical system.
    aliases:
      - crm-core-api
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - db-grounding
      - incident-analysis
      - qa
    systemIds:
      - crm-core
    responsibilityStatus: shared
    deployment:
      serviceNames:
        - crm-core-api
      applicationNames:
        - crm-core-api
      containerNames: []
      deploymentNames:
        - crm-core-api
      namespaceNames:
        - crm
      imageNames: []
      artifactNames:
        - crm-core-api.jar
      processNames: []
      ports:
        - 8080
      environmentHints:
        - prod
      runtimePlatforms:
        - kubernetes
      healthEndpoints:
        - /actuator/health
      managementEndpoints:
        - /actuator
      configKeys:
        - spring.application.name
      notes: []
    codeSearchScope:
      repositories:
        - repoId: crm-core-api-repo
          role: main-service
          moduleIds:
            - crm-core-api-repo:customer-module
          packagePrefixes:
            - com.example.crm.customer
          classHints:
            - CustomerProfileController
            - CustomerProfileService
          reason: Main source repository for the deployed crm-core-api runtime.
          confidence: high
        - repoId: crm-client-generated-repo
          role: generated-client
          moduleIds: []
          packagePrefixes:
            - com.example.crm.generated
          classHints: []
          reason: Generated clients may appear in stacktraces for downstream calls.
          confidence: medium
      packagePrefixes:
        - com.example.crm
      classHints:
        - CustomerProfileController
        - CustomerProfileService
      configPrefixes:
        - crm
      generatedClients:
        - crm-client-generated-repo
      sharedLibraries: []
      searchTogetherWithRuntimeComponents: []
      searchNotes:
        - Include generated clients when stacktrace classes are not found in the main service repo.
    references:
      repositories:
        - crm-core-api-repo
      modules:
        - crm-core-api-repo:customer-module
      processes:
        - customer-profile-update
      boundedContexts:
        - customer-profile
      integrations:
        - crm-core-customer-changed-events
      terms:
        - customer-profile
      teams:
        - crm-platform-team
      externalParties: []
      dataStores:
        - crm-database
      handoffRules: []
    responsibilities: []
    matchSignals:
      exact:
        serviceNames:
          - crm-core-api
        applicationNames:
          - crm-core-api
        artifactNames:
          - crm-core-api.jar
      strong:
        packagePrefixes:
          - com.example.crm
      medium: {}
      weak: {}
    recognition:
      strongSignals:
        - Exact application name crm-core-api.
      weakSignals: []
      falsePositiveSignals: []
      disambiguationRules: []
    relations:
      - targetType: system
        targetId: crm-core
        type: implements
        direction: not-applicable
        via: []
        confidence: high
        evidence:
          - sourceType: config
            source: src/main/resources/application.yml
            detail: Runtime application name maps to CRM Core logical system.
            confidence: high
    runtimeBehavior:
      inbound: []
      outbound: []
      persistence: []
      messaging: []
      scheduling: []
      security: []
    interfaces:
      http:
        inboundEndpointPrefixes:
          - /api/customers
        inboundEndpointTemplates: []
        outboundEndpointPrefixes: []
        outboundEndpointTemplates: []
        publicApiLabels: []
        controllerClasses:
          - CustomerProfileController
        clientClasses: []
      messaging:
        inboundQueues: []
        inboundExchanges: []
        inboundTopics: []
        inboundRoutingKeys: []
        inboundEventNames: []
        outboundQueues: []
        outboundExchanges: []
        outboundTopics:
          - customer.events
        outboundRoutingKeys: []
        outboundEventNames:
          - CustomerProfileChanged
        listenerClasses: []
        publisherClasses:
          - CustomerProfileEventPublisher
      batchAndJobs:
        scheduledJobNames: []
        lockNames: []
        workflowNames: []
        workerNames: []
        triggerEvents: []
        jobClasses: []
      persistence:
        dataStoreIds:
          - crm-database
        databaseType: postgresql
        databaseSchemas:
          - crm
        databaseTables:
          - customer_profile
        hikariPools:
          - crm-pool
        entityClasses:
          - CustomerProfileEntity
        repositoryClasses:
          - CustomerProfileRepository
        migrationPaths:
          - src/main/resources/db/migration
      observability:
        logIndexes: []
        traceServiceNames:
          - crm-core-api
        spanNames: []
        dashboardRefs: []
        alertLabels: []
        metricNames: []
    dependencies:
      internalSystems: []
      externalSystems: []
      integrationGateways: []
      platformServices: []
      messageBrokers:
        - crm-message-broker
      dataStores:
        - crm-database
      securityServices:
        - identity-provider
      observabilityServices: []
      notes: []
    deploymentAndTopology:
      deploymentModel: kubernetes
      environments:
        - prod
      regions: []
      clusters: []
      namespaces:
        - crm
      serviceDiscoveryNames:
        - crm-core-api
      gateways: []
      ingressRoutes: []
      networkZones: []
      notes: []
    observability:
      logIndexes: []
      dashboards: []
      alertLabels: []
      runbookRefs: []
      sliOrSloNames: []
      traceServiceNames:
        - crm-core-api
      metricNames: []
      healthCheckSignals:
        - /actuator/health
    failureModes: []
    routingHints: []
    handoffHints:
      defaultRouteLabel: null
      firstResponderTeamIds:
        - crm-platform-team
      escalationTeamIds: []
      partnerTeamIds: []
      platformSupportTeamIds: []
      externalRouteLabels: []
      requiredEvidence:
        - crm-core-api logs
      preferredEvidence: []
      expectedFirstActions: []
      whenToRouteHere:
        - Exact runtime service name is crm-core-api.
      whenToInvolveAsPartner: []
      whenNotToRouteHere: []
      fallbackIfAmbiguous: null
      notes: []
    analysisHints:
      deterministicMapping:
        - Exact application/service name crm-core-api maps here.
      codeSearch:
        - Search crm-core-api-repo first; include crm-client-generated-repo for generated client stacktrace classes.
      functionDescription:
        - Describe as the deployed API component of CRM Core.
      impactAnalysis:
        - Check CRM Core logical system and customer-profile-update process.
      dbCodeGrounding:
        - Use customer_profile table and CustomerProfileEntity for grounding.
      incidentAnalysis:
        - If logs show only customer profile API errors, start from this runtime component.
      qa: []
    llmToolHints:
      preferredWhen:
        - Evidence contains crm-core-api service name, artifact or trace service.
      avoidWhen: []
      explanationHints:
        - Explain runtime identity separately from logical CRM Core purpose.
      usefulForQuestions:
        - Which code scope belongs to crm-core-api runtime?
      answerStyleHints: []
      disambiguationHints: []
      commonMisreads: []
    evidence:
      - sourceType: config
        source: src/main/resources/application.yml
        detail: spring.application.name=crm-core-api.
        confidence: high
    sourceCoverage:
      status: partial
      scannedSources:
        - repository:crm-core-api-repo
      expectedSources:
        - deployment manifests
        - repo-map.yml
      missingSources:
        - deployment manifests
      notes: []

gaps:
  - id: crm-core-deployment-topology-gap
    type: missing-topology
    severity: medium
    status: open
    affectedNodes:
      - runtime-component:crm-core-api-runtime
    question: Which production cluster and gateway routes host crm-core-api?
    impact:
      - Limits impact analysis and handoff precision for deployment-level failures.
    requiredToResolve:
      - Production deployment manifests or platform topology documentation.
    suggestedSources:
      - deployment-config repository
      - Helm chart repository
    evidence:
      - sourceType: config
        source: src/main/resources/application.yml
        detail: Runtime application name is known, but deployment topology was not available in scanned source.
        confidence: medium
```

## Input

```text
CURRENT FILE:
<existing systems.yml content>

NEW FACTS:
<facts from repository scan, deployment report, telemetry, documentation, existing operational context, or human input>

SCAN SCOPE:
<repository/documentation/deployment/telemetry scope that was actually analyzed, if known>

FULL OPERATIONAL CONTEXT:
<optional summaries or current content of repo-map.yml, processes.yml, integrations.yml, bounded-contexts.yml, teams.yml, glossary.md/terms.yml, handoff-rules.md, operational-context-index.md>

BUILD MEMORY:
<optional temporary cross-repository build memory; do not copy into final systems.yml unless explicitly requested as sidecar>
```
