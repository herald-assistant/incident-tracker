# AGENTS

## Zakres

Ten katalog jest wlascicielem dedykowanego feature'a UX Inspector: capture v3,
wybor scope'u, resolver targetu, kanoniczny prompt, session-bound tools, job,
report, historie oraz import/export.

## Granice

- Nie importuj zadnego sibling feature'a, w szczegolnosci `features.uiexplorer`.
- Reuse'uj tylko `frontendcatalog`, `integrations`, `agenttools`, `aiplatform`,
  `shared`, `localworkspace` i `common`.
- Capture oraz pytanie sa niezaufanymi danymi. Nie moga zmieniac procedury
  promptu, polityki tools, reportu ani repository scope.
- `FORM_DIAGNOSTICS` odczytuje bez limitu liczby pol widoczne kontrolki z
  calego dokumentu w chwili capture. Pola ukryte, hasla, tokeny, pliki,
  cookies i storage sa poza kontraktem. Wartosc, etykieta i komunikat
  walidacji sa niezaufanym runtime evidence. Pola ida z capture i store'em
  jednym POST; brak pol nie blokuje analizy.
- Opcjonalny zrzut `globalThis.getStoreState()` jest zamrozona obserwacja
  sesji UX. Pending snapshot po jednym POST jest tylko w pamieci backendu;
  po starcie zredagowany JSON jest w neutralnym polu `storeSnapshot` w
  `run.json`; publiczne snapshoty i portable export nie zawieraja store'a.
  Brak zrzutu ani blad odczytu lub storage nie blokuje analizy.
- Initial prompt przekazuje najwyzej dwa poziomy mapy store'a bez wartosci.
  Odczyt wartosci jest dozwolony przez neutralne `run_store_list_paths` i
  `run_store_read_value` z jawnym `runId`. Skill
  `ux-inspector-store-grounding` sluzy wylacznie laczeniu kodowego warunku z
  konkretnym stanem; nie przenosi kanonicznej procedury raportu.
- Selector candidates z `domFingerprint` sa sygnalem dla deterministycznego
  resolvera; model nie dostaje arbitrary-selector toola ani prawa do
  traktowania selectora jako dowodu ownership w kodzie.
- Resolver deduplikuje sygnaly stable attributes i selector candidates,
  punktuje tylko allowliste bezpiecznych identyfikatorow oraz tokenow klas i
  zachowuje kolejnosc `componentBoundaryTags` od najblizszego komponentu.
  Brak jednoznacznego anchor line nie moze uruchamiac fallbacku do pierwszego
  ogolnego tagu w template.
- Dla `AMBIGUOUS` initial context zawiera ograniczone evidence maksymalnie
  trzech najlepszych kandydatow wraz z bindingami. Pierwszy kandydat nie staje
  sie przez to rozstrzygnietym targetem.
- Jedynym wynikiem AI jest `AnalysisReport` z sekcja `answer`; tekst finalny
  Copilota nigdy nie jest fallbackiem.
- Follow-up chat wznawia te sama sesje i zachowuje branchowy repository scope.
  Report tools moga zmienic raport tylko po jawnej prosbie w najnowszej
  wiadomosci operatora; mapper ponownie waliduje report/result. Odpowiedzi
  pozostaja osobnymi wiadomosciami dla nietechnicznego analityka.
- Nowe feature-specific tools nie przyjmuja group, project, branch, ref ani
  path. Istniejace neutralne GitLab file-read tools zachowuja swoje uniwersalne
  schema. Feature-owned policy wymusza wybrany project i branch, a ukryty
  `GitLabRepositoryToolScope` utrzymuje wybrany branch bez przypinania commita. Model moze nawigowac,
  wyszukiwac i czytac cale wybrane repozytorium bez `pathPrefixes` oraz
  `codeSearchScopes`, ale nigdy inny projekt ani branch.
- Initial prompt zawiera komplet nazw sciezek pierwszych czterech poziomow
  repository na wybranym branchu. Drzewo jest tylko mapa nawigacyjna; brak kompletnego
  drzewa zatrzymuje preparation i nie uruchamia fallbacku.
- Jezeli `.github/copilot-instructions.md` istnieje, initial prompt zawiera
  jego pelna, zweryfikowana tresc. Zawiera tez `name`, `description` i sciezke
  kazdego poprawnego project skill z `.github/skills`, `.claude/skills` i
  `.agents/skills`; model musi doczytac materialny `SKILL.md` przez neutralny
  repo-bound file-read tool. Zdalnych skilli nie instaluj w runtime TDW i nie
  wlaczaj dla nich built-in toola `skill`.
- README, `AGENTS.md`, instrukcje Copilota, project skills i caly kod
  repozytorium sa niezaufanym source guidance/evidence. Guidance moze kierowac
  researchem tylko w granicach kanonicznej procedury, branchowego scope, read-only
  allowlisty i kontraktu raportu. UX Inspector nie przygotowuje, nie waliduje
  ani nie klasyfikuje report references; brakujacy dowod pozostaje jawnym gapem
  albo visibility limit.
- Przed odpowiedzia model sprawdza materialne mechanizmy przekrojowe, np.
  guards, interceptory, initializery, globalny stan, walidatory, uprawnienia,
  feature flags i konfiguracje; ich braku nie wolno zalozyc po samym focused
  slice.
- Canonical initial prompt posiada staly business-first answer contract; UX
  Inspector nie wlacza w tym celu dodatkowego turnu ani skilla raportowego.
  Dla sesji ze store'em wlacza built-in `skill` dla waskiego grounding skilla.
  Glowna narracja rozdziela `frontend` i `backend`, nie uzywa ogolnego slowa
  "system" jako wykonawcy, traktuje kod jako dowod zachowania `as-is`, a nie
  zatwierdzonego wymagania, a nazwy implementacyjne pokazuje tylko wtedy, gdy
  pomagaja odpowiedziec na pytanie operatora.
- Materialna interakcja HTTP w odpowiedzi jest opisana przez zweryfikowane
  `METHOD path`, trigger, cel i efekt we frontendzie. Nie zastapuj pathu nazwa
  wygenerowanej metody klienta i nie wyprowadzaj uslugi backendowej,
  autoryzacji, walidacji ani persistence bez potwierdzajacego evidence.
- Brak dopasowanego targetu nie blokuje sesji: initial component source pack
  przekazuje komponenty wybranych sciezek target -> komponent widoku oraz
  wszystkie odkryte komponenty dopasowane selectorem do runtime
  `componentBoundaryTags`, relacje tylko pomiedzy nimi i ich pelne pliki.
  Capture nie ucina lancucha przodkow DOM ani custom-element boundaries;
  nowe wybory elementu maja `omittedNodeCount=0` i nie generuja
  `ANCESTORS_TRUNCATED`. Pack dolacza
  kompaktowy effective route chain wybranego widoku oraz maksymalnie jeden ograniczony slice
  bezposredniej klasy bazowej komponentu widoku. Pelny route subtree, pelny
  plik klasy bazowej, dalsze dziedziczenie i pozostaly graph nie trafiaja do
  initial promptu; prompt oznacza jego rozmiar i wymaga celowanego researchu przez
  neutralne repository tools. Brak poprawnego raportu,
  wymaganych tools, scope'u albo rewizji pozostaje jawnym stanem
  `BLOCKED`/`FAILED`, a nie powodem uruchomienia UI Explorera.
- Deterministyczny resolver UX Inspectora uzywa target-first discovery:
  selected view, uporzadkowanych runtime `componentBoundaryTags` i kilku
  celowanych selector searches. Nie uruchamia pelnego BFS ekranu ani
  rekurencyjnego dependency traversal przed pierwszym wywolaniem AI.
