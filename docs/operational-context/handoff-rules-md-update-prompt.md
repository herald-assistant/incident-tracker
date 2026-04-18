# handoff-rules.md update prompt

Update only `handoff-rules.md`.

## Goal

Keep only the routing rules that change who should act next.

## Instructions

1. Read the current `handoff-rules.md`.
2. Merge the new facts.
3. Return the full updated Markdown only.

## Rules

- Keep rules short.
- Use only: `Title`, `Route to`, `Use when`, `Required evidence`, `Expected first action`, `Partner teams`.
- Do not write architecture essays.
- Add a new rule only if it changes routing behavior.
- Preserve the document heading and the `## Open Questions` section.
- If a rule is unclear, keep the existing rule and add an open question.

## Example

A correctly filled file can look like this:

```md
# Handoff Rules

### `integration-external-sync-failure`

**Title:** External synchronous integration failure

**Route to:** Integration Team

**Use when**

- Evidence points to `api.partner.local`, `/partner/resource`, `SOAPFault`, or timeout symptoms.

**Required evidence**

- `correlationId`
- `environment`
- `host`
- `endpoint`
- `exception`

**Expected first action**

- Verify timeout status, external host reachability, and the partner contract owner.

**Partner teams**

- Core Team

### `retain-with-current-owner`

**Title:** Keep the incident with the current owner

**Route to:** No handoff

**Use when**

- Runtime and repository evidence still point to the same team area.

**Required evidence**

- local runtime or repo match

**Expected first action**

- Continue diagnosis locally and collect one more concrete signal before handing off.

## Open Questions

- None
```

## Input

`CURRENT FILE`

`NEW FACTS`
