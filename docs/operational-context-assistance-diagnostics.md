# Operational Context: diagnostyka asysty AI

Ten przewodnik służy do sprawdzania działającej asysty i odtwarzania błędów.
Aktualny kontrakt katalogu, API, narzędzi i zapisu opisuje
[`architecture/operational-context-model-tools-and-usage.md`](architecture/operational-context-model-tools-and-usage.md).
Reguły merytoryczne są w `../operational-context-maintenance/`; ostateczny
kształt pól i referencji określają schemat oraz walidator maintenance. Dawne
plany nie są źródłem aktualnego zachowania.

## Co sprawdzić w przebiegu analizy

1. Otwórz run w Analysis History i boczny panel przebiegu. Krok `PREPARE_AI`
   pokazuje prompt przygotowany przed wywołaniem Copilota; panel pokazuje też
   aktywność AI, użyte źródła, ograniczenia widoczności i szacowany koszt.
   Prompt oraz historia mogą zawierać niezmieniony opis operatora, katalog i
   odczytane pliki GitLab. Przed przeniesieniem materiału do zgłoszenia lub
   testu zanonimizuj cały scenariusz do fikcyjnej domeny CRM.
2. Sprawdź, czy prompt zawiera osobno 11 reguł maintenance, dane operatora
   i dziewięć aktywnych dokumentów katalogu z jednego digesta. Jeśli wybrano
   GitLab, sprawdź także metadane projektu i drzewo. Brak pełnego materiału
   zatrzymuje run jawnie; asysta nie powinna pracować na niejawnie obciętym
   katalogu. Źródłem jest efektywna kopia w
   `${tdw.workspace.directory}/operational-context`, nie seed z JAR-a.
   Sprawdź również sekcję `Effective skill`: startup nie nadpisuje istniejącego
   skilla w `${copilot-home}/skills`. Zmiana packaged seeda nie synchronizuje
   automatycznie jego efektywnej treści. Jawny zapis lub restore w
   `Platform / AI Skills` aktualizuje plik i katalog skilli w pamięci; samo
   skopiowanie pliku na dysku nie aktualizuje cache działającej aplikacji.
   Restore przywraca seed załadowany przez aktualny proces; po zmianie zasobów
   użyj jawnego zapisu aktualnej treści albo uruchom proces z nowym seedem.
   Już przygotowany prompt i aktywny run zachowują poprzednią treść.
3. Przy GitLabie porównaj wybrany projekt i gałąź z przypiętym commitem.
   `source-options` podpowiada projekty zapisane w katalogu, nie wszystkie
   projekty GitLab. Pełny URL projektu musi należeć do skonfigurowanego
   origin i głównej grupy; podgrupy są dozwolone. Drzewo, listowanie i
   wyszukiwanie pokazują kandydatów, ale dopiero pełny odczyt pliku na
   przypiętym commicie daje cytowalny `gitlab:` source ref. Inny projekt z
   tej samej głównej grupy wymaga własnego przypięcia gałęzi do commita.
   Brak opcjonalnego pliku w root nie jest awarią. Brak odczytu pliku
   potrzebnego do wniosku musi pozostać ograniczeniem widoczności.

Sesja asysty nie ma budżetu liczby wywołań tools ani limitu rozmiaru katalogu,
reguł i odczytanych plików. `gitlab_read_repository_file` zwraca kompletny,
zweryfikowany plik z przypiętego commita; nieudany odczyt pozostaje jawną luką.
Stronicowane wyniki drzewa, listy i wyszukiwania mają kursory kontynuacji.

`AGENTS.md` i `.github/copilot-instructions.md` z analizowanego repozytorium
są materiałem o tym repozytorium, nie instrukcjami sesji asysty. Wnioski o
implementacji trzeba potwierdzić w odpowiednim kodzie. Odczyt nie filtruje
nazw ani treści według wzorców danych wrażliwych; nadal obowiązują granice
grupy, ścieżki, formatu tekstowego, kompletności i przypiętego commita.

## Jak rozpoznać miejsce błędu

Przy sprzątaniu porównaj pełny katalog przed i po, a nie samą liczbę findingów.
Instrukcja `Cleanup verification` wymaga rozliczenia treści pomijanej przez
writable schema, pełnych celów mapy, różnych ról, ownershipu, wyników search
i granic per scope/repository. Słabe sygnały systemu również wpływają na dobór
scope'ów GitLaba. Nierozpoznany glossary ID pozostaje jawną luką wiedzy.

- **Run zatrzymał się przed odpowiedzią AI.** Sprawdź krok zbierania katalogu
  i `PREPARE_AI`: komplet dziewięciu YAML, 11 reguł,
  dostępność długiego kontekstu i wybraną gałąź.
- **AI nie zaproponowało zmiany.** Sprawdź prompt, faktycznie przeczytane
  `gitlab:` refs i `visibilityLimits`. Sam wybór projektu ani nazwy plików
  nie potwierdzają roli repozytorium. `BLOCKED` oznacza brak zestawu gotowego
  do review albo blokadę przygotowania; `PARTIAL` wskazuje ograniczenia przy
  istniejących propozycjach.
- **Frontend nie przechodzi walidacji.** Sprawdź jawny wybór
  `repositoryFacts.systemSubtype` operatora. Dla `frontend` draft musi zawierać
  `systemSubtype: frontend`, `repositoryType: frontend` oraz scope tego systemu
  z jednym repozytorium `primary`. Same artefakty Angulara nie klasyfikują
  systemu. W starszym draftcie bez typu repozytorium wybierz frontend w polu
  systemu, następnie dodaj i potwierdź `repositoryType: frontend` przy
  powiązanym repozytorium w tym samym zestawie. Pominięcie scope'u zablokuje
  podgląd. `unknown` pozostaje prawidłową odpowiedzią, gdy rola nie jest znana.
- **Odpowiedź AI jest odrzucona.** Sprawdź log błędu parsera, kontrakt
  ścisłego JSON, dozwolone pola i source refs. Tool
  `operational_context_assistance_validate_draft` jest tylko wstępną,
  read-only kontrolą; backend ponawia parser i walidację po odpowiedzi.
- **`batch/preview` zwraca `VALIDATION_FAILED`.** Odczytaj `fieldErrors`:
  `/mutations/{index}/payload/...` wskazuje wybraną mutację `APPLY`, pole i
  pozycję. Propozycje `SKIP` nie mają indeksu mutacji. Referencja do `CREATE`
  w tym samym wybranym zestawie może wskazywać późniejszą mutację;
  referencja do pominiętej propozycji jest błędem. Po korekcie lub zmianie
  wyboru wykonaj nowy podgląd.
- **Ręczny edytor odrzuca zmianę systemu.** Odczytaj komunikat walidatora oraz
  wskazany typ, ID i pole powiązanej encji. Przy
  `FRONTEND_PRIMARY_REPOSITORY_TYPE_MISMATCH` otwórz wskazane główne
  repozytorium i sprawdź jego `repositoryType`. Zmiana systemu i repozytorium
  przez osobne ręczne zapisy może wymagać kolejności zgodnej z regułami
  katalogu; asysta pozwala przygotować obie zmiany w jednym batchu.
- **Katalog nie ma findings, ale zapis istniejącej karty zwraca `422`.**
  Walidacja read-modelu nie zastępuje walidacji pełnego writable payload.
  Istniejąca karta może być czytelna, a jej zapis zostać odrzucony np. za
  pusty tekst, `null`, niepoprawny typ listy/obiektu lub kształt targetu
  relacji. Sprawdź `code`, `message` i `fieldErrors`; dla ręcznego update
  wskaźniki dotyczą `/payload/...`, dla batcha
  `/mutations/{index}/payload/...`. Walidacja obejmuje także pola, których
  operator nie zmieniał. `422` oznacza odrzucenie mutacji, nie publikację.
  Nie ponawiaj zapisu bez rozliczenia błędu ani nie czyść innych pól poza
  zatwierdzonym zakresem. To niespójność danych z kontraktem zapisu, nie
  obsługiwany alternatywny format; jej uporządkowanie wymaga osobnego podglądu
  i decyzji operatora.
- **Po powrocie z historii brakuje poprawek.** Sprawdź
  `PUT /jobs/{jobId}/review`, zapis runu w `runs/{jobId}/run.json` oraz
  odtworzenie nierozstrzygniętego joba. Lokalny stan przeglądarki chroni
  tylko zmiany niepotwierdzone jeszcze przez serwer. Rozstrzygnięty run jest
  tylko do odczytu.
- **Podgląd przeszedł, zapis odmówiono.** Sprawdź digest katalogu, wartości
  `before` i candidate digest, a następnie ponów podgląd. Zapis jest jedną
  warunkową operacją batch; konflikt lub błąd walidacji nie może zostawić
  częściowego katalogu ani decyzji `APPLY`. Historia analiz nie jest historią
  wersji ani rollbackiem YAML.

Punkty wejścia w kodzie: `features.operationalcontextassistance/job` prowadzi
run, review i historię; `ai` przygotowuje materiał i prompt; `source` zbiera
GitLab; `draft` parsuje i sprawdza propozycje; `integrations.operationalcontext`
posiada schemat, walidację i atomowy zapis. UI jest w
`frontend/src/app/operational-context`. Zmieniając jedną z tych granic,
sprawdź także jej konsumentów i lokalne `AGENTS.md`.

## Odświeżanie katalogu i kontrolowana edycja offline

Udany zapis przez maintenance aktualizuje pliki oraz snapshot katalogu
w pamięci bieżącego procesu. Ręczna zmiana pliku na dysku nie odświeża
snapshotu; odświeżenie strony UI nadal odczyta katalog z pamięci aplikacji.
Taką zmianę proces zobaczy po ponownym uruchomieniu.

Edycja offline nie jest automatycznym obejściem `422`. Można ją wykonać
wyłącznie jako osobno uzgodnioną operację utrzymaniową:

1. Przygotuj konkretny podgląd, zachowaj hash/digest źródeł i uzyskaj zgodę
   operatora na zakres oraz tryb publikacji. Używaj efektywnego katalogu,
   nie seeda z zasobów aplikacji.
2. Operator zatrzymuje aplikację przed edycją; potwierdź jej zatrzymanie
   i zgodność źródeł z zaakceptowanym podglądem. Nie steruj procesem
   aplikacji automatycznie w ramach analizy AI.
3. Sprawdź kandydata parserem YAML, kodekiem, walidatorem read-modelu
   i regułami pól objętych zmianą. Jawnie rozlicz wykryte niezgodności;
   poprawny odczyt nie potwierdza zgodności całej karty z maintenance.
4. Zapisz wyłącznie uzgodnioną zmianę. Porównaj dokumenty przed i po,
   zachowanie pozostałych pól, relacji, ownershipu i granic code search.
   Nie normalizuj pozostałych danych przy okazji.
5. Operator ponownie uruchamia aplikację. Potwierdź aktualny odczyt HTTP
   i `opctx_*`, a następnie odśwież UI i sprawdź wynik. Weryfikacja
   katalogu nie wymaga płatnego uruchomienia AI.

Usunięcie pytania decyzją operatora nie rozstrzyga brakującego faktu i nie
ustanawia definicji ani relacji. Zero Open Questions oznacza brak
zarejestrowanych aktywnych zadań, nie dowód kompletności wiedzy.

## Scenariusze ręcznej weryfikacji

- Na pustym katalogu opisz wdrażany system i wybierz projekt GitLab. Sprawdź,
  czy operator może uzyskać rozpoznawalny system i drogę system → kod bez
  edycji YAML, a sugestia ownershipu nie staje się potwierdzonym faktem.
- Dla biblioteki sprawdź propozycję repozytorium bez sztucznego systemu;
  przy wskazanych konsumentach sprawdź powiązanie z ich systemowymi scope'ami
  bez zmiany primary repozytorium.
- Wznów niedokończoną analizę z historii, popraw pole ręcznie, uruchom
  `batch/preview` i zapisz. Powtórz ścieżkę dla istniejącej encji oraz
  findingu lub otwartego pytania. Potwierdź, że ponowny zapis rozstrzygniętego
  runu jest niedostępny.
- Sprawdź osobno brak pliku źródłowego, obcy URL, niepoprawną referencję,
  zmianę katalogu między podglądem a zapisem i propozycję `CREATE`, do której
  odwołuje się wcześniejsza mutacja. Błąd nie może zmienić katalogu.

Te scenariusze są pomocą przy odbiorze i regresji. Testy automatyczne nie
zastępują sprawdzenia z operatorem, czy propozycje są dla niego zrozumiałe i
przydatne.
