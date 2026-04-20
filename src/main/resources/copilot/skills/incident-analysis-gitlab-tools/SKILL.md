---
name: incident-analysis-gitlab-tools
description: Guide for incident analysis that starts from logs and Dynatrace runtime signals, then uses GitLab tools efficiently to confirm the likely code-level cause of an error.
---

# Incident Analysis With GitLab And Elastic Tools

Use this skill when analyzing an incident from structured evidence such as:

- logs from Elasticsearch
- runtime signals from Dynatrace
- deterministic GitLab resolved code references

## Goal

Produce a diagnosis that is grounded in evidence and, when needed, refine it by
reading enough code from GitLab to understand not only the likely failure point,
but also the surrounding functional flow that gives the incident meaning.

The result should be useful for a technical operator, tester, analyst, or
junior or mid-level developer who may need to react, verify, or hand the case over to
another team.
Prefer a diagnosis that helps someone do the next right thing over a diagnosis
that only sounds plausible.
Assume the reader may be a new analyst who does not yet understand the affected
capability, its collaborators, or the overall request path.

Treat the provided `gitLabGroup` and `gitLabBranch` as fixed repository context
for the current analysis. Infer project names and file paths, but do not
silently switch to a different group or branch unless the caller explicitly
changes them.
Treat the provided `environment` as deployment context only. It is useful for
the diagnosis and user-facing summary, but it is not a GitLab coordinate.

## Required output shape

Return exactly these fields when asked for a diagnosis:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `rationale`
- `affectedFunction`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`

Do not invent extra top-level fields unless the caller explicitly asks for them.

## How To Write The Result

- `detectedProblem`
  Make it specific and technical, not generic. Scope it as narrowly as the
  evidence allows.
- `summary`
  Explain what likely happened in our system, which signals support it most, and
  whether the likely failure domain seems to be inside our system or outside it.
  If visibility is incomplete, say that plainly.
  Prefer one short opening sentence and then a few markdown bullets with the
  strongest signals, where in the broader functional or technical flow the
  failure occurs, likely failure domain, and visibility limits.
- `recommendedAction`
  Prefer a short prioritized markdown list. Each point should say who should act
  next and what should be verified or changed.
  If escalation or handoff is likely needed, name the likely owner such as our
  team, another Tribe, admins, integration owners, or DBA.
  If `operational-context` evidence names a team or handoff rule, keep the
  handoff aligned with that evidence instead of inventing a new owner.
- `rationale`
  Use short markdown bullets. Separate confirmed signals from hypotheses and
  from visibility limits.
  Never join multiple points with pipe separators such as `|`.
  Use real markdown bullets on separate lines.
  Use `**bold**` for the most decision-relevant facts and `` `code spans` `` for
  technical identifiers such as classes, methods, exceptions, CIF values,
  branches, metrics, queues, or DB objects.
  A short `---` separator is allowed only when it materially improves
  readability.
- `affectedFunction`
  Explain the business or technical capability affected by the incident based on
  the broader GitLab exploration.
  Prefer one short opening sentence and then a few markdown bullets describing:
  where this function sits in the wider flow, what enters it, what it calls or
  coordinates, and where the incident interrupts that flow.
  Write this for a new analyst who may not know the area yet.
- `affectedProcess`
  Return a short Polish plain-text label for the most likely affected process.
  Prefer a process matched in `operational-context` evidence. If the process is
  not grounded, write `nieustalone`.
- `affectedBoundedContext`
  Return a short Polish plain-text label for the most likely affected bounded
  context. Prefer a bounded context matched in `operational-context` evidence.
  If the context is not grounded, write `nieustalone`.
- `affectedTeam`
  Return a short Polish plain-text label for the team that should currently own
  the incident or receive the handoff.
  Prefer a team matched in `operational-context` evidence or implied by a
  matched handoff rule. If the owner is not grounded, write `nieustalone`.

## Enterprise Grounding Rules

- Treat Elasticsearch, Dynatrace, and GitLab as evidence from our system only.
- The incident may still be caused by an external integration, infrastructure,
  database state, messaging platform, or another team-owned system.
- If the evidence points outside our system, say so explicitly instead of
  forcing a code-level root cause in our repository.
- Do not recommend vague actions. State exactly what should be checked next and
  why.
- If direct access is missing, call that out and frame the next step as
  verification or escalation, not as a proven conclusion.
- If `operational-context` evidence contains matched processes, bounded
  contexts, teams, or handoff rules, use that evidence to ground ownership.
- Do not name a specific process, bounded context, or team unless it is
  supported by matched operational-context evidence or by very strong
  corroborating runtime/code evidence.

## Evidence-first workflow

1. Start from the attached artifacts only and read the manifest first when one is attached.
2. Form an initial hypothesis from the attached logs, Dynatrace runtime signals, and already-provided GitLab candidates.
   Prefer code references resolved directly from stacktraces, class names, or
   file paths found in logs over generic keyword search.
   If Dynatrace problem evidence contains curated fields such as
   `signalCategories` or `correlationHighlights`, treat them as high-value
   runtime clues for correlation.
   In particular, explicitly consider signals about database connectivity,
   availability, messaging and failure increase when they overlap with the
   incident time window or log symptoms.
3. If the logs look incomplete or too truncated, use the Elastic tool first to fetch more precise log entries for the same `correlationId`.
4. If the evidence is already strong and the surrounding flow is already understandable for the reader, answer without fetching more data.
5. If code context is still needed, use GitLab tools not only to confirm or disprove the hypothesis, but also to understand the surrounding flow of the affected capability.
6. When the local failing method is only one step in a larger business or technical path, widen the exploration enough to explain the entry point, key collaborators, downstream calls, and where the failure interrupts the flow.

## Elastic tool strategy

Use `elastic_search_logs_by_correlation_id` when:

- the evidence contains only a small sample of logs
- the current logs omit an exception body or long message
- you need to confirm whether WARN or ERROR entries appeared later in the same incident

Prefer focused retrieval by the same `correlationId`.
This tool uses the configured Kibana proxy and does not require extra
connection parameters.
Do not switch to a different identifier unless the caller explicitly tells you to.

## GitLab tool strategy

### 1. Narrow the search space first

Prefer `gitlab_search_repository_candidates` when you still need to identify the
most relevant files.

Use inputs inferred from the evidence:

- `projectNames`
- `operationNames`
- `keywords`

Do not search blindly if the evidence already contains a concrete project and
file path.

### 2. Prefer small reads over full files

Prefer `gitlab_read_repository_file_chunk` before `gitlab_read_repository_file`.

Use chunk reads when:

- a stack frame or method name points to a local area of code
- only one method or code block is needed
- you are confirming a narrow hypothesis

Use full file reads only when:

- the file is short
- the chunk result is insufficient
- understanding class-level context is necessary

### 3. Expand from the failing point to the surrounding flow

If the incident appears to involve a broader execution path, read outward from
the failing point in a controlled way:

- first the failing method or stack frame area
- then the containing class or service method
- then a few directly collaborating files, such as orchestrators, facades,
  gateways, validators, mappers, or downstream clients
- then, if still needed, one more upstream or downstream step that clarifies the
  functional path for the reader

The goal is not to map the entire repository. The goal is to explain the local
failure inside the smallest broader flow that makes the incident understandable
to someone new to the area.

### 4. Read iteratively

After each tool result:

- update the hypothesis
- decide whether one more file or chunk is justified by the diagnosis or by the
  need to explain the broader flow
- stop once the diagnosis is sufficiently supported

Do not keep reading files without a specific reason.

## Stop conditions

Stop fetching more repository context when one of these is true:

- the likely cause is supported by multiple signals
- the code and surrounding flow are clear enough for a newcomer to understand
  where the failure happens and what it affects
- the code fragment clearly confirms the suspected root cause and the nearby
  functional context is already understandable
- further reads would be speculative or repetitive

## Efficiency rules

- Prefer a few high-value files that explain the flow over many low-value files.
- Prefer a targeted chunk over a whole file, but allow a somewhat wider read when
  it materially improves the explanation of the flow.
- Reuse already collected evidence instead of re-reading the same file.
- If the evidence remains weak after a few focused reads, say that confidence is limited.

## Grounding rules

- Do not claim a root cause unless it is supported by the provided evidence or fetched code.
- Distinguish clearly between confirmed findings and plausible hypotheses.
- If multiple causes are possible, choose the best-supported one and explain why.
- If you describe the broader flow, ground that explanation in specific code or
  evidence rather than generic architectural assumptions.

## Dependencies

This skill assumes the following tools may be available:

- `elastic_search_logs_by_correlation_id`
- `gitlab_search_repository_candidates`
- `gitlab_read_repository_file`
- `gitlab_read_repository_file_chunk`

If a required tool is unavailable, continue with the evidence already provided
and state the limitation in the rationale.
