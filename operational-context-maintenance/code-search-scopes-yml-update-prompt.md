# code-search-scopes.yml update prompt

## Purpose

Update `code-search-scopes.yml` as the catalog of repository sets that should be
read together for one semantic target. The file answers one question: for this
system, process, bounded context, or integration, which repositories should the
AI consider together and in what order?

Keep scopes narrow and explain why each repository is included. For each
repository, also define the explicit GitLab search boundary: either the whole
repository or a short list of path prefixes/modules that belong to this semantic
target. The actual code search is performed by GitLab tools.

Code-search scopes do not define ownership. Owner and handoff are resolved from
the scope target's bounded context or system.

Keep scopes in the YAML shape below. If the owner is unclear, fix the target's
system or bounded context instead of adding ownership-like fields here.

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
        searchMode: path-prefixes
        pathPrefixes:
          - apps/customer-portal
          - libs/customer-request-ui
        reason: User-facing request journey starts here.
        readFor:
          - user journey and labels
          - request submission behavior
      - repoId: case-management-service
        role: supporting
        priority: 2
        searchMode: whole-repository
        reason: Contains case state changes used by the portal.
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
- `searchMode` is required for every repository and must be either
  `whole-repository` or `path-prefixes`.
- Use `whole-repository` when the repository mostly belongs to the semantic
  target or the relevant area cannot be separated safely.
- Use `path-prefixes` when the semantic target lives only in specific modules
  of a larger repository. Then `pathPrefixes` is required and must contain
  relative GitLab paths without a leading slash.
- `reason` must be understandable to a business/system analyst.
- `readFor` should describe questions to answer, not low-level code clues.
- Keep `limitations` explicit when the scope is intentionally incomplete.
- Keep ownership and routing out of this file.
- Do not store class names, endpoint inventories, exact files, package
  inventories, build files or generated-source details here. `pathPrefixes` are
  only a coarse search boundary.

## Quality check

- Every `repoId` exists in `repo-map.yml`.
- Every scope has at least one repository.
- Every scope has a target that exists in the catalog.
- Every repository declares `searchMode`.
- `path-prefixes` repositories have non-empty `pathPrefixes`.
- `whole-repository` repositories do not declare `pathPrefixes`.
- The scope helps continue analysis when the current repository is not enough.
