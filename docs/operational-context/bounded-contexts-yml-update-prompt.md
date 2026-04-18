# bounded-contexts.yml update prompt

Update only `bounded-contexts.yml`.

## Goal

Keep a short map of semantic boundaries that matter for incidents.

## Instructions

1. Read the current `bounded-contexts.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `ownerTeamId`, `purpose`, `systems`, `repos`, `processes`, `terms`, `signals`, `relations`.
- Focus on language boundaries and incident routing, not full DDD documentation.
- Keep relations short: `target`, `type`, `via`.
- If a boundary is unclear, do not invent it; add an `openQuestions` entry.

## Input

`CURRENT FILE`

`NEW FACTS`
