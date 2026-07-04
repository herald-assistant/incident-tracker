# bounded-contexts.yml update prompt

## Purpose

Update `bounded-contexts.yml` as the semantic boundary catalog. A bounded context
entry explains local language, responsibility, nearby concepts, ownership, and
related catalog entities. It should help analysts avoid mixing business
meanings that look similar in code, logs, or user reports.

Keep entries semantic. Do not use this file as a map of low-level code details.

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
      - Guide where to continue when a requirement crosses context boundaries.
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
        - route-customer-request-issues
    responsibilities:
      - teamId: customer-experience-team
        targetType: bounded-context
        targetId: customer-requests
        role: domain-owner
        scope: local language and user-facing request behavior
        status: current
        confidence: high
        evidence: product ownership notes
        source: bounded-contexts.yml
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
    handoffHints:
      defaultRouteLabel: Customer request domain owner
      firstResponderTeamIds:
        - customer-experience-team
      partnerTeamIds:
        - case-management-team
      requiredEvidence:
        - affected concept or business status
      expectedFirstActions:
        - Confirm whether the question belongs to request intake or case handling.
      whenToRouteHere:
        - The question uses request intake or user-facing status language.
      whenNotToRouteHere:
        - The question is only about internal case handling after handoff.
    relations:
      - type: hands-off-to
        targetType: bounded-context
        target: case-lifecycle
        via:
          - portal-to-case-management
        evidence: accepted request becomes a case for handling
```

## Update rules

- Capture local language, boundaries, ownership and relations.
- Use `references.terms` for glossary entries that define the local language.
- Add relations only when they help navigation across contexts.
- Keep aliases and match signals stable and business-readable.
- Put unclear boundaries into open questions.

## Quality check

- The entry helps explain the system to an analyst or tester.
- It clarifies what belongs here and what should be handed to another context.
- It does not duplicate code details that tools can discover.
