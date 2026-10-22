# glossary.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `glossary.yml` as the structured local-language catalog. A term should
help an analyst, tester, developer or AI understand CRM language and avoid
confusing nearby concepts.

Glossary terms do not define ownership. Ownership and handoff are resolved
from linked bounded contexts and systems.

## YAML shape

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
schemaVersion: 1
catalogKind: operational-context-glossary
terms:
  - id: crm-contact-preference
    term: CRM Contact Preference
    category: domain-term
    lifecycleStatus: active
    definition: An anonymized CRM contact's choice of an available contact channel.
    localMeaningAndBoundaries:
      - Describes a contact channel choice, not authentication credentials.
    aliases:
      - CRM channel preference
    useFor:
      - Explain CRM preference terminology.
    matchSignals:
      exact:
        phrases:
          - CRM contact preference
    canonicalReferences:
      - bounded-context:crm-contact-preferences
    relatedTerms:
      - crm-contact-channel
    doNotConfuseWith:
      - Authentication account
    responsibilityHints:
      - Resolve responsibility through the linked CRM context.
    llmToolHints:
      - Use for CRM terminology grounding.
  - id: crm-contact-channel
    term: CRM Contact Channel
    category: domain-term
    lifecycleStatus: active
    definition: An available way to contact an anonymized CRM contact.
    notes:
      - Describes a channel kind, never a real address or customer record.
gaps:
  - id: crm-vocabulary-boundary
    type: human-confirmation-required
    severity: info
    status: open
    summary: Confirm the fictional CRM vocabulary boundary.
```

## Update rules

- `id`, `term` and `category` are required; `id` is immutable after create.
- `matchSignals` keeps the exact/strong/medium/weak buckets and typed signal
  maps.
- `canonicalReferences` use `type:id`; `relatedTerms` contain glossary IDs.
- Keep one canonical direction for a navigation fact. Do not mirror a context's
  existing term reference or a related term merely to make this term discoverable.
- Never invent a definition or substitute a similarly named term to repair a
  missing ID. Preserve the unresolved meaning as a supported gap on its source
  entity or a draft visibility limit.
- Keep `responsibilityHints` descriptive. Do not encode a team route here.
- Preserve root metadata and gaps; do not edit generated read projections.
- Every test, fixture or example must be strongly anonymized and CRM-only.
