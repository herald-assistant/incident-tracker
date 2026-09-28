# UX Inspector: kontekst stanu uruchomionego frontendu

## Problem

UX Inspector odczytuje obserwacje wskazanego elementu i kod wybranej rewizji,
ale nie widzi stanu aplikacji, od ktorego czesto zalezy zachowanie elementu.
Gdy operator pyta, dlaczego element jest w konkretnym stanie, analiza moze
odtworzyc regule z kodu, lecz nie potrafi sprawdzic jej przeslanek w badanej
sesji. Odpowiedz pozostaje wtedy ogolna albo zawiera luke widocznosci mimo
tego, ze wartosci byly dostepne na otwartej stronie.

## Odbiorca i wartosc

Operator wskazujacy element na uruchomionym frontendzie chce zrozumiec jego
konkretne zachowanie w chwili obserwacji. Oczekuje odpowiedzi, ktora laczy
warunek potwierdzony w implementacji z istotnymi wartosciami zaobserwowanego
stanu oraz odroznia te dwa rodzaje dowodu.

## Oczekiwany rezultat

- UX Inspector moze wykorzystac zamrozona zawartosc globalnego store'a strony,
  jesli frontend jawnie udostepnia ja przez `globalThis.getStoreState()`.
- Analiza otrzymuje wartosci potrzebne do konkretnego pytania bez dolaczania
  calego, potencjalnie bardzo duzego stanu do poczatkowego kontekstu AI.
- Brak funkcji, brak danych, blad odczytu albo transferu nie wstrzymuje
  analizy. Operator i AI widza, ze dane store'a nie sa dostepne w tej sesji.
- Odpowiedz opisuje oddzielnie ogolna regule z kodu i stan konkretnej sesji,
  bez przedstawiania pojedynczego zrzutu jako uniwersalnego zachowania.
- Dane niedozwolone do przekazania AI, w szczegolnosci credentials i sekrety,
  nie staja sie materialem analizy.

## Miary sukcesu

- Dla pytania, w ktorym kod uzaleznia zachowanie elementu od globalnego stanu,
  UX Inspector potrafi odczytac wlasciwe wartosci i wyjasnic konkretny
  przypadek albo jawnie wskazac brakujacy dowod.
- Wielkosc zrzutu nie powoduje osadzenia calej jego zawartosci w poczatkowym
  prompcie ani nie zmienia odpowiedzi na pytania niezalezne od store'a.
- Przypadki niedostepnosci store'a zachowuja dzisiejsza mozliwosc analizy kodu
  i wskazanego elementu.

## Ograniczenia i non-goals

- Zakres obejmuje UX Inspectora. UI Explorer nadal opisuje uniwersalne
  dzialanie widoku bez danych store'a z pojedynczej sesji.
- Zrzut jest obserwacja jednego momentu, a nie historia zmian, dowodem
  pochodzenia danych ani potwierdzeniem zgodnosci uruchomionej strony z
  wybrana rewizja kodu.
- Nie wymaga sie, aby Browser Tools odkrywalo wewnetrzne instancje NgRx,
  uruchamialo akcje, czytalo storage albo zbieralo ruch sieciowy.
