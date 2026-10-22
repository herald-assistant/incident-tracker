# processes.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

The maintenance UI uses actor/system selectors for `participants` and ordered
cards for `steps`. Step references use the canonical `references` object read
by the runtime relation graph; guided signals use `matchSignals.strong.terms`.

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

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
processes:
  - id: crm-contact-preference-update
    name: CRM Contact Preference Update
    type: business-process
    lifecycleStatus: active
    criticality: high
    summary: A CRM agent submits a contact preference and verifies its accepted state.
    purpose: Grounds the expected CRM journey, milestones and handoff.
    aliases:
      - CRM contact preference change
    useFor:
      - Explain the expected contact update journey.
      - Identify the next system or context to inspect.
    participants:
      actors:
        - CRM agent
      primarySystems:
        - crm-agent-portal
      supportingSystems:
        - crm-contact-core
    processBoundary:
      startsWhen:
        - The CRM agent submits an anonymized contact preference.
      endsWhen:
        - The CRM contact view confirms the accepted preference.
    completionSignals:
      successful:
        - CRM contact acceptance confirmation is visible.
    references:
      boundedContexts:
        - crm-contact-preferences
      integrations:
        - crm-contact-to-core
      terms:
        - crm-contact-preference
      handoffRules:
        - crm-contact-sync-delayed
    matchSignals:
      strong:
        businessTerms:
          - CRM contact preference change
    steps:
      - id: submit-contact-update
        name: Submit CRM contact update
        type: user-action
        summary: The CRM agent submits an anonymized contact channel choice.
        references:
          systems:
            - crm-agent-portal
        matchSignals:
          strong:
            terms:
              - submit CRM contact update
      - id: accept-contact-update
        name: Accept CRM contact update
        type: system-handoff
        summary: CRM Contact Core validates and accepts the preference.
        references:
          systems:
            - crm-contact-core
          boundedContexts:
            - crm-contact-preferences
          integrations:
            - crm-contact-to-core
    failureModes:
      - id: crm-request-validation-failed
        name: CRM request validation failed
        summary: The anonymized CRM contact update is rejected before acceptance.
        affectedStep: accept-contact-update
        signals:
          - CRM contact validation rejection
    dataAndArtifacts:
      primaryObjects:
        - ContactPreference
      inputArtifacts:
        - Anonymized CRM contact change request
      outputArtifacts:
        - CRM contact acceptance confirmation
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
- Use `references` for useful navigation to repositories, contexts,
  integrations, glossary terms and handoff rules. Participant system roles
  already connect the process to systems; do not duplicate them in
  `references.systems`.
- This process owns its context and integration references. Do not request the
  inverse reference on those entities; effective navigation supplies both ends.
  Use step references for a distinct milestone role, not a second root-level
  `relations` edge with the same meaning.
- Do not use `outcomes` or `observability`; they are outside the process schema.
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
- `processBoundary`, `lifecycle` and `completionSignals` are objects with the
  documented fields. Unknown keys are removed when the entity is updated.
- Keep process `failureModes` as guided cards with unique kebab-case `id`,
  required `name` and `summary`, optional existing `affectedStep`, and
  observable `signals`; they are hypotheses, not confirmed root causes.
- Keep `dataAndArtifacts` to durable artifact kinds in `primaryObjects`,
  `inputArtifacts`, `outputArtifacts`, `persistedEntities`, `readModels`,
  `auditArtifacts` and `notes`; never copy CRM records or payloads.
- Processes have no entity-level `gaps` or `sourceCoverage` field. Put durable
  uncertainty in an accepted descriptive field and draft `visibilityLimits`,
  rather than inventing a process path or an unsupported property.

## Quality check

- The process can be used to write user stories or test scenarios.
- The process helps decide the next system or bounded context when analysis
  must continue.
- Ownership is not stored here; it is derived from referenced systems and
  bounded contexts.
- The entry does not duplicate details available in code or tools.
