# processes.yml update prompt

You maintain `src/main/resources/operational-context/processes.yml`.

Treat the attached operational-context YAML files as the catalog model.
Use only the documented structure below plus useful fields already present in
this catalog.

## Target file contract

Keep this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-processes
processes: []
gaps: []
```

`gaps` is optional when there are no unresolved durable questions. When used, it
must describe missing process knowledge that affects analysis, ownership,
routing, or code search.

Preferred `processes[]` entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable process name
  type: business-process | operational-process | technical-process
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  criticality: critical | high | medium | low | unknown
  summary: One or two factual sentences.
  operationalOutcome: What successful completion means operationally.
  responsibilityStatus: confirmed | inferred | unknown | shared
  useFor: []
  processBoundary:
    startsWhen: []
    endsWhen: []
    excludes: []
  participants:
    systems: []
    externalParties: []
    teams: []
  references:
    repositories: []
    systems: []
    integrations: []
    boundedContexts: []
    teams: []
    terms: []
    handoffRules: []
  dataAndArtifacts: []
  lifecycle: []
  steps: []
  responsibilities: []
  matchSignals: []
  completionSignals: []
  failureModes: []
  relations: []
  observability: []
  analysisHints: []
  gaps: []
```

Step entries may use `match` for local code/runtime fingerprints:

```yaml
steps:
  - id: step-id
    name: Step name
    summary: Factual step summary.
    participants:
      systems: []
      integrations: []
      boundedContexts: []
    match:
      packagePrefixes: []
      classHints: []
      endpointPrefixes: []
      queues: []
      topics: []
      databaseTables: []
      logMarkers: []
    failureModes: []
    evidence: []
```

Preserve additional existing fields only when they follow the current catalog
style and carry useful evidence. Remove duplicate, empty, speculative, or
shape-conflicting fields.

## Update rules

- Prefer editing an existing process when evidence matches its systems,
  repositories, bounded contexts, integrations, steps, or domain vocabulary.
- Add a new process only when evidence identifies a durable end-to-end flow with
  a meaningful business or operational boundary.
- Do not model a single endpoint, class, table, queue, or batch job as a process
  unless the evidence shows a stable flow boundary.
- Keep `id` stable once created. Use kebab-case.
- Keep `processBoundary` explicit enough that AI can distinguish this process
  from neighboring flows.
- Put ownership and routing facts in `responsibilities`, `participants.teams`,
  `references.teams`, and `handoffHints` when present in this file's structure.
- Put unresolved durable questions in `gaps`; each gap must include enough
  evidence context to be actionable.
- Keep references aligned with the attached `systems.yml`, `repo-map.yml`,
  `bounded-contexts.yml`, `integrations.yml`, `teams.yml`, `glossary.md`, and
  `handoff-rules.md`.

## Output rules

Return the complete updated `processes.yml` content only.
Do not include commentary, markdown fences, diffs, or explanations.
