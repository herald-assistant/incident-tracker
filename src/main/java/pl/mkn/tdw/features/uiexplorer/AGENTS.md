# AGENTS

## Zakres i ownership

Ten katalog jest wlascicielem feature'a `ui-explorer`: publicznego requestu i
wyniku dokumentacji widoku, kontraktu sekcji, feature-owned API, joba,
context pipeline, przygotowania AI, raportu i przyszlej persistence.

## Dozwolone zaleznosci

- Feature moze zalezec od `integrations`, `agenttools`, `aiplatform`, `shared`,
  `localworkspace` i `common` zgodnie z grafem architektury.
- Feature nie importuje `api.*` ani zadnego sibling feature'a.
- `contract` oraz publiczne DTO nie zaleza od GitLaba, Operational Context,
  Copilot SDK ani klas adapterow.
- Reusable rozpoznawanie Angular/Nx nalezy do `integrations.gitlab`, nie do
  tego pakietu.

## Kontrakt produktu

- Jednostka analizy to jeden widok w konkretnym scenariuszu i rewizji zrodla.
- UI Explorer tworzy wylacznie dokumentacje funkcjonalna; publiczny kontrakt
  nie zawiera wyboru profilu ani celu analizy.
- Sekcje to osiem identyfikatorow z planu UI Explorer, a tryby to `OFF`,
  `COMPACT` i `DEEP`.
- Publiczny input nie przyjmuje repository id/path, GitLab group, tokenu,
  nazw plikow, komponentow ani tooli.
- Brak screen discovery, source contextu albo AI jest jawnym stanem
  niedostepnosci. Nie wolno tworzyc placeholderowego promptu, wyniku ani
  raportu udajacego wykonana analize.
- Result jest feature-specific i dopiero deterministyczny assembler mapuje go
  na neutralny `AnalysisReport`.

## Non-goals MVP

- Brak uruchamiania badanego UI i automatyzacji przegladarki.
- Brak przejmowania sesji Keycloak i nowych credentiali.
- Brak multi-repository traversal, follow-up chat i continuation.
- Brak modyfikacji badanego repozytorium albo publikacji dokumentacji.

## Testy

- Wszystkie fixtures, snapshoty, przyklady i nazwy domenowe sa silnie
  zanonimizowane i dotycza wylacznie CRM.
- Testy publicznego API uzywaja `MockMvc`, a granice pakietow sa chronione w
  `PackageDependencyGuardTest`.
