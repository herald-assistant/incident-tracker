# Operational context fill order

## Goal

Use this order when creating or updating operational context. The catalog should
help an analyst start in the right place, continue into the next repository or
system when needed, translate findings into business language, and prepare
development stories or automated test scenarios.

The catalog is not a copy of inventories from other sources. Keep detailed facts
in repositories, observability data, external system evidence, and specialized
tools.

## Fact ownership

| Fact | File |
| --- | --- |
| Canonical system identity, purpose, owner and handoff entry point | `systems.yml` |
| GitLab project identity and semantic references | `repo-map.yml` |
| Repository set to inspect together for one semantic target | `code-search-scopes.yml` |
| Business or operational process path | `processes.yml` |
| System-to-system or partner handoff relationship | `integrations.yml` |
| Local language and semantic boundaries | `bounded-contexts.yml` |
| Team responsibility and routing hints | `teams.yml` |
| Business terms and disambiguation | `glossary.md` |
| Routing rule, evidence needs and first actions | `handoff-rules.md` |
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
- references to known processes, contexts, repositories and owners,
- handoff hints or responsibilities.

### 3. Add team and handoff owner

Update `teams.yml` and `handoff-rules.md` early when ownership is unclear.
This gives AI and analysts a safe next step even before code reading starts.

Minimum useful entry:

- team id/name,
- what the team owns,
- when to route there,
- required evidence,
- expected first action,
- partner teams and visibility limits.

### 4. Add bounded context and glossary language

Update `bounded-contexts.yml` and `glossary.md` when the main risk is language
ambiguity. This is especially useful for business analysts, testers and
requirements work.

Minimum useful entry:

- local meaning,
- what belongs here,
- what not to confuse with,
- related systems, processes, teams and terms.

### 5. Describe the process path

Update `processes.yml` when the user asks about a business journey, a use case,
acceptance criteria, or test scenarios.

Process steps should be business or ownership milestones. They should help
answer:

- what the user expected,
- where the process stopped,
- which system owns the next step,
- what handoff or partner owner may be needed.

### 6. Add integrations as handoff relationships

Update `integrations.yml` when the relevant fact is a boundary between systems
or owners.

Useful integration entries include:

- source and target systems,
- business purpose,
- high-level interaction style and direction,
- related process and bounded contexts,
- owners, partners, failure modes and handoff hints.

Keep detailed source-specific facts outside this catalog.

### 7. Map repositories

Update `repo-map.yml` after the semantic target is clear. Repository entries
should answer:

- which GitLab project is related to this system, process, context or
  integration,
- why it matters,
- who maintains it,
- when to inspect it.

Repository entries should not try to describe internal code organization.

### 8. Define code-search scopes

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

### 9. Run validation and record gaps

Use the operational context API validation and maintenance findings before and
after larger updates.

Check:

- missing referenced ids,
- ambiguous ownership,
- duplicate facts across files,
- entries without useful handoff or navigation value,
- open questions that block a concrete user task.

## Minimal useful catalog

For a new area, stop when these questions have clear answers:

- What system or process should analysis start from?
- Which team owns the first response?
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
