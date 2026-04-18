# Krok 5: Warstwa Serwisowa

W tym kroku wydzielamy logike z kontrolera do osobnego serwisu.

## Po co robimy ten krok

Kontroler nie powinien byc miejscem, w ktorym stopniowo rosnie cala logika
biznesowa. Jego rola to:

- przyjac request HTTP,
- uruchomic walidacje,
- przekazac sterowanie dalej,
- oddac odpowiedz HTTP.

Serwis jest miejscem, w ktorym bedziemy rozwijac logike analizy.

## Co zmienilismy

- dodalismy `AnalysisService`,
- kontroler deleguje teraz prace do serwisu,
- dodalismy osobny test jednostkowy serwisu,
- test kontrolera sprawdza warstwe HTTP niezaleznie od logiki serwisu.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisService.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisControllerTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisServiceTest.java`

## Jak to dziala

Przeplyw jest teraz taki:

1. request trafia do kontrolera,
2. Spring waliduje `request body`,
3. kontroler wywoluje `AnalysisService`,
4. serwis zwraca wynik,
5. kontroler oddaje odpowiedz HTTP.

## Jak testowac

### Test webowy

`AnalysisControllerTest`

Pokazuje:

- mapowanie HTTP -> Java,
- walidacje requestu,
- format odpowiedzi,
- to, ze dla blednego requestu serwis w ogole nie jest wywolywany.

### Test jednostkowy

`AnalysisServiceTest`

Pokazuje:

- czysta logike Java,
- brak zaleznosci od Spring MVC,
- szybki test jednej klasy.

## Co uruchomic

```powershell
mvn test
```

## Jak debugowac

Ustaw breakpointy w:

- `AnalysisController.analyze(...)`
- `AnalysisService.analyze(...)`

Potem uruchom poprawny request i zobacz:

- najpierw wejscie do kontrolera,
- potem przejscie do serwisu,
- potem powrot do odpowiedzi HTTP.

Przy niepoprawnym request body breakpoint w serwisie nie powinien zostac
osiagniety.

## Co warto zrozumiec po tym kroku

1. Co powinno zostac w kontrolerze, a co powinno trafic do serwisu?
2. Czym rozni sie test webowy od testu jednostkowego?
3. Dlaczego podzial warstw ulatwia dalszy rozwoj aplikacji?

## Co dalej

Kolejny dobry krok to rozszerzona obsluga bledow HTTP dla wyjatkow biznesowych i
infrastrukturalnych, a nie tylko dla walidacji requestu.
