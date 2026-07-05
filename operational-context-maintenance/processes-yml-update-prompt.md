# processes.yml update prompt

## Purpose

Update `processes.yml` as the business and operational process catalog. A
process entry explains the user journey, actors, systems, expected outcomes,
failure modes, and handoff boundaries in language useful for analysts, testers,
and development story preparation.

The catalog should say what the business path is and where to continue, not list
incidental clues from another source.

## Ownership rule

Process entries do not define ownership. Owner and handoff are resolved from the
bounded contexts and systems referenced by the process. If a process crosses
several bounded contexts or systems, the resolver should show both sides as
primary/partner owners.

Keep process entries in the YAML shape below. If the process needs ownership or
handoff resolution, add precise system/bounded-context references instead of new
owner-like fields.

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
      - Decide which system or bounded context should be checked next.
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
        - The request is visible to the case handling context.
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
      terms:
        - customer-request
      handoffRules:
        - customer-request-boundary
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
    relations:
      - type: uses
        targetType: integration
        target: portal-to-case-management
        evidence: process boundary between request intake and case handling
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
        summary: Request is accepted and becomes visible in the case handling context.
        references:
          systems:
            - case-management
          boundedContexts:
            - case-lifecycle
          integrations:
            - portal-to-case-management
        matchSignals:
          strong:
            terms:
              - accepted for case handling
    failureModes:
      - Customer cannot complete request intake.
      - Request appears accepted but the next context cannot continue.
```

## Update rules

- Model a process only when it is meaningful to a business or system analyst.
- Steps should be user, business, system or bounded-context milestones.
- Use `references` to connect systems, repositories, contexts, integrations,
  glossary terms and handoff rules.
- Keep step `matchSignals` as business words or durable labels.
- Put uncertainty in open questions rather than inventing a process path.

## Quality check

- The process can be used to write user stories or test scenarios.
- The process helps decide the next system or bounded context when analysis
  must continue.
- Ownership is not stored here; it is derived from referenced systems and
  bounded contexts.
- The entry does not duplicate details available in code or tools.
