# repo-map.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

The maintenance UI edits `git`, `evidence` and `llmToolHints` through guided
fields rather than raw JSON. It preserves server-owned `git.inferred`, while
`projectPath` remains the canonical provider-relative path used by runtime
discovery and code tools.

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
context or system -> owner`.

Keep repository entries in the YAML shape below. If ownership is needed, resolve
or correct the referenced bounded context/system and the related system
code-search scope instead of adding owner-like fields here.

## YAML shape

```yaml
repositories:
  - id: crm-agent-portal
    name: CRM Agent Portal
    shortName: Agent Portal
    repositoryType: frontend
    lifecycleStatus: active
    criticality: high
    summary: Strongly anonymized CRM frontend repository for contact preference handling.
    purpose: Primary project to inspect after the CRM Agent Portal system is selected.
    aliases:
      - crm-portal
      - crm agent ui
    useFor:
      - Inspect anonymized CRM contact-screen behavior after the semantic target is known.
      - Distinguish portal behavior from CRM backend processing.
    git:
      provider: gitlab
      group: crm
      project: agent-portal
      projectPath: crm/agent-portal
      defaultBranch: main
      url: https://gitlab.example.com/crm/agent-portal
      aliases:
        - crm-agent-portal
      inferred: false
    references:
      systems:
        - crm-agent-portal
      processes:
        - crm-contact-preference-update
      boundedContexts:
        - crm-contact-preferences
      integrations:
        - crm-contact-to-consent
      handoffRules:
        - crm-contact-boundary
    matchSignals:
      exact:
        projectPaths:
          - crm/agent-portal
      strong:
        businessTerms:
          - CRM contact screen
      weak:
        phrases:
          - anonymized CRM contact form
    evidence:
      - sourceRef: crm/agent-portal/package.json
        evidenceType: reviewed-build-definition
        note: Strongly anonymized CRM frontend module identity; framework alone does not classify the system.
    llmToolHints:
      answerWhenUserMentions:
        - CRM contact screen
      disambiguateFrom:
        - CRM contact backend service
    relations:
      - type: supports
        targetType: system
        target: crm-agent-portal
        evidence: Primary strongly anonymized CRM frontend repository for the system.
```

## Update rules

- Treat `git.projectPath` as the GitLab link; keep the rest business-readable.
- Use `repositoryType: frontend` only for a reviewed primary repository of a
  system registered as `internal-service/frontend`. Framework files are
  evidence to inspect, not an automatic classification rule.
- Use `references` to connect a repository with systems, processes, bounded
  contexts, integrations and handoff rules as recognition signals.
- Do not add team references to imply repository ownership.
- Add aliases only when they help resolve a real user or tool signal.
- Use guided `evidence` cards only for durable provenance labels or safe
  relative references. They explain why the entry exists; runtime does not
  fetch those sources automatically.
- Use `llmToolHints.answerWhenUserMentions` for repository-selection phrases
  and `disambiguateFrom` for explicit contrasts. These values guide AI
  exploration but never grant access or define ownership.
- Leave code reading order, module boundaries and bounded-context-to-code
  ownership routing to the system-targeted scope in `code-search-scopes.yml`.
- When a repository is a direct internal library dependency of a system's
  primary repository, make sure it has a stable repository entry so it can be
  included in that system's single code-search scope.
- If a repository is unclear, keep the entry small and add a validation finding
  or open question outside this prompt.
