# Walidacja propozycji asysty Operational Context przed odpowiedzia i zapisem

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

`batch/preview` moze zwrocic `VALIDATION_FAILED` z samymi wskaznikami
`/payload/relatedTerms/0` i `/payload/relatedTerms/3`. Operator nie widzi,
ktorej propozycji, ktorych identyfikatorow ani jakiej poprawki dotyczy blad.
Ponadto batch waliduje referencje przy kazdej mutacji, zanim zlozy pozostale
propozycje. Odwolanie do terminu tworzonego pozniej w tym samym zestawie moze
wiec zostac nieslusznie uznane za nieistniejace. Model dostaje obecnie wynik
walidacji dopiero po zakonczeniu swojej sesji, wiec nie moze poprawic
wykrytych przez aplikacje bledow przed wyslaniem propozycji operatorowi.

## Baseline i conformance delta (L2)

- Baseline: neutralny `previewAcceptedBatch` sklada mutacje po kolei. Kazdy
  `canonicalPayload` sprawdza referencje wzgledem dotychczasowego snapshotu.
  Wyjatek zachowuje sciezke pola, ale gubi indeks mutacji; ekran wyswietla
  jedynie ogolny `message`. Caly zestaw jest walidowany ponownie po zlozeniu.
  Sesja Copilota ma read-only GitLab tools tylko przy wybranym zrodle;
  parser draftu i wstepne podglady dzialaja dopiero po jej zakonczeniu.
- Delta: referencje do encji `CREATE` nalezacych do tego samego zaakceptowanego
  zestawu sa dopuszczone podczas skladania, a koncowa walidacja nadal musi
  potwierdzic spojny katalog. Blad strukturalny zachowuje indeks mutacji,
  encje i wskaznik pola. Panel pokazuje wartosc pod wskazana pozycja,
  oznacza propozycje i pole oraz pozwala do nich przejsc. Sesja asysty moze
  wywolac ograniczony, read-only validator calego draftu przed koncowym JSON-em,
  takze gdy nie wybrano GitLaba. Po odpowiedzi backend powtarza walidacje.
- Konsumenci: neutralny batch maintenance (asysta i jego testy integracyjne),
  globalny mapper `ApiExceptionHandler`, feature-owned job API, parser,
  prompt/skill, assembler sesji Copilota, model i panel Angulara. Pojedynczy
  create/update oraz pozostale feature'y nie zmieniaja semantyki walidacji.

## Proponowane rozwiazanie

Przed walidacja mutacji zbudowac zbior ID encji tworzonych w tym samym batchu.
Referencje sprawdzac wzgledem snapshotu i tego zbioru, bez dodawania
pomijanych propozycji. Po zlozeniu wszystkich zmian uruchomic istniejaca
walidacje calego katalogu. Dla wyjatku w mutacji prefiksowac wskaznik
`/mutations/{index}` i podac typ/ID encji w komunikacie; zachowac obecny
ksztalt `fieldErrors`, aby globalny mapper mogl go przekazac bez nowego DTO.
Panel mapuje indeks mutacji na zaakceptowane propozycje (pozycje `SKIP` nie
tworza mutacji), odczytuje wskazana wartosc z wybranej lub poprawionej
propozycji i pokazuje np. `Powiazane terminy, pozycja 1: crm-contact-status —
termin nie istnieje`. Nieznany albo niespojny wskaznik pokazuje sie jako
ogolny blad API, bez zgadywania propozycji.

Dodac feature-owned tool `operational_context_assistance_validate_draft`,
dostepny tylko w sesji tej asysty. Callback jest przekazywany do fabryki tools
tej sesji bez globalnego `ToolCallbackProvider`, wiec nie trafia do serwera MCP.
Przyjmie jeden kompletny draft JSON w istniejacym
kontrakcie, bez osobnych mutation commands. Po stronie serwera zastosuje
aktualny parser, przypiety digest, dozwolone source refs i ten sam neutralny
batch preview. Zwraca tylko status oraz ograniczona liste konkretnych bledow
z indeksami propozycji i pol; nie zapisuje katalogu ani nie ujawnia nowych
danych z innych zrodel. Prompt i polski skill poprosza AI o uzycie toola przed
odpowiedzia oraz poprawienie bledow, z limitem dwoch wywolan. Tool musi byc
dostepny rowniez bez projektu GitLab. Backend po koncowej odpowiedzi nadal
sam parsuje i sprawdza caly draft tym samym preflightem: wywolanie toola przez model nie jest dowodem,
ze pozniejszy JSON jest identyczny ani ze wybory operatora pozostana poprawne.

## Zakres

Neutralne skladanie batcha, sciezki bledow w odpowiedzi preview, obsluga
walidacji w panelu asysty, feature-owned read-only tool, prompt i skill,
testy backendu i frontendu oraz dokumentacja aktualnego zachowania.

## Non-goals

Automatyczne tworzenie brakujacych terminow bez podstawy i decyzji operatora,
zmiana zasad pojedynczej edycji encji i zapisywanie
niepoprawnego katalogu.

## Ograniczenia i ryzyka

Odwolanie do encji z pominietej propozycji pozostaje bledem. Zmiana
`selectedPaths` albo reczna poprawka uniewaznia poprzedni podglad i oznaczenia
bledu. Wartosci i komunikaty z danych katalogu musza byc renderowane jako
tekst. Walidacja i publikacja batcha pozostaja atomowe. Tool ma limit
rozmiaru inputu, liczby wywolan i rozmiaru odpowiedzi. Jego scope pochodzi
z ukrytego kontekstu sesji; model nie wybiera digesta, grupy ani praw
zapisu. Blad lub pominiecie toola nie moze ominac walidacji backendowej.

## Kryteria akceptacji

- Odwolanie do terminu tworzonego pozniej w tym samym wybranym batchu przechodzi
  preview i zapis, jesli koncowy katalog jest poprawny.
- Brakujacy termin, w tym termin z pominietej propozycji, blokuje zapis.
  Odpowiedz identyfikuje mutacje i pole, a UI pokazuje konkretne ID oraz
  pozycje w liscie, oznacza karte i umozliwia przejscie do edycji.
- AI moze wyslac draft do walidatora podczas sesji i dostaje bledy nadajace
  sie do poprawienia przed finalna odpowiedzia. Walidator nie wykonuje
  zapisu, jest dostepny bez GitLaba i nie przyjmuje model-facing digesta.
  Koncowy wynik jest ponownie walidowany na backendzie; wybrany przez
  operatora podzbior wymaga osobnego batch preview.
- Bledne wejscie nie zmienia zadnego YAML-a; istniejące pojedyncze operacje
  zachowuja dotychczasowe wskazniki i reguly.
- Testy frontendu, jego build i pelny profil backend-dev przechodza zgodnie z
  repozytoryjna sekwencja dla zmiany kontraktu backend-frontend.

## Kroki

- [x] Dodac testy i poprawic walidacje referencji dla calosci wybranego batcha,
  w tym referencji w przod i do pominietej propozycji; sprawdzic brak mutacji
  katalogu przy bledzie.
- [x] Powiazac bledy strukturalne batcha z mutacja oraz pokazac w panelu
  propozycje, pole i wskazana wartosc; przetestowac mapowanie przy `SKIP`,
  recznej poprawce i zmianie wyboru.
- [x] Dodac ograniczony read-only validator draftu tylko do sesji asysty,
  z tym samym parserem i batch preview; po finalnej odpowiedzi powtorzyc
  preflight na backendzie. Przetestowac bez GitLaba, z poprawnym
  i niepoprawnym draftem, ze zmienionym digestem, po odczycie GitLaba oraz
  bez wywolania toola. Zaktualizowac prompt i runtime skill po polsku.
- [x] Zaktualizowac instrukcje i architekture oraz wykonac weryfikacje
  backend-frontend, `git diff --check` i audyt anonimizacji testow.

Weryfikacja: Angular 588/588, produkcyjny build frontendu, `mvn -q
-Pbackend-dev clean package` (1561 testow backendu, 0 failures, 0 errors,
1 skipped), `git diff --check`. Dodane testy i dokumentacja uzywaja
fikcyjnej domeny CRM. Spring context potwierdza brak walidatora w globalnych
providerach MCP.
