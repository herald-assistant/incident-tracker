# bounded-contexts.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `bounded-contexts.yml` as the semantic boundary catalog. A bounded context
entry explains local language, responsibility, nearby concepts, ownership, and
related catalog entities. It should help analysts avoid mixing business
meanings that look similar in code, logs, or user reports.

Keep entries semantic. Do not use this file as a map of low-level code details.
Do not use it as the primary map from bounded context to repositories. Code
navigation starts from the system's single `code-search-scopes.yml` entry and
then resolves bounded-context meaning through catalog references and code
evidence. An optional bounded-context code-search scope may be added only as a
semantic slice when it clarifies a durable domain boundary or helps route code
locations back to bounded-context ownership; it does not replace the system
scope.

## Ownership rule

`bounded-contexts.yml` may define ownership. Bounded-context ownership has
priority over system ownership whenever the problem can be mapped to a bounded
context.

Use system ownership only when the bounded context is unknown, missing, or the
problem is system-wide. Keep ownership in the YAML shape below; if the boundary
or owner is unclear, add an open question instead of inventing extra routing
fields.

## YAML shape

```yaml
boundedContexts:
  - id: crm-contact-preferences
    name: CRM Contact Preferences
    shortName: Contact Preferences
    type: core-domain
    lifecycleStatus: active
    summary: Maintains channel preferences for anonymized CRM contacts.
    purpose: Separates contact preference language from CRM campaign audience language.
    aliases:
      - CRM channel preference
    useFor:
      - Explain how an anonymized CRM contact preference is interpreted.
      - Disambiguate contact preferences from CRM campaign selection.
    localLanguageSummary:
      - A preferred channel is the channel enabled for an anonymized CRM contact.
    scope:
      includes:
        - Validate and maintain CRM contact preferences.
      excludes:
        - Manage authentication credentials.
      businessCapabilities:
        - CRM contact preference management
      coreEntities:
        - ContactPreference
      keyDecisions:
        - Whether a requested CRM channel can be enabled.
    semanticBoundary:
      coreConcepts:
        - CRM contact preference
      localConcepts:
        - preferred channel
      canonicalEntities:
        - ContactPreference
      commands:
        - Change CRM contact preference
      events:
        - CRM contact preference changed
      invariants:
        - An enabled CRM channel must be supported for the anonymized contact.
      ownsLanguage:
        - contact preference
      doesNotOwn:
        - authentication credential
    ownership:
      ownerTeamIds:
        - crm-domain-team
      ownershipStatus: explicit
      confidence: high
      source: bounded-contexts.yml
      notes:
        - CRM domain owner confirmed the local language and boundary.
    references:
      systems:
        - crm-contact-core
      processes:
        - crm-contact-preference-management
      integrations:
        - crm-contact-to-profile-store
      terms:
        - crm-contact-preference
      teams:
        - crm-domain-team
      handoffRules:
        - crm-contact-preference-boundary
    matchSignals:
      exact:
        terms:
          - CRM contact preference
      strong:
        aliases:
          - preferred channel
      weak:
        phrases:
          - CRM channel choice
    relations:
      - type: hands-off-to
        targetType: bounded-context
        target: crm-engagement
        via:
          - crm-contact-to-profile-store
        evidence: confirmed contact preferences become input to CRM engagement decisions
    evidence:
      - sourceRef: crm-domain-notes.md
        evidenceType: domain-note
        note: Defines the CRM contact preference boundary without customer data.
    llmToolHints:
      answerWhenUserMentions:
        - CRM contact preference
      disambiguateFrom:
        - CRM campaign audience
      usefulSearchKeywords:
        - contact preference changed
      explanationStyle: Explain the CRM boundary in business language and state visibility limits.
```

## Update rules

- Capture local language, semantic boundaries, ownership and relations.
- Maintain `localLanguageSummary`, `scope`, `semanticBoundary`, `evidence` and
  `llmToolHints` through the guided UI fields. No supported bounded-context
  field requires raw JSON.
- A legacy scalar `localLanguageSummary` remains readable and is normalized to
  a list only after it is edited.
- Treat `scope` and `semanticBoundary` as semantic descriptions, never as an
  inventory of Java classes, tables, endpoints or executable workflow rules.
- Treat `evidence` as provenance only. It does not fetch a source or prove a
  diagnosis. `llmToolHints` guides discovery and explanation but never grants
  access or ownership and cannot override evidence or visibility limits.
- Keep ownership at bounded-context level only when it describes durable domain
  accountability.
- Use `references.terms` for glossary entries that define the local language.
- Add relations only when they help navigation across contexts.
- Keep aliases and match signals stable and business-readable.
- Keep repository and module boundaries in the related system-targeted
  code-search scope. Add a bounded-context-targeted scope only when it provides
  useful semantic narrowing or code-to-team attribution for this context and
  remains consistent with the system scope.
- Put unclear boundaries or unclear ownership into open questions.

## Quality check

- The entry helps explain the system to an analyst or tester.
- It clarifies what belongs here and what should be resolved through another
  context or system owner.
- Its code ownership path is available through the related system-targeted
  code-search scope when code navigation is needed, with an optional
  bounded-context scope only as a semantic slice for attribution.
- It does not duplicate code details that tools can discover.
- Every guided field explains what to enter, its runtime/AI effect and accepted
  format, using only strongly anonymized CRM examples where an example helps.
