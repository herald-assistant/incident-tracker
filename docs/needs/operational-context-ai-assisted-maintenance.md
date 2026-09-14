# Pomoc AI przy tworzeniu i aktualizacji Operational Context

## Problem

Operational Context jest wartosciowy dopiero wtedy, gdy opisuje rzeczywisty
obszar systemu i wskazuje, gdzie zaczac dalsza analize. Lokalny seed jest pusty.
Obecny ekran pozwala edytowac dziewiec typow encji, ale wymaga od uzytkownika
znajomosci modelu katalogu, kolejnosci tworzenia wpisow, referencji i znaczenia
zaawansowanych pol. To szczegolnie utrudnia pierwszy zapis. Przy aktualizacji
uzytkownik widzi validation findings i open questions, lecz nadal musi sam
przelozyc je na konkretne, bezpieczne zmiany w formularzach.

Problem odczuwa analityk lub operator znajacy swoj obszar biznesowy, ale
nieznajacy architektury platformy ani sposobu dzialania AI. Obecny edytor jest
uzyteczny dla osoby utrzymujacej kontrakt katalogu; nie jest dobrym punktem
startowym dla tej grupy.

## Oczekiwany rezultat

- Uzytkownik zaczyna od rozpoznawalnego zadania: opisania nowego obszaru albo
  poprawienia istniejacego wpisu, findingu czy otwartego pytania.
- Podaje informacje, ktore zna, i wskazuje zrodla, do ktorych ma dostep. Nie
  musi wybierac typu encji ani samodzielnie ukladac referencji.
- Dostaje maly, uzyteczny zestaw propozycji do sprawdzenia, z wyjasnieniem
  wartosci kazdej zmiany dla nastepnej analizy, zrodlami, niepewnoscia i
  ograniczeniami widocznosci. Asysta nie oczekuje odpowiedzi w trakcie runu.
- Moze poprawic, przyjac lub odrzucic poszczegolne propozycje przed zapisem.
  Po zapisie widzi aktualny stan katalogu i pozostale luki.
- Przy sprawdzaniu calego zestawu widzi, ktore pola pominal i ktore wybrane
  pola nadal wymagaja potwierdzenia, bez zgadywania zakresu akcji na liscie.
- Moze przerwac przeglad niezapisanego zestawu, wrocic do niego z historii,
  zobaczyc swoje poprawki i dokonczyc zatwierdzenie bez ponownej analizy AI.
- Gdy zrodla sa niepelne, wynik pozostaje czesciowy. Brak danych jest
  widoczny, a nie zastepowany prawdopodobnie brzmiacym faktem. Jawne
  przypisanie ownera w opisie moze stac sie propozycja do recznego przegladu;
  model nie prosi o kolejne potwierdzenie w rozmowie.

## Kryteria sukcesu

- W tescie z osoba nieznajaca modelu katalogu da sie utworzyc pierwszy
  uzyteczny obszar bez otwierania surowych YAML-i i bez instrukcji o dziewieciu
  typach encji. Obszar wskazuje co najmniej rozpoznawalny system, a gdy
  dostepne jest potwierdzone zrodlo kodu, rowniez droge do repozytorium.
- Ten sam uzytkownik potrafi przejsc od findingu lub otwartego pytania do
  propozycji poprawki i rozumie, co zmieni sie po jej zapisaniu.
- Zadna tresc wygenerowana przez AI nie trafia do katalogu bez jawnego wyboru
  uzytkownika i walidacji. Wpis nie nabywa potwierdzonego ownershipu ani
  klasyfikacji z samej sugestii modelu.
- Przed zapisem kazdego proponowanego pola pochodzacego z zewnetrznego
  zrodla mozna odroznic obserwacje od interpretacji i wskazac zrodlo.
- Blad AI, niedostepne zrodlo lub niepoprawna propozycja nie niszcza lokalnego
  katalogu i pozostawiaja reczna edycje dostepna.
- Przed odpowiedzia AI moze sprawdzic caly draft read-only walidatorem, ktory
  wskazuje brakujaca referencje do konkretnej propozycji i pola. Backend
  ponawia te kontrole po odpowiedzi, a podglad wybranego podzbioru pokazuje
  operatorowi ID brakujacego wpisu przy edytowanym polu. Referencja do encji
  tworzonej pozniej w tym samym zestawie jest prawidlowa.
- Powrot do nierozstrzygnietej analizy przywraca wybor pol, potwierdzenia i
  reczne poprawki; ponowny podglad poprzedza zapis katalogu. Rozstrzygnieta
  analiza nie moze zostac zatwierdzona drugi raz.
- Przy opcjonalnym zrodle GitLab uzytkownik rozumie, jaka grupa jest
  skonfigurowana, skad pochodza podpowiedzi projektow i jaka nazwe projektu
  zostanie wyslana. Ten sam projekt nie pojawia sie wielokrotnie, nawet gdy
  katalog ma kilka wpisow wskazujacych jedno repozytorium.
- Gdy projektu nie ma w podpowiedziach, uzytkownik moze wkleic pelny adres
  projektu GitLab. Nie musi rozkladac go na grupe glowna, podgrupe i nazwe
  projektu; obcy lub niejednoznaczny adres jest odrzucany przed analiza.
- Po wskazaniu projektu AI dostaje ograniczona mape jego katalogow i plikow
  oraz moze zejsc glebiej w tym samym przypietym commicie. Same nazwy nie
  potwierdzaja znaczenia kodu: wnioski o implementacji wymagaja odczytu
  odpowiedniego pliku. Zadanie bez bezpiecznej propozycji zachowuje wynik
  i koszt, ale nie wyglada jak zakonczony przeglad zmian.
- Przy sprawdzaniu integracji lub zaleznosci biblioteki AI moze doczytac inny
  projekt z tej samej skonfigurowanej glownej grupy GitLab. Katalog podaje
  znane projekty jako podpowiedzi, ale nie blokuje projektow jeszcze w nim
  niezapisanych. Odczytane pliki maja rozroznialne projekty i commity w zrodlach.

## Ograniczenia produktowe

- Katalog pozostaje curated navigation layer. Nie staje sie kopia inventory
  kodu, runtime, endpointow, tabel ani konfiguracji.
- `system` pozostaje kanonicznym bytem. Ownership moze byc potwierdzony tylko
  na systemie lub bounded context; brak potwierdzenia pozostaje jawny.
- Uzytkownik wybiera glowny projekt i galaz; powiazane projekty w glownej
  grupie sa dostepne do uzasadnionego doczytania przez AI. Dostep do zrodla nie oznacza, ze
  kazda wywnioskowana z niego relacja jest faktem katalogowym.
- Asysta ma najwyzej jeden jawnie wybrany projekt GitLab jako glowny target;
  inne projekty nie moga go zastapic jako dowodu tozsamosci. Wskazana strona
  Confluence pozostaje poza tym przyrostem.
- Asysta przekazuje do AI pelna tresc wybranych i doczytanych plikow GitLab,
  opis operatora oraz aktywny katalog bez heurystycznej redakcji danych.
  Przygotowany prompt i wynik sa widoczne w jobie oraz lokalnej historii.
  Reguly maintenance nadal okreslaja, jakie fakty nalezy utrwalac w katalogu.
- Reczna edycja oraz Validation i Open Questions pozostaja dostepne rownolegle
  z pomoca AI.

## Non-goals pierwszego przyrostu

- Automatyczne opisanie calej organizacji lub skanowanie wszystkich projektow.
- Samodzielny zapis lub masowa aktualizacja katalogu przez AI.
- Wnioskowanie potwierdzonego ownera, bounded contextu, klasyfikacji frontendu
  albo granic odpowiedzialnosci tylko z nazwy projektu czy frameworka.
- Historia wersji, rollback, wieloosobowy approval workflow i wspoldzielony
  katalog.

## Walidacja z uzytkownikami

Do pomiaru czasu, zrozumienia zmian i liczby odrzuconych sugestii sluza trzy
scenariusze: utworzenie pierwszego obszaru, uzupelnienie istniejacej encji
oraz rozwiazanie findingu lub otwartego pytania. Ich przebieg jest opisany w
kanonicznej dokumentacji ekranu. Pomiar z operatorami pozostaje do wykonania.
