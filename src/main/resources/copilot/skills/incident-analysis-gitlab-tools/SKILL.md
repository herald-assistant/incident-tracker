---
name: incident-analysis-gitlab-tools
description: Use GitLab tools efficiently to understand the failing code path, repository predicates, integrations, and surrounding functional flow.
---

# Incident Analysis With GitLab Tools

Use this skill when logs, stack traces, deterministic GitLab evidence, or runtime signals suggest that code context is needed to understand:

- the failing method,
- the repository predicate,
- the entity or DTO mapping,
- the integration path,
- the validation path,
- the async/event flow,
- or the affected function for handoff.

## Fixed repository context

Treat `gitLabGroup` and `gitLabBranch` from the manifest/prompt as fixed.

Do not switch group or branch.
Do not invent project names.
Infer project names and file paths only from evidence and repository exploration.

## Deployment component repository scope

A deployed component may be implemented by more than one GitLab project.
Operational context can list several repositories for the same system/component, including:

- the main service repository,
- internal libraries,
- shared domain modules,
- generated clients or integration libraries,
- supporting modules that are packaged into or called by the deployed service.

When operational context provides `repoIds`, `codeSearchRepoIds`, `codeSearchProjects`, repository `project`, package roots or class hints for a matched system, treat that whole list as the code scope of the deployment component.

If a grounded class, entity, DTO, mapper, client or repository is not found in the main service repository, do not conclude that the code is unavailable after one repository lookup. Make a focused GitLab attempt across the remaining `codeSearchProjects` or matched repository projects first.

Use this especially when:

- the stacktrace class belongs to a package prefix that looks like a library or shared module,
- the failing method delegates to a shared client, mapper, validator or repository abstraction,
- deterministic code evidence shows the service entry point but not the class that decides the failing predicate,
- DB grounding depends on an entity/repository that may live in a library repository.

Keep the search bounded: search the listed component repositories with the grounded class/package/method hints, read only the best matching outline/chunk, then stop if no useful library code is found.

## Available repository catalog

Use `gitlab_list_available_repositories` when the relevant repository is not grounded by logs, deterministic evidence, or existing code references, but the incident contains loose clues about another repository, component, module, package, endpoint, integration, system, bounded context, or process.

The tool reads the repository catalog from operational context for the fixed session group. It does not search code and it does not change the fixed branch or group.

Use the returned `projectName` as the input for later GitLab search, flow context, outline, chunk, and read tools. Treat `gitLabPath`, `summary`, and repository metadata as disambiguating context.

Match incident clues against:

- repository `name`, `aliases`, `projectName`, and `gitLabPath`,
- `systems`, `runtimeComponents`, `boundedContexts`, `processes`, and `integrations`,
- `packagePrefixes`, `endpointPrefixes`, and `modulePaths`.

Prefer one catalog call at the beginning of a cross-repository investigation. Do not repeat the catalog lookup unless new evidence clearly points to another repository family.

## GitLab tool reason

Every GitLab tool call must include the optional `reason` argument.

Write `reason` in Polish as one short, practical sentence for a junior analyst.
Explain what the tool call is meant to verify or clarify.
Do not include hidden reasoning, long analysis, or step-by-step deliberation.

Good examples:

- `Sprawdzam fragment metody ze stacktrace, zeby potwierdzic predykat repozytorium.`
- `Szukam uzyc klasy encji, zeby ustalic gdzie zaczyna sie przeplyw biznesowy.`
- `Czytam serwis i repozytorium, zeby wyjasnic juniorowi ktory warunek odcina dane.`

## When not to use GitLab tools

The exception is `AFFECTED_FUNCTION_GITLAB_RECOMMENDED`: when that gap is listed and GitLab tools are enabled, make one focused GitLab attempt to improve `affectedFunction`, even if deterministic code evidence already explains the local failure.

Do not call GitLab tools when:

- attached deterministic code evidence already contains the relevant stack frame, surrounding method, direct collaborator and enough flow context for a newcomer, and `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` is not listed;
- the logs, runtime signals and attached code evidence already explain both:
    - the likely issue,
    - and the affected function / broader flow, and `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` is not listed;
- the incident is clearly outside repository visibility;
- another tool type is more direct for the current hypothesis, for example DB tools for a concrete data check.

If the likely technical error is clear but `affectedFunction` would remain shallow, use GitLab tools to read enough surrounding code to explain the flow and handoff.

## Exploration goal

The goal is not to map the whole repository.

The goal is to understand the smallest useful cross-file and, when relevant, cross-repository flow that explains:

- where the incident starts,
- which component receives or creates the failing operation,
- where the failing method sits,
- what data or request enters that method,
- which repository, mapper, validator, integration client, listener, scheduler or outbox handler participates,
- where the incident interrupts the function,
- what a beginner or mid-level analyst should verify next,
- which team or owner may need to receive the handoff when evidence supports it.

## AffectedFunction grounding

If `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` is listed in the manifest and GitLab tools are enabled, make a focused GitLab exploration attempt before the final answer.

Use the attempt to write `affectedFunction` in non-code, technical/functional language.
The answer should explain:

- what capability or operation is affected,
- what starts it,
- what data or business object is being handled,
- which application components participate at a high level,
- where the incident interrupts the flow,
- whether the impact is read, write, validation, async processing, integration or handoff.

Do not turn `affectedFunction` into a code walkthrough.
Mention classes, methods, files or repositories only as supporting evidence.

If the GitLab attempt finds no useful flow context, stop and state that limitation.

## Tool order

1. Prefer attached deterministic GitLab evidence.
2. Use `gitlab_list_available_repositories` when projectName/GitLab path is unclear and evidence contains only loose repository, system, module, package, endpoint, integration, or bounded-context clues.
3. If operational context lists multiple `codeSearchProjects` for the matched system, use those projects as one component scope before treating a class as missing.
4. Use `gitlab_search_repository_candidates` when project/file is unclear or you need broad cross-repository candidates after using available catalog clues.
5. Use `gitlab_find_class_references` when an exception, stacktrace, entity, repository, DTO or mapper class is grounded and you need files that declare, import or directly use that class.
6. When `gitlab_find_class_references` returns no useful result for the main project, retry once across the other operational-context projects for that component.
7. Use `gitlab_find_flow_context` when the local failure is known but the broader flow or collaborators are unclear; pass focused `keywords` grounded in logs, stacktrace, code evidence or current tool results.
8. Use `gitlab_read_repository_file_outline` before full file reads when you need to understand a file role cheaply.
9. Use `gitlab_read_repository_file_chunk` or `gitlab_read_repository_file_chunks` before full file reads.
10. Use `gitlab_read_repository_file` only when:
    - the file is short,
    - the chunk is insufficient,
    - class-level context is necessary,
    - or the broader flow cannot be understood from chunks/outlines.

If a listed tool is not available in the current session, use the available GitLab tools and state limitations only if they affect the diagnosis.

## Search strategy

Use inputs inferred from evidence:

- stacktrace class names,
- exception names,
- repository method names,
- entity names,
- DTO names,
- endpoint or operation names,
- queue/topic/message names,
- downstream client names,
- service/container/project hints,
- operational-context `codeSearchProjects`, repository `project`, package roots and class hints,
- available repository catalog signals such as aliases, systems, bounded contexts, package prefixes, endpoint prefixes and module paths,
- business identifiers from logs.

Search broadly enough to find the relevant project and direct collaborators, but do not read every candidate.

Prefer ranked candidates and role hints over blind full-file reads.

## Exception -> entity/repository -> DB targeting

When the incident suggests a JPA, repository or data issue, do not jump straight into broad DB discovery.

Instead:

1. Ground the class from logs, stacktrace or deterministic evidence:
   - entity,
   - repository,
   - DTO,
   - mapper,
   - validator,
   - service.
2. If the project is unclear, use `gitlab_list_available_repositories` first when loose repository or component clues are available, then use `gitlab_search_repository_candidates` with the selected `projectName` values.
3. If the class is grounded, use `gitlab_find_class_references` with:
   - the fully qualified class name when known,
   - the simple class name,
   - hints such as `@Entity`, `@Table`, `@Query`, `JpaRepository`, `JoinColumn`, `mappedBy`, repository method names, business keys or exception names.
4. Read the entity/repository files with outline or focused chunks.
5. Only then drive DB tools with the code-derived table, column and relation hints.

If `DB_CODE_GROUNDING_NEEDED` is listed in the manifest, treat this as a required attempt before DB table/column discovery whenever GitLab tools are available.

If the attempt does not find an entity or repository, do not keep browsing indefinitely.
Move to DB discovery as fallback and make the limitation visible in the DB tool `reason`.

Use this sequence especially for:

- `EntityNotFoundException`,
- `JpaObjectRetrievalFailureException`,
- repository empty-result behavior,
- missing dictionary/reference data,
- orphan relation symptoms,
- unexpected join/filter behavior.

## Chunk-first reading strategy

Start from the most grounded location:

- stacktrace file and line,
- class name from logs,
- method name from exception,
- repository method name,
- endpoint/controller/service name,
- deterministic candidate from attached evidence.

Read outward in this order:

1. failing method or stack frame area,
2. containing class/service method,
3. direct collaborator:
    - repository,
    - mapper,
    - validator,
    - facade,
    - gateway,
    - downstream client,
    - scheduler,
    - listener,
    - outbox/event handler,
4. one or two direct upstream/downstream steps if they materially improve the explanation for a beginner analyst,
5. related repository/component only when the current evidence indicates a cross-component flow or handoff.

## What to extract from code

When reading code, extract only what helps the diagnosis and final UX:

- method name and responsibility,
- entry point or trigger,
- repository predicate,
- derived Spring Data query intent from method names such as `findBy...AndStatus...`,
- explicit `@Query` predicates when present,
- entity/table/field names,
- JPA annotations such as `@Entity`, `@Table`, `@Column`, `@JoinColumn`, `@JoinTable`, `mappedBy`, `@Embeddable`, `@ElementCollection`,
- ID or business key used,
- tenant/context/status filters,
- soft-delete or validity filters,
- integration endpoint/client,
- async message/event type,
- error handling path,
- classes importing the grounded entity/repository and what role they play,
- direct collaborators,
- ownership hints if grounded.

## Repository predicate analysis

When the incident involves "not found", empty result, entity lookup, data filtering, or repository failure, identify:

- direct key predicate,
- business key predicate,
- query-derivation parts from repository method names,
- tenant/context predicate,
- status/state predicate,
- soft-delete predicate,
- validity-date predicate,
- type/discriminator predicate,
- joins or relation loading,
- the entity and relation annotations that suggest which tables and links DB tools should verify.

This information should guide DB/data diagnostics.

## Broader flow explanation

If the failing method is only a local step, explain the surrounding flow.

For example:

- controller/request handler -> service -> repository,
- listener/scheduler -> service -> outbox table -> downstream call,
- validator -> dictionary/reference lookup -> save,
- facade -> mapper -> downstream client.

Do not describe the whole system.
Describe the smallest broader flow that helps a new analyst understand the incident.

## Stop conditions

Stop reading code when:

- the likely failure point is clear,
- the repository predicate or integration call is understood,
- the affected function can be explained to a newcomer,
- the direct upstream/downstream collaborator is clear enough for handoff,
- further reads would be speculative,
- or the remaining question requires DB/runtime/downstream visibility rather than more code.

Do not stop only because the local exception is clear.
Stop when both the technical failure and the affected flow are clear enough.

## Context budget

Tool call count is less important than context quality.

It is acceptable to perform broader GitLab exploration when it materially improves `affectedFunction`, handoff, or next action.

Prefer:

- more ranked candidate searches,
- file outlines,
- focused chunks,
- small batches of related chunks,

over:

- many large full-file reads,
- repeated reads of the same file,
- reading unrelated candidates,
- dumping code into the final answer.

## Grounding

When describing code behavior, mention the supporting class, method, file, or tool result.

If code context remains incomplete, state that limitation instead of guessing.
