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
        path: 'flow-explorer',
        loadComponent: () =>
          import('./features/flow-explorer/pages/flow-explorer-page/flow-explorer-page').then(
            (module) => module.FlowExplorerPageComponent
          ),
        data: {
          section: 'Analysis Features',
          title: 'Flow Explorer'
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
          title: 'Elastic Logs',
          capabilityInfo: {
            description:
              'Manualne testowanie helper API dla Elasticsearch/Kibana. Widok sluzy do debugowania logow i przygotowania inputu bez logiki konkretnej analizy.',
            badges: ['Reusable przez AI', 'Readonly'],
            meta: [
              { label: 'Endpointy', value: 'POST /api/elasticsearch/logs/*' },
              {
                label: 'Wymagany scope',
                value: 'correlationId albo HTTP path/status/metoda oraz okno czasu'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, ale sesja AI dostaje zakres przez feature-owned policy i ToolContext'
              },
              {
                label: 'Guardrails',
                value: 'Kibana space, index pattern, auth, readonly query i backendowe limity'
              }
            ]
          }
        }
      },
      {
        path: 'gitlab',
        component: GitLabEvidenceConsoleComponent,
        data: {
          section: 'Tool Workbench',
          title: 'GitLab Source',
          capabilityInfo: {
            description:
              'Manualne testowanie GitLab helper API: repo discovery, endpoint inventory, use-case context, odczyt plikow i source resolve.',
            badges: ['Reusable przez AI', 'Readonly'],
            meta: [
              { label: 'Endpointy', value: 'POST /api/gitlab/*' },
              {
                label: 'Wymagany scope',
                value: 'group, projectName, branch/ref albo source resolve base URL i symbol'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, ale sesja AI dostaje scope przez feature-owned hidden ToolContext'
              },
              {
                label: 'Guardrails',
                value: 'configured GitLab auth, readonly REST calls, scan limits i character budgets'
              }
            ]
          }
        }
      },
      {
        path: 'database',
        component: DatabaseConsoleComponent,
        data: {
          section: 'Tool Workbench',
          title: 'Database Tools',
          capabilityInfo: {
            description:
              'Manualne uruchamianie typed DB capability z tym samym zakresem i guardrailami, ktorych uzywa warstwa AI.',
            badges: ['Reusable przez AI', 'Readonly'],
            meta: [
              { label: 'Endpointy', value: 'POST /api/database/*' },
              {
                label: 'Wymagany scope',
                value: 'environment wybiera skonfigurowane polaczenie i dozwolone schematy'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, ale scope sesji AI pochodzi z hidden ToolContext poza tym API'
              },
              {
                label: 'Guardrails',
                value: 'configured environments, allowlista schematow, typed filters, masking i limity'
              }
            ]
          }
        }
      },
      {
        path: 'operational-context',
        data: {
          section: 'Tool Workbench',
          title: 'Operational Context',
          capabilityInfo: {
            description:
              'Manualne przegladanie i walidacja Operational Context jako reusable capability dla featureow i tooli, bez logiki konkretnej analizy.',
            badges: ['Reusable przez AI', 'Catalog'],
            meta: [
              { label: 'Endpointy', value: 'GET /api/operational-context/*' },
              {
                label: 'Wymagany scope',
                value: 'Konfiguracja katalogu operational-context z zasobow aplikacji'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, przez neutralne opctx_* tools bez incidentowego inputu'
              },
              {
                label: 'Guardrails',
                value: 'Readonly catalog view, walidacja katalogu i jawne open questions'
              }
            ]
          }
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
