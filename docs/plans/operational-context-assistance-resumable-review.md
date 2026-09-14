# Wznawialny przeglad propozycji Operational Context Assistance

Status: done

Source need: [Operational Context AI-assisted maintenance](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Operator moze opuscic ekran po przygotowaniu propozycji AI, zanim zatwierdzi
zestaw. Historia pokazywala wtedy tylko wynik AI, a lokalny wybor pol i poprawki
znikaly. Powrot musi przywrocic roboczy przeglad i pozwolic dokonczyc decyzje.

## Proponowane rozwiazanie

Baseline: job i jego autoryzowany zakres sa aktywne w pamieci procesu;
LocalAnalysisRunStore utrwala snapshot, ale historia otwiera go read-only.
Wybor pol, potwierdzenia i poprawki sa tylko stanem komponentu.

Conformance delta: feature zapisuje typowany roboczy przeglad w snapshocie
joba, udostepnia osobny endpoint zapisu bez mutacji katalogu i wznawia
zakonczony, nierozstrzygniety job z lokalnego zapisu. UI przechowuje ostatnia
zmiane lokalnie do czasu potwierdzenia zapisu serwera. Preview i decyzja nadal
waliduja biezacy digest i oryginalny draft. Odrzucona alternatywa: samo
localStorage nie pozwala wznowic backendowej decyzji po restarcie procesu.

Konsumenci: API joba, feature-owned persister i eksport historii, ekran
Operational Context, testy feature'a. Neutralny format LocalAnalysisRunStore
i inne feature'y pozostaja bez zmian.

## Zakres

Zapis i odtworzenie roboczego wyboru, poprawek i potwierdzen; powrot z historii
do nierozstrzygnietego zestawu; ponowny preview przed zatwierdzeniem.

## Non-goals

Wieloosobowy workflow, rollback katalogu, ponowne uruchamianie AI i edycja
zatwierdzonego juz zestawu.

## Ograniczenia i ryzyka

Nie wolno przyjmowac nieznanych sciezek ani zmieniac zweryfikowanych pol
repozytorium. Starszy zapis bez bezpiecznego zakresu dla utworzenia
repozytorium pozostaje tylko do odczytu. Lokalny fallback w przegladarce
sluzy tylko do zachowania niezapisanych jeszcze zmian; zapis katalogu nadal
wymaga walidacji serwera i jawnej decyzji.

## Kryteria akceptacji

Po wyjsciu i powrocie widac ten sam wybor, reczne poprawki i potwierdzenia.
Nierozstrzygniety job daje sie podgladnac i zapisac takze po restarcie.
Rozstrzygniety job jest read-only. Nieudany zapis roboczy jest widoczny,
a poprawki nie znikaja.

## Kroki

- [x] Rozszerzono kontrakt i stan joba o walidowany zapis roboczy. MockMvc
  odrzuca nieznane pola, a test joba sprawdza niedozwolone poprawki i zapis po
  decyzji.
- [x] Wznowiono job z lokalnej historii z autoryzowanym zakresem. Testy joba i
  persistera sprawdzaja ponowne otwarcie oraz blokade starszego formatu bez
  metadanych zakresu repozytorium.
- [x] UI przywraca wybor i poprawki po powrocie z historii. Testy sprawdzaja
  ponowne otwarcie, finalne zatwierdzenie oraz wyjscie przed odpowiedzia
  serwera.
- [x] Zaktualizowano architekture i instrukcje lokalne. Weryfikacja:
  586 testow Angulara, build produkcyjny, `mvn -q -Pbackend-dev clean package`
  oraz celowane testy zmienionych scenariuszy backendowych przeszly.
