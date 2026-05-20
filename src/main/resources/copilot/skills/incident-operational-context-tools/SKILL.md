---
name: incident-operational-context-tools
description: Incident-analysis playbook for using neutral operational context catalog tools to ground systems, processes, bounded contexts, ownership, code scope and handoff.
---

# Incident Operational Context Tools

Use this skill when operational context tools are available in an incident analysis session.

Operational context is a catalog of systems, repositories, code-search scopes, processes, integrations, bounded contexts, teams, glossary terms and handoff rules.

It is useful for grounding names, relationships, ownership, code scope, DB targeting hints and handoff guidance.
It is not standalone proof of the incident root cause.

## When to use

Use operational context tools when:

- deterministic operational-context evidence is missing, partial or too narrow,
- the affected process, bounded context, team, repository or integration is unclear,
- a GitLab project or code-search scope is not grounded,
- a DB application/schema target needs help from system or repository context,
- the final answer needs a concrete handoff route,
- the user asks a follow-up about business/operational meaning, ownership, process, glossary terms or related systems.

Do not use these tools just because they are available.

## Tool order

Prefer this order:

1. Use attached incident artifacts first.
2. Use `opctx_get_scope` at most once when you do not know which catalog types are available.
3. Use `opctx_list_entities` for a table-of-contents browse when you do not know the catalog term yet.
4. Use `opctx_search` when you have a concrete signal from logs, code, tool results or the user question.
5. Use `opctx_get_entity` before relying on ownership, handoff, process, bounded-context, relation or code-search details.

## Browsing rules

Use `opctx_list_entities` narrowly:

- browse one type at a time,
- prefer a simple filter when any clue exists,
- do not page through the whole catalog,
- read another page only when the previous page makes it plausible that the relevant entity is nearby,
- use it especially for processes, bounded contexts, integrations and glossary terms when the model may not know the vocabulary.

## Grounding rules

Do not invent or silently upgrade catalog context into incident evidence.

Only name `affectedProcess`, `affectedBoundedContext` or `affectedTeam` when the catalog entity is supported by incident artifacts or tool results.
If the match is weak, write `nieustalone` or state the limitation.

`system` is the canonical catalog entity.
Runtime, deployment, service and container names are recognition signals and properties of a system, not separate canonical runtime components.

## GitLab targeting

Use operational context to narrow GitLab exploration:

- prefer `codeSearchScope` when it matches the semantic target you are analyzing: bounded context, process, system or integration,
- treat `codeSearchScope.target.type/id` as the reason this repository set belongs together,
- use all relevant `projectName` values from the scope for GitLab search/flow tools,
- start with repositories marked `primary-implementation` or priority `1`,
- follow supporting libraries, generated clients, integration adapters, legacy modules or collaborator repositories only when evidence or `traversal.expandWhen` points there,
- use package prefixes, class hints, endpoint hints and queue/topic hints as focused search terms.

Do not conclude that code is unavailable after one repository lookup when operational context lists a wider code-search scope.

## DB targeting

Operational context can help choose an application, deployment, system, repository or DB hint for DB tools.

It does not prove a data issue.
For JPA, repository or data-access symptoms, still ground entity/repository/table hints from deterministic GitLab evidence or enabled GitLab tools before broad DB discovery when possible.

## Handoff

Use handoff hints and handoff rules as routing guidance.

When recommending handoff, include:

- why the route is relevant to this incident,
- what evidence should be passed,
- what the receiving team or party should verify first.

If ownership is ambiguous, say so.

## Tool reason

Every operational context tool call must include the optional `reason` argument.

Write `reason` in Polish as one short, practical sentence for the operator.
Do not include hidden reasoning, long analysis or step-by-step deliberation.

Good examples:

```text
Sprawdzam, czy sygnaly z logow pasuja do znanego procesu lub bounded contextu.
Przegladam katalog integracji, bo evidence nie wskazuje jednoznacznego downstreamu.
Pobieram szczegoly systemu, zeby potwierdzic wlasciciela i zakres szukania kodu.
```

## Anti-patterns

Do not:

- browse the whole catalog for safety,
- use glossary terms as proof of failure,
- name owners, processes or bounded contexts without incident grounding,
- use operational context instead of GitLab to prove code behavior,
- use operational context instead of DB tools to prove data state,
- call `opctx_get_scope` repeatedly,
- treat deployment/runtime names as separate canonical entities.
