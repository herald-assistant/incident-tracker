# 00 Parent Operational Context Update Prompt

This is the parent prompt for every operational-context update run.

It defines the global process, safety rules, data model principles, and role boundaries used by the Coordinator, Researcher, File Updaters, and Validator.

File-specific prompts define how to update one target file. This parent prompt defines how the entire run must behave.

## Purpose

Operational context is a compact, query-friendly operational knowledge graph.

It is used by deterministic mappers, evidence providers, GitLab/code-search scoping, LLM tools, incident analysis, follow-up chat, onboarding, system Q&A, and future system-analysis features.

It is not only incident documentation.
It is not only ownership documentation.
It is not a full architecture essay.
It is not temporary agent memory.

Every retained fact must support at least one of these use cases:

1. deterministic runtime-to-system mapping;
2. runtime-to-repository/code-scope mapping;
3. repository/module/package/class lookup;
4. process or business capability understanding;
5. integration dependency analysis;
6. bounded-context and domain-language interpretation;
7. incident triage or coordination;
8. change impact analysis;
9. LLM tool answers over systems, repos, processes, integrations, teams, and vocabulary.

Prefer compact structured facts over prose.
Prefer stable identifiers and match keys over narrative descriptions.

## Run model

The default workflow is a manual, single-repository update run:

```text
one selected source repository
  -> discovery report
  -> selected operational-context files updated in the catalog repository
  -> validation report
```

The current source repository is a partial source.
It is not the whole system.
It may contain only one side of an integration, one module of a larger bounded context, or one library used by a deployed component.

Therefore every update must be safe regardless of the order in which repositories are scanned.

## Run configuration

Every run must have a resolved run configuration.

The Coordinator may receive it directly in chat or load it from:

```text
operational-context/run/current-run.yml
```

A valid configuration looks like this:

```yaml
runId: backend-2026-05-04
mode: single-repository-manual-run

catalogRoot: incident-tracker
sourceRepositoryRoot: backend

promptsRoot: .github/prompts
catalogFilesRoot: src/main/resources/operational-context
discoveryRoot: .github/discovery
reportsRoot: .github/reports

sourceRepository:
  repositoryId: backend
  repositoryType: service-repository
  localPath: C:\Users\mknie\IdeaProjects\backend
  branch: main

rules:
  readOnlySourceRepository: true
  writeOnlyCatalogRoot: true
  doNotAnalyzeOtherWorkspaceRoots: true
  doNotInferGlobalAbsenceFromLocalAbsence: true
  openQuestionsAreCatalogLevelOnly: true
```

Minimum required fields:

```yaml
catalogRoot: <catalog repository root>
sourceRepositoryRoot: <source repository root to analyze>
```

Prefer named VS Code workspace roots over absolute paths.
If a raw local path is provided and tools cannot access it, ask the user to add it to the workspace.

Do not guess `sourceRepositoryRoot` when multiple repository roots are available.

## Default repository layout

The default catalog repository layout is:

```text
.github/agents/
  operational-context-coordinator.agent.md

.github/prompts/
  00-parent-operational-context-update-prompt.md
  01-repository-discovery-prompt.md
  02-validation-prompt.md
  systems-yml-update-prompt.md
  repo-map-yml-update-prompt.md
  integrations-yml-update-prompt.md
  processes-yml-update-prompt.md
  bounded-contexts-yml-update-prompt.md
  teams-yml-update-prompt.md
  glossary-md-update-prompt.md
  handoff-rules-md-update-prompt.md

.github/run/
  current-run.yml

.github/discovery/
  <repositoryId>.discovery.yml

.github/reports/
  <runId>-validation-report.md

src/main/resources/operational-context/
  systems.yml
  repo-map.yml
  integrations.yml
  processes.yml
  bounded-contexts.yml
  teams.yml
  glossary.md
  handoff-rules.md
  operational-context-index.md
```

If the actual catalog files root differs, use `catalogFilesRoot` from run configuration.

## Role boundaries

### Coordinator

The Coordinator resolves run configuration, controls the workflow, delegates to subagents, and produces the final summary.

The Coordinator must not use one source repository as global truth.

### Researcher

The Researcher reads one source repository and produces a discovery report.

The Researcher must not update final catalog files.

### File Updater

A File Updater updates one target file using:

- this parent prompt;
- the file-specific update prompt;
- the current target file;
- the discovery report from the current source repository.

A File Updater must not scan the source repository directly unless the Coordinator explicitly requests a tiny follow-up verification.

### Validator

The Validator checks the catalog after changes and produces a validation report.

The Validator is report-only by default.

## Read/write boundaries

The source repository is read-only.

```text
Allowed to read:
  sourceRepositoryRoot
  catalogRoot/promptsRoot
  catalogRoot/catalogFilesRoot
  catalogRoot/discoveryRoot

Allowed to write:
  catalogRoot/catalogFilesRoot
  catalogRoot/discoveryRoot
  catalogRoot/reportsRoot

Forbidden to write:
  sourceRepositoryRoot
  any other workspace root unless explicitly allowed
```

Do not modify, format, stage, commit, create, or delete files in `sourceRepositoryRoot`.

Do not inspect unrelated workspace roots unless they are explicitly listed as `additionalReadOnlyRoots`.

## Monotonic merge rule

Every update must be monotonic unless explicit contradictory evidence exists.

Allowed:

```text
- add a confirmed runtime signal;
- add a confirmed alias;
- add a confirmed repo/module/package/class hint;
- add a confirmed integration endpoint/queue/topic/event;
- add a confirmed relation;
- refine confidence with stronger evidence;
- append source/provenance hints;
- add a catalog-level open question when appropriate.
```

Forbidden without explicit contradictory evidence:

```text
- delete existing IDs;
- delete existing signals;
- replace a non-empty list with a shorter list from the current source repo;
- remove repo links from a system;
- remove systems from a repository;
- remove integrations/processes/contexts/teams because they are not visible locally;
- change responsibility to null;
- collapse multiple responsibility roles into a single owner;
- rewrite rich structured entries into shorter examples from prompts.
```

Absence of evidence in the current source repository is not evidence of global absence.

## Deterministic mapping keys

Capture stable match keys in structured fields whenever possible:

```text
deployment component names
service names
application names
container names
artifact IDs
GitLab project paths
module paths
package prefixes
class/interface/enum names
endpoint prefixes
queue names
exchange names
topic names
routing keys
event/schema names
Hikari pool names
DB schemas/tables
log markers
exception classes
scheduler/job names
workflow/state-machine markers
```

Do not hide these only in prose.

## Responsibility model

Do not force single ownership.

Model responsibility as relations such as:

```text
accountable owner
maintainer
contributor
participant
domain expert
first responder
integration partner
platform support
external owner
unknown / unresolved
```

Do not infer ownership from:

```text
package name
directory name
repository name
author name
team name appearing in a flow as worker/participant
local source presence only
```

If the source only shows participation, record participation, not ownership.

## Temporary uncertainty vs catalog-level questions

Do not use final `openQuestions` as temporary agent memory.

Use these buckets:

```yaml
candidateFacts:
  description: Concrete positive facts observed in the source repository.

deferredCandidates:
  description: Plausible cross-repository facts that require another source.

localAbsences:
  description: Things searched for but not observed locally; not global truth.

conflicts:
  description: Direct contradictions with existing catalog or another source.

openQuestions:
  description: Persistent catalog-level unresolved questions only.
```

Promote a question to final `openQuestions` only when:

- it affects deterministic mapping, analysis, responsibility, handoff, or semantic interpretation;
- it cannot be resolved from the current source;
- it is not merely a repo-local absence;
- it is valid at catalog level;
- it names the affected entity and suggested source needed for resolution.

## Source/provenance expectations

Important candidate facts should include source/provenance:

```yaml
sourceRefs:
  - repositoryId: backend
    filePath: src/main/java/com/example/crm/customer/CustomerController.java
    evidenceType: spring-controller
    symbol: CustomerController
```

The final catalog may keep compact provenance or confidence fields depending on the file-specific schema.
Discovery reports should retain richer provenance.

## Security and data hygiene

Never write these into operational context:

```text
credentials
API tokens
private keys
session cookies
personal customer data
sample production records
full payloads with sensitive business data
unredacted secrets from config files
```

Operationally useful names may be retained when needed:

```text
service names
internal host patterns
endpoint patterns
queue/topic names
schema/table names
class/package names
exception markers
```

Redact sensitive values while preserving useful pattern information.

## YAML and Markdown quality rules

For YAML files:

- use spaces only, never tabs;
- use 2-space indentation;
- quote values containing `:`, `{}`, `[]`, `#`, commas inside flow sequences, or leading special characters;
- prefer block sequences for endpoint paths containing `{id}` placeholders;
- preserve top-level wrapper and schema version required by the file-specific prompt;
- keep stable kebab-case IDs.

For Markdown files:

- preserve heading hierarchy;
- keep entries concise and structured;
- do not create duplicate `Open Questions` sections;
- do not paste large raw source snippets.

## Target file responsibilities

```text
systems.yml:
  runtime/deployment systems, external systems, platform systems, recognition signals, code scope links.

repo-map.yml:
  deterministic bridge from runtime/code evidence to repositories, modules, package/class hints, shared libraries, generated clients.

integrations.yml:
  sync/async/batch/file/DB/gateway-mediated contracts between systems with operational signals.

processes.yml:
  business or operational flows, lifecycle breakpoints, triggers, completion signals, failure modes.

bounded-contexts.yml:
  semantic boundaries, local language, context relationships, runtime/code signals.

teams.yml:
  responsibility relations, first response, domain expertise, maintainers, support roles.

glossary.md:
  local vocabulary, aliases, disambiguation, evidence signals, canonical references.

handoff-rules.md:
  coordination overlay and routing exceptions, not an ownership registry.

operational-context-index.md:
  catalog contract and file-purpose guide; update only when the model or file responsibilities change.
```

## File-specific prompt rule

A File Updater must read exactly one file-specific prompt for its target file.

Do not load all file-specific prompts in one update step.

Use:

```text
parent prompt
+ one target file prompt
+ current target file
+ discovery report
```

## Final output expectations for a run

A complete manual run should produce:

```text
- discovery report under discoveryRoot;
- updated selected catalog files under catalogFilesRoot;
- validation report under reportsRoot;
- concise chat summary with changed files, findings, and deferred candidates.
```

The chat summary should not paste full files unless explicitly requested.
