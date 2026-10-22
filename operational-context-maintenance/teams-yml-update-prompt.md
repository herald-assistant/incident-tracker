# teams.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `teams.yml` as the catalog of team identifiers, labels and collaboration
clues. A team entry should help users recognize a team returned by ownership
resolution and understand why it may be involved.

Keep the file focused on team identity and analyst-readable collaboration
context. Do not add inventory from other sources or long operational runbooks.

## Ownership rule

Team entries do not define ownership. Ownership is assigned only from
`systems.yml` and `bounded-contexts.yml`. `teams.yml` may describe a team so an
owner id is understandable, but it is never a reverse ownership source.

Keep team entries in the YAML shape below. If ownership is missing or unclear,
update the owning system/bounded context or add an open question there.

## YAML shape

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
teams:
  - id: crm-domain-team
    name: CRM Domain Team
    type: product
    lifecycleStatus: active
    summary: Team label for the fictional CRM contact domain.
    purpose: Explains the team returned by explicit system or context ownership.
    aliases:
      - CRM Contact Team
    useFor:
      - Recognize the CRM domain owner label.
    matchSignals:
      exact:
        names:
          - CRM Domain Team
  - id: crm-experience-team
    name: CRM Experience Team
    type: product
    lifecycleStatus: active
    summary: Team label for the fictional CRM agent portal.
    matchSignals:
      exact:
        names:
          - CRM Experience Team
```

## Update rules

- A team entry should answer "what does this team label mean?".
- Team entries do not contain `references` or `relations`. Use the owning
  system or bounded context for relationships.
- Keep evidence/action guidance in handoff rules, not in team routing hints.
- Do not duplicate every relationship already described by systems, processes
  or integrations.
- If ownership is uncertain, add an open question on the owning system or
  bounded context instead of adding reverse ownership here.
