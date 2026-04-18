# repo-map.yml update prompt

Update only `repo-map.yml`.

## Goal

Keep a short map from runtime evidence to GitLab repositories and modules.

## Instructions

1. Read the current `repo-map.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `project`, `group`, `ownerTeamId`, `systems`, `processes`, `contexts`, `modules`, `signals`, `handoff`.
- Prefer paths, packages, class hints, and project names over generic repo description.
- Do not create duplicate repository entries.
- Preserve the top-level wrapper: `schemaVersion`, `repositories`, `openQuestions`.
- If module ownership is unclear, keep the repo-level owner and add an `openQuestions` entry.

## Example

A correctly filled file can look like this:

```yaml
schemaVersion: 1

repositories:
  - id: app-core-repo
    project: app-core-repo
    group: example/platform
    ownerTeamId: core-team
    systems: [app-core]
    processes: [main-process]
    contexts: [core-context]
    modules:
      - id: core-module
        name: Core Module
        paths: [src/main/java/com/example/app/core]
        packages: [com.example.app.core]
        classHints: [SyncGateway, WorkItemService]
      - id: integration-module
        name: Integration Module
        paths: [src/main/java/com/example/app/integration]
        packages: [com.example.app.integration]
        classHints: [PartnerClient, PartnerSoapGateway]
    signals:
      projectNames: [app-core-repo]
      serviceNames: [app-core]
      containerNames: [app-core]
      packagePrefixes: [com.example.app]
      endpoints: [/api/resources, /partner/resource]
      hosts: [api.partner.local]
      queues: []
      topics: []
      schemas: []
      spans: [SyncGateway.call]
      markers: [APP_CORE]
      errors: [SOAPFault]
    handoff:
      target: core-team
      requiredEvidence: [correlationId, environment, gitLabBranch, project, filePath, className, exception]

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
