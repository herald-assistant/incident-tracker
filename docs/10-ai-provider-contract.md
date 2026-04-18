# Krok 10: Kontrakt Providera AI

W tym kroku przygotowujemy bardzo maly kontrakt dla warstwy AI, ale jeszcze nie
podlaczamy go do glownego endpointu `/analysis`.

## Po co robimy ten krok

Chcemy rozdzielic dwie rzeczy:

- nasza logike domenowa analizy,
- sposob, w jaki jakis provider AI analizuje zebrane evidence.

To pozwala najpierw zrozumiec granice odpowiedzialnosci, a dopiero potem wejsc w
szczegoly GitHub Copilot Java SDK.

## Co dodalismy

- `AnalysisAiProvider` jako interfejs providera,
- `AnalysisAiAnalysisRequest` jako model wejscia do providera,
- `AnalysisEvidenceSection`, `AnalysisEvidenceItem` i `AnalysisEvidenceAttribute`
  jako generyczny format evidence dla warstwy AI,
- `AnalysisAiAnalysisResponse` jako model wyjscia,
- `AnalysisEvidenceProvider` jako rozszerzalny kontrakt dla zrodel evidence,
- `AnalysisEvidenceCollector` jako registry oparte o Spring IoC.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AnalysisAiProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AnalysisAiAnalysisRequest.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AnalysisEvidenceSection.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AnalysisEvidenceItem.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AnalysisEvidenceAttribute.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AnalysisAiAnalysisResponse.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceCollector.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceCollectorTest.java`

## Co jest wazne w tym kroku

Na razie:

- nie wywolujemy jeszcze Copilot SDK,
- nie zmieniamy zachowania endpointu `/analysis`,
- nie wpinamy providera AI do glownego flow.

Tworzymy tylko kontrakt i izolowany punkt, w ktorym przyszly provider AI bedzie
pracowal.

## Jak to dziala

1. Kazde zrodlo implementuje `AnalysisEvidenceProvider`.
2. Kazdy provider sam wie, jak pobrac i nazwac swoje evidence.
3. `AnalysisEvidenceCollector` zbiera wszystkie beany providera z IoC i sklada liste sekcji.
4. `AnalysisAiAnalysisRequest` niesie juz tylko `correlationId` i `evidenceSections`.
5. `AnalysisAiProvider` analizuje evidence i zwraca osobny wynik AI.
6. W obecnym runtime robi to provider oparty o GitHub Copilot SDK.

## Jak testowac

```powershell
mvn test
```

Szczegolnie interesuje nas:

- `AnalysisEvidenceCollectorTest`

## Jak debugowac

Ustaw breakpointy w:

- `AnalysisEvidenceCollector.collect(...)`
- `CopilotSdkAnalysisAiProvider.analyze(...)`

Potem uruchom test `AnalysisEvidenceCollectorTest` i zobacz:

1. jak evidence zamienia sie na request providera AI,
2. jak kazdy provider zwraca swoja sekcje evidence,
3. jak IoC sklada provider registry bez recznego spinania,
4. jak provider AI buduje diagnoze na podstawie danych, a nie gotowego wyniku.

## Co warto zrozumiec po tym kroku

1. Po co oddzielac provider AI interfejsem?
2. Dlaczego nie warto od razu wiac kodu domenowego z konkretnym SDK?
3. Jak ten kontrakt ulatwi pozniejsze podlaczenie Copilot SDK?

## Co dalej

Kolejny krok to pierwszy kontakt z GitHub Copilot Java SDK w izolacji, nadal bez
podpinania go do glownego flow endpointu.
