Update only `systems.yml` and return the full ready-to-save YAML document only.

You are building the operational system catalog for this repository. Read all attached sources and produce the final content of `src/main/resources/operational-context/systems.yml`.

Important repository-specific constraints:
- Preserve the top-level shape:
  - `schemaVersion: 1`
  - `systems: [...]`
  - `openQuestions: [...]`
- Each system entry should use this minimal structure:
  - `id`
  - `name`
  - `type`
  - `ownerTeamId`
  - `partnerTeamIds`
  - `externalOwner`
  - `purpose`
  - `processes`
  - `contexts`
  - `repos`
  - `dependsOn`
  - `signals.serviceNames`
  - `signals.containerNames`
  - `signals.projectNames`
  - `signals.packagePrefixes`
  - `signals.endpoints`
  - `signals.hosts`
  - `signals.queues`
  - `signals.topics`
  - `signals.schemas`
  - `signals.spans`
  - `signals.markers`
  - `handoff.target`
  - `handoff.requiredEvidence`

How to derive systems:
1. Start from the current `systems.yml`.
2. Use `teams.yml`, `processes.yml`, `repo-map.yml`, `bounded-contexts.yml`, and `integrations.yml` as the primary source of truth for ids, ownership, scope, and dependencies.
3. Use attached incident analysis exports only to identify recurring runtime fingerprints such as service names, container names, project names, package prefixes, endpoint paths, hosts, queue/topic names, spans, and markers.
4. Use `handoff-rules.md` to align handoff targets and required evidence.
5. Merge duplicate observations into one stable system entry.

Hard rules:
- Reuse ids from attached files exactly as they appear. Do not rename team, process, context, repository, or integration ids.
- `systems.yml` is the map of runtime systems as a whole, not only external systems.
- Use `type` to distinguish systems, for example `internal` vs `external`.
- Do not invent ownership.
- Do not invent repositories for external systems.
- Do not create separate system entries for the same system just because incidents show different environments, branches, pods, namespaces, host variants, or deployment versions.
- Do not model individual interfaces or single endpoints as separate systems; those belong in `integrations.yml`.
- Keep one system entry per meaningful operational runtime component or external dependency.
- Prefer stable runtime recognition signals over prose.
- Prefer short, reusable signals over copied log bodies.
- Do not paste full stacktraces, long exception bodies, tokens, or transient URLs into signals.
- `dependsOn` should reference system ids when that dependency is clearly supported by attached sources.
- If a dependency is visible only once or weakly inferred, prefer an `openQuestions` entry instead of guessing.
- Do not output explanations, markdown fences, comments, or anything except the final YAML.

What matters most:
- each system must be recognizable from runtime evidence
- `ownerTeamId`, `partnerTeamIds`, `processes`, `contexts`, and `repos` must stay consistent with the other attached operational-context files
- internal and external systems may coexist in this file
- `integrations.yml` describes contracts between systems, while `systems.yml` describes the systems themselves

Repository-specific guidance:
- This repo enriches incidents from Elasticsearch, Dynatrace, and deterministic GitLab code resolution, so system recognition often comes from both runtime evidence and code references.
- Internal systems are often identified by service names, container names, project names, package prefixes, classes, or GitLab-resolved repositories.
- External systems are often identified by stable hosts, endpoint families, queue/topic names, schemas, or repeated error markers seen in incidents and referenced integrations.
- If an incident shows an external host or endpoint but not enough evidence for a stable system identity, add an `openQuestions` entry instead of fabricating a system.

Universal examples below are illustrative only.
Do not copy ids, names, or values from the examples unless they are supported by the attached sources.

Example 1: internal system with clear ownership

If the attached files say:
- `teams.yml` contains `payments-team`
- `repo-map.yml` contains repository `payments-api-repo`
- `processes.yml` contains process `payment-capture`
- `bounded-contexts.yml` contains context `payments`
- incidents repeatedly show service `payments-api`, container `payments-api`, package prefix `com.example.payments`

Then a valid system entry could look like this fragment:

- id: payments-api
  name: Payments API
  type: internal
  ownerTeamId: payments-team
  partnerTeamIds: []
  externalOwner: null
  purpose: Executes payment capture and settlement flow.
  processes: [payment-capture]
  contexts: [payments]
  repos: [payments-api-repo]
  dependsOn: [ledger-service]
  signals:
    serviceNames: [payments-api]
    containerNames: [payments-api]
    projectNames: [payments-api-repo]
    packagePrefixes: [com.example.payments]
    endpoints: [/api/payments, /api/settlements]
    hosts: []
    queues: []
    topics: [payments.events]
    schemas: []
    spans: [PaymentService.capture]
    markers: [PAYMENTS]
  handoff:
    target: payments-oncall
    requiredEvidence: [correlationId, environment, serviceName, className, endpoint, exception]

Reason:
- one stable runtime component
- ownership is explicit
- signals are short and reusable

Example 2: external system with weak evidence

If the attached sources show only:
- one incident mentions host `crm.partner.local`
- one log line contains endpoint `/crm/customers`
- no explicit owner or stable system identity in `teams.yml`, `repo-map.yml`, `processes.yml`, `bounded-contexts.yml`, or current `systems.yml`

Then do not invent a fully modeled system entry.
Add an open question such as:

openQuestions:
  - "Is `crm.partner.local` a stable external system that should be modeled in `systems.yml`, and if so what is its canonical id and owner?"

Reason:
- one host and one endpoint are not enough to define a stable system entry confidently

If the attached evidence is too weak to create a confident system entry, keep that area out of `systems` and add a precise item to `openQuestions`.

Return the full updated YAML only.
