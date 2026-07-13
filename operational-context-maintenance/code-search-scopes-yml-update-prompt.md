# code-search-scopes.yml update prompt

## Purpose

Update `code-search-scopes.yml` as the code-navigation index for agents and team
delivery workspaces working with large systems. The file catalogs repository
sets that should be read together for one system, plus optional semantic slices
for bounded contexts. The required system scope answers one question: for this
system, which repositories should an agent consider together and in what order?

Keep scopes narrow and explain why each repository is included. For each
repository, also define the explicit GitLab search boundary: either the whole
repository or a short list of path prefixes/modules that belong to this semantic
target. The actual code search is performed by GitLab tools.

Code-search scopes do not define ownership. Owner and handoff are resolved from
the target system and, when known, its referenced bounded contexts.

Every cataloged system must have exactly one code-search scope with
`target.type: system` and `target.id` equal to the system id. Put the system's
primary implementation repository and directly imported internal library
repositories into the same system scope.

Optional bounded-context-targeted scopes are allowed when they help describe a
durable semantic slice inside one or more systems and support reverse mapping
from code location to bounded context and owner. They must not replace, fragment
or contradict the required system scope. Do not create process or integration
targeted scopes unless the catalog model gets an explicit governance rule for
them.

This file is the canonical bridge between semantic context and code:

- system -> required code-search scope -> repository -> path prefix -> code,
- bounded context -> optional code-search scope -> repository -> path prefix -> code,
- code/file path -> repository + path prefix -> code-search scope -> system ->
  bounded context or system owner.

Keep scopes in the YAML shape below. If the owner is unclear, fix the target
system or its referenced bounded contexts instead of adding ownership-like
fields here.

## YAML shape

```yaml
codeSearchScopes:
  - id: customer-portal-code-scope
    name: Customer Portal code scope
    scopeType: system
    lifecycleStatus: active
    summary: Repositories and modules to inspect for the Customer Portal system.
    target:
      type: system
      id: customer-portal
    useFor:
      - Understand request intake behavior across UI and service projects.
      - Keep broad code discovery efficient for the Customer Portal system.
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
      - repoId: customer-request-shared
        role: library
        priority: 2
        searchMode: path-prefixes
        pathPrefixes:
          - customer-request-contracts
        reason: Imported internal library detected from primary repository pom.xml files.
        readFor:
          - shared contracts and validation types used by the primary system code
    limitations:
      - Does not cover partner-owned systems beyond the cataloged handoff boundary.
```

## Update rules

- Use one `target` per scope.
- Maintain exactly one code-search scope per system. The scope id should be
  stable and system-oriented, for example `<system-id>-code-scope`.
- The required system scope target must be `target.type: system` and `target.id`
  must match the system id.
- The required system scope must contain the system's `primary` repository and
  any directly imported internal library repositories needed to understand the
  system code.
- Optional bounded-context scopes may target `bounded-context` when they make
  domain ownership, local-language navigation, and code-to-team attribution
  clearer. They should usually use repository/prefix subsets already present in
  the related system scope.
- Do not create process or integration targeted scopes unless the catalog model
  gets an explicit governance rule for them.
- Do not duplicate code ownership by listing repositories directly on the
  system or bounded context. Use this scope as the navigational link from
  system to code.
- `role` should express why a repository belongs in the set, for example
  `primary`, `library`, `supporting`, `shared`, `reference`, `legacy`, or
  `migration-peer`.
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

## Internal library detection

When creating or updating the code-search scope for a system:

- Identify the `primary` repository for that system first.
- Inspect every `pom.xml` in the primary repository. Use these POM files only to
  detect direct dependencies of the primary repository.
- Treat dependencies with the same or closely related internal `groupId` as
  candidates for internal libraries, especially when their artifact or module
  names map to cataloged repositories in `repo-map.yml`.
- Add matching internal library repositories to the same system code-search
  scope with role `library` or `shared-library`, a lower priority than the
  primary repository, and an explicit `reason` that the dependency was detected
  from primary repository POM files.
- Do not scan POM files from library repositories. Do not recursively add
  transitive libraries. If a library dependency cannot be mapped to a known
  repository, add or update `repo-map.yml` first or record an explicit
  limitation/open question.

## Quality check

- Every `repoId` exists in `repo-map.yml`.
- Every scope has at least one repository.
- Every cataloged system has exactly one system-targeted code-search scope.
- No system has more than one code-search scope.
- Optional bounded-context scopes target existing bounded contexts and do not
  replace the required system scope.
- No code-search scope targets a process or integration.
- Every system scope has one clear `primary` repository or priority `1`
  repository.
- Direct internal library repositories imported by the primary repository POM
  files are included in the system scope when they can be mapped to
  `repo-map.yml`.
- Every repository declares `searchMode`.
- `path-prefixes` repositories have non-empty `pathPrefixes`.
- `whole-repository` repositories do not declare `pathPrefixes`.
- The scope helps continue analysis when the current repository is not enough.
- The scope supports reverse routing from repository/path prefix to system and
  then to bounded-context or system ownership.
