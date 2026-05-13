# bounded-contexts.yml update prompt

You maintain `src/main/resources/operational-context/bounded-contexts.yml`.

Treat the attached operational-context YAML files as the catalog model.
Use only the documented structure below plus useful fields already present in
this catalog.

## Target file contract

Keep this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-bounded-contexts
boundedContexts: []
gaps: []
```

Each `boundedContexts[]` entry describes a stable semantic or domain boundary.
It should explain vocabulary, responsibilities, and recognition signals well
enough for AI to connect evidence to code and operational ownership.

Preferred entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable context name
  type: domain | subdomain | capability | support-context | technical-context
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  aliases: []
  summary: One or two factual sentences.
  localLanguageSummary: []
  useFor: []
  scope:
    includes: []
    excludes: []
  semanticBoundary:
    coreConcepts: []
    commands: []
    events: []
    invariants: []
  references:
    repositories: []
    systems: []
    processes: []
    integrations: []
    teams: []
    terms: []
    handoffRules: []
  responsibilityStatus: confirmed | inferred | unknown | shared
  responsibilities: []
  matchSignals: []
  relations: []
  operationalSignals: []
  analysisHints: []
  llmToolHints: []
  evidence: []
  sourceCoverage: []
  gaps: []
```

Preserve additional existing fields only when they follow the current catalog
style and carry useful evidence. Remove duplicate, empty, speculative, or
shape-conflicting fields.

## Update rules

- Prefer editing an existing bounded context when evidence matches its
  vocabulary, packages, repositories, systems, processes, integrations, or
  source coverage.
- Add a new bounded context only when evidence identifies a durable semantic
  boundary, not just a technical package or endpoint.
- Keep `id` stable once created. Use kebab-case and domain language.
- Put terms in `references.terms` and enrich `glossary.md` separately when the
  term itself needs a definition.
- Put ownership and routing facts in `responsibilities`, `references.teams`,
  and `handoffHints` when present in this file's structure.
- Put unresolved durable questions in top-level or entry-level `gaps`; each gap
  must include enough evidence context to be actionable.
- Keep references aligned with the attached `systems.yml`, `repo-map.yml`,
  `processes.yml`, `integrations.yml`, `teams.yml`, `glossary.md`, and
  `handoff-rules.md`.

## Output rules

Return the complete updated `bounded-contexts.yml` content only.
Do not include commentary, markdown fences, diffs, or explanations.
