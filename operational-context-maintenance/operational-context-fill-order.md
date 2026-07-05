# Operational context fill order

## Goal

Use this order when creating or updating operational context. The catalog should
help an analyst start in the right place, continue into the next repository or
system when needed, translate findings into business language, and prepare
development stories or automated test scenarios.

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
| Repository set to inspect together for one semantic target | `code-search-scopes.yml` |
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
- references to known processes, contexts, repositories and integrations,
- `ownership` only when there is durable system-level accountability.

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

### 9. Define code-search scopes

Update `code-search-scopes.yml` last, once systems, processes, contexts and
repositories are known.

Each scope should define:

- one semantic target,
- repositories to inspect together,
- role and priority per repository,
- reason and `readFor`,
- limitations.

This lets an agent continue analysis across repositories without guessing the
next project.

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
- Repository scope explains why to inspect a project, not how the project is
  organized internally.
- Uncertainty is visible as a limitation, open question or validation finding.
