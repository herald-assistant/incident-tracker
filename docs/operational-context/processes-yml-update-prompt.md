# processes.yml update prompt

Update only `processes.yml`.

## Goal

Keep a short map of business processes:
- owner team
- systems involved
- key steps
- completion signals

## Instructions

1. Read the current `processes.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `purpose`, `ownerTeamId`, `partnerTeamIds`, `systems`, `externalSystems`, `repos`, `contexts`, `steps`, `completionSignals`, `handoffHints`.
- Keep steps short and only include operationally useful signals.
- Do not invent process boundaries.
- Preserve the top-level wrapper: `schemaVersion`, `processes`, `openQuestions`.
- If information is uncertain, add it to `openQuestions`.
- Inside YAML flow sequences (`[value1, value2]`), always double-quote any value that contains curly braces `{}`, colons `:`, hash `#`, square brackets `[]`, or commas. Example: `endpointPrefixes: ["/api/resource/{id}/details"]`.
- For endpoint paths or any values with URL path-template placeholders (`{id}`, `{customerId}`, etc.), prefer YAML block sequences (`- item`) over flow sequences, or double-quote every value in the flow sequence.
- Never use TAB characters for indentation. YAML forbids TABs entirely. Use only spaces (2 spaces per level).
- When in doubt, quote the value. Unquoted `{` in a flow sequence breaks YAML parsing.

## Example

A correctly filled file can look like this:

```yaml
schemaVersion: 1

processes:
  - id: main-process
    name: Main Process
    purpose: Handles the main synchronous business flow for work items.
    ownerTeamId: core-team
    partnerTeamIds: [integration-team]
    systems: [app-core]
    externalSystems: [partner-service]
    repos: [app-core-repo]
    contexts: [core-context, partner-context]
    steps:
      - id: receive-request
        name: Receive request and start orchestration
        ownerTeamId: core-team
        systems: [app-core]
        signals:
          serviceNames: [app-core]
          endpoints: [/api/resources]
          hosts: []
          queues: []
          topics: []
          events: [work-item-received]
      - id: call-partner-service
        name: Call Partner Service
        ownerTeamId: integration-team
        systems: [app-core, partner-service]
        signals:
          serviceNames: [app-core]
          endpoints: [/partner/resource]
          hosts: [api.partner.local]
          queues: []
          topics: []
          events: []
    completionSignals: [main-process-completed, work-item-saved]
    handoffHints:
      - If the flow fails on the partner call, route first to the integration owner.
      - If the request never reaches the partner call, keep the incident with the core team.

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
