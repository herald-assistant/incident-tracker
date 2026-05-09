Update only `repo-map.yml` and return the full ready-to-save YAML document only.

You are maintaining `src/main/resources/operational-context/repo-map.yml`.
Read all attached sources and produce the final content of that file.

## Purpose

`repo-map.yml` is not a flat GitLab project list. It is the deterministic bridge
from runtime, deployment, log, code, build, DB and operational-context evidence
to repositories, modules and code-search scopes.

It must help later analysis answer:

- which GitLab repository or module matches this runtime signal, class,
  package, stacktrace, endpoint, schema, datasource or deployment marker;
- which repositories should be searched together for one deployed component;
- which shared libraries, generated clients, integration libraries,
  schema/config repos or monorepo modules are part of that search;
- which gaps remain unresolved without inventing ownership or source facts.

## Output Shape

Preserve this exact top-level wrapper and order:

```yaml
schemaVersion: 1
catalogKind: operational-context-repository-map
repositories: []
codeSearchScopes: []
gaps: []
```

Use `gaps`, not legacy `openQuestions`.

Do not downgrade the file to the old shape with top-level `project`, `group`,
`ownerTeamId`, `systems`, `processes` or `contexts` fields. Keep the current
schema style with nested `git`, `references`, `sourceLayout`, `modules`,
`matchSignals`, `lookupHints`, `analysisHints`, `handoffHints`, `llmToolHints`,
`evidence`, `sourceCoverage` and `gaps` where those fields already exist or are
needed.

## Core Model

### `repositories[]`

A repository entry describes one source repository, or one repository-like
source when it is explicitly catalogued.

Use it to capture:

- canonical identity: stable id, name, repository type, Git provider, group,
  project, project path, aliases and lifecycle;
- what it contains: deployable service, shared library, generated client,
  integration library, schema repo, config repo, frontend, monorepo,
  documentation, infrastructure or mixed source;
- deterministic match signals: project names, project paths, build
  coordinates, package prefixes, source roots, endpoints, queue/topic names,
  datasource/schema/table/entity names, class hints and stacktrace hotspots;
- modules or subareas inside that same GitLab repository;
- relationships to systems, processes, bounded contexts, integrations, terms,
  teams and other repositories;
- repository-local hints about how this repo participates in broader
  code-search scopes.

Create one `repositories[]` entry per canonical GitLab project. Do not create
one repository entry per module, directory, file, package or stacktrace frame.
Use `modules[]` for meaningful subareas inside one repository.

### `codeSearchScopes[]`

Top-level `codeSearchScopes` are first-class. They describe which repositories
must be searched together for one system, process or bounded context.

Use them whenever a runtime/code investigation may span more than one source
area, for example:

- a main service repository plus shared domain/technical libraries;
- a main service repository plus generated clients;
- a service plus integration adapter libraries;
- a service plus schema/message-contract repositories;
- a deployed component whose code is split across a monorepo and another repo;
- a service plus deployment/config or workflow/config repository when those
  files are necessary to understand runtime behavior.

Do not hide multi-repository search scope only in prose, repository notes or
`analysisHints`. Represent it explicitly through top-level `codeSearchScopes`.
Repository-local `codeSearch` hints are useful, but they are secondary.

Runtime analysis consumes top-level `codeSearchScopes` directly. The evidence
digest exposes scope ids, repository ids, project names and repository roles;
GitLab discovery tools return the same scopes so AI can search all projects for
one deployment component together. If a relationship matters during incident
analysis, it must be represented structurally in `codeSearchScopes`, not only as
free text.

## Shared Libraries And Generated Clients

When a shared library, generated client or integration library is a separate
GitLab repository:

1. Create or update its own `repositories[]` entry.
2. Set an appropriate `repositoryType`, such as `shared-library`,
   `generated-client`, `integration-library`, `data-access-library` or
   `schema-repository`.
3. Add strong recognition signals: package prefixes, exported packages, build
   coordinates, generated-client identifiers, schema coordinates, source roots,
   class hints and stacktrace hotspots.
4. Add repository-local `codeSearch` hints when useful, such as
   `scopeRole: shared-library`, `searchTogetherWith`, `consumedBySystems`,
   `consumedByRepositories`, `dependencyCoordinates`,
   `generatedClientCoordinates` or `schemaCoordinates`.
5. Add it to every relevant top-level `codeSearchScopes[].repositories` entry
   for consuming systems where evidence shows it is part of the code search
   scope.

When a shared library is a module inside a monorepo, do not create a separate
repository entry. Model it under the owning repository's `modules[]`, and refer
to that module with `moduleIds` inside the relevant `codeSearchScopes`.

Do not add a shared library to every scope just because it exists. Include it
only when evidence supports the relationship, such as:

- Maven/Gradle/npm dependency coordinates;
- imports or source references from the consuming repo;
- generated client usage;
- packaged runtime code or dependency manifests;
- stacktrace classes from that library in incidents for the consuming runtime;
- package/class/source-root overlap;
- schema, API or message contract coordinates;
- operational context references from systems, processes, integrations or
  bounded contexts.

Shared libraries and generated clients can explain stacktrace classes and
contracts. They do not automatically own the consuming runtime behavior,
incident handoff or business process. Keep ownership and responsibility
evidence separate from dependency evidence.

For current runtime compatibility, also keep repository-level references and
repository-local code-search hints in sync when evidence supports them. For
example, a shared library consumed by `payments-api` may reference that system
as a consumer, but the authoritative multi-repo search grouping still belongs
in top-level `codeSearchScopes`.

## Derivation Workflow

1. Start from the current `repo-map.yml`; preserve confirmed entries and ids.
2. Read attached operational context (`systems.yml`, `teams.yml`,
   `processes.yml`, `bounded-contexts.yml`, `integrations.yml`,
   `glossary.md`, `handoff-rules.md`) for stable ids and relationships.
3. Read attached incident exports, GitLab-resolved code evidence, build files,
   dependency manifests, deployment/config evidence and documentation fragments.
4. Identify canonical GitLab projects before adding or changing repository
   entries. Prefer GitLab project metadata and stable project paths over runtime
   aliases.
5. For each repository, collect deterministic signals first: project path,
   package roots, build coordinates, source roots, class hints, endpoints,
   queues/topics, datasource/schema/table/entity markers and recurring file
   paths.
6. Decide whether observed source areas are separate repositories or modules
   inside one repository.
7. Build or update `codeSearchScopes` for systems:
   start with the primary service repo, then add supported shared libraries,
   generated clients, integration libraries and config/schema repos with roles,
   priorities, module ids and reasons.
8. Add typed `gaps` for durable unresolved issues that affect deterministic
   mapping or code-search scope. Do not use gaps as temporary scan memory.

## Repository Entry Guidance

Prefer this field order when creating or normalizing repository entries:

```yaml
- id: stable-repository-id
  name: Human readable repository name
  repositoryType: service | shared-library | generated-client | integration-library | data-access-library | deployment-config | workflow-config | schema-repository | frontend | documentation | test-support | monorepo | aggregator | infrastructure | mixed | unknown
  lifecycleStatus: active | planned | deprecated | retired | candidate | external | unknown
  criticality: critical | high | medium | low | unknown
  git:
    provider: gitlab
    group: null
    project: null
    projectPath: null
    defaultBranch: null
    url: null
    aliases: []
    inferred: false
  purpose: Short operational description.
  useFor: []
  responsibilityStatus: unknown
  responsibilities: []
  references:
    systems: []
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
    scopeRole: null
    searchTogetherWith: []
    consumedBySystems: []
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
  lookupHints: {}
  persistenceHints: {}
  analysisHints: {}
  handoffHints: {}
  llmToolHints: {}
  evidence: []
  sourceCoverage:
    status: unknown
    scannedSources: []
    expectedSources: []
    limitations: []
  gaps: []
```

Keep existing valid fields that are not shown here. Do not invent fields only to
fill the template.

## Code-Search Scope Guidance

Use this shape for top-level `codeSearchScopes[]`:

```yaml
- id: stable-code-search-scope-id
  name: Human readable scope name
  lifecycleStatus: active
  target:
    systems: []
    deploymentComponents: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
  useFor:
    - deterministic-mapping
    - code-search
    - incident-analysis
  repositories:
    - repoId: primary-service-repo
      role: primary
      priority: 1
      include: true
      moduleIds: []
      reason: Main deployed service repository.
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
    status: unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

Allowed repository roles in a scope include:

- `primary`
- `shared-library`
- `generated-client`
- `integration-library`
- `data-access-library`
- `schema-repository`
- `deployment-config`
- `workflow-config`
- `supporting`
- `exclude`

Set `priority` so the main service is searched first, then libraries and
generated clients, then config/schema/supporting repos when relevant. Use
`include: false` with role `exclude` only for explicit anti-scope evidence.

## YAML Syntax Safety Rules

- Never use TAB characters for indentation. YAML forbids TABs entirely. Use
  only spaces, with 2 spaces per level.
- Prefer block sequences for paths, endpoints, hosts, queue names, topics,
  class names and any value that may contain special characters.
- Inside YAML flow sequences (`[value1, value2]`), double-quote any value that
  contains curly braces `{}`, colons `:`, hash `#`, square brackets `[]`,
  commas, leading `*`, leading `&` or URL-like syntax.
- Endpoint paths with path-template placeholders such as `{id}` or
  `{customerId}` are safest as block sequences.

```yaml
# correct
endpointHints:
  - /api/resource/{id}/details
  - /api/resource/{id}/summary

# also correct
endpointHints: ["/api/resource/{id}/details"]

# wrong: unquoted curly braces in a flow sequence can break YAML parsing
endpointHints: [/api/resource/{id}/details]
```

- When in doubt, quote the scalar or use a block sequence.

## Hard Rules

- Reuse ids from attached files exactly as they appear.
- Do not rename team, system, process, context, integration or repository ids
  unless the current file already contains a clear duplicate that must be
  merged.
- Do not invent ownership, responsibility, handoff target or team routing.
- Do not create duplicate repository entries for the same GitLab project.
- Do not create one repository entry per file, class, stacktrace frame or
  package.
- Do not merge separate GitLab repositories into one entry just because they
  share package prefixes.
- Do not delete confirmed repositories, modules or scopes just because they are
  not visible in the current scan.
- Do not create broad code-search scopes that include unrelated repositories.
- Prefer deterministic signals over prose.
- Prefer short reusable hints over copied file contents.
- Do not paste source code, long stacktraces, secrets, tokens, payloads or long
  exception bodies into the YAML.
- Do not output explanations, markdown fences, comments or anything except the
  final YAML.

## What Matters Most

- Every repository must be recognizable from runtime evidence, GitLab evidence
  or stable source/build signals.
- Every multi-repository runtime search should have an explicit
  `codeSearchScopes[]` entry.
- Shared libraries and generated clients should be searchable with their
  consumers when evidence supports it.
- Modules should narrow investigation inside a repository, not mirror every
  directory.
- Gaps should be precise, durable and useful for future catalog improvement.

## Universal Examples

The examples below are illustrative only. Do not copy ids, names or values from
them unless they are supported by attached sources.

### Example 1: service repository plus shared library

If evidence shows:

- runtime system `payments-api`;
- GitLab project `core/payments-api`;
- Maven dependency `com.example.payments:payments-shared-kernel`;
- stacktraces for `payments-api` include `MoneyAmount` and `PaymentSnapshot`
  classes from `com.example.payments.shared`;
- GitLab project `libs/payments-shared-kernel` contains those shared classes;

then model both repositories and connect them through a code-search scope:

```yaml
repositories:
  - id: payments-api-repo
    name: Payments API Repository
    repositoryType: service
    lifecycleStatus: active
    criticality: high
    git:
      provider: gitlab
      group: core
      project: payments-api
      projectPath: core/payments-api
      defaultBranch: main
      url: null
      aliases:
        - payments-api
      inferred: false
    purpose: Deployable Payments API service.
    useFor:
      - deterministic-mapping
      - code-search
      - incident-analysis
    responsibilityStatus: unknown
    responsibilities: []
    references:
      systems:
        - payments-api
      deploymentComponents: []
      repositories:
        - payments-shared-kernel-repo
      processes:
        - payment-capture
      boundedContexts:
        - payments
      integrations: []
      terms: []
      teams: []
      handoffRules: []
    sourceLayout:
      repositoryRoot: .
      buildTool: maven
      buildFiles:
        - pom.xml
      sourceRoots:
        - payments-service/src/main/java/com/example/payments
      testRoots: []
      resourceRoots:
        - payments-service/src/main/resources
      modulePaths: []
      generatedSourcePaths: []
      importantPaths:
        - payments-service/src/main/java/com/example/payments
      configurationFiles:
        - payments-service/src/main/resources/application.yml
      deploymentFiles: []
      databaseMigrationPaths: []
      workflowDefinitionPaths: []
      documentationPaths: []
    modules: []
    runtimeMappings: []
    codeSearch:
      scopeRole: primary
      searchTogetherWith:
        - payments-shared-kernel-repo
      consumedBySystems: []
      consumedByRepositories: []
      consumesRepositories:
        - payments-shared-kernel-repo
      exportedPackages: []
      importedPackageHints:
        - com.example.payments.shared
      dependencyCoordinates:
        - com.example.payments:payments-shared-kernel
      generatedClientCoordinates: []
      schemaCoordinates: []
      notes:
        - Search shared kernel when stacktraces include shared payment model classes.
    matchSignals:
      exact:
        projectNames:
          - payments-api
        projectPaths:
          - core/payments-api
      strong:
        packagePrefixes:
          - com.example.payments
        endpointPrefixes:
          - /api/payments
      medium:
        classHints:
          - PaymentController
          - PaymentService
      weak: {}
    lookupHints:
      likelyEntryClasses:
        - PaymentController
        - PaymentService
      likelyFiles: []
      likelyDirectories:
        - payments-service/src/main/java/com/example/payments
      stacktraceHotspots:
        - PaymentService
      searchKeywords:
        - PaymentService
      searchAntiPatterns: []
    evidence: []
    sourceCoverage:
      status: partial
      scannedSources:
        - repo:core/payments-api@main
      expectedSources:
        - repo:libs/payments-shared-kernel@main
      limitations: []
    gaps: []

  - id: payments-shared-kernel-repo
    name: Payments Shared Kernel Repository
    repositoryType: shared-library
    lifecycleStatus: active
    criticality: medium
    git:
      provider: gitlab
      group: libs
      project: payments-shared-kernel
      projectPath: libs/payments-shared-kernel
      defaultBranch: main
      url: null
      aliases:
        - payments-shared-kernel
      inferred: false
    purpose: Shared payment model and value object library.
    useFor:
      - code-search
      - dependency-analysis
      - incident-analysis
    responsibilityStatus: unknown
    responsibilities: []
    references:
      systems:
        - payments-api
      deploymentComponents: []
      repositories:
        - payments-api-repo
      processes:
        - payment-capture
      boundedContexts:
        - payments
      integrations: []
      terms: []
      teams: []
      handoffRules: []
    sourceLayout:
      repositoryRoot: .
      buildTool: maven
      buildFiles:
        - pom.xml
      sourceRoots:
        - src/main/java/com/example/payments/shared
      testRoots: []
      resourceRoots: []
      modulePaths: []
      generatedSourcePaths: []
      importantPaths:
        - src/main/java/com/example/payments/shared
      configurationFiles: []
      deploymentFiles: []
      databaseMigrationPaths: []
      workflowDefinitionPaths: []
      documentationPaths: []
    modules: []
    runtimeMappings: []
    codeSearch:
      scopeRole: shared-library
      searchTogetherWith:
        - payments-api-repo
      consumedBySystems:
        - payments-api
      consumedByRepositories:
        - payments-api-repo
      consumesRepositories: []
      exportedPackages:
        - com.example.payments.shared
      importedPackageHints: []
      dependencyCoordinates:
        - com.example.payments:payments-shared-kernel
      generatedClientCoordinates: []
      schemaCoordinates: []
      notes:
        - Shared model classes can appear in Payments API stacktraces.
        - Runtime behavior still belongs to the consuming service unless evidence says otherwise.
    matchSignals:
      exact:
        projectNames:
          - payments-shared-kernel
        projectPaths:
          - libs/payments-shared-kernel
        artifactIds:
          - payments-shared-kernel
        groupIds:
          - com.example.payments
      strong:
        packagePrefixes:
          - com.example.payments.shared
        buildCoordinates:
          - com.example.payments:payments-shared-kernel
      medium:
        classHints:
          - MoneyAmount
          - PaymentSnapshot
      weak: {}
    lookupHints:
      likelyEntryClasses: []
      likelyFiles: []
      likelyDirectories:
        - src/main/java/com/example/payments/shared
      stacktraceHotspots:
        - MoneyAmount
        - PaymentSnapshot
      searchKeywords:
        - MoneyAmount
        - PaymentSnapshot
      searchAntiPatterns:
        - Do not treat shared model class as runtime owner without consumer context.
    evidence: []
    sourceCoverage:
      status: partial
      scannedSources:
        - repo:libs/payments-shared-kernel@main
      expectedSources: []
      limitations: []
    gaps: []

codeSearchScopes:
  - id: payments-api-code-search
    name: Payments API Code Search Scope
    lifecycleStatus: active
    target:
      systems:
        - payments-api
      deploymentComponents: []
      processes:
        - payment-capture
      boundedContexts:
        - payments
      integrations: []
      terms: []
    useFor:
      - deterministic-mapping
      - code-search
      - incident-analysis
    repositories:
      - repoId: payments-api-repo
        role: primary
        priority: 1
        include: true
        moduleIds: []
        reason: Main deployed Payments API service repository.
      - repoId: payments-shared-kernel-repo
        role: shared-library
        priority: 2
        include: true
        moduleIds: []
        reason: Shared payment model classes can appear in Payments API stacktraces.
    packagePrefixes:
      - com.example.payments
      - com.example.payments.shared
    classHints:
      - PaymentService
      - MoneyAmount
      - PaymentSnapshot
    endpointHints:
      - /api/payments
    queueTopicHints: []
    databaseHints:
      datasourceNames: []
      hikariPools: []
      schemas:
        - payments
      tables: []
      entities: []
      migrations: []
    workflowHints:
      jobNames: []
      workflowNames: []
      definitionPaths: []
    searchStrategy:
      priorityOrder:
        - payments-api-repo
        - payments-shared-kernel-repo
      includeGeneratedClients: true
      includeSharedLibraries: true
      includeDeploymentConfig: false
      includeDocumentation: false
      notes:
        - Start with the service repo for runtime behavior, then inspect shared kernel for model/value-object stacktrace classes.
    limitations: []
    evidence: []
    sourceCoverage:
      status: partial
      scannedSources:
        - repo:core/payments-api@main
        - repo:libs/payments-shared-kernel@main
      expectedSources: []
      limitations: []
```

### Example 2: shared code inside a monorepo

If evidence shows `billing-api` and `billing-shared` are directories/modules
inside the same GitLab project, keep one `repositories[]` entry and model the
shared code as a module. In `codeSearchScopes[].repositories`, reference the
same `repoId` with `moduleIds` instead of creating a fake library repository.

Reason:

- the canonical GitLab identity is one project;
- modules narrow search inside that project;
- a separate repository entry would invent a repository that does not exist.

### Example 3: runtime alias is too weak

If evidence only shows:

- repeated runtime project name `backend`;
- generic service name `backend-service`;
- no stable GitLab project path;
- no recurring file paths, package roots or class hotspots;
- no trustworthy evidence for module boundaries or shared libraries;

then do not invent many repositories, modules or code-search scopes.

Prefer either:

- one cautious repo-level entry if the canonical repository identity is
  supported elsewhere; or
- a precise typed gap explaining which evidence is missing.

Return the full updated YAML only.
