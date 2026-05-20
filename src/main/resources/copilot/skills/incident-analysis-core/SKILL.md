---
name: incident-analysis-core
description: Core evidence-first rules for producing incident functionalAnalysis and technicalAnalysis results.
---

# Incident Analysis Core Skill

Use this skill for every incident analysis.

## Product goal

The result is split by audience:

- `functionalAnalysis` is for a business/system analyst who needs system, process, bounded-context and handoff context.
- `technicalAnalysis` is for the technical receiver who must fix, verify or route the issue.

Do not only identify the local exception.
Explain where the incident sits in the system and produce a concrete Technical Handoff v1 for the receiver.

A good answer should help the reader:

- understand what likely failed,
- understand where it failed in the wider system/process flow,
- know whether the issue is likely data, code, integration, infrastructure, configuration, or outside current visibility,
- know who should act next,
- know what exact verification or fix should be performed.

## Evidence-first workflow

1. Read `00-incident-manifest.json` first when available or when its content is embedded in the prompt.
2. Use the manifest as the artifact index and session context.
3. Check manifest `toolPolicy.enabledCapabilityGroups` and `toolPolicy.disabledCapabilityGroups` before assuming that GitLab, Elasticsearch or DB tools are available.
4. Treat incident artifacts, or their embedded prompt copies, as the primary source of truth.
5. Form the initial hypothesis from logs, runtime signals, deterministic code evidence and operational context.
6. Use tools only when they can materially confirm, reject or refine a concrete hypothesis.
7. Treat `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED` as a concrete gap for the analyst-facing section. Use attached operational context first; if needed and enabled, make a focused Operational Context lookup.
8. Treat `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` as a concrete gap for Technical Handoff v1. If GitLab tools are enabled, make a focused GitLab attempt before the final answer.
9. If existing artifacts are already sufficient but GitLab tools are enabled and `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` is listed, still use a small focused GitLab search/read for `technicalAnalysis`; if GitLab tools are disabled, use those artifacts directly.
10. If a capability group is disabled because equivalent artifacts are already attached, use those artifacts directly instead of complaining about missing tool access.
11. If visibility is incomplete, explain the limitation and provide the next verification step.

## Session invariants

Treat these values as fixed for the current session:

- `correlationId`,
- `environment`,
- `gitLabBranch`,
- `gitLabGroup`.

Do not invent or silently change:

- environment,
- branch,
- group,
- project,
- deployment,
- table,
- queue,
- process,
- bounded context,
- team,
- downstream system.

If a value is not grounded in evidence, write that it is not established.

## Dynatrace runtime signal semantics

When the `dynatrace/runtime-signals` artifact contains structured status lines, interpret them literally.

- If `collection status: COLLECTED` and a component is marked as `MATCHED, NO_RELEVANT_SIGNALS`, treat it as explicit absence of Dynatrace-confirmed problems or abnormal metrics for that component in the incident window.
- If `collection status: UNAVAILABLE`, `DISABLED`, or `SKIPPED`, treat missing Dynatrace metrics, problems, or component signals as lack of visibility, not as evidence of healthy runtime.
- If `collection status: COLLECTED` but `correlation status: NO_MATCH`, treat Dynatrace as inconclusive for this incident. Do not claim that runtime was healthy only because Dynatrace did not correlate a component.
- Prefer the component-level Dynatrace summary lines over raw technical detail when explaining the final diagnosis. Use raw problem or metric detail only when it materially strengthens the evidence.

## Diagnosis quality standard

Always separate:

- directly confirmed evidence,
- best-supported hypothesis,
- unverified assumptions,
- visibility limits,
- affected functional or technical flow,
- concrete next action.

Do not overclaim root cause.

Use strong language only when multiple signals point to the same conclusion, for example:

- log exception,
- runtime signal,
- code path,
- DB/data evidence,
- operational context.

If the evidence is weaker, write the conclusion as a supported hypothesis.

## Result split

The final JSON has two main Markdown fields.

`functionalAnalysis` follows Functional Analysis v1. It explains:

- where we are in the system,
- what process or bounded context is involved,
- what business/system capability is affected,
- what starts the flow,
- what business object, status, event, decision or integration participates,
- where the failure interrupts the flow,
- what the analyst should pass to the right owner.

`technicalAnalysis` follows Technical Handoff v1. It explains:

- exact technical location,
- evidence,
- root cause or best hypothesis,
- execution flow,
- proposed fix or expected receiver action,
- tests and verification.

Do not mix the two. The functional section should not be noisy with implementation details; the technical section should not be diluted by educational prose.

Good examples:

- "Pobranie aktywnego rekordu klienta po identyfikatorze z requestu."
- "Asynchroniczne przetwarzanie eventu outbox po zmianie statusu zamówienia."
- "Walidacja danych referencyjnych przed zapisem wniosku."
- "Integracja z systemem płatności podczas finalizacji transakcji."

Bad examples:

- "EntityNotFoundException"
- "Repository"
- "Błąd bazy"
- "Problem w kodzie"

## Technical action quality

The technical handoff recommendation must be concrete and actionable.

Avoid:

- "sprawdzić logi",
- "zweryfikować aplikację",
- "przeanalizować bazę",
- "skontaktować się z zespołem".

Prefer:

- who should act,
- what object/key/state should be checked,
- which system or data area should be verified,
- what change or handoff is likely needed.

Examples:

- "Tester/DBA powinien uzupełnić brakujący rekord referencyjny `X` na środowisku `Y` albo poprawić dane wejściowe wskazujące na ten identyfikator."
- "Developer powinien sprawdzić, czy do metody `findBy...` przekazywany jest właściwy `tenantId`, bo DB pokazuje rekord dla innego kontekstu."
- "Właściciel integracji powinien zweryfikować odpowiedź systemu downstream, bo dane wewnętrzne wyglądają spójnie, a błąd pojawia się po wywołaniu zewnętrznym."

## Handoff and ownership

Use `operational-context` evidence when available.
When it contains `codeSearchScopes`, `codeSearchProjects` or several repository projects for the matched semantic target, treat them as one implementation search scope, including libraries and shared modules that can be relevant to the incident.
If repository roles are present, start with the `primary-implementation` or priority `1` repository and then follow supporting-library, generated-client, integration-adapter, legacy or collaborator repositories when the hypothesis needs them.

Do not name a specific process, bounded context, or team unless it is supported by:

- matched operational-context evidence,
- deterministic code/runtime evidence,
- or a clearly identified integration/deployment owner.

If ownership is ambiguous, write `nieustalone`.

If recommending handoff, include:

- why handoff is needed,
- what evidence should be passed,
- what the receiving party should verify.

## External and out-of-visibility causes

The incident may be caused by something outside the directly visible evidence:

- external integration,
- downstream service,
- platform configuration,
- database state,
- messaging layer,
- infrastructure,
- another team-owned component.

If the evidence points outside our system, say that directly.
Do not force a code-level root cause when the code only shows where the error surfaced.

When the local logs or code only show an opaque HTTP failure from another system and Elasticsearch tools are enabled:

- use `elastic_summarize_http_calls_by_path` with a grounded path or stable path prefix from logs, the user request, or prior evidence,
- compare recent samples and status distribution for the same endpoint family,
- then use `elastic_fetch_http_call_logs` for one or a few concrete comparison calls only when the summary reveals useful candidate paths or statuses,
- check whether successful and failing calls differ by path variant, HTTP status, method, caller service, timestamp cluster, message hints, null/empty values, constraint-like wording, or request/response format clues,
- treat the result as supporting evidence, not proof, unless the logs explicitly contain the accepted/rejected value or downstream reason.

Do not invent endpoint paths. If only the failing path is known, search that stable prefix first.
If a comparison path is provided to `elastic_fetch_http_call_logs`, it searches by that path instead of the current incident correlationId; if `path` is omitted, it uses the current hidden correlationId.

## Stop conditions

Stop fetching more evidence when:

- the likely cause is supported by multiple independent signals,
- the affected flow is clear enough for handoff,
- another tool call would only repeat existing evidence,
- the next required verification is outside current visibility,
- tool exploration would become speculative.

If confidence remains limited after focused exploration, state the limitation.

## Anti-patterns

Do not:

- dump raw evidence into the final answer,
- produce a code-only explanation with no operational next step,
- hide weak evidence,
- invent ownership,
- over-explore the repository or database without a concrete diagnostic reason,
- use tools to browse randomly,
- diagnose a data issue without data evidence,
- diagnose an implementation issue before checking data when the symptom is clearly data-dependent,
- return generic advice without a concrete verification target.
