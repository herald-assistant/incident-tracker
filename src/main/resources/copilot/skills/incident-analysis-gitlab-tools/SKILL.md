---
name: incident-analysis-gitlab-tools
description: Guide for incident analysis that starts from logs and Dynatrace runtime signals, then uses GitLab tools efficiently to confirm the likely code-level cause of an error.
---

# Incident Analysis With GitLab And Elastic Tools

Use this skill when analyzing an incident from structured evidence such as:

- logs from Elasticsearch
- runtime signals from Dynatrace
- GitLab resolved code references or repository exploration hints

## Goal

Produce a diagnosis that is grounded in evidence and, when needed, refine it by
reading only the smallest useful amount of code from GitLab.

The result should be useful for a technical operator, tester, analyst, or
junior or mid-level developer who may need to react, verify, or hand the case over to
another team.
Prefer a diagnosis that helps someone do the next right thing over a diagnosis
that only sounds plausible.

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

Do not invent extra top-level fields unless the caller explicitly asks for them.
If the caller explicitly asks for an exploratory incident flow, the caller may
also require a `diagram` field with JSON only.

## How To Write The Result

- `detectedProblem`
  Make it specific and technical, not generic. Scope it as narrowly as the
  evidence allows.
- `summary`
  Explain what likely happened in our system, which signals support it most, and
  whether the likely failure domain seems to be inside our system or outside it.
  If visibility is incomplete, say that plainly.
  Prefer one short opening sentence and then a few markdown bullets with the
  strongest signals, likely failure domain, and visibility limits.
- `recommendedAction`
  Prefer a short prioritized markdown list. Each point should say who should act
  next and what should be verified or changed.
  If escalation or handoff is likely needed, name the likely owner such as our
  team, another Tribe, admins, integration owners, or DBA.
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

## Evidence-first workflow

1. Start from the provided evidence sections only.
2. Form an initial hypothesis from logs, Dynatrace runtime signals, and already-provided GitLab candidates.
   Prefer code references resolved directly from stacktraces, class names, or
   file paths found in logs over generic keyword search.
   If Dynatrace problem evidence contains curated fields such as
   `signalCategories` or `correlationHighlights`, treat them as high-value
   runtime clues for correlation.
   In particular, explicitly consider signals about database connectivity,
   availability, messaging and failure increase when they overlap with the
   incident time window or log symptoms.
3. If the logs look incomplete or too truncated, use the Elastic tool first to fetch more precise log entries for the same `correlationId`.
4. If the evidence is already strong, answer without fetching more data.
5. If code context is still needed, use GitLab tools to confirm or disprove the hypothesis.

## Exploratory extension mode

When the caller says this is an exploratory step that extends a conservative
baseline:

1. Treat the conservative result as the starting point, not as ground truth.
2. Use tools more boldly, but still economically, to test whether a broader
   cross-component flow is plausible.
3. Build a simple and readable flow only when it helps explain what likely
   happened.
4. Mark every uncertain node or edge as `HYPOTHESIS`.
5. Mark the component that emitted the visible exception or error log as the
   error source when the evidence supports it.
6. If the caller requires a diagram field, return valid JSON only and do not
   wrap it in markdown fences.

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

### 3. Read iteratively

After each tool result:

- update the hypothesis
- decide whether one more file or chunk is justified
- stop once the diagnosis is sufficiently supported

Do not keep reading files without a specific reason.

## Stop conditions

Stop fetching more repository context when one of these is true:

- the likely cause is supported by multiple signals
- the code fragment clearly confirms the suspected root cause
- further reads would be speculative or repetitive
- you already have enough support to explain a simple flow diagram

## Efficiency rules

- Prefer at most a few high-value files over many low-value files.
- Prefer a targeted chunk over a whole file.
- Reuse already collected evidence instead of re-reading the same file.
- If the evidence remains weak after a few focused reads, say that confidence is limited.

## Grounding rules

- Do not claim a root cause unless it is supported by the provided evidence or fetched code.
- Distinguish clearly between confirmed findings and plausible hypotheses.
- If multiple causes are possible, choose the best-supported one and explain why.

## Dependencies

This skill assumes the following tools may be available:

- `elastic_search_logs_by_correlation_id`
- `gitlab_search_repository_candidates`
- `gitlab_read_repository_file`
- `gitlab_read_repository_file_chunk`

If a required tool is unavailable, continue with the evidence already provided
and state the limitation in the rationale.
