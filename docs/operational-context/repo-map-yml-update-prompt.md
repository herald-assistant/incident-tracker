# repo-map.yml update prompt

Update only `repo-map.yml`.

## Goal

Keep a short map from runtime evidence to GitLab repositories and modules.
This includes both main service repositories and library/shared repositories
that can contain classes used by a deployed component.

## Instructions

1. Read the current `repo-map.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `project`, `group`, `ownerTeamId`, `systems`, `processes`, `contexts`, `modules`, `signals`, `handoff`.
- Prefer paths, packages, class hints, and project names over generic repo description.
- Add library, shared module, generated client, or integration repositories when they are part of a system's code search scope. Link them back to the system through `systems`.
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

  - id: app-shared-lib-repo
    project: libs/app-shared-lib
    group: example/platform
    ownerTeamId: core-team
    systems: [app-core]
    processes: [main-process]
    contexts: [core-context]
    modules:
      - id: shared-domain-module
        name: Shared Domain Module
        paths: [src/main/java/com/example/shared]
        packages: [com.example.shared]
        classHints: [SharedPredicate, SharedLookupClient]
    signals:
      projectNames: [app-shared-lib]
      serviceNames: []
      containerNames: []
      packagePrefixes: [com.example.shared]
      endpoints: []
      hosts: []
      queues: []
      topics: []
      schemas: []
      spans: []
      markers: []
      errors: []
    handoff:
      target: core-team
      requiredEvidence: [correlationId, environment, gitLabBranch, project, filePath, className, exception]

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
