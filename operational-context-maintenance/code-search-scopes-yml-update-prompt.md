# code-search-scopes.yml update prompt

## Purpose

Update `code-search-scopes.yml` as the catalog of repository sets that should be
read together for one semantic target. The file answers one question: for this
system, process, bounded context, or integration, which repositories should the
AI consider together and in what order?

Keep scopes narrow and explain why each repository is included. The actual code
search is performed by GitLab tools.

## YAML shape

```yaml
codeSearchScopes:
  - id: customer-request-handling-scope
    name: Customer request handling code scope
    scopeType: process
    lifecycleStatus: active
    summary: Repositories to inspect together for customer request handling.
    target:
      type: process
      id: customer-request-handling
    useFor:
      - Understand the customer request path across UI and service projects.
      - Prepare development stories or test scenarios for this business process.
    repositories:
      - repoId: customer-portal-ui
        role: primary
        priority: 1
        reason: User-facing request journey starts here.
        readFor:
          - user journey and labels
          - request submission behavior
      - repoId: case-management-service
        role: supporting
        priority: 2
        reason: Owns case state changes used by the portal.
        readFor:
          - business state handling
          - validation and handoff rules
    limitations:
      - Does not cover partner-owned systems beyond the cataloged handoff boundary.
```

## Update rules

- Use one `target` per scope.
- `role` should express why a repository belongs in the set, for example
  `primary`, `supporting`, `shared`, `reference`, `legacy`, or `migration-peer`.
- `priority` is the read order. `1` means start here.
- `reason` must be understandable to a business/system analyst.
- `readFor` should describe questions to answer, not low-level code clues.
- Keep `limitations` explicit when the scope is intentionally incomplete.

## Quality check

- Every `repoId` exists in `repo-map.yml`.
- Every scope has at least one repository.
- Every scope has a target that exists in the catalog.
- The scope helps continue analysis when the current repository is not enough.
