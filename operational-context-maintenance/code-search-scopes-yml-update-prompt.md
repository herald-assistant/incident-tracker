# code-search-scopes.yml update prompt

You maintain `src/main/resources/operational-context/code-search-scopes.yml`.

Return only the complete updated `code-search-scopes.yml` content.

## Purpose

`code-search-scopes.yml` defines semantic implementation search scopes. It
answers: "which repositories and modules should be searched together to
understand this bounded context, process, system or integration?"

Do not model deployment components here. Runtime names, service names,
containers and deployments belong to `systems.yml` as signals of a system.

## Top-level contract

```yaml
schemaVersion: 1
catalogKind: operational-context-code-search-scopes
codeSearchScopes: []
gaps: []
```

## codeSearchScopes[]

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

## Rules

- Use exactly one `target { type, id }`.
- Do not use `target.systems`, `target.processes`, `target.boundedContexts`,
  `target.integrations`, `include`, top-level `packagePrefixes`, or
  `searchStrategy`.
- List only repositories that are part of the scope. Absence means excluded.
- Prefer `scopeType: bounded-context` when a capability is spread across a
  modular monolith, microservices and shared libraries.
- Use `scopeType: process` only when the stable search boundary is a flow/use
  case rather than a single bounded context.
- Use repository `role` and `priority` to express read order; use `traversal`
  for conditions such as when to expand to libraries, generated clients, legacy
  modules or integration adapters.
- Keep hints specific enough to reduce broad GitLab searches.

## Redundancy Rules

- `repo-map.yml` owns repository inventory and module definitions.
- `codeSearchScopes[].target` says which one semantic target the scope
  implements.
- `codeSearchScopes[].repositories[]` says which repositories/modules must be
  searched together for that target.
- Do not copy every repository reference into the scope target.

## Validation

- Every `target.id` must exist in the matching catalog file.
- Every `repoId` must exist in `repo-map.yml repositories[]`.
- Every `moduleIds[]` value must exist as `repo-map.yml repositories[].modules[].id`
  or canonical module id.
- If evidence names an uncertain repository, module or target, add a durable
  `gaps[]` item instead of inventing a stable scope.
