# Wybor galezi w asyscie Operational Context

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator musi recznie znac i wpisywac galaz lub commit po wyborze projektu
GitLab. Flow Explorer ma juz selektor galezi z pobieraniem i filtrem. Ta sama
czynnosc w asyscie powinna korzystac z tego wzorca, rowniez dla projektu
wskazanego pelnym URL pod grupa z podgrupami.

## Baseline i conformance delta (L2)

- Baseline: asysta pobiera katalogowe podpowiedzi projektow, ale ref jest
  wolnym inputem z wartoscia domyslna z konfiguracji. Flow Explorer i UI
  Explorer uzywaja wspolnego `app-gitlab-branch-select` oraz endpointu
  systemowego, ktory wyprowadza primary repozytoria z katalogu.
- Delta: asysta odczytuje galezie jednego wybranego projektu przez osobny
  feature-owned endpoint z ta sama walidacja URL/grupy co collector. Wspolny
  selektor dostaje opcjonalne zrodlo i loader, bez zmiany wywolan Flow/UI
  Explorer. Reczny ref jest jawnym wariantem dla galezi lub commita.
- Konsumenci: Flow Explorer i UI Explorer zachowuja obecne `systemId` i API.
  Asysta korzysta ze wspolnego UI, a start joba nadal wysyla `ref` w tym samym
  kontrakcie. Integracja GitLab `listBranches` pozostaje bez zmian.

## Proponowane rozwiazanie

Reuse wspolnego selektora z wstrzyknietym przez asyste loaderem oraz
feature-owned odczytem galezi dla jednego projektu. Alternatywa kopiowania
selektora do asysty zwiekszalaby drift UI, a systemowy endpoint Flow Explorer
nie obsluguje nowego repozytorium spoza katalogu.

## Zakres

Endpoint listowania galezi wybranego projektu, picker w asyscie, reczny ref,
czyszczenie starego wyboru, testy i dokumentacja.

## Non-goals

Zmiana zrodla analizy, kontraktu joba, sposobu przypinania commita, discovery
wszystkich projektow GitLab oraz wyboru wielu repozytoriow.

## Ograniczenia i ryzyka

Odczyt jest ograniczony do skonfigurowanego origin/grupy, jednej strony do
100 galezi i filtra. Brak dostepu do listy galezi nie moze usunac recznej
mozliwosci podania refa. Zmiana projektu uniewaznia stary ref. Wspolny
komponent musi zachowac zachowanie Flow i UI Explorer.

## Kryteria akceptacji

Po wyborze projektu z katalogu lub wklejeniu pelnego URL asysta automatycznie
pokazuje galezie i pozwala je filtrowac. Wybor trafia do `gitLabSource.ref`.
Mozna recznie podac galaz lub commit. Zmiana projektu usuwa poprzedni ref.
Nieprawidlowy URL/grupa sa odrzucane przed odczytem GitLab. Istniejacy
Flow Explorer nadal dziala.

## Kroki

- [x] Krok 1: Ustalono baseline API, walidacji URL, selektora i konsumentow.
- [x] Krok 2: Dodano odczyt galezi i podlaczono wspolny selektor; testy
  celowane backendu oraz caly Angular test suite przeszly.
- [x] Krok 3: Przypadki URL z podgrupami i zmiany projektu pokryte testami.
  Angular: 560 testow i produkcyjny build. Maven `backend-dev clean package`:
  1530 testow, 0 bledow, 0 porazek, 1 pominiety. `git diff --check` bez bledow.
