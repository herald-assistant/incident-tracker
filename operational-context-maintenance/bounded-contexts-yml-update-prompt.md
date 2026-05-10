Update only `bounded-contexts.yml` and return the full ready-to-save YAML document only.

You are building the semantic boundary map for this repository. Read all attached sources and produce the final content of `src/main/resources/operational-context/bounded-contexts.yml`.

Important repository-specific constraints:
- Preserve the top-level shape:
  - `schemaVersion: 1`
  - `boundedContexts: [...]`
  - `openQuestions: [...]`
- Prefer this normalized bounded-context structure:
  - `id`
  - `name`
  - `ownerTeamId`
  - `purpose`
  - `systems`
  - `repos`
  - `processes`
  - `terms`
  - `scope.systemIds`
  - `scope.repositoryIds`
  - `scope.processIds`
  - `scope.businessDomains`
  - `scope.capabilities`
  - `boundaries.incomingInputs`
  - `boundaries.outgoingOutputs`
  - `runtimeFingerprints.serviceNames`
  - `runtimeFingerprints.containerNames`
  - `runtimeFingerprints.endpointPrefixes`
  - `runtimeFingerprints.queueNames`
  - `runtimeFingerprints.topicNames`
  - `runtimeFingerprints.databaseSchemas`
  - `runtimeFingerprints.packagePrefixes`
  - `runtimeFingerprints.logMarkers`
  - `incidentHints.likelySymptoms`
  - `incidentHints.likelyOwningTeamIds`
  - `incidentHints.likelyPartnerTeamIds`
  - `observability.expectedBusinessSignals`
  - `ubiquitousLanguage.keyTerms[].term`
  - `ubiquitousLanguage.keyTerms[].synonyms`
  - `relations[].target`
  - `relations[].type`
  - `relations[].via`

How to derive bounded contexts:
1. Start from the current `bounded-contexts.yml`.
2. Use `systems.yml`, `teams.yml`, `processes.yml`, `repo-map.yml`, and `integrations.yml` as the primary source of truth for ids, ownership, scope, and relationships.
3. Use attached incident analysis exports only to identify recurring business vocabulary, semantic boundaries, and stable language clusters such as:
   - agreement
   - pricing order
   - customer details
   - document generation
   - collateral
   - rating
   - currency
4. Use GitLab-resolved code evidence and recurring package/class names to reinforce semantic groupings, but do not create a new bounded context from a single package or class.
5. Merge duplicate observations into one stable bounded context entry.

YAML syntax safety rules:
- Never use TAB characters for indentation. YAML forbids TABs entirely. Use only spaces (2 spaces per level).
- Inside YAML flow sequences (`[value1, value2]`), always double-quote any value that contains curly braces `{}`, colons `:`, hash `#`, square brackets `[]`, or commas. Example: `endpointPrefixes: ["/api/resource/{id}/details"]` - never `endpointPrefixes: [/api/resource/{id}/details]`.
- For lists of endpoint paths, hosts, or any values that may contain URL path-template placeholders like `{id}`, `{customerId}`, etc., prefer YAML block sequences over flow sequences:

```yaml
# correct - block sequence, no quoting needed
endpoints:
  - /api/resource/{id}/details
  - /api/resource/{id}/summary

# correct - flow sequence with quotes
endpointPrefixes: ["/api/resource/{id}/details"]

# WRONG - unquoted curly braces in flow sequence break YAML parsing
endpointPrefixes: [/api/resource/{id}/details]
```

- When in doubt, quote the value. Plain unquoted strings in flow sequences are fragile.

Hard rules:
- Reuse ids from attached files exactly as they appear. Do not rename team, system, repository, process, or integration ids.
- Do not invent bounded-context boundaries.
- Do not create one bounded context per endpoint, controller, repository module, or integration.
- Keep one bounded context per meaningful semantic language boundary that helps incident routing and interpretation.
- Prefer stable vocabulary, business capabilities, and ownership over technical decomposition.
- `systems`, `repos`, and `processes` must stay consistent with the other attached operational-context files.
- If evidence suggests a semantic area but the boundary is unclear, keep the current state or add an `openQuestions` entry instead of inventing a new context.
- Keep `terms` short and domain-specific.
- Keep `relations` short and only where the relation is operationally useful.
- Use `relations[].via` for the ids of systems or integrations only when the attached evidence supports it.
- Do not paste full source code, long stacktraces, long request bodies, tokens, or generic framework terms into the YAML.
- Do not output explanations, markdown fences, comments, or anything except the final YAML.

What matters most:
- each bounded context should represent a recognizable semantic boundary
- `ownerTeamId`, `systems`, `repos`, and `processes` must stay aligned with the rest of the operational-context catalog
- `terms` and `ubiquitousLanguage` should help interpret incidents, not duplicate the glossary
- `runtimeFingerprints` should help match incidents back to the right semantic area
- `relations` should help routing across semantic boundaries when incidents cross them

Repository-specific guidance:
- This repo enriches incidents from Elasticsearch, Dynatrace, and deterministic GitLab code resolution, so semantic boundaries should be supported by both runtime evidence and code structure when possible.
- Repeated package roots, class names, process names, and code hotspots are useful hints for a context, but they are not sufficient on their own.
- If attached incidents repeatedly mix different vocabularies, prefer modeling separate bounded contexts and relate them rather than collapsing everything into one generic area.
- If a runtime flow repeatedly crosses one semantic area into another through a known integration or system boundary, capture that as a short `relations` entry.
- If a term is generic infrastructure language rather than domain language, do not use it as the primary basis for a bounded context.

Universal examples below are illustrative only.
Do not copy ids, names, or values from the examples unless they are supported by the attached sources.

Example 1: clear semantic boundary with stable language

If the attached files say:
- `systems.yml` contains `payments-api`
- `repo-map.yml` contains `payments-api-repo`
- `processes.yml` contains `payment-capture`
- repeated code and incident evidence uses terms like `payment`, `settlement`, `capture`
- ownership points to `payments-team`

Then a valid bounded context entry could look like this fragment:

- id: payments
  name: Payments
  ownerTeamId: payments-team
  purpose: Owns payment capture and settlement semantics.
  systems: [payments-api]
  repos: [payments-api-repo]
  processes: [payment-capture]
  terms: [payment, settlement, capture]
  scope:
    systemIds: [payments-api]
    repositoryIds: [payments-api-repo]
    processIds: [payment-capture]
    businessDomains: [payments]
    capabilities: [capture payment, settle payment]
  boundaries:
    incomingInputs: [payment request]
    outgoingOutputs: [payment decision, settlement event]
  runtimeFingerprints:
    serviceNames: [payments-api]
    containerNames: [payments-api]
    endpointPrefixes: [/api/payments, /api/settlements]
    queueNames: []
    topicNames: [payments.events]
    databaseSchemas: [payments]
    packagePrefixes: [com.example.payments]
    logMarkers: [PAYMENTS]
  incidentHints:
    likelySymptoms: [payment creation failure, settlement mismatch]
    likelyOwningTeamIds: [payments-team]
    likelyPartnerTeamIds: []
  observability:
    expectedBusinessSignals: [payment-created, settlement-completed]
  ubiquitousLanguage:
    keyTerms:
      - term: payment
        synonyms: [transaction]
      - term: settlement
        synonyms: [clearing]
  relations:
    - target: ledger
      type: customer-supplier
      via: [payments-to-ledger-sync]

Reason:
- one stable semantic language
- scope matches systems, repo, and process ids
- runtime and domain vocabulary reinforce each other

Example 2: recurring technical hotspot but unclear semantic boundary

If the attached sources show only:
- repeated stacktraces in classes under `com.example.common.integration`
- generic terms like `request`, `response`, `adapter`
- no stable business vocabulary
- no clear ownership or process boundary

Then do not invent a bounded context.
Add an open question such as:

openQuestions:
  - "Do the recurring `common.integration` classes belong to an existing bounded context, or do they only represent shared technical infrastructure?"

Reason:
- technical packaging alone is not enough to define a semantic boundary

If the attached evidence is too weak to create a confident bounded-context entry, keep that area out of `boundedContexts` and add a precise item to `openQuestions`.

Return the full updated YAML only.
