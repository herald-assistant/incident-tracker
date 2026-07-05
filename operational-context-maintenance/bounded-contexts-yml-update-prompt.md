# bounded-contexts.yml update prompt

## Purpose

Update `bounded-contexts.yml` as the semantic boundary catalog. A bounded context
entry explains local language, responsibility, nearby concepts, ownership, and
related catalog entities. It should help analysts avoid mixing business
meanings that look similar in code, logs, or user reports.

Keep entries semantic. Do not use this file as a map of low-level code details.

## Ownership rule

`bounded-contexts.yml` may define ownership. Bounded-context ownership has
priority over system ownership whenever the problem can be mapped to a bounded
context.

Use system ownership only when the bounded context is unknown, missing, or the
problem is system-wide. Keep ownership in the YAML shape below; if the boundary
or owner is unclear, add an open question instead of inventing extra routing
fields.

## YAML shape

```yaml
boundedContexts:
  - id: customer-requests
    name: Customer Requests
    shortName: Requests
    lifecycleStatus: active
    summary: Context for request intake, validation, and user-facing request status.
    purpose: Separates customer request language from downstream case handling language.
    aliases:
      - request intake
      - customer request
    useFor:
      - Explain user-facing request concepts to analysts and testers.
      - Disambiguate request intake from case lifecycle handling.
      - Resolve domain ownership before falling back to system ownership.
    ownership:
      ownerTeamIds:
        - customer-experience-team
      ownerLabel: ""
      ownershipStatus: explicit
      confidence: high
      source: bounded-contexts.yml
      notes:
        - Product owner confirmed for local language and business behavior.
    references:
      systems:
        - customer-portal
      repositories:
        - customer-portal-ui
      processes:
        - customer-request-handling
      integrations:
        - portal-to-case-management
      terms:
        - customer-request
      teams:
        - customer-experience-team
      handoffRules:
        - customer-request-boundary
    matchSignals:
      exact:
        terms:
          - customer request
      strong:
        aliases:
          - request intake
      weak:
        phrases:
          - portal request status
    relations:
      - type: hands-off-to
        targetType: bounded-context
        target: case-lifecycle
        via:
          - portal-to-case-management
        evidence: accepted request becomes a case for handling
```

## Update rules

- Capture local language, semantic boundaries, ownership and relations.
- Keep ownership at bounded-context level only when it describes durable domain
  accountability.
- Use `references.terms` for glossary entries that define the local language.
- Add relations only when they help navigation across contexts.
- Keep aliases and match signals stable and business-readable.
- Put unclear boundaries or unclear ownership into open questions.

## Quality check

- The entry helps explain the system to an analyst or tester.
- It clarifies what belongs here and what should be resolved through another
  context or system owner.
- It does not duplicate code details that tools can discover.
