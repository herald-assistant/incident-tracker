# teams.yml update prompt

## Purpose

Update `teams.yml` as the ownership and routing catalog. A team entry should
explain what the team owns, when to involve it, which catalog entities it is
responsible for, and what evidence helps the team act.

Keep the file focused on responsibility and handoff. Do not add inventory from
other sources or long operational runbooks.

## YAML shape

```yaml
teams:
  - id: customer-experience-team
    name: Customer Experience Team
    shortName: Customer Experience
    lifecycleStatus: active
    summary: Owns customer-facing request intake and portal behavior.
    purpose: First responder for user-facing request issues and related requirement analysis.
    aliases:
      - CX team
      - portal team
    useFor:
      - Route issues described in customer request or portal language.
      - Review development stories for request intake behavior.
      - Confirm acceptance criteria for user-facing journeys.
    references:
      systems:
        - customer-portal
      repositories:
        - customer-portal-ui
      processes:
        - customer-request-handling
      boundedContexts:
        - customer-requests
      integrations:
        - portal-to-case-management
      terms:
        - customer-request
      handoffRules:
        - route-customer-request-issues
    responsibilities:
      - teamId: customer-experience-team
        targetType: system
        targetId: customer-portal
        role: owner
        scope: user-facing behavior and request intake
        status: current
        confidence: high
        evidence: team catalog
        source: teams.yml
      - teamId: customer-experience-team
        targetType: bounded-context
        targetId: customer-requests
        role: domain-owner
        scope: local language and business rules
        status: current
        confidence: high
        evidence: team catalog
        source: teams.yml
    matchSignals:
      exact:
        names:
          - Customer Experience Team
      strong:
        aliases:
          - portal team
          - CX team
      weak:
        phrases:
          - customer request owner
    handoffHints:
      defaultRouteLabel: Customer Experience support
      firstResponderTeamIds:
        - customer-experience-team
      partnerTeamIds:
        - case-management-team
      requiredEvidence:
        - affected customer journey
        - business request id when available
      preferredEvidence:
        - screenshot or user-facing error text
      expectedFirstActions:
        - Confirm the affected journey and whether another owner is needed.
      whenToRouteHere:
        - The issue affects request intake or portal-visible behavior.
      whenToInvolveAsPartner:
        - Another owner is investigating a problem with portal-facing impact.
      whenNotToRouteHere:
        - The issue is only downstream after a confirmed handoff.
      fallbackIfAmbiguous: Ask for the affected journey before changing owner.
    relations:
      - type: partners-with
        targetType: team
        target: case-management-team
        evidence: shared request handling process
```

## Update rules

- A team entry should answer "who should be involved and why?".
- Use responsibilities for typed ownership; use handoff hints for routing.
- Keep required evidence actionable and user-facing.
- Do not duplicate every relationship already owned by systems, processes or
  integrations unless it helps routing.
- If ownership is uncertain, use `confidence` and add an open question.
