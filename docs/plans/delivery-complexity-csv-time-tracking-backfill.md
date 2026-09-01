# Skrypt backfill Jira time tracking dla biznesowych CSV

Status: done

Source need: [Uzupelnienie starszych CSV o Jira time tracking](../needs/delivery-complexity-csv-time-tracking-backfill.md)

## Poziom zmiany

L1 - niezalezne narzedzie operatorskie. Nie zmienia runtime aplikacji ani
kontraktu eksportu; materializuje aktualny kontrakt czterech kolumn w starszych
plikach.

## Kroki

- [x] Dodac bezdependencyjny skrypt Node z kontraktem PowerShell i Bearer PAT.
- [x] Dodac parser/zapis zgodny z biznesowym CSV oraz walidacje assessmentu.
- [x] Deduplikowac issue globalnie, ograniczyc rownoleglosc i obsluzyc retry.
- [x] Dodac bezpieczny output, dry-run, overwrite oraz in-place z backupem.
- [x] Dodac jawny, procesowo ograniczony tryb `--insecure` dla wewnetrznych
  certyfikatow Jira.
- [x] Dodac testy parsera, Jira HTTP, deduplikacji i operacji plikowych.
- [x] Udokumentowac uruchomienie, wynik i ograniczenia.

## Wynik

Skrypt `tools/enrich-assessment-csv-time-tracking.mjs` skanuje biezacy katalog,
pobiera aktualny snapshot trzech pol Jira REST API v2 i zapisuje cztery kolumny
bez modyfikowania pozostalych danych raportu. Domyslnie tworzy podkatalog
`_enriched-time-tracking`; tryb `--in-place` tworzy backup dla kazdego pliku.

Weryfikacja obejmuje wbudowany runner `node:test`, kontrole skladni i wywolanie
pomocy CLI.
