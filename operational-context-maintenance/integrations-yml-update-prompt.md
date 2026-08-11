# integrations.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

The maintenance UI uses structured participant cards for `source`, `targets`,
`intermediaries` and `finalTargets`; it serializes the same canonical YAML
shape shown below. Participant-level `repositories` from older data are
preserve-only and are not shown as editable controls; use top-level
`references.repositories` and code-search scopes for code navigation.

## Purpose

Update `integrations.yml` as the catalog of relationships between systems and
external parties. An integration entry should explain who talks to whom, why it
matters, what business process is affected, and where the handoff boundary is.

Keep the entry at system/process level. Do not store low-level communication
details that can be discovered from code, logs, partner documentation, or a
specialized tool.

## Ownership rule

Integration entries do not define ownership. Owner and handoff are resolved from
the source/target bounded contexts first, then source/target systems. If the
problem is on the boundary between systems or contexts, the resolver should
surface owners for both sides. If a system/context is not cataloged with a team,
the resolver may infer labels such as "owner of system Salesforce" or "owner of
domain customer".

Keep integration entries in the YAML shape below. If owner or handoff is needed,
model the participating systems/bounded contexts precisely instead of adding
owner-like fields here.

## YAML shape

```yaml
integrations:
  - id: portal-to-case-management
    name: Portal to Case Management
    shortName: Portal case handoff
    category: internal-collaboration
    lifecycleStatus: active
    summary: Customer portal sends accepted request information to case management.
    purpose: Explains the system boundary for customer request processing.
    integrationStyle: synchronous-request
    flowDirection: outbound
    criticality: high
    aliases:
      - portal case handoff
    useFor:
      - Explain the boundary between portal request intake and case handling.
      - Decide whether an issue appears before, at, or after the handoff boundary.
    participants:
      source:
        system: customer-portal
        boundedContext: customer-requests
        role: initiates customer request handoff
        externalOwner: ""
        notes:
          - Portal owns the user-facing journey before handoff through its system/context ownership.
      targets:
        - system: case-management
          boundedContext: case-lifecycle
          role: receives accepted case state
          externalOwner: ""
          notes:
            - Case handling ownership is resolved from the target system/context.
      intermediaries: []
      finalTargets: []
    references:
      systems:
        - customer-portal
        - case-management
      repositories:
        - customer-portal-ui
        - case-management-service
      processes:
        - customer-request-handling
      boundedContexts:
        - customer-requests
        - case-lifecycle
      handoffRules:
        - customer-request-boundary
    matchSignals:
      exact:
        names:
          - Portal to Case Management
      strong:
        terms:
          - case handoff
      weak:
        phrases:
          - customer request accepted by case team
    relations:
      - type: source-system
        targetType: system
        target: customer-portal
        evidence: participant source
      - type: target-system
        targetType: system
        target: case-management
        evidence: participant target
    failureModes:
      - name: CRM case handoff not visible
        type: delivery-failure
        symptom: The source accepts the anonymized CRM request but the target context does not expose the case.
        impact: CRM case handling cannot continue.
```

## Update rules

- Use `participants` as the source of source/target relationships.
- Do not add or change `participants.*.repositories`; the backend preserves
  legacy values, while editable repository navigation belongs in top-level
  `references` and code-search scopes.
- Use `references` for navigation only; do not duplicate every participant
  unless it helps the UI or AI start analysis.
- `integrationStyle` and `flowDirection` are high-level labels, not a detailed
  detail list.
- Keep `failureModes` as guided cards with required `name` and `type`, plus at
  least an observable `symptom` or durable `impact`. They ground CRM triage and
  handoff hypotheses but never confirm root cause by themselves.
- Prefer clear boundary language over internal labels.
- Do not add team references to imply source or target ownership.
