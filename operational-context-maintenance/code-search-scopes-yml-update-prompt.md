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

Prefer a bounded-context target when the code belongs to a known semantic
boundary. Use a system target only for system-wide code or when the bounded
context is unknown. Use process or integration targets for cross-context flows
or boundary relationships that intentionally span more than one bounded
context.

This file is the canonical bridge between semantic context and code:

- bounded context -> code-search scope -> repository -> path prefix -> code,
- code/file path -> repository + path prefix -> code-search scope -> bounded
  context -> owner.

Keep scopes in the YAML shape below. If the owner is unclear, fix the target's
system or bounded context instead of adding ownership-like fields here.

## YAML shape

```yaml
codeSearchScopes:
  - id: customer-requests-code-scope
    name: Customer Requests code scope
    scopeType: bounded-context
    lifecycleStatus: active
    summary: Repositories and modules to inspect for the Customer Requests bounded context.
    target:
      type: bounded-context
      id: customer-requests
    useFor:
      - Understand request intake behavior across UI and service projects.
      - Route code findings back to the Customer Requests owner.
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
- Prefer `target.type: bounded-context` when a bounded context owns the
  semantic behavior implemented by this code. Fall back to `system` only for
  system-wide code or unknown context.
- Do not duplicate code ownership by listing repositories directly on the
  bounded context. Use this scope as the navigational link from context to code.
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
- The target is the most precise semantic owner available: bounded context
  before system when the code can be mapped to a bounded context.
- Every repository declares `searchMode`.
- `path-prefixes` repositories have non-empty `pathPrefixes`.
- `whole-repository` repositories do not declare `pathPrefixes`.
- The scope helps continue analysis when the current repository is not enough.
- The scope supports reverse routing from repository/path prefix to bounded
  context and then to resolved ownership.
