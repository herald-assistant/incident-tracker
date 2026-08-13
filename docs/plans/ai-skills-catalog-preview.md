# Podglad katalogu AI Skills - read-only MVP

Status: done

Source need: [`../needs/ai-skills-catalog-preview.md`](../needs/ai-skills-catalog-preview.md)

Zatwierdzenie zakresu: polecenie uzytkownika z 2026-08-13, aby zbudowac
uzgodniony podglad bez edycji per feature.

Klasyfikacja zmiany: L2 - nowa projekcja wspolnego mechanizmu runtime, nowe
shared/operator API oraz nowy ekran platformowy.

## Cel

Udostepnic spojny z platforma, read-only katalog AI Skills z wyszukiwaniem,
filtrowaniem, bezposrednim linkiem i bezpiecznym podgladem renderowanej oraz
surowej tresci `SKILL.md`.

## Baseline

- `CopilotSkillRuntimeLoader` przy starcie materializuje i waliduje caly
  packaged katalog pod jednym rootem runtime.
- Platforma przechowuje obecnie tylko liste nazw oraz root przekazywany do
  sesji NEW i EXISTING; nie ma neutralnej projekcji tresci skilli.
- Incident Analysis, Flow Explorer, Change Verification i Config Drift Viewer
  korzystaja z tego samego rootu, ale wybieraja workflow w swoich promptach.
- Nie istnieje HTTP API katalogu skilli ani frontendowy route.
- `app-shell` ma grupe `Platform`, route metadata i wzorzec status strip +
  panel uzywany przez istniejace ekrany.
- `MarkdownContentComponent` zapewnia wspolne, sanitizowane renderowanie
  Markdown przez DOMPurify.
- Worktree przed implementacja jest czysty.

## Conformance delta

- Cel: niemutowalna projekcja zwalidowanego, efektywnego katalogu runtime.
- Wlasciciel mechaniki odczytu: `aiplatform.copilot.runtime`.
- Wlasciciel HTTP i operator-facing DTO: `api.aiskills`.
- Publiczne API: dwa nowe endpointy GET pod `/api/ai/skills`; bez endpointow
  mutujacych i bez legacy aliasow.
- Frontend: nowy lazy route `/ai-skills` oraz `/ai-skills/:skillName` w grupie
  `Platform`; bez `capabilityInfo`, bo nie jest to Tool Workbench.
- Runtime sesji: bez zmian w rootach, allowliscie built-in `skill`, promptach,
  tools, policy, hidden context, reportach i wynikach feature'ow.
- Storage: bez zmian; API nie przyjmuje ani nie zwraca sciezki filesystemu.
- Kompatybilnosc: zmiana addytywna; dotychczasowi konsumenci nazw i katalogu
  zachowuja ten sam kontrakt.

## Reuse-first i capability gap analysis

| Potrzeba | Istniejacy mechanizm | Reuse bez zmian | Mala neutralna ekstrakcja | Nowa capability |
| --- | --- | --- | --- | --- |
| Efektywny katalog | `CopilotSkillRuntimeLoader` | materializacja i walidacja | immutable skill projection | nie |
| HTTP dla operatora | wzorzec `api.*` | globalne bledy API | nie | cienkie `api.aiskills` |
| Shell i nawigacja | `app-shell`, route data | tak | nie | nie |
| Render Markdown | `MarkdownContentComponent` | tak | nie | nie |
| Wyszukiwanie i filtry | feature-local page state | nie dotyczy | nie | lokalna projekcja UX |
| Copy | `copyTextToClipboard` | tak | nie | nie |
| Job, AI activity, report, chat, history | shared analysis UI | niepotrzebne | nie | nie |

## Konsumenci zmienianego mechanizmu

| Konsument | Wplyw |
| --- | --- |
| Copilot NEW sessions | nadal dostaja ten sam jeden root runtime |
| Copilot EXISTING sessions | nadal dostaja ten sam jeden root runtime |
| Incident Analysis | brak zmiany workflow i prompt guidance |
| Flow Explorer | brak zmiany workflow i prompt guidance |
| Change Verification | brak zmiany workflow i prompt guidance |
| Config Drift Viewer DEEP | brak zmiany workflow i prompt guidance |
| Shared/operator API | nowy read-only konsument projekcji katalogu |
| Angular AI Skills | nowy konsument listy i szczegolu |

## Kontrakt backend-frontend

### `GET /api/ai/skills`

Zwraca envelope `ai-skills.catalog` w wersji `1`, tryb `READ_ONLY`, zrodlo
`COPILOT_RUNTIME`, liczbe skilli oraz posortowane elementy:

```text
name, description, lineCount
```

### `GET /api/ai/skills/{skillName}`

Zwraca envelope `ai-skills.detail` w wersji `1`:

```text
mode, source, name, description, lineCount, markdown, rawMarkdown
```

`markdown` nie zawiera YAML frontmatter i jest renderowany przez wspolny
sanitizowany komponent. `rawMarkdown` odpowiada zwalidowanej tresci pliku.
`skillName` jest dopasowywane dokladnie do immutable katalogu; nie jest
rozwiazywane jako sciezka.

Brak nazwy zwraca kontrolowane `404` z kodem `AI_SKILL_NOT_FOUND`.

## Projekcja UX

- Status strip: efektywny katalog runtime, read-only, liczba skilli i format
  `SKILL.md`.
- Lista: wyszukiwarka po nazwie/opisie, filtr workflow, filtr
  odpowiedzialnosci, licznik wynikow i zwarte wiersze.
- Workflow jest wyprowadzany tylko do prezentacji z rozpoznanych prefiksow;
  nieznane nazwy trafiaja do `Other`.
- Odpowiedzialnosc jest pomocnicza etykieta wyprowadzana z nazwy; fallback to
  `Guidance`.
- Szczegol: powrot do katalogu, metadane, przelacznik Rendered/Raw, copy i
  sanitizowany Markdown.
- Bez edit, run, assign, upload oraz informacji o lokalnej sciezce.

## Macierz testow

- [x] loader: metadata, raw/body Markdown, line count i brak regresji rootu,
- [x] API service: mapowanie listy, dokladny lookup i controlled not found,
- [x] MockMvc: lista, szczegol oraz `404 AI_SKILL_NOT_FOUND`,
- [x] frontend API service: oba URL-e i typowany transport,
- [x] page: lista, wyszukiwanie/filtry, detail, rendered/raw, empty/error,
- [x] shell: route metadata, nawigacja `Platform` i aktywny deep link,
- [x] architecture guard: dozwolony kierunek `api -> aiplatform`,
- [x] produkcyjny frontend bundle i wspolny package backend-frontend.

## Kroki wykonania

- [x] Krok 1: Rozszerzyc loader o immutable projekcje zwalidowanych skilli i
  dodac read-only shared/operator API. Weryfikacja: testy loadera, service i
  MockMvc oraz brak zmian w kontrakcie session roots.
- [x] Krok 2: Dodac modele, serwis API, lazy routes, nawigacje i ekran katalogu
  oraz szczegolu zgodny z istniejacym shellem. Weryfikacja: testy serwisu,
  strony i `app.spec.ts`.
- [x] Krok 3: Zaktualizowac kanoniczne dokumenty ownershipu i podgladu skilli,
  wykonac architecture diff oraz sprawdzic brak niezatwierdzonych mutacji,
  sciezek i selekcji per feature.
- [x] Krok 4: Uruchomic testy Angulara, produkcyjny build UI oraz
  `mvn -q -Pbackend-dev clean package`; dopiero po pozytywnym wyniku oznaczyc
  kryteria i kroki jako wykonane.

## Rollback

Usuniecie nowych route'ow, API i projekcji katalogu przywraca baseline bez
migracji danych. Zmiana nie zapisuje zadnych danych ani nie modyfikuje
materializowanego katalogu.

## Wynik weryfikacji

- celowane testy loadera, service, MockMvc, strony, serwisu Angular i shella
  przeszly,
- `PackageDependencyGuardTest` przeszedl,
- pelny frontend: 47 plikow testowych, 353 testy, 0 bledow,
- produkcyjny `npm --prefix frontend run build` wygenerowal aktualny bundle,
- wizualny smoke test przeszedl dla desktopu, 760 px i 420 px; lista, detail,
  Raw i deep link nie zglosily bledow konsoli,
- `mvn -q -Pbackend-dev clean package` przeszedl: 1048 testow, 0 failures,
  0 errors; powstal `incident-tracker-0.0.1-SNAPSHOT.jar`.
