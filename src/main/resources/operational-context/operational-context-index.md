# Operational Context Index

This directory stores the compact operational catalog used by the
`operational-context` evidence provider.

The current files are intentionally starter templates.
They should contain only information that helps:

- identify the affected runtime system,
- target the right GitLab repository or module,
- understand the likely business or technical flow,
- explain domain vocabulary,
- route the incident to the right owner.

## File Purpose

- `systems.yml`
  map of internal and external runtime systems with recognition signals and
  handoff hints.
- `repo-map.yml`
  mapping from runtime evidence to GitLab repositories, packages, classes and
  likely code areas.
- `processes.yml`
  short map of the business or operational flows that matter during incident
  triage.
- `integrations.yml`
  operational contracts between systems together with the signals that reveal
  them in incidents.
- `bounded-contexts.yml`
  semantic boundaries and domain language clusters that help explain what area
  of the business is affected.
- `teams.yml`
  ownership map aligned with the ids reused by the other catalog files.
- `glossary.md`
  local terms and synonyms that help interpret logs, code and handoff labels.
- `handoff-rules.md`
  short routing rules that change who should act next.

## How to use this catalog

1. Identify the system with `systems.yml`.
2. Target code with `repo-map.yml`.
3. Understand the flow with `processes.yml` and `integrations.yml`.
4. Interpret the domain with `bounded-contexts.yml` and `glossary.md`.
5. Route the incident with `handoff-rules.md` and `teams.yml`.

If the evidence is too weak to fill a file confidently, keep the structure
minimal and add an `openQuestions` entry instead of inventing ownership or
domain boundaries.
