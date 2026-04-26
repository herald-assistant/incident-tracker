# Functional And Technical Optimization Backlog

Ten backlog pokazuje, co po optymalizacjach Copilota jest juz zrobione, a co
pozostaje do kolejnych PR-ow.

## Done

Telemetry:

- metrics registry per `copilotSessionId`,
- preparation metrics,
- execution durations,
- tool counts by group,
- returned characters,
- parser/fallback fields,
- budget and quality signals.

Response contract:

- JSON-only prompt contract,
- DTO/parser pod `analysis.ai.copilot.response`,
- fenced JSON tolerance,
- partial fallback preserving parsed fields,
- legacy labels removed.

Quality:

- `CopilotResponseQualityGate`,
- quality findings/severity/report,
- report-only default,
- telemetry/log integration.

Coverage and policy:

- `CopilotEvidenceCoverageEvaluator`,
- Elastic/GitLab/runtime/operational/data diagnostic coverage,
- evidence gaps in manifest,
- coverage-aware `CopilotToolAccessPolicy`,
- DB tools gated by environment and data need.

Artifacts:

- `00-incident-manifest.json`,
- `01-incident-digest.md`,
- stable artifact-only item IDs,
- evidence references in JSON contract,
- manifest read order.

Tool governance:

- backend tool budget,
- soft/hard modes,
- raw SQL separate limit,
- tool description decorators,
- extended DB/GitLab capture.

Flow:

- `AnalysisAiPreparedAnalysis`,
- `CopilotSdkPreparedRequest` implements generic prepared contract,
- orchestrator builds prompt once and reuses prepared request.

Docs:

- architecture, onboarding and `/pro` context updated for current runtime.

## P0 - Evaluation and regressions

Add golden evaluation fixtures for Copilot output:

- input evidence pack,
- expected required JSON fields,
- expected affected function depth,
- accepted/rejected recommended actions,
- expected evidence reference behavior,
- expected tool budget envelope.

This is the biggest missing safety net before more prompt/model changes.

## P1 - Observability productization

Turn telemetry logs into a usable report:

- aggregate by analysis run,
- show latency and cost proxies,
- show fallback/quality/budget rates,
- compare before/after policy or prompt changes.

Can start as offline script or dev endpoint before UI.

## P1 - UI audit view

Expose in job UI:

- `toolEvidenceSections`,
- budget warnings/denials,
- quality findings,
- evidence references,
- visibility limits,
- coverage report.

The backend already produces most of this material; UX still needs projection.

## P1 - Budget tuning

Tune defaults using real sessions:

- GitLab search limit,
- GitLab chunk limit,
- returned character limits,
- DB discovery vs typed diagnostics,
- raw SQL policy.

Keep raw SQL default at zero until there is a separate approval story.

## P2 - Soft repair

Add optional `SOFT_REPAIR` quality mode:

- run a short second model pass,
- include failed quality findings,
- require corrected JSON only,
- no additional tools in repair stage unless explicitly designed.

Do not mix this with parser fallback.

## P2 - Permission hardening

Review whether `analysis.ai.copilot.permission-mode=approve-all` should remain
the app default. Session hooks already block local tools in analysis flow, but
the property name is still operationally sensitive.

## P2 - Deterministic evidence improvements

Improve deterministic GitLab and operational context before AI:

- better candidate ranking,
- richer upstream/downstream hints,
- faster file outlines,
- smaller code windows with stable line references.

This reduces AI tool exploration.

## P2 - Data diagnostics governance

Improve DB governance:

- more typed checks before samples,
- stronger masking/projection rules,
- better DB evidence UI,
- explicit raw SQL approval if ever enabled.

## P3 - Model routing and multi-stage flows

After golden eval and telemetry:

- compare model/reasoning effort,
- consider planner/diagnoser split,
- consider no-tools first pass followed by targeted tool pass,
- consider SDK attachments only with rollback plan.

## P3 - Pattern memory

Longer-term:

- store incident patterns,
- match recurring failures,
- suggest known next steps,
- keep model response grounded in current evidence, not just memory.
