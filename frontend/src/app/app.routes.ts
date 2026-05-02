import { Routes } from '@angular/router';

import { AnalysisConsoleComponent } from './features/analysis-console/analysis-console';
import { EvidenceConsoleComponent } from './features/evidence-console/evidence-console';

export const routes: Routes = [
  {
    path: '',
    component: AnalysisConsoleComponent
  },
  {
    path: 'evidence',
    component: EvidenceConsoleComponent
  },
  {
    path: 'operational-context',
    loadChildren: () =>
      import('./operational-context/operational-context.routes').then(
        (routesModule) => routesModule.operationalContextRoutes
      )
  },
  {
    path: '**',
    redirectTo: ''
  }
];
