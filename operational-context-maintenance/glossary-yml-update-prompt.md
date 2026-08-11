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

```yaml
schemaVersion: 1
catalogKind: operational-context-glossary
terms:
  - id: crm-customer-profile
    term: CRM Customer Profile
    category: domain-term
    lifecycleStatus: active
    definition: An anonymized CRM view of a customer.
    localMeaningAndBoundaries:
      - Represents the CRM profile used during customer-case handling.
    aliases:
      - CRM profile
    useFor:
      - case-routing
    matchSignals:
      exact:
        alias:
          - "alias:customer profile"
    canonicalReferences:
      - system:crm-customer-service
      - bounded-context:crm-customer-management
    relatedTerms:
      - crm-contact-preference
    doNotConfuseWith:
      - Authentication account
    responsibilityHints:
      - Resolve ownership through the CRM customer context.
    llmToolHints:
      - Use only for CRM terminology grounding.
    notes:
      - Strongly anonymized CRM example.
gaps:
  - id: crm-vocabulary-boundary
    type: human-confirmation-required
    severity: info
    status: open
    summary: Confirm the anonymous CRM vocabulary boundary.
```

## Update rules

- `id`, `term` and `category` are required; `id` is immutable after create.
- `matchSignals` keeps the exact/strong/medium/weak buckets and typed signal
  maps.
- `canonicalReferences` use `type:id`; `relatedTerms` contain glossary IDs.
- Keep `responsibilityHints` descriptive. Do not encode a team route here.
- Preserve root metadata and gaps; do not edit generated read projections.
- Every test, fixture or example must be strongly anonymized and CRM-only.
