# Temporary UI block for Runtime Configuration Verification DEEP

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Tryb `DEEP` pozostaje zaimplementowany w backendzie i w kontrakcie wyniku, ale
nie powinien byc obecnie dostepny do uruchomienia z glownego ekranu feature'a.
Operator ma widziec, ze capability jest planowana, bez mozliwosci wybrania jej
przed zakonczeniem rollout readiness.

## Proponowane rozwiazanie

Zablokowac przycisk opcji `DEEP` w formularzu Runtime Configuration
Verification i pokazac w jego prawym gornym rogu badge `SOON`, zgodny wizualnie
z `app-shell__nav-badge`. Nie zmieniac backendu, publicznych DTO ani renderowania
historycznych i importowanych wynikow `DEEP`.

## Zakres

- lokalny formularz Runtime Configuration Verification,
- stan disabled i dostepna etykieta opcji `DEEP`,
- badge `SOON`,
- test komponentu potwierdzajacy brak mozliwosci wyboru.

## Non-goals

- usuniecie `DEEP` z backendu lub `input-options`,
- zmiana job API, historii, importu/eksportu albo Workbencha,
- usuniecie renderera istniejacych wynikow `DEEP`.

## Ograniczenia i ryzyka

Blokada jest tymczasowym affordance UI, a nie granica bezpieczenstwa. Backend
zachowuje obecny kontrakt, aby nie psuc zapisanych wynikow i dalszego rozwoju
capability.

## Kryteria akceptacji

- `BASIC` pozostaje domyslny i mozna go uruchomic,
- opcja `DEEP` jest widoczna, ale disabled,
- badge `SOON` znajduje sie w prawym gornym rogu opcji i odpowiada stylowi
  badge'a nawigacji,
- klikniecie `DEEP` nie uruchamia preflightu ani nie zmienia trybu,
- historyczny/importowany wynik `DEEP` nadal moze zostac wyswietlony.

## Baseline i conformance delta

- Baseline: UI oferuje `BASIC` i `DEEP`; backend oraz zapisane wyniki obsluguja
  oba tryby.
- Delta UI: nowy wybor `DEEP` jest tymczasowo niedostepny i oznaczony `SOON`.
- Publiczne API/DTO, job lifecycle, deterministic result, AI runtime, tools,
  persistence, import/export i security boundary: bez zmian.
- Konsumenci: tylko glowny komponent strony i jego test. Workbench, backend i
  renderowanie wyniku pozostaja nietkniete.
- Ownership i graf zaleznosci: bez zmian; modyfikacja pozostaje w frontendzie
  feature'a.

## Kroki

- [x] Krok 1: Zablokowac wybor `DEEP`, dodac badge `SOON`, zachowac obsluge
  istniejacych wynikow `DEEP`, dodac test regresyjny i wykonac celowana
  weryfikacje frontendu oraz drift check.

  Weryfikacja 2026-08-01: opcja `DEEP` jest disabled, ma dostepna etykiete
  `Deep — wkrotce` i badge `SOON`; klikniecie pozostawia `BASIC` i nie wywoluje
  preflightu. Istniejace testy uruchomienia oraz prezentacji wyniku `DEEP`
  pozostaly aktywne przez kontrolowane wlaczenie capability w tescie. Przeszlo
  `npm test -- --watch=false` (226 testow), `npm run build` oraz
  `mvn -q "-Dtest=PackageDependencyGuardTest" test` i `git diff --check`.
  Architecture diff potwierdzil brak zmian API, DTO, backendu, Workbencha,
  historii/importu i grafu zaleznosci.
