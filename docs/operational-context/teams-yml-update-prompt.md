# teams.yml update prompt

Update only `teams.yml`.

## Goal

Keep a short operational map of teams:
- who owns what
- how to recognize their area in runtime
- where to hand off

## Instructions

1. Read the current `teams.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `purpose`, `owns`, `signals`, `handoff`.
- Prefer short phrases over long descriptions.
- Do not invent ownership.
- Do not duplicate teams.
- If a fact is unclear, keep the current value and add an `openQuestions` entry.
- Preserve the top-level wrapper: `schemaVersion`, `teams`, `openQuestions`.
- Keep list values short and operational.

## Example

A correctly filled file can look like this:

```yaml
schemaVersion: 1

teams:
  - id: core-team
    name: Core Team
    purpose: Owns the main application flow and the primary GitLab repository.
    owns:
      systems: [app-core]
      repos: [app-core-repo]
      processes: [main-process]
      contexts: [core-context]
      integrations: []
    signals:
      serviceNames: [app-core]
      containerNames: [app-core]
      projectNames: [app-core-repo]
      packagePrefixes: [com.example.app]
      endpoints: [/api/resources]
      hosts: []
      queues: []
      topics: []
    handoff:
      target: tech-lead
      requiredEvidence: [correlationId, environment, serviceName, endpoint, exception]

  - id: integration-team
    name: Integration Team
    purpose: Owns cross-system contracts and partner-facing runtime failures.
    owns:
      systems: []
      repos: []
      processes: [main-process]
      contexts: [partner-context]
      integrations: [app-core-to-partner-sync, app-core-work-item-events]
    signals:
      serviceNames: []
      containerNames: []
      projectNames: []
      packagePrefixes: []
      endpoints: [/partner/resource]
      hosts: [api.partner.local]
      queues: [work-item.sync.queue]
      topics: [work-item.events]
    handoff:
      target: integration-owner
      requiredEvidence: [correlationId, environment, host, endpoint, exception]

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
