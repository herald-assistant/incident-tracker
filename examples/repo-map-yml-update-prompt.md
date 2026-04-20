Update only `repo-map.yml` and return the full ready-to-save YAML document only.

You are building the repository and module map for this repository. Read all attached sources and produce the final content of `src/main/resources/operational-context/repo-map.yml`.

Important repository-specific constraints:
- Preserve the top-level shape:
  - `schemaVersion: 1`
  - `repositories: [...]`
  - `openQuestions: [...]`
- Prefer this normalized repository structure:
  - `id`
  - `project`
  - `group`
  - `ownerTeamId`
  - `systems`
  - `processes`
  - `contexts`
  - `gitLab.projectPath`
  - `gitLab.groupPath`
  - `sourceLayout.packageRoots`
  - `sourceLayout.classNamePrefixes`
  - `sourceLayout.importantPaths`
  - `sourceLayout.entrypoints`
  - `runtimeMappings.serviceNames`
  - `runtimeMappings.containerNames`
  - `runtimeMappings.applicationNames`
  - `runtimeMappings.projectNames`
  - `runtimeMappings.endpointPrefixes`
  - `runtimeMappings.queueNames`
  - `runtimeMappings.topicNames`
  - `runtimeMappings.databaseSchemas`
  - `runtimeMappings.logMarkers`
  - `runtimeMappings.traceSpans`
  - `sourceLookupHints.stacktraceHotspots`
  - `sourceLookupHints.likelyEntryClasses`
  - `sourceLookupHints.likelyConfigFiles`
  - `incidentHints.likelyChangeAreas`
  - `modules[].id`
  - `modules[].name`
  - `modules[].paths`
  - `modules[].packages`
  - `modules[].classHints`
  - `modules[].runtimeFingerprints.serviceNames`
  - `modules[].runtimeFingerprints.containerNames`
  - `modules[].runtimeFingerprints.endpointPrefixes`
  - `modules[].runtimeFingerprints.queueNames`
  - `modules[].runtimeFingerprints.topicNames`
  - `modules[].runtimeFingerprints.databaseSchemas`
  - `modules[].runtimeFingerprints.classNameHints`
  - `modules[].sourceLookupHints.stacktraceHotspots`
  - `modules[].sourceLookupHints.likelyEntryClasses`
  - `handoff.target`
  - `handoff.requiredEvidence`

How to derive repository entries:
1. Start from the current `repo-map.yml`.
2. Use `systems.yml`, `teams.yml`, `processes.yml`, `bounded-contexts.yml`, and `integrations.yml` as the primary source of truth for ownership and scope.
3. Use attached incident analysis exports and GitLab-resolved code evidence to identify:
   - canonical project names
   - group/project paths
   - recurring file paths
   - package roots
   - class names and stacktrace hotspots
   - entrypoint classes
   - runtime mappings between logs and repositories
4. If attached sources include GitLab project metadata, prefer canonical GitLab project paths from there.
5. Merge duplicate observations into one stable repository entry.

YAML syntax safety rules:
- Never use TAB characters for indentation. YAML forbids TABs entirely. Use only spaces (2 spaces per level).
- Inside YAML flow sequences (`[value1, value2]`), always double-quote any value that contains curly braces `{}`, colons `:`, hash `#`, square brackets `[]`, or commas. Example: `endpoints: ["/api/resource/{id}/details"]` - never `endpoints: [/api/resource/{id}/details]`.
- For lists of endpoint paths, hosts, or any values that may contain URL path-template placeholders like `{id}`, `{customerId}`, etc., prefer YAML block sequences over flow sequences:

```yaml
# correct - block sequence, no quoting needed
endpoints:
  - /api/resource/{id}/details
  - /api/resource/{id}/summary

# correct - flow sequence with quotes
endpoints: ["/api/resource/{id}/details"]

# WRONG - unquoted curly braces in flow sequence break YAML parsing
endpoints: [/api/resource/{id}/details]
```

- When in doubt, quote the value. Plain unquoted strings in flow sequences are fragile.

Hard rules:
- Reuse ids from attached files exactly as they appear. Do not rename team, system, process, context, or integration ids.
- Do not invent ownership.
- Do not create duplicate repository entries for the same GitLab project.
- Do not create one repository entry per file or per stacktrace frame.
- Use `modules` for meaningful subareas inside one repository when the evidence points to stable code zones.
- If module boundaries are weak, keep the repo-level entry and add an `openQuestions` item instead of inventing modules.
- Prefer canonical repository identity over runtime aliases:
  - if a short runtime `projectName` differs from the real GitLab project path, keep the canonical repo in `project` / `gitLab.projectPath`
  - keep runtime aliases in `runtimeMappings.projectNames`
- Prefer paths, packages, class hints, project names, and stable entrypoints over generic repo descriptions.
- Prefer short reusable signals over copied file contents.
- Do not paste full source code, long stacktraces, tokens, or long exception bodies into the YAML.
- Keep `sourceLayout` and `sourceLookupHints` practical for incident routing and GitLab file targeting.
- `handoff.target` and `handoff.requiredEvidence` should be short and operational.
- Do not output explanations, markdown fences, comments, or anything except the final YAML.

What matters most:
- each repository must be recognizable from runtime evidence and resolved code references
- `ownerTeamId`, `systems`, `processes`, and `contexts` must stay consistent with the other attached operational-context files
- `project`, `group`, and `gitLab.*` should identify the repository more reliably than runtime aliases
- `modules` should help narrow investigation inside a repository, not mirror every directory in the tree

Repository-specific guidance:
- This repo enriches incidents from Elasticsearch, Dynatrace, and deterministic GitLab code resolution, so repository mapping should combine runtime signals with actual code references.
- Repeated `projectName`, `filePath`, `symbol`, `className`, `containerName`, `serviceName`, and stacktrace evidence are especially valuable.
- If attached incidents show one runtime application mapping to multiple code areas inside the same GitLab repository, keep one repository entry and model the code areas as modules.
- If attached incidents show multiple runtime applications pointing to the same repository, keep one repository entry and list the aliases in `runtimeMappings`.
- If evidence only shows a vague runtime alias like `backend` with no stable GitLab path, package roots, or file paths, avoid over-modeling and add an `openQuestions` entry.

Universal examples below are illustrative only.
Do not copy ids, names, or values from the examples unless they are supported by the attached sources.

Example 1: one repository with meaningful modules

If the attached files say:
- `teams.yml` contains `payments-team`
- `systems.yml` contains `payments-api`
- `processes.yml` contains `payment-capture`
- `bounded-contexts.yml` contains context `payments`
- incidents repeatedly show:
  - service `payments-api`
  - container `payments-api`
  - runtime project name `payments-api`
  - file paths under `payments-service/src/main/java/com/example/payments/...`
  - stacktrace hotspots in `PaymentService` and `SettlementService`

Then a valid repository entry could look like this fragment:

- id: payments-api-repo
  project: payments-api
  group: core/platform
  ownerTeamId: payments-team
  systems: [payments-api]
  processes: [payment-capture]
  contexts: [payments]
  gitLab:
    projectPath: [core/platform/payments-api]
    groupPath: [core/platform]
  sourceLayout:
    packageRoots: [payments-service/src/main/java/com/example/payments]
    classNamePrefixes: [com.example.payments]
    importantPaths: [payments-service/src/main/java/com/example/payments, payments-service/src/main/resources]
    entrypoints: [PaymentController, SettlementController]
  runtimeMappings:
    serviceNames: [payments-api]
    containerNames: [payments-api]
    applicationNames: []
    projectNames: [payments-api]
    endpointPrefixes: [/api/payments, /api/settlements]
    queueNames: []
    topicNames: [payments.events]
    databaseSchemas: [payments]
    logMarkers: [PAYMENTS]
    traceSpans: [PaymentService.capture, SettlementService.settle]
  sourceLookupHints:
    stacktraceHotspots: [PaymentService, SettlementService]
    likelyEntryClasses: [com.example.payments.web.PaymentController, com.example.payments.web.SettlementController]
    likelyConfigFiles: [payments-service/src/main/resources/application.yml]
  incidentHints:
    likelyChangeAreas: [capture flow, settlement flow]
  modules:
    - id: payments-capture
      name: Payments Capture
      paths: [payments-service/src/main/java/com/example/payments/capture]
      packages: [com.example.payments.capture]
      classHints: [PaymentService, CaptureCommandHandler]
      runtimeFingerprints:
        serviceNames: [payments-api]
        containerNames: [payments-api]
        endpointPrefixes: [/api/payments]
        queueNames: []
        topicNames: []
        databaseSchemas: [payments]
        classNameHints: [PaymentService]
      sourceLookupHints:
        stacktraceHotspots: [PaymentService]
        likelyEntryClasses: [com.example.payments.web.PaymentController]
    - id: payments-settlement
      name: Payments Settlement
      paths: [payments-service/src/main/java/com/example/payments/settlement]
      packages: [com.example.payments.settlement]
      classHints: [SettlementService]
      runtimeFingerprints:
        serviceNames: [payments-api]
        containerNames: [payments-api]
        endpointPrefixes: [/api/settlements]
        queueNames: []
        topicNames: [payments.events]
        databaseSchemas: [payments]
        classNameHints: [SettlementService]
      sourceLookupHints:
        stacktraceHotspots: [SettlementService]
        likelyEntryClasses: [com.example.payments.web.SettlementController]
  handoff:
    target: payments-oncall
    requiredEvidence: [correlationId, environment, gitLabBranch, project, filePath, className, exception]

Reason:
- one stable GitLab repository
- modules capture meaningful code areas
- runtime and code evidence reinforce each other

Example 2: runtime alias is too weak for module mapping

If the attached sources show only:
- repeated runtime project name `backend`
- generic service name `backend-service`
- no stable GitLab project path
- no recurring file paths, package roots, or class hotspots
- no trustworthy evidence for splitting the repo into modules

Then do not invent many repositories or modules.
Prefer either:
- one cautious repo-level entry if the canonical repository identity is still supported elsewhere, or
- an open question such as:

openQuestions:
  - "Does runtime alias `backend` map to one canonical GitLab repository, and if so what is its stable project path and module breakdown?"

Reason:
- runtime aliases alone are too weak for confident repository/module mapping

If the attached evidence is too weak to create a confident repository entry, keep that area out of `repositories` and add a precise item to `openQuestions`.

Return the full updated YAML only.
