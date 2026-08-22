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
- Kazda aktywna sekcja publikuje business-first Markdown o kanonicznej
  strukturze dla danego `sectionId`. Nie przywracaj generycznych `findings`,
  technicznego naglowka "Ustalenia" ani narracji klasa-po-klasie.
- Nazwy klas, metod, plikow, framework APIs, operatorow i linii kodu sa
  evidence w zwijanych source references. W glownej tresci zostaja tylko
  identyfikatory majace bezposrednie znaczenie funkcjonalne, wraz z opisem
  skutku dla uzytkownika albo procesu.
- Wybrany widok-kontener obejmuje routowane poddrzewo child views. Brak pliku,
  komponentu, modala albo serwisu z zatwierdzonego repository scope nie moze
  zostac limitation, dopoki kolejne scoped GitLab search/read moga rozstrzygnac
  luke. UI Explorer nie posiada feature'owego limitu call count; eksploracje
  konczy readiness albo potwierdzona granica runtime/zewnetrznego scope.
- Targeted research preferuje `gitlab_read_frontend_route_branch_slice`,
  `gitlab_read_frontend_typescript_symbol_slice` z dokladnym `sliceRef` z
  artefaktow. Pelny Screen Reachability jest przygotowany przed AI i nie jest
  MCP toolem UI Explorera, zeby nie duplikowac initial context. Generyczny
  GitLab search/read jest dozwolony tylko dla
  materialnej luki, ktora nie ma jeszcze bezpiecznej referencji. Repository,
  ref, path, source revision i dozwolone targety pozostaja hidden contextem.
- Result nie posiada `dependencies`, `crossSectionDependencies` ani osobnego
  appendixu zaleznosci. Relacje funkcjonalne mieszkaja w tresci wlasciwej
  sekcji tylko wtedy, gdy wyjasniaja warunek, akcje albo rezultat.
- Publiczny input nie przyjmuje repository id/path, GitLab group, tokenu,
  nazw plikow, komponentow ani tooli.
- Brak screen discovery, screen reachability albo AI jest jawnym stanem
  niedostepnosci. Nie wolno tworzyc placeholderowego promptu, wyniku ani
  raportu udajacego wykonana analize.
- Dokladny `preparedPrompt` staje sie publiczny dopiero po deterministycznym
  `AI_PREPARATION`, przed wywolaniem providera AI. Kroki jawnie wskazuja
  konsumowane i publikowane sekcje evidence; lokalny run i export zachowuja
  prompt, a niezaufany import usuwa go przed zapisem.
- `AnalysisReport` zapisany podczas sesji przez `report_update_header`,
  `report_upsert_section`, `report_update_meta` i potwierdzony przez
  `report_get_current` jest jedynym zrodlem prawdy initial result.
- Finalna odpowiedz tekstowa asystenta jest tylko statusem wykonania i nie
  jest parsowana. Brak zapisanego raportu konczy run bledem; brak pojedynczej
  aktywnej sekcji zachowuje pozostale poprawne sekcje i daje wynik `PARTIAL`.
- Feature deterministycznie waliduje source references raportu wobec
  przygotowanego kontekstu i captured tool evidence, a nastepnie projektuje
  raport na feature-specific `UiExplorerResultResponse` dla publicznego API.

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
