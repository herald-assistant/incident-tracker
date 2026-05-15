# Operational Context Read Model Optimization Plan

## Goal

Optimize the operational context read model for agents, tools, REST clients and FE without adding more catalog data. The default read path should help narrow code exploration, repository selection, flow understanding and blast-radius reasoning while preserving provenance, confidence, limitations and suggested next reads. Heavy diagnostic and UI inventory data remains available through expanded reads.

The write model stays in YAML/MD with one source of truth. Redundant relations are allowed in Java read models, not in catalog files. `system` remains the canonical catalog entity and operational context tools remain neutral `opctx_*` capabilities.

## Baseline

Baseline was collected from the real REST API with `analysis.operational-context.enabled=true` against the current `src/main/resources/operational-context` catalog before behavior changes.

| Endpoint | Target | Payload size | Valuable fields | Noise / repetitions | Recommended action |
| --- | --- | ---: | --- | --- | --- |
| `/api/operational-context/summary` | catalog | 240 KB | counts, catalog status, validation totals | full healthCards enumerate all entities, 770 sourceRefs, repeated validation labels | split summary from expanded health inventory |
| `/api/operational-context/validation` | catalog | 172 KB | 304 findings, no errors | repeated handoff warnings dominate | keep expanded/diagnostic; summary gets grouped counts |
| `/api/operational-context/search?q=limit` | query | 43 KB | ranked candidates, actions | 87 results, no sourceRefs/provenance | default top-N with provenance-like refs and next reads |
| `/api/operational-context/entities/system/clp-limit-process` | `system/clp-limit-process` | 13 KB | identity, signals, validation | repeated sourceRefs/entityId, raw preview | default omits rawSourcePreview |
| `/api/operational-context/entities/process/limit-process` | `process/limit-process` | 14 KB | process overview, related entities | repeated item wrappers | keep FE expanded, add compact entity envelope |
| `/api/operational-context/entities/integration/clp-collateral-to-cbz` | `integration/clp-collateral-to-cbz` | 11 KB | endpoints, class/client hints, systems | endpoint signal does not resolve via blast-radius unless in flow steps | add limitation/next read for integration-to-flow gap |
| `/api/operational-context/entities/bounded-context/limit-management` | `bounded-context/limit-management` | 25 KB | terms, runtime signals | 64 sourceRefs for one entity | move full runtime signals to expanded |
| `/api/operational-context/entities/code-search-scope/clp-collateral-full-scope` | `code-search-scope/clp-collateral-full-scope` | 47 KB | repo roles, entry hints | 91 hints, 136 sourceRefs | default top hints plus links to expanded |
| `/api/operational-context/read-model/entities/system/clp-limit-process/relations` | `system/clp-limit-process` | 46 KB | incoming/outgoing graph | 88 relation-ish items, 97 empty fields | relevance sort plus top-N |
| `/api/operational-context/read-model/entities/system/clp-limit-process/code-search` | `system/clp-limit-process` | 128 KB | repos, priorities, code hints | 1,378 hints, 1,114 empty fields | compact default repo/scope bundle |
| `/api/operational-context/read-model/entities/process/limit-process/implementations` | `process/limit-process` | 275 KB | lifecycle roles, repo/scope mapping | 807 repeated ids/labels, large duplicated hints | default implementation cards, expanded full hints |
| `/api/operational-context/read-model/entities/process/limit-process/flow` | `process/limit-process` | 579 KB | ordered 11-step flow | implementation/code hints repeated per step | default step summary plus links to implementation/code-search |
| `/api/operational-context/read-model/entities/process/limit-process/blast-radius` | `process/limit-process` | 583 KB | affected process graph | heavy repeated code refs | compact impact graph, expanded heavy code refs |
| `/api/operational-context/read-model/blast-radius?type=endpoint&id=/clp/process/limit` | endpoint signal | 582 KB | affected flow/steps | 324 repo refs, 11,988 hint slots | compact impact graph, expanded heavy code refs |
| `/api/operational-context/read-model/blast-radius?type=class&id=NewLimitOrderController` | broad class signal | 3.7 MB | reveals broad/generic class risk | 8,511 repeats, 78,570 hints | generic-signal guard plus truncation required |

## Representative Questions

- Which repository, module and shared library should be read first for a failing limit-process endpoint?
- Is a target a legacy, target, parallel, fallback or support implementation?
- Which flow steps and upstream/downstream systems are relevant before reading GitLab?
- What is the likely blast radius for an endpoint, class, table, queue or topic signal?
- Which limitations or provenance gaps should block a confident handoff?
- Which next operational-context read or tool call is the smallest useful next step?

## Representative Targets

| Role | Target |
| --- | --- |
| Big system | `system/clp-limit-process` |
| Process | `process/limit-process` |
| External integration | `integration/clp-collateral-to-cbz` |
| Bounded context | `bounded-context/limit-management` |
| Code-search scope | `code-search-scope/clp-collateral-full-scope` |
| Specific blast-radius signal | `endpoint /clp/process/limit` |
| Broad-risk signal | `class NewLimitOrderController` |

## Metrics

Each iteration samples representative REST payloads and records:

- JSON size in bytes.
- Top-level field count.
- Relation, sourceRef, validation finding, repository, module and hint counts.
- Repeated ids/labels and empty fields.
- Fields without provenance or confidence.
- Fields that narrow analysis versus fields that only repeat labels, summaries or raw payload.
- Default versus expanded ownership for heavy fields.

Data is valuable when it helps choose repositories/modules, distinguish lifecycle roles, understand flow, assess blast radius, narrow GitLab/DB/opctx exploration, avoid local-language confusion, justify handoff, show limitations or propose next reads/tools.

Data is noise when it repeats ids/labels, publishes raw payload, lacks provenance/confidence, does not influence a next step, duplicates another part of the response or adds tokens without improving analysis.

## Target Contract

Read-model endpoints support `profile=index|summary|default|expanded`.

- Missing `profile` remains backward compatible and returns the current FE-safe expanded response.
- `profile=expanded` returns the current full response.
- `profile=default` returns an LLM/tool-oriented envelope with compact `data`, `links`, `availableExpansions`, `suggestedNextReads`, `suggestedTools`, `reasonToExpand`, `omittedBecause`, `truncation`, `relevanceScore`, `confidence`, `limitations`, `provenance`, `sourceRefs` and `validationFindings`.
- `profile=summary` and `profile=index` use smaller budgets for list/search contexts.
- `rawSourcePreview`, verbose explainability groups, full validation lists and repeated sourceRefs stay expanded/diagnostic.

Default budgets:

| Read model | Default budget |
| --- | --- |
| Entity relations | top 8 outgoing, top 8 incoming, top 10 neighbors |
| Code-search | top scopes/repos by priority, top 12 hints per hint class |
| Implementations | top 8 implementation cards, no full duplicated hint graph |
| Flow | ordered steps up to 12, compact refs/hints per step |
| Blast-radius | top impacted flows/steps/nodes, compact implementations, hard truncation for broad class/table/queue/topic matches |

## HATEOAS-Inspired Affordances

We do not implement classical HATEOAS for REST purity. The contract is practical for LLM/agent exploration:

- Default answers "what matters now" and provides links to focused sibling reads.
- Expanded answers "pull more only when the compact answer is insufficient."
- `availableExpansions` names possible heavier reads.
- `suggestedNextReads` tells the agent how to narrow scope before GitLab or DB exploration.
- `suggestedTools` stays neutral and uses reusable capability names.
- `reasonToExpand`, `omittedBecause` and `truncation` make token-saving decisions explicit.

## Field Decisions

| Field/group | Default | Expanded | Decision |
| --- | --- | --- | --- |
| Identity, lifecycle/status, summary | yes | yes | keep default |
| Provenance, confidence, limitations | yes | yes | mandatory for LLM |
| Links, expansions, next reads/tools | yes | yes where useful | mandatory for LLM-oriented envelopes |
| Full sourceRef repetition | limited/deduped | yes | move repeated refs to expanded |
| Raw source preview | no | yes | remove from default |
| Full health-card inventory | no | yes | summary default keeps counts only |
| Full code-search hint graph | compact counts/top hints | yes | expanded only |
| Full flow step implementation hints | compact refs/top hints | yes | expanded only |
| Full blast-radius implementation refs | compact impact graph | yes | expanded only |
| FE-specific table rows | no-profile/expanded | expanded | FE remains compatible until it opts in |
| Diagnostic validation list | grouped/limited | yes | `/validation` remains diagnostic |

## Iteration Status

| Iteration | Hypothesis | Change | Tests | REST sampling | Decision |
| --- | --- | --- | --- | --- | --- |
| 1. Baseline and plan | Current payloads contain useful graph data but default reads are too heavy for agents. | Collected real REST baseline and created this plan. | Existing targeted operational-context tests passed before implementation. | Baseline table above. | Proceed with profile rollout; no write-model changes. |
| 2. Metrics and contract tests | Contract tests should protect no-profile compatibility and default compactness. | Added coverage for `profile=default`, expanded compatibility, links/expansions/truncation, no default raw preview field, and broad class blast-radius truncation. | Passed targeted backend tests. | See iteration 3 REST table. | Keep tests as contract guard for next optimizations. |
| 3. Profile projections | API-side compact projections can reduce default payloads without touching catalog builders. | Implemented backward-compatible `profile` query parsing and compact envelope projection in API layer. | Passed targeted backend tests. | Default payloads reduced; no-profile remains expanded. | Good first rollout; FE remains stable. |
| 4. Relevance and next reads | Sorted top-N plus next reads improve narrowing more than raw graph volume. | Sorted relations/repos/implementations/impact nodes and added links, available expansions, next reads and neutral suggested tools. | Passed targeted backend tests. | Default envelopes expose links and truncation metadata. | Keep; refine relevance scoring with real usage later. |
| 5. Dedup/noise reduction | Default sourceRefs and hints should be limited and deduped. | Limited/deduped sourceRefs; changed flow/blast implementation refs to lightweight refs and hint counts. | Passed targeted backend tests. | Flow default reduced from 579 KB to 107 KB; broad class blast-radius from 3.7 MB to 158 KB. | Better, but flow/blast can still be tightened in later iterations. |
| 6. FE vs LLM split | FE can remain stable while LLM/tools opt into default. | Missing `profile` and `profile=expanded` return existing expanded payloads; default is opt-in. | Passed targeted backend tests. | No-profile sizes match baseline. | FE-safe rollout confirmed. |
| 7. MCP tools default affordances | Agents pay token cost through `opctx_*`; tool results should expose compact default contract and next reads directly. | Added neutral `affordances` to opctx scope/list/search/entity results, compacted entity section maps/lists/sourceRefs, and added focused include links/suggested next reads. | Passed targeted MCP tests and package dependency guard. | Real catalog payload regression added for `codeSearchScope/clp-collateral-full-scope` default tool entity result under 100 KB. | Good tool-facing rollout; next step is to route Copilot evidence capture/feedback display to surface affordances cleanly. |
| 8. Tool affordance evidence capture | Operator-facing tool evidence should show next-read guidance without storing raw/default tool payloads again. | Added generic Copilot platform capture for top-level `affordances`, publishing compact `ai/tool-affordances` evidence with profile, links, suggested next reads/tools and truncation metadata. | Passed targeted affordance capture, feedback listener and package dependency guard tests. | Not a REST payload change; evidence capture test asserts heavy nested tool payload is omitted. | Keep: this surfaces LLM affordances in session evidence while preserving neutral platform boundaries. |
| 9. Relations and blast relevance scoring | Compact default payloads should not only be smaller; top items should explain why they are first and which read narrows scope next. | Added item-level `relevanceScore`, `reasonToRead` and dynamic next reads for relations, blast flows, impacted steps, impacted nodes and impacted implementations. Added stricter broad-signal budgets for class/table/queue/topic blast-radius defaults. | Passed targeted operational-context API/contract tests and package dependency guard. | REST sampling recorded below. | Keep: quality improved; broad class default became smaller after the stricter guard. |
| 10. SourceRef and label dedupe | Default payloads should preserve provenance without repeating verbose sourceRef and nested entity summary fields. | Added top-level `provenance.sourceRefs` summary, compacted default `sourceRefs` to stable `refId/file/path/target/role`, and removed repeated `summary` from nested entity refs. | Passed targeted operational-context API/contract tests and package dependency guard. | REST sampling recorded below. | Keep: preserves provenance while reducing heavier default read models. |
| 11. Maintenance guidance | Optimizations will regress unless future catalog/read-model edits know what belongs in default versus expanded. | Documented profile ownership, LLM affordances, provenance/sourceRef rules, scoring/truncation guidance and a maintenance checklist. | Docs-only change; no runtime tests needed. | Not applicable. | Keep: guidance now captures the constraints used in iterations 1-10. |

## REST Results

| Iteration | Endpoint | Target | Before | After | Quality decision |
| --- | --- | --- | ---: | ---: | --- |
| Baseline | `/summary` | catalog | 240 KB | n/a | Too heavy for default; keep as expanded inventory. |
| Baseline | `/search?q=limit` | query | 43 KB | n/a | Needs top-N default and next reads. |
| Baseline | `/entities/system/clp-limit-process` | big system | 13 KB | n/a | Good entity context, but raw preview and repeated refs are expanded-only. |
| Baseline | `/read-model/entities/process/limit-process/flow` | process | 579 KB | n/a | Default should be compact step summary. |
| Baseline | `/read-model/blast-radius?type=class&id=NewLimitOrderController` | broad class | 3.7 MB | n/a | Needs truncation and broad-signal limitation. |
| Iteration 3/5 | `/summary?profile=default` | catalog | 240 KB | 4.4 KB | Good: counts, status, links and top sourceRefs without inventory noise. |
| Iteration 3/5 | `/search?q=limit&profile=default` | query | 43 KB | 20 KB | Better: top 10 candidates plus next reads; still could add stronger provenance later. |
| Iteration 3/5 | `/entities/system/clp-limit-process?profile=default` | big system | 13 KB | 9.4 KB | Better: raw preview omitted, links/next reads added. |
| Iteration 3/5 | `/read-model/entities/system/clp-limit-process/code-search?profile=default` | big system | 128 KB | 44 KB | Better: scoped repos and hints remain useful, full hint graph expanded-only. |
| Iteration 3/5 | `/read-model/entities/process/limit-process/flow?profile=default` | process | 579 KB | 107 KB | Much better: ordered flow kept, repeated implementation hints reduced. |
| Iteration 3/5 | `/read-model/blast-radius?type=endpoint&id=/clp/process/limit&profile=default` | endpoint signal | 582 KB | 77 KB | Much better: compact impact graph with truncation metadata. |
| Iteration 3/5 | `/read-model/blast-radius?type=class&id=NewLimitOrderController&profile=default` | broad class | 3.7 MB | 158 KB | Good first guard: broad-signal limitation and truncation active; still a candidate for stricter class-signal budget. |
| Iteration 7 | `opctx_get_entity` | `codeSearchScope/clp-collateral-full-scope` | n/a | `< 100 KB` test guard | Tool result now carries default affordances, truncation metadata and focused next reads; no raw payload. |
| Iteration 8 | Copilot tool evidence | `opctx_get_entity` affordances | raw tool result historically available only as invocation output | compact `ai/tool-affordances` section | Good: session evidence keeps profile, next reads, tools, truncation and limitations; heavy nested result payload is not copied. |
| Iteration 9 | `/read-model/entities/system/clp-limit-process/relations?profile=default` | big system relations | 46 KB baseline | 21.5 KB | Better: top relations now include score, reason and next read target; still below baseline. |
| Iteration 9 | `/read-model/entities/process/limit-process/blast-radius?profile=default` | process blast-radius | 583 KB baseline | 81.7 KB | Better: impacted flows explain direct-hit/downstream reasons and remain compact. |
| Iteration 9 | `/read-model/blast-radius?type=endpoint&id=/clp/process/limit&profile=default` | endpoint signal | 582 KB baseline | 80.8 KB | Better: specific endpoint gets scored impact graph and dynamic flow/code-search next reads. |
| Iteration 9 | `/read-model/blast-radius?type=class&id=NewLimitOrderController&profile=default` | broad class signal | 3.7 MB baseline; 158 KB previous default | 57.3 KB | Better: stricter broad-signal guard offsets scoring metadata and reduces token cost. |
| Iteration 10 | `/read-model/entities/system/clp-limit-process/relations?profile=default` | big system relations | 21.5 KB previous default | 21.6 KB | Neutral size impact; provenance is now summarized and sourceRefs are compact stable refs. |
| Iteration 10 | `/read-model/entities/system/clp-limit-process/code-search?profile=default` | big system code-search | 44 KB previous default | 31.7 KB | Better: nested refs no longer repeat summaries; sourceRef provenance remains top-level. |
| Iteration 10 | `/read-model/entities/process/limit-process/flow?profile=default` | process flow | 107 KB previous default | 71.3 KB | Better: ordered flow remains, repeated nested entity summaries removed. |
| Iteration 10 | `/read-model/entities/process/limit-process/blast-radius?profile=default` | process blast-radius | 81.7 KB previous default | 62.1 KB | Better: provenance preserved with lower repeated ref/summary cost. |
| Iteration 10 | `/read-model/blast-radius?type=endpoint&id=/clp/process/limit&profile=default` | endpoint signal | 80.8 KB previous default | 61.1 KB | Better: specific endpoint context remains compact and scored. |
| Iteration 10 | `/read-model/blast-radius?type=class&id=NewLimitOrderController&profile=default` | broad class signal | 57.3 KB previous default | 45.1 KB | Better: broad-signal default got smaller while keeping limitations and next reads. |

No-profile REST sampling after implementation matched expanded baseline sizes, confirming backward compatibility for FE and existing REST consumers.

## Verification Log

| Date | Command / check | Result |
| --- | --- | --- |
| 2026-05-15 | `mvn -q "-Dtest=OperationalContextViewServiceTest,OperationalContextControllerTest" test` | Passed |
| 2026-05-15 | `mvn -q "-Dtest=OperationalContextReadModelContractTest,OperationalContextCodeSearchReadModelBuilderTest,OperationalContextImplementationReadModelBuilderTest,OperationalContextFlowReadModelBuilderTest,OperationalContextBlastRadiusReadModelBuilderTest,OperationalContextAdapterTest,OperationalContextReadModelValidatorTest" test` | Passed |
| 2026-05-15 | `mvn -q -DskipTests compile` after final entity-link cleanup | Passed |
| 2026-05-15 | `mvn -q "-Dtest=OperationalContextReadModelContractTest" test` after final entity-link cleanup | Passed |
| 2026-05-15 | REST sampling with `analysis.operational-context.enabled=true` on port 18080 | Passed; sizes recorded above |
| 2026-05-15 | `mvn -q "-Dtest=OperationalContextMcpToolsTest,OperationalContextMcpToolsContextTest" test` | Passed |
| 2026-05-15 | `mvn -q "-Dtest=PackageDependencyGuardTest" test` | Passed |
| 2026-05-15 | `mvn -q "-Dtest=CopilotToolAffordanceEvidenceCaptureListenerTest,CopilotToolFeedbackInvocationListenerTest" test` | Passed |
| 2026-05-15 | `mvn -q "-Dtest=PackageDependencyGuardTest" test` after affordance evidence capture | Passed |
| 2026-05-15 | `mvn -q "-Dtest=OperationalContextViewServiceTest,OperationalContextControllerTest,OperationalContextReadModelContractTest" test` after relevance scoring | Passed |
| 2026-05-15 | REST sampling on port 18080 with `analysis.operational-context.enabled=true` after relevance scoring | Passed; sizes recorded above |
| 2026-05-15 | `mvn -q "-Dtest=PackageDependencyGuardTest" test` after relevance scoring | Passed |
| 2026-05-15 | `mvn -q "-Dtest=OperationalContextViewServiceTest,OperationalContextControllerTest,OperationalContextReadModelContractTest" test` after sourceRef/label dedupe | Passed |
| 2026-05-15 | `mvn -q "-Dtest=PackageDependencyGuardTest" test` after sourceRef/label dedupe | Passed |
| 2026-05-15 | REST sampling on port 18080 with `analysis.operational-context.enabled=true` after sourceRef/label dedupe | Passed; sizes recorded above |
| 2026-05-15 | Maintenance guidance update in architecture and fill-order docs | Docs-only; runtime tests not needed |

## Stage Plan

1. Documentation baseline: create this living plan and measurement log.
2. Metrics and contract tests: assert expanded compatibility, compact default shape, sourceRef/repetition limits and no raw payload in default.
3. Profiles: implement query parsing and compact default projections while preserving no-profile REST behavior as expanded.
4. Relevance and next reads: score/sort relations and blast-radius hits; add suggested reads and limitations.
5. Dedup/noise reduction: reduce repeated sourceRefs/labels in default through top-level sourceRef summary and item references.
6. FE vs LLM split: keep FE expanded views stable; route tools/LLM-oriented reads to default; document field ownership.
7. Verification loop: run targeted backend tests, sample REST payloads and append before/after sizes.
8. Tool evidence capture: surface compact affordances in Copilot session evidence without copying raw tool payloads.
9. Relevance tuning: add item-level scores, reasons and stricter broad-signal budgets for relation/blast-radius defaults.
10. SourceRef/label dedupe: compact default source refs and nested entity refs while keeping top-level provenance.
11. Maintenance guidance: update catalog/read-model guidance after stable iteration results.
