# repo-map.yml update prompt

## Purpose

Update `repo-map.yml` as the catalog of GitLab projects and their business
meaning. A repository entry should help an analyst or AI tool choose the right
project to inspect after a system, process, bounded context, or integration has
been identified.

Keep repository entries semantic and navigational. Do not describe internal file
organization or low-level code clues here.

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
      teams:
        - customer-experience-team
      handoffRules:
        - route-customer-request-issues
    responsibilities:
      - teamId: customer-experience-team
        targetType: repository
        targetId: customer-portal-ui
        role: maintainer
        scope: product behavior and review
        status: current
        confidence: high
        evidence: repository ownership notes
        source: repo-map.yml
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
    handoffHints:
      defaultRouteLabel: Customer Experience maintainers
      firstResponderTeamIds:
        - customer-experience-team
      requiredEvidence:
        - affected journey or screen name
      expectedFirstActions:
        - Check whether the change belongs in this project or in a related service.
    relations:
      - type: supports
        targetType: system
        target: customer-portal
        evidence: primary user-facing project for the system
```

## Update rules

- Treat `git.projectPath` as the GitLab link; keep the rest business-readable.
- Use `references` to connect a repository with systems, processes, bounded
  contexts, integrations, teams and handoff rules.
- Add aliases only when they help resolve a real user or tool signal.
- Leave code reading order to `code-search-scopes.yml`.
- If a repository is unclear, keep the entry small and add a validation finding
  or open question outside this prompt.
