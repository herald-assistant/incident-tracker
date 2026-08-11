# Edycja katalogu Operational Context z UI

## Problem

Operational Context jest curated navigation layer uzywana przez operatora,
tools i feature'y analityczne. Reczna edycja plikow w repozytorium podnosi prog
wejscia, wydluza droge od findingu do poprawki i zwieksza ryzyko uszkodzenia
referencji.

## Oczekiwany rezultat MVP

Operator moze z poziomu UI:

- dodac nowa encje katalogowa,
- edytowac kompletny kanoniczny payload istniejacej encji,
- usunac encje, ktora nie ma blokujacych inbound references,
- zobaczyc walidacje domenowa oraz delete impact,
- po zapisie od razu zobaczyc przeliczone listy, relacje, validation i open
  questions.

`src/main/resources/operational-context` jest tylko seedem. Przy pierwszym
starcie aplikacja kopiuje komplet dokumentow do
`tdw-data/operational-context`; pozniej wszystkie odczyty i mutacje korzystaja
wylacznie z tej lokalnej kopii. Aktualizacja aplikacji nie nadpisuje danych
uzytkownika.

## Zasady produktowe i ograniczenia

- Operational Context pozostaje curated navigation layer, a nie inventory
  kodu, runtime, endpointow, kolejek, tabel ani deploymentow.
- `system` pozostaje kanonicznym bytem katalogowym.
- Jawny ownership moze nalezec tylko do `system` i `bounded-context`.
- Validation i Open Questions sa wyliczonymi maintenance inboxami, nie
  niezaleznymi encjami do usuwania.
- ID istniejacej encji jest immutable; rename pozostaje osobna przyszla
  operacja migracji grafu.
- Delete stosuje `RESTRICT`, pokazuje zaleznosci i nie wykonuje cascade.
- Katalog nie moze przechowywac sekretow, danych produkcyjnych ani prywatnych
  danych kontaktowych.
- MVP jest lokalny i jednoosobowy. Nie ma security/rollout gate, trybow
  classpath/workspace, rewizji, historii, ETag/`If-Match` ani rollbacku.
- Zapis jednej operacji zmienia dokladnie jeden logiczny dokument i podmienia
  go atomowo dopiero po przejsciu walidacji katalogu.

## Kryteria sukcesu

- Create, edit i dozwolony delete nie wymagaja edycji resources ani ponownego
  budowania aplikacji.
- Pierwszy start tworzy kompletna lokalna kopie, a restart zachowuje zmiany.
- Seed z nowszej aplikacji nie nadpisuje istniejacej lokalnej kopii.
- Niepoprawny candidate nie zmienia pliku i zwraca problemy do poprawy.
- Przerwany zapis nie pozostawia czesciowego dokumentu.
- Delete referencjonowanej encji pokazuje blokujace zaleznosci.
- Po zapisie UI, tools i nowe wykonania feature'ow czytaja ten sam biezacy
  katalog.
- Wszystkie nowe testy i fixture'y sa mocno zanonimizowane; przyklady domenowe
  dotycza wylacznie CRM.

## Non-goals MVP

- Security boundary dla wystawionej sieciowo aplikacji.
- Wieloinstancyjny lub wspoldzielony magazyn i ochrona przed lost update.
- Historia zmian, rewizje, manifesty, rollback albo wbudowany backup.
- Edytor dowolnego surowego YAML/Markdown jako glowny UX.
- Cascade delete lub automatyczny rename wszystkich referencji.
- Organizacyjny approval workflow i automatyczne merge requesty.

## Ryzyka i swiadome trade-offy

- Rownoczesna edycja z kilku kart ma semantyke last-write-wins.
- Backup i odzyskanie poprzedniego stanu sa odpowiedzialnoscia uzytkownika,
  np. przez skopiowanie `tdw-data/operational-context`.
- Aplikacja nie zapewnia ochrony write API po wystawieniu poza lokalne,
  zaufane srodowisko; taki deployment wymaga osobnej potrzeby.

## Przyszle rozszerzenia

- wspoldzielony katalog oparty o Git albo baze danych,
- role, security, audyt autora i review zmian,
- import, eksport, backup i porownanie kopii katalogu,
- optimistic concurrency i historia zmian,
- jawny rename z kontrolowana migracja referencji.
