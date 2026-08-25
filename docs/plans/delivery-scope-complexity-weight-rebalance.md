# Rebalans wag Delivery Scope Complexity

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Eksperymentalny scoring Delivery Scope Complexity ma silniej uwzgledniac
nowosc dostarczonej zmiany, a slabiej refaktoryzacje i architekture. Uzytkownik
jawnie zatwierdzil zmiane wag `novelty` z `0.15` na `0.20` oraz
`refactorAndArchitecture` z `0.15` na `0.10`, a nastepnie doprecyzowanie
dedykowanego runtime skilla zgodnie z przekazanym zalacznikiem.

## Baseline

- Backend deterministycznie liczy wynik `0-200` z szesciu wymiarow, ktorych
  wagi sumuja sie do `1.00`.
- Publiczny wynik zawiera wage i punkty kazdego wymiaru; API, job state,
  prompt, skill oraz schema eksportu pozostaja bez zmian.
- UI pokazuje stale wagi w opisie algorytmu i wartosci zwrocone w breakdownie.
- Wlascicielem scoringu pozostaje `features.deliveryscopecomplexity`.

## Proponowane rozwiazanie

Zmienic dwie stale w backendowym kalkulatorze, odpowiadajacy opis w UI,
fixture testowy oraz kanoniczny runtime flow. Dodac regresje, ktora sprawdza
obie nowe wagi, ich punkty i zachowanie sumy maksymalnej. Doprecyzowac rubric
w runtime skillu bez zmiany jego nazwy, odpowiedzialnosci ani output contract.

## Zakres

- `novelty`: `0.15` -> `0.20`,
- `refactorAndArchitecture`: `0.15` -> `0.10`,
- rubric `novelty`, `businessAndInvariants` i `scopeSignal` zgodnie z
  zalacznikiem,
- testy i dokumentacja bezposrednio opisujace te wagi.

## Non-goals

- zmiana pozostalych wag lub wzoru scoringu,
- zmiana promptu, nazwy skilla, API, joba, historii albo eksportu,
- zmiana Delivery Complexity Assessment.

## Ograniczenia i ryzyka

Suma wag musi pozostac rowna `1.00`. Historyczne wyniki i eksporty zachowuja
zapisane breakdowny; nowe uruchomienia korzystaja z nowych wag bez migracji.

## Conformance delta

- Wlasciciel: bez zmian, feature Delivery Scope Complexity.
- Publiczne API/DTO: bez zmian strukturalnych; zmieniaja sie wartosci `weight`
  i wyliczone `points`/`finalScore` nowych runow.
- Context/evidence, prompt/artifacts i tools/policy: bez zmian. Runtime skill
  otrzymuje bardziej rygorystyczne zasady oceny znanych wzorcow i przenoszonej
  semantyki biznesowej bez zmiany output contract.
- Job state/persistence/export: bez zmian kontraktu i schema.
- Shared FE/UX i zaleznosci: bez zmian; jedyny konsument UI otrzymuje
  zaktualizowany opis algorytmu.
- Znany drift: brak nowego lub rozszerzonego driftu.

## Kryteria akceptacji

- nowe runy licza `novelty` z waga `0.20` i
  `refactorAndArchitecture` z waga `0.10`,
- wszystkie wagi nadal sumuja sie do `1.00`, a maksimum wynosi `200`,
- UI i dokumentacja pokazuja nowe wartosci,
- runtime skill zawiera reguly z zalacznika i przechodzi test kontraktu oraz
  walidacje frontmatter,
- testy backendu i frontendu oraz build wspolnego zakresu przechodza.

## Kroki

- [x] Krok 1: zmienic scoring, testy, UI i runtime flow; zweryfikowac testami
  celowanymi Delivery Scope Complexity. Dowod: `DeliveryScopeScoringServiceTest`
  oraz 17 testow strony Delivery Scope Complexity przeszlo.
- [x] Krok 2: wykonac macierz weryfikacji zmiany backend-frontend i oznaczyc
  plan jako zakonczony. Dowod: 453 testy Angulara, produkcyjny build Angulara
  oraz `mvn -q -Pbackend-dev clean package` przeszly.
- [x] Krok 3: zaktualizowac dedykowany runtime skill zgodnie z zalacznikiem,
  dodac regresje kontraktu i uruchomic adekwatne testy skilli. Dowod:
  `DeliveryScopeRubricContractTest`, `CopilotRuntimeSkillFrontmatterTest` oraz
  pelne `mvn -q test` przeszly; lokalna effective kopia jest identyczna z
  packaged seedem.
