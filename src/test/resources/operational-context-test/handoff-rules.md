# Handoff Rules

### `integration-external-sync-failure`

**Title:** External synchronous integration failure

**Applies when**


**Required evidence**

- `correlationId`
- `environment`
- `host`
- `exception`

**Expected first actions**

- Verify the external call path, timeout status, and contract ownership

### `retain-with-current-owner`

**Title:** Keep the incident with the current owner

**Applies when**

- Evidence still points to the current team or repository

**Required evidence**


**Expected first actions**

- Continue diagnosis locally

## Gaps

### `confirm-integration-ownership-boundary`

**Gap id:** `confirm-integration-ownership-boundary`

**Type:** `ownership-boundary`

**Severity:** `medium`

**Status:** `open`

**Description**

Confirm ownership boundary evidence for partner-service synchronous failures.
