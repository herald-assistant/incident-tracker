# AGENTS

## Zakres

Ten katalog odpowiada za sekwencyjny pipeline zbierania evidence i kontrakt
krokow analizy.

Obejmuje:

- `AnalysisContext` jako wspolny stan sekwencyjnego pipeline,
- `AnalysisEvidenceCollector` jako jawny orchestrator krokow evidence,
- kontrakty providera, metadata krokow i referencje evidence,
- `provider/`
  konkretne kroki pipeline: Elasticsearch, deployment context, Dynatrace,
  GitLab deterministic i operational context.

Nie obejmuje:

- adapterow integracyjnych z `../adapter`,
- AI promptu i wykonania modelu z `../ai`,
- HTTP flow z `../sync` i `../job`.

## Zasady modyfikacji

- Collector jest source of truth dla kolejnosci providerow. Nie wracaj do
  ukrytej kolejnosci opartej o `@Order` albo niejawne listy beanow.
- Lifecycle krokow evidence jest centralny: collector odpowiada za wywolywanie
  listenera przed i po kazdym providerze.
- Kazdy provider powinien byc samodzielna jednostka jednego kroku pipeline i
  zwracac jedno `AnalysisEvidenceSection`.
- Kazdy provider musi miec jawne `stepCode`, `stepLabel`, `stepPhase`,
  `consumedEvidence` i `producedEvidence`.
- Downstream providery powinny czytac dane przez typowane widoki lub helpery
  blisko capability, a nie przez stringowe odczyty atrybutow rozsiane po kodzie.
- Nie mieszaj tu logiki MCP, kontrolerow HTTP ani prompt buildera AI.
- Przy dodaniu nowego providera dopisz go explicite w collectorze i zaktualizuj
  testy kolejnosci oraz descriptorow.
- Evidence pozostaje generyczne na granicy z AI i UI, nawet jesli wewnetrznie
  czytanie odbywa sie przez silniej typowane read modele.

## Testy

- Dla collectora utrzymuj test jawnej kolejnosci i descriptorow krokow.
- Dla providerow preferuj testy jednostkowe skupione na heurystykach i
  publikowanym evidence.
- Gdy zmieniasz ksztalt evidence, sprawdz downstreamy w `../flow`, `../job`,
  `../ai` i kolejnych providerach.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/13-evidence-provider-registry.md`
