# handoff-rules.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `handoff-rules.yml` as the structured situation-and-evidence playbook.
A rule explains when a CRM boundary handoff is useful, what evidence is needed
and what first action is practical. It does not store the receiving team.

## YAML shape

```yaml
schemaVersion: 1
catalogKind: operational-context-handoff-rules
handoffRules:
  - id: crm-contact-sync-delayed
    title: CRM contact synchronization is delayed
    useWhen:
      - An anonymized CRM customer update is not visible downstream.
    doNotUseWhen:
      - The update is inside its documented processing window.
    requiredEvidence:
      - An anonymized CRM correlation key.
    expectedFirstAction:
      - Verify both sides of the CRM synchronization boundary.
    references:
      systems:
        - crm-customer-service
      processes:
        - crm-contact-update
      integrations:
        - crm-contact-sync
      terms:
        - crm-customer-profile
    notes:
      - Strongly anonymized CRM example.
    llmToolHints:
      - Collect evidence from both CRM sides.
    limitations:
      - No production identifiers.
gaps:
  - id: crm-handoff-boundary
    type: ownership-boundary
    severity: info
    status: open
    description: Confirm the anonymous CRM handoff boundary.
```

## Update rules

- `id` and `title` are required; `id` is immutable after create.
- Keep positive and negative applicability conditions explicit.
- `references` use catalog IDs grouped by type; do not route directly to a
  team.
- Link affected systems, processes and integrations through `references`;
  the rule does not maintain a second list of affected targets.
- Prefer concrete evidence and first actions over generic investigation text.
- Every test, fixture or example must be strongly anonymized and CRM-only.
