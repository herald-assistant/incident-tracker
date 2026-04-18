# repo-map.yml update prompt

Update only `repo-map.yml`.

## Goal

Keep a short map from runtime evidence to GitLab repositories and modules.

## Instructions

1. Read the current `repo-map.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `project`, `group`, `ownerTeamId`, `systems`, `processes`, `contexts`, `modules`, `signals`, `handoff`.
- Prefer paths, packages, class hints, and project names over generic repo description.
- Do not create duplicate repository entries.
- If module ownership is unclear, keep the repo-level owner and add an `openQuestions` entry.

## Input

`CURRENT FILE`

`NEW FACTS`
