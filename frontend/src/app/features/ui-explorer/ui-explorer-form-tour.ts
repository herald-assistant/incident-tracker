import type { FormTourStep } from '../../core/models/form-tour';

export const UI_EXPLORER_START_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'ui-explorer-application',
    title: 'Application',
    description: 'Wybierz aplikację, której ekran chcesz opisać. Lista zawiera frontendy zarejestrowane w Operational Context.'
  },
  {
    anchor: 'ui-explorer-branch',
    title: 'Branch / Ref',
    description: 'Wybierz wersję kodu, z której ma powstać dokumentacja. Zostaw podpowiedziany branch, jeśli zawiera interesujący Cię widok.'
  },
  {
    anchor: 'ui-explorer-inventory',
    title: 'Load views',
    description: 'Kliknij Load views, aby pobrać katalog dla wybranej aplikacji i brancha albo odświeżyć pobraną listę.'
  },
  {
    anchor: 'ui-explorer-view',
    title: 'View',
    description: 'Po załadowaniu katalogu znajdź i wybierz widok po ścieżce, np. /customers/:id. Nazwa komponentu pomaga rozróżnić podobne pozycje.'
  },
  {
    anchor: 'ui-explorer-sections',
    title: 'Section modes',
    description: 'Zostaw co najmniej jedną aktywną sekcję. Off pomija sekcję, Compact opisuje najważniejsze fakty, a Deep szerzej analizuje kod.'
  },
  {
    anchor: 'ui-explorer-ai',
    title: 'Model i poziom rozumowania',
    description: 'Do pierwszego uruchomienia możesz użyć ustawień domyślnych. Poziom rozumowania zależy od modelu i wpływa na sposób wykonania analizy.'
  },
  {
    anchor: 'ui-explorer-scenario',
    title: 'Doprecyzuj oczekiwany opis',
    description: 'Opcjonalnie wskaż scenariusz, który ma opisać dokumentacja. Możesz poprosić o opis walidacji podczas edycji danych kontaktu.'
  },
  {
    anchor: 'ui-explorer-run',
    title: 'Run UI Explorer',
    description: 'Gdy wybierzesz widok i pozostawisz co najmniej jedną aktywną sekcję, uruchom przygotowanie dokumentacji na podstawie kodu.'
  },
  {
    anchor: 'ui-explorer-import',
    title: 'Import',
    description: 'Wczytaj wcześniejszy wynik UI Explorera z wyeksportowanego pliku JSON.'
  }
];

export const UI_EXPLORER_EMPTY_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'ui-explorer-empty',
    title: 'Dodaj frontend do katalogu',
    description: 'Zarejestruj frontend i wskaż jego główne repozytorium w Operational Context. Po powrocie do UI Explorera będzie można wybrać aplikację i przejść przez formularz.'
  }
];
