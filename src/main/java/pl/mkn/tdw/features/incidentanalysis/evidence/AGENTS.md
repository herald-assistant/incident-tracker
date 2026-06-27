# AGENTS

## Zakres

Ten katalog odpowiada za deterministyczny pipeline zbierania evidence i
kontrakt krokow analizy.

Obejmuje:

- `AnalysisContext` jako wspolny stan sekwencyjnego pipeline,
- `AnalysisEvidenceCollector` jako jawny orchestrator krokow evidence,
- kontrakty providera, metadata krokow i referencje evidence,
- `provider/`
  konkretne kroki pipeline: Elasticsearch, deployment context, Dynatrace,
  GitLab deterministic i operational context.

Nie obejmuje:

- adapterow integracyjnych z `integrations`,
- AI promptu i wykonania modelu z `features.incidentanalysis.ai`,
- HTTP job flow z `features.incidentanalysis.job`.

## Zasady modyfikacji

- Collector jest source of truth dla kolejnosci providerow i ewentualnych
  rownoleglych grup krokow. Nie wracaj do ukrytej kolejnosci opartej o
  `@Order` albo niejawne listy beanow.
- Lifecycle krokow evidence jest centralny: collector odpowiada za wywolywanie
  listenera przed i po kazdym providerze.
- Jesli kroki sa uruchamiane rownolegle, collector nadal ma publikowac ich
  lifecycle i wynik w deterministycznej kolejnosci, zgodnej z pipeline.
- Kazdy provider powinien byc samodzielna jednostka jednego kroku pipeline i
  zwracac jedno `shared.evidence.AnalysisEvidenceSection`.
- Kazdy provider musi miec jawne `stepCode`, `stepLabel`, `stepPhase`,
  `consumedEvidence` i `producedEvidence`.
- Downstream providery powinny czytac dane przez typowane widoki lub helpery
  blisko capability, a nie przez stringowe odczyty atrybutow rozsiane po kodzie.
- Operational context evidence matchuje i publikuje code-search scope dla
  dopasowanego `system`. Nie przywracaj posredniego targetowania po
  osobnym bycie runtime; deployment/runtime nazwy traktuj jako sygnaly
  rozpoznania systemu.
- Nie mieszaj tu logiki MCP, kontrolerow HTTP ani prompt buildera AI.
- Przy dodaniu nowego providera dopisz go explicite w collectorze i zaktualizuj
  testy kolejnosci oraz descriptorow.
- Evidence pozostaje generyczne na granicy z AI i UI przez
  `shared.evidence`, nawet jesli wewnetrznie czytanie odbywa sie przez silniej
  typowane read modele.

## Testy

- Dla collectora utrzymuj test jawnej kolejnosci i descriptorow krokow.
- Dla providerow preferuj testy jednostkowe skupione na heurystykach i
  publikowanym evidence.
- Gdy zmieniasz ksztalt evidence, sprawdz downstreamy w
  `features.incidentanalysis.flow`, `features.incidentanalysis.job`,
  `features.incidentanalysis.ai` i kolejnych providerach.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/architecture/08-operational-context-model-tools-and-usage.md`
