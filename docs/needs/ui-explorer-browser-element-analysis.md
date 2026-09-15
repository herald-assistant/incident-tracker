# UI Explorer - pytania o element uruchomionej strony

Status: draft

## Potrzeba

Uzytkownik pracujacy w uruchomionej aplikacji widzi konkretny przycisk, pole
formularza, wartosc, sekcje albo komunikat i chce szybko zrozumiec jego
zachowanie. Nie zna route'a katalogowego, nazwy komponentu, sciezki pliku,
powiazanych serwisow ani sposobu poruszania sie po repository.

Obecny UI Explorer rozpoczyna analize od wybranego systemu, refa i ekranu oraz
tworzy dokumentacje calego widoku. To dobre wejscie do poznania ekranu, ale
jest zbyt szerokie dla pytan wynikajacych z obserwacji jednego elementu, np.:

- „Jakie warunki walidacyjne ma to pole formularza?”,
- „Skad biora sie dane wyswietlane w tym miejscu?”,
- „Dlaczego ten przycisk jest wyszarzony i zablokowany?”,
- „Co stanie sie po uruchomieniu tej akcji?”.

Uzytkownik potrzebuje wskazac element bezposrednio na aktualnie ogladanej
stronie, zadac pytanie naturalnym jezykiem i otrzymac w Team Delivery
Workspace odpowiedz ugruntowana w kodzie. Rozwiazanie nie moze wymagac
instalacji rozszerzenia przegladarki, poniewaz czesc organizacji blokuje
rozszerzenia politykami stacji roboczych.

## Uzytkownicy i moment uzycia

Glownym uzytkownikiem jest analityk biznesowo-systemowy pracujacy na srodowisku
testowym albo innym kontrolowanym srodowisku aplikacji. Z tego samego wejscia
moze korzystac tester, developer, product owner lub osoba utrzymujaca system.

Potrzeba pojawia sie, gdy uzytkownik:

- ma przed soba konkretny stan uruchomionej strony,
- potrafi wskazac element wizualnie, ale nie zna jego implementacji,
- potrzebuje odpowiedzi o warunku, danych, stanie albo skutku akcji,
- chce zachowac wynik jako zwykla analize UI Explorer w platformie.

## Oczekiwane doswiadczenie

1. Uzytkownik jednorazowo dodaje do zakladek przygotowany przez TDW maly
   launcher, ktory przy jawnym uruchomieniu pobiera publiczny statyczny runtime
   z przypietego originu TDW. Alternatywnie zapisuje pelny, samowystarczalny kod
   jako DevTools Snippet. Nie instaluje rozszerzenia i nie nadaje stalego
   dostepu do odwiedzanych originow.
2. Na badanej stronie uruchamia launcher. Od razu wlacza sie sledzenie myszy i
   efektowne podswietlenie elementu w granatowo-niebieskiej palecie TDW.
3. Klikniecie zatrzymuje element, nie wykonuje jego zwyklej akcji i otwiera
   zaufany ekran capture w TDW.
4. Ekran TDW pokazuje zrozumiale podsumowanie i zredagowany payload. Dopiero
   tam uzytkownik podaje pytanie lub polecenie, wybiera model AI i zgodny z
   nim `reasoningEffort`.
5. Po zatwierdzeniu TDW przyjmuje asynchroniczny job, pokazuje identyfikator i
   zapisuje run w `Analysis History`. Wynik, evidence, usage i ograniczenia sa
   prezentowane w platformie, nie na badanej stronie.
6. `Escape`, ponowne uruchomienie launchera albo anulowanie usuwa overlay i
   listenery bez zmiany stanu biznesowego strony.
7. Jezeli przegladarka blokuje popup lub odcina `window.opener`, uzytkownik
   moze skopiowac zredagowany capture i wkleic go recznie na ekranie TDW.

`Ctrl` + `Alt` nie jest uzywany w pierwszym wydaniu. Jawne uruchomienie
bookmarkleta lub snippetu eliminuje konflikt z `AltGr` i przypadkowe
nasluchiwanie na wszystkich stronach.

## Jednostka analizy

Jednostka tego trybu jest **pytanie o wskazany element w konkretnym stanie
strony i przypietej rewizji zrodla**. Obejmuje:

- pytanie lub polecenie uzytkownika,
- wskazany element i jego obserwowalny stan DOM,
- semantyczna sciezke przodkow od elementu do root dokumentu,
- kontekst strony i routingu potrzebny do rozpoznania ekranu,
- zweryfikowany albo jawnie niezweryfikowany zwiazek uruchomionej strony z
  przypieta rewizja kodu.

Sciezka DOM nie jest automatycznie logicznym stosem komponentow frameworka.
Wynik musi rozdzielac obserwowany element i jego przodkow od potwierdzonego
powiazania z komponentem, template'em, handlerem, stanem i usluga w kodzie.

## Oczekiwany kontekst

Capture powinien byc wystarczajacy do znalezienia wlasciwego miejsca w kodzie,
ale nie moze byc kopia calej strony. Obejmuje:

- typ, role, dostepna nazwe, ograniczony tekst, stabilne identyfikatory i
  obserwowalny stan elementu,
- ograniczony lancuch przodkow wraz z informacja o kompakcji, Shadow DOM i
  ramce,
- origin, znormalizowana trase, tytul, jezyk i nazwy parametrow query,
- rozroznienie `disabled`, `readonly`, `required`, `invalid`, `checked`,
  `expanded` i `hidden`, gdy sa obserwowalne,
- jawne informacje o obcieciu, redakcji albo niedostepnej granicy.

Capture nie obejmuje hasel, tokenow, cookies, zawartosci `localStorage` ani
`sessionStorage`, pelnego DOM, wartosci pol formularza, historii sieciowej,
schowka ani automatycznego zrzutu ekranu.

## Oczekiwany wynik

Wynik ma przede wszystkim odpowiedziec na zadane pytanie. Powinien zawierac:

- bezposrednia odpowiedz jezykiem uzytkownika,
- warunki, walidacje, stan i warianty majace znaczenie dla elementu,
- droge od elementu/template'u przez handler, stan lub serwis do operacji
  backendowej, jezeli jest widoczna,
- source references do potwierdzonego materialu,
- poziom pewnosci osobno dla obserwacji runtime i powiazania ze zrodlem,
- visibility limits i otwarte pytania dla zachowan zaleznych od danych,
  backendu, uprawnien albo niezweryfikowanej rewizji.

Warunek widocznosci albo `disabled` w przegladarce nie moze byc przedstawiony
jako dowod backendowej autoryzacji. Tekst strony i atrybuty DOM sa niezaufanym
evidence, a nie instrukcjami dla AI.

## Zakres pierwszego wydania

- Chrome desktop i zwykle strony `http`/`https`, na ktorych polityka
  przegladarki pozwala uruchomic bookmarklet albo DevTools Snippet.
- Maly bookmarklet ladujacy wersjonowane zasoby statyczne z dokladnego originu
  TDW oraz funkcjonalnie rownowazny, samowystarczalny snippet dla developerow.
- Jeden wskazany element w top-level dokumencie; otwarte Shadow DOM jest
  obserwowane w zakresie dostepnym dla skryptu.
- Efemeryczny runtime bez magazynu ustawien, stalego dostepu do originu i
  sekretow TDW.
- Zaufany ekran TDW do podgladu, pytania, modelu, `reasoningEffort` i startu
  joba.
- Reczny transfer zredagowanego JSON jako fallback transportu.
- Automatyczne mapowanie strony do zarejestrowanego frontendu i przypietej
  rewizji kodu po stronie backendu.
- Obecna rodzina Angular/Nx wspierana przez UI Explorer; kontrakt capture
  pozostaje framework-neutralny.

„Dziala na dowolnej stronie” oznacza dostepnosc selektora na zwyklej stronie
`http`/`https`, o ile browser i polityka organizacji dopuszczaja wykonanie
bookmarkleta/snippetu. Analiza kodu moze wystartowac tylko dla strony, ktora
backend jednoznacznie mapuje do zarejestrowanego frontendu i kontrolowanego
repository scope. Brak mapowania jest jawnym stanem blokujacym.

## Kryteria sukcesu

- Uzytkownik uruchamia selektor bez rozszerzenia i bez znajomosci Angulara,
  GitLaba, sciezek plikow ani katalogu ekranow.
- Sam hover nie wykonuje requestu do TDW i nie zmienia stanu biznesowego.
- Klikniecie wyboru nie uruchamia akcji badanego elementu i tworzy dokladnie
  jeden capture.
- Obca strona nie otrzymuje tokenu, cookie TDW, listy modeli ani klienta REST.
- Adres bookmarkleta pozostaje wyraznie ponizej 2 KiB i nie osadza pelnego
  runtime.
- Ekran TDW waliduje origin, `event.source`, nonce, schemat i limit payloadu,
  a przed startem pokazuje dane operatorowi.
- Formularz korzysta z aktualnego katalogu modeli i dopuszczalnych
  `reasoningEffort` z backendu, gdy podlaczony zostanie produkcyjny transport.
- Backend rozstrzyga system, ref, immutable source revision i ekran albo
  zwraca zrozumialy powod blokady.
- Run pojawia sie w `Analysis History` juz jako `QUEUED`.
- Payload, logi, historia i export nie zawieraja sekretow ani surowych
  wartosci formularza.
- Zamkniecie albo blad usuwa runtime z badanej strony bez przeladowania.

## Non-goals pierwszego wydania

- Rozszerzenie Chrome, publikacja w Chrome Web Store, instalator binarny lub
  obchodzenie polityk organizacji blokujacych wykonywanie skryptow.
- Odpowiadanie przez AI na badanej stronie albo polling wyniku przez runtime
  bookmarkleta.
- Wykonywanie akcji biznesowych, automatyczne wypelnianie formularzy,
  modyfikowanie kodu albo obchodzenie autoryzacji badanego systemu.
- Nagrywanie sesji, przechwytywanie requestow sieciowych, cookies, storage,
  schowka, mikrofonu albo kamery.
- Screenshot i analiza pixel-perfect.
- Zaleznosc od `window.ng`, Angular DevTools, source maps albo debug buildu.
- Pelne wsparcie `chrome://`, Chrome Web Store, PDF viewer, `file://`,
  cross-origin iframe i stron blokujacych `javascript:` lub DevTools.
- Zgadywanie repository albo rewizji z nazwy hosta lub tekstu strony.
- Zastapienie obecnej screen-centered dokumentacji widoku.

## Ograniczenia i ryzyka

- Bookmarklet i snippet dzialaja w main world badanej strony. Strona moze
  obserwowac, zmodyfikowac albo zaklocic runtime; dlatego nie wolno umieszczac
  w nim sekretow ani ufnie traktowac capture po stronie backendu.
- CSP, Chrome Local Network Access, polityki enterprise, blokada `javascript:`
  lub wylaczone DevTools moga uniemozliwic jedna albo obie metody uruchomienia.
- Popup blocker oraz Cross-Origin-Opener-Policy moga przerwac automatyczny
  `postMessage`; wymagany jest jawny fallback copy/paste.
- DOM, Shadow DOM, portale, overlaye i iframe nie zawsze odzwierciedlaja
  logiczne ownership komponentu. Niejednoznacznosc musi pozostac widoczna.
- Origin i route nie dowodza wersji wdrozonego artefaktu. Brak wiarygodnego
  build revision obniza confidence i tworzy visibility limit.
- Nawet ograniczony tekst, route i atrybuty moga zawierac dane klienta.
  Minimalizacja, redakcja, limity rozmiaru i ponowna walidacja backendowa sa
  wymaganiem.
- Pobranie runtime z lokalnego TDW moze zostac zablokowane przez CSP albo
  wymagac zgody Chrome na Local Network Access. DevTools Snippet pozostaje
  pelnym fallbackiem uruchomieniowym bez pobierania zewnetrznego skryptu.

## Decyzje produktowe do zatwierdzenia przed backendem

- Jak origin i route sa mapowane na `systemId` i czy deployment publikuje
  wiarygodny identyfikator commita.
- Czy niejednoznaczny ekran zawsze blokuje start joba.
- Jak dlugo przechowywany jest zredagowany capture w historii i exporcie.
- Czy obok bookmarkleta i snippetu organizacja chce udostepnic wariant
  userscriptu dla zarzadzanych srodowisk, jezeli polityka na to pozwala.
