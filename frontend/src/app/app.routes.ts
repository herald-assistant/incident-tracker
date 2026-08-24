import { Routes } from '@angular/router';

import { AppShellComponent } from './components/app-shell/app-shell';
import { AnalysisConsoleComponent } from './features/analysis-console/analysis-console';
import { DatabaseConsoleComponent } from './features/database-console/database-console';
import { ElasticEvidenceConsoleComponent } from './features/evidence-console/elastic-evidence-console';
import { GitLabEvidenceConsoleComponent } from './features/evidence-console/gitlab-evidence-console';
import { PlatformLandingPageComponent } from './features/platform-landing/platform-landing';
import { pendingAiSkillChangesGuard } from './features/ai-skills/guards/pending-ai-skill-changes.guard';

export const routes: Routes = [
  {
    path: '',
    component: AppShellComponent,
    children: [
      {
        path: '',
        component: PlatformLandingPageComponent,
        data: {
          section: 'Platform',
          title: 'Team Delivery Workspace'
        }
      },
      {
        path: 'incident-analysis',
        component: AnalysisConsoleComponent,
        data: {
          section: 'Analysis Features',
          title: 'Incident Analysis'
        }
      },
      {
        path: 'analysis-history',
        loadComponent: () =>
          import('./features/analysis-history/analysis-history-page').then(
            (module) => module.AnalysisHistoryPageComponent
          ),
        data: {
          section: 'Platform',
          title: 'Analysis History'
        }
      },
      {
        path: 'workspace-settings',
        loadComponent: () =>
          import('./features/workspace-settings/workspace-settings-page').then(
            (module) => module.WorkspaceSettingsPageComponent
          ),
        data: {
          section: 'Platform',
          title: 'Workspace Settings'
        }
      },
      {
        path: 'ai-skills/:skillName',
        loadComponent: () =>
          import('./features/ai-skills/pages/ai-skills-page/ai-skills-page').then(
            (module) => module.AiSkillsPageComponent
          ),
        canDeactivate: [pendingAiSkillChangesGuard],
        data: {
          section: 'Platform',
          title: 'AI Skills'
        }
      },
      {
        path: 'ai-skills',
        loadComponent: () =>
          import('./features/ai-skills/pages/ai-skills-page/ai-skills-page').then(
            (module) => module.AiSkillsPageComponent
          ),
        canDeactivate: [pendingAiSkillChangesGuard],
        data: {
          section: 'Platform',
          title: 'AI Skills'
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
        path: 'ui-explorer',
        loadComponent: () =>
          import('./features/ui-explorer/pages/ui-explorer-page/ui-explorer-page').then(
            (module) => module.UiExplorerPageComponent
          ),
        data: {
          section: 'Analysis Features',
          title: 'UI Explorer'
        }
      },
      {
        path: 'change-verification',
        loadComponent: () =>
          import(
            './features/change-verification/pages/change-verification-page/change-verification-page'
          ).then((module) => module.ChangeVerificationPageComponent),
        data: {
          section: 'Analysis Features',
          title: 'Change Verification'
        }
      },
      {
        path: 'config-drift-viewer',
        loadComponent: () =>
          import(
            './features/config-drift-viewer/pages/config-drift-viewer-page/config-drift-viewer-page'
          ).then((module) => module.ConfigDriftViewerPageComponent),
        data: {
          section: 'Analysis Features',
          title: 'Config Drift Viewer'
        }
      },
      {
        path: 'delivery-complexity-assessment',
        loadComponent: () =>
          import(
            './features/delivery-complexity-assessment/pages/delivery-complexity-assessment-page/delivery-complexity-assessment-page'
          ).then((module) => module.DeliveryComplexityAssessmentPageComponent),
        data: {
          section: 'Analysis Features',
          title: 'Delivery Complexity Assessment'
        }
      },
      {
        path: 'delivery-scope-complexity',
        loadComponent: () =>
          import(
            './features/delivery-scope-complexity/pages/delivery-scope-complexity-page/delivery-scope-complexity-page'
          ).then((module) => module.DeliveryScopeComplexityPageComponent),
        data: {
          section: 'Analysis Features',
          title: 'Delivery Scope Complexity'
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
              'Manualne testowanie GitLab helper API: repo discovery, MR discovery po Jira key, endpoint inventory, use-case context, odczyt plikow i source resolve.',
            badges: ['Reusable przez AI', 'Readonly'],
            meta: [
              { label: 'Endpointy', value: 'POST /api/gitlab/*' },
              {
                label: 'Wymagany scope',
                value: 'group, projectName, branch/ref albo source resolve groupPath, projectPath i symbol'
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
        path: 'jira',
        loadComponent: () =>
          import('./features/jira-source-console/jira-source-console').then(
            (module) => module.JiraSourceConsoleComponent
          ),
        data: {
          section: 'Tool Workbench',
          title: 'Jira Source',
          capabilityInfo: {
            description:
              'Manualne testowanie Jira issue source API: pobieranie materialu story/issue, linkow, komentarzy i skonfigurowanych kryteriow akceptacji.',
            badges: ['Reusable przez AI', 'Readonly'],
            meta: [
              { label: 'Endpointy', value: 'POST /api/jira/issue/material' },
              {
                label: 'Wymagany scope',
                value: 'issue key albo link do Jira issue; konfiguracja korzysta z base URL i personal access token'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, jako readonly issue material capability dla featureow takich jak Change Verification'
              },
              {
                label: 'Guardrails',
                value: 'readonly REST call, Bearer PAT, limity komentarzy/linkow/tekstu i jawne visibility limitations'
              }
            ]
          }
        }
      },
      {
        path: 'confluence',
        loadComponent: () =>
          import('./features/confluence-source-console/confluence-source-console').then(
            (module) => module.ConfluenceSourceConsoleComponent
          ),
        data: {
          section: 'Tool Workbench',
          title: 'Confluence Source',
          capabilityInfo: {
            description:
              'Manualne testowanie Confluence page source API: rozpoznawanie pageId i pobieranie tytulu, wersji oraz tekstu strony.',
            badges: ['Reusable przez AI', 'Readonly'],
            meta: [
              { label: 'Endpointy', value: 'POST /api/confluence/page/content' },
              {
                label: 'Wymagany scope',
                value: 'link do strony zgodny z URL pattern; konfiguracja korzysta z base URL i personal access token'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, jako readonly material kontekstowy pobierany przez featurey korzystajace z Jira remote links'
              },
              {
                label: 'Guardrails',
                value: 'readonly REST call, Bearer PAT, URL allowlist i limit dlugosci tekstu'
              }
            ]
          }
        }
      },
      {
        path: 'config-drift-viewer-tools',
        loadComponent: () =>
          import(
            './features/config-drift-viewer/workbench/config-drift-viewer-workbench-page/config-drift-viewer-workbench-page'
          ).then((module) => module.ConfigDriftViewerWorkbenchPageComponent),
        data: {
          section: 'Tool Workbench',
          title: 'Config Drift Viewer Pipeline',
          capabilityInfo: {
            description:
              'Readonly preview projekcji operatorskiej oraz — tylko w DEEP — mapowania, anonimizacji i dokładnego AI-safe inputu przygotowanego przez produkcyjny pipeline Config Drift Viewer.',
            badges: ['Operator preview', 'AI-safe boundary', 'Readonly'],
            meta: [
              {
                label: 'Endpoint',
                value: 'POST /api/config-drift-viewer/v1/workbench/preview'
              },
              {
                label: 'Wymagany scope',
                value:
                  'allowlistowane configuration repository, internal-service i dwa branche devX/zt00X'
              },
              {
                label: 'AI',
                value:
                  'BASIC nie generuje inputu AI; DEEP pokazuje dokładny sanitizowany prompt i artifacts, ale nie uruchamia modelu'
              },
              {
                label: 'Guardrails',
                value:
                  'dokładne wartości tylko w projekcji operatora; sanitizowany mapping i input AI, backendowy code-search scope i jawne visibility limits'
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
              { label: 'Endpointy', value: 'Read API + capability-driven maintenance API' },
              {
                label: 'Wymagany scope',
                value: 'Lokalna kopia katalogu w tdw-data/operational-context'
              },
              {
                label: 'Reusable przez AI',
                value: 'Tak, przez neutralne opctx_* tools bez incidentowego inputu'
              },
              {
                label: 'Guardrails',
                value: 'Walidacja domenowa i delete-impact bez cascade'
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
