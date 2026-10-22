# handoff-rules.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `handoff-rules.yml` as the structured situation-and-evidence playbook.
A rule explains when a CRM boundary handoff is useful, what evidence is needed
and what first action is practical. It does not store the receiving team.

## YAML shape

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
schemaVersion: 1
catalogKind: operational-context-handoff-rules
handoffRules:
  - id: crm-contact-sync-delayed
    title: CRM contact acceptance is not visible
    useWhen:
      - An anonymized CRM contact update was submitted but acceptance is not visible.
    doNotUseWhen:
      - The update is still within its documented processing window.
    requiredEvidence:
      - An anonymized CRM correlation key and observations from both sides.
    expectedFirstAction:
      - Verify submission and acceptance at the CRM integration boundary.
    references:
      systems:
        - crm-agent-portal
        - crm-contact-core
      integrations:
        - crm-contact-to-core
      terms:
        - crm-contact-preference
    notes:
      - Fictional CRM example; team routing is resolved from contexts and systems.
    llmToolHints:
      - Collect evidence from both CRM sides before interpreting the handoff.
    limitations:
      - No conclusion about the current runtime state follows from this rule alone.
gaps:
  - id: crm-handoff-window
    type: human-confirmation-required
    severity: info
    status: open
    summary: Confirm the fictional CRM processing window.
```

## Update rules

- `id` and `title` are required; `id` is immutable after create.
- Keep positive and negative applicability conditions explicit.
- `references` use catalog IDs grouped by type; do not route directly to a
  team.
- Link affected systems, processes and integrations through `references`;
  the rule does not maintain a second list of affected targets.
- If a process already links this rule, do not add the inverse process reference
  solely for discovery. The effective map exposes incoming links.
- Prefer concrete evidence and first actions over generic investigation text.
- Every test, fixture or example must be strongly anonymized and CRM-only.
