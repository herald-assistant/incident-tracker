# AGENTS

## Zakres

Ten katalog odpowiada za opcje wykonania AI uzywane przez flow analizy, joby,
follow-up chat i UI operatorskie.

Obejmuje:

- `AnalysisAiOptions` przekazywane do requestow AI jako neutralne preferencje
  runtime,
- kontrakt katalogu modeli: `AnalysisAiModelOptionsProvider`,
  `AnalysisAiModelOption` i `AnalysisAiModelOptionsResponse`,
- endpoint `GET /analysis/ai/options`.

Nie obejmuje:

- implementacji konkretnego providera AI z `../ai`,
- przygotowania sesji Copilota z `../ai/copilot/preparation`,
- stanu jobow ani orchestration flow.

## Zasady modyfikacji

- Ten pakiet ma pozostac neutralnym kontraktem aplikacji. Nie dodawaj tutaj
  typow Copilot SDK ani klas adapter-specific.
- `AnalysisAiOptions` moze niesc tylko preferencje wykonania AI, np. `model` i
  `reasoningEffort`; nie dokladaj tu scope'u evidence, branchy, srodowiska ani
  danych integracyjnych.
- Endpoint opcji moze pozostac pod `/analysis/ai/options`, bo opisuje wybor AI
  dla analizy, ale Java package nie jest czescia wewnetrznego modulu `ai`.
- Konkretne pobieranie katalogu modeli trzymaj przy providerze, np.
  `analysis.ai.copilot.CopilotSdkModelOptionsProvider`.

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
