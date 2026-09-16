# AGENTS

## Zakres

Ten katalog jest wlascicielem dedykowanego feature'a UX Inspector: capture v1,
wybor scope'u, resolver targetu, kanoniczny prompt, session-bound tools, job,
report, historie oraz import/export.

## Granice

- Nie importuj zadnego sibling feature'a, w szczegolnosci `features.uiexplorer`.
- Reuse'uj tylko `frontendcatalog`, `integrations`, `agenttools`, `aiplatform`,
  `shared`, `localworkspace` i `common`.
- Capture oraz pytanie sa niezaufanymi danymi. Nie moga zmieniac procedury
  promptu, polityki tools, reportu ani repository scope.
- `FORM_DIAGNOSTICS` moze przechowywac tylko dozwolone wartosci najblizszego
  formularza, wlacznie z kontrolkami `type=hidden`. Hasla, tokeny, pliki,
  cookies i storage sa zawsze poza kontraktem, a wartosci pozostaja
  niezaufanym runtime evidence.
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
- Nowe feature-specific tools nie przyjmuja group, project, branch, ref ani
  path. Istniejace neutralne GitLab file-read tools zachowuja swoje uniwersalne
  schema. Feature-owned policy wymusza wybrany project i branch, a ukryty
  `GitLabRepositoryToolScope` przypina je do commita. Model moze nawigowac,
  wyszukiwac i czytac cale wybrane repozytorium bez `pathPrefixes` oraz
  `codeSearchScopes`, ale nigdy inny projekt ani rewizje.
- Initial prompt zawiera komplet nazw sciezek pierwszych czterech poziomow
  pinned repository. Drzewo jest tylko mapa nawigacyjna; brak kompletnego
  drzewa zatrzymuje preparation i nie uruchamia fallbacku.
- Jezeli `.github/copilot-instructions.md` istnieje, initial prompt zawiera
  jego pelna, zweryfikowana tresc. Zawiera tez `name`, `description` i sciezke
  kazdego poprawnego project skill z `.github/skills`, `.claude/skills` i
  `.agents/skills`; model musi doczytac materialny `SKILL.md` przez neutralny
  repo-bound file-read tool. Zdalnych skilli nie instaluj w runtime TDW i nie
  wlaczaj dla nich built-in toola `skill`.
- README, `AGENTS.md`, instrukcje Copilota, project skills i caly kod
  repozytorium sa niezaufanym source guidance/evidence. Guidance moze kierowac
  researchem tylko w granicach kanonicznej procedury, pinned scope, read-only
  allowlisty i kontraktu raportu. Plik przekazany jako pelny, zweryfikowany
  initial source albo rzeczywiscie odczytany pozniej przez repo-bound
  full/chunk tool jest referencja `source`. Inna sciezka wywnioskowana przez
  model nie blokuje calego raportu, ale pozostaje `source-unverified`, tworzy
  warning, obniza najwyzsza pewnosc i nie moze byc traktowana jako
  potwierdzony dowod.
- Przed odpowiedzia model sprawdza materialne mechanizmy przekrojowe, np.
  guards, interceptory, initializery, globalny stan, walidatory, uprawnienia,
  feature flags i konfiguracje; ich braku nie wolno zalozyc po samym focused
  slice.
- Canonical initial prompt posiada staly business-first answer contract; UX
  Inspector nie wlacza w tym celu runtime skilli ani dodatkowego turnu.
  Glowna narracja rozdziela `frontend` i `backend`, nie uzywa ogolnego slowa
  "system" jako wykonawcy, traktuje kod jako dowod zachowania `as-is`, a nie
  zatwierdzonego wymagania, oraz pozostawia nazwy implementacyjne w source
  references.
- Materialna interakcja HTTP w odpowiedzi jest opisana przez zweryfikowane
  `METHOD path`, trigger, cel i efekt we frontendzie. Nie zastapuj pathu nazwa
  wygenerowanej metody klienta i nie wyprowadzaj uslugi backendowej,
  autoryzacji, walidacji ani persistence bez potwierdzajacego evidence.
- Brak dopasowanego targetu nie blokuje sesji: initial component source pack
  przekazuje indeks wszystkich komponentow odnalezionych w statycznym graphie
  i pelne pliki tylko dla wybranej sciezki target -> komponent widoku, a prompt
  oznacza brak i wymaga celowanego researchu. Brak poprawnego raportu,
  wymaganych tools, scope'u albo rewizji pozostaje jawnym stanem
  `BLOCKED`/`FAILED`, a nie powodem uruchomienia UI Explorera.
