# Release summary

Status: zrealizowana w zakresie Liquibase; tryb zmian kontraktów API odłożony.

## Problem i użytkownicy

Przed wdrożeniem analityk, developer i osoba koordynująca release potrzebują
szczegółowego opisu tego, co zmieni się pomiędzy dwiema wersjami aplikacji.
Lista commitów ani surowy diff nie wyjaśniają funkcjonalnego znaczenia zmian.
Ręczne zebranie migracji i odnalezienie ich zastosowania w kodzie jest
czasochłonne i sprzyja pomijaniu pojedynczych kolumn, indeksów oraz warunków
wykonania.

## Oczekiwany rezultat

Użytkownik wybiera aplikację oraz dwa branche/refy: stan bazowy i stan
docelowy. Otrzymuje sekcyjny, tabelaryczny raport zmian wdrożeniowych,
zrozumiały dla analityka i wystarczająco szczegółowy dla developera.

Pierwszy zakres obejmuje zmiany bazodanowe wynikające z Liquibase. Raport ma
obejmować wszystkie ustalone zmiany, w tym każdą tabelę, kolumnę, indeks,
ograniczenie i zmianę danych, a nie tylko przykłady albo najważniejsze pozycje.
Każda pozycja zawiera opis techniczny, znaczenie funkcjonalne, zastosowanie
w systemie i uzasadnienie oparte na dostępnych źródłach. Gdy celu nie da się
potwierdzić, raport wskazuje ten brak zamiast wymyślać intencję autora.

Zapowiedziany drugi tryb będzie opisywał zmiany kontraktów API systemu.
Na wyraźne doprecyzowanie użytkownika jego szczegółowego zakresu i rozwiązania
obecnie nie analizujemy. Nie jest częścią bieżącego wdrożenia.

## Ustalenia użytkownika

- Wybór aplikacji ma przypominać selektor Application we Flow Explorerze.
- Dwa osobne selektory służą do wyboru porównywanych branchy/refów.
- Zmiany Liquibase są pobierane deterministycznie z GitLaba przed analizą AI.
- Początkowy kontekst AI zawiera kompletny zebrany materiał zmian oraz
  kompletne encje Operational Context dotyczące wybranego systemu,
  repozytoriów i ich powiązania w zakresach wyszukiwania kodu. Encje nie są
  streszczane; nie zakładamy, że zawierają informacje specyficzne dla Liquibase.
- AI może samodzielnie doczytywać kod oraz wiedzę o pojęciach, repozytoriach,
  systemach, bounded contextach i procesach.
- Nie nakładamy budżetu liczby wywołań narzędzi potrzebnych do przygotowania
  raportu. Raport ma powstać przez dostępne narzędzia `report_`.

## Wartość i kryteria sukcesu

- Użytkownik rozpoznaje kierunek porównania i dokładne wersje źródeł.
- Raport zawiera wszystkie zmienione elementy w obsługiwanym materiale;
  nie zastępuje ich ogólnym zdaniem „dodano pola i indeksy”.
- Dla nowej tabeli opisane są wszystkie jej kolumny i ograniczenia, a dla
  indeksu także kolejność kolumn, unikalność i dostępne warunki/wyrażenia.
- Uzasadnienia funkcjonalne mają źródła albo jawne oznaczenie niepewności.
- Dodania, modyfikacje, usunięcia, zmiana dołączenia migracji i zmiany
  warunków wykonania są rozróżnione.
- Niepełny odczyt, nierozpoznana konstrukcja i brak potwierdzenia funkcjonalnego
  pozostają widoczne; nie są prezentowane jako „brak zmian”.
- Można zapisać wynik w historii, udostępnić raport oraz dopytać o jego treść
  w znanym wzorcu pracy innych analiz.
- Testowy, w całości fikcyjny scenariusz CRM potwierdza pokrycie wszystkich
  oczekiwanych tabel, kolumn, indeksów, zmian danych i ograniczeń widoczności.

## Ograniczenia i non-goals

Porównujemy materiał repozytoryjny. Bez historii wykonania migracji w konkretnej
bazie nie ustalimy, które changesety rzeczywiście zostały już wykonane.
Usunięcie migracji z repozytorium nie oznacza automatycznie usunięcia obiektu
z bazy. Wnioski o kolejności wdrożenia, rollbacku i zgodności ze starszą
aplikacją muszą uwzględniać tę różnicę.

Zakres nie obejmuje wykonywania migracji, połączenia z produkcyjną bazą,
generowania zmian do repozytoriów ani zatwierdzania wdrożenia. Nie obejmuje
obecnie projektowania trybu API, wszystkich możliwych rozszerzeń Liquibase
ani arbitralnie dużego materiału przekraczającego okno dostępnego modelu.
Ograniczenie techniczne ma być jawne; nie wolno po cichu obcinać materiału.

## Założenia do oceny wraz z planem

- Aplikacja oznacza kanoniczny system z Operational Context. W systemie
  wielorepozytoryjnym zakres porównania musi być jawny, z pokryciem per repo;
  nie wolno arbitralnie wybrać pierwszego repozytorium.
- Bieżący formularz wybiera jedną parę nazw refów dla systemu. Niezależne pary
  wersji poszczególnych repozytoriów wymagają osobnego rozszerzenia.
- Nieograniczona liczba wywołań nie usuwa uprawnień do repozytoriów ani
  ograniczeń technicznych dostawców. Błędy, timeout i zbyt duży kontekst są
  widocznymi stanami wykonania.

Wynikowy kontrakt: [runtime Release summary](../architecture/release-summary-runtime-flow.md).
Checklista dostarczenia: [plan Release summary](../plans/release-summary.md).
