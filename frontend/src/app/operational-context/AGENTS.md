# Operational Context Frontend AGENTS

## Rola ekranu

`/operational-context` jest ekranem `Tool Workbench / Operational Context`.
To shared context/catalog capability dla feature'ow i tools, nie osobny
produktowy feature i nie element sekcji `Platform`.

## API

Uzywaj istniejacych shared/operator endpointow
`/api/operational-context/*` do odczytu i recznej edycji. Asysta AI korzysta z
feature-owned `/api/operational-context/assistance/jobs/*`. Ten ekran nie
powinien zmieniac modelu danych ani dodawac incident-specific scope'u.

Operational Context tools sa neutralne `opctx_*` i nie przyjmuja
`correlationId`, `environment`, `gitLabGroup` ani `gitLabBranch` jako
model-facing input. Frontend nie powinien sugerowac inaczej.

## Layout

Ekran powinien pozostac roboczy i gesty informacyjnie:

- kompaktowy status strip,
- zakladki katalogowe,
- Signal Resolver,
- katalogowe listy encji,
- `Validation` jako inbox utrzymaniowy,
- `Open Questions` jako inbox utrzymaniowy,
- przycisk `Uzupelnij z AI` w prawym rogu paska statusu, otwierajacy
  prowadzony review malych propozycji; bez osobnej zakladki asysty,
- prawy detail drawer.

Nie dodawaj lokalnego hero ani `workbench-header`.

## Validation i Open Questions

Maintenance inbox ma priorytetowac:

- severity/status,
- krotki tytul/pytanie,
- entity target,
- source file/path,
- akcje kopiowania maintenance targetu.

Filtry powinny zawężac aktualny inbox bez zmiany API. Kopiowanie targetu ma
dawac operatorowi konkretna sciezke/fakt do poprawy katalogu.

Wpisy z jednoznacznym, writable targetem moga otwierac asyste w trybie
`RESOLVE_FINDING`, przekazujac identyfikator findingu albo pytania oraz typ i
ID encji. Projekcje pozostaja read-only; samo uruchomienie AI nie oznacza
findingu ani pytania jako rozwiazanego.

## Detail drawer

Szczegoly encji pokazuj w prawym drawerze, nie w modalu. Drawer ma stale akcje:

- `Copy`,
- `Open raw`,
- `Close`.

Raw source preview i read model payloads moga byc przewijane w drawerze.
Drawer nie powinien zajmowac calego desktopowego ekranu, chyba ze wymusza to
mobile viewport.

Dla writable encji detail moze otworzyc `IMPROVE_ENTITY` z typem i ID targetu.
Reczny `Edit` pozostaje dostepny.

## Asysta AI i edytor

Empty state otwiera `CREATE_AREA` bez wymagania znajomosci typow encji.
Przycisk `Uzupelnij z AI` otwiera widok asysty (domyslnie `CREATE_AREA`) i
zachowuje widoczny run podczas zmiany sekcji. Uzytkownik podaje opis obszaru
i opcjonalnie jeden projekt GitLab/ref. `IMPROVE_ENTITY` i `RESOLVE_FINDING` musza dostac
konkretny target z detail albo inboxu.

Wybor projektu pokazuje skonfigurowana grupe i unikalne podpowiedzi z
feature-owned `source-options`. To projekty juz opisane w Operational Context,
nie pelna lista z GitLaba. Dla wyboru z katalogu wysylaj wzgledne `project`
w `gitLabSource.project`; reczny wariant przyjmuje pelny URL projektu i wysyla
go w `gitLabSource.projectUrl`. Nie wymagaj od uzytkownika dzielenia URL na
glowna grupe, podgrupe i nazwe projektu. Backend weryfikuje origin i zakres
skonfigurowanej grupy przed startem joba.

Po wskazaniu projektu asysta pobiera jego galezie przez
`source-options/branches` i pokazuje wspolny `app-gitlab-branch-select`
z filtrem, tak jak Flow Explorer. Reczne wpisanie galezi pozostaje
dostepne. Zmiana projektu musi usunac poprzedni ref, zanim pojawia sie opcje
nowego projektu; nie wysylaj refa wybranego dla innego repozytorium.

Przy `CREATE_AREA` z GitLabem pokazuj jeden wybor roli projektu: nieznana,
nowy wdrazany system, niewdrazana biblioteka albo kod istniejacego systemu.
Pokazuj tylko pasujace do roli pytania: nazwe nowego systemu, rozwijana
opcjonalna nazwe uslugi w logach, jeden istniejacy system albo maksymalnie
piec systemow korzystajacych z biblioteki. Opcje systemow pochodza z
aktualnego katalogu ekranu. Odpowiedzi wysylaj jako typowane
`repositoryFacts`, oddzielnie od wolnego opisu; bez GitLaba i w innych
trybach nie wysylaj tego pola. Zmiana roli lub zrodla nie moze pozostawiac
ukrytych odpowiedzi w requestcie.

Panel pokazuje status oraz zwarta liste propozycji. Formularz, zrodla,
ograniczenia i pelny diff rozwija sie na zadanie; szczegoly jednej propozycji
pokazuja zmiany pol `before/after`, podstawe, source refs i pytania.
Wspolny boczny aside pokazuje kroki, bezpieczny przebieg pracy Copilota,
prompt przygotowany przed sesja i usage/koszt. Runy sa w Analysis History;
odtworzony run jest tylko do odczytu, bez ponownego batch decision.
AI widzi pelny katalog i moze doczytac wskazane repozytorium. Operator wybiera
pola w dowolnych propozycjach, pomija propozycje przez odznaczenie wszystkich
jej pol, poprawia proponowane wartosci i potwierdza wymagane fakty. Wartosc
tekstowa ma prosty edytor, struktura edytor JSON zachowujacy typ. Korekta
wybranego pola wymaga osobnego potwierdzenia i nowego podgladu. Tozsamosc
`repository.git` i lista `code-search-scope.repositories` nie podlegaja
korekcie w review, bo sa zwiazane ze zweryfikowanym zrodlem/scope'em.
Operator uruchamia jeden podglad calego zestawu.
Odpowiedz `batch/preview` jest autorytatywna dla wyboru; wstepne podglady
pojedynczych propozycji nie blokuja zapisu. Po poprawnym podgladzie operator
zapisuje caly zestaw jednym `batch/decision`. Nie wysylaj wartosci AI jako
samodzielnego payloadu do maintenance: decyzje wskazuja sciezki z draftu
zachowanego w jobie i opcjonalne poprawki operatora `editedValues`.
Historia zachowuje obie wartosci: oryginalna propozycje AI i zatwierdzona
korekte operatora. Po zapisie odswiez katalog, Validation i Open
Questions jednym odswiezeniem. Po bledzie sieci odczytaj ten sam job, zanim
pozwolisz ponowic zapis. Konflikt aktualnosci zachowuje wybor i wymaga nowego
podgladu.

Reczny edytor encji pozostaje dostepny na biezacej wersji danych. Domyslnie
eksponuje pola podstawowe i rozwija zaawansowane sekcje na
zadanie, ale nadal umozliwia edycje calego kanonicznego kontraktu.
