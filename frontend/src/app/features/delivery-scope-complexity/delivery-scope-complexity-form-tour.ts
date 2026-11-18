import type { FormTourStep } from '../../core/models/form-tour';

export const DELIVERY_SCOPE_COMPLEXITY_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'technical-assessment-project',
    title: 'Projekt Jira',
    description: 'Wpisz klucz projektu, np. CRM. Technical Change Assessment oceni złożoność i zakres dostarczonych zmian technicznych.'
  },
  {
    anchor: 'technical-assessment-from',
    title: 'Od',
    description: 'Wybierz początek okresu, w którym zgłoszenia zostały zakończone.'
  },
  {
    anchor: 'technical-assessment-to',
    title: 'Do',
    description: 'Wybierz ostatni dzień okresu włącznie. Analiza obejmie powiązane zmiany techniczne dostarczone w tym okresie.'
  },
  {
    anchor: 'technical-assessment-run',
    title: 'Rozpocznij',
    description: 'Uruchom ocenę po wskazaniu projektu i dat. Platforma dobierze ustawienia AI i policzy Complexity Points według złożoności i zakresu zmiany technicznej.'
  },
  {
    anchor: 'technical-assessment-import',
    title: 'Importuj run',
    description: 'Wczytaj wyeksportowany plik JSON z wynikiem Technical Change Assessment.'
  }
];
