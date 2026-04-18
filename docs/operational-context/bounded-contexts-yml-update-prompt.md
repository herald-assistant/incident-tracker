# bounded-contexts.yml update prompt

Update only `bounded-contexts.yml`.

## Goal

Keep a short map of semantic boundaries that matter for incidents.

## Instructions

1. Read the current `bounded-contexts.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `ownerTeamId`, `purpose`, `systems`, `repos`, `processes`, `terms`, `signals`, `relations`.
- Focus on language boundaries and incident routing, not full DDD documentation.
- Keep relations short: `target`, `type`, `via`.
- Preserve the top-level wrapper: `schemaVersion`, `boundedContexts`, `openQuestions`.
- If a boundary is unclear, do not invent it; add an `openQuestions` entry.

## Example

A correctly filled file can look like this:

```yaml
schemaVersion: 1

boundedContexts:
  - id: core-context
    name: Core Context
    ownerTeamId: core-team
    purpose: Owns the internal semantics of the main application flow.
    systems: [app-core]
    repos: [app-core-repo]
    processes: [main-process]
    terms: [work-item, soap-fault]
    signals:
      packagePrefixes: [com.example.app.core]
      endpoints: [/api/resources]
      topics: []
      schemas: []
      markers: [APP_CORE]
    relations:
      - target: partner-context
        type: conformist
        via: [app-core-to-partner-sync]

  - id: partner-context
    name: Partner Context
    ownerTeamId: integration-team
    purpose: Represents the external partner contract consumed by the platform.
    systems: [partner-service]
    repos: []
    processes: [main-process]
    terms: [partner-contract, soap-fault]
    signals:
      packagePrefixes: []
      endpoints: [/partner/resource]
      topics: []
      schemas: []
      markers: [PARTNER]
    relations:
      - target: core-context
        type: published-language
        via: [app-core-to-partner-sync]

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
