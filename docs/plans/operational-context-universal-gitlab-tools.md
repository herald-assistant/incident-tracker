# Wspolny katalog GitLab tools dla asysty Operational Context

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Osobne `gitlab_pinned_*` powielaly nawigacje i odczyt istniejacych GitLab MCP
tools. AI asysty nie moglo sprawdzic biblioteki albo integracji w drugim
projekcie, nawet w skonfigurowanej glownej grupie. Operator nie powinien
podawac commita w formularzu, bo aplikacja i tak przypina galaz do rewizji.

## Baseline i conformance delta (L3)

- Baseline: asysta ma cztery osobno rejestrowane `pinned_*` tools i hidden scope
  jednego projektu/commita. Istniejacy `gitlab_read_repository_file` ma osobny
  odczyt z refem modelu, a search/list innych feature'ow maja inne kontrakty.
  Parser uznaje tylko ref z pelnego odczytu; nowe repozytorium musi byc
  potwierdzone plikiem wybranego projektu.
- Delta: jedno `GitLabMcpToolConfiguration` publikuje ogolne branch/tree/list/content
  search/read pod prefixem `gitlab_`. Feature posiada allowliste pieciu tools
  i budzet. Hidden scope wiąże wybrany projekt z gałęzią operatora i commitem,
  a inne projekty w skonfigurowanej głównej grupie przypina do ich commitów.
  Czytelną tożsamość źródła tworzy dopiero zweryfikowany pełny odczyt.
- Konsumenci: asysta zmienia allowliste, prompt, skill i activity sanitization;
  pozostałe feature'y zachowują dotychczasowe nazwy i swoje allowlisty.
  `gitlab_read_repository_file` rozszerza odpowiedź o opcjonalny `sourceRef`
  przy sesji ze scope'em. UI asysty pokazuje i przyjmuje tylko gałąź.

## Proponowane rozwiazanie

Wspolny rdzen `integrations.gitlab` odpowiada za ograniczona eksploracje
drzewa, stronicowanie, wyszukiwanie kandydatow na galezi i pelny odczyt
na commicie z kontrola rozmiaru,
rewizji, hasha oraz potencjalnie wrazliwej tresci. MCP mapuje te capability
na piec neutralnych narzedzi. Scope sesji utrzymuje niezmienne rewizje i
rejestr cytowalnych refow. Lista projektow z repo-map.yml jest w prompcie
podpowiedzia, nie allowlista. Alternatywa rozbudowania `pinned_*` o drugi
projekt zachowalaby duplikacje i osobna konwencje.

## Zakres

Tool catalog, scope, prompt/skill, walidacja source refs, selektor galezi,
testy i kanoniczna dokumentacja. Nie zmieniamy modelu katalogu ani procesu
decyzji i warunkowego zapisu YAML.

## Non-goals

- Automatyczny skan wszystkich projektow grupy.
- Nadanie kazdemu feature'owi tej samej allowlisty albo budzetu.
- Odczyt projektow poza skonfigurowana glowna grupa GitLab.

## Ograniczenia i ryzyka

Model widzi `projectName` i `branchRef`, ale nie wybiera grupy. Dla wybranego
projektu serwer ignoruje alternatywna galaz i odrzuca jej uzycie; dla innych
projektow serwer rozwiazuje wskazana galaz do commita przed odczytem.
Wynik list/search/tree nie jest dowodem tresci. Dodatkowy projekt nie moze
potwierdzic `repository.git` wybranego projektu. Piec tools pozostaje
objete twardym limitem sesji. Pelny odczyt ma limit 256 KiB i odrzuca
prawdopodobnie wrazliwe pliki, w tym sekrety w POM/JSON.

## Kryteria akceptacji

- Jeden provider publikuje piec ogolnych nazw, a zadna nazwa `pinned_*` nie
  pozostaje w katalogu MCP.
- Asysta widzi projekty z Operational Context, moze wyszukac i przeczytac
  plik z innego projektu w glownej grupie, ustala jego galaz z GitLaba
  i zwraca osobny commit w `sourceRef`.
- Wybrany projekt nie zmienia galezi ani commita pod wplywem modelu; nowy
  wpis repozytorium nadal wymaga refa pliku tego projektu.
- UI przyjmuje galaz i odrzuca wklejony identyfikator commita.
- Testy rejestracji, scope, odczytu, promptu, parsera, UI i budzetu przechodza.

## Kroki

- [x] Ustalono baseline, liste konsumentow i granice nowego kontraktu.
  Dowod: audyt `GitLabMcpTools`, `GitLabPinnedProjectMcpTools`, allowlist
  feature'ow, parsera oraz formularza asysty.
- [x] Scalono katalog MCP, dodano scope wielu projektow i bezpieczny odczyt.
  Dowod: kompilacja backendu i celowane testy tooli, scope'u oraz budzetu.
- [x] Zmieniono prompt, skill i UI na wspolne nazwy oraz galaz bez commita.
  Dowod: testy promptu, activity i formularza; wynik koncowy w ostatnim kroku.
- [x] Uruchomiono weryfikacje backendu/frontendu, sprawdzono granice source refs
  i uaktualniono architekture. Dowod: 562 testy Angulara, build Angulara oraz
  `mvn -q -Pbackend-dev clean package` (1531 testow, 0 failures/errors,
  1 skipped). Test joba potwierdza, ze odczyt drugiego projektu nie jest
  liczony jako odczyt projektu wybranego przez operatora.
