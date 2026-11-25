# Plikowe odpowiedzi OpenAPI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator chce odczytać dokument zwrócony przez narzędzie API w tej samej turze.
Zakres zatwierdzony w rozmowie: PNG/JPEG/WebP/GIF, PDF i DOCX, bez zapisu pliku
do przyszłych tur. Ponowne wywołanie narzędzia wykonuje nowe HTTP.

## Proponowane rozwiązanie

Standardowe response content MIME w YAML, także istniejący wildcard. REST zwraca
typowany plik poza tekstem JSON. Neutralny callback przenosi tekst i binaria;
SDK i MCP mapują je na własne wyniki. PDF jest renderowany do JPEG wszystkich
stron tym samym helperem co ręczne załączniki, niezależnie od modelu.
Obrazy przechodzą jako binaria; DOCX jako tekst i osadzone obrazy odczytane przez
JDK ZIP/StAX (bez zewnętrznych relacji i DTD). Live próba potwierdziła pomijanie
natywnego pliku DOCX w tool result na GPT i Claude. Treść binarna nie trafia do activity,
transkryptu ani eksportu; pozostaje tylko podczas wykonania narzędzia/sesji.

## Zakres

Importer/REST, neutralny callback z binariami, adaptery SDK/MCP, współdzielony
renderer, testy i dokumentacja. L3: nowy delivery mode wyniku narzędzia.

## Non-goals

Trwały zapis plików z API, download UI, automatyczne pobieranie URL z JSON,
upload plików do API, OCR, renderowanie układu DOCX, zmiana auth, retry lub doboru modeli.

## Baseline i conformance delta

Baseline: JSON/text i wildcard (inne binaria jako base64 w JSON), jeden request
HTTP, captured auth, blokada mutacji po OUTCOME_UNKNOWN. Ręczne załączniki PDF
korzystają z feature-owned renderera. Delta: jawne MIME dla wspieranych formatów,
plik jako osobny neutralny wynik i binarny wynik narzędzia. Brak zależności
integracji/tools od feature'a lub Copilota. Jeden bieżący kontrakt V1.

Konsumenci: importer/selekcja/podgląd YAML, REST, katalog callbacków, SDK handler
i capture, MCP, System Assistant (obie ekspozycje), ręczne załączniki PDF.
Pozostałe tools zachowują zwykły wynik tekstowy i swoją policy.

## Ograniczenia i ryzyka

Limity CLI/modelu pozostają. Odczyt DOCX w wyniku narzędzia wymaga live testu.
Błąd przygotowania pliku po wykonanym HTTP nie uprawnia do retry mutacji.
Nazwę normalizujemy; rodzaj pliku ustala Content-Type, bez pobierania URL.
Rollback: cofnięcie rozszerzenia MIME i wyników binarnych; bez migracji storage.

## Kryteria akceptacji

PDF/obraz jest odczytywany po tool call w tej samej turze, także przy drugim
wywołaniu. Nie przybywa zapisanych załączników. SDK/MCP dostają właściwe binaria,
a activity/historia zwykły wynik tekstowy, bez binariów i base64 obrazów.
JSON, tekst, auth i write guard działają.

## Kroki

- [x] Importer i REST: MIME i typowany wynik plikowy; testy statusów, nagłówków,
  wildcard, JSON/text i nazw plików.
- [x] Callback, SDK i MCP: binaria bez base64 w tekstowym wyniku/logach;
  wspólny renderer PDF i testy regresji ręcznych załączników oraz write guard.
- [x] Live PDF/obraz/DOCX z fikcyjnego API, ponowne pobranie, brak storage;
  testy konsumentów i architecture guards, dokumentacja i build backendu.

Dowody w `tdw-data/assistant-attachment-cli-test`:

- `openapi-files-tests.log`: 153 testy, 0 błędów, 3 live pominięte;
  SDK/MCP, auth/write guard, załączniki i architecture guards.
- `openapi-files-live-package.log`: cztery tury GPT-6.1-Terra z fikcyjnym API,
  zmieniony PDF po drugim pobraniu, PNG oraz tekst i obraz z DOCX; brak
  zapisanych załączników i base64 obrazów w snapshotach. Test i package exit 0.
- `openapi-files-http-tests.log`: wildcard, JSON błędu oraz niezadeklarowany
  typ pliku; 3 testy bez błędów.
- `openapi-files-final-package.log`: końcowe testy OpenAPI, bridge SDK, DOCX
  i architecture guards oraz build exit 0; schema refs sprawdzone w OpenAPI
  3.0 i 3.1, wildcard i status/default bez błędów. `git diff --check` exit 0.
