import { Routes } from '@angular/router';

import { AppShellComponent } from './components/app-shell/app-shell';
import { AnalysisConsoleComponent } from './features/analysis-console/analysis-console';
import { DatabaseConsoleComponent } from './features/database-console/database-console';
import { ElasticEvidenceConsoleComponent } from './features/evidence-console/elastic-evidence-console';
import { GitLabEvidenceConsoleComponent } from './features/evidence-console/gitlab-evidence-console';

export const routes: Routes = [
  {
    path: '',
    component: AppShellComponent,
    children: [
      {
        path: '',
        component: AnalysisConsoleComponent,
        data: {
          section: 'Analysis Features',
          title: 'Incident Analysis'
        }
      },
      {
        path: 'evidence',
        redirectTo: 'elastic',
        pathMatch: 'full'
      },
      {
        path: 'elastic',
        component: ElasticEvidenceConsoleComponent,
        data: {
          section: 'Tool Workbench',
          title: 'Elastic Logs'
        }
      },
      {
        path: 'gitlab',
        component: GitLabEvidenceConsoleComponent,
        data: {
          section: 'Tool Workbench',
          title: 'GitLab Source'
        }
      },
      {
        path: 'database',
        component: DatabaseConsoleComponent,
        data: {
          section: 'Tool Workbench',
          title: 'Database Tools'
        }
      },
      {
        path: 'operational-context',
        data: {
          section: 'Tool Workbench',
          title: 'Operational Context'
        },
        loadChildren: () =>
          import('./operational-context/operational-context.routes').then(
            (routesModule) => routesModule.operationalContextRoutes
          )
      },
      {
        path: '**',
        redirectTo: ''
      }
    ]
  }
];
