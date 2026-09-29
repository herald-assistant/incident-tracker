# UI Explorer i UX Inspector: analiza wedlug wybranego brancha

Status: zatwierdzona do realizacji.

## Problem i odbiorcy

Operator wybiera frontend, branch i widok przed analiza. Katalog widokow moze
pozostawac w cache, a branch w repozytorium moze przesunac sie po jego
pobraniu. Obecnie UI Explorer i UX Inspector porownuja rewizje katalogu z
rewizja odczytana przy starcie, wiec analiza bywa blokowana i wymaga
odswiezenia katalogu. To utrudnia prace, gdy intencja operatora dotyczy
aktualnie dostepnego kodu na wybranym branchu.

## Oczekiwany rezultat

- Wybor aplikacji, brancha i widoku wystarcza do rozpoczecia analizy, jesli
  widok mozna odnalezc w zrodle dostepnym dla tego brancha.
- Initial run, narzedzia AI i follow-up odczytuja wybrany branch z dozwolonym
  cache, bez przypinania sesji do SHA commita.
- Przesuniecie brancha nie powoduje konfliktu tylko dlatego, ze katalog
  widokow powstal wczesniej. Brak widoku lub kodu w aktualnie odczytanym
  zrodle jest pokazany jako rzeczywista luka albo blad wyboru.
- Operator widzi zrozumiala informacje o czasie zebrania katalogu. Metadane
  wyniku nie sugeruja, ze wszystkie odczyty pochodza z jednego immutable
  commita, jesli aplikacja tego nie gwarantuje.
- Nad polem `View` w UI Explorerze i UX Inspectorze operator widzi `Data:`
  z data i godzina utworzenia katalogu, w tym samym formacie i ukladzie co
  data nad polem `Endpoint` we Flow Explorerze. Ponowny odczyt z cache
  zachowuje pierwotna date katalogu.
- Interfejs obu feature'ow nie pokazuje SHA ani commit id przy wyborze widoku,
  w stanie runu, podgladzie wyniku ani nazwie pobieranego raportu.
- Bezpieczenstwo odczytu pozostaje ograniczone do wybranej aplikacji,
  skonfigurowanego repozytorium i dozwolonych sciezek.

## Kryteria sukcesu

1. Katalog pobrany przed przesunieciem brancha nie blokuje startu z powodu
   roznicy SHA; analiza szuka wybranego widoku na wybranym branchu.
2. Kazdy nowy odczyt w initial run i follow-up uzywa brancha jako refa i moze
   skorzystac z cache, bez ukrytego przepisania na SHA.
3. Nieobecny widok, brak refa, niedostepne zrodlo i limity discovery sa
   raportowane jawnie. System nie wybiera innego widoku ani brancha.
4. Czas katalogu jest prezentowany jako informacja o jego wieku, bez obietnicy
   swiezosci wszystkich plikow lub spojnosci commitow w calej analizie.
5. UI Explorer i UX Inspector nie prezentuja SHA ani commit id. Etykieta
   `Data:` nad `View` ma format daty Flow Explorera i pochodzi z timestampu
   katalogu, ktory nie zmienia sie przy cache hit.
6. Nowe runy i eksporty maja jeden kontrakt branchowy w wersji `1`. Stare
   formaty, lokalne runy i kontynuacje nie wymagaja migracji ani odtwarzania;
   ich lokalne dane i artefakty sa usuwane.

## Ograniczenia i non-goals

- Cache moze oddac dane starsze od aktualnego HEAD brancha. Potrzeba nie wymaga
  odczytu live przy kazdym wywolaniu ani atomowego snapshotu repozytorium.
- Capture UX Inspectora nadal jest obserwacja runtime z chwili wyboru elementu;
  nie dowodzi, ze aktualny kod brancha odpowiada tej samej wdrozonej wersji UI.
- Nie zmieniaja sie pytania analityczne, sekcje raportu ani reguly dostepu do
  innych repozytoriow.
- Nie zachowujemy kompatybilnosci wstecznej publicznego start requestu,
  importu/eksportu, zapisanych lokalnie runow ani follow-up starych sesji.

Plan wykonania: [Wspolna strategia branchowa UI Explorera i UX Inspectora](../plans/frontend-analysis-branch-source-strategy.md).
