# repo-map.yml update prompt

Field formats, constrained values and runtime/AI effects are defined in
[`operational-context-field-guidance.md`](operational-context-field-guidance.md).

The maintenance UI edits `git`, `evidence` and `llmToolHints` through guided
fields rather than raw JSON. `projectPath` is the required provider-relative
path used by runtime discovery and code tools.

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

The first YAML examples in all nine file prompts form one fictional CRM
catalogue and are validated together. Copy only facts justified for the task;
examples are not templates to regenerate complete entities.

```yaml
repositories:
  - id: crm-agent-portal
    name: CRM Agent Portal
    shortName: Agent Portal
    repositoryType: frontend
    lifecycleStatus: active
    summary: Fictional CRM frontend repository for contact preference handling.
    purpose: Inspect the user-facing CRM contact journey.
    aliases:
      - CRM agent UI repository
    useFor:
      - Inspect the CRM contact screen after selecting its semantic target.
    git:
      provider: gitlab
      group: CRM
      project: agent-portal
      projectPath: CRM/agent-portal
      defaultBranch: main
      url: https://gitlab.example.com/CRM/agent-portal
    references:
      processes:
        - crm-contact-preference-update
    matchSignals:
      exact:
        projectPaths:
          - CRM/agent-portal
    evidence:
      - sourceRef: CRM/agent-portal/package.json
        evidenceType: reviewed-build-definition
        note: Fictional CRM frontend identity confirmed together with the operator role statement.
    llmToolHints:
      answerWhenUserMentions:
        - CRM contact screen
      disambiguateFrom:
        - CRM Contact Core backend repository
  - id: crm-contact-core
    name: CRM Contact Core repository
    repositoryType: service
    lifecycleStatus: active
    summary: Implements fictional CRM contact preference validation and persistence.
    git:
      provider: gitlab
      group: CRM
      project: contact-core
      projectPath: CRM/contact-core
      defaultBranch: main
  - id: crm-contact-contracts
    name: CRM Contact Contracts
    repositoryType: shared-library
    lifecycleStatus: active
    summary: Shared fictional CRM contact contract library.
    git:
      provider: gitlab
      group: CRM
      project: contact-contracts
      projectPath: CRM/contact-contracts
      defaultBranch: main
```

## Update rules

- Treat `git.projectPath` as the GitLab link; keep the rest business-readable.
- Use only the documented `git` fields; `git.inferred` is not part of the
  repository contract.
- Use `repositoryType: frontend` only for a reviewed primary repository of a
  system registered as `internal-service/frontend`. Framework files are
  evidence to inspect, not an automatic classification rule.
- Use `references` to connect a repository with systems, processes, bounded
  contexts, integrations and handoff rules as recognition signals.
  Add a distinct semantic fact only; do not mirror an existing reference or
  copy the system-to-repository relation already owned by a code-search scope.
- Do not add team references to imply repository ownership.
- Add aliases only when they help resolve a real user or tool signal.
- Use guided `evidence` cards only for durable provenance labels or safe
  relative references. They explain why the entry exists; runtime does not
  fetch those sources automatically.
- Use `llmToolHints.answerWhenUserMentions` for repository-selection phrases
  and `disambiguateFrom` for explicit contrasts. These values guide AI
  exploration but never grant access or define ownership.
- Leave code reading order, module boundaries and bounded-context-to-code
  ownership routing to `code-search-scopes.yml`: directly targeted context
  scopes are preferred for their context, with related scopes as fallback.
  Repository entries have no `notes`; use `summary`, provenance `evidence` or
  a supported gap for a durable clarification.
- When a repository is a direct internal library dependency of a system's
  primary repository, make sure it has a stable repository entry so it can be
  included in that system's single code-search scope.
- If a repository is unclear, keep the entry small and add a validation finding
  or open question outside this prompt.
