# Uzupelnianie starszych raportow CSV o czas z Jira

Skrypt [enrich-assessment-csv-time-tracking.mjs](../tools/enrich-assessment-csv-time-tracking.mjs)
uzupelnia starsze biznesowe eksporty obu assessmentow o aktualny snapshot:

- `timeSpentSeconds` z Jira `timespent`,
- `originalEstimateSeconds` z Jira `timeoriginalestimate`,
- `remainingEstimateSeconds` z Jira `timeestimate`,
- `timeTrackingCapturedAt` z czasu wykonania skryptu.

Wymagany jest Node.js 18 lub nowszy. Narzedzie nie wymaga `npm install`.

## Uruchomienie w Windows PowerShell

Przejdz do katalogu zawierajacego wczesniejsze eksporty `.csv`, ustaw adres
Jira i personal access token, a nastepnie uruchom skrypt przez jego pelna
sciezke:

```powershell
cd C:\raporty\delivery-complexity
$env:JIRA_BASE_URL = "https://jira.example.com"
$env:JIRA_TOKEN = "jira-personal-access-token"
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs"
Remove-Item Env:JIRA_TOKEN
```

Alternatywnie skrypt rozpoznaje nazwy zgodne z konfiguracja Spring:
`ANALYSIS_JIRA_BASE_URL` i `ANALYSIS_JIRA_TOKEN`. Krotsze zmienne `JIRA_*` maja
pierwszenstwo, gdy ustawione sa oba warianty.

Domyslnie zrodla pozostaja bez zmian. Wzbogacone pliki o tych samych nazwach
powstaja w:

```text
C:\raporty\delivery-complexity\_enriched-time-tracking\
```

Skrypt skanuje tylko bezposrednie pliki `.csv` z aktualnego katalogu, bez
podkatalogow. Rozpoznaje biznesowy eksport po wspolnych kolumnach kontraktu i
odrzuca przypadkowe CSV. `issueKey` jest deduplikowany pomiedzy wszystkimi
plikami, dlatego jedno issue powoduje jedno pobranie z Jira.

## Przydatne tryby

Najpierw zweryfikuj pliki i dostep do Jira bez zapisu:

```powershell
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs" --dry-run
```

Ponownie zbuduj istniejacy katalog wynikowy:

```powershell
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs" --overwrite
```

Jawnie zmien pliki zrodlowe. Przed zapisem kazdy plik otrzyma kopie
`nazwa.csv.bak-<timestamp>`:

```powershell
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs" --in-place
```

Uruchomienie z innego katalogu albo z mniejsza rownolegloscia:

```powershell
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs" `
  --directory C:\raporty\delivery-complexity `
  --concurrency 3
```

Pelna lista opcji:

```powershell
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs" --help
```

## Wewnetrzny certyfikat Jira

Preferowane rozwiazanie to wskazanie firmowego CA przez `NODE_EXTRA_CA_CERTS`
albo uruchomienie Node z `--use-system-ca`. Gdy srodowisko wymaga swiadomego
wylaczenia weryfikacji certyfikatu, uzyj jawnej opcji:

```powershell
node "C:\sciezka\do\incident-tracker\tools\enrich-assessment-csv-time-tracking.mjs" --insecure
```

Alternatywnie mozna ustawic `$env:JIRA_INSECURE_TLS = "true"`. Tryb jest
ograniczony do zapytan Jira wykonywanych przez ten proces i wypisuje
ostrzezenie. Nie zmienia ustawien systemu ani aplikacji.

## Bledy i bezpieczenstwo danych

- HTTP 429 i bledy 5xx sa ponawiane z opoznieniem.
- HTTP 401/403 przerywa przebieg przed zapisem.
- Jesli nie uda sie pobrac zadnego issue, pliki nie sa zapisywane.
- Jesli tylko niektore issue sa niedostepne, pozostaja dla nich dotychczasowe
  wartosci albo puste komorki, powstaja pozostale wyniki, a proces konczy sie
  kodem `2` i wypisuje liste problemow.
- Brak wartosci Jira pozostaje pusta komorka, a nie zero.
- Token jest przyjmowany tylko przez `JIRA_TOKEN` albo
  `ANALYSIS_JIRA_TOKEN`, nie przez argument CLI.
- Istniejace pliki wynikowe nie sa nadpisywane bez `--overwrite`.

Jezeli certyfikat CA jest dostepny, wskaz go zamiast uzywac `--insecure`:

```powershell
$env:NODE_EXTRA_CA_CERTS = "C:\certyfikaty\corporate-ca.pem"
```

Snapshot odzwierciedla stan Jira w chwili uruchomienia skryptu, a nie stan z
dnia wykonania historycznego assessmentu. Skrypt nie pobiera worklogow i nie
przypisuje czasu do osob.
