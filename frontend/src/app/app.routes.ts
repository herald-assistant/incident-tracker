import { Routes } from '@angular/router';

import { AnalysisConsoleComponent } from './features/analysis-console/analysis-console';
import { DatabaseConsoleComponent } from './features/database-console/database-console';
import { EvidenceConsoleComponent } from './features/evidence-console/evidence-console';

export const routes: Routes = [
  {
    path: '',
    component: AnalysisConsoleComponent
  },
  {
    path: 'evidence',
    redirectTo: 'elastic',
    pathMatch: 'full'
  },
  {
    path: 'elastic',
    component: EvidenceConsoleComponent,
    data: { integration: 'elastic' }
  },
  {
    path: 'gitlab',
    component: EvidenceConsoleComponent,
    data: { integration: 'gitlab' }
  },
  {
    path: 'database',
    component: DatabaseConsoleComponent
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
