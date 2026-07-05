# handoff-rules.md update prompt

## Purpose

Update `handoff-rules.md` as the situation and evidence playbook for analysts
and AI-assisted analysis. A handoff rule should explain when a handoff-style
analysis is needed, when it is not, what evidence should be collected, and what
the first practical action is.

Keep rules short and operational. Do not store team routing here.

## Ownership rule

Handoff rules do not define the receiving team. The recipient is resolved from
linked bounded contexts and systems:

- bounded-context ownership has priority,
- system ownership is the fallback,
- boundary problems should surface owners of both sides,
- missing cataloged teams may be shown as inferred labels such as "owner of
  system Salesforce" or "owner of domain customer".

Keep rules in the markdown shape below. If the receiver is unclear, improve the
linked systems/bounded contexts or record an open question instead of adding a
team route to the rule.

## Markdown shape

```markdown
## Customer request boundary

### Customer request boundary

**Rule id:** customer-request-boundary

**Title:** Customer request boundary

**Applies when:**
- The user-visible problem concerns request intake or portal request status.
- The affected journey may have stopped before or at the handoff to case handling.

**Does not apply when:**
- The issue is fully inside one known bounded context and no boundary decision is needed.

**Required evidence:**
- Affected customer journey or business request id when available.
- User-visible symptom.
- Step where the process stopped, if known.
- Source and target system or bounded context when this is a boundary problem.

**Expected first actions:**
- Confirm whether the problem is before, at, or after the handoff boundary.
- Use resolved ownership of the linked bounded contexts/systems to identify the owner or partner owners.

**Operational context links:**
- system:customer-portal
- system:case-management
- process:customer-request-handling
- bounded-context:customer-requests
- bounded-context:case-lifecycle
- integration:portal-to-case-management

**Notes:**
- If ownership is unclear, record the missing system or bounded-context owner as an open question.
```

## Update rules

- One rule per `###` heading.
- Include `Rule id`, `Title`, `Applies when`, `Required evidence` and
  `Expected first actions`.
- Keep `Does not apply when` explicit to avoid noisy routing.
- Use `Operational context links` with `type:id` references only.
- Do not link teams as a handoff target; teams are selected by ownership
  resolution from systems and bounded contexts.
- Prefer concrete evidence needs over generic "investigate" instructions.

## Quality check

- The rule helps decide whether a handoff or boundary analysis is needed.
- The evidence list is realistic for an analyst or operator.
- The rule names visibility limits when ownership cannot be resolved from
  linked systems or bounded contexts.
