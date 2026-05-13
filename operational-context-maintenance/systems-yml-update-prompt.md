# systems.yml update prompt

You maintain `src/main/resources/operational-context/systems.yml`.

Treat the attached operational-context YAML files as the catalog model.
Use only the documented structure below plus useful fields already present in
this catalog.

## Target file contract

Keep this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-system-map
systems: []
gaps: []
```

Each `systems[]` entry describes a stable system as the canonical catalog
entity. Deployment names, service names, runtime aliases, repositories,
queues, endpoints, and database objects are properties or recognition signals
for a system. They are not separate catalog entities.

Preferred entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable name
  type: internal-service | external-system | platform-service | database | library | operator-tool
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  aliases: []
  summary: One or two factual sentences.
  systemType: business-service | process-service | integration-service | shared-library | datastore | external-dependency | platform-capability
  criticality: critical | high | medium | low | unknown
  match:
    serviceNames: []
    containerNames: []
    projectNames: []
    packagePrefixes: []
    endpoints: []
    hosts: []
    queues: []
    topics: []
    schemas: []
    spans: []
    markers: []
  references:
    repositories: []
    processes: []
    boundedContexts: []
    integrations: []
    teams: []
    terms: []
    handoffRules: []
  dependencies:
    upstream: []
    downstream: []
    platformServices: []
  deployment:
    environments: []
    runtimeNames: []
  responsibilities: []
  handoffHints: []
  notes: []
```

Preserve additional existing fields only when they already follow the current
catalog style and carry useful evidence. Remove duplicate, empty, speculative,
or shape-conflicting fields.

## Update rules

- Prefer editing an existing system when the evidence matches an existing `id`,
  alias, repository, package prefix, endpoint, host, queue, or integration.
- Add a new system only when the evidence identifies a durable runtime or
  external dependency boundary that operators will need to recognize again.
- Keep `id` stable once created. Use kebab-case and avoid environment-specific
  suffixes unless the system identity itself contains them.
- Keep summaries short and factual. Do not paste stack traces, full URLs with
  transient query parameters, tokens, or large log fragments into the catalog.
- Put ownership and routing facts in `responsibilities`, `references.teams`,
  and `handoffHints` when evidence supports them.
- Put unresolved durable questions in top-level or entry-level `gaps`; each gap
  must include enough evidence context to be actionable.
- Keep references aligned with the attached `repo-map.yml`, `processes.yml`,
  `bounded-contexts.yml`, `integrations.yml`, `teams.yml`,
  `glossary.md`, and `handoff-rules.md`.

## Matching guidance

Use `match` for recurring signals that help AI and operators recognize the
system during analysis:

- service, pod, container, artifact, and project names
- package prefixes and class families
- endpoint prefixes and stable API paths
- messaging destinations and stable database/schema names
- log markers, span names, error codes, and business identifiers

Do not create a system from a single weak signal. If a host, endpoint, queue,
or package prefix appears without a stable system identity, record a precise
`gaps` item instead.

## Output rules

Return the complete updated `systems.yml` content only.
Do not include commentary, markdown fences, diffs, or explanations.
