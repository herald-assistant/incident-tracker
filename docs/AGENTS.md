# AGENTS

## Zakres

Te instrukcje obowiazuja dla calego katalogu `docs/`.

Dokumentacja ma rozdzielac trzy rozne odpowiedzialnosci:

- `architecture/` opisuje obowiazujacy albo jawnie zatwierdzony docelowy stan
  systemu,
- `needs/` opisuje problem oraz wartosc dla uzytkownika lub biznesu,
- `plans/` opisuje proponowany sposob dostarczenia zmiany albo aktywny backlog.

Nie lacz tych odpowiedzialnosci w jednym pliku. Git jest historia zmian;
kanoniczna dokumentacja nie powinna byc kronika dochodzenia do obecnego stanu.

## `architecture/`

Dokument architektoniczny moze zawierac:

- trwale decyzje i niezmienniki,
- ownership, granice pakietow i dozwolone kierunki zaleznosci,
- publiczne kontrakty i runtime flow,
- zasady bezpieczenstwa, reuse'u, rozszerzalnosci i utrzymania,
- aktualny stan implementacji, jezeli jest kanonicznym opisem zachowania.

Niezatwierdzony wariant, eksperyment albo mozliwy przyszly target pozostaje w
`plans/`. Nie zapisuj go w `architecture/` jezykiem podjetej decyzji.

Nie umieszczaj tutaj:

- list krokow implementacji,
- checklist `[ ]` / `[x]` sledzacych wykonanie konkretnej zmiany,
- kolejnosci przyszlych PR-ow,
- aktywnego backlogu,
- historii zakonczonej migracji,
- otwartych propozycji udajacych podjeta decyzje.

Wielokrotnego uzytku checklisty preflight, review i Definition of Done moga
pozostac w kanonicznym playbooku, jezeli nie zapisuja statusu konkretnego
planu.

Po zakonczeniu planu zaktualizuj odpowiedni dokument w `architecture/`, aby
opisywal wynikowy stan, a nie przebieg prac.

## `needs/`

Dokument potrzeby odpowiada przede wszystkim na pytania:

- jaki problem wystepuje,
- kto go odczuwa i w jakim momencie,
- jaka wartosc ma zostac dostarczona,
- jaki rezultat jest oczekiwany,
- jak zmierzymy sukces,
- jakie sa ograniczenia, ryzyka, non-goals i decyzje produktowe do
  doprecyzowania.

Dokument potrzeby nie wybiera technicznego rozwiazania. Nazwy klas, pakietow,
komponentow, endpointow, tooli i kolejnosc implementacji moga pojawic sie
wylacznie wtedy, gdy sa rzeczywistym ograniczeniem biznesowym, regulacyjnym,
bezpieczenstwa albo kompatybilnosci.

## `plans/`

Plan jest propozycja wykonania zmiany, a nie zrodlem prawdy o aktualnej
architekturze. Kazdy plan musi:

- wskazac potrzebe, ktora zaspokaja, oraz wyjasnic `dlaczego`,
- linkowac do `../needs/<nazwa>.md`, jezeli wynika z opisanej potrzeby
  biznesowej,
- opisac proponowane rozwiazanie, reuse obecnych mechanizmow i alternatywy,
- zawierac zakres, non-goals, ograniczenia, ryzyka i kryteria akceptacji,
- miec kroki wykonawcze zapisane jako checklista `[ ]` / `[x]`,
- pozwalac wykonac i zweryfikowac jeden krok bez zgadywania zakresu kolejnego,
- okreslac testy albo inny dowod wykonania kazdego kroku.

Jesli plan nie wynika z osobnego dokumentu w `needs/`, musi jawnie nazwac
potrzebe techniczna lub produktowa w sekcji `Potrzeba / dlaczego`. Nie tworz
sztucznej potrzeby biznesowej tylko po to, aby miec link.

## Wymagany naglowek planu

Kazdy nowy plan zaczyna sie od metadanych:

```markdown
# <Nazwa planu>

Status: draft | approved | in-progress | blocked | done | superseded

Source need: [<nazwa>](../needs/<nazwa>.md) | brak osobnego dokumentu

## Potrzeba / dlaczego

<Problem i wartosc, ktore uzasadniaja prace.>

## Proponowane rozwiazanie

<Podejscie, reuse istniejacych mechanizmow, alternatywy i trade-offy.>

## Zakres

<Co nalezy do planu.>

## Non-goals

<Czego plan swiadomie nie obejmuje.>

## Ograniczenia i ryzyka

<Granice bezpieczenstwa, kompatybilnosc, zaleznosci i ryzyka.>

## Kryteria akceptacji

<Warunki, po ktorych mozna uznac zakres za dostarczony.>

## Kroki

- [ ] Krok 1: <zmiana, wynik, weryfikacja i kryterium akceptacji>
- [ ] Krok 2: <zmiana, wynik, weryfikacja i kryterium akceptacji>
```

Sekcje opisowe moga uzywac zwyklych list. Kazda czynnosc implementacyjna,
migracyjna, dokumentacyjna i weryfikacyjna musi jednak byc elementem
checklisty.

## Zatwierdzanie i wykonywanie planu

- `draft` nie upowaznia do implementacji.
- Przed rozpoczeciem pierwszego niezaznaczonego kroku przedstaw
  uzytkownikowi jego zakres, oczekiwany wynik, ryzyka i weryfikacje.
- Rozpocznij krok dopiero po jawnym zatwierdzeniu przez uzytkownika.
- Po wykonaniu kroku oznacz go `[x]`, dopisz albo podaj dowod weryfikacji i
  przedstaw kolejny krok do zatwierdzenia.
- Nie oznaczaj kroku `[x]`, jezeli nie spelniono jego kryterium akceptacji.
- Zatwierdzenie calego planu albo jawnie wskazanego zakresu krokow jest
  zatwierdzeniem wszystkich krokow w tym zakresie. Poza takim przypadkiem
  zatwierdzenie jednego kroku nie obejmuje nastepnych.
- Gdy w trakcie pracy zmienia sie zakres, ryzyko, kontrakt publiczny albo
  rozwiazanie, najpierw zaktualizuj plan i uzyskaj ponowne zatwierdzenie
  dotknietych krokow.
- Kroki zablokowane pozostaja `[ ]`; przyczyna blokady jest opisana obok kroku
  lub w osobnej sekcji.

## Zakonczenie i porzadkowanie

Po wykonaniu planu:

1. oznacz wszystkie rzeczywiscie wykonane kroki `[x]`,
2. ustaw `Status: done`,
3. przenies trwale decyzje i wynikowy stan do `architecture/`,
4. zaktualizuj albo zamknij powiazany dokument w `needs/`, jezeli zmienil sie
   zakres potrzeby,
5. usun plan, gdy nie pelni juz funkcji operacyjnej; historie zachowuje Git.

Plan `superseded` musi wskazywac dokument, ktory go zastapil. Aktywny backlog
moze pozostac w `plans/`, ale kazdy jego element musi miec nazwane `dlaczego`,
status checklisty i te same bramki zatwierdzania.

## Nazwy i linki

- Uzywaj nazw `kebab-case.md` bez prefiksow numerycznych.
- Kolejnosc czytania utrzymuj w `docs/README.md`, nie w nazwach plikow.
- Linki z planow do potrzeb zapisuj relatywnie, np.
  `../needs/change-verification.md`.
- Po przeniesieniu lub zmianie nazwy pliku zaktualizuj wszystkie referencje w
  repozytorium i sprawdz, czy nie pozostaly martwe linki.
