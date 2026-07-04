# integrations.yml update prompt

## Purpose

Update `integrations.yml` as the catalog of relationships between systems and
external parties. An integration entry should explain who talks to whom, why it
matters, what business process is affected, and who should be involved when the
handoff leaves the current owner.

Keep the entry at system/process level. Do not store low-level communication
details that can be discovered from code, logs, partner documentation, or a
specialized tool.

## YAML shape

```yaml
integrations:
  - id: portal-to-case-management
    name: Portal to Case Management
    shortName: Portal case handoff
    category: internal-collaboration
    lifecycleStatus: active
    summary: Customer portal sends accepted request information to case management.
    purpose: Explains the system boundary and handoff owner for customer request processing.
    integrationStyle: synchronous-request
    flowDirection: outbound
    criticality: high
    aliases:
      - portal case handoff
    useFor:
      - Explain which team owns request processing after portal submission.
      - Decide whether an issue belongs to portal behavior or case handling.
    participants:
      source:
        system: customer-portal
        boundedContext: customer-requests
        repositories:
          - customer-portal-ui
        role: initiates customer request handoff
        externalOwner: ""
        notes:
          - Portal owns the user-facing journey before handoff.
      targets:
        - system: case-management
          boundedContext: case-lifecycle
          repositories:
            - case-management-service
          role: owns accepted case state
          externalOwner: ""
          notes:
            - Case team owns processing after handoff.
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
      teams:
        - customer-experience-team
        - case-management-team
      handoffRules:
        - route-customer-request-issues
    responsibilities:
      - teamId: customer-experience-team
        targetType: integration
        targetId: portal-to-case-management
        role: source-owner
        scope: before handoff
        status: current
        confidence: high
        evidence: source system ownership
        source: integrations.yml
      - teamId: case-management-team
        targetType: integration
        targetId: portal-to-case-management
        role: target-owner
        scope: after handoff
        status: current
        confidence: high
        evidence: target system ownership
        source: integrations.yml
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
    handoffHints:
      defaultRouteLabel: Source owner first, target owner if handoff was accepted
      firstResponderTeamIds:
        - customer-experience-team
      partnerTeamIds:
        - case-management-team
      requiredEvidence:
        - customer request id
        - business state before and after handoff if available
      expectedFirstActions:
        - Decide whether the symptom appears before or after the handoff boundary.
      whenToRouteHere:
        - The issue is described as a handoff between portal and case handling.
      whenNotToRouteHere:
        - The problem is entirely inside one system with no cross-system handoff.
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
      - Handoff accepted by source but not visible to target owner.
      - Target owner rejects or cannot continue the business process.
```

## Update rules

- Use `participants` as the owner of source and target relationships.
- Use `references` for navigation only; do not duplicate every participant
  unless it helps the UI or AI start analysis.
- `integrationStyle` and `flowDirection` are high-level labels, not a detailed
  detail list.
- Keep `failureModes` business-visible and useful for triage.
- Prefer clear handoff language over internal labels.
