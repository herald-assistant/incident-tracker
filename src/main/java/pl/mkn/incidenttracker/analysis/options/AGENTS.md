# AGENTS

## Zakres

Ten katalog jest przejsciowa fasada shared/operator API dla opcji wykonania AI
uzywanych przez flow analizy, joby, follow-up chat i UI operatorskie.

Obejmuje:

- `AnalysisAiOptions` przekazywane do requestow AI jako neutralne preferencje
  runtime,
- kontrakt katalogu modeli: `AnalysisAiModelOptionsProvider`,
  `AnalysisAiModelOption` i `AnalysisAiModelOptionsResponse`,
- endpoint `GET /analysis/ai/options`.

Nie obejmuje:

- implementacji konkretnego providera AI z `../ai`,
- incident-specific przygotowania sesji Copilota z
  `../../features/incidentanalysis/ai/copilot/preparation`,
- stanu jobow ani orchestration flow,
- stabilnego docelowego ownershipu shared/operator API.

Docelowy split:

- `AnalysisAiOptions` i podobne neutralne preferencje wykonania trafia do
  `shared.ai`,
- controller/DTO endpointu `GET /analysis/ai/options` trafia do
  `api.aioptions` albo rownowaznego podpakietu `api.*`,
- katalog modeli Copilota pozostaje w
  `aiplatform.copilot.runtime.options`.

## Zasady modyfikacji

- Ten pakiet ma pozostac neutralnym kontraktem aplikacji. Nie dodawaj tutaj
  typow Copilot SDK ani klas adapter-specific.
- `AnalysisAiOptions` moze niesc tylko preferencje wykonania AI, np. `model` i
  `reasoningEffort`; nie dokladaj tu scope'u evidence, branchy, srodowiska ani
  danych integracyjnych.
- Endpoint opcji ma zachowac URL `/analysis/ai/options`, ale Java package jest
  przejsciowy. Nie traktuj go jako incident feature ani wewnetrzny modul `ai`.
- Konkretne pobieranie katalogu modeli Copilota trzymaj w platformie:
  `aiplatform.copilot.runtime.options.CopilotSdkModelOptionsProvider`.
  W tym pakiecie zostaje tylko fasada mapujaca platformowe DTO na kontrakt
  endpointu aplikacji.

## Testy

- Dla endpointu preferuj `MockMvc`.
- Dla modelu opcji sprawdzaj normalizacje pustych wartosci i defaulty requestu
  AI.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/onboarding/03-package-map.md`
- `docs/onboarding/04-entrypoints-and-user-flows.md`
