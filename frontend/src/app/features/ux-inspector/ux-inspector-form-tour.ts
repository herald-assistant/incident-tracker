import type { FormTourStep } from '../../core/models/form-tour';

const CAPTURE_STEP: FormTourStep = {
  anchor: 'ux-inspector-capture',
  title: 'Element z Browser Tools',
  description: 'Sprawdź, czy nazwa elementu i ścieżka strony odpowiadają temu, co wskazano. Niżej możesz rozwinąć dostępne dane z przeglądarki.'
};

const IMPORT_STEP: FormTourStep = {
  anchor: 'ux-inspector-import',
  title: 'Import',
  description: 'Wczytaj wcześniej wyeksportowany raport UX Inspectora w formacie JSON.'
};

export const UX_INSPECTOR_SETUP_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'ux-inspector-browser-tools',
    title: 'TDW Browser Tools',
    description: 'Przeciągnij zakładkę na pasek zakładek Chrome. Otwórz badaną stronę, uruchom zakładkę, wybierz UX Inspector i wskaż element. TDW otworzy formularz analizy.'
  }
];

export const UX_INSPECTOR_FORM_TOUR: readonly FormTourStep[] = [
  CAPTURE_STEP,
  {
    anchor: 'ux-inspector-application',
    title: 'Application',
    description: 'Wybierz frontend odpowiadający stronie, z której przejęto element. Lista pochodzi z Operational Context.'
  },
  {
    anchor: 'ux-inspector-branch',
    title: 'Branch / Ref',
    description: 'Wybierz wersję kodu, która odpowiada badanemu środowisku albo zmianie.'
  },
  {
    anchor: 'ux-inspector-inventory',
    title: 'Load views',
    description: 'Załaduj lub odśwież listę widoków dla wybranej aplikacji i wersji kodu.'
  },
  {
    anchor: 'ux-inspector-view',
    title: 'View',
    description: 'Potwierdź podpowiedziany widok albo wybierz go po ścieżce. Z tego widoku UX Inspector zacznie szukać kodu elementu.'
  },
  {
    anchor: 'ux-inspector-ai',
    title: 'Model i poziom rozumowania',
    description: 'Potwierdź model. Jeśli udostępnia poziomy rozumowania, wybierz jeden z nich. Możesz zostawić wartości ustawione w formularzu.'
  },
  {
    anchor: 'ux-inspector-question',
    title: 'Pytanie lub polecenie',
    description: 'Zadaj jedno konkretne pytanie o wskazany element, np. „Kiedy przycisk Zapisz jest nieaktywny?”.'
  },
  {
    anchor: 'ux-inspector-run',
    title: 'Uruchom UX Inspector',
    description: 'Po uzupełnieniu pól uruchom analizę. Raport odwoła się do informacji z przeglądarki i do kodu wybranego widoku.'
  },
  IMPORT_STEP
];

export const UX_INSPECTOR_EMPTY_CATALOG_TOUR: readonly FormTourStep[] = [
  CAPTURE_STEP,
  {
    anchor: 'ux-inspector-empty-catalog',
    title: 'Dodaj frontend do katalogu',
    description: 'W Operational Context zarejestruj frontend i przypisz mu główne repozytorium, aby móc potwierdzić aplikację i widok.'
  },
  IMPORT_STEP
];
