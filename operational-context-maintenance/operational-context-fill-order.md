# Operational context fill order

Use this order when enriching `src/main/resources/operational-context`.

The YAML files in this directory are the source of truth for the catalog model.
Keep every file in the documented structure and use `gaps` for durable
unresolved questions.

## 1. `repo-map.yml`

Start with repositories, modules, source roots, package prefixes, runtime
mappings, and reusable `codeSearchScopes`. This connects analysis evidence to
real code and is the backbone for AI-guided fetching.

Fill first:

- `repositories[].git`
- `repositories[].sourceLayout`
- `repositories[].modules`
- `repositories[].runtimeMappings`
- `repositories[].codeSearch`
- `repositories[].sourceCoverage`
- `repositories[].scannedSubtrees`
- `codeSearchScopes[]`

## 2. `systems.yml`

Map stable systems as canonical catalog entities. Runtime service names,
deployment names, endpoints, queues, schemas, and package prefixes are signals
or properties of a system, not separate catalog entities.

Fill first:

- `systems[].id`, `name`, `type`, `lifecycleStatus`
- `systems[].summary`
- `systems[].systemType`
- `systems[].criticality`
- `systems[].match`
- `systems[].references`
- `systems[].dependencies`
- `systems[].deployment`
- `systems[].responsibilities`
- `systems[].handoffHints`

## 3. `bounded-contexts.yml`

Capture domain and semantic boundaries after code and system identities are
clear. Link contexts to repositories, systems, processes, integrations, teams,
terms, and handoff rules.

Fill first:

- `boundedContexts[].scope`
- `boundedContexts[].semanticBoundary`
- `boundedContexts[].references`
- `boundedContexts[].responsibilities`
- `boundedContexts[].matchSignals`
- `boundedContexts[].operationalSignals`
- `boundedContexts[].analysisHints`
- `boundedContexts[].llmToolHints`
- `boundedContexts[].sourceCoverage`

## 4. `integrations.yml`

Describe durable contracts and data flows between systems, external parties,
repositories, or platform services.

Fill first:

- `integrations[].participants`
- `integrations[].contract`
- `integrations[].channels`
- `integrations[].transport`
- `integrations[].references`
- `integrations[].matchSignals`
- `integrations[].implementation`
- `integrations[].responsibilities`
- `integrations[].handoffHints`
- `integrations[].failureModes`

## 5. `processes.yml`

Build process descriptions from confirmed systems, repositories, contexts, and
integrations. A process should represent a durable business or operational
flow, not a single implementation artifact.

Fill first:

- `processes[].processBoundary`
- `processes[].participants`
- `processes[].references`
- `processes[].dataAndArtifacts`
- `processes[].lifecycle`
- `processes[].steps`
- `processes[].responsibilities`
- `processes[].matchSignals`
- `processes[].completionSignals`
- `processes[].failureModes`
- `processes[].relations`
- `processes[].observability`
- `processes[].analysisHints`

## 6. `teams.yml`

Add teams and external parties after catalog targets are stable enough to avoid
speculative ownership.

Fill first:

- `teams[].matchSignals`
- `teams[].responsibilities`
- `teams[].routingHints`
- `teams[].handoffHints`
- `teams[].collaboration`
- `teams[].analysisHints`
- `externalParties[]`

Use `responsibilities[]` to link teams to catalog targets. Each responsibility
should include `targetType`, `targetId`, `role`, `status`, `confidence`, and
evidence when available.

## 7. `glossary.md`

Define terms that appear repeatedly in code, logs, business language, or
operator handoffs. Keep definitions short and link terms back from YAML through
`references.terms`.

## 8. `handoff-rules.md`

Keep handoff rules practical and operator-facing. Link rules from YAML through
`references.handoffRules` and use entity-level `handoffHints` for short routing
guidance.

## General rules

- Prefer updating existing entries over creating duplicates.
- Keep ids stable and kebab-cased.
- Store durable uncertainty in `gaps`, not in prose-only comments.
- Do not invent ownership, process boundaries, or integrations from a single
  weak signal.
- Keep references synchronized across YAML files.
- Keep match fields compact: recurring identifiers only, no secrets, no full
  stack traces, no transient URLs.
- Preserve useful existing evidence and source coverage.
- Return complete file content when using a file-specific update prompt.
