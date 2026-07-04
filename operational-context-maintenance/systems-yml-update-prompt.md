# systems.yml update prompt

## Purpose

Update `systems.yml` as the canonical catalog of durable systems. A system entry
helps an analyst decide what business/system area is involved, who owns it, what
other catalog entries are related, and where a handoff should start.

Keep this file compact. Facts that can be found by GitLab tools, logs, external
observability, or repository discovery do not belong here.

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
      - Explain ownership and handoff for portal-facing problems.
    participants:
      externalOwner: ""
    references:
      repositories:
        - customer-portal-ui
      processes:
        - customer-request-handling
      boundedContexts:
        - customer-requests
      integrations:
        - portal-to-case-management
      teams:
        - customer-experience-team
      handoffRules:
        - route-customer-request-issues
    responsibilities:
      - teamId: customer-experience-team
        targetType: system
        targetId: customer-portal
        role: owner
        scope: functional support and triage
        status: current
        confidence: high
        evidence: confirmed by team ownership notes
        source: systems.yml
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
    handoffHints:
      defaultRouteLabel: Customer Experience support
      firstResponderTeamIds:
        - customer-experience-team
      partnerTeamIds: []
      requiredEvidence:
        - user-visible symptom
        - affected business request id when available
      expectedFirstActions:
        - Confirm whether the issue is isolated to portal-facing request handling.
      whenToRouteHere:
        - The user symptom is described in portal or customer request language.
      whenNotToRouteHere:
        - The issue is only about a downstream partner area with no portal impact.
      fallbackIfAmbiguous: Start with customer-experience-team and ask for the affected business journey.
    relations:
      - type: uses
        targetType: process
        target: customer-request-handling
        evidence: primary business process for the system
```

## Update rules

- Prefer stable business and system names over incidental strings.
- Keep `references` only for catalog entities that are useful for navigation.
- Keep `matchSignals` small and durable. Use names, aliases and business terms.
- Put ownership in `responsibilities` or `handoffHints`, not in free text only.
- Use `relations` for meaningful navigation that is not already obvious from
  another owner field.
- If a fact is uncertain, add a note/open question instead of inventing a value.

## Quality check

- Every entry has `id`, `name`, `summary`, `purpose`, owner or handoff guidance.
- References point to existing catalog ids or are intentionally left empty.
- The entry helps a business analyst decide where to start or who to involve.
