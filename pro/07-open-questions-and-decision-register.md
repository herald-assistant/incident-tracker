# Open Questions And Decision Register

Ten dokument rozdziela decyzje juz podjete od tematow, ktore nadal wymagaja
osobnego PR-a albo produktu.

## Decision Register

### D1 - Public analysis request remains `correlationId` only

Status: accepted.

`environment`, `gitLabBranch` and DB scope are derived by backend evidence and
configuration. `gitLabGroup` comes from application config.

### D2 - AI boundary stays generic

Status: accepted.

AI providers consume `AnalysisAiAnalysisRequest` and `AnalysisEvidenceSection`.
Adapter DTOs stay behind evidence providers, adapters or view helpers.

### D3 - Copilot artifacts are embedded in prompt

Status: accepted.

Current runtime uses logical artifacts rendered inline in the prompt.
SDK attachments are not used for evidence delivery.

Changing this requires explicit runtime change, tests and rollback plan.

### D4 - Prepared analysis flow replaces double prompt preparation

Status: accepted.

`AnalysisAiPreparedAnalysis` is the generic prepared contract. The job prompt
is `prepared.prompt()` and execution reuses the same prepared request.

### D5 - Response contract is JSON-only

Status: accepted.

Copilot must return valid JSON. Parser supports full JSON and fenced JSON
tolerance. Legacy labeled parser has been removed.

### D6 - Quality gate starts as report-only

Status: accepted.

Quality findings are logged and metered. Runtime response is not changed in
`REPORT_ONLY`.

### D7 - Tool policy is coverage-aware

Status: accepted.

Tool availability is based on `CopilotEvidenceCoverageReport` and evidence
gaps, not on simple "evidence section exists" checks.

### D8 - DB tools require grounded need and resolved environment

Status: accepted.

DB tools are available only when data diagnostics are justified and the
environment is resolved. Discovery-only mode is allowed for weaker signals.
Raw SQL stays disabled by default.

### D9 - Tool budget is backend-enforced

Status: accepted.

Budget state is per Copilot session. Soft mode logs warnings; hard mode returns
controlled `denied_by_tool_budget` result.

### D10 - Incident digest and item IDs are artifact-only

Status: accepted.

`01-incident-digest.md` is generated after the manifest. Stable `itemId`
values are added during artifact rendering and do not change the public
`AnalysisEvidenceItem` contract.

### D11 - Tool descriptions can be Copilot-specific

Status: accepted.

`CopilotToolDescriptionDecorator` may append model guidance to Spring tool
descriptions without changing tool implementation.

### D12 - Tool evidence capture is part of audit

Status: accepted.

GitLab and DB tool results can be normalized into `toolEvidenceSections` for
job/UI audit.

## Open Questions

### Q1 - How should telemetry be consumed?

Backend logs structured metrics, but the product still needs a dashboard,
offline report or dev endpoint to compare runs.

Decision needed:

- log-only for now,
- local report script,
- persisted metrics,
- UI dashboard.

### Q2 - What is the golden evaluation format?

Before model/prompt experiments, define fixtures:

- input evidence pack,
- expected JSON shape,
- expected quality findings,
- expected evidence references,
- acceptable tool budget envelope.

### Q3 - When to enable `SOFT_REPAIR`?

Quality gate has modes for future behavior, but only report-only should be
used until tests prove the repair pass improves output without hiding evidence
limits.

### Q4 - Should SDK attachments ever replace embedded prompt artifacts?

Current decision is embedded prompt. Attachments may reduce prompt size later,
but they introduce delivery-mode risk and require dedicated tests.

### Q5 - How strict should Copilot permissions be?

Main analysis flow blocks local workspace tools by session hooks, but
`permission-mode` default remains operationally important. Decide whether to
change default after regression tests.

### Q6 - How should UI present tool evidence and quality findings?

Backend exposes `toolEvidenceSections`, quality findings in telemetry and JSON
visibility limits/evidence references. UI still needs a concise operator view.

### Q7 - How should DB raw SQL be governed?

Current policy and budget default disable raw SQL. If enabled later, it needs:

- explicit property,
- approval/audit story,
- result masking/projection rules,
- tests for denial and allowed use.

### Q8 - Which budget thresholds are correct?

Defaults are a starting point:

- total calls 16,
- GitLab calls 8,
- GitLab search 3,
- GitLab full file 1,
- GitLab chunks 6,
- DB calls 8,
- raw SQL 0.

Tune using telemetry from real incidents.

### Q9 - Should quality findings affect public response?

Today no. Future options:

- keep report-only,
- expose findings in job API,
- soft repair,
- strict fallback for severe failures.

Do not silently change user-facing diagnosis without a product decision.

## Resolved Questions

Telemetry exists and records preparation, execution, tools, parser, quality
and budget metrics.

Response parsing is JSON-only; legacy labels are no longer supported.

GitLab `gitlab_find_class_references` exists as a real MCP tool.

DB tool results are captured into `database/tool-results` evidence sections.

Prompt is prepared once and reused for execution.
