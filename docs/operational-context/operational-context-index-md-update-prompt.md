# operational-context-index.md update prompt

Update only `operational-context-index.md`.

## Goal

Keep a short description of what each operational-context file is for.

## Instructions

1. Read the current `operational-context-index.md`.
2. Merge the new facts.
3. Return the full updated Markdown only.

## Rules

- Keep the file short.
- Describe purpose, not process theory.
- The index should explain how the files help incident analysis, routing, and repo targeting.

## Example

A correctly filled file can look like this:

```md
# Operational Context Index

This directory stores the compact operational catalog used by incident enrichment.

- `teams.yml`: maps ownership, default handoff targets, and team-specific runtime signals.
- `processes.yml`: describes business flows, key runtime steps, and completion signals.
- `systems.yml`: identifies internal and external systems from logs, telemetry, and deployment clues.
- `integrations.yml`: captures important sync or async contracts together with their failure signals.
- `repo-map.yml`: links runtime evidence to GitLab repositories, modules, and likely code areas.
- `bounded-contexts.yml`: shows semantic boundaries that affect routing, blame, and vocabulary.
- `handoff-rules.md`: keeps short routing rules for the next responsible team.
- `glossary.md`: explains local terms that help interpret evidence and search repositories.
```

## Input

`CURRENT FILE`

`NEW FACTS`
