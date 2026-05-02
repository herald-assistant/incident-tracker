# AGENTS

## Zakres

Ten katalog jest wlascicielem kontraktow AI specyficznych dla analizy
incydentow oraz ich implementacji Copilot.

Obejmuje:

- `initial/`
  kontrakt poczatkowej analizy incydentu: `InitialAnalysisProvider`,
  `InitialAnalysisRequest`, `InitialAnalysisPreparation` i
  `InitialAnalysisResponse`,
- `chat/`
  kontrakt follow-up chatu dla zakonczonej analizy: provider, request/response,
  turny i snapshot poczatkowej analizy,
- `copilot/`
  incidentowe providery Copilota, preparation, prompt/artifacts, coverage,
  response parser oraz GitLab/DB tool evidence capture.

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
- Follow-up chat ma reuse'owac snapshot poczatkowej analizy, historie rozmowy,
  hidden tool context i proste user-facing tool evidence.

## Testy

- Zmiany w `initial/` i `chat/` powinny sprawdzac flow/job oraz follow-up
  request mapping.
- Zmiany w `copilot/preparation` powinny miec testy promptu, incident run
  assembly i ladowania skilli.
- Zmiany w `copilot/response` powinny miec testy parsowania odpowiedzi modelu.
