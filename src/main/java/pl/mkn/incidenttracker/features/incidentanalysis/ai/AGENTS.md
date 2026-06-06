# AGENTS

## Zakres

Ten katalog jest wlascicielem kontraktow AI specyficznych dla analizy
incydentow oraz ich implementacji Copilot.

Obejmuje:

- `initial/`
  kontrakt poczatkowej analizy incydentu: `InitialAnalysisProvider`,
  `InitialAnalysisRequest`, `InitialAnalysisPreparation` i
  `InitialAnalysisResponse` z rozdzielonym `functionalAnalysis` oraz
  `technicalAnalysis`,
- `chat/`
  kontrakt follow-up chatu dla zakonczonej analizy: provider, request/response,
  turny i snapshot poczatkowej analizy,
- `copilot/`
  incidentowe providery Copilota, preparation, prompt/artifacts, coverage,
  response parser, incident tool policy/guidance oraz GitLab/DB tool evidence
  capture.

Neutralne mechanizmy runtime Copilota mieszkaja w `aiplatform.copilot`, a
neutralne evidence/usage DTO w `shared.evidence` i `shared.ai`.

## Zasady

- Ten katalog moze znac `correlationId`, resolved `environment`,
  `gitLabBranch`, `gitLabGroup`, incident evidence i operator-facing response.
- Nie przenos tu reusable runtime Copilota, generic tool invocation ani
  adapterow integracyjnych.
- Nie importuj `features.incidentanalysis` z `aiplatform`, `agenttools`,
  `integrations`, `shared` ani `common`.
- Prompt ma niesc dane konkretnego incydentu. Stale zasady pracy z toolami i
  evidence trzymaj w skillu albo incident preparation.
- Runtime skille w `src/main/resources/copilot/skills` utrzymuj po polsku.
  Zostawiaj bez tlumaczenia tylko identyfikatory techniczne: nazwy skilli,
  tooli, pol JSON, klas, metod, endpointow, plikow i kontraktow.
- Initial response contract nie ma kompatybilnosci wstecznej ze starymi polami:
  nie dodawaj `summary`, `recommendedAction`, `rationale`, `affectedFunction`
  ani `evidenceReferences`.
- `incident-functional-analysis` jest runtime skillem dla sekcji
  `functionalAnalysis`; `incident-technical-handoff` jest runtime skillem dla
  `technicalAnalysis`.
- Operational context tools sa neutralne i mieszkaja w `agenttools`. Tutaj
  moze mieszkac tylko incidentowa semantyka ich uzycia: coverage-aware policy,
  prompt/guidance i skill `incident-operational-context-tools`.
- Incident guidance dla `opctx_*` ma traktowac operational context jako
  kontekst katalogowy do ownershipu, scope GitLaba/DB i handoffu, a nie jako
  samodzielny dowod root cause.
- Follow-up chat ma reuse'owac snapshot poczatkowej analizy, historie rozmowy,
  hidden tool context i proste user-facing tool evidence.

## Testy

- Zmiany w `initial/` i `chat/` powinny sprawdzac flow/job oraz follow-up
  request mapping.
- Zmiany w `copilot/preparation` powinny miec testy promptu, incident run
  assembly, policy tools i ladowania skilli.
- Zmiany w `copilot/response` powinny miec testy parsowania odpowiedzi modelu.
