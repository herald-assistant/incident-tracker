# glossary.md update prompt

## Purpose

Update `glossary.md` as the local language catalog. A glossary term should help
an analyst, tester, developer, or AI model understand what a business term means
in this system and what it must not be confused with.

Keep terms concise. Link to systems, repositories, processes, bounded contexts,
integrations, teams and handoff rules when those links help navigation.

Glossary terms do not define ownership. Owner and handoff are resolved from
linked bounded contexts and systems.

Keep glossary entries in the markdown shape below. If a fact does not fit this
shape, link the relevant catalog entity or record an open question instead of
adding a new ownership or routing field.

## Markdown shape

```markdown
## Request handling

### Customer request

**Term:** Customer request

**Category:** business concept

**Definition:** User-facing request started by a customer before it becomes visible in case handling.

**Aliases:**
- request intake
- portal request

**Local meaning and boundaries:**
- Belongs to the customer request bounded context before handoff.
- Used when talking about the portal-visible journey and status.

**Not to confuse with:**
- Case: the target-side work item after accepted handoff.

**Match signals:**
- customer request
- request intake
- portal request status

**Canonical references:**
- bounded-context:customer-requests
- process:customer-request-handling
- system:customer-portal

**Notes:**
- Use this term when translating findings into business-facing language.
```

## Update rules

- One term per `###` heading.
- `Term`, `Category` and `Definition` should be present for every entry.
- `Local meaning and boundaries` should explain how the term is used in this
  product landscape.
- `Not to confuse with` should name nearby terms that often cause ambiguity.
- `Match signals` should be short business words or stable labels.
- `Canonical references` use `type:id`, for example `system:customer-portal`.
- Do not use team references to imply ownership.
- Use `Notes` for analyst guidance that does not belong to a typed field.

## Quality check

- The term helps explain a result to a business user.
- The term helps disambiguate a system, process, context, owner or handoff.
- The entry does not duplicate facts owned by another catalog file.
