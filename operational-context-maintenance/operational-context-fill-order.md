# Operational context fill order

## Goal

Use this order when creating or updating operational context. The catalog should
act as a knowledge index for agents and team delivery workspaces: help start in
the right place, choose the right code-search scope when code is needed,
translate findings into business language, and prepare development stories or
automated test scenarios.

The catalog is not a copy of inventories from other sources. Keep detailed facts
in repositories, observability data, external system evidence, and specialized
tools.

## Ownership model

Ownership is stored only on:

- `systems.yml`,
- `bounded-contexts.yml`.

Bounded-context ownership has priority over system ownership. System ownership
is the fallback when the bounded context is unknown, missing, or the problem is
system-wide. Repositories, code-search scopes, processes, integrations, teams,
glossary terms and handoff rules never define owners.

Boundary problems should resolve both sides through referenced systems and
bounded contexts. If a concrete team is not cataloged, the resolver may expose
an inferred label such as "owner of system Salesforce" or "owner of domain
customer".

Generated YAML/Markdown should follow the current shapes in the file-specific
prompts. If a fact does not fit a shape, link the right catalog entity or record
an open question instead of inventing extra ownership or routing fields.

## Fact ownership

| Fact | File |
| --- | --- |
| Canonical system identity, purpose, system-level ownership and navigation | `systems.yml` |
| Local language, semantic boundaries and bounded-context ownership | `bounded-contexts.yml` |
| GitLab project identity and semantic references | `repo-map.yml` |
| Semantic-to-code bridge: target, repository set and GitLab search boundary | `code-search-scopes.yml` |
| Business or operational process path | `processes.yml` |
| System-to-system or partner boundary relationship | `integrations.yml` |
| Team identifiers, labels and collaboration clues | `teams.yml` |
| Business terms and disambiguation | `glossary.md` |
| Handoff situation, evidence needs and first actions | `handoff-rules.md` |
| Catalog rules, quality notes and current open questions | `operational-context-index.md` |

## Recommended order

### 1. Start from the question

Write down what the user or analyst is trying to do:

- learn the system,
- analyze an incident,
- transform business requirements into development stories,
- prepare automated test scenarios,
- continue analysis from one repository to another,
- decide ownership or handoff.

This decides which catalog entities need more detail. Do not enrich every file
equally.

### 2. Identify canonical systems

Update `systems.yml` first when the question does not yet have a stable target.
Create or refine only durable systems that users, analysts or teams recognize.

Minimum useful entry:

- `id`, `name`, `kind`, `summary`, `purpose`,
- aliases and use cases,
- references to known processes, contexts, integrations, teams and terms,
- `ownership` only when there is durable system-level accountability.

Do not list repositories on a system. Repository navigation for a system goes
through exactly one system-targeted entry in `code-search-scopes.yml`.

### 3. Add bounded context and glossary language

Update `bounded-contexts.yml` and `glossary.md` when the main risk is language
ambiguity or domain ownership. This is especially useful for business analysts,
testers and requirements work.

Minimum useful bounded-context entry:

- local meaning,
- what belongs here,
- what not to confuse with,
- related systems, processes and terms,
- `ownership` only when there is durable bounded-context accountability.

Do not use `bounded-contexts.yml` as the primary map from context to
repositories or modules. The default code path for a bounded context starts
from the related system scope:

`bounded-context -> system -> code-search scope -> repository -> path prefix -> code`.

An optional bounded-context code-search scope may be added later when it helps
describe a durable semantic slice or route a code location back to bounded
context ownership, but it must not replace the required system scope.

### 4. Add team labels only after owners exist

Update `teams.yml` when a team id used by system or bounded-context ownership
needs a readable label, aliases or collaboration context.

Minimum useful entry:

- team id/name,
- aliases,
- what the team label means,
- references that help navigation.

Do not add reverse ownership to teams. If ownership is unclear, add an open
question on the system or bounded context instead.

### 5. Describe the process path

Update `processes.yml` when the user asks about a business journey, a use case,
acceptance criteria, or test scenarios.

Process steps should be business, system or bounded-context milestones. They
should help answer:

- what the user expected,
- where the process stopped,
- which system or bounded context should be checked next,
- what evidence is needed before a boundary handoff.

### 6. Add integrations as boundary relationships

Update `integrations.yml` when the relevant fact is a boundary between systems,
bounded contexts or external parties.

Useful integration entries include:

- source and target systems,
- source and target bounded contexts when known,
- business purpose,
- high-level interaction style and direction,
- related process and failure modes.

Keep detailed source-specific facts and owner assignments outside this file.

### 7. Add handoff rules for evidence, not routing

Update `handoff-rules.md` when an analyst needs a repeatable way to decide
whether a boundary handoff is needed and what evidence must be collected.

Minimum useful entry:

- applies/does-not-apply conditions,
- required evidence,
- expected first actions,
- operational context links to systems, bounded contexts, processes or
  integrations.

The receiving owner is resolved from linked systems and bounded contexts, not
from the rule.

### 8. Map repositories

Update `repo-map.yml` after the semantic target is clear. Repository entries
should answer:

- which GitLab project is related to this system, process, context or
  integration,
- why it matters,
- when to inspect it.

Repository entries should not try to describe internal code organization or
define maintainers as owners.

Repository references to systems, processes or bounded contexts are recognition
signals and navigation hints owned by `repo-map.yml`. They are not the
canonical route from a system to code or from code to ownership. When code
ownership matters, model the route through a `code-search-scopes.yml` entry with
the related system as target.

### 9. Define code-search scopes

Update `code-search-scopes.yml` last, once systems, processes, contexts and
repositories are known.

Each scope should define:

- one target,
- exactly one required code-search scope per system,
- for required system scopes, `target.type: system` and `target.id` matching
  the system id,
- optional bounded-context scopes only when they provide useful semantic
  narrowing, code-to-bounded-context attribution, or code-to-team routing and
  remain consistent with the related system scope,
- repositories to inspect together,
- one `primary` repository or priority `1` repository for the system,
- directly imported internal library repositories inferred from all `pom.xml`
  files in the primary repository,
- role and priority per repository,
- `searchMode` per repository: `whole-repository` or `path-prefixes`,
- `pathPrefixes` only when a larger repository contains the relevant bounded
  context or process in specific modules,
- reason and `readFor`,
- limitations.

This lets an agent continue analysis across repositories without guessing the
next project. It also lets the agent restrict GitLab search to known modules
without turning operational context into a class, endpoint or file inventory.
System scopes serve broad, efficient code navigation. Bounded-context scopes
serve semantic attribution: they help connect a repository path or module back
to a bounded context and then to the responsible team.

When deriving library repositories, inspect every `pom.xml` in the primary
repository and look for direct internal dependencies with the same or closely
related `groupId`. Add only library repositories that can be mapped to
`repo-map.yml`. Do not scan POM files from those library repositories and do not
recursively add transitive dependencies.

The intended navigation paths are:

- from semantics to code:
  `system -> required code-search scope -> repository -> path prefix -> code`,
- from bounded-context semantics to code:
  `bounded-context -> optional code-search scope -> repository -> path prefix -> code`,
- from code to owner:
  `code/file path -> repository + path prefix -> code-search scope -> system ->
  bounded context or resolved system ownership`.

### 10. Run validation and record gaps

Use the operational context API validation and maintenance findings before and
after larger updates.

Check:

- missing referenced ids,
- missing system/bounded-context ownership for concrete user tasks,
- duplicate facts across files,
- entries without useful navigation value,
- open questions that block a concrete user task.

## Minimal useful catalog

For a new area, stop when these questions have clear answers:

- What system or process should analysis start from?
- Which bounded context or system owns the first response?
- Which bounded context or business term explains the user language?
- Which repository or repository set should be inspected next?
- What evidence is needed before a handoff?
- What limitation or open question remains?

## Review checklist

- The entry helps the next analysis step.
- Facts have one owner file.
- References use existing catalog ids.
- Handoff guidance is understandable to a business/system analyst.
- Bounded-context-owned code is linked through the related system-targeted
  code-search scope, optionally refined by a bounded-context scope, not through
  repository lists embedded in the bounded context.
- Repository scope explains why to inspect a project, not how the project is
  organized internally beyond the coarse `searchMode`/`pathPrefixes` search
  boundary.
- Uncertainty is visible as a limitation, open question or validation finding.
