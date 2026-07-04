# processes.yml update prompt

## Purpose

Update `processes.yml` as the business and operational process catalog. A
process entry explains the user journey, actors, systems, expected outcomes,
failure modes, and handoff boundaries in language useful for analysts, testers,
and development story preparation.

The catalog should say what the business path is and where to continue, not list
incidental clues from another source.

## YAML shape

```yaml
processes:
  - id: customer-request-handling
    name: Customer request handling
    shortName: Request handling
    type: business
    lifecycleStatus: active
    criticality: high
    summary: Customer starts a request, the portal validates it, and case management continues handling.
    purpose: Gives analysts one business path for incidents, changes, and test scenarios.
    aliases:
      - customer request
      - request intake
    useFor:
      - Explain what the user expected to happen.
      - Prepare development stories or acceptance test scenarios.
      - Decide which system owns the next analysis step.
    participants:
      actors:
        - customer
        - support operator
      primarySystems:
        - customer-portal
      supportingSystems:
        - case-management
      externalSystems: []
      platformComponents: []
    processBoundary:
      endsWhen:
        - The request is visible to the owning case team.
    outcomes:
      successArtifacts:
        - customer request accepted
        - case created for handling
    references:
      systems:
        - customer-portal
        - case-management
      repositories:
        - customer-portal-ui
        - case-management-service
      boundedContexts:
        - customer-requests
        - case-lifecycle
      integrations:
        - portal-to-case-management
      teams:
        - customer-experience-team
        - case-management-team
      terms:
        - customer-request
      handoffRules:
        - route-customer-request-issues
    responsibilities:
      - teamId: customer-experience-team
        targetType: process
        targetId: customer-request-handling
        role: process-owner
        scope: request intake and user-facing behavior
        status: current
        confidence: high
        evidence: process ownership notes
        source: processes.yml
    matchSignals:
      exact:
        terms:
          - customer request handling
      strong:
        aliases:
          - request intake
      weak:
        phrases:
          - customer cannot submit a request
    handoffHints:
      defaultRouteLabel: Customer request process owner
      firstResponderTeamIds:
        - customer-experience-team
      partnerTeamIds:
        - case-management-team
      requiredEvidence:
        - business request id when available
        - observed step where the journey stopped
      expectedFirstActions:
        - Map the symptom to one process step before assigning ownership.
    relations:
      - type: uses
        targetType: integration
        target: portal-to-case-management
        evidence: process handoff
    steps:
      - id: submit-request
        name: Submit request
        type: user-action
        summary: Customer enters the request and submits it in the portal.
        references:
          systems:
            - customer-portal
          boundedContexts:
            - customer-requests
        matchSignals:
          strong:
            terms:
              - submit request
      - id: accept-for-handling
        name: Accept for handling
        type: system-handoff
        summary: Request is accepted and handed to the case owner.
        references:
          systems:
            - case-management
          integrations:
            - portal-to-case-management
        matchSignals:
          strong:
            terms:
              - accepted by case team
    failureModes:
      - Customer cannot complete request intake.
      - Request appears accepted but the next owner cannot continue.
```

## Update rules

- Model a process only when it is meaningful to a business or system analyst.
- Steps should be user, business, or ownership milestones.
- Use `references` to connect systems, repositories, contexts, integrations,
  teams, glossary terms and handoff rules.
- Keep step `matchSignals` as business words or durable labels.
- Put uncertainty in open questions rather than inventing a process path.

## Quality check

- The process can be used to write user stories or test scenarios.
- The process helps decide the next system or team when analysis must continue.
- The entry does not duplicate details available in code or tools.
