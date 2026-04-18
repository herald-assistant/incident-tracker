# glossary.md update prompt

Update only `glossary.md`.

## Goal

Keep only terms that help incident routing, repo lookup, or interpretation.

## Instructions

1. Read the current `glossary.md`.
2. Merge the new facts.
3. Return the full updated Markdown only.

## Rules

- Keep entries short.
- Use only: `Term`, `Category`, `Definition`, `Typical evidence signals`, `Canonical references`.
- Prefer local meanings over generic definitions.
- Do not keep terms that do not help incident analysis.
- If a meaning is unclear or disputed, add an open question instead of guessing.

## Input

`CURRENT FILE`

`NEW FACTS`
