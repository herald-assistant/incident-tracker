# UX Inspector - import bez arbitralnego limitu envelope

Status: in-progress

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

UX Inspector potrafi wyeksportowac poprawny envelope v1 wiekszy niz milion
znakow, glownie przez historie aktywnosci AI i tool evidence, a nastepnie
odrzuca ten sam plik podczas importu. Operator nie moze przez to odtworzyc
poprawnego wyniku wygenerowanego przez aplikacje.

Uzytkownik zatwierdzil 2026-09-17 usuniecie limitu rozmiaru importu.

## Proponowane rozwiazanie

Usunac feature-owned limit miliona znakow dla calego dokumentu JSON. Zachowac
strict envelope v1, walidacje top-level fields, capture v1, kanonizacje capture,
terminalny status, pojedyncza sekcje `answer` oraz kontrole spojnosci scope'u i
raportu. Nie zmieniac schema ani wersji eksportu.

Klasyfikacja: **L1**, poniewaz zmienia sie zachowanie publicznego importu
feature'a bez zmiany DTO i envelope.

## Zakres

- walidacja importu UX Inspectora,
- regresyjny test poprawnego envelope wiekszego niz dotychczasowy limit,
- kanoniczny runtime flow import/export.

## Non-goals

- zmiana capture v1 i jego limitow bezpieczeństwa,
- zmiana schema, version, result contract albo frontendowego parsera,
- usuwanie walidacji struktury, kanonicznosci i spojnosci wyniku,
- modyfikacja zawartosci eksportu.

## Ograniczenia i ryzyka

- Endpoint nadal parsuje JSON przed walidacja feature'a; ta zmiana usuwa tez
  dodatkowa pelna serializacje `JsonNode` wykonywana tylko do pomiaru znakow.
- Limity poszczegolnych niezaufanych danych capture pozostaja bez zmian.
- Zmiana nie dotyka trwajacego osobno inkrementu source packu.

## Baseline i conformance delta

- Baseline: export zwraca caly `UxInspectorJobStateSnapshot` bez limitu
  rozmiaru, ale import odrzuca `JsonNode.toString().length() > 1_000_000`.
- Reprodukcja: poprawny export v1 z 2026-09-17 ma okolo 1,43 mln znakow po
  kompresji i jest odrzucany przed walidacja schema.
- Wlasciciel: bez zmian, `features.uxinspector.job.importing`.
- Publiczne API/DTO/schema/version: bez zmian.
- Context, prompt, tools, report, job state, persistence i frontend: bez zmian.
- Konsumenci: `POST /api/ux-inspector/imports`, frontendowy import UX Inspectora
  oraz local persistence utworzonego read-only runu.
- Kompatybilnosc: dotychczas poprawne mniejsze eksporty pozostaja poprawne;
  dodatkowo akceptowane sa poprawne eksporty dowolnego rozmiaru obwiedni.
- Architecture drift: naprawiona zostaje niesymetrycznosc export/import, bez
  nowych zaleznosci i bez importu sibling feature'a.

Baseline testow przed zmiana:
`mvn -q "-Dtest=UxInspectorImportServiceTest,UxInspectorExportServiceTest,UxInspectorJobControllerTest" test`
- PASS.

## Kryteria akceptacji

- poprawny envelope v1 wiekszy niz milion znakow jest importowany,
- niepoprawny schema, version, pola, capture i niespojny wynik sa nadal
  odrzucane,
- importowany run pozostaje read-only i nie zachowuje prepared promptu,
- testy UX Inspectora i pelna regresja backendu przechodza.

## Kroki

- [x] Usunac limit calego dokumentu z importera i dodac test regresyjny dla
  poprawnego envelope wiekszego niz milion znakow.
- [x] Zaktualizowac kanoniczny runtime flow i uruchomic testy celowane oraz
  `PackageDependencyGuardTest`.
- [ ] Powtorzyc `mvn -q test` przy wylacznym dostepie do `target`; biezacy run
  zostal zaklocony przez rownolegla przebudowe wspolnego katalogu klas.
- [x] Wykonac architecture diff i zapisac wynik weryfikacji.
- [ ] Ustawic plan jako `done` po przejsciu pelnej regresji backendu.

## Wynik weryfikacji

- Baseline przed zmiana:
  `mvn -q "-Dtest=UxInspectorImportServiceTest,UxInspectorExportServiceTest,UxInspectorJobControllerTest" test`
  - PASS.
- Po zmianie:
  `mvn -q "-Dtest=UxInspectorImportServiceTest,UxInspectorExportServiceTest,UxInspectorJobControllerTest,PackageDependencyGuardTest" test`
  - PASS po odbudowie klas; test obejmuje envelope przekraczajacy milion
    znakow i zachowanie prepared promptu jako danych nieodtwarzanych.
- Izolowane potwierdzenie po probie pelnej regresji:
  `mvn -q "-Dtest=UxInspectorImportServiceTest,PackageDependencyGuardTest" test`
  - PASS.
- `mvn -q test` oraz `mvn -q clean test` nie daly wiarygodnego wyniku calego
  repozytorium. Rownolegla aktywnosc w tym samym workspace zmieniala
  `target/test-classes` po uruchomieniu JVM testowej, co powodowalo masowe
  `ClassNotFoundException` dla istniejacych klas pomocniczych innych feature'ow.
  Testy UX Inspectora nie zglaszaly regresji.
- Architecture diff: bez nowych importow i zaleznosci, bez zmiany endpointu,
  DTO, schema/version, capture, result/report, persistence i frontendu. Znika
  tylko niesymetryczny limit calego envelope oraz dodatkowa serializacja
  `JsonNode` wykonywana do pomiaru znakow. Walidacje struktury, kanonicznosci i
  spojnosci pozostaja aktywne.
- Nie uruchamiano testow ani builda Angulara, poniewaz frontendowy parser i
  modele nie zostaly zmienione.
