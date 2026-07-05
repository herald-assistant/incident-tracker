# repo-map.yml update prompt

## Purpose

Update `repo-map.yml` as the catalog of GitLab projects and their business
meaning. A repository entry should help an analyst or AI tool choose the right
project to inspect after a system, process, bounded context, or integration has
been identified.

Keep repository entries semantic and navigational. Do not describe internal file
organization or low-level code clues here.

## Ownership rule

Repository entries do not define ownership. Owner and handoff are resolved from
bounded contexts or systems selected through operational context. Repository
`references.boundedContexts` are recognition and navigation signals; they are
not the canonical code ownership route. For code-to-owner routing, use
`code-search-scopes.yml`:

`code/file path -> repository + path prefix -> code-search scope -> bounded
context -> owner`.

Keep repository entries in the YAML shape below. If ownership is needed, resolve
or correct the bounded-context/system target and its code-search scope instead
of adding owner-like fields here.

## YAML shape

```yaml
repositories:
  - id: customer-portal-ui
    name: Customer Portal UI
    shortName: Portal UI
    repositoryType: frontend
    lifecycleStatus: active
    criticality: high
    summary: User interface for customer request handling.
    purpose: Primary project to inspect when the question concerns portal screens and user-facing behavior.
    aliases:
      - portal-ui
      - customer portal frontend
    useFor:
      - Inspect portal-facing behavior after the system or process is identified.
      - Confirm visible labels and user journey assumptions.
    git:
      provider: gitlab
      group: business-platform
      project: customer-portal-ui
      projectPath: business-platform/customer-portal-ui
      defaultBranch: main
      url: https://gitlab.example.com/business-platform/customer-portal-ui
      aliases:
        - customer-portal
      inferred: false
    references:
      systems:
        - customer-portal
      processes:
        - customer-request-handling
      boundedContexts:
        - customer-requests
      integrations:
        - portal-to-case-management
      handoffRules:
        - customer-request-boundary
    matchSignals:
      exact:
        projects:
          - customer-portal-ui
      strong:
        terms:
          - portal UI
      weak:
        phrases:
          - customer request screen
    relations:
      - type: supports
        targetType: system
        target: customer-portal
        evidence: primary user-facing project for the system
```

## Update rules

- Treat `git.projectPath` as the GitLab link; keep the rest business-readable.
- Use `references` to connect a repository with systems, processes, bounded
  contexts, integrations and handoff rules as recognition signals.
- Do not add team references to imply repository ownership.
- Add aliases only when they help resolve a real user or tool signal.
- Leave code reading order, module boundaries and bounded-context-to-code
  ownership routing to `code-search-scopes.yml`.
- If a repository is unclear, keep the entry small and add a validation finding
  or open question outside this prompt.
