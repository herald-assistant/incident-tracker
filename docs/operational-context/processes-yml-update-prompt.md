# processes.yml update prompt

Update only `processes.yml`.

## Goal

Keep a short map of business processes:
- owner team
- systems involved
- key steps
- completion signals

## Instructions

1. Read the current `processes.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `purpose`, `ownerTeamId`, `partnerTeamIds`, `systems`, `externalSystems`, `repos`, `contexts`, `steps`, `completionSignals`, `handoffHints`.
- Keep steps short and only include operationally useful signals.
- Do not invent process boundaries.
- If information is uncertain, add it to `openQuestions`.

## Input

`CURRENT FILE`

`NEW FACTS`
