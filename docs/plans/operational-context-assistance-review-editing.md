# Zwarty przeglad i reczna korekta propozycji Operational Context

Status: completed

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Kilka propozycji z wieloma polami, zrodlami i lacznym diffem tworzy bardzo dlugi
ekran. Operator musi przewijac przez formularz i powtorzony wynik, a bledna
wartosc AI moze tylko odrzucic. Potrzeba wprost wymaga poprawienia, przyjecia
lub odrzucenia propozycji przed zapisem.

## Baseline i conformance delta (L1)

- Baseline: wynik joba zawiera typowany draft, per-pole wybor i potwierdzenie,
  jeden batch preview oraz jedna warunkowa publikacje. Decision przyjmuje tylko
  `selectedPaths` i `confirmedPaths`; backend bierze `after` z draftu. Historia
  przechowuje draft i decyzje. UI pokazuje wszystkie pola i drugi raz caly diff
  pod rozbudowanym formularzem.
- Delta: decision przyjmuje opcjonalne `editedValues` tylko dla wybranych pol.
  Backend ogranicza rozmiar i typ, wymaga potwierdzenia poprawki, odrzuca
  override pol tozsamosci zrodla i granic kodu, a koncowa walidacja katalogu
  pozostaje autorytatywna. Podglad i zapis otrzymuja identyczny zestaw wartosci;
  historia zapisuje zatwierdzone poprawki. UI zwija formularz po uruchomieniu,
  pokazuje zwięzla liste propozycji i szczegoly jednej propozycji naraz;
  zrodla, ograniczenia i pelny diff sa dostepne na zadanie.
- Konsumenci: feature-owned API, job, historia Operational Context Assistance,
  serwis Angulara i panel review. Inne feature'y i neutralny batch maintenance
  zachowuja kontrakty.

## Proponowane rozwiazanie

Jedna aktywna propozycja ma edytor pol z prostym tekstem dla wartosci string
i JSON dla struktur. Korekta pozostaje decyzja operatora, a nie dowodem AI.
Serwer buduje z niej ten sam warunkowy batch command, ktory obsluguje obecny
preview i zapis. Zmiana dowolnej wartosci uniewaznia poprzedni preview.
Alternatywa recznego zapisu kazdej encji w istniejacym edytorze rozbilaby
atomowosc zestawu i utrudnila powiazania miedzy nowymi wpisami.

## Zakres

Feature-owned request/decision/history, backendowe kontrole poprawki,
kompaktowy panel review, testy API/job/UI oraz dokumentacja aktualnego flow.

## Non-goals

- Edycja identyfikatora encji albo dodawanie pol, ktorych AI nie zaproponowalo.
- Edycja `repository.git` i `code-search-scope.repositories` w tym review;
  te pola w zestawie sa wiazane ze zweryfikowanym zrodlem i scope'em. Reczny
  edytor katalogu pozostaje dostepny po zapisie.
- Mutacja katalogu bez podgladu, historia wersji lub rollback YAML.

## Ograniczenia i ryzyka

Operator moze poprawic tylko wybrane `after`; `before`, typ encji, ID i sciezka
sa niezmienne. Backend sprawdza rozmiar, typ i potwierdzenie poprawki, a
maintenance waliduje wynikowy katalog i stale `before`. Digest podgladu
odrzuca pozniejsza zmiane wartosci. Rozstrzygnieta historia pozostaje read-only;
nierozstrzygniety przeglad wznawia sie wedlug
[planu wznawialnego przegladu](operational-context-assistance-resumable-review.md).
Rollback kodu przywraca dawny formularz i request; opcjonalne pole jest
kompatybilne wstecz z dotychczasowymi klientami.

## Kryteria akceptacji

- Przy wielu propozycjach domyslny widok pokazuje liste i jedna propozycje,
  bez podwojonego rozwinietego diffu i formularza.
- Operator poprawia tekst lub strukture, widzi oznaczenie poprawki i jej
  rzeczywista wartosc w preview, po zapisie oraz w historii.
- Nie mozna zapisac poprawki bez potwierdzenia i nowego poprawnego podgladu.
- Pola spoza wybranej propozycji, niedozwolone override, zmiana typu,
  wartosc null i stale dane nie sa publikowane.
- Testy feature API/job/Angular, build frontendu i backendu przechodza.

## Kroki

- [x] Dodano kontrakt `editedValues` i kontrole backendu, z testem precondition
  i zapisu faktycznie poprawionej wartosci. Dowod: celowane testy joba i MockMvc.
- [x] Przebudowano panel na zwięzla liste + szczegoly oraz edycje i reset pol;
  test UI sprawdza, ze zmiana uniewaznia preview i wysyla te same decyzje do
  podgladu i zapisu.
- [x] Dostosowano historie, dokumentacje i instrukcje. Test integracyjny
  sprawdza rzeczywisty YAML, test persystencji odtwarza korekte z historii.
  Angular: 577/577 testow, produkcyjny build; backend:
  `mvn -q -Pbackend-dev clean package`; `git diff --check` bez bledow.
