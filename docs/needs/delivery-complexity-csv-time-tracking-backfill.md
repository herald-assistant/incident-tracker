# Uzupelnienie starszych CSV o Jira time tracking

Status: approved

## Potrzeba

Starsze biznesowe eksporty Delivery Complexity Assessment oraz Delivery Scope
Complexity nie zawieraja snapshotow czasu, przez co nie moga zasilic nowej
sekcji efektywnosci na ekranie Delivery Complexity Trends.

Operator potrzebuje samodzielnego skryptu Node uruchamianego w katalogu Windows
z wieloma historycznymi CSV. Skrypt ma pobrac aktualne pola time tracking z Jira
po `issueKey` i dodac do kazdego raportu:

- `timeSpentSeconds`,
- `originalEstimateSeconds`,
- `remainingEstimateSeconds`,
- `timeTrackingCapturedAt`.

## Oczekiwane zachowanie

- Oba formaty assessmentow sa obslugiwane tym samym narzedziem.
- Issue powtorzone w wielu plikach jest pobierane z Jira tylko raz.
- CSV zachowuje format Excel: UTF-8 BOM, separator `;`, CRLF i poprawne
  cytowanie komorek.
- Domyslny przebieg nie nadpisuje danych zrodlowych.
- Jawny tryb in-place zawsze tworzy kopie zapasowe.
- Token Jira nie jest przekazywany w argumentach procesu ani logowany.
- Brakujace albo niedostepne issue sa jawnie raportowane i nie sa zamieniane na
  zera.

## Poza zakresem

- rekonstrukcja historycznego time tracking na dzien wykonania assessmentu,
- pobieranie worklogow i przypisywanie czasu do osob,
- zmiana wynikow lub innych kolumn assessmentu,
- automatyczne odczytywanie sekretow z lokalnego workspace aplikacji.
