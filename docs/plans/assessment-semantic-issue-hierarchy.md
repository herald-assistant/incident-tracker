# Semantyczna hierarchia Jira w obu assessmentach

Status: done

Zatwierdzenie: uzytkownik zatwierdzil caly plan poleceniem `wykonaj` 2026-09-30.

Source need: [Semantyczna hierarchia zadan w obu assessmentach](../needs/assessment-semantic-issue-hierarchy.md)

## Potrzeba / dlaczego

AI powinno otrzymywac jawny zakres oceny oraz potwierdzone relacje
nadrzedne-podrzedne zamiast interpretowac customowe nazwy typow Jira.
Sama podmiana etykiety `Type` nie wystarczy: obecny profil assessment nie
pobiera parent ani subtasks, a skill Scope definiuje jednostke przez nazwy
`Story`, `Bug`, `Task` i subtaski.

Poziom zmiany: L2, poniewaz obok dwoch feature-owned kontraktow prompt/skill
zmienia sie zachowanie reusable adaptera Jira, uzywanego takze przez
Change Verification oraz Jira Source.

## Baseline

Stan sprawdzony 2026-09-30:

| Obszar | Aktualne zachowanie |
| --- | --- |
| Publiczny input | Oba feature'y przyjmuja `jiraProject`, daty, model i opcjonalny effort przez wlasne `/api/.../jobs`. |
| Qualification | Typed Jira search, aktualna kategoria Done i ostatnie przejscie do Done w lokalnym oknie dat; bez filtra issue type. |
| Delivery Unit | Spojny komponent grafu zakwalifikowane issue-MR; jeden MR ma jedna tozsamosc. Sama hierarchia Jira nie laczy jednostek. |
| Jira material | Oba discovery services uzywaja `JiraIssueMaterialRequest.assessment`; `includeParent=false`, `includeSubTasks=false`, `includeComments=false`. |
| Adapter | Istnieja `parentIssue` i `subTasks`. Parent jest pobierany po wykryciu subtaska przez flagę Jira lub fallback po nazwie. Odczyty parent/children maja hardcoded `includeComments=true`. |
| AI evidence | Oba `DeliveryEvidencePacketBuilder` renderuja `- Type: <issueType>`, summary, opis, AC i dokumenty; nie renderuja hierarchii. Pakiet nie przycina materialu zwroconego przez integracje. |
| Runtime | Jedna nowa sesja na jednostke, pelny effective skill inline, pusta allowlista tools, jeden finalny JSON, zapis prepared prompt i raw response. |
| Skill | Scope definiuje jednostke przez `Story`, `Bug`, `Task`; drugi assessment opisuje biezaca Delivery Unit bez takiego katalogu typow. |
| Wynik | Assessment: siedem wymiarow `0-4` i deterministyczne DSP. Scope: szesc wymiarow score/scopeSignal i deterministyczny wynik `0-200`. |
| Job i historia | QUEUED przed async, partial persistence, usage i visibility limits, eksport/import V1 bez continuation i follow-up. |
| UI i CSV | Oryginalny `issueType` jest metadana snapshotu oraz CSV; Trends filtruje po tych metadanych. |
| Effective skills | Obie lokalne kopie `tdw-data/copilot/skills/.../SKILL.md` sa zgodne z obecnym packaged seedem. Loader nie nadpisuje istniejacych plikow. |

Baseline testowy: 9 suite'ow, 44 testy, 0 failures/errors/skips.

```text
mvn -q -Pbackend-dev -Dtest=DeliveryAssessmentSourceDiscoveryServiceTest,DeliveryScopeSourceDiscoveryServiceTest,DeliveryEvidencePacketBuilderTest,DeliveryAssessmentCopilotRunRequestAssemblerTest,DeliveryScopeCopilotRunRequestAssemblerTest,JiraRestIssueAdapterTest,PackageDependencyGuardTest,IntegrationPackageBoundaryTest test
```

Porownanie referencyjne: Incident Analysis rozdziela zaufane instrukcje od
inline evidence; Change Verification reuse'uje neutralny Jira material
z parent/children. Nie ma potrzeby importowania ktoregos z tych feature'ow.

## Proponowane rozwiazanie

Wykorzystac obecne `JiraIssueMaterial.parentIssue` i `subTasks`, bez nowego
publicznego DTO i bez slownika typow. Neutralny adapter odczytuje relacje
z `fields.parent` i `fields.subtasks`; flaga `issuetype.subtask` sluzy
wykryciu luki, gdy zadanie podrzedne nie ma dostepnego parent key. Usunac
fallback rozpoznajacy role po nazwie typu. Jawny parent moze reprezentowac
szerszy kontekst, dlatego nie jest automatycznie nazywany Story ani jednostka
oceny.

Profil assessment pobiera bezposredni parent i bezposrednie children bez
komentarzy. Odczyt parent dla assessmentu nie pobiera rodzenstwa ani dalszych
przodkow; odczyt child nie rozwija kolejnego poziomu ani parent. Profil
detailed zachowuje wlasny zakres komentarzy i materialu powiazanego.
Parametry profilu maja byc przekazywane do zagniezdzonych odczytow, zamiast
hardcoded wlaczania komentarzy. Limity integracji i awarie parent/child sa
jawnie propagowane do pakietu jednostki.

Kazdy feature samodzielnie projektuje material do artifactu:

```text
Oceniane zadania: CRM-124, CRM-125
Zadanie nadrzedne: CRM-123 (kontekst intencji)
Zadanie podrzedne: CRM-124 -> CRM-123 (w zakresie tej Delivery Unit)
Zadanie podrzedne: CRM-125 -> CRM-123 (w zakresie tej Delivery Unit)
```

Oceniane zadania sa dokladnie obecnymi `unit.issues()`. Dodatkowy material
parent/children ma jawna role kontekstowa oraz informacje, czy jego klucz
nalezy do ocenianej jednostki. Te same issue i dokumenty sa renderowane raz,
z zachowaniem wszystkich relacji i pelnego materialu zwroconego przez
integracje. Przy braku relacji zadanie pozostaje ocenianym zadaniem bez
potwierdzonego parent/child; model nie zgaduje, ze jest Story.

Usunac renderowanie metadanej `Type` w obu pakietach. Prompt i oba skille
maja wymagac oceny union zachowania potwierdzonego przez MR-y jednostki.
Opis rodzica i innych dzieci nie dowodzi dostarczenia calego ich zakresu.
Strukturalny kontrakt zakresu oceny w prompcie ma pierwszenstwo przed
sprzecznymi instrukcjami dotyczacymi typow w effective skillu.
Nie redagowac nazw wystepujacych naturalnie w opisach, tytulach albo diffach.

Alternatywy: slownik customowych typow wymaga stalego utrzymania i nie
potwierdza relacji; sama instrukcja ignorowania typu pozostawia model bez
hierarchii; laczenie jednostek po parent zmienia semantyke agregacji i wymaga
osobnej potrzeby. Wybrane podejscie reuse'uje istniejacy model relacji.

## Zakres i conformance delta

| Obszar | Delta |
| --- | --- |
| Ownership | Neutralne odczyty i profile w `integrations.jira`; znaczenie zakresu oceny w kazdym feature. |
| Publiczne API/DTO | Bez zmiany ksztaltu request/result/joba oraz `JiraIssueMaterial`; rozszerzone wykorzystanie istniejacych relacji. |
| Context/evidence | Parent i direct children, role semantyczne, membership jednostki, propagacja ograniczen; bez metadanej issue type w AI artifacts. |
| Prompt/artifacts/skills | Spojny workflow oparty na hierarchii i potwierdzonym delivery scope w obu feature'ach. |
| Tools/policy/hidden scope | Bez zmian; brak tools w sesji. |
| Wynik/report/scoring | Bez zmian kontraktu i arytmetyki; zmienia sie input interpretacji AI. |
| Job/persistence/export | Ten sam V1 i zapis wykonanego promptu; brak nowego formatu albo migratora. |
| Shared FE/UX | Bez nowych pol lub komponentow; oryginalny typ zostaje metadana operatorska. |
| Zaleznosci | Bez nowych krawedzi, bez importow sibling feature'ow. |
| Lokalny runtime | Jawne odswiezenie tylko dwoch obecnych DEFAULT kopii skilli po implementacji; bez zmiany loadera. |

Konsumenci adaptera: oba assessment discovery services, Change Verification
source/prompt/job evidence oraz `/api/jira/issue/material` i Jira Source UI.
Konsumenci metadanej `issueType`: snapshoty obu assessmentow, import/export
V1, CSV i Delivery Complexity Trends. Ci konsumenci zachowuja nazwe typu.
Prepared prompt w API/historii pokazuje dokladnie nowy input AI.

## Non-goals

Zmiana scoringu, laczenie Delivery Units po hierarchii, rozszerzenie JQL
lub okna dat, dodanie MR-ow dla kontekstowych zadan, follow-up, tools,
slownik typow, nowe komponenty UI i zmiana formatu historii nie naleza do
tego planu. Dowolne issue links nie sa dowodem parent/child. Customowe
relacje modelowane poza natywna hierarchia wymagaja osobnego rozpoznania.

## Ograniczenia i ryzyka

Dodatkowe odczyty zwiekszaja koszt source discovery oraz rozmiar promptu.
Pozostaja pod obecnymi bounded executors i limitami integracji, z jednym
poziomem powiazan i deduplikacja. Blad kontekstowego odczytu nie usuwa
poprawnie zakwalifikowanego zadania; staje sie visibility limit.

Pelny zakres rodzica moze zawyzac ocene, dlatego membership i rola kontekstowa
sa jawne, a scoring wymaga implementacyjnego evidence biezacej jednostki.
Testy struktury promptu nie gwarantuja identycznych numerycznych wynikow
niedeterministycznego modelu; live kalibracja jest osobna czynnoscia.

Nie utrzymujemy kompatybilnosci starego workflow AI: stare instrukcje o typach,
fallback po nazwie oraz testy tego zachowania sa zastapione aktualnym
kontraktem. Nie zmieniamy ksztaltu danych historii/importu, wiec nie ma
starszego formatu do migrowania lub czyszczenia. Historyczne wyniki nie sa
automatycznie przeliczane; nowa ocena wymaga nowego runu.

Effective pliki skilli nie sa nadpisywane przy starcie. Przed lokalnym
odswiezeniem porownac je ponownie z poprzednim seedem; gdy plik stal sie
CUSTOM, zachowac go i wskazac operatorowi roznice. Nie dodawac automatycznej
migracji ani globalnego nadpisywania effective katalogu.

Zastany drift w dotykanym obszarze: nazewniczy fallback adaptera i typowe
nazwy w skillu sa naprawiane; powtorzenie feature-owned lifecycle obu
assessmentow pozostaje zgodne z izolacja eksperymentu. Nie modernizujemy
innych formatow ani feature'ow. Zastane zmiany uzytkownika w dokumentacji
architektonicznej pozostaja zachowane.

## Kryteria akceptacji

- Customowe nazwy typow nie wystepuja jako metadana w input AI i nie steruja
  wykrywaniem relacji; standardowe i fikcyjne customowe typy z ta sama
  hierarchia daja identyczna projekcje AI.
- Jednostka ze wskazanymi dziecmi dostaje wlasciwy parent context oraz jawny
  membership; children poza jednostka nie rozszerzaja ocenianego zakresu.
- Standalone, wiele rodzicow w jednej jednostce, brak parent, nieudany odczyt
  child, limity oraz powtorzony dokument/issue maja deterministyczny wynik
  projekcji i jawne ograniczenia.
- Assessment nie pobiera ani nie renderuje komentarzy przy zadaniu,
  parent lub child; pozostale wykluczone dane nie przeciekaja do AI.
- Graf issue-MR, scoring, API/CSV metadane, V1 oraz one-shot runtime zachowuja
  obecne kontrakty, a konsumenci neutralnego adaptera przechodza regresje.

## Kroki

- [x] Krok 1: Rozszerzyc profil assessment o bezposrednia hierarchie,
  przekazac ustawienia profilu do powiazanych odczytow, usunac wykrywanie po
  nazwie typu i zachowac ograniczony traversal. Dowod: `JiraRestIssueAdapterTest`
  przez MockRestServiceServer dla fikcyjnych standardowych/customowych typow,
  parent/direct children, braku komentarzy, awarii i limitow; regresja
  `JiraSourceControllerTest`, Change Verification source/prompt oraz testow
  granic pakietow. Kryterium: relacja wynika z danych Jira i profile nie
  rozszerzaja swoich uprawnien do tresci.
  Weryfikacja: celowany Maven z adapterem Jira (11 przypadkow), Jira Source,
  Change Verification source/prompt i oboma testami granic zakonczyl sie
  kodem 0 dnia 2026-09-30.
- [x] Krok 2: Zmienic oba evidence builders, prompty i packaged skille na
  semantyczne role i jawny zakres oceny; deduplikowac kontekst i dokumenty,
  propagowac jego visibility limits. Dowod: oba source discovery tests,
  evidence builder tests, rubric contract tests i assembler tests, w tym
  identyczny input po zmianie samych nazw typow, standalone oraz dzieci
  poza jednostka. Kryterium: AI widzi poprawna hierarchie bez `Type`,
  a zakres jest dokladnie bieżąca Delivery Unit.
  Weryfikacja: oba feature'y przeszly celowane testy source, evidence,
  rubric, assemblera i grafu jednostek; testy obejmuja identyczny input
  dla Story/Dev Story i Sub-task/Sub Task dev, rozne tresci tego samego
  zadania, wspolnych/roznych rodzicow, standalone i custom effective skill.
- [x] Krok 3: Po ponownym porownaniu odswiezyc dwie lokalne DEFAULT kopie
  evaluatorow, zaktualizowac oba runtime-flow dokumenty i lokalne instrukcje
  feature'ow/integracji oraz potrzebe. Dowod: zgodnosc effective i packaged
  skilli, `CopilotRuntimeSkillFrontmatterTest`, diff/check oraz kontrola
  anonimizacji scenariuszy CRM. Kryterium: faktyczny prepared prompt stosuje
  nowy kontrakt; cudze CUSTOM skills i pozostale zasoby sa zachowane.
  Weryfikacja: obie effective kopie byly zgodne z poprzednim seedem
  (porownanie SHA-256 przed zapisem) i sa zgodne z nowym seedem po zapisie.
  Frontmatter oraz kontrakty promptow przeszly testy; diff/check i przeglad
  dotknietych scenariuszy CRM nie wykazaly problemow.
- [x] Krok 4: Wykonac regresje calego zakresu i architecture diff. Ze wzgledu
  na wspolny adapter zasilajacy Jira Source wykonac kolejno
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`,
  `mvn -q -Pbackend-dev clean package`. Obejmuje to oba assessmenty,
  Change Verification, API Jira, historie/import V1, scoring i granice.
  `npm ci` tylko przy braku zaleznosci lub zmianie manifestow.
  Kryterium: wymagane testy i buildy przechodza, metadane typow w raportach/CSV
  oraz wynikowy graf zaleznosci sa zgodne z baseline; plan otrzymuje status
  done dopiero po wszystkich dowodach.
  Weryfikacja 2026-09-30: 83 pliki / 638 testow Angulara przeszlo,
  produkcyjny build Angulara zakonczyl sie kodem 0, a
  `mvn -q -Pbackend-dev clean package` zakonczyl sie kodem 0.
  Backend: 362 suite'y, 1760 przypadkow, 0 failures/errors; jeden opcjonalny
  `CopilotSdkContextTierLiveTest` pominiety, 1759 testow wykonanych.
  Oba assessmenty oraz adapter Jira obejmuje 31 suite'ow / 121 testow.
  Wynikowy JAR: `target/team-delivery-workspace-0.0.1-SNAPSHOT.jar`.
  Pelne testy frontendowe powtorzono z czterema workerami po trzech timeoutach
  przy domyslnej rownoleglosci; tymczasowa konfiguracja zostala usunieta.
  Testy/build Angulara i pakowanie Maven wykonano poza sandboxem po bledach
  dostepu do katalogow / Maven Central. Nie zmieniono konfiguracji projektu.
  Architecture diff i `git diff --check` sa poprawne; oryginalny typ w
  snapshotach/CSV, graf issue-MR, format V1 i granice zaleznosci sa zachowane.
