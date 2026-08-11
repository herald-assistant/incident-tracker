# Lokalne ustawienia Confluence w Workspace Settings

## Problem

Operator moze lokalnie skonfigurowac polaczenie z Jira z poziomu Workspace
Settings, ale nie ma analogicznej mozliwosci dla Confluence. Konfiguracja
Confluence wymaga przez to zmiany bazowej konfiguracji uruchomieniowej, mimo ze
jest uzywana przez ten sam lokalny workspace oraz widoki operatorskie.

## Uzytkownik i wartosc

Uzytkownik lokalnego Team Delivery Workspace powinien moc ustawic podstawowe
dane polaczenia z Confluence w tym samym miejscu i wedlug tych samych zasad co
Jira. Pozwala to przelaczac lokalny workspace miedzy instancjami bez edycji
pliku dystrybucyjnego i bez restartu aplikacji po kazdej zmianie.

## Oczekiwany rezultat

- Workspace Settings pokazuje efektywny adres Confluence i informacje o zrodle
  kazdego ustawienia.
- Uzytkownik moze lokalnie nadpisac adres Confluence i personal access token.
- Token pozostaje polem sekretnym w UI i nie trafia do logow ani eksportow
  analiz.
- Przywrocenie wartosci bazowej usuwa lokalny override.
- Zapisana zmiana jest uzywana przez kolejne odczyty stron Confluence bez
  restartu aplikacji.
- Istniejace lokalne workspace'y bez ustawien Confluence nadal dzialaja.

## Kryteria sukcesu

- Confluence ma w Workspace Settings taki sam model `DEFAULT` / `CUSTOM` jak
  Jira.
- Confluence Source oraz konsumenci stron Confluence uzywaja efektywnych
  ustawien po zapisie.
- Stare ustawienia workspace'u sa odczytywane bez migracji wykonywanej recznie.
- Testy kontraktu API, lokalnego zapisu i UI chronia nowe zachowanie.

## Ograniczenia i ryzyka

- Dozwolony wzorzec adresow stron Confluence pozostaje osobna bramka
  bezpieczenstwa. Lokalny adres bazowy nie moze automatycznie rozszerzac tej
  allowlisty.
- Token jest lokalnym sekretem przechowywanym w prywatnym katalogu workspace'u
  zgodnie z obecnym modelem pozostalych integracji.

## Non-goals

- Zmiana sposobu pobierania stron Confluence lub kontraktu Confluence Source.
- Udostepnienie w UI technicznych limitow odpowiedzi, ustawien SSL albo innych
  zaawansowanych parametrow integracji.
- Automatyczne generowanie lub rozszerzanie dozwolonego wzorca URL.
- Dodanie zdalnego magazynu sekretow albo wielouzytkownikowego modelu
  konfiguracji.
