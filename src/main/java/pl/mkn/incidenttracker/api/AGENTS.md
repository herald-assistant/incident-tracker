# AGENTS

## Zakres

Ten katalog jest globalna warstwa HTTP oraz docelowe miejsce na
shared/operator API dla frontendu.

Obecnie obejmuje:

- wspolny kontrakt bledow HTTP,
- globalny `ApiExceptionHandler`,
- wspolny kontrakt walidacji,
- katalog opcji AI nad `aiplatform.copilot.runtime.options`,
- stabilne fasady nad integracjami uzywane przez wiele ekranow, np.
  Elasticsearch log search, GitLab repository/source search, Database console
  i Operational Context catalog UI.

## Zasady

- Feature-specific endpointy trzymaj przy feature, np.
  `features.<feature>.api`.
- Shared/operator endpointy moga delegowac do `aiplatform`, `integrations`,
  `shared` albo malych neutralnych use case'ow.
- Nie dodawaj tutaj orchestration konkretnego feature'a, evidence pipeline,
  promptow, skilli, job state ani adapterow REST.
- Nie importuj `api.*` z feature'ow jako kontraktu runtime. Jesli feature
  potrzebuje wspolnego typu, przenies go do `shared` albo blizej wlasciciela.
- Stabilne endpointy FE/operatora trzymaj tutaj; adapter, porty, modele
  request/result i service capability zostaja w `integrations.*`.

## Aktualny refactor opcji AI

Historyczne `analysis.options` jest zamkniete. Aktualny split:

- neutralne `AnalysisAiOptions` mieszka w `shared.ai`,
- controller i DTO endpointu `GET /analysis/ai/options` mieszkaja w
  `api.aioptions`,
- platformowy katalog modeli zostaje w `aiplatform.copilot.runtime.options`.
