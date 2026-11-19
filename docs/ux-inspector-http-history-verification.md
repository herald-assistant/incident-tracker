# UX Inspector — weryfikacja capture i historii HTTP

Weryfikacja automatyczna calego zakresu zmian: 2026-11-13. Obejmuje capture
przez REST, historie HTTP, natywne typy HttpDebugSnapshotUtils oraz typowany
odczyt ze scislym Jacksonem i Bean Validation. Biezacy kontrakt i granice
opisuje [runtime flow](architecture/ux-inspector-runtime-flow.md).

## Testy i pakiet — 2026-11-13

- `node --test frontend/tests/browser-tools/*.test.mjs`: 25 testow,
  wszystkie zaliczone.
- `npm --prefix frontend test -- --watch=false`: 88 plikow, 713 testow,
  wszystkie zaliczone.
- `npm --prefix frontend run build`: powodzenie; wygenerowany bundle w
  `src/main/resources/static`. Pobranie skonfigurowanych Google Fonts wymagalo
  dostepu do sieci poza sandboxem.
- `mvn -q -Pbackend-dev clean package`: powodzenie; 2210 testow w 402
  zestawach, 0 failures, 0 errors, 3 pominięcia. Pominiete sa dwa testy live
  Copilot context tier i jeden live Release summary.
  `PackageDependencyGuardTest` (4) i `FrontendPageTest` (16) zaliczone.
- Wynikowy JAR zawiera wszystkie 62 aktualne pliki statyczne, identyczne
  bajtowo z bundlem, bez dodatkowych starych zasobow. Browser Tools sa
  identyczne ze zrodlami w `frontend/public/browser-tools`; usunietego
  deserializera capture nie ma w JAR-ze.
- Przeglad dokumentacji objal kontrakt, ownership, transport, zakres formularza
  i ograniczenia pilotow. Usunieto pozostaly plan i need capture oraz ich
  referencje. Linki w zmienionych dokumentach i `git diff --check` sa poprawne.

## Typowany odczyt i walidacja

Odczyt capture i opcjonalnych blokow korzysta z DTO, lokalnego scislego
Jacksona i Bean Validation. Reczna deserializacja capture zostala usunieta;
sanitizer HTTP zachowuje redakcje i bezstratna obsluge natywnych body.
Import, eksport zapisanych runow i continuation uzywaja wspolnego scislego
preflightu capture. Kontrakt JSON v1 i Browser Tools 1.7.0 pozostaja aktualne.

Testy obejmuja:

- missing/null/false/0, scalar coercion, overflow, enum ordinal,
  daty ISO zamiast timestampow, 8 kombinacji dostepnosci blokow opcjonalnych,
  rzeczywisty binding MVC, walidacja natywnych metadanych oraz odrzucenie
  uszkodzonego capture w imporcie, zapisanym eksporcie i continuation.
- niezaleznosc globalnego ObjectMappera: scisly odczyt
  capture rowniez przy globalnie wylaczonym FAIL_ON_UNKNOWN_PROPERTIES.

## Historia HTTP i natywne body

Testy obejmuja m.in. 50 wpisow i pelne dlugie URL-e, niezaleznosc getterow,
retry, timeout, redakcje, prywatne persistence, celowany odczyt body,
precyzje JSON w tekscie, duplikaty kluczy, pojedyncze linie ponad 100 KB,
Unicode i caly zserializowany callback <=8000 bajtow UTF-8. Sprawdzono
rowniez brak dodatkowego ucinania w podgladzie aktywnosci Copilota,
cztery kombinacje dostepnosci store/HTTP i przypiecie follow-up do
pierwotnego runu. Wszystkie nowe scenariusze sa fikcyjnym CRM.

Testy natywnych typow obejmuja File/Blob w korzeniu i zagniezdzonym JSON,
puste pliki, MIME/nazwe/size/lastModified, pelny zakres bajtow 0-255,
UTF-8 z BOM, Unicode, NUL i CRLF, duze dane, JSON z dokladnymi liczbami,
FormData z powtorzonymi nazwami i plikami oraz zamrozenie przed mutacja.
Sprawdzono redakcje tekstu i pol formularza, walidacje Base64 i rozmiarow,
SOURCE_OMITTED, BINARY_READ_FAILED z zachowaniem metadanych, persistence po
restarcie i odczyt Base64 przez rzeczywisty callback w porcjach <=8000 bajtow.
Podglad Angulara zachowuje dane i metadane oraz powod pominięcia body.
Browser Tools 1.7.0; format capture i zapisu nadal v1.

## Pilot HTTP z drugiego originu

Pilot wykonano 2026-11-11, przed rozszerzeniem testow natywnych typow.

Fikcyjna strona `frontend/public/browser-tools/demo/index.html` byla serwowana
na osobnym lokalnym originie. Automatyzacja JSDOM wykonala rzeczywisty
preflight i POST do uruchomionego Spring Boot, a po zamknieciu strony GET
capture po serwerowym ID. Sprawdzono oba profile, store + HTTP oraz HTTP bez
store'a, cztery stany wywolan, body 126000 znakow, redakcje sekretu i
`Cache-Control: no-store`. Wszystkie sprawdzenia zakonczyly sie powodzeniem.
To pilot transportu HTTP i runtime JS; nie byl recznym testem Chrome/CSP.

Wczesniejszy pilot przegladarkowy z 2026-09-29 potwierdzil dwa originy,
oba profile, serwerowe ID, nawigacje, zwijany podglad, redakcje store'a,
odtworzenie po refresh oraz 404 po restarcie przed startem. Automatyzacja
korzystala z testowego otwartego Shadow DOM menu. Nie potwierdzono pelnego
recznego scenariusza od wyboru elementu, przez start analizy z GitLab, do
continuation po restarcie na skonfigurowanym srodowisku. Ponizszy pilot
Copilota potwierdza kontynuacje narzedzi ze snapshotami, ale nie zastepuje
tego pelnego scenariusza UI.

## Pilot narzedzi z rzeczywistym Copilotem

Pilot wykonano 2026-11-11. Obsluge natywnych typow zweryfikowano testami
callbackow i persistence; tego pilota modelowego nie powtarzano.

Pilot wykorzystal normalny runtime Copilot SDK i katalog modeli aplikacji
(wybor `auto`), osobny tymczasowy katalog sesji oraz wylacznie fikcyjne dane
CRM. Dozwolone byly tylko narzedzia snapshotow. Nie czytal rzeczywistych
repozytoriow GitLab ani danych organizacji.

Pierwszy proces JVM wykonal dwa odczyty:

- HTTP: `entryId=101`, `part=responseBody`, `pointer=/customer/permissions`;
- store: `pointer=/contactId`.

Model odczytal `editable=false`, `reason=ARCHIVED` i `contactId=crm-101`.
Po zakonczeniu procesu drugi proces JVM wznowil te sama sesje z zapisanym
runem, udostepniajac tylko HTTP tools. Model wykonal trzy odczyty:

- JSON w tekscie: `entryId=102`, `part=responseBody`, `pointer=/error`;
- duze body: `entryId=101`, `part=responseBody`, `pointer=/notes`, offset 0;
- kolejny fragment tego body przez zwrocony `nextOffset=4000`.

Odczytal `CRM_SAVE_FAILED`, `retryable=false` i potwierdzil `hasMore=true`
dla obu fragmentow. Jest to dowod pracy narzedzi z modelem oraz continuation
po restarcie; target resolution i integracje GitLab pokrywaja testy backendu.

## Przejście od karty w UI do backendu

W próbie z 19 listopada 2026 dwa modele odpowiedziały na to samo pytanie
o źródło danych karty klienta. W obu analizach początkowych narzędzia odczytały
klienta HTTP we frontendzie i implementację endpointu w osobnym projekcie
backendu. Po poprawce resolvera odtworzenie obu zapisanych wyborów na źródłach
z tych analiz wskazało komponent karty i jej tag otwierający. Akcja sąsiedniego
przycisku pozostała przy przycisku.

Oba zapisy miały status historii HTTP `UNAVAILABLE` z powodem `GETTER_MISSING`
i pustą historię chatu. Osobnej próby wymaga pełna ścieżka od zarejestrowanego
żądania do kodu w follow-upie. Do jej weryfikacji wybierz element po wywołaniu
API i sprawdź w podglądzie capture metodę oraz pełny URL. Po analizie zapytaj
w chacie, który system obsługuje to żądanie. Porównaj adres z zapisanym
wzorcem systemu oraz sprawdź projekt, rewizję i kod endpointu w odczytach GitLaba.

## Lokalne dane i wydanie

Inwentaryzacja przy pilocie `tdw-data/index.json` i `runs/**/run.json` nie wykazala
starych runow UX Inspectora wymagajacych usuniecia. Tymczasowe dane pilota
usunieto po weryfikacji. Kontrakt pozostaje w dokumentacji architektury;
zakres i ograniczenia pilotow sa opisane powyzej.

Backend, Angular i Browser Tools nalezy wydac razem. Po aktualizacji trzeba
ponownie dodac bookmarklet i wykonac capture V1. Rollback przywraca cale
poprzednie wydanie i wymaga nowego capture; nie ma migratora danych.
