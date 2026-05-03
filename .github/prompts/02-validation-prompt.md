# 02 Operational Context Validation Prompt

This prompt is used by the Validator phase.

The Validator checks the operational-context catalog after a manual single-repository update run.

The Validator is report-only by default.
It must not perform semantic edits unless the user explicitly asks.

## Inputs

The Validator receives:

```yaml
catalogRoot: <catalog repository root>
sourceRepositoryRoot: <source repository root analyzed in this run>
runId: <run id>
repositoryId: <source repository id>
parentPrompt: .github/prompts/00-parent-operational-context-update-prompt.md
validationPrompt: .github/prompts/02-validation-prompt.md
discoveryReport: .github/discovery/<repositoryId>.discovery.yml
catalogFilesRoot: src/main/resources/operational-context
changedFiles: []
outputValidationReport: .github/reports/<runId>-validation-report.md
```

The source repository is read-only.
The Validator should validate catalog files under `catalogRoot` only.

## Mission

Validate that the changed operational-context catalog remains:

- syntactically valid;
- internally consistent;
- safe for partial-source, single-repository updates;
- useful for deterministic mapping;
- usable as a query-friendly operational knowledge graph;
- free from repo-local uncertainty promoted into persistent catalog data;
- free from secrets and sensitive sample data.

## Default behavior

Report-only.

The Validator must produce a validation report.
It may propose fixes.
It must not apply fixes unless the user explicitly requested automatic validation fixes.

Exception: if the user explicitly enabled `allowMechanicalValidationFixes: true`, the Validator may apply purely mechanical fixes such as:

- YAML quote fixes;
- duplicate `Open Questions` heading cleanup;
- indentation correction;
- sorting duplicate list values;
- removing obvious empty duplicate entries.

Even then, do not apply semantic fixes automatically.

## Validation report output

Write or return a Markdown report at:

```text
.github/reports/<runId>-validation-report.md
```

Use this structure:

```md
# Operational Context Validation Report

## Run

- Run ID:
- Source repository:
- Catalog root:
- Files checked:
- Discovery report:

## Summary

- Overall status: PASS | PASS_WITH_WARNINGS | FAIL
- Blocking findings:
- Warning findings:
- Info findings:

## Findings

### `<finding-id>`

- Severity: blocker | warning | info
- Category: syntax | reference-integrity | partial-source-safety | responsibility | deterministic-mapping | open-questions | discovery-promotion | security | quality
- Files:
- Entities:
- Problem:
- Evidence:
- Recommendation:
- Auto-fixable: yes | no

## Cross-file reference audit

## Partial-source safety audit

## Open Questions audit

## Discovery promotion audit

## Security/data hygiene audit

## Suggested next actions
```

## Severity definitions

```text
blocker:
  Catalog is invalid, unsafe, unparsable, or likely to break deterministic mapping.

warning:
  Catalog is usable but has gaps, ambiguous relations, weak provenance, or possible quality issues.

info:
  Non-blocking observations, improvement ideas, or deferred candidates.
```

## Syntax validation

Check YAML files:

```text
systems.yml
repo-map.yml
integrations.yml
processes.yml
bounded-contexts.yml
teams.yml
```

Validate:

- YAML parses;
- no tab indentation;
- 2-space indentation is used consistently;
- endpoint values with `{id}` placeholders are quoted or use block sequences;
- values with `:`, `{}`, `[]`, `#`, or commas inside flow sequences are quoted;
- top-level wrapper exists;
- schema version exists;
- primary list key exists.

Check Markdown files:

```text
glossary.md
handoff-rules.md
operational-context-index.md
```

Validate:

- heading hierarchy is coherent;
- no duplicate top-level title;
- no duplicate `Open Questions` sections unless intentionally scoped;
- entries follow the expected structure from their file-specific prompt;
- no large pasted source code blocks or raw sensitive payloads.

## Expected top-level wrappers

Unless file-specific prompts define otherwise, expect:

```yaml
systems.yml:
  schemaVersion: 2
  systems: []
  openQuestions: []

repo-map.yml:
  schemaVersion: 2
  repositories: []
  openQuestions: []

integrations.yml:
  schemaVersion: 2
  integrations: []
  openQuestions: []

processes.yml:
  schemaVersion: 2
  processes: []
  openQuestions: []

bounded-contexts.yml:
  schemaVersion: 2
  boundedContexts: []
  openQuestions: []

teams.yml:
  schemaVersion: 2
  teams: []
  openQuestions: []
```

For Markdown files, validate against the file-specific prompt rather than forcing YAML wrappers.

## Cross-file reference validation

Build an in-memory ID index from all catalog files.

Check these references when present:

```text
systems -> repos
systems -> processes
systems -> contexts / boundedContexts
systems -> dependsOn systems
systems -> integrations
systems -> teams/responsibility team IDs

repositories -> systems
repositories -> processes
repositories -> boundedContexts
repositories -> teams/responsibility team IDs

integrations -> source/target/mediator systems
integrations -> processes
integrations -> boundedContexts
integrations -> repositories
integrations -> teams/responsibility team IDs

processes -> systems
processes -> externalSystems
processes -> repositories
processes -> integrations
processes -> boundedContexts
processes -> teams/responsibility team IDs

boundedContexts -> systems
boundedContexts -> repositories
boundedContexts -> processes
boundedContexts -> integrations or relation IDs
boundedContexts -> teams/responsibility team IDs

teams -> systems
teams -> repositories
teams -> processes
teams -> integrations
teams -> boundedContexts

handoff-rules.md -> mentioned canonical IDs when clearly expressed

glossary.md -> canonical references
```

Classify missing references:

```text
blocker:
  missing reference breaks deterministic mapping or points to a clearly required internal entity.

warning:
  missing reference may be a deferred cross-repo candidate or external system not yet modeled.

info:
  missing reference is mentioned only as free text and does not break machine-readable fields.
```

## Deterministic mapping validation

For every internal runtime system, check whether it has at least one useful recognition signal, such as:

```text
service name
application name
container name
deployment component
artifact ID
endpoint prefix
queue/topic/event
Hikari pool
DB schema
package prefix
class hint
log marker
exception class
```

For every repository, check whether it has at least one useful code-scope signal, such as:

```text
Git project path
module path
package prefix
class hint
artifact ID
source root
endpoint prefix
client/listener class
```

For every integration, check whether it has at least one useful operational signal, such as:

```text
endpoint prefix
host pattern
queue/exchange/topic/routing key
event name/schema
client class
listener class
producer class
exception class
error marker
```

For every process, check whether it has at least one useful trigger or lifecycle signal:

```text
endpoint
event
job
state transition
workflow marker
class hint
completion signal
```

Warn when entries are only prose and lack deterministic mapping keys.

## Partial-source safety validation

Check that the update did not treat the current source repository as global truth.

Flag as blocker or warning if changed files appear to:

- delete existing facts because they were not visible in the current repo;
- replace non-empty lists with shorter local lists;
- remove repo links, system links, context links, or integration links without contradiction evidence;
- change responsibility/ownership to null based on local absence;
- promote `localAbsences` from the discovery report into final catalog facts;
- add final `openQuestions` that are only repo-local scratchpad notes;
- infer global topology from one library or one generated client.

## Responsibility validation

Operational context does not require single ownership.

Validate that responsibility fields, when present, preserve distinctions such as:

```text
accountable owner
maintainer
participant
contributor
domain expert
first responder
integration partner
platform support
external owner
unknown/unresolved
```

Flag findings when:

- a team is marked as accountable owner based only on local package/repo presence;
- participation is converted into ownership;
- multi-team responsibility is collapsed without evidence;
- handoff rules say only `owner` when no owner is modeled;
- ownership is invented from CODEOWNERS when CODEOWNERS only indicates maintainers/reviewers.

## Open Questions validation

Final `openQuestions` must be catalog-level.

A valid final open question should include:

- affected entity ID;
- affected file or domain;
- why it matters;
- what source should resolve it;
- status or confidence if supported by schema.

Flag as warning when open questions are vague, for example:

```text
Who owns this?
Check later.
Need more info.
```

Flag as blocker if open questions are used as temporary memory or to store local absences from one repository.

## Discovery promotion validation

Read the discovery report and compare it with changed files.

Check:

- positive `candidateFacts` were promoted into the right file types;
- `deferredCandidates` were not promoted as final facts unless supported by concrete evidence;
- `localAbsences` were not promoted as deletions or final open questions;
- `conflicts` were acknowledged or preserved for human review;
- target file recommendations were followed or intentionally skipped.

A skipped candidate is not automatically wrong. Report it if it seems significant and unexplained.

## Security/data hygiene validation

Flag blockers for:

```text
API tokens
passwords
private keys
session cookies
authorization headers
personal customer data
sample production records
full business payloads with sensitive data
unredacted secrets from configuration files
```

Flag warnings for over-specific data that may be unnecessary:

```text
full internal URLs with environment-specific hostnames when a host pattern would suffice
raw sample request/response payloads
long stacktraces pasted into catalog files
large code blocks copied from source files
```

## File-specific quality validation

### systems.yml

Check that systems are runtime/deployment entities, external systems, platform systems, databases, brokers, or frontends.

Warn if code-only libraries are modeled as systems without deployment evidence.

### repo-map.yml

Check that repositories include deterministic code-scope signals and can support code search scoping.

Warn if a deployed component's known shared library/generated client scope is omitted after being discovered.

### integrations.yml

Check that integrations represent operational contracts, not random hosts.

Warn if a mediated integration fails to distinguish source-to-mediator from mediator-to-final-target when both are operationally meaningful.

### processes.yml

Check that processes represent meaningful business/operational flows, not every method call.

Warn if process steps lack triggers, completion signals, or runtime/code fingerprints.

### bounded-contexts.yml

Check that bounded contexts represent semantic boundaries and local language.

Warn if the file becomes a duplicate of systems, repo-map, or integrations.

### teams.yml

Check that teams model responsibility relations, not just ownership.

Warn if ownership is inferred without explicit evidence.

### glossary.md

Check that glossary entries are useful when returned alone by an LLM tool.

Warn if entries lack aliases, disambiguation, evidence signals, or canonical references where relevant.

### handoff-rules.md

Check that handoff rules change routing or coordination behavior.

Warn if the file duplicates every integration instead of documenting exceptions, coordination rules, or ambiguous ownership situations.

## Validation report example

```md
# Operational Context Validation Report

## Run

- Run ID: `crm-core-api-2026-05-04`
- Source repository: `crm-core-api`
- Catalog root: `incident-tracker`
- Files checked:
  - `systems.yml`
  - `repo-map.yml`
  - `integrations.yml`
  - `processes.yml`
  - `bounded-contexts.yml`
  - `glossary.md`
- Discovery report: `operational-context/discovery/crm-core-api.discovery.yml`

## Summary

- Overall status: PASS_WITH_WARNINGS
- Blocking findings: 0
- Warning findings: 2
- Info findings: 1

## Findings

### `missing-email-platform-system`

- Severity: warning
- Category: reference-integrity
- Files: `integrations.yml`, `systems.yml`
- Entities: `crm-core-to-email-platform`, `email-platform`
- Problem: Integration target system `email-platform` is referenced but no matching system entry exists yet.
- Evidence: Discovery report notes this as a target-side counterpart outside the analyzed repository.
- Recommendation: Keep the integration as a partial contract if the schema supports external targets, or add a minimal external system entry after checking the Email Platform repository/ownership source.
- Auto-fixable: no

### `local-absence-not-promoted`

- Severity: info
- Category: partial-source-safety
- Files: none
- Entities: `no-kafka-bindings-observed`
- Problem: Kafka absence was correctly kept in discovery `localAbsences` and not promoted to final catalog.
- Evidence: `crm-core-api.discovery.yml`
- Recommendation: No action.
- Auto-fixable: no

### `glossary-term-missing-disambiguation`

- Severity: warning
- Category: quality
- Files: `glossary.md`
- Entities: `qualified-lead`
- Problem: Glossary term has evidence signals but no `Do not confuse with` section.
- Evidence: The discovery report included a distinction from marketing-qualified lead.
- Recommendation: Add a short disambiguation section if the glossary prompt format allows it.
- Auto-fixable: yes

## Cross-file reference audit

- Systems -> Repositories: pass
- Repositories -> Systems: pass
- Integrations -> Systems: warning for `email-platform`
- Processes -> Contexts: pass
- Glossary canonical references: pass

## Partial-source safety audit

- No existing lists were shortened.
- No existing ownership was nulled.
- No local absences were promoted into final files.

## Open Questions audit

- No repo-local scratchpad questions were added.

## Discovery promotion audit

- Positive candidates promoted: 6
- Deferred candidates promoted: 0
- Local absences promoted: 0

## Security/data hygiene audit

- No secrets or personal data detected.

## Suggested next actions

1. Analyze the Email Platform repository to resolve target-side contract details.
2. Add or confirm external system entry for `email-platform` if it is a stable dependency.
```
