import type { FormTourStep } from '../../core/models/form-tour';

export const RELEASE_SUMMARY_IMPORT_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'release-summary-import',
    title: 'Import',
    description: 'Wczytaj wcześniej wyeksportowany raport Release summary w formacie JSON. Raport otworzy się w trybie odczytu.'
  }
];

export const RELEASE_SUMMARY_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'release-summary-application',
    title: 'Application',
    description: 'Wybierz aplikację, dla której chcesz opisać zmiany Liquibase. Porównanie obejmie jej jedno główne repozytorium.'
  },
  {
    anchor: 'release-summary-branch',
    title: 'Branch',
    description: 'Wskaż gałąź analizowanego wydania. Oba porównywane stany zostaną dobrane z tej samej gałęzi.'
  },
  {
    anchor: 'release-summary-from',
    title: 'Od',
    description: 'Podaj pierwszy dzień okresu. Stan początkowy wyznaczy ostatni commit sprzed początku tego dnia.'
  },
  {
    anchor: 'release-summary-to',
    title: 'Do',
    description: 'Podaj ostatni dzień okresu włącznie. Stan końcowy obejmie commity do końca tego dnia w strefie czasowej przeglądarki.'
  },
  {
    anchor: 'release-summary-preview',
    title: 'Podgląd commitów',
    description: 'Po wybraniu dat sprawdź pod nimi czasy, skróty SHA i opisy commitów. Identyczne SHA oznaczają brak zmian między stanami.'
  },
  {
    anchor: 'release-summary-ai',
    title: 'Model i poziom rozumowania',
    description: 'Możesz użyć dostępnego modelu domyślnego albo wybrać inny model i udostępniony poziom rozumowania.'
  },
  {
    anchor: 'release-summary-run',
    title: 'Generuj raport',
    description: 'Po sprawdzeniu podglądu uruchom analizę. Raport wyjaśni zmiany Liquibase i ich znaczenie dla działania aplikacji.'
  },
  ...RELEASE_SUMMARY_IMPORT_TOUR
];

export const RELEASE_SUMMARY_EMPTY_CATALOG_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'release-summary-application',
    title: 'Katalog aplikacji',
    description: 'Do porównania potrzebujesz aplikacji z jednym repozytorium primary o priorytecie 1 w Operational Context. Jeśli wystąpił błąd pobierania, użyj ponowienia przy polu.'
  },
  ...RELEASE_SUMMARY_IMPORT_TOUR
];
