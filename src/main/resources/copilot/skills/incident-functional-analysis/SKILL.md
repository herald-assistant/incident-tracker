---
name: incident-functional-analysis
description: Functional Analysis v1 contract for incident results written for business-system analysts who need system, process, bounded-context and handoff context without implementation-first noise.
---

# Incident Functional Analysis Skill

Use this skill for the `functionalAnalysis` field in every initial incident
analysis result.

The reader is a business/system analyst. They may know business rules,
high-level architecture and team ownership, but they do not know the code.

The goal is to help the analyst understand where the incident sits in the
system and route it correctly. Do not write a developer handoff here.

## Mandatory quality bar

`functionalAnalysis` must be:

- written in Polish,
- understandable without reading Java code,
- grounded in incident evidence and operational context,
- explicit about system, process, bounded context and handoff when known,
- concrete enough to let an analyst route the issue,
- free from root-cause claims based only on catalog context,
- clear about missing visibility.

If a value is missing, keep the section and use one of:

- `Nie ustalono`
- `Nie dotyczy`
- `Brak danych w evidence`
- `Hipoteza, wymaga potwierdzenia`

## Evidence rules

Use incident artifacts as the primary source of truth.

Use Operational Context for:

- canonical system naming,
- process and bounded-context vocabulary,
- glossary and local language,
- integrations and upstream/downstream context,
- handoff and ownership guidance,
- code-search scope only as supporting context.

Operational Context is not proof of root cause. It can explain where the
incident likely sits and how to route it, but logs/code/runtime/DB evidence
must support failure claims.

Use GitLab details only to translate implementation into functional meaning.
Do not turn this field into a class/method walkthrough.

## Output format

Use exactly this top-level structure, in this order.

````markdown
# Functional Analysis v1: <short analyst-facing title>

## 1. Gdzie jestesmy w systemie

| Pole | Wartosc |
|---|---|
| System / aplikacja | <canonical system/application or Nie ustalono> |
| Proces | <affected process or Nie ustalono> |
| Bounded context | <bounded context or Nie ustalono> |
| Integracja / downstream | <relevant integration/system or Nie dotyczy> |
| Wlasciciel / handoff | <team/owner/route or Nie ustalono> |

## 2. Co ten fragment robi funkcjonalnie

<Explain the business/system capability in 4-8 short sentences. Mention the
business object, decision, status, validation, integration, event or data flow
being handled. Prefer process language over code language.>

## 3. Co sie stalo w tym incydencie

- **Objaw:** <what evidence shows in operator-friendly language>
- **Miejsce przerwania flow:** <where the process/system flow is interrupted>
- **Skutek funkcjonalny:** <what cannot proceed, may be delayed, rejected or routed incorrectly>

## 4. Dlaczego to ma znaczenie

<Explain the impact on process continuity, data consistency, customer/user
experience, downstream systems, SLA, manual work or handoff. If impact is not
confirmed, say exactly that.>

## 5. Komu to przekazac i po co

| Pole | Wartosc |
|---|---|
| Sugerowany odbiorca | <team/system owner/role or Nie ustalono> |
| Powod przekazania | <why this receiver is relevant> |
| Pierwsze pytanie / akcja | <one concrete verification/action for the receiver> |

## 6. Co jest potwierdzone, a czego nie wiemy

**Potwierdzone:**
- <evidence-grounded fact>

**Niepotwierdzone / brak widocznosci:**
- <missing evidence or limitation>
````

## Writing rules

- Start from the user-visible/system process, not from the exception.
- Use code identifiers only as anchors, for example: "evidence wskazuje na klase `X`", not as the main explanation.
- Explain local jargon when it comes from glossary or bounded context.
- Do not claim a team, process or context unless incident evidence or tool results support the catalog match.
- If the incident is technical but functionally important, explain the normal business/system flow first, then the interruption.
- If the issue is outside the analyzed system, state what evidence should be passed to the receiving system/team.

## Anti-patterns

Do not:

- write only "blad w klasie X" or "problem w repozytorium Y",
- paste stack traces or code snippets,
- give implementation fix instructions,
- hide missing process/context data,
- use Operational Context as proof that a root cause happened,
- merge this section with Technical Handoff v1.
