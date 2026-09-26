# Czytelne drzewo repozytorium w promptach początkowych

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

UX Inspector i Operational Context Assistance pokazują modelowi fragment drzewa
repozytorium w różnych formach: pierwszy jako płaską listę pełnych ścieżek,
drugi jako tablicę wpisów JSON. Operator oczekuje jednolitej, hierarchicznej
mapy katalogów i plików z wcięciami `├──`, `└──` i `│`, podobnej do podanego
przykładu. Mapa ma ułatwić modelowi nawigację, bez nadawania nazwom ścieżek
statusu dowodu treści plików.

## Klasyfikacja, baseline i konsumenci

Zmiana L2: dotyka promptów dwóch feature'ów (L1) i dodaje mały neutralny
renderer używany przez oba (L2).

| Obszar | Obecny stan, który pozostaje prawdziwy | Konsumenci |
| --- | --- | --- |
| UX Inspector | Osobny `ux-inspector/repository-tree.md` zawiera komplet nazw ścieżek z pierwszych czterech poziomów przypiętego commita. Brak kompletnego drzewa blokuje preparation. Lista plików zasila wykrywanie instrukcji i skilli repozytorium. | Initial prompt, logical artifact, repository guidance, testy preparation i run assemblera. |
| Operational Context Assistance | Po wyborze projektu collector pobiera ograniczony slice: do czterech poziomów, 120 wpisów i 12 żądań. `selectedSource.tree` przechowuje `path`, `depth`, `entries`, `continuations` i `truncated`; brak lub ucięcie drzewa jest jawnym ograniczeniem. | Initial prompt, JSON logical artifact, testy promptu i collectora. |
| GitLab navigation tools | `gitlab_list_repository_tree` zwraca typowane wpisy z limitami, a `gitlab_list_repository_files` zwraca stronicowane ścieżki. | UX Inspector i Operational Context Assistance; odpowiedzi tools pozostają bez zmian. |

Baseline testów: `UxInspectorRepositoryTreeArtifactServiceTest`,
`UxInspectorPromptAndSkillsTest` i
`OperationalContextAssistancePromptPreparationServiceTest` przeszły przed
zmianą (`mvn -q -Dtest=... test`, 2026-09-26). Worktree był czysty.

## Proponowane rozwiązanie i conformance delta

Mały, deterministyczny renderer w `common` przyjmuje nazwę root i typowane
ścieżki (`directory`/`file`). Buduje hierarchię bez odczytu dodatkowych danych,
sortuje rodzeństwo deterministycznie i oznacza katalogi końcowym `/`. Nazwa
root to ostatni segment wybranego projektu; pełna identyfikacja projektu, branch
i commit pozostają w metadanych poza diagramem. Renderer nie importuje
`features.*`, `integrations.*` ani klas platformy AI.

Przykładowa postać fragmentu w promptach (fikcyjne CRM):

```text
crm-ui/
├── public/
│   └── index.html
└── src/
    └── components/
        ├── Button/
        │   └── Button.tsx
        └── Navbar/
```

UX Inspector zachowuje metadane `repository`, `branch`, `commit`, `depth`,
`content` i `complete`, a płaskie `paths:` zastępuje diagramem. Prompt nadal
opisuje go jako mapę nawigacyjną bez treści plików.

Operational Context Assistance pokazuje metadane projektu w JSON bez duplikacji
tablicy `tree.entries`, a obok osobny blok tekstowy z diagramem. `depth`,
`truncated` i `continuations` pozostają jawne i maszynowo czytelne przy
metadanych; `selectedSource.tree` w logical artifact zachowuje obecny kontrakt.
Gdy slice jest częściowy, diagram zawiera tylko faktycznie pobrane węzły i nie
udaje kompletnego drzewa. Gdy nie ma wpisów, prompt jawnie rozróżnia pusty
fragment od problemu z pobraniem przez istniejące visibility limits.

Alternatywa: dwa lokalne renderery. Odrzucona, bo format i sortowanie mogłyby
ponownie się rozjechać.

Conformance delta: zmienia się wyłącznie prezentacja drzewa w initial promptach
i powiązanym artefakcie UX Inspectora. Bez zmian pozostają API/DTO, zbieranie
contextu, treść plików, tool schema i wyniki tools, allowlisty, hidden scope,
budżety, report, job state, historia/import/export oraz UI. Żaden feature nie
importuje drugiego. Nowy neutralny helper ma dwóch rzeczywistych konsumentów.

## Zakres

- Wspólny renderer hierarchicznego drzewa ścieżek.
- Użycie renderera w obu initial promptach, z zachowaniem metadanych i sygnałów
  niekompletności.
- Testy formatu i aktualizacja kanonicznych opisów runtime.

## Non-goals

- Zmiana wyników `gitlab_list_repository_tree` lub `gitlab_list_repository_files`.
- Zmiana głębokości, limitów, paginacji, wyboru projektu lub odczytu plików.
- Dopisywanie komentarzy `# Poziom N` do każdej linii diagramu; poziom wynika z
  wcięcia, a `depth` pozostaje metadaną.

## Ograniczenia i ryzyka

- Diagram nie może sugerować, że ucięty slice jest kompletny ani że sama nazwa
  pliku potwierdza jego treść.
- Sortowanie nie może zmienić zbioru ścieżek używanych przez UX Inspector do
  wykrywania repository guidance.
- Zmiana treści promptu może wpłynąć na zachowanie modelu; testy sprawdzają
  dokładny kształt diagramu, metadane, kontynuacje i brak utraty ścieżek.
- Nowy kod wspólny nie może wprowadzić zależności na feature lub GitLab.

## Kryteria akceptacji

1. Oba initial prompty pokazują katalogi i pliki w tej samej notacji drzewa.
2. Dla tych samych wejściowych ścieżek renderer daje ten sam stabilny wynik;
   root, rodzeństwo, ostatnia gałąź i puste drzewo są obsłużone jawnie.
3. UX Inspector nadal pobiera komplet pierwszych czterech poziomów i przekazuje
   tę samą listę plików do repository guidance.
4. Operational Context Assistance nadal pokazuje `truncated` i wszystkie
   `continuations` z cursorami oraz zachowuje oryginalny JSON logical artifact.
5. Testy celowane, `PackageDependencyGuardTest` i wymagane `mvn -q test`
   przechodzą. Brak nowych przykładów z rzeczywistymi danymi organizacji.

## Kroki

- [x] Krok 1: Dodać neutralny renderer z testami dla zagnieżdżenia, kolejności,
  typów, pustych i częściowych danych. Dowód: test renderera i przegląd grafu
  zależności (`RepositoryPathTreeRendererTest` przeszedł; helper importuje tylko
  klasy JDK).
- [x] Krok 2: Podłączyć renderer w UX Inspectorze i Operational Context
  Assistance, zaktualizować prompty oraz testy dokładnego formatu, metadanych i
  continuation. Dowód: celowane testy obu feature'ów i run assemblera przeszły.
- [x] Krok 3: Zaktualizować `ux-inspector-runtime-flow.md`,
  `operational-context-model-tools-and-usage.md` i, jeśli wymaga tego opis
  ownership, `package-dependencies.md`; wykonać architecture diff,
  `PackageDependencyGuardTest`, `mvn -q test`, anonimizacyjny przegląd diffu i
  `git diff --check`. Dowód: `mvn -q test` zakończył się kodem 0,
  `PackageDependencyGuardTest` ma 4 testy bez błędów, `git diff --check`
  zakończył się kodem 0, nowe przykłady używają wyłącznie fikcyjnego CRM.
