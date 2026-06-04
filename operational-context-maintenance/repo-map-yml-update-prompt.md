# repo-map.yml update prompt

You maintain `src/main/resources/operational-context/repo-map.yml`.

Return only the complete updated `repo-map.yml` content.

## Purpose

`repo-map.yml` is the repository inventory. It answers: "what Git repositories,
modules and source layouts exist, and what catalog entities do they generally
relate to?"

Semantic multi-repository implementation search scopes are not stored here.
Maintain them in `code-search-scopes.yml`.

## Top-level contract

```yaml
schemaVersion: 1
catalogKind: operational-context-repository-map
repositories: []
gaps: []
```

## repositories[]

Use `repositories[]` for stable facts about one Git repository:

- Git coordinates and default branch,
- repository type, lifecycle and short summary,
- catalog references owned by the repository model,
- source layout, build files, modules and important paths,
- repository/package/class/endpoint/queue hints that are true for the repository,
- module-level hints when a monorepo or modular monolith needs narrowing.

Do not add `codeSearchScopes`, scope `target`, traversal policy or repository
read-order guidance here. Do not duplicate process flow, integration
participants, bounded-context definitions or team ownership when another catalog
file owns that fact.

## Validation

- Every repository `id` must be stable kebab-case.
- Every `references.*` id must exist in the matching catalog file when known.
- Every `modules[].id` must be stable because `code-search-scopes.yml` may
  reference it through `repositories[].moduleIds`.
- If evidence is uncertain, add a durable `gaps[]` item instead of inventing a
  stable repository/module fact.
