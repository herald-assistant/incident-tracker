# systems.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `systems.yml` as the canonical catalog of durable systems. A system entry
helps an analyst decide what business/system area is involved, who owns it at
system level, what other semantic catalog entries are related, and where
analysis should continue.

Keep this file compact. Facts that can be found by GitLab tools, logs, external
observability, repository discovery, or `code-search-scopes.yml` do not belong
here.

Systems do not reference repositories directly. When code navigation is needed,
create or update the single `code-search-scopes.yml` entry whose `target` is
this system.

## Ownership rule

`systems.yml` may define ownership. System ownership is the fallback owner when
the affected bounded context is unknown or has no explicit ownership.

Use bounded-context ownership first whenever the problem is clearly inside a
bounded context. Use system ownership when the problem is system-wide,
infrastructure-facing, integration-facing, or only the system is known.

Keep ownership in the YAML shape below. If accountability is unclear, use a
supported gap or draft visibility limit. Never persist an inferred owner label
with low confidence.

## When to add or change a system

Add or update a system when the evidence shows a durable business/application
area, external partner system, platform capability, or operator tool that users
need to recognize across analyses.

Do not create a system from one weak clue. If the clue is ambiguous, add an
open question or validation note instead.

## YAML shape

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
systems:
  - id: crm-contact-core
    name: CRM Contact Core
    shortName: Contact Core
    systemType: internal-service
    systemSubtype: backend
    lifecycleStatus: active
    criticality: high
    summary: Validates and maintains anonymized CRM contact preferences.
    purpose: Applies contact channel choices and exposes their durable state.
    aliases:
      - CRM Contact Service
    useFor:
      - Start analysis for the CRM Contact Core system.
    runtime:
      configurationDirectory: crm/contact-service
    ownership:
      ownerTeamIds:
        - crm-domain-team
      ownershipStatus: explicit
      confidence: high
      source: Explicit operator statement in the fictional CRM scenario.
    matchSignals:
      exact:
        serviceNames:
          - crm-contact-service
      strong:
        names:
          - CRM Contact Core
    notes:
      - Context assignments are maintained on bounded contexts; code belongs in scopes.
  - id: crm-agent-portal
    name: CRM Agent Portal
    systemType: internal-service
    systemSubtype: frontend
    lifecycleStatus: active
    summary: Lets a CRM agent review and change anonymized contact preferences.
    purpose: Provides the user-facing contact preference journey.
    ownership:
      ownerTeamIds:
        - crm-experience-team
      ownershipStatus: explicit
      confidence: high
      source: Explicit operator statement in the fictional CRM scenario.
    matchSignals:
      exact:
        serviceNames:
          - crm-agent-portal-ui
```

The UI exposes `participants.externalOwner` and
`runtime.configurationDirectory` as guided inputs. Add `participants` only for
an externally operated boundary, for example `externalOwner: CRM managed
platform provider`; omit it for a locally operated system. Use `runtime` only
for the repository-relative Config Drift configuration directory. Keep service,
deployment and application identities in `matchSignals`.

## Update rules

- Prefer stable business and system names over incidental strings.
- Use only canonical `systemType`; do not write legacy `type` or `kind`.
- Every `internal-service` must declare exactly one `systemSubtype` from:
  `frontend`, `backend`, `worker`, `mixed`, `unknown`. Omit `systemSubtype`
  for all other system types.
- Use `unknown` when reviewed evidence does
  not support a narrower subtype. Never infer `frontend` or `backend` from a
  repository name, `package.json`, build file or framework alone.
- A UI selectable by UI Explorer must be modeled as its own durable system
  with `systemType: internal-service` and `systemSubtype: frontend`, even when
  its repository is a monorepo shared with another deployable system.
- Keep `ownership` only for durable system-level accountability.
- Use `ownerTeamIds` when the team exists in `teams.yml`; use `ownerLabel` only
  when the owner is an external/domain label without a cataloged team.
- Keep `references` only for catalog entities that are useful for navigation.
  `references.teams` is not an ownership source; it only links the owner label
  to the team catalog.
- Do not add `references.repositories`; code navigation goes through
  `code-search-scopes.yml`.
- Ensure this system has exactly one system-targeted code-search scope when it
  is an internal system or otherwise requires code discovery.
- Keep `matchSignals` small and durable. Use names, aliases and business terms.
  All system signal strengths, aliases and `useFor` participate in GitLab
  application matching. Do not preserve unrelated metadata as `weak` signals;
  compare code-scope selection before and after identity changes.
- Keep the optional `runtime.configurationDirectory` safe and
  repository-relative. Do not put service or deployment identities in
  `runtime`.
- Use `participants.externalOwner` only for external operational
  responsibility; local team ownership belongs in `ownership`.
- Use `relations` for meaningful navigation that is not already obvious from
  another typed field.
- Keep explicit context-to-system assignments on the bounded context, process
  roles on the process and integration roles in integration participants. Do
  not add inverse references or duplicate integration dependencies for discovery.
- If ownership is uncertain, use a supported gap or draft visibility limit.
  Ownership proposals require an explicit operator statement and confirmation.

## Quality check

- Every entry has `id`, `name`, `summary`, `purpose` and useful navigation.
- Every `internal-service` has a supported explicit `systemSubtype`; frontend
  classification is backed by reviewed catalog evidence.
- Ownership is present only when there is a durable system-level owner.
- References point to existing semantic catalog ids or are intentionally left
  empty.
- No entry contains `references.repositories`.
- The entry helps a business analyst decide where to start or which system
  owner/bounded context should be checked next.
