# integrations.yml update prompt

Update only `integrations.yml`.

## Goal

Keep a short map of important operational integrations:
- source and target
- owner
- protocol and type
- runtime signals that reveal this integration

## Instructions

1. Read the current `integrations.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `from`, `to`, `ownerTeamId`, `partnerTeamIds`, `externalOwner`, `protocol`, `type`, `processes`, `contexts`, `signals`, `handoff`.
- One entry per meaningful integration contract, not per generic host or queue.
- Keep only signals that are useful in incidents.
- If ownership is unclear, do not guess; add an `openQuestions` entry.

## Input

`CURRENT FILE`

`NEW FACTS`
