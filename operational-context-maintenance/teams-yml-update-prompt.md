# teams.yml update prompt

You maintain `src/main/resources/operational-context/teams.yml`.

Treat the attached operational-context YAML files as the catalog model.
Use only the documented structure below plus useful fields already present in
this catalog.

## Target file contract

Keep this top-level structure:

```yaml
schemaVersion: 1
catalogKind: operational-context-team-map
teams: []
externalParties: []
gaps: []
```

Each `teams[]` entry describes an internal operational or delivery team. Each
`externalParties[]` entry describes a party outside the internal team catalog
that can own, support, or participate in a system, process, integration, or
handoff.

Preferred team entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable team name
  type: engineering | operations | business | support | platform | unknown
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  aliases: []
  summary: One or two factual sentences.
  responsibilityEvidenceType: confirmed | inferred | unknown | mixed
  matchSignals: []
  responsibilities:
    - targetType: system | repository | process | boundedContext | integration | handoffRule | term
      targetId: referenced-catalog-id
      role: owner | primary-support | contributor | consumer | approver | escalation | observer
      scope: []
      side: source | target | shared | none
      status: confirmed | inferred | unknown
      confidence: high | medium | low | unknown
      evidence: []
  routingHints: []
  handoffHints: []
  collaboration: []
  analysisHints: []
  gaps: []
```

Preferred external party entry fields:

```yaml
- id: stable-kebab-case-id
  name: Human readable party name
  type: vendor | bank-unit | regulator | customer | partner | unknown
  lifecycleStatus: active | inactive | unknown | planned | deprecated
  aliases: []
  summary: One or two factual sentences.
  matchSignals: []
  responsibilities: []
  routingHints: []
  handoffHints: []
  analysisHints: []
  gaps: []
```

Preserve additional existing fields only when they follow the current catalog
style and carry useful evidence. Remove duplicate, empty, speculative, or
shape-conflicting fields.

## Update rules

- Prefer editing an existing team or external party when evidence matches a
  known name, alias, routing hint, support cue, ownership record, or handoff.
- Add a new team only when evidence identifies a durable group that operators
  can route to again.
- Add an external party when the evidence identifies a durable non-team
  participant, vendor, banking unit, partner, customer group, or regulator.
- Keep `id` stable once created. Use kebab-case and readable domain language.
- Express ownership through `responsibilities[]`, using `targetType` and
  `targetId` to point to the catalog entity.
- Use `handoffHints` for practical routing and escalation guidance.
- Put unresolved durable questions in top-level or entry-level `gaps`; each gap
  must include enough evidence context to be actionable.
- Keep references aligned with the attached `systems.yml`, `repo-map.yml`,
  `processes.yml`, `bounded-contexts.yml`, `integrations.yml`,
  `glossary.md`, and `handoff-rules.md`.

## Output rules

Return the complete updated `teams.yml` content only.
Do not include commentary, markdown fences, diffs, or explanations.
