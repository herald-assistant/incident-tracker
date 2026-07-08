# systems.yml update prompt

## Purpose

Update `systems.yml` as the canonical catalog of durable systems. A system entry
helps an analyst decide what business/system area is involved, who owns it at
system level, what other semantic catalog entries are related, and where
analysis should continue.

Keep this file compact. Facts that can be found by GitLab tools, logs, external
observability, repository discovery, or `code-search-scopes.yml` do not belong
here.

Systems do not reference repositories directly. When code navigation is needed,
create or update a `code-search-scopes.yml` entry whose `target` is this system
or, preferably, the bounded context that owns the behavior.

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
  - id: customer-portal
    name: Customer Portal
    shortName: Portal
    kind: internal-application
    lifecycleStatus: active
    operationalStatus: live
    criticality: high
    summary: Customer-facing area used to start and track customer requests.
    purpose: Gives business users one recognizable entry point for request handling.
    aliases:
      - portal
      - customer self service
    useFor:
      - Start incident or requirement analysis when the signal points to customer request handling.
      - Resolve system-level ownership when the bounded context is unknown.
    participants:
      externalOwner: ""
    ownership:
      ownerTeamIds:
        - customer-experience-team
      ownerLabel: ""
      ownershipStatus: explicit
      confidence: high
      source: systems.yml
      notes:
        - Confirmed system-level accountable owner.
    references:
      processes:
        - customer-request-handling
      boundedContexts:
        - customer-requests
      integrations:
        - portal-to-case-management
      teams:
        - customer-experience-team
      handoffRules:
        - customer-request-boundary
    matchSignals:
      exact:
        terms:
          - Customer Portal
      strong:
        aliases:
          - portal
          - self service
      weak:
        phrases:
          - customer request screen
    relations:
      - type: uses
        targetType: process
        target: customer-request-handling
        evidence: primary business process for the system
```

## Update rules

- Prefer stable business and system names over incidental strings.
- Keep `ownership` only for durable system-level accountability.
- Use `ownerTeamIds` when the team exists in `teams.yml`; use `ownerLabel` only
  when the owner is an external/domain label without a cataloged team.
- Keep `references` only for catalog entities that are useful for navigation.
  `references.teams` is not an ownership source; it only links the owner label
  to the team catalog.
- Do not add `references.repositories`; code navigation goes through
  `code-search-scopes.yml`.
- Keep `matchSignals` small and durable. Use names, aliases and business terms.
- Use `relations` for meaningful navigation that is not already obvious from
  another typed field.
- If ownership is uncertain, set low confidence or add an open question instead
  of inventing a team.

## Quality check

- Every entry has `id`, `name`, `summary`, `purpose` and useful navigation.
- Ownership is present only when there is a durable system-level owner.
- References point to existing semantic catalog ids or are intentionally left
  empty.
- No entry contains `references.repositories`.
- The entry helps a business analyst decide where to start or which system
  owner/bounded context should be checked next.
