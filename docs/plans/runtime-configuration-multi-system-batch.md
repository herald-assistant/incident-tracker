# Config Drift Verification multi-system batch

Status: in progress

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Operator powinien moc uruchomic jedno porownanie branchy dla wielu
`internal-system` jednoczesnie. Formularz ma domyslnie zaznaczac wszystkie
dostepne komponenty, backend ma wykonac ich porownania rownolegle, a wynik ma
pozostac czytelny przez osobna zakladke dla kazdego komponentu i jego plikow.

## Klasyfikacja

Zmiana L3. Zmienia publiczny request joba, model stanu i wyniku, orkiestracje,
persistencje, export/import oraz glowny ekran feature'a.

## Baseline

- request ma pojedyncze `systemId`,
- `RuntimeConfigurationVerificationJobState` przechowuje jeden deterministic
  context, jeden diff i jeden wynik,
- job wykonuje jeden scope na jednym tasku,
- historia i export nazywaja run pojedynczym `systemId`,
- UI uzywa zwyklego pojedynczego `<select>` i renderuje jeden wynik,
- Tool Workbench wykonuje pojedynczy preview dla wskazanego systemu.

## Docelowy kontrakt

### Request

`RuntimeConfigurationVerificationJobStartRequest` przyjmuje `systemIds` jako:

- niepusta liste unikalnych identyfikatorow,
- maksymalnie 50 pozycji jako ochrone publicznego endpointu,
- kolejnosc stabilna i zachowana w stanie joba oraz zakladkach,
- brak legacy `systemId` w nowym request contract.

### Stan joba

Jeden parent job zawiera uporzadkowana liste
`RuntimeConfigurationComponentRunSnapshot`. Kazdy element ma:

- `componentRunId`, `systemId`, `systemLabel`, `configurationDirectory`,
- niezalezny `status`, biezacy krok, blad i timestamps,
- niezalezne kroki, evidence, aktywnosc AI, prompt, wynik oraz report.

Top-level job zachowuje wspolne parametry runu: repository, branche, mode,
code ref i opcje AI. Nie utrzymuje drugiej kopii pojedynczego component result.

### Status batcha

- `COMPLETED`: wszystkie komponenty zakonczyly sie bez ograniczen,
- `COMPLETED_WITH_LIMITATIONS`: co najmniej jeden komponent ma ograniczenia
  albo blad, ale istnieje przynajmniej jeden wynik,
- `FAILED`: zaden komponent nie dostarczyl wyniku albo fan-out nie mogl
  wystartowac,
- blad jednego komponentu nie anuluje pozostalych komponentow.

## Orkiestracja i rownoleglosc

- scope jest rozwiazywany niezaleznie per `systemId`,
- komponenty sa uruchamiane na feature-owned bounded executorze,
- limit rownoleglosci jest konfigurowalny przez
  `features.runtime-configuration-verification.max-parallel-components`,
  domyslnie `20`,
- kazdy komponent ma izolowany state i dla `DEEP` unikalny runtime/session id,
- parent job publikuje postep jako liczbe zakonczonych komponentow,
- persistencja snapshotu jest synchronizowana na poziomie parent joba.

Aktualnie `DEEP` pozostaje zablokowany w UI. Kontrakt batcha nie powinien
uniemozliwiac przyszlego rownoleglego `DEEP`; izolacja promptu, evidence, tool
scope i usage musi byc per komponent.

## UI/UX

- pole `Komponent / internal-system` staje sie rozwijanym multi-selectem,
- po zaladowaniu input-options zaznaczone sa wszystkie systemy,
- dropdown ma checkboxy, akcje `Zaznacz wszystkie` / `Wyczysc` i skrot
  `N z M wybranych`,
- start jest zablokowany przy pustym wyborze,
- po odtworzeniu historii/importu zaznaczone sa systemy zapisane w runie,
- wynik uzywa shared `AnalysisResultTabsComponent`: jedna zakladka per
  komponent w kolejnosci requestu,
- zakladka pokazuje status komponentu; aktywny panel reuse'uje istniejacy
  renderer diffu i jego liste plikow,
- komponent zakonczony bledem ma wlasna zakladke z czytelnym bledem, bez
  ukrywania udanych wynikow pozostalych komponentow.

Tool Workbench pozostaje pojedynczym preview. Jego celem jest inspekcja
pipeline'u dla jednego scope'u, a nie odtwarzanie batch joba.

## Historia, export i import

- nazwa historii: `N komponentow · source -> target · mode`,
- export schema i result contract maja pierwsza kanoniczna wersje `1`,
- nowy export zawiera caly batch i component snapshots,
- importer V1 odtwarza zakladki bez dostepu do GitLaba,
- importer akceptuje wylacznie pelny kontrakt V1; inne wersje i niepelne rekordy bez
  projekcji plikowej sa odrzucane bez migracji i fallbacku prezentacji,
- filename nie zawiera listy wszystkich systemow, tylko liczbe komponentow i
  pare branchy.

## Conformance delta i konsumenci

- publiczne API: start request i job snapshot,
- runtime: job service, state, listener routing i rownolegly fan-out,
- AI: per-component run id, prompt, evidence, tool scope i usage,
- presentation: per-component result bez mieszania `differenceId`,
- persistence: local run index, snapshot sanitizer oraz export/import V1,
- frontend: models, API request, formularz, polling, historia/import/export,
  shared tabs i feature result view,
- Workbench API/model: bez zmian.

Granice pozostaja zgodne z architektura: scope i orchestration mieszkaja w
feature, GitLab adapter pozostaje reusable i nie poznaje batcha, a shared tabs
nie poznaja semantyki Config Drift Verification.

## Macierz testow

- request: brak listy, pusta lista, duplikaty, zly identyfikator, limit 50,
- defaults UI: wszystkie systemy wybrane po input-options,
- orchestration: wszystkie komponenty startuja, limit rownoleglosci jest
  respektowany, kolejnosc wynikow pozostaje zgodna z requestem,
- partial failure: jeden blad + pozostale sukcesy daje
  `COMPLETED_WITH_LIMITATIONS`,
- all failed: batch ma `FAILED`,
- isolation: kroki, evidence, prompt, usage i result nie przeciekaja miedzy
  komponentami,
- persistence: snapshoty posrednie i koncowe, nazwa historii,
- portability: export V1, import V1 oraz odrzucenie innych wersji i niepelnego V1,
- UI: multi-select, select all/clear, empty guard, component tabs, failed tab,
  zmiana aktywnego komponentu i odtworzenie importu,
- regresja: pojedynczy wybrany komponent nadal daje poprawny batch z jedna
  zakladka; Workbench pozostaje pojedynczy.

## Kroki i bramki akceptacji

- [x] Krok 1: Wprowadzic batch contract (`systemIds`, component snapshots) i
  wydzielic obecne wykonanie pojedynczego systemu do izolowanego component
  runnera. W tym kroku orchestrator moze jeszcze wykonywac komponenty
  sekwencyjnie, ale caly backend, testy i snapshoty maja juz uzywac nowego
  kontraktu.
- [x] Krok 2: Dodac bounded parallel fan-out, agregacje statusu parent joba,
  partial failure i bezpieczna, synchronizowana persistencje snapshotow.
- [x] Krok 3: Ustanowic export/result contract V1, dodac import wylacznie
  pelnego V1, usunac wszystkie sciezki legacy i fallback prezentacji oraz
  zaktualizowac historie i filename.
- [x] Krok 4: Zastapic pojedynczy select komponentu multi-selectem, zaznaczac
  wszystkie opcje po starcie i wysylac `systemIds`; dodac testy zachowania
  formularza.
- [x] Krok 5: Dodac zakladki per komponent przez shared result tabs, przeniesc
  istniejacy widok diff/findings do aktywnego component panelu i pokazac
  lokalny blad w zakladce komponentu.
- [x] Krok 6: Zaktualizowac runtime flow, bundle i wykonac pelna macierz testow
  backend/frontend, package dependency guard, package oraz drift check.

Kazdy krok wymaga osobnej zgody przed implementacja.

Stan po kroku 4: backend wykonuje uporzadkowany batch rownolegle na izolowanym,
feature-owned executorze z domyslnym limitem `20`. Export, lokalna historia i
import uzywaja wylacznie pelnego kontraktu V1 z component snapshots. Inne wersje,
niezgodna kolejnosc batcha i wynik bez projekcji plikowej sa odrzucane bez
migracji albo fallbacku UI. Nazwa historii i pliku eksportu uzywa liczby
komponentow. Formularz ma multi-select z domyslnie zaznaczonymi wszystkimi
systemami, akcjami zaznaczenia/czyszczenia i blokada pustego wyboru; historia i
import odtwarzaja zapisane `systemIds`. Ekran wyniku uzywa shared result tabs w
kolejnosci requestu; kazda zakladka pokazuje status i wlasny diff plikowy, a
blad komponentu jest izolowany w jego panelu. Caly feature-specific export,
result contract i kompaktowy format AI/Workbench maja jedna kanoniczna wersje
V1; nie ma parsera ani migracji dla innych wersji.

Stan po kroku 6: runtime flow i produkcyjny bundle sa aktualne. Pelna bramka
jakosci przeszla bez bledow: 988 testow backendu, 228 testow frontendu,
`PackageDependencyGuardTest` oraz packaging aplikacji. Architecture diff nie
wykazal nowych importow z feature'a do Incident Analysis, nowych aliasow API
ani rozszerzenia model-facing tool schema. Batch pozostaje w granicy
`features.runtimeconfigurationverification`, korzysta z reusable integracji i
shared UI, a pozostaly architecture drift nie zostal rozszerzony.

## Non-goals

- batch preview w Tool Workbench,
- automatyczne dobieranie komponentow na podstawie zmienionych plikow,
- anulowanie pojedynczego komponentu albo calego batcha,
- zapis zmian do repozytorium konfiguracji,
- odblokowanie opcji `DEEP` w UI.
