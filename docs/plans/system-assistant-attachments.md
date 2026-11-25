# Załączniki System Assistanta

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator dołącza obrazy PNG/JPEG/WebP/GIF, PDF i DOCX do rozmowy. Zakres został
ustalony w rozmowie i zatwierdzony poleceniem wykonania minimalnej implementacji.

## Proponowane rozwiązanie

Feature-owned strumieniowy upload `application/octet-stream` zapisuje pliki przed kolejną wiadomością w
`runs/{id}/attachments/{uuid}.{extension}`. Metadane są w lokalnym `storeSnapshot`
tego samego run.json, poza eksportowanym transkryptem. GET rozmowy zwraca listę
metadanych. Każda NEW tura dołącza wszystkie zapisane pliki jako SDK blobs.
Feature-owned adapter SDK przygotowuje załączniki po platformowym prepare.
PDF jest renderowany przez PDFBox do obrazów JPEG wszystkich stron, w pamięci,
jednakowo dla każdego modelu; oryginał pozostaje w runie. Obrazy i DOCX pozostają
natywnymi blobs. Konwersję PDF zatwierdzono w rozmowie jako naprawę pomijania
dokumentów przez ścieżkę GPT z narzędziami w CLI 1.0.86 (także 1.0.103).
Istniejące usuwanie runu usuwa cały katalog. Oba widoki używają istniejącego
slota akcji wspólnego chatu, bez zmiany wspólnego modelu wiadomości.

## Zakres

Upload podczas idle, trwały zapis bez osobnego magazynu tymczasowego, odtworzenie,
ponowne dołączanie i prezentacja nazw. Brak limitów TDW i kontroli kompatybilności
modelu. Dodawanie jest blokowane podczas uploadu/wysyłania/aktywnej tury.

## Non-goals

OCR, konwersja DOCX, download/preview, usuwanie pojedynczego
pliku, eksport binariów i lokalnego katalogu załączników, nowa obsługa powrotu OIDC,
zmiana doboru modeli, runtime skilli i istniejących narzędzi.

## Baseline i conformance delta

L3: nowy delivery mode binariów i trwałe zasoby rozmowy. Baseline: JSON message
API, NEW per tura, bieżąca konfiguracja profilu, brak initial reportu, jeden
operation guard, trwałe przyjęcie przed AI, transkrypt V1 i obecna nawigacja.
Delta: nowy endpoint uploadu, metadane publicznej rozmowy i lokalnego store,
wewnętrzny input AI z binariami; neutralny writer dostaje opcjonalny snapshot.
Pozostaje jeden bieżący V1, bez aliasów, migracji i wersjonowanych ścieżek.
Nowa capability nie zmienia kształtu eksportowanego transkryptu ani starych pól.

Konsumenci: feature API/service/history/AI, oba widoki tej samej strony Angular,
shared history export/delete, writer snapshotów używany przez inne feature'y,
neutralny activity capture SDK. Bez importów sibling feature'ów.

Referencje: Incident Analysis i Flow Explorer używają neutralnego writera oraz
runtime; ich dotychczasowy prompt i sposób dostarczania evidence pozostają.

## Ograniczenia i ryzyka

Limity modelu/CLI pozostają. Pilot natywnych dokumentów bez narzędzi nie dowodzi
działania konfiguracji narzędziowej. PDF jest więc renderowany dla wszystkich
modeli, także gdy profil nie ma operacji; ścieżka nie zależy od listy modeli.
Duże pliki zwiększają pamięć i czas tury. Nieudany upload/zapis może pozostawić
plik w katalogu runu; zostanie usunięty dopiero wraz z rozmową, zgodnie z zakresem.
Rollback konwersji: cofnięcie adaptera PDFBox i jego zależności; zapis oryginałów
i metadanych pozostaje bez zmian. Natywny PDF na GPT z narzędziami ponownie nie
będzie odczytywany. Bez migracji formatów.
Usuwanie danych ogranicza się do usunięcia rozmowy przez istniejące API/historię.

## Kryteria akceptacji

Pliki są trwale zapisane przed wiadomością, wracają po odczycie historii,
docierają do każdej NEW tury z oryginalną nazwą i MIME; eksport ich nie zawiera.
Upload busy/nieznanego runu jest odrzucany. Usunięcie runu usuwa pliki.
Kontrolka działa w ramce i pełnym ekranie; brak tymczasowego storage w FE.

## Kroki

- [x] Naprawa PDF: jeden adapter PDFBox, wszystkie strony jako obrazy dla każdego
  modelu, bez zapisu pochodnych i bez zmiany tools/policy. Testy kolejności stron,
  skanu bez tekstu, innych formatów i błędu PDF; live PDF z runu e877bf6f oraz
  wywołanie narzędzia w tej samej turze, testy feature'a i architecture guards.
  Dowód: `pdf-fix-tests.log` (58 testów feature'a bez błędów, jeden live pominięty,
  4 architecture guards), `production-pdfbox-probe.log` (PDF operatora na
  GPT-5.6-Luna, GPT-6.1-Terra i Claude Haiku 4.5, każde z wywołaniem narzędzia),
  `pdf-fix-live.log` (pełny feature: zeskanowany PDF i OpenAPI w dwóch NEW turach),
  `pdf-local-render-results.json` (lokalny odczyt obu zgłoszonych PDF-ów),
  `pdf-fix-render-tests.log` (2 testy renderowania, 0 błędów),
  `pdf-fix-package.log` (gotowy backend z PDFBox, exit 0).
  Wszystkie logi w `tdw-data/assistant-attachment-cli-test`, bez tokenu.
- [x] Backend: upload, metadane lokalnego runu, odtworzenie i SDK blobs; testy
  formatów, busy, persist-before-AI, kolejnej tury, eksportu i usunięcia.
- [x] Frontend: przycisk wielokrotnego uploadu i nazwy w istniejącym chacie;
  testy HTTP, obu widoków, blokad oraz błędów.
- [x] Dokumentacja i weryfikacja: aktualizacja runtime/instrukcji, testy feature'a,
  neutralnego writera/runtime i architecture guard, testy/build Angulara,
  package backendu oraz kontrola finalnego diffu. Bazowe i końcowe logi w
  `tdw-data/assistant-attachment-cli-test/`.

## Dowody wykonania

- Backend: pełny `mvn -o -q -Dfrontend.skip=true test` zakończony kodem 0;
  436 raportów, 2355 testów, 0 failures/errors, 6 testów opt-in pominiętych.
- Frontend: pełna regresja 106 plików / 1042 testy; po dopracowaniu obsługi
  utraconej odpowiedzi uploadu ponownie 10 plików / 180 testów feature'a.
- Produkcyjny build Angulara oraz `mvn -o -q -DskipTests -Dfrontend.skip=true package`
  zakończone kodem 0. Build ma zastane ostrzeżenie budżetu CSS platform-landing.
- Headless Edge na zbudowanym bundle, z syntetycznym API: upload PDF i nazwy
  załączników w pełnym widoku 1440 px oraz osadzonym 700 px, bez page errors;
  screenshoty zweryfikowane. Testy kontrolują busy i utratę odpowiedzi HTTP.
- Weryfikacja finalnego diffu: bez nowej zależności dokumentowej, bez zmiany
  grafu pakietów i selekcji modeli/skilli; dotychczasowa zmiana użytkownika
  w application.properties zachowana.
