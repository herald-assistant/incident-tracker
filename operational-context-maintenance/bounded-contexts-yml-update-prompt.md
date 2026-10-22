# bounded-contexts.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

## Purpose

Update `bounded-contexts.yml` as the semantic boundary catalog. A bounded context
entry explains local language, responsibility, nearby concepts, ownership, and
related catalog entities. It should help analysts avoid mixing business
meanings that look similar in code, logs, or user reports.

Keep entries semantic. Do not use this file as a map of low-level code details.
Keep repository lists and module boundaries in `code-search-scopes.yml`. A
directly targeted bounded-context scope is the first code slice returned for
this context. Without it, the read model falls back through effective links to
related scopes, including the system scope. An optional context scope refines
the semantic boundary and does not replace a required system scope.

## Ownership rule

`bounded-contexts.yml` may define ownership. Bounded-context ownership has
priority over system ownership whenever the problem can be mapped to a bounded
context.

Use system ownership only when the bounded context is unknown, missing, or the
problem is system-wide. Keep ownership in the YAML shape below; if the boundary
or owner is unclear, add an open question instead of inventing extra routing
fields.

## YAML shape

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
boundedContexts:
  - id: crm-contact-preferences
    name: CRM Contact Preferences
    type: supporting-domain
    lifecycleStatus: active
    summary: Defines the local meaning and validation of CRM contact channel choices.
    purpose: Keeps preference semantics separate from authentication and campaign selection.
    localLanguageSummary:
      - A contact preference is the channel choice maintained for an anonymized CRM contact.
    ownership:
      ownerTeamIds:
        - crm-domain-team
      ownershipStatus: explicit
      confidence: high
      source: Explicit operator statement in the fictional CRM scenario.
    scope:
      includes:
        - Validate and maintain contact preferences
      excludes:
        - Manage authentication credentials
      businessCapabilities:
        - CRM contact preference management
      coreEntities:
        - ContactPreference
      keyDecisions:
        - Whether a requested contact channel is supported
    semanticBoundary:
      coreConcepts:
        - Contact preference
      localConcepts:
        - Preferred contact channel
      canonicalEntities:
        - ContactPreference
      commands:
        - Change CRM contact preference
      events:
        - CRM contact preference changed
      invariants:
        - An enabled CRM channel must be supported for the contact.
      ownsLanguage:
        - CRM contact preference
      doesNotOwn:
        - Authentication credential
    references:
      systems:
        - crm-contact-core
    evidence:
      - sourceRef: crm-domain-notes.md
        evidenceType: domain-note
        note: Defines the fictional CRM preference boundary without customer records.
    sourceCoverage:
      status: partial
      scannedSources:
        - Fictional CRM domain notes
      limitations:
        - CRM retry behavior has not been reviewed.
    gaps:
      - id: crm-retry-confirmation
        summary: Confirm the CRM retry behavior and its completion evidence.
        severity: info
        status: open
    matchSignals:
      strong:
        businessTerms:
          - CRM contact preference
    llmToolHints:
      answerWhenUserMentions:
        - CRM contact channel choice
      disambiguateFrom:
        - CRM campaign audience
      usefulSearchKeywords:
        - contact preference changed
      explanationStyle: Explain the boundary and state visibility limits.
```

## Update rules

- Capture local language, semantic boundaries, ownership and relations.
- Maintain `localLanguageSummary`, `scope`, `semanticBoundary`, `evidence` and
  `llmToolHints` through the guided UI fields. No supported bounded-context
  field requires raw JSON.
- `localLanguageSummary` is a list of non-blank local-language statements.
- Treat `scope` and `semanticBoundary` as semantic descriptions, never as an
  inventory of Java classes, tables, endpoints or executable workflow rules.
- Treat `evidence` as provenance only. It does not fetch a source or prove a
  diagnosis. `llmToolHints` guides discovery and explanation but never grants
  access or ownership and cannot override evidence or visibility limits.
- Keep ownership at bounded-context level only when it describes durable domain
  accountability.
- Use `references.terms` for glossary entries that define the local language.
  If the term already has this context in `canonicalReferences`, do not add
  the inverse link just for discovery. Missing glossary IDs remain explicit
  gaps; do not invent definitions or guess a similarly named target.
- Keep explicit `references.systems` assignments here. An integration's
  participants do not replace a context-to-system assignment or ownership fact.
- Processes own process-to-context references and integration participants own
  their context roles. Do not mirror those links here.
- Add relations only for distinct semantic facts, never reverse/derived links.
- Keep aliases and recognition signals stable and business-readable.
- Keep repository and module boundaries in the related system-targeted
  code-search scope. Add a bounded-context-targeted scope only when it provides
  useful semantic narrowing or code-to-team attribution for this context and
  remains consistent with the system scope.
- Put unclear boundaries or unclear ownership into open questions.

## Quality check

- The entry helps explain the system to an analyst or tester.
- It clarifies what belongs here and what should be resolved through another
  context or system owner.
- Its code path uses a directly targeted context scope first, or related scopes
  as fallback. Preserve each scope's own search boundary and limitations.
- It does not duplicate code details that tools can discover.
- Every guided field explains what to enter, its runtime/AI effect and accepted
  format, using only strongly anonymized CRM examples where an example helps.
