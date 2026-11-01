# Release summary — niezawodny odczyt źródeł

Status: in-progress

Source need: [Release summary](../needs/release-summary.md)

## Potrzeba / dlaczego

Przebieg wskazany przez operatora zatrzymał się podczas pobierania źródeł,
przed AI. Ponowienie tego samego porównania potrafi zakończyć zbieranie
poprawnie, ale zapisany błąd nie wskazuje pliku, strony ani przyczyny.
Naprawa mieści się w zleconej realizacji i weryfikacji Release summary.

## Baseline i conformance delta

Poziom L2: feature-owned diagnostyka i neutralny odczyt GitLab. Baseline:
przypięte SHA, pełne drzewo, odczyt UTF-8 zweryfikowany rozmiarem i hashem,
blokada AI przy niekompletnym materiale, historia od QUEUED, cztery kroki.
Endpointy, request/response, V1, manifest, raport, prompt, skille, tools,
scope, GOAL_DRIVEN, chat i continuation pozostają bez zmian. Testy bazowe
LiquibaseEvidenceCollector, GitLabRestRepositoryAdapter
i GitLabVerifiedRepositoryFileReader przechodzą.
Log: tdw-data/logs/release-summary-source-baseline.log.

Delta: nieudany odczyt zachowuje lokalizację i bezpieczny opis przyczyny.
Neutralna integracja ponawia wyłącznie odczyty po przejściowych błędach
transportu/HTTP. Nie ponawia walidacji treści, braku uprawnień ani braku pliku.
Nie zmieniamy kontraktów DTO ani wersji, nie potrzebujemy migracji danych.
Zastane zmiany formatowania trzech klas Release summary nie należą do naprawy.

## Rozwiązanie, konsumenci i alternatywy

- Collector przypisuje awarię do strony, ścieżki i etapu odczytu; szczegóły
  HTTP/połączenia są sanitizowane, bez treści odpowiedzi, tokenów i kodu.
- Jeden neutralny mechanizm ponowień dla idempotentnych odczytów plików,
  metadanych i stron pełnego drzewa. Maksymalnie trzy próby, krótki backoff,
  respektowanie Retry-After w ograniczonym czasie i przerwania wątku.
- Konsumenci: Release summary, GitLab read/navigation tools, verified source
  services, katalog/source resolve używające pełnego drzewa; shared evidence
  UI i historia wyświetlają istniejące pola bez zmian.
- Ręczne ponawianie całego runu pozostawia losowe awarie i powtarza poprawne
  odczyty. Nieograniczone ponawianie mogłoby zablokować analizę. Nie wprowadzamy
  osobnego klienta HTTP, cache ani kopii paginacji w feature.

## Zakres, ryzyka i kryteria

Zakres: diagnostyka źródeł, ograniczone ponawianie odczytu, regresja i aktualna
aplikacja. Po wyczerpaniu prób AI nadal jest zablokowane. Nie zmniejszamy
walidacji blobów ani kompletności. Retry nie obejmuje wywołań modelu,
publikowania raportu ani innych mutacji. Zmiany TLS i konfiguracji auth są
poza zakresem. Ryzyko dodatkowego czasu ograniczają liczba prób i Retry-After.
Wszystkie fixture'y i przykłady pozostają fikcyjnym CRM.

## Kroki i weryfikacja

- [x] Diagnostyka: wskaż plik/stronę i bezpieczną przyczynę błędu; testy
  awarii drzewa, pliku, zależności i sanitizacji oraz regresja feature'a.
  75 testów poprawnych, 1 pilot pominięty; log source-step1. Odczyt raw
  zachowuje status HTTP i Retry-After bez treści odpowiedzi serwera. Commit.
- [ ] Odporność odczytu: ograniczone retry tylko dla błędów przejściowych,
  także przerwanego body; testy MockRestServiceServer dla powodzenia po
  błędzie, wyczerpania prób, Retry-After, trwałych błędów i przerwania.
  Regresja integracji i konsumentów, kontrola granic. Commit.
- [ ] Pełne testy backendu, aktualizacja dokumentacji, uruchomienie nowej
  aplikacji i ponowienie właściwej analizy z preferencjami operatora.
  Weryfikacja zapisanych źródeł, promptu i wyniku. Commit zamykający.
