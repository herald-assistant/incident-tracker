# Krok 13: Evidence Pipeline, Provider Registry I AI-First Flow

W tym kroku registry providerow dojrzalo do pelnego pipeline:

- kazde zrodlo samo przygotowuje `AnalysisEvidenceSection`,
- Spring zbiera wszystkie implementacje `AnalysisEvidenceProvider`,
- providerzy dalej pracuja sekwencyjnie na wspolnym `AnalysisContext`,
- ale kazdy krok deklaruje tez jawnie:
  - `phase`,
  - `consumesEvidence`,
  - `producesEvidence`,
- flow synchroniczny i jobowy reuse'uja wspolny `AnalysisOrchestrator`,
- AI pozostaje glownym analizatorem, a nie dodatkiem do rule engine.

## Po co robimy ten krok

Chcemy uniknac sytuacji, w ktorej jedna klasa musi zgadywac:

- kto od kogo zalezy,
- skad biora sie `environment` i `branch`,
- ktory provider publikuje jakie fakty,
- i jak to pokazac w UI.

Zamiast tego pipeline ma teraz dwa poziomy czytelnosci:

1. kolejnosc wykonania,
2. jawne metadata zaleznosci.

To jest szczegolnie wazne dla zaleznosci:

- Elasticsearch -> Deployment context,
- Deployment context -> Dynatrace,
- Deployment context -> GitLab deterministic,
- wszystko zebrane -> Operational context,
- evidence -> AI.

## Co dodalismy

- `AnalysisEvidenceReference`
- `AnalysisStepPhase`
- rozszerzone `AnalysisEvidenceProviderDescriptor`
- jawne `consumedEvidence()` i `producedEvidence()` w providerach
- `AnalysisOrchestrator`
- `analysis.evidence.provider.deployment`
- `analysis.evidence.provider.operationalcontext`
- `DeploymentContextEvidenceProvider`
- `OperationalContextEvidenceMapper`
- `OperationalContextCatalogMatcher`

## Co uproscilismy

- `AnalysisService` nie sklada juz calej analizy samodzielnie,
- `AnalysisJobService` nie dubluje juz orchestration flow,
- `GitLabDeterministicEvidenceProvider` nie odpowiada juz za deployment facts,
- `OperationalContextEvidenceProvider` nie trzyma juz calej logiki matchingu w
  jednej klasie.

To jest celowe uproszczenie:

- collector odpowiada za sekwencje krokow,
- orchestrator odpowiada za flow `collect -> prepare -> analyze -> map result`,
- deployment odpowiada za derived facts,
- GitLab deterministic odpowiada za code evidence,
- operational context odpowiada za enrichment katalogowy.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceCollector.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceProviderDescriptor.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceReference.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisStepPhase.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/deployment/`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/elasticsearch/ElasticLogEvidenceView.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisOrchestrator.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/elasticsearch/ElasticLogEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/dynatrace/DynatraceEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/gitlabdeterministic/GitLabDeterministicEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/operationalcontext/`

## Jak to dziala teraz

1. Endpoint przyjmuje `correlationId`.
2. `AnalysisService` albo `AnalysisJobService` deleguje do `AnalysisOrchestrator`.
3. Orchestrator uruchamia `AnalysisEvidenceCollector`.
4. Collector startuje od pustego `AnalysisContext` i pobiera wszystkie beany `AnalysisEvidenceProvider`.
5. Providerzy uruchamiaja sie sekwencyjnie i po kazdym kroku collector doklada nowa sekcje evidence.
6. Orchestrator czyta deployment facts przez typowany view, buduje `AnalysisAiAnalysisRequest` i przygotowuje prompt.
7. `AnalysisAiProvider` analizuje evidence.
8. Wynik AI jest mapowany na `AnalysisResultResponse`.

## Aktualne kroki pipeline

1. `ElasticLogEvidenceProvider`
   Publikuje `elasticsearch/logs`.
2. `DeploymentContextEvidenceProvider`
   Czyta logs i publikuje `deployment-context/resolved-deployment`.
3. `DynatraceEvidenceProvider`
   Czyta logs + deployment context i publikuje `dynatrace/runtime-signals`.
4. `GitLabDeterministicEvidenceProvider`
   Czyta logs + deployment context i publikuje `gitlab/resolved-code`.
5. `OperationalContextEvidenceProvider`
   Czyta logs + deployment + Dynatrace + GitLab i publikuje
   `operational-context/matched-context`.
6. `AnalysisAiProvider`
   Czyta caly zebrany context i zwraca wynik analizy.

## Jak debugowac

Ustaw breakpointy w:

- `AnalysisEvidenceCollector.collect(...)`
- `DeploymentContextEvidenceProvider.collect(...)`
- `DynatraceEvidenceProvider.collect(...)`
- `GitLabDeterministicEvidenceProvider.collect(...)`
- `OperationalContextEvidenceProvider.collect(...)`
- `AnalysisOrchestrator.analyze(...)`
- `CopilotSdkAnalysisAiProvider.analyze(...)`

## Jak testowac

```powershell
mvn -q test
```

Najwazniejsze testy:

- `AnalysisEvidenceCollectorTest`
- `AnalysisServiceTest`
- `AnalysisJobServiceTest`
- `DeploymentContextEvidenceProviderTest`
- `OperationalContextEvidenceProviderTest`

## Co warto zrozumiec po tym kroku

1. Dlaczego `@Order` nie wystarcza jako jedyny opis zaleznosci?
2. Dlaczego deployment facts warto wydzielic z GitLaba?
3. Dlaczego typowane views nad evidence sa bezpieczniejsze niz stringowe odczyty?
4. Dlaczego sync i async analiza powinny reuse'owac ten sam orchestrator?

## Co dalej

Naturalny kolejny krok to dalsze upraszczanie heurystyk i katalogu
operational context albo bogatsza prezentacja flow zaleznosci na froncie,
bo backend ma juz do tego potrzebne metadata.
