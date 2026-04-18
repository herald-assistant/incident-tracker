Update only `teams.yml` and return the full ready-to-save YAML document only.

You are building the operational ownership map for this repository. Read all attached sources and produce the final content of `src/main/resources/operational-context/teams.yml`.

Important repository-specific constraints:
- Preserve the top-level shape:
  - `schemaVersion: 1`
  - `tribe: { id: ..., name: ... }`
  - `teams: [...]`
  - `openQuestions: [...]`
- Each team entry should use this minimal structure:
  - `id`
  - `name`
  - `purpose`
  - `owns.systems`
  - `owns.repos`
  - `owns.processes`
  - `owns.contexts`
  - `owns.integrations`
  - `signals.serviceNames`
  - `signals.containerNames`
  - `signals.projectNames`
  - `signals.packagePrefixes`
  - `signals.endpoints`
  - `signals.hosts`
  - `signals.queues`
  - `signals.topics`
  - `handoff.target`
  - `handoff.requiredEvidence`

How to derive teams:
1. Start from the current `teams.yml`.
2. Use `systems.yml`, `repo-map.yml`, `processes.yml`, `integrations.yml`, and `bounded-contexts.yml` as the primary source of truth for ownership and ids.
3. Use `handoff-rules.md` to align likely routing targets and required evidence.
4. Use attached incident analysis exports only to enrich short runtime signals and to confirm recurring routing patterns.
5. Do not use architecture docs or incident logs to invent ownership that is not supported by the operational-context sources.

Hard rules:
- Reuse ids from attached files exactly as they appear. Do not rename system, repo, process, context, or integration ids.
- Do not invent ownership.
- Prefer explicit owner fields from attached sources over inference from incidents.
- If evidence is not strong enough, keep the current value or add an `openQuestions` entry.
- If a team id is clearly implied by attached sources but the display name is not explicit, derive a readable title-cased name from the id and add an `openQuestions` note to confirm it.
- If `tribe` is not clearly evidenced, keep:
  - `id: null`
  - `name: null`
- Keep lists short and operational.
- Prefer stable identifiers over prose.
- Do not duplicate teams or create aliases for the same team.
- Do not treat GitLab group or environment as a team.
- Do not output explanations, markdown fences, comments, or anything except the final YAML.

What matters most:
- `owns.*` must be consistent with the ids used in the other attached operational-context files.
- `signals.*` are secondary and should only contain recurring, incident-useful fingerprints.
- `handoff.target` and `handoff.requiredEvidence` should be short and practical.

Universal examples below are illustrative only.
Do not copy ids, names, or values from the examples unless they are supported by the attached sources.

Example 1: clear ownership across sources

If the attached files say:
- `systems.yml` contains system `payments-api` with `ownerTeamId: payments-team`
- `repo-map.yml` contains repository `payments-api-repo` with `ownerTeamId: payments-team`
- `processes.yml` contains process `payment-capture` with `ownerTeamId: payments-team`
- `bounded-contexts.yml` contains context `payments` with `ownerTeamId: payments-team`
- `integrations.yml` contains integration `payments-to-ledger-sync` with `ownerTeamId: payments-team`

Then a valid team entry could look like this fragment:

- id: payments-team
  name: Payments Team
  purpose: Owns payment capture and settlement flow.
  owns:
    systems: [payments-api]
    repos: [payments-api-repo]
    processes: [payment-capture]
    contexts: [payments]
    integrations: [payments-to-ledger-sync]
  signals:
    serviceNames: [payments-api]
    containerNames: [payments-api]
    projectNames: [payments-api-repo]
    packagePrefixes: [com.example.payments]
    endpoints: [/api/payments, /api/settlements]
    hosts: []
    queues: []
    topics: [payments.events]
  handoff:
    target: payments-oncall
    requiredEvidence: [correlationId, environment, serviceName, endpoint, exception]

Reason:
- ownership is explicit in attached sources
- ids are reused exactly
- signals stay short and operational

Example 2: runtime signals exist but ownership is unclear

If the attached files show only:
- incidents mention host `crm.partner.local` and endpoint `/crm/customers`
- no `ownerTeamId` for that area in `systems.yml`, `repo-map.yml`, `processes.yml`, `integrations.yml`, or `bounded-contexts.yml`
- no such team already exists in current `teams.yml`

Then do not invent a new team.
Keep ownership unchanged and add an open question such as:

openQuestions:
  - "Who owns the CRM customer integration exposed via host `crm.partner.local` and endpoint `/crm/customers`?"

Reason:
- runtime evidence alone is not enough to assign ownership
- open question is better than fabricated routing

If the attached evidence is too weak to create a confident team entry, keep `teams: []` for that area and add a precise item to `openQuestions`.

Return the full updated YAML only.
