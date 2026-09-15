# UI Explorer - pytania o element uruchomionej strony

Status: draft

## Potrzeba

Uzytkownik pracujacy w uruchomionej aplikacji widzi konkretny przycisk, pole
formularza, wartosc, sekcje albo komunikat i chce szybko zrozumiec jego
zachowanie. Nie zna jednak route'a katalogowego, nazwy komponentu, sciezki
pliku, powiazanych serwisow ani sposobu poruszania sie po repozytorium.

Obecny UI Explorer rozpoczyna analize od wybranego systemu, refa i ekranu oraz
tworzy dokumentacje calego widoku. To dobre wejscie do poznania ekranu, ale
jest zbyt szerokie dla pytan wynikajacych z obserwacji jednego elementu, np.:

- „Jakie warunki walidacyjne ma to pole formularza?”,
- „Skad biora sie dane wyswietlane w tym miejscu?”,
- „Dlaczego ten przycisk jest wyszarzony i zablokowany?”,
- „Co stanie sie po uruchomieniu tej akcji?”.

Uzytkownik potrzebuje wskazac element bezposrednio na aktualnie ogladanej
stronie, zadac pytanie naturalnym jezykiem i otrzymac w Team Delivery
Workspace odpowiedz ugruntowana w kodzie. Nie powinien recznie tlumaczyc
obserwacji z UI na techniczne identyfikatory zrodel.

## Uzytkownicy i moment uzycia

Glownym uzytkownikiem jest analityk biznesowo-systemowy pracujacy na srodowisku
testowym albo innym kontrolowanym srodowisku aplikacji. Z tego samego wejscia
moze korzystac tester, developer, product owner lub osoba utrzymujaca system.

Potrzeba pojawia sie w chwili, gdy uzytkownik:

- ma przed soba konkretny stan uruchomionej strony,
- potrafi wskazac element wizualnie, ale nie zna jego implementacji,
- potrzebuje odpowiedzi o warunku, danych, stanie albo skutku akcji,
- chce zachowac wynik jako zwykla analize UI Explorer, a nie jednorazowa
  odpowiedz znikajaca wraz z zamknieciem strony.

## Oczekiwane doswiadczenie

1. Na wspieranej stronie uzytkownik przytrzymuje `Ctrl` + `Alt`. Ruch myszy
   wlacza lokalne sledzenie elementu pod kursorem i efektowne podswietlenie w
   granatowo-niebieskiej palecie Team Delivery Workspace.
2. Klikniecie podczas aktywnego wyboru zatrzymuje element, nie wykonuje jego
   normalnej akcji i otwiera modal nad badana strona.
3. Modal pokazuje zrozumiale podsumowanie wyboru i pozwala podac pytanie lub
   polecenie, wybrac model AI oraz zgodny z nim `reasoningEffort`.
4. System sam rozpoznaje zrodlo aplikacji, wersje kodu i ekran albo jawnie
   wyjasnia, dlaczego nie moze ich bezpiecznie rozstrzygnac. Uzytkownik nie
   podaje repository, grupy GitLab, brancha, commita, pliku ani komponentu.
5. Po zatwierdzeniu uzytkownik od razu dostaje informacje, ze job zostal
   przyjety, identyfikator analizy i akcje otwarcia Team Delivery Workspace.
   Run jest od razu widoczny w `Analysis History`.
6. Rozszerzenie nie czeka na wynik i nie prezentuje odpowiedzi AI. Pelny
   przebieg, evidence, usage, wynik i ograniczenia sa dostepne w dedykowanej
   analizie UI Explorer w platformie.
7. Anulowanie, `Escape`, zwolnienie klawiszy przed kliknieciem albo blad
   polaczenia przywracaja strone do poprzedniego stanu bez pozostawienia
   overlayow i listenerow.

## Jednostka analizy

Jednostka tego trybu jest **pytanie o wskazany element w konkretnym stanie
strony i przypietej rewizji zrodla**. Obejmuje:

- pytanie lub polecenie uzytkownika,
- wskazany element i jego stan dostepny w DOM,
- semantyczna sciezke przodkow od elementu do root dokumentu,
- kontekst strony i routingu potrzebny do rozpoznania ekranu,
- zweryfikowany albo jawnie niezweryfikowany zwiazek uruchomionej strony z
  przypieta rewizja kodu.

Sciezka DOM nie jest automatycznie logicznym stosem komponentow frameworka.
Wynik musi rozdzielac obserwowany element i jego przodkow od potwierdzonego
powiazania z komponentem, template'em, handlerem, stanem i usluga w kodzie.

## Oczekiwany kontekst

Kontekst przekazywany do analizy powinien byc wystarczajacy do znalezienia
wlasciwego miejsca w kodzie, ale nie moze byc kopia calej strony. Powinien
obejmowac w szczegolnosci:

- bezpieczna charakterystyke elementu: typ, role, dostepna nazwe, ograniczony
  tekst, stabilne identyfikatory i obserwowalny stan,
- przejscie po przodkach do root wraz z granicami Shadow DOM albo ramki, gdy sa
  widoczne,
- origin, znormalizowana trase, tytul, jezyk i ograniczone sygnaly buildu lub
  zasobow strony,
- informacje pozwalajace odroznic `disabled`, `readonly`, ukrycie, blad
  walidacji i inne obserwowalne stany bez przesylania wartosci pola,
- jawne informacje o obcieciu, redakcji, niedostepnej ramce albo
  niejednoznacznym dopasowaniu.

Kontekst nie obejmuje hasel, tokenow, cookies, zawartosci `localStorage` ani
`sessionStorage`, pelnego DOM, wartosci pol formularza, historii sieciowej ani
automatycznego zrzutu ekranu.

## Oczekiwany wynik

Wynik ma przede wszystkim odpowiedziec na zadane pytanie. Powinien zawierac:

- bezposrednia odpowiedz jezykiem uzytkownika,
- warunki, walidacje, stan i warianty majace znaczenie dla elementu,
- droge od elementu/template'u przez handler, stan lub serwis do operacji
  backendowej, jezeli jest widoczna,
- source references do potwierdzonego materialu,
- poziom pewnosci osobno dla obserwacji runtime i powiazania ze zrodlem,
- visibility limits i otwarte pytania dla zachowan zaleznych od danych,
  backendu, uprawnien, zewnetrznej biblioteki albo niezweryfikowanej rewizji.

Warunek widocznosci albo `disabled` w przegladarce nie moze byc przedstawiony
jako dowod backendowej autoryzacji. Tekst strony i atrybuty DOM sa niezaufanym
evidence, a nie instrukcjami dla AI.

## Zakres pierwszego wydania

- Chrome desktop i zwykle strony `http`/`https`, dla ktorych uzytkownik
  przyznal rozszerzeniu dostep do originu.
- Jeden wskazany element w top-level dokumencie albo wspieranej ramce.
- Automatyczne mapowanie strony do jednego zarejestrowanego frontendu i jednej
  przypietej rewizji kodu.
- Obecna rodzina Angular/Nx wspierana przez UI Explorer; kontrakt capture
  pozostaje framework-neutralny, aby pozniejsze adaptery nie wymagaly zmiany
  rozszerzenia.
- Asynchroniczny job, natychmiastowa historia, report-first wynik, source
  evidence, confidence, visibility limits i usage w istniejacym UX platformy.

„Dziala na dowolnej stronie” oznacza dostepnosc interakcji na zwyklej stronie
`http`/`https` po przyznaniu uprawnienia. Analiza kodu moze wystartowac tylko
dla strony, ktora backend jednoznacznie mapuje do zarejestrowanego frontendu i
kontrolowanego repository scope. Brak mapowania jest jawnym stanem blokujacym,
a nie powodem do zgadywania repozytorium przez AI.

## Kryteria sukcesu

- Uzytkownik uruchamia pytanie o element bez znajomosci Angulara, GitLaba,
  sciezek plikow i katalogu ekranow.
- Sam hover nie wykonuje requestu do TDW, nie pobiera kodu i nie zmienia stanu
  biznesowego strony.
- Klikniecie wyboru nie uruchamia akcji badanego elementu i tworzy dokladnie
  jeden capture.
- Modal korzysta z aktualnego katalogu modeli i dopuszczalnych reasoning
  efforts z backendu; nie utrzymuje lokalnej listy.
- Backend sam rozstrzyga system, ref, immutable source revision i ekran albo
  zwraca zrozumialy powod blokady.
- Run pojawia sie w `Analysis History` juz jako `QUEUED`, a kolejne snapshoty
  aktualizuja ten sam wpis.
- Dla pola, przycisku i wartosci danych wynik odpowiada na pytanie, pokazuje
  potwierdzona sciezke zrodlowa i nie uzupelnia luk runtime domyslem.
- Brak dostepu do strony, TDW, zrodla albo AI daje bezpieczny, naprawialny blad
  bez utraty kontroli nad badana strona.
- Payload, logi, job snapshot, historia i export nie zawieraja zabronionych
  sekretow ani surowych wartosci formularza.

## Non-goals pierwszego wydania

- Odpowiadanie przez AI wewnatrz rozszerzenia albo polling wyniku przez
  rozszerzenie.
- Wykonywanie akcji biznesowych, automatyczne wypelnianie formularzy,
  modyfikowanie kodu albo obchodzenie autoryzacji badanego systemu.
- Nagrywanie sesji, przechwytywanie requestow sieciowych, cookies, storage,
  schowka, mikrofonu albo kamery.
- Screenshot i analiza pixel-perfect; moze to byc osobny, jawnie zatwierdzony
  inkrement po ocenie wartosci i ryzyka danych.
- Zaleznosc od `window.ng`, Angular DevTools, source maps albo debug buildu.
- Pelne wsparcie stron `chrome://`, Chrome Web Store, PDF viewer, `file://`,
  incognito i cross-origin iframe bez dodatkowych uprawnien platformy.
- Zgadywanie repository albo rewizji na podstawie nazwy hosta, tekstu strony
  lub podobienstwa nazw.
- Zastapienie obecnej screen-centered dokumentacji widoku.

## Ograniczenia i ryzyka

- `Ctrl` + `Alt` moze kolidowac z `AltGr`, skrotami systemu i skrotami strony;
  wybor nie moze aktywowac sie dla stanu `AltGraph` i wymaga testow na
  docelowych klawiaturach.
- DOM, Shadow DOM, portale, overlaye i iframe nie zawsze odzwierciedlaja
  logiczne ownership komponentu. Niejednoznacznosc musi pozostac widoczna.
- Origin i route nie dowodza wersji wdrozonego artefaktu. Brak wiarygodnego
  build revision musi obnizac confidence i tworzyc visibility limit.
- Nawet ograniczony tekst, route i atrybuty moga zawierac dane klienta.
  Minimalizacja, redakcja, limity rozmiaru i krotki lifecycle surowego capture
  sa wymaganiem, nie optymalizacja.
- Rozszerzenie dzialajace na wielu originach ma szerokie uprawnienia. Dostep
  powinien byc przyznawany per origin, a nie bezwarunkowo przy instalacji.
- Badana strona jest niezaufana. Nie moze wskazac adresu TDW, endpointu,
  repository scope ani wywolac dowolnego requestu przez service worker.

## Decyzje produktowe do zatwierdzenia

- Czy pierwsze wydanie ma wymagac jawnego nadania dostepu dla kazdego originu,
  zgodnie z rekomendowanym modelem, czy organizacja dopuszcza centralnie
  zarzadzana allowliste hostow.
- Jak strona jest mapowana na `systemId` i ref oraz czy wdrozenie publikuje
  wiarygodny identyfikator commita.
- Jak rozszerzenie jest parowane z operatorska sesja TDW i jak dlugo zyje
  ograniczony credential rozszerzenia.
- Czy top-level dokument i same-origin iframe wystarcza w pierwszym wydaniu.
- Czy portable export pytania elementowego jest wymagany od razu, czy
  wystarcza lokalna historia i copy/download raportu Markdown.
