Update only `processes.yml` and return the full ready-to-save YAML document only.

You are building the business process map for this repository. Read all attached sources and produce the final content of `src/main/resources/operational-context/processes.yml`.

Important repository-specific constraints:
- Preserve the top-level shape:
  - `schemaVersion: 1`
  - `processes: [...]`
  - `openQuestions: [...]`
- Prefer this normalized process structure:
  - `id`
  - `name`
  - `purpose`
  - `ownerTeamId`
  - `partnerTeamIds`
  - `systems`
  - `externalSystems`
  - `repos`
  - `contexts`
  - `completionSignals`
  - `entryCriteria.triggers`
  - `entryCriteria.inboundArtifacts`
  - `entryCriteria.initiatingSystems`
  - `outcomes.successArtifacts`
  - `outcomes.failureArtifacts`
  - `outcomes.completionSignals`
  - `incidentRouting.likelyExternalSystemIds`
  - `observability.expectedPrimarySignals`
  - `steps[].id`
  - `steps[].name`
  - `steps[].ownerTeamId`
  - `steps[].systems`
  - `steps[].participatingSystems`
  - `steps[].inboundArtifacts`
  - `steps[].outboundArtifacts`
  - `steps[].runtimeFingerprints.serviceNames`
  - `steps[].runtimeFingerprints.containerNames`
  - `steps[].runtimeFingerprints.endpointPrefixes`
  - `steps[].runtimeFingerprints.queueNames`
  - `steps[].runtimeFingerprints.topicNames`
  - `steps[].runtimeFingerprints.eventTypes`
  - `steps[].runtimeFingerprints.batchJobs`
  - `steps[].runtimeFingerprints.hostPatterns`
  - `steps[].completionSignals`
  - `handoffHints`

How to derive processes:
1. Start from the current `processes.yml`.
2. Use `systems.yml`, `teams.yml`, `repo-map.yml`, `integrations.yml`, and `bounded-contexts.yml` as the primary source of truth for ids, ownership, participating systems, and semantic boundaries.
3. Use attached incident analysis exports only to identify recurring operational flow signals such as:
   - service names and containers
   - endpoint families
   - queue/topic usage
   - recurring step names in code and stacktraces
   - repeated controller/service/class hotspots
   - consistent external dependencies visible during the same business flow
4. Use `handoff-rules.md` to align practical routing hints.
5. Merge duplicate observations into one stable business process entry.

YAML syntax safety rules:
- Never use TAB characters for indentation. YAML forbids TABs entirely. Use only spaces (2 spaces per level).
- Inside YAML flow sequences (`[value1, value2]`), always double-quote any value that contains curly braces `{}`, colons `:`, hash `#`, square brackets `[]`, or commas. Example: `endpointPrefixes: ["/api/resource/{id}/details"]` - never `endpointPrefixes: [/api/resource/{id}/details]`.
- For lists of endpoint paths, hosts, or any values that may contain URL path-template placeholders like `{id}`, `{customerId}`, etc., prefer YAML block sequences over flow sequences:

```yaml
# correct - block sequence, no quoting needed
endpoints:
  - /api/resource/{id}/details
  - /api/resource/{id}/summary

# correct - flow sequence with quotes
endpointPrefixes: ["/api/resource/{id}/details"]

# WRONG - unquoted curly braces in flow sequence break YAML parsing
endpointPrefixes: [/api/resource/{id}/details]
```

- When in doubt, quote the value. Plain unquoted strings in flow sequences are fragile.

Hard rules:
- Reuse ids from attached files exactly as they appear. Do not rename team, system, repository, context, or integration ids.
- Do not invent process boundaries.
- Do not create one process per endpoint, controller, Feign call, queue, or exception.
- Keep one process entry per meaningful business or operational flow that is useful during incident triage.
- A process may include multiple technical steps if they belong to one business flow.
- Use `steps` only for operationally meaningful milestones inside the process.
- Keep step descriptions short and grounded in observable runtime or code evidence.
- Prefer `systems`, `externalSystems`, `repos`, and `contexts` that are already modeled in the attached operational-context files.
- If ownership is unclear, keep the current value or add an `openQuestions` entry.
- If a possible flow appears only once in evidence and does not have a stable boundary, do not model it as a new process.
- Prefer recurring completion signals, business artifacts, step fingerprints, and handoff hints over long prose.
- Do not paste full stacktraces, long request/response bodies, tokens, or entire source code into the YAML.
- `handoffHints` should stay short, practical, and routing-oriented.
- Do not output explanations, markdown fences, comments, or anything except the final YAML.

What matters most:
- each process should help incident routing and understanding of what business flow is currently failing
- `ownerTeamId`, `partnerTeamIds`, `systems`, `externalSystems`, `repos`, and `contexts` must stay consistent with the other attached operational-context files
- `steps` should reflect useful operational checkpoints, not every implementation detail
- `completionSignals` should help recognize whether the flow completed, stalled, or failed

Repository-specific guidance:
- This repo enriches incidents from Elasticsearch, Dynatrace, and deterministic GitLab code resolution, so process mapping should combine runtime evidence with resolved code hotspots.
- Repeated controller names, service names, package areas, endpoints, and integration calls are good hints for process steps, but they do not automatically define separate processes.
- If attached incidents show a coherent flow crossing internal code and one or more external dependencies, prefer one process entry with multiple steps over several micro-processes.
- If a flow is mostly internal but repeatedly involves a known external dependency, keep the process and reference the external dependency in `externalSystems` and `incidentRouting.likelyExternalSystemIds`.
- If evidence suggests only a low-level technical operation without a stable business-flow boundary, add an `openQuestions` entry instead of inventing a process.

Universal examples below are illustrative only.
Do not copy ids, names, or values from the examples unless they are supported by the attached sources.

Example 1: one business flow with multiple steps

If the attached files say:
- `teams.yml` contains `payments-team`
- `systems.yml` contains `payments-api`
- `systems.yml` contains external system `rating-service`
- `repo-map.yml` contains `payments-api-repo`
- `bounded-contexts.yml` contains `payments`
- incident and code evidence repeatedly show:
  - endpoint `/api/payments`
  - service `payments-api`
  - class hotspots `PaymentController`, `PaymentService`, `RatingClient`
  - a recurring external call to `rating-service`

Then a valid process entry could look like this fragment:

- id: payment-capture
  name: Payment Capture
  purpose: Collects input, validates rating, and creates a payment decision.
  ownerTeamId: payments-team
  partnerTeamIds: [risk-integration-team]
  systems: [payments-api]
  externalSystems: [rating-service]
  repos: [payments-api-repo]
  contexts: [payments]
  completionSignals: [payment-created, decision-saved]
  entryCriteria:
    triggers: [payment request]
    inboundArtifacts: [payment payload]
    initiatingSystems: [payments-api]
  outcomes:
    successArtifacts: [payment decision, persisted payment]
    failureArtifacts: [validation failure, external rating timeout]
    completionSignals: [payment-created, decision-saved]
  incidentRouting:
    likelyExternalSystemIds: [rating-service]
  observability:
    expectedPrimarySignals: [PaymentController, PaymentService, RatingClient]
  steps:
    - id: accept-payment-request
      name: Accept payment request
      ownerTeamId: payments-team
      systems: [payments-api]
      participatingSystems: [payments-api]
      inboundArtifacts: [payment payload]
      outboundArtifacts: [validated request]
      runtimeFingerprints:
        serviceNames: [payments-api]
        containerNames: [payments-api]
        endpointPrefixes: [/api/payments]
        queueNames: []
        topicNames: []
        eventTypes: []
        batchJobs: []
        hostPatterns: []
      completionSignals: [request-accepted]
    - id: fetch-rating
      name: Fetch external rating
      ownerTeamId: risk-integration-team
      systems: [payments-api, rating-service]
      participatingSystems: [payments-api, rating-service]
      inboundArtifacts: [customer identifiers]
      outboundArtifacts: [rating result]
      runtimeFingerprints:
        serviceNames: [payments-api]
        containerNames: [payments-api]
        endpointPrefixes: []
        queueNames: []
        topicNames: []
        eventTypes: []
        batchJobs: []
        hostPatterns: [rating.example.local]
      completionSignals: [rating-loaded]
  handoffHints:
    - If the flow fails during rating fetch, route first to the integration owner and include host, endpoint, and exception.

Reason:
- one coherent business flow
- steps are operationally meaningful
- external dependency is modeled as part of the flow, not as a separate process

Example 2: repeated technical hotspot but unclear process boundary

If the attached sources show only:
- repeated stacktraces in `DocumentCleanupService`
- one endpoint
- no clear evidence whether this belongs to a larger flow or a standalone business process
- no stable completion signals

Then do not invent a new process entry.
Add an open question such as:

openQuestions:
  - "Does `DocumentCleanupService` belong to an existing business process, or should it be modeled as a separate process with stable completion signals?"

Reason:
- technical hotspots alone are not enough to define a stable process boundary

If the attached evidence is too weak to create a confident process entry, keep that area out of `processes` and add a precise item to `openQuestions`.

Return the full updated YAML only.
