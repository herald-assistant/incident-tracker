# integrations.yml update prompt

You maintain `src/main/resources/operational-context/integrations.yml`.

Treat the attached operational-context YAML files as the catalog model.
Use only the documented structure below plus useful fields already present in
this catalog.

## Target file contract

Keep this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-integrations
integrations: []
gaps: []
```

Each `integrations[]` entry describes a durable interaction contract or
operational data flow between catalog systems, repositories, external parties,
or platform services.

Preferred entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable integration name
  category: synchronous-api | asynchronous-messaging | database | file-transfer | batch | library-call | external-service
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  summary: One or two factual sentences.
  integrationStyle: REST | SOAP | messaging | database | file | scheduler | library | mixed | unknown
  flowDirection: inbound | outbound | bidirectional | internal | unknown
  criticality: critical | high | medium | low | unknown
  dataSensitivity: public | internal | confidential | restricted | unknown
  responsibilityStatus: confirmed | inferred | unknown | shared
  useFor: []
  participants:
    source:
      system: null
      repository: null
      team: null
    targets: []
    externalParties: []
  contract:
    operations: []
    payloads: []
    identifiers: []
    guarantees: []
  channels:
    http: []
    messaging: []
    database: []
    file: []
  transport:
    protocols: []
    http: []
    messaging: []
    database: []
    file: []
  references:
    repositories: []
    systems: []
    processes: []
    boundedContexts: []
    teams: []
    terms: []
    handoffRules: []
  matchSignals: []
  implementation: []
  responsibilities: []
  handoffHints: []
  failureModes: []
  gaps: []
```

Preserve additional existing fields only when they follow the current catalog
style and carry useful evidence. Remove duplicate, empty, speculative, or
shape-conflicting fields.

## Update rules

- Prefer editing an existing integration when evidence matches participants,
  endpoint paths, queues, topics, table names, payload names, WSDL/OpenAPI
  contracts, package prefixes, or known repositories.
- Add a new integration only when there is a durable contract or flow that
  operators will need to recognize again.
- Keep `id` stable once created. Use kebab-case and name both sides or the
  durable contract when possible.
- Use catalog `system` ids in participants and references. Deployment names,
  service names, hosts, and queues belong in `channels`, `transport`,
  `matchSignals`, or `implementation`.
- Do not paste full request bodies, tokens, full stack traces, or transient
  URLs into the catalog.
- Put ownership and routing facts in `responsibilities`, `references.teams`,
  and `handoffHints` when evidence supports them.
- Put unresolved durable questions in top-level or entry-level `gaps`; each gap
  must include enough evidence context to be actionable.
- Keep references aligned with the attached `systems.yml`, `repo-map.yml`,
  `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md`, and
  `handoff-rules.md`.

## Output rules

Return the complete updated `integrations.yml` content only.
Do not include commentary, markdown fences, diffs, or explanations.
