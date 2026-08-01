# Config Drift Verification UI name

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Dotychczasowa product-facing nazwa feature'a jest za dluga w sidebarze, a
ikona `difference` jest zbyt podobna do pozostalych feature'ow weryfikacyjnych.
Po ostatniej zmianie selektora trybu opis `BASIC` utracil pionowy uklad.

## Proponowane rozwiazanie

Zmienić wyłącznie product-facing nazwę na `Config Drift Verification`, ikonę
feature'a na Material Symbol `build_circle` i przywrócić wspólny układ copy dla
opcji `BASIC` oraz `DEEP`. Stabilne ID `runtime-configuration-verification`,
route, API, pakiety, schema eksportu i nazwa Workbench pozostają bez zmian.

## Zakres

- sidebar, topbar/breadcrumb, platform landing i historia analiz,
- ikona feature'a we wszystkich product-facing listingach,
- układ przycisku `BASIC`,
- testy UI i aktualizacja kanonicznej dokumentacji nazwy.

## Non-goals

- migracja URL, API, feature ID, pakietów albo eksportu,
- zmiana nazwy capability `Runtime Configuration` w Tool Workbench,
- zmiana działania `BASIC` lub `DEEP`.

## Ograniczenia i ryzyka

Rename nie może naruszyć odtwarzania historii po stabilnym feature ID.

## Kryteria akceptacji

- operator widzi `Config Drift Verification` w sidebarze, topbarze, landingu i
  historii,
- feature używa ikony `build_circle`,
- tytuł i opis `BASIC` są ponownie ułożone pionowo,
- techniczne kontrakty pozostają bez zmian,
- testy frontendu i production build przechodzą.

## Baseline i conformance delta

- Baseline: product-facing label to `Runtime Configuration Verification`, ikona
  to `difference`, a `BASIC` nie ma klasy copy używanej przez `DEEP`.
- Delta: tylko nazwa, ikona i lokalny layout UI.
- Publiczne API/DTO, runtime, tools, prompt, persistence, historia i eksport:
  bez zmian; historia nadal rozpoznaje stabilny feature ID.
- Konsumenci zmienianej nazwy: app shell, route metadata, platform landing,
  analysis history, feature aside i ich testy.
- Ownership i graf zależności: bez zmian.

## Kroki

- [x] Krok 1: Poprawić layout `BASIC`, zmienić product-facing nazwę i ikonę we
  wszystkich konsumentach UI, zaktualizować testy, bundle i dokumentację oraz
  wykonać drift check.

  Weryfikacja 2026-08-01: `BASIC` i `DEEP` używają wspólnego
  `mode-option__copy`; sidebar, route metadata, landing, historia i feature
  aside pokazują `Config Drift Verification`, a listingi używają
  `build_circle`. Techniczne ID, route, API, export schema oraz Workbench nie
  zmieniły się. Przeszły `npm test -- --watch=false` (227 testów),
  `npm run build`, `mvn -q "-Dtest=PackageDependencyGuardTest" test` i
  `git diff --check`.
