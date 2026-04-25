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

- `src/test/java/pl/mkn/incidenttracker/analysis/adapter`

### Evidence pipeline

- `src/test/java/pl/mkn/incidenttracker/analysis/evidence`

### Job i sync

- `src/test/java/pl/mkn/incidenttracker/analysis/job`
- `src/test/java/pl/mkn/incidenttracker/analysis/sync`

### MCP i AI

- `src/test/java/pl/mkn/incidenttracker/analysis/mcp`
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

1. dodaj port i modele do `analysis.adapter.<system>`,
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

1. dodaj go do `analysis.mcp.<capability>`,
2. deleguj do adaptera albo use case'u,
3. dopisz rejestracje przez `ToolCallbackProvider`,
4. sprawdz bridge Copilota, policyke dostepu do tooli i testy MCP context.

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

## Checkpoint

- Gdy chcesz dodac nowe dane do promptu, czy powinny wpasc najpierw jako
  evidence, czy bezposrednio do providera AI?
- Jak rozpoznasz, ze logika incidentowa trafila przypadkiem do adaptera?
- Jakie testy odpalic po zmianie w `analysis.mcp`?
- Jakie testy odpalic po zmianie job snapshotu albo komponentu Angular?
