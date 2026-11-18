import type { FormTourStep } from '../../core/models/form-tour';

const IMPORT_STEP: FormTourStep = {
  anchor: 'flow-explorer-import',
  title: 'Import',
  description: 'Wczytaj wcześniej wyeksportowany wynik Flow Explorera w formacie JSON.'
};

export const FLOW_EXPLORER_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'flow-explorer-application',
    title: 'Application',
    description: 'Wybierz aplikację, która udostępnia badany endpoint. Lista pochodzi z Operational Context.'
  },
  {
    anchor: 'flow-explorer-branch',
    title: 'Branch / Ref',
    description: 'Wskaż wersję kodu, z której Flow Explorer ma odczytać endpoint i ścieżkę wykonania.'
  },
  {
    anchor: 'flow-explorer-inventory',
    title: 'Load endpoints',
    description: 'Załaduj lub odśwież katalog endpointów dla wybranej aplikacji i gałęzi.'
  },
  {
    anchor: 'flow-explorer-endpoint',
    title: 'Endpoint',
    description: 'Wybierz punkt wejścia po metodzie i ścieżce, np. GET /customers. Od tej operacji zacznie się opis przepływu.'
  },
  {
    anchor: 'flow-explorer-goal',
    title: 'Goal',
    description: 'Dostępny cel Deep Discovery opisuje przebieg operacji, decyzje, dane i integracje.'
  },
  {
    anchor: 'flow-explorer-sections',
    title: 'Section modes',
    description: 'Dostosuj zakres opisu. Off pomija sekcję, Compact zbiera najważniejsze fakty, a Deep nadaje sekcji priorytet w analizie.'
  },
  {
    anchor: 'flow-explorer-ai',
    title: 'Model i poziom rozumowania',
    description: 'Na początek użyj ustawień domyślnych. Dostępny poziom rozumowania zależy od wybranego modelu.'
  },
  {
    anchor: 'flow-explorer-instructions',
    title: 'Doprecyzuj oczekiwany opis',
    description: 'Opcjonalnie wskaż zachowanie, które chcesz lepiej zrozumieć. Możesz poprosić o szczegóły walidacji statusu klienta.'
  },
  {
    anchor: 'flow-explorer-run',
    title: 'Run Flow Explorer',
    description: 'Po wybraniu endpointu uruchom analizę. Wynik opisze działanie wybranej operacji na podstawie kodu.'
  },
  IMPORT_STEP
];

export const FLOW_EXPLORER_EMPTY_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'flow-explorer-application',
    title: 'Dodaj aplikację do katalogu',
    description: 'Zarejestruj system i powiąż repozytorium w Operational Context. Następnie wróć do Flow Explorera i wybierz aplikację.'
  },
  IMPORT_STEP
];
