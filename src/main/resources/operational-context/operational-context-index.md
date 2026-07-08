# Operational Context Index

```yaml
schemaVersion: 1
kind: operational-context.index
```

## Catalog Purpose

This directory contains a reusable, evidence-backed operational context catalog. Incident analysis is the first consumer, but the catalog also supports deterministic mapping, code-search scope selection, function explanation, impact analysis, DB/code grounding, integration dependency analysis, process and bounded-context understanding, local vocabulary disambiguation, QA, onboarding and future AI analysis features.

## Operational Graph Model

The catalog describes an operational graph, not a single ownership table. The graph connects logical systems, repositories, code-search scopes, generated clients, shared libraries, integrations, processes, bounded contexts, glossary terms, teams, external parties, responsibility relations, deterministic match signals, routing overlays and durable gaps.

A single repository, process, integration or bounded context may involve several teams with different roles. Shared libraries and generated clients may be part of code-search scope without becoming runtime owners.

## Catalog Files

- `systems.yml` owns logical systems, runtime recognition signals, dependencies and system-level gaps. It does not define repository search scope.
- `repo-map.yml` owns repository identities, purpose, relations and limitations. It does not define module layouts or file-level search boundaries.
- `code-search-scopes.yml` owns explicit `codeSearchScopes` used to search service code together with shared libraries, generated clients and config sources. Each scope targets one semantic entity, such as a bounded context, system, process or integration, and defines per-repository `searchMode` and optional `pathPrefixes`.
- `processes.yml` owns business, operational, technical, scheduled, event-driven and data processes, including operationally meaningful steps.
- `integrations.yml` owns operational contracts between systems, mediators, brokers, external targets, data stores and channels.
- `bounded-contexts.yml` owns semantic/domain boundaries, local language, context relations and DB/code grounding hints.
- `teams.yml` owns internal teams, external parties and role-based responsibility relations.
- `glossary.md` owns local vocabulary, aliases, acronyms, error markers and term-level disambiguation.
- `handoff-rules.md` is a derived coordination overlay for incident handoff and routing thresholds.

## Deterministic Signals

Catalog entries should keep stable, queryable signals outside prose: system aliases, service names, deployment names, container names, GitLab project paths, process names, integration names, glossary terms, log markers, exception classes, error codes, metrics and spans.

Code details such as package prefixes, classes, endpoint paths, queues, topics, DB tables, source layouts and module directories are discovered by GitLab, DB, runtime or log tools. The catalog may point to a semantic entity and its code-search scope, but should not become a technical inventory of implementation details.

Exact or strong signals are required for high-confidence deterministic mapping. Weak or generic words such as `backend`, `service`, `timeout`, `failure`, `database`, `integration` or `queue` are not enough to assert a system, responsibility or route without stronger evidence.

## Query And Consumption Model

Runtime features and LLM agents should query an operational-context adapter for focused graph slices instead of loading the whole catalog by default. A useful query result includes matched candidates, matched signals, summaries, code-search scopes, related systems, repositories, processes, integrations, bounded contexts, teams, terms, responsibility or routing views, source coverage, limitations and durable gaps.

## Responsibilities And Routing

Responsibilities are role-based relations, not forced owners. A team may be a runtime operator, repo maintainer, module steward, domain steward, integration-side steward, producer, consumer, platform support, data owner, first responder, worker, contributor or external owner. Routing and handoff are downstream views over those facts and must not overwrite catalog ownership, topology, integration, process or vocabulary facts.

## Gaps And Build Memory

Use `gaps` for durable catalog-level unresolved issues that affect deterministic mapping, code-search scope, system/runtime recognition, process/context interpretation, integration analysis, responsibility, handoff behavior, DB/code grounding or LLM answers.

Temporary scan-order uncertainty belongs in `BUILD MEMORY`, discovery reports or sidecar outputs. Do not use final catalog gaps as scratchpad memory between repository scans.

## Update Quality Gates

- Preserve existing evidence-backed facts unless explicit contradictory evidence is available.
- Merge confirmed positive facts; do not regenerate global truth from one source.
- Treat absence in one repository or document as `not observed`, not global absence.
- Keep code-search scopes explicit when runtime code spans service repositories, shared libraries, generated clients or config repositories.
- Keep handoff rules concise, evidence-backed and subordinate to core catalog facts.
- Do not store secrets, credentials, tokens, personal contact data, full production payloads or sensitive business records.
