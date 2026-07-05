# teams.yml update prompt

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

```yaml
teams:
  - id: customer-experience-team
    name: Customer Experience Team
    shortName: Customer Experience
    lifecycleStatus: active
    summary: Team visible as owner for customer-facing request intake and portal behavior.
    purpose: Helps analysts understand the team label when ownership resolver returns this team.
    aliases:
      - CX team
      - portal team
    useFor:
      - Recognize the team when returned from system or bounded-context ownership.
      - Understand collaboration language for request intake behavior.
      - Confirm who can review user-facing journey assumptions when already selected as owner.
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
        - customer-request-boundary
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
    relations:
      - type: collaborates-with
        targetType: team
        target: case-management-team
        evidence: shared request handling process
```

## Update rules

- A team entry should answer "what does this team label mean?".
- Use `references` for navigation and context only; they do not assign
  ownership.
- Keep evidence/action guidance in handoff rules, not in team routing hints.
- Do not duplicate every relationship already described by systems, processes
  or integrations.
- If ownership is uncertain, add an open question on the owning system or
  bounded context instead of adding reverse ownership here.
