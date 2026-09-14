# Czytelna walidacja wyboru propozycji Operational Context

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Po uruchomieniu podgladu operator nie widzi na zwartej liscie, ktore pola
zostaly pominiete ani ktore wybrane pola nadal wymagaja potwierdzenia. Akcja
zatwierdzajaca pola poza karta propozycji ma niejasny zakres.

## Baseline i conformance delta (L1)

- Baseline: lista pokazuje licznik wybranych pol, ale nie pokazuje brakujacych
  potwierdzen. Szczegoly pokazują checkbox potwierdzenia tylko dla aktywnej
  propozycji. Podglad zestawu moze byc poprawny mimo brakujacego potwierdzenia;
  `canSave()` blokuje wtedy zapis bez wskazania przyczyny. Odznaczenie pola jest
  legalnym pominieciem, a przycisk zatwierdzenia wszystkich pol jest rodzenstwem
  karty propozycji.
- Delta: po sprawdzeniu zestawu panel pokazuje oddzielnie brak potwierdzenia
  wybranego pola i pominięcie pola w liscie oraz w szczegolach. Brak
  potwierdzenia blokuje zapis; celowe pominięcie pozostaje dozwolone. Akcja
  zatwierdzenia pol znajduje sie wewnatrz obramowania swojej propozycji.
- Konsumenci: tylko panel przegladu Operational Context Assistance i jego
  testy. API, zapis decyzji, historia oraz pozostale feature'y bez zmian.

## Proponowane rozwiazanie

Lokalny stan walidacji pojawia sie przy prośbie o podglad i aktualizuje po
kazdej zmianie wyboru lub potwierdzenia. Lista i szczegoly używają tych samych
obliczen. Podsumowanie przy podgladzie prowadzi do pierwszej propozycji
wymagajacej uwagi. Karta listy ma dwie oddzielne akcje bez zagniezdzania
przyciskow: otwarcie szczegolow i zatwierdzenie zwyklych pol.

## Zakres

Frontendowy panel, style, testy interakcji oraz lokalna instrukcja UI.

## Non-goals

Zmiana semantyki pomijania pol, kontraktu batch preview i zapisu katalogu.

## Ograniczenia i ryzyka

Ostrzezenie o pominietym polu nie moze sugerowac, ze jest ono wymagane do
zapisu. Recznie poprawione wartosci nadal wymagaja oddzielnego potwierdzenia.
Zmiana ukladu nie moze tworzyc zagniezdzonych przyciskow ani psuc nawigacji
klawiatura.

## Kryteria akceptacji

- Po `Sprawdź cały zestaw` lista wskazuje propozycje z brakujacymi
  potwierdzeniami i pominietymi polami; aktywny szczegol wskazuje konkretne
  checkboxy oraz przyczyne.
- Operator moze przejsc od podsumowania do pierwszej propozycji wymagajacej
  uwagi. Zmiana checkboxu aktualizuje oznaczenia i uniewaznia stary podglad.
- Celowe pominiecie pozostaje mozliwe, a brak potwierdzenia nadal blokuje
  zapis. Przycisk zatwierdzenia pol jest wizualnie wewnatrz odpowiedniej karty.
- Testy panelu, komplet testow frontendu i build produkcyjny przechodza.

## Kroki

- [x] Dodano lokalne oznaczenia walidacji i nawigacje do pierwszej pozycji;
  test panelu sprawdza brak potwierdzenia, pominiecie i legalny zapis po
  celowym pominieciu.
- [x] Przeniesiono akcje zatwierdzania do karty propozycji; test sprawdza jej
  zakres, polozenie poza przyciskiem otwierajacym szczegoly oraz fokus po
  przejsciu z podsumowania.
- [x] Zaktualizowano instrukcje UI i potrzebę. Weryfikacja: 587/587 testow
  Angulara oraz produkcyjny `npm run build`; diff i anonimizacja sprawdzone.
