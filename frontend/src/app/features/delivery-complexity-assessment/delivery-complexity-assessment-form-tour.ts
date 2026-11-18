import type { FormTourStep } from '../../core/models/form-tour';

export const DELIVERY_COMPLEXITY_ASSESSMENT_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'behavior-assessment-project',
    title: 'Projekt Jira',
    description: 'Wpisz klucz projektu, np. CRM. Behavior Change Assessment oceni dostarczone zmiany zachowania na podstawie zgłoszeń i powiązanych zmian kodu.'
  },
  {
    anchor: 'behavior-assessment-from',
    title: 'Od',
    description: 'Wybierz początek okresu, w którym zgłoszenia zostały zakończone.'
  },
  {
    anchor: 'behavior-assessment-to',
    title: 'Do',
    description: 'Wybierz ostatni dzień okresu włącznie. Daty wyznaczają zakres dostarczonych zmian do oceny.'
  },
  {
    anchor: 'behavior-assessment-run',
    title: 'Rozpocznij',
    description: 'Uruchom ocenę po wskazaniu projektu i dat. Platforma dobierze ustawienia AI i policzy Complexity Points dla dostarczonych zmian zachowania.'
  },
  {
    anchor: 'behavior-assessment-import',
    title: 'Importuj run',
    description: 'Wczytaj wyeksportowany plik JSON z wynikiem Behavior Change Assessment.'
  }
];
