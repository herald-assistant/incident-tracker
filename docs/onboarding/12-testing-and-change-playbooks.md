# Krok 12: Testowanie I Bezpieczne Rozwijanie Feature'ow

## Cel

Na koncu sciezki nauczyc sie, jak dotykac systemu bez psucia granic
architektonicznych.

## Po tym kroku rozumiesz

- ktore testy czytac najpierw dla konkretnego obszaru,
- jak dodac nowy adapter, provider albo tool,
- jak rozpoznac, ze zmiana trafila do zlego pakietu.

## Mapa testow

### Adaptery

- `src/test/java/pl/mkn/incidenttracker/integrations`

### Evidence pipeline

- `src/test/java/pl/mkn/incidenttracker/analysis/evidence`

### Job

- `src/test/java/pl/mkn/incidenttracker/analysis/job`

### MCP i AI

- `src/test/java/pl/mkn/incidenttracker/agenttools`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot`

### UI i frontend

- `src/test/java/pl/mkn/incidenttracker/ui`
- `frontend/src/app/**/*.spec.ts`

## Komendy, ktore warto znac

- `mvn -q clean test`
  backendowe testy jednostkowe i integracyjne Spring Boot.
- `cd frontend && npm test`
  testy Angulara. Nie sa uruchamiane przez `mvn test`.
- `cd frontend && npm run build`
  produkcyjny build UI do weryfikacji zmian frontendowych.
- `mvn -q -DskipTests package`
  pelne pakowanie backendu razem z buildem Angulara w fazie `prepare-package`.

## Playbook 1: nowy adapter

1. dodaj port i modele do `integrations.<system>`,
2. dodaj adapter REST i properties,
3. dodaj helper endpoint tylko jesli przydaje sie recznie testowac capability,
4. dopiero potem reuse'uj adapter z providera albo toola.

## Playbook 2: nowy provider evidence

1. dodaj provider do `analysis.evidence.provider.<capability>`,
2. zdefiniuj `stepCode`, `stepLabel`, `stepPhase`, `consumedEvidence`,
   `producedEvidence`,
3. dopisz go jawnie w `AnalysisEvidenceCollector`,
4. sprawdz downstreamy: flow, job, AI i ewentualny frontend.

## Playbook 3: nowy tool

1. dodaj go do `agenttools.<capability>.mcp`,
2. deleguj do adaptera albo use case'u,
3. dopisz rejestracje przez `ToolCallbackProvider`,
4. sprawdz `aiplatform.copilot.tools.CopilotSdkToolFactory`, policyke dostepu
   do tooli i testy MCP context,
5. jesli tool ma ukryty scope, dodaj go przez
   `aiplatform.copilot.tools.context`,
6. jesli tool jest drogi lub ryzykowny dla analizy incydentow, dodaj guidance w
   `features.incidentanalysis.ai.copilot.tools.description`,
7. jesli tool ma limit albo walidacje runtime, dodaj
   `CopilotToolInvocationPolicy` w `aiplatform.copilot.tools.policy`,
8. jesli wynik ma trafic do user-facing evidence, dodaj listener i mapper w
   feature'u, np. `features.incidentanalysis.ai.copilot.tools.<capability>`.

Nie dopisuj logiki konkretnego toola do
`aiplatform.copilot.tools.CopilotToolInvocationHandler`.
Handler ma pozostac boundary invocation, a side-effecty maja isc przez eventy.

## Playbook 4: zmiana AI

1. prompt i request-specific zasady zmieniaj w preparation,
2. stale zasady zmieniaj w skillu,
3. nie przeciekaj modelami adapterow do providera AI,
4. sprawdz parsowanie odpowiedzi, prepared prompt i artifact/tool policy.

## Playbook 5: zmiana job payloadu albo frontendu

1. zaktualizuj kontrakt backendowy i projekcje `AnalysisJobResponse`,
2. zaktualizuj modele TS w `frontend/src/app/core/models`,
3. sprawdz komponenty widoku analizy i `/evidence`,
4. odpal `src/test/java/pl/mkn/incidenttracker/ui`,
5. odpal `cd frontend && npm test`,
6. potwierdz `cd frontend && npm run build` albo `mvn -q -DskipTests package`.

## Playbook 6: zmiana follow-up chatu

1. nie rozszerzaj startu joba o tresc rozmowy; chat zostaje pod
   `POST /analysis/jobs/{analysisId}/chat/messages`,
2. utrzymaj request chatu jako `message`, bez recznego `environment`, branch,
   GitLab group ani DB scope'u,
3. jesli zmieniasz prompt lub policy chatu, sprawdz
   `CopilotIncidentFollowUpPreparationService`, `CopilotIncidentFollowUpPromptRenderer` i
   `CopilotIncidentToolAccessPolicyFactory.createForFollowUp(...)`,
4. jesli zmieniasz payload, zaktualizuj `AnalysisChatMessageResponse`, modele
   TS i import/eksport analizy,
5. odpal testy job controller/service oraz celowane testy Copilot preparation
   albo tool policy.

## Playbook 7: zmiana runtime Copilot tools

1. rejestracje definicji zmieniaj w
   `aiplatform.copilot.tools.CopilotSdkToolFactory`,
2. hidden scope i `ToolContext` zmieniaj w
   `aiplatform.copilot.tools.context`,
3. session validation albo inne neutralne blokady zmieniaj jako
   `CopilotToolInvocationPolicy` w `aiplatform.copilot.tools.policy`; budget
   jest przejsciowo w `analysis.ai.copilot.tools.policy.budget`,
4. logowanie/metryki/audyt dopinaj jako listenery eventow invocation albo, dla
   decyzji budzetu, przez `aiplatform.copilot.tools.policy.budget`,
5. GitLab/DB albo przyszla capability evidence mapuj w swoim
   `tools.<capability>`,
6. lifecycle publikacji sekcji evidence trzymaj w
   `aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore`, bez
   payload-specific logiki.

Celowane testy po takiej zmianie:

- `CopilotSdkToolFactory*Test`,
- `CopilotToolBudgetPolicyTest`,
- `CopilotToolBudgetRegistryTest`,
- `CopilotToolEvidenceSessionStore*Test`,
- testy MCP konkretnego toola i testy preparation/follow-up, jesli zmienia sie
  allowlista tools.

## Checkpoint

- Gdy chcesz dodac nowe dane do promptu, czy powinny wpasc najpierw jako
  evidence, czy bezposrednio do providera AI?
- Jak rozpoznasz, ze logika incidentowa trafila przypadkiem do adaptera?
- Jakie testy odpalic po zmianie w `agenttools.<capability>.mcp`?
- Jakie testy odpalic po zmianie job snapshotu albo komponentu Angular?
- Jak odroznisz zmiane finalnej analizy JSON-only od zmiany follow-up chatu,
  ktory odpowiada operatorskim tekstem?
