# teams.yml update prompt

Update only `teams.yml`.

## Goal

Keep a short operational map of teams:
- who owns what
- how to recognize their area in runtime
- where to hand off

## Instructions

1. Read the current `teams.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `purpose`, `owns`, `signals`, `handoff`.
- Prefer short phrases over long descriptions.
- Do not invent ownership.
- Do not duplicate teams.
- If a fact is unclear, keep the current value and add an `openQuestions` entry.
- Keep list values short and operational.

## Input

`CURRENT FILE`

`NEW FACTS`
