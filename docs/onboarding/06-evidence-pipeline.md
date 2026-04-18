# Krok 6: Pipeline Evidence

## Cel

Zrozumiec najwazniejszy backendowy mechanizm projektu: sekwencyjne zbieranie
evidence na wspolnym `AnalysisContext`.

## Po tym kroku rozumiesz

- jak dziala `AnalysisContext`,
- dlaczego collector jest jawny i sekwencyjny,
- po co provider deklaruje `phase`, `consumedEvidence` i `producedEvidence`,
- jak downstream czyta wczesniej zebrane dane.

## Glowny model

Collector uruchamia providerow po kolei:

1. Elasticsearch,
2. deployment context,
3. Dynatrace,
4. GitLab deterministic,
5. operational context.

Kazdy provider:

- czyta `AnalysisContext`,
- moze korzystac z evidence z poprzednich krokow,
- zwraca jedno `AnalysisEvidenceSection`.

## Typowane czytanie evidence

`AnalysisContext` przechowuje generyczne sekcje, ale providery i flow czytaja je
przez typed views albo helpery blisko capability, np. dla logow i deploymentu.

To jest kompromis:

- AI i UI dostaja stabilne, generyczne evidence,
- backend unika stringowego odczytu atrybutow w losowych miejscach.

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisContext.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceCollector.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider`

## Przeczytaj po kolei

- `provider/elasticsearch/ElasticLogEvidenceProvider.java`
- `provider/deployment/DeploymentContextEvidenceProvider.java`
- `provider/dynatrace/DynatraceEvidenceProvider.java`
- `provider/gitlabdeterministic/GitLabDeterministicEvidenceProvider.java`
- `provider/operationalcontext/OperationalContextEvidenceProvider.java`

## Checkpoint

- Dlaczego collector jest lepszym miejscem na lifecycle krokow niz sami
  providery?
- Co trzeba zmienic, gdy dodajesz nowy provider?
- Dlaczego kolejki providerow nie trzymamy juz w `@Order`?
