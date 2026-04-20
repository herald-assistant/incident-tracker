# systems.yml update prompt

Update only `systems.yml`.

## Goal

Keep a short map of systems:
- who owns the system
- what it does
- how to recognize it in logs or telemetry

## Instructions

1. Read the current `systems.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `type`, `ownerTeamId`, `partnerTeamIds`, `externalOwner`, `purpose`, `processes`, `contexts`, `repos`, `dependsOn`, `signals`, `handoff`.
- Prefer concrete runtime signals over architecture prose.
- Do not model every interface here; only keep the signals needed for incident routing.
- Preserve the top-level wrapper: `schemaVersion`, `systems`, `openQuestions`.
- If ownership or topology is unclear, add an `openQuestions` entry.
- Inside YAML flow sequences (`[value1, value2]`), always double-quote any value that contains curly braces `{}`, colons `:`, hash `#`, square brackets `[]`, or commas. Example: `endpoints: ["/api/resource/{id}/details"]`.
- For endpoint paths or any values with URL path-template placeholders (`{id}`, `{customerId}`, etc.), prefer YAML block sequences (`- item`) over flow sequences, or double-quote every value in the flow sequence.
- Never use TAB characters for indentation. YAML forbids TABs entirely. Use only spaces (2 spaces per level).
- When in doubt, quote the value. Unquoted `{` in a flow sequence breaks YAML parsing.

## Example

A correctly filled file can look like this:

```yaml
schemaVersion: 1

systems:
  - id: app-core
    name: App Core
    type: internal
    ownerTeamId: core-team
    partnerTeamIds: [integration-team]
    externalOwner: null
    purpose: Executes the main application behavior and orchestrates the work-item flow.
    processes: [main-process]
    contexts: [core-context]
    repos: [app-core-repo]
    dependsOn: [partner-service]
    signals:
      serviceNames: [app-core]
      containerNames: [app-core]
      projectNames: [app-core-repo]
      packagePrefixes: [com.example.app]
      endpoints: [/api/resources]
      hosts: []
      queues: []
      topics: []
      schemas: []
      spans: [SyncGateway.call]
      markers: [APP_CORE]
    handoff:
      target: core-team
      requiredEvidence: [correlationId, environment, serviceName, className, endpoint, exception]

  - id: partner-service
    name: Partner Service
    type: external
    ownerTeamId: null
    partnerTeamIds: [integration-team]
    externalOwner: partner-owner
    purpose: Provides partner data required by the main application flow.
    processes: [main-process]
    contexts: [partner-context]
    repos: []
    dependsOn: []
    signals:
      serviceNames: []
      containerNames: []
      projectNames: []
      packagePrefixes: []
      endpoints: [/partner/resource]
      hosts: [api.partner.local]
      queues: []
      topics: []
      schemas: []
      spans: []
      markers: [PARTNER]
    handoff:
      target: integration-team
      requiredEvidence: [correlationId, environment, host, endpoint, exception]

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
