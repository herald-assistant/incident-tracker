---
name: incident-analysis-core
description: Core evidence-first rules for producing an operationally useful incident diagnosis, affected function explanation, and handoff-ready result.
---

# Incident Analysis Core Skill

Use this skill for every incident analysis.

## Product goal

The result is for an operator, tester, analyst, or junior/mid developer who may not know the affected system area.

Do not only identify the local exception.
Explain the affected function, the broader technical or business flow, what evidence confirms the diagnosis, what is still uncertain, and what the next concrete action should be.

A good answer should help the reader:

- understand what likely failed,
- understand where it failed in the wider flow,
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
7. Treat `AFFECTED_FUNCTION_GITLAB_RECOMMENDED` as a concrete hypothesis about the affected flow. If GitLab tools are enabled, make a focused GitLab attempt before the final answer to improve `affectedFunction`.
8. If existing artifacts are already sufficient but GitLab tools are enabled, still use a small focused GitLab search/read for `affectedFunction`; if GitLab tools are disabled, use those artifacts directly.
9. If the likely technical error is clear but the broader flow is not understandable for a beginner analyst, prefer focused GitLab reads over a shallow final answer.
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

## Broader flow explanation

The field `affectedFunction` is not a place for only the exception name.

Explain the function in a way that a new analyst can understand:

- what capability or operation is affected,
- what starts the flow,
- which service/class/repository/integration appears to participate,
- where the failure interrupts the flow,
- whether the failure blocks read, write, validation, async processing, integration call, or handoff,
- which upstream or downstream collaborator is relevant if supported by evidence.

When GitLab tools are enabled, use them to ground `affectedFunction` before the final answer.
Keep the exploration focused: one flow-context/class-reference search plus outline/chunks is enough unless the result clearly points to a direct collaborator.

Write `affectedFunction` in non-code but technical/functional language.
It may mention classes, methods or repositories as evidence, but the main text should describe the capability, business/technical operation, input/object, participating components and interruption point.

When the technical failure is already clear but the broader flow is not clear, use focused GitLab reads to make the final answer useful to a newcomer, not only to a developer who already knows the system.

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

## Recommended action quality

The recommendation must be concrete and actionable.

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
When it contains `codeSearchProjects` or several repository projects for the matched system, treat them as the component's code scope, including libraries and shared modules that can be relevant to the incident.

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
