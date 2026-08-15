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

Keep ownership in the YAML shape below. If accountability is unclear, add an
open question or low-confidence owner label instead of inventing extra routing
fields.

## When to add or change a system

Add or update a system when the evidence shows a durable business/application
area, external partner system, platform capability, or operator tool that users
need to recognize across analyses.

Do not create a system from one weak clue. If the clue is ambiguous, add an
open question or validation note instead.

## YAML shape

```yaml
systems:
  - id: crm-contact-core
    name: CRM Contact Core
    shortName: Contact Core
    systemType: internal-service
    systemSubtype: backend
    lifecycleStatus: active
    operationalStatus: live
    criticality: high
    summary: Anonymized CRM service responsible for contact preference handling.
    purpose: Gives analysts one durable system boundary for CRM contact changes.
    aliases:
      - contact service
      - crm contacts
    useFor:
      - Start analysis when a signal concerns anonymized CRM contact preferences.
      - Resolve system-level ownership when the bounded context is unknown.
    runtime:
      configurationDirectory: crm/contact-service
    ownership:
      ownerTeamIds:
        - crm-domain-team
      ownershipStatus: explicit
      confidence: high
      source: systems.yml
      notes:
        - Confirmed system-level accountable owner.
    references:
      processes:
        - crm-contact-preference-update
      boundedContexts:
        - crm-contact-preferences
      integrations:
        - crm-contact-to-consent
      teams:
        - crm-domain-team
      handoffRules:
        - crm-contact-boundary
    matchSignals:
      exact:
        serviceNames:
          - crm-contact-service
      strong:
        businessTerms:
          - CRM contact preference
      weak:
        phrases:
          - anonymized CRM contact update
    relations:
      - type: uses
        targetType: process
        target: crm-contact-preference-update
        evidence: Primary anonymized CRM process for the system.
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
- Use `unknown` during a deterministic breaking migration when evidence does
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
- Keep the optional `runtime.configurationDirectory` safe and
  repository-relative. Do not put service or deployment identities in
  `runtime`.
- Use `participants.externalOwner` only for external operational
  responsibility; local team ownership belongs in `ownership`.
- Use `relations` for meaningful navigation that is not already obvious from
  another typed field.
- If ownership is uncertain, set low confidence or add an open question instead
  of inventing a team.

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
