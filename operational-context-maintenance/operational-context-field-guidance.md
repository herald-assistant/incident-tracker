# Operational context field guidance

## Purpose

This document is the canonical field-level guide for the Operational Context
maintenance UI and the file-specific maintenance prompts. It explains what an
operator enters, how the value is consumed by runtime read models or AI, and
which values are actually constrained.

UI tooltips must preserve these four parts for every editable field:

1. what to enter,
2. runtime or AI effect,
3. accepted format and values,
4. an anonymized CRM-domain example when an example is useful.

Do not describe a convention as backend enforcement. The maintenance API
validates required fields, references and the strict rules listed below. Other
scalar classifications are free text, but new values should follow the shared
catalogue vocabulary so filtering and AI interpretation remain consistent.

The maintenance UI renders the highest-risk structures as guided controls,
not raw JSON:

- ownership uses team, status and confidence selectors,
- references use per-entity-type catalogue pickers,
- repository Git identity uses explicit provider/group/project/path/branch/URL
  fields and alias lines,
- system external responsibility and runtime configuration use guided
  `externalOwner` and `configurationDirectory` inputs,
- repository provenance uses repeatable `sourceRef`/`evidenceType`/`note`
  cards, while AI exploration guidance uses two phrase lists,
- code-search target uses `system` / `bounded-context` and entity selectors;
  `scopeType` is derived from `target.type`,
- code-search repositories use repeatable repository/search-boundary rows,
- process participants use actor lines and role-specific system pickers,
- process steps use ordered milestone cards with canonical reference pickers
  and strong business terms,
- integration participants use source/target/intermediary/final-target cards,
- match signals use repeatable confidence/key/value rows with suggestions
  appropriate for the edited entity type,
- relations use repeatable semantic-edge cards with catalogue target pickers,
  optional integration paths and evidence,
- process boundaries, lifecycle, completion evidence, failure modes, process
  artifacts, source coverage and catalogue gaps use their dedicated guided
  controls,
- bounded-context local language, scope, semantic boundary, provenance evidence
  and AI exploration hints use dedicated list fields and repeatable evidence
  cards.

These controls serialize the unchanged canonical YAML/JSON shapes described
below and preserve unknown extensions already present in an edited entry.
No supported canonical field requires a raw JSON input in the MVP. Unknown
extensions remain preserved by the edit round-trip, but are not presented as
editable fields until they become part of the documented contract.

## Strict maintenance validation

- Every entity requires a stable `id` and its display field: `name`, `term` or
  `title`. IDs are immutable after creation.
- A repository requires `git.project` or `git.projectPath`.
- `system.participants`, when present, is an object and its `externalOwner` is
  non-blank text. `system.runtime.configurationDirectory`, when present, is a
  safe repository-relative path of at most 255 characters without a leading or
  trailing slash, `//`, `..`, backslash or `@{`.
- Repository `evidence` is a list of objects with required non-blank
  `sourceRef` and `evidenceType` plus optional non-blank `note`.
- Repository `llmToolHints` is an object whose known
  `answerWhenUserMentions` and `disambiguateFrom` fields are lists of non-blank
  text.
- A code-search `target.type` is exactly `system` or `bounded-context`; its
  `target.id` must exist.
- Every code-search repository requires an existing, unique `repoId`, a
  positive `priority`, and `searchMode` equal to `whole-repository` or
  `path-prefixes`. At least one repository must have role `primary` or priority
  `1`. `path-prefixes` requires non-empty safe relative `pathPrefixes`;
  `whole-repository` forbids them.
- An integration requires `participants.source` and at least one item in
  `participants.targets` or `participants.finalTargets`.
- `ownership.ownershipStatus: explicit` requires a valid `ownerTeamIds` entry
  or a non-empty `ownerLabel`. Only systems and bounded contexts define
  ownership.
- Reference IDs and typed references must resolve to existing catalogue
  entities and must not create prohibited self-references.
- Match-signal buckets are `exact`, `strong`, `medium` or `weak`; every signal
  key contains a non-empty list of non-blank values. Legacy flat signal maps
  remain readable and are written as guided `strong` rows when changed.
- Bounded-context `localLanguageSummary` is non-blank text or a list of
  non-blank statements. The UI reads the legacy scalar and normalizes it to a
  list only after the operator changes the field.
- Bounded-context `scope` and `semanticBoundary` are objects. Every known field
  is a list of non-blank text; unknown extensions are preserved but are not
  projected to AI.
- Bounded-context `evidence` is a list of objects with required non-blank
  `sourceRef` and `evidenceType` plus optional non-blank `note`.
- Bounded-context `llmToolHints` is an object. Its known phrase fields are lists
  of non-blank text and optional `explanationStyle` is non-blank text.
- A canonical relation requires a semantic `type`, a supported `targetType`
  and an existing non-self `target`. A genuinely uncatalogued side may instead
  use `externalSystem`. Duplicate semantic edges are rejected.
- A glossary term requires `category`.

## Shared fields

These fields apply to systems, repositories, processes, integrations, bounded
contexts and teams. Code-search scopes use only `id`, `name`,
`lifecycleStatus`, `summary` and `useFor` from this set.

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `id` | Required unique lowercase kebab-case identifier, for example `crm-contact-core`. Immutable after create. | Canonical API key and graph/reference target; visible to AI tools and used by delete-impact checks. |
| `name` | Required human-readable name, for example `CRM Contact Core`. | Primary label in UI, read models, search and AI explanations. |
| `shortName` | Optional compact, unambiguous label, for example `Contact Core`. | Fallback display label and additional searchable identity. |
| `lifecycleStatus` | Backend free text. Catalogue convention: `active`, `planned`, `deprecated`, `retired`. | Tells operators and AI whether context is current; it does not enable or disable application execution. |
| `summary` | One or two durable business-readable sentences. | Searchable overview used by UI and AI before advanced fields are read. |
| `purpose` | Durable functional intent, not an implementation inventory. | Helps AI connect technical evidence to business outcome and distinguish similar entities. |
| `aliases` | One alternate business, technical or historical name per item. | Included in generic signals for search and AI entity resolution. |
| `useFor` | One analyst question or use case per item. | Guides AI and tool consumers when selecting relevant context. |

## Shared structured fields

### `references`

The UI uses grouped catalogue pickers and serializes a JSON/YAML object whose
supported lists are `systems`, `repositories`,
`processes`, `boundedContexts`, `integrations`, `terms`, `teams` and
`handoffRules`. Use only lists appropriate for the entity's file-specific
rules. IDs must exist.

References build the relation index, related-entity views, reverse navigation
and delete-impact blockers. They also let AI move from a piece of context to
the next relevant entity. A system never references repositories directly;
system-to-code navigation goes through `code-search-scopes.yml`.

CRM example:

```json
{
  "systems": ["crm-contact-core"],
  "processes": ["crm-contact-update"],
  "terms": ["crm-customer-profile"]
}
```

### `ownership`

Only systems and bounded contexts use this object. The UI exposes structured
team/status/confidence/source controls. Supported serialized properties are
`ownerTeamIds`, `ownerLabel`, `ownershipStatus`, `confidence`, `source` and
`notes`. `ownershipStatus: explicit` requires an existing team ID or an owner
label. Confidence convention is `high`, `medium` or `low`.

Bounded-context ownership has priority; system ownership is the fallback. AI
uses resolved ownership for impact and handoff, while repositories, processes,
integrations, teams, glossary terms and handoff rules never define owners.

```json
{
  "ownerTeamIds": ["crm-domain-team"],
  "ownershipStatus": "explicit",
  "confidence": "high",
  "source": "CRM domain catalogue"
}
```

### `matchSignals`

The UI uses repeatable rows with a fixed confidence selector (`exact`,
`strong`, `medium`, `weak`), a signal-key input with entity-specific
suggestions, and one value per line. It serializes a JSON/YAML object in which
each bucket maps durable evidence keys such as
`projectNames`, `projectPaths`, `serviceNames`, `routes`, `businessTerms`,
`phrases`, `aliases` or `teamNames` to string lists.

Runtime resolution and catalogue search compare evidence with these values.
AI tools expose the signals to explain why an entity matched. Put a value in a
stronger bucket only when the evidence is reliably identifying.

Signal keys are intentionally extensible because integrations and runtime
evidence introduce durable identifiers such as `bindings`, `exchanges`,
`routingKeys`, `consumerGroups`, `configKeys`, `packagePrefixes` or
`emailAliases`. The UI suggests keys appropriate for the current entity and
also keeps keys already present in the entry. Do not create a custom key unless
an actual evidence source emits that field.

| Entity type | Guided signal-key suggestions |
| --- | --- |
| `system` | `serviceNames`, `deploymentNames`, `applicationNames`, `routes`, `endpoints`, `projectNames`, `projectPaths`, `businessTerms`, `aliases`, `configKeys` |
| `repository` | `projectPaths`, `projectNames`, `buildCoordinates`, `groupIds`, `artifactIds`, `configKeys`, `aliases` |
| `process` | `businessTerms`, `routes`, `operationNames`, `eventNames`, `exchanges`, `routingKeys`, `aliases` |
| `integration` | `routes`, `endpoints`, `operationNames`, `bindings`, `exchanges`, `routingKeys`, `consumerGroups`, `hostPatterns`, `configKeys`, `artifactIds`, `businessTerms` |
| `bounded-context` | `businessTerms`, `routes`, `packagePrefixes`, `classNames`, `dbTables`, `schedulerNames`, `exchanges`, `routingKeys`, `consumerGroups`, `configKeys`, `artifactIds` |
| `team` | `teamNames`, `aliases`, `emailAliases`, `collaborationIds` |
| `glossary-term` | `phrases`, `aliases`, `fieldNames`, `businessTerms` and existing typed keys such as `word`, `field`, `class`, `enum`, `endpoint`, `table`, `system`, `integration`, `bounded-context` |

```json
{
  "exact": { "serviceNames": ["crm-contact-service"] },
  "strong": { "routes": ["/crm/contacts"] }
}
```

### `relations`

The UI uses repeatable semantic-edge cards and serializes a JSON/YAML list. The
canonical shape uses `type`, `targetType`, `target`, optional integration IDs in
`via`, and optional `evidence`. `targetType` is selected from the nine
catalogue entity types and `target` is selected from existing entities. For a
genuinely uncatalogued boundary, `externalSystem` can replace the canonical
target; it is preserved for AI context but does not create an internal graph
edge.

Relations become graph edges for related-entity reads, ownership/navigation
inference and delete-impact checks. Avoid duplicating a reference without
adding semantic meaning. Legacy `targetContextId`, `targetProcessId` and a
missing `targetType` for a system target remain readable; changing their target
through the UI writes canonical `targetType` plus `target` and preserves other
unknown relation extensions.

### `sourceCoverage`

The UI writes one object with `status`, `scannedSources`, `expectedSources` and
`limitations`. Prefer `complete`, `partial` or `unknown` for new entries.
Existing `full`, `scanned` and `fully-scanned` values remain readable and
selectable so an unrelated edit does not silently change established meaning.
Legacy `sources` is shown as `scannedSources` and normalized only when that list
is edited.

`opctx_get_entity` exposes the maintained status and source lists. Its
affordances also promote `limitations` to explicit AI visibility limits.
Source descriptions are provenance, not catalogue references, and do not prove
that every expected source was reviewed.

```json
{
  "status": "partial",
  "scannedSources": ["Anonymized CRM architecture notes"],
  "expectedSources": ["CRM retry design"],
  "limitations": ["CRM batch retry ownership is not confirmed"]
}
```

### `gaps`

The UI writes a list of cards. Every card requires an actionable `summary`.
Optional `id` uses lowercase kebab-case and is unique inside the entry; it is a
maintenance identifier, not a catalogue entity. Optional `type` describes the
kind of missing knowledge. `severity` is `error`, `warning` or `info`, while
`status` is `open` or `resolved`. `suggestedNextSources` lists useful evidence
to inspect but does not fetch or trust it automatically.

The codec turns every actionable card into an Open Questions inbox item and
`opctx_get_entity` returns it to AI. A gap constrains conclusions; it is never
evidence that a production defect exists. Legacy non-blank string items remain
accepted for compatibility, but new maintenance should use cards.

```json
[
  {
    "id": "crm-retry-owner",
    "type": "unconfirmed-ownership",
    "summary": "Confirm ownership of CRM batch retries.",
    "severity": "warning",
    "status": "open",
    "suggestedNextSources": ["Anonymized CRM operations notes"]
  }
]
```

## Systems

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `systemType` | Backend free text. Current vocabulary: `internal-service`, `business-service`, `gateway`, `data-store`, `message-broker`, `platform-service`, `external-system`, `external-saas`, `identity-provider`, `middleware`. | Classifies the canonical system in UI and AI context. |
| `operationalStatus` | Short durable operating state; do not store incident-specific status. | Gives AI operating context but does not change application execution. |
| `criticality` | Backend free text. Current vocabulary: `critical`, `high`, `medium`, `low`, `unknown`. | Used for impact prioritization in read models and AI analysis. |
| `participants` | Guided optional `externalOwner` text. Use it only when the system boundary is operated outside the local team catalogue; omit the object for locally operated systems. | `opctx_get_entity` exposes the label as external responsibility without creating local team ownership. |
| `runtime` | Guided optional `configurationDirectory`: safe repository-relative path up to 255 characters, for example `crm/contact-service`. Service, deployment and application identities belong in `matchSignals`, not here. | Config Drift Viewer uses the directory to select the system configuration scope; `opctx_get_entity` exposes the known value without raw runtime extensions. |
| `sourceCoverage` | Guided status plus checked/expected sources and limitations; use the shared shape above. | Exposed by `opctx_get_entity`; limitations become explicit AI visibility limits. |
| `gaps` | Guided actionable cards using the shared shape above. | Feeds Open Questions and AI knowledge limits; never proves a defect. |
| `notes` | One durable clarification per item. | Preserved in detailed context; does not replace signals or references. |

## Repositories

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `repositoryType` | Backend free text. Current vocabulary includes `service`, `monorepo`, `shared-library`. | Distinguishes application repositories and reusable code sources before exploration. |
| `criticality` | Current vocabulary: `critical`, `high`, `medium`, `low`, `unknown`. | Contextual prioritization; actual read order comes from code-search `priority`. |
| `git` | Guided fields for `provider`, `group`, `project`, `projectPath`, `defaultBranch`, `url` and one `aliases` value per line; `project` or `projectPath` is required. `inferred` is server-owned and not editable. | Connects the catalogue ID to GitLab discovery, code tools and technical ownership resolution. `projectPath` is the canonical provider-relative lookup identity; project and aliases are fallback candidates. |
| `evidence` | Guided cards with required `sourceRef`, required `evidenceType` and optional `note`. Use stable relative paths or durable document labels, never source contents, credentials or customer data. | Operator detail and `opctx_get_entity` expose the known fields as explainable provenance; the application does not fetch the reference automatically. |
| `sourceCoverage` | Guided status plus checked/expected repository areas and limitations. | Exposed by `opctx_get_entity`; prevents a partial mapping from appearing exhaustive. |
| `gaps` | Guided missing Git identity or semantic-mapping questions. | Feeds Open Questions; AI may request targeted evidence instead of inventing a link. |
| `llmToolHints` | Guided non-blank phrase lists: `answerWhenUserMentions` for discovery triggers and `disambiguateFrom` for explicit contrasts. Enter selection guidance, not answers or secrets. | Discovery phrases are searchable repository signals; both lists are exposed by `opctx_get_entity` to guide AI exploration without defining access or ownership. |
| `notes` | One durable clarification per item. | Available in detailed context but not used as a match unless also modeled as a signal. |

CRM `git` example:

```json
{
  "provider": "gitlab",
  "group": "crm",
  "project": "contact-service",
  "projectPath": "crm/contact-service",
  "defaultBranch": "main"
}
```

The UI preserves an existing server-owned `git.inferred` value during update,
but never sends or creates that field. `projectPath` contains `group/project`,
not a host URL; use `url` only as a navigable operator link.

CRM provenance and exploration example:

```yaml
evidence:
  - sourceRef: crm/contact-service/pom.xml
    evidenceType: build-definition
    note: Anonymized CRM service module.
llmToolHints:
  answerWhenUserMentions:
    - CRM contact validation
  disambiguateFrom:
    - CRM authentication account service
```

## Code-search scopes

Code-search fields are deliberately narrower than the common entity shape.
`shortName`, `purpose` and `aliases` are not editable or accepted for this
entity type.

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `scopeType` | Derived by the UI from `target.type`: `system` or `bounded-context`; it is not a separate form input. | Labels required system scopes versus optional semantic slices and cannot drift from the selected target type. |
| `target` | UI selectors produce a required object with `type` strictly `system` or `bounded-context`, and an existing `id`. | Canonical semantic-to-code bridge used by code search, Flow Explorer, ownership resolution and reverse mapping. |
| `repositories` | Required repeatable UI rows serialized to the list described below. | Defines which repositories AI/code tools inspect, in what order and within which paths. |
| `limitations` | One intentional coverage boundary per item. | AI exposes incomplete code visibility rather than assuming complete coverage. |

Each `repositories` item supports:

| Property | Format / values | Effect |
| --- | --- | --- |
| `repoId` | Required existing repository ID; unique inside the scope. | Selects the Git identity to read. |
| `role` | Semantic role such as `primary`, `library`, `supporting`, `shared`, `reference`, `legacy` or `migration-peer`. | Explains why code belongs in the scope; `primary` also satisfies the required main repository rule. |
| `priority` | Required positive integer; `1` means start here. | Determines repository read order. |
| `reason` | Business/system-readable inclusion reason. | Helps operators and AI justify the selected source. |
| `readFor` | Questions to answer, one per item. | Guides AI exploration without embedding low-level clues. |
| `searchMode` | Strictly `whole-repository` or `path-prefixes`. | Selects the GitLab search boundary. |
| `pathPrefixes` | Required non-empty safe relative paths for `path-prefixes`; forbidden for `whole-repository`. No leading slash, `..` or backslash. | Restricts code search to relevant modules. |

```json
{
  "type": "system",
  "id": "crm-contact-core"
}
```

```json
[
  {
    "repoId": "crm-contact-repository",
    "role": "primary",
    "priority": 1,
    "reason": "Contains the CRM contact workflow.",
    "readFor": ["Contact validation and persistence"],
    "searchMode": "path-prefixes",
    "pathPrefixes": ["apps/crm-contact", "libs/crm-contracts"]
  }
]
```

## Processes

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `type` | Established process vocabulary, for example `business-process`; backend free text. | Distinguishes business and operational flows in search and AI context. |
| `criticality` | Current vocabulary: `critical`, `high`, `medium`, `low`, `unknown`. | Prioritizes affected functional paths. |
| `operationalOutcome` | Observable successful business result. | Gives AI a functional completion target. |
| `participants` | Guided actor lines plus role-specific selectors for existing `primarySystems`, `supportingSystems`, `externalSystems` and `platformComponents`. A system should have one role in a process. | Creates typed system graph edges used by related-entity views, Flow Explorer and AI to reconstruct the functional path. Actors explain human roles but do not create ownership. |
| `steps` | Ordered cards with a unique lowercase kebab-case `id`, required `name`, optional `type`/`summary`, canonical `references` and optional strong business terms stored as `matchSignals.strong.terms`. | The array order is the process sequence. Step identity, text and match signals are searchable; references create step graph edges and give AI an explainable flow. |
| `processBoundary` | Guided object with optional `businessCapability` and non-blank text lists `startsWhen`, `endsWhen`, `includes`, `excludes`, `assumptions`. Legacy non-blank string/list values remain readable as `endsWhen`. | The full known boundary is indexed and exposed by `opctx_get_entity`; it tells AI where the functional flow starts and ends and which adjacent responsibilities must not be attributed to this process. Assumptions remain explicit limitations, not evidence. |
| `lifecycle` | Guided object with trigger cards (`type`, `name`, optional `exchange`), `entryCriteria`, `statuses`, transition cards (`from`, `to`, `trigger`), `terminalStates` and success/partial/failed/cancellation outcome lists. Trigger type and name plus transition target and trigger are required. Legacy non-blank string/list values remain readable as `statuses`. | The known lifecycle is indexed and exposed by `opctx_get_entity` so AI can reconstruct state progress and distinguish not-started, in-progress, terminal, failed and cancelled paths. It is descriptive context and never configures an executable workflow engine. |
| `completionSignals` | Guided non-blank evidence lists `successful`, `partial`, `failed`, `cancelled`. Legacy non-blank string/list values remain readable as `successful`. | Observable evidence categories are indexed and exposed by `opctx_get_entity`; AI compares actual evidence with them but the maintained text alone never proves the current process state or root cause. |
| `failureModes` | Guided cards with unique kebab-case `id`, required `name` and `summary`, optional existing `affectedStep`, and observable `signals`. Legacy non-blank strings remain readable. | Structured cards are exposed by catalogue detail and `opctx_get_entity`; their leaf text is indexed as process signals and supplies hypotheses, never proof of root cause. |
| `dataAndArtifacts` | Guided lists: `primaryObjects`, `inputArtifacts`, `outputArtifacts`, `persistedEntities`, `readModels`, `auditArtifacts`, `notes`. Artifact kinds only; never customer data or payloads. | `opctx_get_entity` exposes and indexes the categories, connecting evidence and milestones to business artifacts. |
| `relations` | Guided semantic edges using canonical `targetType` plus an existing non-self `target`; legacy `targetProcessId` remains readable. | Builds process and cross-entity navigation. |

The process-step picker supports systems, repositories, bounded contexts,
integrations, glossary terms and handoff rules. Every selected ID must already
exist. Legacy `steps[].match` is server-owned: the UI does not edit or send it,
and the backend preserves it for an existing step matched by `id`. New signal
maintenance uses `matchSignals`; the first guided MVP control writes durable
business phrases to `matchSignals.strong.terms` while preserving other
existing signal buckets and keys.

`processBoundary`, `lifecycle` and `completionSignals` have separate jobs:

- the boundary defines the functional scope and stop conditions,
- the lifecycle describes allowed durable states, transitions and their meaning,
- completion signals describe facts an operator can observe when evaluating a
  concrete execution.

Editing any legacy string/list shape writes the canonical object while leaving
an untouched legacy value unchanged. Unknown object fields are preserved by the
editor and storage codec. AI tools receive only the known projection, never
unknown raw extensions.

CRM process-boundary example:

```json
{
  "businessCapability": "CRM Contact Preference Management",
  "startsWhen": ["An anonymized CRM contact update is accepted."],
  "endsWhen": ["The CRM contact view confirms the update."],
  "includes": ["CRM contact preference validation"],
  "excludes": ["Authentication credential lifecycle"],
  "assumptions": ["CRM contact identity is already resolved."]
}
```

CRM lifecycle example:

```json
{
  "triggers": [{"type": "api", "name": "CRM contact update"}],
  "entryCriteria": ["CRM contact identity is available."],
  "statuses": ["requested", "applied"],
  "transitions": [
    {"from": "requested", "to": "applied", "trigger": "CRM validation succeeds."}
  ],
  "terminalStates": ["applied"],
  "successOutcomes": ["CRM contact preference is applied."],
  "partialOutcomes": ["CRM projection remains pending."],
  "failedOutcomes": ["CRM validation rejects the update."],
  "cancellationOutcomes": ["CRM agent cancels the update."]
}
```

CRM completion-signal example:

```json
{
  "successful": ["CRM contact confirmation is recorded."],
  "partial": ["CRM projection remains pending."],
  "failed": ["CRM validation rejection is recorded."],
  "cancelled": ["CRM cancellation is recorded."]
}
```

CRM failure-mode example:

```json
{
  "id": "crm-contact-rejected",
  "name": "CRM contact rejected",
  "summary": "The anonymized CRM contact update is rejected before persistence.",
  "affectedStep": "validate-contact",
  "signals": ["CRM validation rejection", "CRM update confirmation is absent"]
}
```

CRM data/artifact example:

```json
{
  "primaryObjects": ["ContactPreference"],
  "inputArtifacts": ["Anonymized CRM contact change request"],
  "outputArtifacts": ["CRM contact update confirmation"],
  "persistedEntities": ["ContactPreference"],
  "readModels": ["CRM contact view"],
  "auditArtifacts": ["CRM contact change audit metadata"],
  "notes": ["No customer records or payloads are stored in the catalogue."]
}
```

CRM step example:

```json
{
  "id": "accept-contact-update",
  "name": "Accept CRM contact update",
  "type": "business-step",
  "summary": "Validate and accept the anonymized CRM contact change.",
  "references": {
    "systems": ["crm-contact-core"],
    "boundedContexts": ["crm-customer-context"]
  },
  "matchSignals": {
    "strong": { "terms": ["accept CRM contact update"] }
  }
}
```

## Integrations

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `category` | Backend free text. Current vocabulary includes `internal-api`, `external-api`, `gateway-route`, `messaging`, `event-stream`, `notification`. | Drives integration filtering and high-level boundary classification. |
| `integrationStyle` | Current vocabulary includes `synchronous`, `synchronous-request`, `asynchronous`, `async-message`, `event-stream`, `gateway-mediated`, `mixed`. | Grounds synchronous/asynchronous failure reasoning. |
| `flowDirection` | Current vocabulary includes `source-to-target`, `request-response`, `bidirectional`, `fanout`. | Explains information flow; bidirectional-like values are interpreted as both-sided context. |
| `criticality` | Current vocabulary: `critical`, `high`, `medium`, `low`, `unknown`. | Prioritizes boundary impact. |
| `dataSensitivity` | Current vocabulary includes `internal`, `confidential`; backend free text. | Descriptive handling context for AI; it does not implement access control. |
| `participants` | Required source and target structure described below. | Produces directed graph edges and identifies boundary start, intermediaries and destination. |
| `failureModes` | Guided cards with required `name` and `type`, plus at least `symptom` or `impact`. Stable type examples: `timeout`, `upstream-error`, `rejected-message`, `unavailable`, `contract-mismatch`, `delivery-failure`. Legacy non-blank strings remain readable. | Structured cards are exposed by catalogue detail and `opctx_get_entity`; their leaf text is indexed as integration signals and grounds hypotheses/handoff without proving diagnosis. |

The UI renders participant cards. `participants.source` is required. At least
one `targets` or `finalTargets` item is required. `intermediaries` is optional.
A participant supports existing `system`, existing `boundedContext`, plus
`role`, `externalOwner` and `notes`. Participant-level `repositories` from
older data are server-owned and preserved but are not editable. Link code
through top-level `references` and canonical code-search scopes instead.

```json
{
  "source": { "system": "crm-contact-core", "role": "client" },
  "targets": [{ "system": "crm-profile-store", "role": "server" }]
}
```

```json
[
  {
    "name": "CRM profile timeout",
    "type": "timeout",
    "symptom": "The CRM profile response exceeds the agreed window.",
    "impact": "The CRM contact update cannot continue."
  }
]
```

## Bounded contexts

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `type` | Established domain vocabulary such as `core-domain`, `supporting-domain`, `domain`, `subdomain`; backend free text. | Classifies the semantic boundary for search and AI. |
| `localLanguageSummary` | One non-blank local-language statement per line. A legacy non-blank scalar remains readable and is normalized to a list only after editing. | Indexed as bounded-context signals and exposed to `opctx_get_entity`; helps AI translate evidence using the context's meaning rather than a global guess. |
| `scope.includes` | Responsibilities and behaviours that belong to this boundary, one per line. | Included in search signals and the explicit AI scope projection; positive evidence can be attributed to this context. |
| `scope.excludes` | Nearby responsibilities that do not belong here, one per line. | Gives AI a negative boundary and prevents incorrect attribution or ownership fallback. |
| `scope.businessCapabilities` | Durable business capabilities owned by the boundary, one per line. | Improves semantic search and explains why a process or question belongs to this context. |
| `scope.coreEntities` | Domain concepts owned here, not Java classes or database tables, one per line. | Grounds the local domain model in operator and AI detail views without becoming a code inventory. |
| `scope.keyDecisions` | Durable business decisions made inside the context, one per line. | Helps functional analysis identify where a decision is made; it is descriptive context, not executable rules. |
| `semanticBoundary.coreConcepts` | Central concepts whose meaning is stable inside this context, one per line. | Gives AI the positive vocabulary of the boundary. |
| `semanticBoundary.localConcepts` | Terms whose meaning is specifically local or different from neighbouring contexts, one per line. | Prevents AI from applying an adjacent context's definition. |
| `semanticBoundary.canonicalEntities` | Canonical domain entities or aggregates, not implementation class names, one per line. | Grounds domain reasoning while code discovery remains in code-search scopes. |
| `semanticBoundary.commands` | Business intents accepted by the context, one per line. | Helps AI interpret request/action language; it does not configure command handling. |
| `semanticBoundary.events` | Business facts emitted or recognized by the context, one per line. | Helps AI relate process and integration evidence; it does not define topics or schemas. |
| `semanticBoundary.invariants` | Business truths that must remain valid, one per line. | Grounds functional diagnosis and explanation; entries are context, not executable validation. |
| `semanticBoundary.ownsLanguage` | Phrases whose authoritative meaning belongs here, one per line. | Strengthens attribution when the same phrase occurs across contexts. |
| `semanticBoundary.doesNotOwn` | Phrases or concepts deliberately owned elsewhere, one per line. | Prevents AI from assigning responsibility based on wording alone. |
| `relations` | Guided semantic edges using canonical `targetType` plus an existing non-self `target`; legacy `targetContextId` remains readable. | Builds context navigation and supports ownership inference. |
| `evidence[].sourceRef` | Required non-blank logical reference to a stable source, never a customer record or payload. | Exposes explainable provenance; it does not automatically fetch or trust the source. |
| `evidence[].evidenceType` | Required non-blank source classification such as `domain-note`, `process-description` or `repository-documentation`. | Helps operators and AI judge what kind of support the source provides; it is not a confidence score. |
| `evidence[].note` | Optional non-blank explanation of what the source supports. | Keeps provenance interpretable without turning evidence into a root-cause claim. |
| `sourceCoverage` | Guided status plus reviewed/expected sources and limitations using the shared shape. | Exposed by `opctx_get_entity`; limitations become AI visibility limits. |
| `gaps` | Guided actionable boundary or ownership questions using the shared shape. | Feeds Open Questions and prevents unsupported conclusions. |
| `llmToolHints.answerWhenUserMentions` | User phrases that make this context useful, one per line. | Searchable context signals that help AI choose this entity; they never create ownership. |
| `llmToolHints.disambiguateFrom` | Nearby contexts or meanings that should be contrasted, one per line. | Prompts AI to verify semantic boundaries before answering. |
| `llmToolHints.usefulSearchKeywords` | Stable business-oriented discovery phrases, one per line. | Indexed as bounded-context signals and suggested for further code/context exploration; not proof that evidence belongs here. |
| `llmToolHints.explanationStyle` | Optional non-blank instruction describing how to explain the context. | Shapes presentation only; it cannot override evidence, ownership, visibility limits or tool policy. |

Guided, strongly anonymized CRM example:

```json
{
  "localLanguageSummary": [
    "A CRM contact preference is the channel choice maintained for an anonymized contact."
  ],
  "scope": {
    "includes": ["Validate and maintain CRM contact preferences"],
    "excludes": ["Manage authentication credentials"],
    "businessCapabilities": ["CRM contact preference management"],
    "coreEntities": ["ContactPreference"],
    "keyDecisions": ["Whether a requested CRM channel can be enabled"]
  },
  "semanticBoundary": {
    "coreConcepts": ["CRM contact preference"],
    "localConcepts": ["preferred channel"],
    "canonicalEntities": ["ContactPreference"],
    "commands": ["Change CRM contact preference"],
    "events": ["CRM contact preference changed"],
    "invariants": ["An enabled CRM channel must be supported for the anonymized contact"],
    "ownsLanguage": ["contact preference"],
    "doesNotOwn": ["authentication credential"]
  },
  "evidence": [
    {
      "sourceRef": "crm-domain-notes.md",
      "evidenceType": "domain-note",
      "note": "Defines the CRM contact preference boundary without customer data."
    }
  ],
  "llmToolHints": {
    "answerWhenUserMentions": ["CRM contact preference"],
    "disambiguateFrom": ["CRM campaign audience"],
    "usefulSearchKeywords": ["contact preference changed"],
    "explanationStyle": "Explain the CRM boundary in business language and state visibility limits."
  }
}
```

## Teams

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `type` | Established organization vocabulary such as `product` or `platform`; backend free text. | Describes team identity. It does not make the team an owner. |
| `matchSignals` | Stable team names, aliases or collaboration identifiers in confidence buckets. | Improves team search and label recognition; ownership still comes from systems or bounded contexts. |

## Glossary terms

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `id` | Required immutable kebab-case term ID, for example `crm-customer-profile`. | Canonical glossary key and graph target. |
| `term` | Required phrase users encounter. | Primary glossary label in search and AI tools. |
| `category` | Required backend free text. Current vocabulary includes `domain-term`, `business-term`, `integration-term`, `technical-term`. | Supports glossary filtering and tells AI what kind of language it is. |
| `lifecycleStatus` | Convention: `active`, `planned`, `deprecated`, `retired`. | Prevents obsolete language from appearing preferred. |
| `definition` | Concise definition of one concept. | Primary terminology grounding for AI explanations. |
| `localMeaningAndBoundaries` | One local-use or exclusion statement per item. | Resolves context-dependent meaning. |
| `aliases` | One alternate phrase per item. | Improves glossary matching and AI resolution. |
| `useFor` | One appropriate question/use per item. | Guides selection of glossary context. |
| `doNotConfuseWith` | Nearby concepts or explicit contrasts. | Prevents semantic conflation. |
| `canonicalReferences` | One existing `type:id` per item. Supported canonical types include system, repository, code-search-scope, process, integration, bounded-context, team, glossary-term/term and handoff-rule. | Creates typed graph navigation from language to operational entities. |
| `relatedTerms` | Existing glossary IDs; no self-reference. | Creates term-to-term navigation. |
| `responsibilityHints` | Descriptive system/context clue, never team routing. | Helps AI continue discovery while ownership resolution remains canonical. |
| `llmToolHints` | Terminology and evidence interpretation guidance. | Directly grounds AI tool use. |
| `notes` | Durable clarification or provenance. | Available in detailed context. |

## Handoff rules

| Field | What to enter and accepted values | Runtime / AI effect |
| --- | --- | --- |
| `id` | Required immutable kebab-case rule ID, for example `crm-contact-sync-delayed`. | Canonical rule key and graph target. |
| `title` | Required recognizable situation. | Primary matching and display label. |
| `confidence` | Backend free text; convention `high`, `medium`, `low`. | Calibrates the suggestion but never replaces evidence. |
| `useWhen` | Observable positive applicability conditions. | AI checks them before recommending handoff. |
| `doNotUseWhen` | Observable exclusions. | Suppresses misleading handoff matches. |
| `requiredEvidence` | Minimum collectible evidence, without real customer data. | Becomes the AI evidence checklist and visibility limit. |
| `expectedFirstAction` | Ordered, concrete receiving-side actions. | Included in technical handoff. |
| `references` | Existing operational-context IDs grouped by type; never team ownership. | Lets AI read supporting context before applying the rule. |
| `affectedSystems` | One `system:id` per item. | Scopes system impact and follow-up reads. |
| `affectedProcesses` | One `process:id` per item. | Grounds functional impact. |
| `affectedIntegrations` | One `integration:id` per item. | Grounds boundary and participant analysis. |
| `notes` | Durable clarification or provenance. | Available in detailed context. |
| `llmToolHints` | Evidence/tool reads to perform before applying the rule. | Directly guides AI handoff preparation. |
| `limitations` | Explicit cases or visibility boundaries not covered. | Prevents AI from presenting the rule as universal. |

## Maintenance consistency rule

When implementation changes an editable field, strict validator, relation
mapping, ownership behavior, code-search behavior or AI/tool projection,
update together:

- this guide,
- the relevant file-specific prompt,
- the UI tooltip guidance,
- anonymized CRM-only tests.
