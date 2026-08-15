package pl.mkn.tdw.integrations.gitlab.frontend;

import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CrmFrontendGitLabRepositoryPort implements GitLabRepositoryPort {

    private final Map<String, String> files;
    private final boolean branchExists;

    CrmFrontendGitLabRepositoryPort() {
        this(crmFiles(), true);
    }

    CrmFrontendGitLabRepositoryPort(Map<String, String> files, boolean branchExists) {
        this.files = new LinkedHashMap<>(files);
        this.branchExists = branchExists;
    }

    static Map<String, String> crmFiles() {
        var files = new LinkedHashMap<String, String>();
        files.put("package.json", """
                {
                  "name": "crm-agent-portal",
                  "dependencies": {
                    "@angular/core": "20.0.0",
                    "@angular/material": "20.0.0",
                    "@ngrx/store": "20.0.0",
                    "rxjs": "8.0.0",
                    "keycloak-angular": "20.0.0"
                  },
                  "devDependencies": { "nx": "21.0.0" }
                }
                """);
        files.put("angular.json", "{ \"projects\": { \"crm-agent\": {} } }");
        files.put("nx.json", "{ \"targetDefaults\": {} }");
        files.put("apps/crm-agent/project.json", "{ \"name\": \"crm-agent\" }");
        files.put("apps/crm-agent/src/app/app.routes.ts", """
                import { Routes } from '@angular/router';
                import { CrmDashboardComponent } from './dashboard/crm-dashboard.component';
                import { CrmAuthGuard } from './auth/crm-auth.guard';
                import { CrmRoleGuard } from './auth/crm-role.guard';

                export const appRoutes: Routes = [
                  {
                    path: 'contacts/:contactId',
                    canActivate: [CrmAuthGuard],
                    children: [
                      {
                        path: 'preferences',
                        loadComponent: () => import('./contact-preferences/crm-contact-preferences.component')
                          .then(module => module.CrmContactPreferencesComponent)
                      }
                    ]
                  },
                  { path: 'dashboard', component: CrmDashboardComponent },
                  {
                    path: 'segments',
                    canMatch: [CrmRoleGuard],
                    loadChildren: () => import('./segments/crm-segments.module')
                      .then(module => module.CrmSegmentsModule)
                  },
                  { path: 'legacy', redirectTo: 'contacts' },
                  { path: 'runtime-view', loadComponent: crmRuntimeViewFactory },
                  { path: 'runtime-area', loadChildren: crmRuntimeRoutesFactory },
                  ...crmRuntimeRoutes
                ];
                """);
        files.put("apps/crm-agent/src/app/dashboard/crm-dashboard.component.ts", """
                @Component({
                  selector: 'crm-dashboard',
                  standalone: true,
                  template: `<section>Strongly anonymized CRM dashboard</section>`,
                  styles: [`section { display: grid; }`]
                })
                export class CrmDashboardComponent {}
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts", """
                import { Component, inject } from '@angular/core';
                import { FormBuilder, Validators } from '@angular/forms';
                import { Store } from '@ngrx/store';
                import { CrmContactActions } from './state/crm-contact.actions';
                import { selectCrmContactPreferences } from './state/crm-contact.selectors';
                import { saveCrmContactPreferences } from './state/crm-contact.effects';
                import { crmContactReducer } from './state/crm-contact.reducer';
                import { CrmContactApi } from './services/crm-contact.api';
                import { CrmContactSocket } from './services/crm-contact.socket';
                import { CrmChannelControlComponent } from './controls/crm-channel-control.component';

                @Component({
                  selector: 'crm-contact-preferences',
                  standalone: true,
                  imports: [CrmChannelControlComponent],
                  templateUrl: './crm-contact-preferences.component.html',
                  styleUrls: ['./crm-contact-preferences.component.scss']
                })
                export class CrmContactPreferencesComponent {
                  private readonly formBuilder = inject(FormBuilder);
                  private readonly store = inject(Store);
                  private readonly contactApi = inject(CrmContactApi);
                  private readonly contactSocket = inject(CrmContactSocket);
                  readonly form = this.formBuilder.group({ channel: ['', Validators.required] });
                  readonly preferences$ = this.store.select(selectCrmContactPreferences);
                  readonly updates$ = this.contactSocket.updates$.pipe(switchMap(() => this.contactApi.getPreferences()));
                  save(): void { this.store.dispatch(CrmContactActions.save()); }
                  canEdit(): boolean { return this.hasPermission('CRM_CONTACT_EDIT'); }
                  private hasPermission(permission: string): boolean { return permission.length > 0; }
                }
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.html", """
                <form [formGroup]="form">
                  <crm-channel-control formControlName="channel" />
                  <button type="button" (click)="save()">Save synthetic CRM preference</button>
                </form>
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.scss",
                ":host { display: block; }");
        files.put("apps/crm-agent/src/app/contact-preferences/state/crm-contact.actions.ts", """
                export const CrmContactActions = {
                  save: createAction('[CRM Contact] Save Preferences')
                };
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/state/crm-contact.selectors.ts", """
                export const selectCrmContactPreferences = createSelector(selectCrmContactState, state => state.preferences);
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/state/crm-contact.effects.ts", """
                export const saveCrmContactPreferences = createEffect(() => actions$.pipe());
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/state/crm-contact.reducer.ts", """
                export const crmContactReducer = createReducer(initialCrmContactState);
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/services/crm-contact.api.ts", """
                import { CrmContactHttpFallback } from './crm-contact-http-fallback';
                export class CrmContactApi {
                  private readonly client = inject(ContactApiClient);
                  private readonly fallback = inject(CrmContactHttpFallback);
                  getPreferences(): Observable<CrmPreferences> { return this.client.getPreferences(); }
                }
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/services/crm-contact-http-fallback.ts", """
                export class CrmContactHttpFallback {
                  private readonly http = inject(HttpClient);
                  load(): Observable<CrmPreferences> { return this.http.get<CrmPreferences>('/api/crm/contact-preferences'); }
                }
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/services/crm-contact.socket.ts", """
                export class CrmContactSocket {
                  readonly updates$ = webSocket<CrmPreferenceUpdate>('wss://crm.invalid/contact-preferences');
                }
                """);
        files.put("apps/crm-agent/src/app/contact-preferences/controls/crm-channel-control.component.ts", """
                export class CrmChannelControlComponent implements ControlValueAccessor {
                  readonly formDefinition: FormSchema = { fields: [] };
                }
                """);
        files.put("apps/crm-agent/src/app/auth/crm-auth.guard.ts", """
                export const CrmAuthGuard = () => inject(KeycloakService).isLoggedIn();
                """);
        files.put("apps/crm-agent/src/app/auth/crm-role.guard.ts", """
                export const CrmRoleGuard = () => inject(CrmPermissionService).hasRole('CRM_SEGMENT_VIEW');
                """);
        files.put("apps/crm-agent/src/app/segments/crm-segments.module.ts", """
                import { CrmSegmentsRoutingModule } from './crm-segments-routing.module';
                @NgModule({ imports: [CrmSegmentsRoutingModule] })
                export class CrmSegmentsModule {}
                """);
        files.put("apps/crm-agent/src/app/segments/crm-segments-routing.module.ts", """
                import { CrmSegmentComparisonComponent } from './crm-segment-comparison.component';
                const routes: Routes = [
                  { path: '', component: CrmSegmentComparisonComponent }
                ];
                @NgModule({ imports: [RouterModule.forChild(routes)] })
                export class CrmSegmentsRoutingModule {}
                """);
        files.put("apps/crm-agent/src/app/segments/crm-segment-comparison.component.ts", """
                @Component({
                  template: `<section>Strongly anonymized CRM segment comparison</section>`
                })
                export class CrmSegmentComparisonComponent {}
                """);
        return Map.copyOf(files);
    }

    @Override
    public boolean branchExists(String group, String projectName, String branch) {
        return branchExists;
    }

    @Override
    public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
        return List.of();
    }

    @Override
    public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
        return List.of();
    }

    @Override
    public List<GitLabRepositoryFile> listRepositoryFiles(
            String group,
            String projectName,
            String branch,
            String pathPrefix
    ) {
        return files.keySet().stream()
                .filter(path -> pathPrefix == null || path.equals(pathPrefix) || path.startsWith(pathPrefix + "/"))
                .map(path -> new GitLabRepositoryFile(group, projectName, branch, path))
                .toList();
    }

    @Override
    public GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String filePath,
            int maxCharacters
    ) {
        var content = files.get(filePath);
        if (content == null) {
            throw new IllegalArgumentException("Synthetic CRM fixture file not found: " + filePath);
        }
        var limit = Math.max(1, maxCharacters);
        var truncated = content.length() > limit;
        return new GitLabRepositoryFileContent(
                group,
                projectName,
                branch,
                filePath,
                truncated ? content.substring(0, limit) : content,
                truncated
        );
    }

    @Override
    public GitLabRepositoryFileMetadata readFileMetadata(
            String group,
            String projectName,
            String branch,
            String filePath
    ) {
        return new GitLabRepositoryFileMetadata(
                group,
                projectName,
                branch,
                filePath,
                "crm-blob-123",
                "crm-commit-abc123",
                "crm-commit-abc123",
                "2026-08-15T08:00:00Z",
                "crm-content-sha-123",
                (long) files.get(filePath).length()
        );
    }

    @Override
    public GitLabRepositoryFileChunk readFileChunk(
            String group,
            String projectName,
            String branch,
            String filePath,
            int startLine,
            int endLine,
            int maxCharacters
    ) {
        throw new UnsupportedOperationException("Synthetic CRM fixture does not use chunk reads.");
    }
}
