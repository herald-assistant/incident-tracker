Update only `integrations.yml` and return the full ready-to-save YAML document only.

You are building the operational integration catalog for this repository. Read all attached sources and produce the final content of `src/main/resources/operational-context/integrations.yml`.

Important repository-specific constraints:
- Preserve the top-level shape:
  - `schemaVersion: 1`
  - `integrations: [...]`
  - `openQuestions: [...]`
- Each integration entry should use this minimal structure:
  - `id`
  - `name`
  - `from`
  - `to`
  - `ownerTeamId`
  - `partnerTeamIds`
  - `externalOwner`
  - `protocol`
  - `type`
  - `processes`
  - `contexts`
  - `signals.serviceNames`
  - `signals.containerNames`
  - `signals.projectNames`
  - `signals.endpoints`
  - `signals.hosts`
  - `signals.queues`
  - `signals.topics`
  - `signals.schemas`
  - `signals.markers`
  - `signals.errors`
  - `handoff.target`
  - `handoff.requiredEvidence`

How to derive integrations:
1. Start from the current `integrations.yml`.
2. Use `systems.yml`, `teams.yml`, `processes.yml`, `bounded-contexts.yml`, and `repo-map.yml` as the primary source of truth for ids, ownership, and scope.
3. Use attached incident analysis exports only to identify recurring runtime fingerprints such as hosts, endpoint paths, queue/topic names, exception markers, and involved services.
4. Use `handoff-rules.md` to align handoff targets and required evidence.
5. If the same contract appears in logs and in GitLab-resolved code, merge it into one integration entry.

Hard rules:
- Reuse ids from attached files exactly as they appear. Do not rename team, system, process, context, or repository ids.
- Prefer `from` and `to` to reference stable system ids from `systems.yml` when those systems are modeled there.
- If one side is an external party and it is not modeled in `systems.yml`, keep the current value or derive a short stable external identifier only if it is strongly evidenced by attached sources.
- Do not invent ownership.
- Do not invent protocol or integration type.
- Keep one entry per meaningful operational contract, not per host, pod, namespace, environment, or single HTTP call instance.
- Do not create separate entries for request and response of the same contract.
- Do not create separate entries for the same contract just because different incidents show different environments or host variants.
- Prefer stable operational signals over long prose.
- Prefer endpoint prefixes, host patterns, queue/topic names, service names, container names, project names, and short error markers.
- Do not copy full URLs with transient parameters, long exception bodies, tokens, or whole stacktraces into signals.
- If evidence points only to a host or endpoint but not to a clearly identifiable contract, add an `openQuestions` entry instead of inventing a new integration.
- Do not output explanations, markdown fences, comments, or anything except the final YAML.

What matters most:
- `from`, `to`, `ownerTeamId`, `partnerTeamIds`, `processes`, and `contexts` must be consistent with other attached operational-context files.
- `signals.*` should make the integration recognizable during incident triage.
- `handoff.target` and `handoff.requiredEvidence` should be short and practical.

Repository-specific guidance:
- This repo enriches incidents from Elasticsearch, Dynatrace, and deterministic GitLab code resolution, so integration signals often come from both runtime evidence and code references.
- Treat incident exports as evidence for recognition patterns, not as proof of ownership.
- If a contract is visible through Feign logs, external hostnames, SOAP/REST calls, queue/topic usage, or adapter/service names in resolved code, prefer one merged integration entry for that contract.
- If the attached sources show only internal application flow without a distinct cross-system contract, do not model it as an integration entry.

Universal examples below are illustrative only.
Do not copy ids, names, or values from the examples unless they are supported by the attached sources.

Example 1: clear synchronous external integration

If the attached files say:
- `systems.yml` contains system `orders-api`
- `processes.yml` contains process `order-approval`
- `bounded-contexts.yml` contains context `orders`
- incident evidence repeatedly shows host `rating.example.local`
- logs show endpoint `/api/rating/v1/scores`
- attached sources explicitly indicate `ownerTeamId: risk-integration-team`

Then a valid integration entry could look like this fragment:

- id: orders-to-rating-sync
  name: Orders to Rating synchronous API
  from: orders-api
  to: rating-service
  ownerTeamId: risk-integration-team
  partnerTeamIds: [orders-team]
  externalOwner: null
  protocol: REST
  type: synchronous-http
  processes: [order-approval]
  contexts: [orders]
  signals:
    serviceNames: [orders-api]
    containerNames: [orders-api]
    projectNames: [orders-api-repo]
    endpoints: [/api/rating/v1/scores]
    hosts: [rating.example.local]
    queues: []
    topics: []
    schemas: []
    markers: [FeignClient]
    errors: [GatewayTimeout, Read timed out]
  handoff:
    target: risk-integration-oncall
    requiredEvidence: [correlationId, environment, host, endpoint, exception]

Reason:
- one clear contract
- ownership is explicit
- signals are short and incident-useful

Example 2: evidence is too weak to define a contract

If the attached sources show only:
- one incident mentions host `partner.local`
- one log line contains endpoint `/api/customer`
- no explicit ownership in `systems.yml`, `teams.yml`, `processes.yml`, `bounded-contexts.yml`, or current `integrations.yml`
- no recurring runtime evidence that this is a stable contract

Then do not invent a new integration entry.
Add an open question such as:

openQuestions:
  - "Is `partner.local` with endpoint `/api/customer` a distinct operational integration contract, and if so who owns it?"

Reason:
- host plus endpoint alone is not enough to define ownership or contract boundaries

If the attached evidence is too weak to create a confident integration entry, keep that area out of `integrations` and add a precise item to `openQuestions`.

Return the full updated YAML only.
