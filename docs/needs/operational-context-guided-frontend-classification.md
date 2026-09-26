# Prowadzona klasyfikacja frontendu w Operational Context

## Problem

Operator tworzący system i jego repozytorium przez asystę AI może poprawić
`systemSubtype` na `frontend`, ale propozycja repozytorium nie musi zawierać
`repositoryType`. Przegląd pozwala edytować tylko pola zaproponowane przez AI,
więc operator nie może dokończyć spójnego zestawu. Walidacja zwraca kod i
fingerprint bez wyjaśnienia zależności. Po zapisaniu systemu z
`systemSubtype: unknown` ręczny edytor odmawia późniejszej zmiany na
`frontend` ogólnym komunikatem o niepoprawnym kandydacie katalogu.

Źródła kodu mogą wskazywać technologię UI, ale nie są wystarczającym
oświadczeniem o klasyfikacji systemu i jego głównego repozytorium. Operator
nie powinien odgadywać wymaganych pól ani kolejności osobnych zapisów.

## Oczekiwany rezultat

- Operator może jawnie określić rolę nowego systemu przed uruchomieniem AI,
  także wybrać `unknown`.
- Klasyfikacja `frontend` jest stosowana do systemu i jego głównego
  repozytorium jako jedna sprawdzana decyzja operatora. Asysta nie wywodzi
  jej samodzielnie z nazwy repozytorium, frameworka ani układu plików.
- W istniejącym, nierozstrzygniętym przeglądzie operator może uzupełnić
  brakującą, powiązaną klasyfikację repozytorium bez uruchamiania nowego joba
  i bez osobnego, przejściowego zapisu katalogu.
- Walidacja zestawu i ręczny edytor podają zrozumiały powód blokady,
  wskazują wymagane pole oraz powiązaną encję.
- Niepoprawny zestaw nigdy nie jest zapisywany częściowo.

## Mierniki akceptacji

- W scenariuszu CRM jawny wybór `frontend` pozwala zatwierdzić system,
  repozytorium i scope w jednym batchu.
- Scenariusz ze starszym draftem, w którym brak `repositoryType`, można
  dokończyć w przeglądzie po jawnym potwierdzeniu operatora.
- Próba ustawienia `frontend` bez zgodnego głównego repozytorium podaje nazwę
  repozytorium i wymagane `repositoryType`, zamiast samego kodu lub ogólnego
  błędu.
- Gdy operator nie zna klasyfikacji, `unknown` pozostaje poprawnym wynikiem
  bez automatycznego zgadywania przez AI.

## Ograniczenia

- Nie zmieniać definicji `system`, `repository`, code-search scope ani reguł
  kwalifikacji UI Explorera.
- Nie dawać AI prawa do zapisu katalogu i nie osłabiać walidacji całego
  kandydata.
- Zachować odtwarzanie starszych nierozstrzygniętych runów.
- Nie kopiować rzeczywistych danych organizacji ani klienta do testów,
  przykładów i dokumentacji; nowe scenariusze używają fikcyjnej domeny CRM.
