# Handoff Rules

### `integration-external-sync-failure`

**Title:** External synchronous integration failure

**Route to:** Integration Team

**Use when**

- Evidence points to an external host, endpoint, or SOAP fault

**Required evidence**

- `correlationId`
- `environment`
- `host`
- `endpoint`
- `exception`

**Expected first action**

- Verify the external call path, timeout status, and contract ownership

**Partner teams**

- Core Team

### `retain-with-current-owner`

**Title:** Keep the incident with the current owner

**Route to:** No handoff

**Use when**

- Evidence still points to the current team or repository

**Required evidence**

- local runtime or repo match

**Expected first action**

- Continue diagnosis locally

## Open Questions

- None
