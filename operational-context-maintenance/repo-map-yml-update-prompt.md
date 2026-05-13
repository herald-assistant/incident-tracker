# repo-map.yml update prompt

You maintain `src/main/resources/operational-context/repo-map.yml`.

Treat the attached operational-context YAML files as the catalog model.
Use only the documented structure below plus useful fields already present in
this catalog.

## Target file contract

Keep this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-repo-map
repositories: []
codeSearchScopes: []
gaps: []
```

`repositories[]` describes stable Git repositories and their internal source
layout. `codeSearchScopes[]` describes reusable code-search entry points for
AI-guided analysis. A scope should help the model fetch the right code without
knowing repository internals.

Preferred repository entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable repository name
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  summary: One or two factual sentences.
  git:
    provider: gitlab
    group: group-or-namespace
    project: project-name
    projectPath: group/project
    defaultBranch: main
  references:
    systems: []
    processes: []
    boundedContexts: []
    integrations: []
    teams: []
    terms: []
    handoffRules: []
  sourceLayout: []
  modules: []
  runtimeMappings: []
  codeSearch: []
  matchSignals: []
  lookupHints: []
  persistenceHints: []
  analysisHints: []
  handoffHints: []
  llmToolHints: []
  evidence: []
  sourceCoverage: []
  scannedSubtrees: []
  gaps: []
```

Preferred `codeSearchScopes[]` entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable scope name
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  summary: One or two factual sentences.
  repositoryId: referenced-repository-id
  appliesTo:
    systems: []
    processes: []
    boundedContexts: []
    integrations: []
  roots: []
  includeGlobs: []
  excludeGlobs: []
  packagePrefixes: []
  classHints: []
  endpointHints: []
  queueHints: []
  topicHints: []
  databaseHints: []
  termHints: []
  priorityHints: []
  analysisHints: []
  evidence: []
  gaps: []
```

Preserve additional existing fields only when they follow the current catalog
style and carry useful evidence. Remove duplicate, empty, speculative, or
shape-conflicting fields.

## Update rules

- Prefer editing an existing repository when evidence matches its Git path,
  artifact id, package prefix, module path, service name, or source layout.
- Add a new repository only when the evidence identifies a stable repository
  that AI or an operator should search again.
- Keep `id` stable once created. Use kebab-case.
- Keep Git coordinates under `git`.
- Put catalog relations in `references` and scope applicability in
  `codeSearchScopes[].appliesTo`.
- Use `sourceLayout`, `modules`, `runtimeMappings`, `codeSearch`, and
  `scannedSubtrees` to describe concrete repository structure.
- Use `sourceCoverage` to state what has actually been scanned and what remains
  unknown.
- Use `gaps` for durable unresolved issues that affect repository discovery,
  code search, ownership, or runtime mapping.
- Keep references aligned with the attached `systems.yml`, `processes.yml`,
  `bounded-contexts.yml`, `integrations.yml`, `teams.yml`, `glossary.md`, and
  `handoff-rules.md`.

## Code-search scope rules

Create or update a `codeSearchScopes[]` entry when a repeated analysis question
needs a stable search boundary, for example:

- a process spans only part of a large repository
- a bounded context has known package roots or module paths
- an integration has stable endpoint, queue, WSDL, client, or adapter code
- a system maps to several Maven modules or package families

Each scope must include enough roots, package prefixes, class hints, endpoint
hints, queue/topic hints, or term hints to make AI-guided fetching precise.
Avoid broad roots when a narrower module or package is visible in evidence.

## Output rules

Return the complete updated `repo-map.yml` content only.
Do not include commentary, markdown fences, diffs, or explanations.
