import type { FormTourStep } from '../../core/models/form-tour';

export const CONFIG_DRIFT_VIEWER_FORM_TOUR: readonly FormTourStep[] = [
  {
    anchor: 'config-drift-mode',
    title: 'Tryb analizy',
    description: 'Dostępny tryb Basic porównuje konfigurację środowisk. Deep jest oznaczony jako przygotowywany.'
  },
  {
    anchor: 'config-drift-repository',
    title: 'Repozytorium konfiguracji',
    description: 'Wybierz repozytorium zawierające pliki konfiguracji, które chcesz porównać.'
  },
  {
    anchor: 'config-drift-components',
    title: 'Komponent / internal-service',
    description: 'Zaznacz jeden lub więcej komponentów. Domyślnie wybrane są wszystkie dostępne pozycje. Każdy komponent otrzyma własne porównanie.'
  },
  {
    anchor: 'config-drift-source',
    title: 'Środowisko źródłowe',
    description: 'Wybierz środowisko odniesienia. Jego konfiguracja znajdzie się po lewej stronie porównania.'
  },
  {
    anchor: 'config-drift-target',
    title: 'Środowisko docelowe',
    description: 'Wybierz drugie środowisko. Porównanie pokaże różnice względem konfiguracji źródłowej. Oba środowiska muszą być różne.'
  },
  {
    anchor: 'config-drift-run',
    title: 'Run verification',
    description: 'Uruchom porównanie po wybraniu repozytorium, komponentów i dwóch środowisk. Basic przygotuje zestawienie różnic konfiguracji.'
  },
  {
    anchor: 'config-drift-import',
    title: 'Import',
    description: 'Wczytaj wcześniej wyeksportowany wynik Config Drift Viewer w formacie JSON.'
  }
];
