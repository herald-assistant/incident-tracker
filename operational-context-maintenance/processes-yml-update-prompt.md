# processes.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

The maintenance UI uses actor/system selectors for `participants` and ordered
cards for `steps`. Step references use the canonical `references` object read
by the runtime relation graph; legacy `match` is preserved by the backend but
new guided signals are written to `matchSignals.strong.terms`.

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
      - id: crm-request-validation-failed
        name: CRM request validation failed
        summary: The anonymized CRM request is rejected before it can be accepted for handling.
        affectedStep: submit-request
        signals:
          - CRM request validation rejection
          - CRM case confirmation is absent
    dataAndArtifacts:
      primaryObjects:
        - CrmCustomerRequest
      inputArtifacts:
        - Anonymized CRM customer request
      outputArtifacts:
        - CRM case acceptance confirmation
      persistedEntities:
        - CrmCustomerRequest
      readModels:
        - CRM case handling view
      auditArtifacts:
        - CRM request change audit metadata
      notes:
        - Store artifact kinds only, never CRM customer records or payloads.
```

## Guided CRM process semantics

Maintain the process boundary, lifecycle and completion evidence as separate
canonical objects. The example is strongly anonymized and uses only the CRM
domain:

```yaml
processBoundary:
  businessCapability: CRM Contact Preference Management
  startsWhen:
    - An anonymized CRM contact update is accepted.
  endsWhen:
    - The CRM contact view confirms the update.
  includes:
    - CRM contact preference validation
  excludes:
    - Authentication credential lifecycle
  assumptions:
    - CRM contact identity is already resolved.
lifecycle:
  triggers:
    - type: api
      name: CRM contact update
  entryCriteria:
    - CRM contact identity is available.
  statuses:
    - requested
    - applied
  transitions:
    - from: requested
      to: applied
      trigger: CRM validation succeeds.
  terminalStates:
    - applied
  successOutcomes:
    - CRM contact preference is applied.
  partialOutcomes:
    - CRM projection remains pending.
  failedOutcomes:
    - CRM validation rejects the update.
  cancellationOutcomes:
    - CRM agent cancels the update.
completionSignals:
  successful:
    - CRM contact confirmation is recorded.
  partial:
    - CRM projection remains pending.
  failed:
    - CRM validation rejection is recorded.
  cancelled:
    - CRM cancellation is recorded.
```

## Update rules

- Model a process only when it is meaningful to a business or system analyst.
- Steps should be user, business, system or bounded-context milestones.
- Use `references` to connect systems, repositories, contexts, integrations,
  glossary terms and handoff rules.
- Keep step `matchSignals` as business words or durable labels.
- Keep `processBoundary` limited to `businessCapability`, `startsWhen`,
  `endsWhen`, `includes`, `excludes` and `assumptions`. Boundary assumptions
  are limitations, never confirmed runtime evidence.
- Keep lifecycle trigger cards explicit (`type`, `name`, optional `exchange`)
  and transition cards explicit (`from`, required `to`, required `trigger`).
  Lifecycle is descriptive operational context, not workflow configuration.
- Keep `completionSignals` as observable evidence grouped into `successful`,
  `partial`, `failed` and `cancelled`. Do not copy lifecycle outcomes into this
  field unless they are independently observable facts.
- Legacy non-blank string/list shapes remain readable. Normalize them only
  after actual editing: boundary to `endsWhen`, lifecycle to `statuses` and
  completion signals to `successful`. Preserve unknown object extensions.
- Keep process `failureModes` as guided cards with unique kebab-case `id`,
  required `name` and `summary`, optional existing `affectedStep`, and
  observable `signals`; they are hypotheses, not confirmed root causes.
- Keep `dataAndArtifacts` to durable artifact kinds in `primaryObjects`,
  `inputArtifacts`, `outputArtifacts`, `persistedEntities`, `readModels`,
  `auditArtifacts` and `notes`; never copy CRM records or payloads.
- Put uncertainty in open questions rather than inventing a process path.

## Quality check

- The process can be used to write user stories or test scenarios.
- The process helps decide the next system or bounded context when analysis
  must continue.
- Ownership is not stored here; it is derived from referenced systems and
  bounded contexts.
- The entry does not duplicate details available in code or tools.
