# repo-map.yml update prompt

You maintain `src/main/resources/operational-context/repo-map.yml`.

Treat the attached operational-context YAML files as the catalog model. Return
only the complete updated `repo-map.yml` content.

## Purpose

`repo-map.yml` is the write model for repository discovery and semantic code
search. It should help AI and operators answer: "where is this bounded context,
process, system or integration implemented?"

Do not make `codeSearchScopes[]` a deployment-component list. Runtime names,
service names, containers and deployments belong to `systems.yml` as signals of
a system.

## Top-level contract

```yaml
schemaVersion: 1
catalogKind: operational-context-repo-map
repositories: []
codeSearchScopes: []
gaps: []
```

## repositories[]

Use `repositories[]` for stable facts about one Git repository:

- Git coordinates and default branch,
- repository type, lifecycle and short summary,
- catalog references owned by the repository model,
- source layout, build files, modules and important paths,
- package/class/endpoint/queue hints that are true for the repository,
- module-level hints when a monorepo or modular monolith needs narrowing.

Do not duplicate process flow, integration participants, bounded-context
definitions or team ownership here when another catalog file owns that fact.

## codeSearchScopes[]

Use `codeSearchScopes[]` for reusable semantic implementation scopes. A scope
has exactly one semantic target and an ordered list of repositories/modules to
read together.

Preferred shape:

```yaml
- id: stable-kebab-case-id
  name: Human readable scope name
  scopeType: bounded-context | process | system | integration | shared-capability
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  summary: One or two factual sentences.
  target:
    type: bounded-context | process | system | integration
    id: existing-catalog-id
  useFor:
    - code-search
    - incident-analysis
  repositories:
    - repoId: existing-repository-id
      role: primary-implementation | supporting-library | generated-client | integration-adapter | legacy-implementation | target-implementation | parallel-implementation | deployment-config
      priority: 1
      moduleIds: []
      reason: Why this repository belongs to the semantic implementation scope.
      readFor: []
  hints:
    packagePrefixes: []
    classHints: []
    endpointHints: []
    queueTopicHints: []
    database:
      datasourceNames: []
      hikariPools: []
      schemas: []
      tables: []
      entities: []
      migrations: []
    workflow:
      jobNames: []
      workflowNames: []
      definitionPaths: []
  traversal:
    rules: []
    expandWhen: []
  limitations: []
```

Rules:

- Do not use `target.systems`, `target.processes`, `target.boundedContexts`,
  `target.integrations`, `include`, top-level `packagePrefixes`, or
  `searchStrategy`.
- List only repositories that are part of the scope. Absence means excluded.
- Prefer `scopeType: bounded-context` when the implementation boundary is a
  domain capability spread across a modular monolith, microservices and shared
  libraries.
- Use `scopeType: process` only when the stable search boundary is a flow/use
  case rather than a single bounded context.
- Use repository `role` and `priority` to express read order; use `traversal`
  for conditions such as when to expand to libraries, generated clients, legacy
  modules or integration adapters.
- Keep hints specific enough to reduce broad GitLab searches.

## Redundancy rules

- `repositories[].references.*` says what the repository generally relates to.
- `codeSearchScopes[].target` says which one semantic target this search scope
  implements.
- `codeSearchScopes[].repositories[]` says which repositories/modules must be
  searched together for that target.
- Do not copy every repository reference into the scope target.
- Do not create reciprocal references only for symmetry.

## Validation

- Every `target.id` must exist in the matching catalog file.
- Every `repoId` must exist in `repositories[]`.
- Every `moduleIds[]` value must exist as `repositories[].modules[].id` or
  canonical module id.
- If evidence names an uncertain repository, module or target, add a durable
  `gaps[]` item instead of inventing a stable scope.
