# systems.yml update prompt

Update only `systems.yml`.

## Goal

Keep a short map of systems:
- who owns the system
- what it does
- how to recognize it in logs or telemetry

## Instructions

1. Read the current `systems.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `type`, `ownerTeamId`, `partnerTeamIds`, `externalOwner`, `purpose`, `processes`, `contexts`, `repos`, `dependsOn`, `signals`, `handoff`.
- Prefer concrete runtime signals over architecture prose.
- Do not model every interface here; only keep the signals needed for incident routing.
- If ownership or topology is unclear, add an `openQuestions` entry.

## Input

`CURRENT FILE`

`NEW FACTS`
