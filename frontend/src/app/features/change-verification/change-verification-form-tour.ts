import type { FormTourStep } from '../../core/models/form-tour';

export const CHANGE_VERIFICATION_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'change-verification-jira',
    title: 'Jira key or link',
    description: 'Wklej klucz Jira, np. CRM-123, lub link do zgłoszenia opisującego zmianę. Z tego zgłoszenia aplikacja dobierze materiał do weryfikacji.'
  },
  {
    anchor: 'change-verification-scope',
    title: 'Verification scope',
    description: 'Story rules sprawdza wymagania z Jira i powiązanych stron Confluence. Repository instructions sprawdza zasady z instrukcji repozytorium. Zaznacz co najmniej jeden zakres.'
  },
  {
    anchor: 'change-verification-ai',
    title: 'Model i poziom rozumowania',
    description: 'Na początek możesz zostawić wartości domyślne. Przy zmianie modelu wybierz jeden z udostępnionych poziomów rozumowania.'
  },
  {
    anchor: 'change-verification-notes',
    title: 'Operator notes',
    description: 'Opcjonalnie podaj kontekst wydania lub obszar, któremu weryfikacja ma poświęcić uwagę.'
  },
  {
    anchor: 'change-verification-run',
    title: 'Run Verification',
    description: 'Po wskazaniu zgłoszenia i zakresu uruchom weryfikację. Wynik pokaże ustalenia dla poszczególnych reguł oraz ich wpływ na wydanie.'
  },
  {
    anchor: 'change-verification-import',
    title: 'Import',
    description: 'Wczytaj wcześniej wyeksportowany wynik Change Verification w formacie JSON.'
  }
];
