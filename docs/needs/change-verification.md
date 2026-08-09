# Change Verification - Business Need

Status: active

Ostatnio zweryfikowano zakres potrzeby: 2026-08-09. Stan implementacji i
kolejnosc kolejnych zmian naleza do kodu oraz zatwierdzonych dokumentow w
`../plans/`, nie do tego pliku.

## Cel dokumentu

Ten dokument opisuje biznesowa potrzebe i oczekiwany rezultat feature'u
`Change Verification`. Nie opisuje stanu implementacji ani technicznego
rozwiazania. Kazdy nowy plan rozwoju tego feature'a powinien wskazywac ten
plik jako `Source need` i zaczynac od potwierdzenia, ktora czesc potrzeby jest
aktualnie realizowana.

## Kontekst biznesowy

Tribe pracuje w rytmie duzych, korporacyjnych wdrozen. Release'e sa rzadkie,
ale ciezkie: zwykle obejmuja wiele zmian, wiele komponentow wdrozeniowych i
duzy frontend. W takim modelu koszt bledu rosnie pod koniec cyklu, bo
rozbieznosc pomiedzy oczekiwaniem biznesowym, implementacja, testami i
gotowoscia srodowiska wychodzi dopiero na UAT, preprod albo w trakcie
wdrozenia.

Obecny problem nie polega glownie na braku dokumentacji. Problem polega na
tym, ze zespol potrzebuje szybciej odpowiedziec na praktyczne pytania:

- czy zmiana zaimplementowana w MR-ach rzeczywiscie odpowiada temu, co
  opisano w Jira i dolaczonych materialach,
- czy implementacja trzyma sie obowiazujacych instrukcji repozytorium,
  architektury i platformy,
- jak najprosciej sprawdzic zmiane wykonywalnym smoke testem,
- czy test zostawil po sobie dane, ktore trzeba bezpiecznie posprzatac,
- gdzie sa rozbieznosci i co nalezy poprawic: kod, story, kryteria akceptacji
  albo zakres testu.

`Change Verification` ma zmniejszyc ryzyko wdrozen przez zamiane nieczytanych
raportow i recznych przegladow na krotki, source-backed wynik oraz opcjonalny
wykonywalny dowod dzialania zmiany.

## Potrzeba uzytkownika

Uzytkownik chce podac link albo key zadania Jira i dostac odpowiedz:

```text
Czy zmiana dowozi to, co obiecano, czy jest zgodna z instrukcjami zespolu
i jak moge ja szybko zweryfikowac na srodowisku?
```

Feature powinien pomagac szczegolnie wtedy, gdy:

- story ma opis celu, kryteria akceptacji albo linki do Confluence,
- MR-y sa podlinkowane w Jira,
- MR-y nie sa podlinkowane, ale w organizacji istnieje konwencja trzymania
  Jira key w tytule MR-a, branchu albo commicie,
- story jest slabe albo puste i trzeba wygenerowac smoke testy z rekonesansu
  kodu,
- zespol chce sprawdzic tylko zgodnosc, bez generowania i uruchamiania testow,
- zespol chce wygenerowac edytowalna kolekcje Postman bez odpalania requestow,
- zespol chce uruchomic zaakceptowany smoke pack i zobaczyc wynik wraz z
  cleanupem albo manualna instrukcja cleanupu.

## Docelowy rezultat

Docelowy rezultat feature'u to nie kolejny dokument opisowy. Rezultatem ma byc
operacyjna odpowiedz skladajaca sie z czterech mozliwych warstw:

1. `Story Compliance` - ocena, czy implementacja odpowiada wymaganiom ze
   story, kryteriom akceptacji i dolaczonym materialom.
2. `Instruction Compliance` - ocena, czy implementacja jest zgodna z
   obowiazujacymi instrukcjami repozytorium, architektury i Copilota.
3. `Smoke Pack` - edytowalna kolekcja Postman oraz zmienne srodowiskowe
   wygenerowane dla zmiany.
4. `Execution Result` - wynik uruchomienia zaakceptowanych testow, readonly DB
   evidence oraz cleanup wykonany przez endpoint albo manualna SQL-ka
   przygotowana dla uzytkownika.

Uzytkownik powinien moc zakonczyc prace po kazdej warstwie. Pelny flow jest
wartosciowy, ale feature nie moze wymuszac kosztownego procesu, gdy potrzebna
jest tylko szybka kontrola zgodnosci albo tylko smoke pack.

## Zakres funkcjonalny

### 1a. Story Compliance

Feature analizuje material ze story:

- tytul i opis zadania Jira,
- cel zmiany,
- kryteria akceptacji,
- komentarze i ustalenia, jezeli sa istotne,
- linki do Confluence albo innych wewnetrznych materialow,
- podlinkowane MR-y,
- MR-y znalezione fallbackiem po Jira key w nazwie MR-a, branchu albo commicie.

Nastepnie porownuje wymagania z implementacja:

- diff MR-ow,
- endpointy,
- DTO i walidacje,
- serwisy i reguly biznesowe,
- mappery,
- FE zmiany,
- migracje,
- testy dodane albo zmienione w ramach MR-a,
- konfiguracje i feature flags, jezeli sa czescia zmiany.

Oczekiwany wynik:

| Requirement / Signal | Implementation Evidence | Assessment | Suggested Action |
| --- | --- | --- | --- |
| Kryterium albo sygnal ze story | Plik, metoda, test, MR albo brak dowodu | Covered / Partially covered / Not found / Ambiguous / Beyond scope | Co poprawic albo potwierdzic |

Feature powinien rozrozniac:

- brak implementacji,
- czesciowe pokrycie,
- implementacje szersza niz story,
- niejednoznacznosc story,
- potencjalna potrzebe korekty story zamiast kodu,
- potrzebe pytania do PO, analityka, tech leada albo ownera systemu.

Slaba albo niepelna dokumentacja nie powinna ograniczac manualnego review do
statusu `Insufficient story`. Po ocenie wymagan zapisanych w Jira i Confluence
AI moze zaproponowac maksymalnie piec dodatkowych kontroli krytycznych dla
release'u i od razu ocenic je wzgledem widocznej implementacji. Takie kontrole:

- musza byc pokazane osobno od `Story Compliance` i `Instruction Compliance`,
- nie sa wymaganiami kontraktowymi ani rekonstrukcja ustnych ustalen z
  refinementu,
- musza wskazywac konkretne sygnaly, powod krytycznosci, status, evidence,
  ryzyko pominiecia, rekomendacje i pewnosc AI,
- nie moga byc ogolna best practice ani sugestia stylistyczna,
- nie zmieniaja werdyktu zgodnosci z materialem zrodlowym.

Brak uzasadnionej dodatkowej kontroli jest poprawnym wynikiem; AI nie powinno
wypelniac limitu na sile.

### 1b. Instruction Compliance

Feature analizuje zgodnosc zmiany z obowiazujacymi instrukcjami. Zrodlem
autorytetu nie ma byc gust AI, tylko pliki instrukcyjne i dokumenty wskazane
przez repozytorium.

Zrodla instrukcji:

- globalny `AGENTS.md`,
- lokalne `AGENTS.md` dla sciezek zmienionych w MR-ach,
- Copilot instructions, np. `.github/copilot-instructions.md`, jezeli istnieja,
- pliki wskazane w Copilot instructions,
- architecture instructions i dokumenty z `docs/architecture`, jezeli sa
  wskazane przez instructions albo wynikaja z lokalnych zasad repo,
- inne jawnie skonfigurowane standardy zespolu.

Instruction Compliance powinno zbudowac `instruction context` dla konkretnej
zmiany:

1. zebrac changed files z MR-ow,
2. dla kazdego changed file znalezc obowiazujace lokalne `AGENTS.md`,
3. dolaczyc globalne instructions,
4. odczytac Copilot instructions i wskazane przez nie pliki,
5. raportowac findings tylko z konkretnym source reference.

Oczekiwany wynik:

| Instruction Source | Rule / Expectation | Evidence | Assessment | Suggested Action |
| --- | --- | --- | --- | --- |
| Plik instrukcji | Zasada, ktora ma znaczenie dla zmiany | Co znaleziono w kodzie | Compliant / Non-compliant / Needs confirmation / Not applicable | Co zmienic albo potwierdzic |

Finding bez zrodla powinien byc traktowany jako sugestia, nie jako naruszenie.

### 2. Smoke Pack

Feature generuje edytowalny smoke pack dla zmiany. Smoke pack powinien byc
mozliwy do przejrzenia, poprawienia i wyeksportowania jako kolekcja Postman.

Gdy story jest dobre, smoke pack powinien wynikac z kryteriow akceptacji.
Gdy story jest puste albo slabe, smoke pack powinien wynikac z rekonesansu
kodu i zmienionych endpointow.

Smoke pack powinien zawierac:

- Postman collection,
- Postman environment albo liste zmiennych,
- requesty happy path,
- podstawowe negatywne przypadki walidacji,
- asercje HTTP status,
- asercje JSON response,
- sekwencje requestow, jezeli flow wymaga kilku krokow,
- placeholdery danych testowych,
- opcjonalne readonly DB assertions jako czesc definicji scenariusza,
- opis zalozen, luk i danych, ktore uzytkownik musi uzupelnic.

Smoke pack nie powinien udawac pelnej regresji. Ma byc szybkim, wykonywalnym
sprawdzeniem najwazniejszych obietnic zmiany.

### 3. Execution And Cleanup

Po akceptacji smoke packa przez uzytkownika platforma moze uruchomic testy na
wskazanym srodowisku.

Execution powinien obejmowac:

- wykonanie zaakceptowanych HTTP requestow,
- walidacje response,
- readonly DB checks, jezeli scenariusz je zawiera,
- cleanup przez jawnie wskazany i dozwolony endpoint,
- readonly DB check po cleanupie,
- raport wyniku z evidence.

Jezeli cleanup endpoint nie istnieje albo cleanup sie nie powiedzie:

- platforma nie wykonuje write SQL na DB,
- platforma pokazuje, jakie dane testowe prawdopodobnie zostaly utworzone,
- platforma generuje manual cleanup SQL jako instrukcje dla uzytkownika,
- wynik zostaje oznaczony jako `Cleanup required`,
- uzytkownik uruchamia SQL samodzielnie swoim kontem i w swoim procesie
  organizacyjnym.

Ta granica jest kluczowa: DB pozostaje readonly dla platformy i AI.

## Tryby uzycia

Feature powinien wspierac kilka trybow, bo rozne sytuacje wymagaja roznej
glebokosci:

| Tryb | Co robi | Kiedy jest przydatny |
| --- | --- | --- |
| `Verify Change` | Story compliance, instruction compliance, smoke pack i opcjonalne execution | Pelna weryfikacja zmiany przed UAT albo release |
| `Check Compliance` | Tylko zgodnosc ze story i/lub instructions | Szybki review MR-a albo gotowosci story |
| `Generate Smoke Pack` | Tylko generowanie kolekcji Postman z Jira/MR/kodu | Story jest puste albo zespol chce tylko artefakt do testu |
| `Run Smoke Pack` | Tylko uruchomienie zaakceptowanego smoke packa | Test zostal juz przygotowany i trzeba go wykonac na srodowisku |

Opcje `Story Compliance` i `Instruction Compliance` powinny byc niezalezne,
zaznaczalne i mozliwe do uruchomienia osobno.

## Uzytkownicy i momenty pracy

Glowni uzytkownicy:

- developer, ktory chce przed review upewnic sie, ze zmiana pokrywa story,
- tech lead, ktory chce szybko zobaczyc rozbieznosci i naruszenia instrukcji,
- tester albo QA, ktory chce dostac startowy smoke pack bez recznego
  odtwarzania endpointow,
- analityk albo PO, ktory chce zobaczyc, czy story jest wystarczajaco jasne i
  czy implementacja nie zmienila zakresu,
- release owner, ktory chce wiedziec, czy kluczowe zmiany maja executable
  verification.

Najwazniejsze momenty uzycia:

- przed merge MR-a,
- przed przekazaniem na UAT,
- przed duzym wdrozeniem,
- po wdrozeniu na srodowisko testowe albo preprod,
- po zmianie story, gdy trzeba sprawdzic, czy implementacja nadal pokrywa
  aktualne oczekiwania.

## Wartosc biznesowa

Feature ma usuwac z codziennej pracy czynnosci, ktore sa kosztowne, powtarzalne
i czesto wykonywane nierowno:

- reczne przeklikiwanie Jiry, MR-ow i Confluence w celu zrozumienia zakresu,
- reczne porownywanie acceptance criteria z kodem,
- reczne sprawdzanie, czy zmiana nie lamie lokalnych zasad architektury,
- reczne skladanie requestow Postmana,
- reczne wymyslanie minimalnych smoke testow,
- reczne szukanie danych, ktore test zostawil po sobie,
- reczne pisanie cleanup SQL w sytuacji awaryjnej.

Oczekiwane efekty:

- mniej rozjazdow wykrywanych dopiero na UAT albo po wdrozeniu,
- szybszy feedback dla developera i analityka,
- wieksza stabilnosc duzych release'ow,
- bardziej powtarzalna jakosc MR-ow,
- szybsze przygotowanie smoke testow,
- budowanie biblioteki uzytecznych smoke packow powiazanych ze zmianami,
- jasniejszy audyt: co sprawdzono, na podstawie jakich zrodel i z jakim
  wynikiem.

## Mierniki sukcesu

Pierwsze mierniki powinny byc praktyczne, nie telemetryczne dla samej
telemetrii:

- czas od podania Jira key do pierwszego raportu zgodnosci,
- liczba wykrytych rozbieznosci story vs implementation,
- liczba findings z konkretnym source reference,
- liczba smoke packow zaakceptowanych bez pisania od zera,
- liczba smoke packow uruchomionych przed UAT albo release,
- procent execution zakonczonych statusem `Passed`,
- liczba execution zakonczonych `Cleanup required`,
- liczba zmian, dla ktorych story bylo `Insufficient story`,
- subiektywna ocena uzytkownika: czy wynik skrocil prace review/testowania.

## Statusy wyniku

Feature powinien komunikowac wynik krotko i operacyjnie:

- `Compliant` - implementacja jest zgodna z wybranym profilem weryfikacji.
- `Discrepancies found` - sa rozbieznosci ze story albo instrukcjami.
- `Insufficient story` - material ze story nie pozwala ocenic zgodnosci.
- `Instructions incomplete` - nie znaleziono wystarczajacych instrukcji albo
  czesc plikow instrukcyjnych jest niedostepna.
- `Smoke pack generated` - smoke pack jest gotowy do edycji albo eksportu.
- `Ready to run` - smoke pack ma wymagane dane i moze zostac uruchomiony.
- `Passed` - testy przeszly i cleanup sie udal.
- `Failed` - testy nie przeszly.
- `Cleanup required` - test zostawil dane wymagajace recznego sprzatniecia.
- `Cannot verify` - brakuje dostepu, danych, MR-ow, endpointow albo
  srodowiska.

## Zasady bezpieczenstwa i zaufania

`Change Verification` musi byc source-backed i kontrolowane przez uzytkownika.

Zasady:

- AI nie powinno oceniac zgodnosci bez pokazania zrodel.
- Findings dotyczace instructions musza wskazywac konkretny plik instrukcji.
- Execution wymaga akceptacji smoke packa przez uzytkownika.
- Platforma moze wykonywac tylko zaakceptowane requesty HTTP.
- Cleanup przez HTTP musi byc jawny i allowlisted.
- DB access dla platformy pozostaje readonly.
- AI ani platforma nie wykonuja write SQL.
- Manual cleanup SQL jest instrukcja dla czlowieka, nie akcja platformy.
- Wynik musi pokazywac ograniczenia widocznosci, np. brak Confluence, brak
  MR-ow, brak dostepu do FE repo, niepewne mapowanie endpointu albo brak danych
  testowych.
- Feature nie powinien automatycznie zmieniac kodu ani story. Moze proponowac
  korekty, rozszerzenia i pytania do potwierdzenia.
- Kontrole zasugerowane przez AI musza byc jawnie oznaczone jako
  niekontraktowe i nie moga byc przedstawiane jako naruszenie story lub
  instrukcji.

## Relacja do pozostalych feature'ow

`Change Verification` uzupelnia obecne feature'y platformy:

- `Incident Analysis` odpowiada: co sie zepsulo i co z tym zrobic.
- `Flow Explorer` odpowiada: jak dziala proces, endpoint albo use case.
- `Change Verification` odpowiada: czy konkretna zmiana dowozi obietnice,
  jest zgodna z instrukcjami i da sie ja potwierdzic wykonywalnym testem.

Wspolna platformowa obietnica pozostaje taka sama: zbieramy kontekst z wielu
zrodel, AI interpretuje go w kontrolowanym kontrakcie, a uzytkownik dostaje
rezultat do pracy, nie tylko streszczenie.

## Granice i non-goals

W pierwszym zakresie `Change Verification` nie powinno byc:

- pelnym systemem test automation,
- pelna regresja release'u,
- automatycznym reviewerem kodu wedlug ogolnego gustu AI,
- generatorem dokumentacji wdrozeniowej,
- narzedziem do samodzielnych zmian w DB,
- narzedziem do automatycznego mergowania albo poprawiania kodu,
- zamiennikiem decyzji PO, analityka, tech leada albo QA,
- globalnym compliance engine dla calej organizacji.

Feature ma zaczac od konkretnej zmiany i dawac szybki, praktyczny wynik.

## Otwarte decyzje produktowe przed kolejnym planem

Przed planem obejmujacym dany zakres trzeba doprecyzowac:

- czy pierwszym wejsciem MVP ma byc tylko Jira key/link, czy takze bezposredni
  MR link,
- czy Confluence jest wymaganym zrodlem MVP, czy opcjonalnym wzbogaceniem,
- jaki minimalny format smoke packa zapisujemy wewnetrznie poza eksportem
  Postmana,
- czy zapisujemy zaakceptowane smoke packi jako biblioteke scenariuszy,
- czy execution ma byc dostepne od razu w MVP, czy dopiero po
  `Check Compliance` i `Generate Smoke Pack`,
- jakie srodowiska wolno wskazac do execution,
- jak uzytkownik dostarcza dane testowe i sekrety auth do requestow,
- jaki poziom source refs jest wymagany w pierwszej wersji,
- czy manual cleanup SQL ma byc tylko tekstem do skopiowania, czy osobnym
  zatwierdzanym artefaktem w wyniku.
