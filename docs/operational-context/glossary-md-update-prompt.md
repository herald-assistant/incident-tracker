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
- Preserve the document heading and the `## Open Questions` section.
- If a meaning is unclear or disputed, add an open question instead of guessing.

## Example

A correctly filled file can look like this:

```md
# Glossary

### `soap-fault`

**Term:** SOAP Fault

**Category:** `integration-term`

**Definition:** Error returned by the partner SOAP API when the synchronous contract fails.

**Typical evidence signals**

- `SOAPFault`
- `Read timed out`
- `api.partner.local`
- `/partner/resource`

**Canonical references**

- `app-core-to-partner-sync`
- `partner-context`

### `work-item`

**Term:** Work Item

**Category:** `business-term`

**Definition:** Canonical business object processed by the main application flow.

**Typical evidence signals**

- `main-process`
- `app-core`
- `com.example.app.core`

**Canonical references**

- `core-context`
- `main-process`

## Open Questions

- None
```

## Input

`CURRENT FILE`

`NEW FACTS`
