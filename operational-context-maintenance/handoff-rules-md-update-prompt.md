# handoff-rules.md update prompt

## Purpose

Update `handoff-rules.md` as the routing playbook for analysts and AI-assisted
analysis. A handoff rule should explain when to route to a team, when not to,
which partner teams may be needed, and what evidence should be collected before
the handoff.

Keep rules short and operational. Do not store deep detail here.

## Markdown shape

```markdown
## Customer request routing

### Route customer request issues

**Rule id:** route-customer-request-issues

**Title:** Route customer request issues

**Route to:** customer-experience-team

**Applies when:**
- The user-visible problem concerns request intake or portal request status.
- The affected journey stops before the case team clearly owns the work item.

**Does not apply when:**
- The issue is fully inside case handling after accepted handoff.

**Required evidence:**
- Affected customer journey or business request id when available.
- User-visible symptom.
- Step where the process stopped, if known.

**Expected first actions:**
- Confirm whether the problem is before or after the handoff boundary.
- Check whether the case management team is a partner owner for the next step.

**Partner teams:**
- case-management-team

**Operational context links:**
- system:customer-portal
- process:customer-request-handling
- bounded-context:customer-requests
- integration:portal-to-case-management
- team:customer-experience-team

**Notes:**
- If ownership is unclear, start with the route team and record the ambiguity.
```

## Update rules

- One rule per `###` heading.
- Include `Rule id`, `Title`, `Route to`, `Applies when`, `Required evidence`
  and `Expected first actions`.
- Keep `Does not apply when` explicit to avoid noisy routing.
- Use `Partner teams` when another owner may need to join but is not the first
  responder.
- Use `Operational context links` with `type:id` references only.
- Prefer concrete evidence needs over generic "investigate" instructions.

## Quality check

- The rule helps choose a team without reading code first.
- The evidence list is realistic for an analyst or operator.
- The rule names visibility limits when ownership is ambiguous.
