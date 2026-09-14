# Operational Context maintenance

Ten katalog zawiera zasady merytoryczne utrzymania katalogu. Jedenaście plików
Markdown wskazanych w
`OperationalContextAssistanceCatalogMaterialService.GUIDANCE_NAMES` jest
pakowanych do aplikacji i przekazywanych asyście AI jako reguły przed danymi
operatora, GitLaba i aktywnego katalogu. Skrypty PowerShell nie są częścią
promptu AI. Ten `AGENTS.md` też nie jest przekazywany do sesji; służy osobom
zmieniającym reguły.

- Przed zmianą porównaj regułę z aktualnymi dziewięcioma YAML, schematem
  `OperationalContextCatalogEntitySchema`, walidacją
  `OperationalContextCatalogMaintenanceService` i kanonicznym opisem w
  `docs/architecture/operational-context-model-tools-and-usage.md`. Schemat i
  walidator mają pierwszeństwo przed przykładem w prompcie. Nie dodawaj
  kluczy, których maintenance nie zapisze, ani opisów dawnych formatów.
- Zachowuj podział faktów między systemem, repozytorium, systemowym lub
  domenowym code-search scope, procesem, integracją, bounded contextem,
  zespołem, terminem i regułą handoffu. Katalog jest indeksem nawigacyjnym;
  nie kopiuj do niego inventory klas, endpointów lub konfiguracji runtime.
- Oddzielaj obserwację w źródle od interpretacji AI i jawnej informacji
  operatora. Brak obserwacji w jednym pliku nie dowodzi braku relacji. Nie
  przyznawaj potwierdzonego ownershipu ani roli `frontend` z nazwy projektu,
  frameworka lub sugestii modelu.
- Reguła niewpisywania sekretów, danych osobowych i produkcyjnych payloadów
  do kuratorowanego katalogu nie oznacza redakcji materiału wejściowego.
  Asysta przekazuje opis, aktywny katalog i odczytane tekstowe pliki do AI bez
  heurystycznego maskowania; ograniczają ją zakres GitLaba, przypięty commit,
  format, ścieżka i rozmiar odczytu.
- Asysta daje propozycje do ręcznej korekty i przeglądu. Gdy brakuje podstawy
  do pola, opisuj ograniczenie widoczności; nie wymagaj dialogu z operatorem
  podczas runu ani nie instruuj AI do samodzielnego zapisu YAML.
- Każdy nowy przykład w promptach, skryptach i testach musi być całkowicie
  fikcyjnym scenariuszem CRM zgodnie z głównym `AGENTS.md`.

Przy zmianie nazw lub zakresu reguł sprawdź także listę `GUIDANCE_NAMES`,
`OperationalContextMaintenanceInstructionsTest`, test przygotowania
materiału AI, runtime skill `operational-context-catalog-revision` i tooltipy
edytora. Weryfikację diagnostyczną opisuje
`docs/operational-context-assistance-diagnostics.md`.
