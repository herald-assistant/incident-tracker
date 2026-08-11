# Nadpisywanie ustawien Confluence w Workspace Settings

Status: done

Source need: [Lokalne ustawienia Confluence w Workspace Settings](../needs/workspace-settings-confluence-overrides.md)

## Potrzeba / dlaczego

Workspace Settings pozwala lokalnie nadpisac podstawowe ustawienia Jira, ale
nie udostepnia analogicznego kontraktu dla Confluence. Operator musi przez to
wracac do bazowej konfiguracji uruchomieniowej, mimo ze Confluence jest
reusable capability uzywana przez Tool Workbench i Change Verification.

## Poziom zmiany

L2 - przekrojowy. Zmiana rozszerza shared/operator API, wersjonowany model
`settings.json`, mutable properties integracji oraz wspolny ekran Platform /
Workspace Settings konsumowany przez frontend.

## Proponowane rozwiazanie

Rozszerzyc obecny neutralny mechanizm Workspace Settings dokladnie wedlug
wzorca Jira:

- wystawic `analysis.confluence.base-url` i `analysis.confluence.token` w
  `GET /api/workspace/settings`,
- przyjmowac sekcje `confluence` w `PUT /api/workspace/settings`,
- zapisac opcjonalne lokalne wartosci w osobnej sekcji `confluence` pliku
  `settings.json`,
- po starcie oraz po zapisie ustawic efektywne wartosci na istniejacym
  `ConfluenceProperties`,
- pokazac w Angularowym Workspace Settings panel Confluence z obsluga
  `DEFAULT`, `CUSTOM`, restore default oraz ukrywaniem tokenu.

Zakres jest celowo zgodny z Jira: obejmuje podstawowe dane polaczenia
`baseUrl` i `token`. `analysis.confluence.url-pattern` pozostaje deploymentowa
allowlista adresow i nie jest automatycznie zmieniana razem z adresem bazowym.
`analysis.confluence.max-text-characters` pozostaje limitem technicznym poza
UI.

## Baseline

Feature/capability: shared Workspace Settings oraz reusable Confluence
capability.

Obecna wartosc dla operatora: ekran pokazuje efektywne ustawienia i pozwala
lokalnie nadpisywac brand, Copilot, Jira, GitLab, Elasticsearch i Dynatrace.
Confluence nie jest widoczne.

Publiczny input i output: `GET /api/workspace/settings` zwraca pola wraz z
wartoscia bazowa, lokalna, efektywna, zrodlem i flaga sekretu. Whole-document
`PUT /api/workspace/settings` przyjmuje sekcje wspieranych ustawien. Brak sekcji
Confluence.

Kanoniczne endpointy: `GET /api/workspace/settings` oraz
`PUT /api/workspace/settings`; Confluence Source korzysta z
`POST /api/confluence/page/content`.

Local history/import/export/continuation: `settings.json` ma schemat
`tdw.workspace-settings`, wersje 6 i nie jest czescia eksportu analiz. Brakujace
sekcje sa normalizowane do pustych ustawien.

Shared komponenty i modele FE: kontrakt HTTP jest w
`frontend/src/app/core/models/workspace-settings.models.ts`, a formularz i
prezentacja w `features/workspace-settings`.

Testy chroniace zachowanie: `WorkspaceSettingsServiceTest`,
`WorkspaceSettingsControllerTest`, `LocalWorkspaceStoreTest` i integracyjny
`app.spec.ts`. Bazowy przebieg z 2026-08-11: celowane testy backendu przeszly;
frontend: 44 pliki testowe i 340 testow przeszlo.

Znane drifty w dotykanym obszarze: dokumenty architektoniczne nie wymieniaja
obecnej obslugi Jira w zakresie Workspace Settings. Zmiana naprawi ten opis
razem z dopisaniem Confluence.

## Conformance delta

Cel zmiany: lokalne nadpisywanie podstawowych ustawien Confluence tym samym
modelem co Jira.

Dlaczego nie wystarcza obecny mechanizm: mechanika merge/apply jest reusable,
ale publiczny DTO, plik ustawien i formularz maja jawna liste sekcji bez
Confluence.

Warstwa bedaca wlascicielem: `api.workspacesettings` posiada shared/operator
HTTP i aplikowanie ustawien; `localworkspace.settings` posiada zapis;
`integrations.confluence.ConfluenceProperties` pozostaje wlascicielem danych
integracji; frontendowy `core` i ekran Workspace Settings posiadaja kontrakt i
prezentacje.

Zmiana publicznego API/DTO: addytywna sekcja `values.confluence` w odpowiedzi
GET oraz `confluence` w request PUT, z polami `baseUrl` i `token`.

Zmiana context/evidence: bez zmian.

Zmiana prompt/artifacts/skills: bez zmian.

Zmiana tools/policy/hidden scope/budzetu: bez zmian.

Zmiana report/result: bez zmian.

Zmiana job state/persistence/export: job state i eksport bez zmian;
`settings.json` przechodzi z wersji 6 na 7 i dostaje opcjonalna sekcje
`confluence`.

Zmiana shared FE/UX: jeden nowy panel w istniejacym wzorcu Workspace Settings,
bez nowego shared komponentu.

Nowe lub usuniete zaleznosci: `api.workspacesettings` dostaje dozwolona
zaleznosc do istniejacego `integrations.confluence.ConfluenceProperties`; brak
nowego kierunku zaleznosci.

Konsumenci dotknietego shared mechanizmu: frontend Workspace Settings,
filesystemowy store ustawien, Confluence Source, Jira remote-link enrichment
oraz Change Verification korzystajace z pobranych stron Confluence.

Kompatybilnosc i migracja: zmiana DTO jest addytywna. Stary `settings.json`
bez sekcji `confluence` jest normalizowany do pustej sekcji, a kolejny zapis
utrwala wersje 7. `PUT` pozostaje whole-document update wykonywany przez
bundlowany frontend tej samej wersji.

Testy regresji: serwis i kontroler Workspace Settings, round-trip oraz odczyt
starego pliku ustawien, zachowanie formularza Angulara, istniejace testy
adaptera Confluence i konsumenta Jira, pelny zestaw testow frontendu, build UI
oraz backend package.

Dokumentacja: `key-decisions.md` i `system-overview.md` dostaja aktualny zakres
Jira i Confluence; plan i need sa widoczne w `docs/README.md`.

Znany drift: brak Jira w dokumentacji zostanie naprawiony; pozostale granice
Workspace Settings pozostaja bez zmian.

## Reuse i audyt konsumentow

| Potrzeba | Istniejacy mechanizm | Reuse bez zmian | Delta |
| --- | --- | --- | --- |
| merge bazowej i lokalnej wartosci | `WorkspaceSettingsService` | tak | dodanie sekcji Confluence |
| lokalny zapis | `LocalWorkspaceSettingsFile` i store JSON | tak | addytywna sekcja, wersja 7 |
| runtime connection config | mutable `ConfluenceProperties` | tak | apply efektywnych `baseUrl` i `token` |
| HTTP dla UI | `GET/PUT /api/workspace/settings` | tak | addytywne DTO |
| formularz i source badges | Workspace Settings Angular | tak | panel zgodny z Jira |
| pobieranie stron | Confluence adapter i source API | tak | bez zmian |

## Zakres

- Backendowy response/update DTO Workspace Settings.
- Lokalny model i kompatybilny odczyt/zapis `settings.json`.
- Aplikowanie override'ow na zywe `ConfluenceProperties`.
- Angularowy model, formularz i panel Confluence.
- Testy kontraktu, persistence, runtime apply i zachowania operatora.
- Aktualizacja kanonicznej dokumentacji zakresu Workspace Settings.
- Regeneracja produkcyjnego bundle Angulara.

## Non-goals

- Override `analysis.confluence.url-pattern` albo
  `analysis.confluence.max-text-characters`.
- Zmiana adaptera, endpointu Confluence Source lub materialu Change
  Verification.
- Nowy magazyn sekretow, szyfrowanie `settings.json` albo zdalna konfiguracja.
- Zmiana semantyki whole-document `PUT` dla pozostalych sekcji.

## Ograniczenia i ryzyka

- Confluence nadal wymaga, aby docelowy URL pasowal do bazowego
  `analysis.confluence.url-pattern`; zmiana `baseUrl` nie rozszerza tej
  allowlisty.
- Token jest zwracany lokalnemu UI zgodnie z obecnym kontraktem sekretow
  Workspace Settings. Nie wolno dodac jego logowania ani eksportu.
- Wszystkie miejsca konstruujace wersjonowany record i publiczne DTO musza byc
  zaktualizowane atomowo z frontendem.
- Build Angulara aktualizuje wygenerowane pliki w `src/main/resources/static`;
  nie wolno ich edytowac recznie.

## Kryteria akceptacji

- GET zwraca `values.confluence.baseUrl` i `values.confluence.token` z
  poprawnymi property keys, source oraz `secret=true` tylko dla tokenu.
- PUT zapisuje rozne od bazowych wartosci jako override, a puste lub identyczne
  z bazowymi wartosci usuwaja override.
- Efektywne wartosci trafiaja do `ConfluenceProperties` na starcie i po PUT.
- Stary plik ustawien bez sekcji Confluence jest czytelny.
- UI pokazuje panel Confluence, status `DEFAULT`/`CUSTOM`, restore default i
  kontrolowana widocznosc PAT.
- Test UI potwierdza wysylany payload Confluence, nie tylko render odpowiedzi.
- Dokumentacja wymienia Jira i Confluence w aktualnym zakresie Workspace
  Settings.
- Pelna macierz weryfikacji przechodzi bez regresji.

## Macierz testow

| Warstwa | Dowod |
| --- | --- |
| service merge/apply | `WorkspaceSettingsServiceTest` |
| HTTP JSON i sekret | `WorkspaceSettingsControllerTest` |
| zapis v7 i kompatybilnosc v6 | `LocalWorkspaceStoreTest` |
| dynamiczne uzycie ustawien | istniejace `ConfluenceRestPageAdapterTest` i `JiraRestIssueAdapterTest` plus asercje runtime apply w service test |
| formularz operatora | `app.spec.ts` albo dedykowany test Workspace Settings, obejmujacy render i PUT |
| granice pakietow | `PackageDependencyGuardTest` |
| frontend regresja | `npm --prefix frontend test -- --watch=false` |
| bundle | `npm --prefix frontend run build` |
| wspolny kontrakt backend/frontend | `mvn -q -Pbackend-dev clean package` po buildzie UI |

## Kroki

- [x] Krok 1: rozszerzyc backendowy kontrakt i lokalny model o Confluence,
  zaaplikowac efektywne `baseUrl` i `token`, podbic wersje ustawien do 7 oraz
  dodac testy serwisu, HTTP i kompatybilnego persistence. Wynik: backend
  obsluguje addytywna sekcje bez regresji starych plikow.
- [x] Krok 2: rozszerzyc Angularowy model i Workspace Settings o panel
  Confluence oraz test renderowania, source/reset/token visibility i payloadu
  PUT. Wynik: operator moze zapisac i przywrocic podstawowe ustawienia tak jak
  dla Jira.
- [x] Krok 3: zaktualizowac dokumenty kanoniczne, wygenerowac bundle, wykonac
  pelna macierz testow i architecture diff, a po spelnieniu kryteriow oznaczyc
  plan jako `done`. Wynik: kontrakt backend/frontend, artefakt i dokumentacja sa
  spojne.

## Dowody weryfikacji

- Celowane testy backendu: 18/18 przeszlo dla
  `WorkspaceSettingsControllerTest`, `WorkspaceSettingsServiceTest`,
  `LocalWorkspaceStoreTest` i `PackageDependencyGuardTest`.
- Celowany test Angulara `app.spec.ts`: 19/19 przeszlo.
- `npm --prefix frontend test -- --watch=false`: 44 pliki testowe i 341 testow
  przeszlo.
- `npm --prefix frontend run build`: produkcyjny bundle zostal wygenerowany w
  `src/main/resources/static`.
- `mvn -q -Pbackend-dev clean package`: czysty pakiet backendu z pelna regresja
  przeszedl; wynikowy JAR zawiera aktualny `index.html`, main bundle i chunk
  Workspace Settings.
- `git diff --check`: brak bledow whitespace.
- Architecture diff: jedyna nowa produkcyjna zaleznosc top-level to dozwolone
  `api -> integrations.confluence`; nie zmieniono promptow, tools, hidden
  scope, job state ani eksportow analiz. Publiczne DTO i `settings.json` sa
  rozszerzone addytywnie, a test potwierdza odczyt pliku wersji 6 bez sekcji
  Confluence.
