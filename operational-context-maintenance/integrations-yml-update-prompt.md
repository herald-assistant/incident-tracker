# integrations.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

The maintenance UI uses structured participant cards for `source`, `targets`,
`intermediaries` and `finalTargets`; it serializes the same canonical YAML
shape shown below. Use top-level `references.repositories` and code-search
scopes for code navigation; participants have no repository field.

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
the resolver may display an inferred label such as "owner of CRM Contact Core".
Do not save that display inference as explicit ownership.

Keep integration entries in the YAML shape below. If owner or handoff is needed,
model the participating systems/bounded contexts precisely instead of adding
owner-like fields here.

## YAML shape

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
integrations:
  - id: crm-contact-to-core
    name: CRM Portal to Contact Core
    category: internal-api
    lifecycleStatus: active
    summary: The CRM portal submits a contact preference to CRM Contact Core.
    purpose: Explains the boundary between the agent journey and preference acceptance.
    integrationStyle: synchronous-request
    flowDirection: source-to-target
    criticality: high
    participants:
      source:
        system: crm-agent-portal
        role: submits the contact preference
      targets:
        - system: crm-contact-core
          boundedContext: crm-contact-preferences
          role: validates and accepts the contact preference
          notes:
            - The target context owns preference semantics; system ownership remains separate.
    references:
      terms:
        - crm-contact-preference
    matchSignals:
      strong:
        businessTerms:
          - CRM contact preference submission
    failureModes:
      - name: CRM contact handoff not visible
        type: delivery-failure
        symptom: The CRM portal submits an anonymized update but acceptance is not visible.
        impact: The CRM agent cannot confirm the updated preference.
```

## Update rules

- Use `participants` as the source of source/target relationships.
- Do not repeat participant systems or contexts in `references` or in equivalent
  `relations`. A process owns its link to this integration; do not store the
  inverse process reference just for discoverability. The effective map supplies it.
- Omit absent optional participant fields such as `externalOwner`; blank strings
  and empty objects are invalid. Use external-owner labels only when supported.
- Participants have no `repositories` field. Repository navigation belongs in
  top-level `references` and code-search scopes.
- Use editable `references` for navigation only; participant systems already
  define the system relationship, so do not propose `references.systems`.
- `integrationStyle` and `flowDirection` are high-level labels, not a detailed
  detail list.
- Keep `failureModes` as guided cards with required `name` and `type`, plus at
  least an observable `symptom` or durable `impact`. They ground CRM triage and
  handoff hypotheses but never confirm root cause by themselves.
- Prefer clear boundary language over internal labels.
- Do not add team references to imply source or target ownership.
