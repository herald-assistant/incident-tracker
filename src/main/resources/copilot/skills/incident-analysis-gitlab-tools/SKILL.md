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

## When not to use GitLab tools

Do not call GitLab tools when:

- attached deterministic code evidence already contains the relevant stack frame, surrounding method, direct collaborator and enough flow context for a newcomer;
- the logs, runtime signals and attached code evidence already explain both:
    - the likely issue,
    - and the affected function / broader flow;
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

## Tool order

1. Prefer attached deterministic GitLab evidence.
2. Use `gitlab_find_flow_context` when the local failure is known but the broader flow or collaborators are unclear.
3. Use `gitlab_search_repository_candidates` when project/file is unclear or you need broad cross-repository candidates.
4. Use `gitlab_read_repository_file_outline` before full file reads when you need to understand a file role cheaply.
5. Use `gitlab_read_repository_file_chunk` or `gitlab_read_repository_file_chunks` before full file reads.
6. Use `gitlab_read_repository_file` only when:
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
- business identifiers from logs.

Search broadly enough to find the relevant project and direct collaborators, but do not read every candidate.

Prefer ranked candidates and role hints over blind full-file reads.

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
- entity/table/field names,
- ID or business key used,
- tenant/context/status filters,
- soft-delete or validity filters,
- integration endpoint/client,
- async message/event type,
- error handling path,
- direct collaborators,
- ownership hints if grounded.

## Repository predicate analysis

When the incident involves "not found", empty result, entity lookup, data filtering, or repository failure, identify:

- direct key predicate,
- business key predicate,
- tenant/context predicate,
- status/state predicate,
- soft-delete predicate,
- validity-date predicate,
- type/discriminator predicate,
- joins or relation loading.

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