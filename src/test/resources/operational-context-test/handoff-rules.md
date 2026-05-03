# Handoff Rules

### `integration-external-sync-failure`

**Title:** External synchronous integration failure

**Route decision**

Candidate teams: Integration Team

**Applies when**

- Evidence points to an external host, endpoint, or SOAP fault

**Required evidence**

- `correlationId`
- `environment`
- `host`
- `endpoint`
- `exception`

**Expected first actions**

- Verify the external call path, timeout status, and contract ownership

**Partner teams**

- Core Team

### `retain-with-current-owner`

**Title:** Keep the incident with the current owner

**Route decision**

Candidate teams: No handoff

**Applies when**

- Evidence still points to the current team or repository

**Required evidence**

- local runtime or repo match

**Expected first actions**

- Continue diagnosis locally

## Gaps

### `confirm-integration-routing`

**Gap id:** `confirm-integration-routing`

**Type:** `responsibility-routing`

**Severity:** `medium`

**Status:** `open`

**Description**

Confirm actual routing target for partner-service synchronous failures.
