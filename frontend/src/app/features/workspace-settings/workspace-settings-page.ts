import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';
import { finalize } from 'rxjs';

import {
  SourceStatePillComponent,
  type SourceState
} from '../../components/source-state-pill/source-state-pill';
import {
  WorkspaceSettingsField,
  WorkspaceSettingsResponse,
  WorkspaceSettingsUpdateRequest
} from '../../core/models/workspace-settings.models';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { WorkspaceSettingsApiService } from '../../core/services/workspace-settings-api.service';

@Component({
  selector: 'app-workspace-settings-page',
  imports: [ReactiveFormsModule, MatTooltipModule, SourceStatePillComponent],
  templateUrl: './workspace-settings-page.html',
  styleUrl: './workspace-settings-page.scss'
})
export class WorkspaceSettingsPageComponent {
  private readonly settingsApi = inject(WorkspaceSettingsApiService);
  private readonly uiConfig = inject(AppUiConfigService);
  private readonly destroyRef = inject(DestroyRef);

  readonly settings = signal<WorkspaceSettingsResponse | null>(null);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly errorMessage = signal('');
  readonly saveMessage = signal('');
  readonly showToken = signal(false);
  readonly showConfigDriftViewerGitLabToken = signal(false);
  readonly showCopilotGithubToken = signal(false);
  readonly showJiraToken = signal(false);
  readonly showConfluenceToken = signal(false);
  readonly showElasticsearchAuthorizationHeader = signal(false);
  readonly showDynatraceApiToken = signal(false);

  readonly form = new FormGroup({
    appUi: new FormGroup({
      title: new FormControl('', { nonNullable: true })
    }),
    copilot: new FormGroup({
      localGithubToken: new FormControl('', { nonNullable: true })
    }),
    jira: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      token: new FormControl('', { nonNullable: true })
    }),
    confluence: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      token: new FormControl('', { nonNullable: true })
    }),
    gitLab: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      group: new FormControl('', { nonNullable: true }),
      token: new FormControl('', { nonNullable: true })
    }),
    configDriftViewerGitLab: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      token: new FormControl('', { nonNullable: true })
    }),
    elasticsearch: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      kibanaSpaceId: new FormControl('', { nonNullable: true }),
      indexPattern: new FormControl('', { nonNullable: true }),
      authorizationHeader: new FormControl('', { nonNullable: true })
    }),
    dynatrace: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      apiToken: new FormControl('', { nonNullable: true })
    })
  });

  readonly overrideCount = computed(() => {
    const settings = this.settings();
    if (!settings) {
      return 0;
    }
    return this.fields(settings).filter((field) => field.source === 'WORKSPACE_SETTINGS').length;
  });

  readonly workspaceStatusLabel = computed(() =>
    this.settings()?.workspaceEnabled ? 'Enabled' : 'Disabled'
  );

  constructor() {
    this.loadSettings();
  }

  loadSettings(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.saveMessage.set('');

    this.settingsApi
      .getSettings()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false))
      )
      .subscribe({
        next: (settings) => this.applySettings(settings),
        error: (error) => {
          this.errorMessage.set(toErrorMessage(error, 'Nie udało się odczytać ustawień workspace.'));
        }
      });
  }

  save(event: Event): void {
    event.preventDefault();
    this.isSaving.set(true);
    this.errorMessage.set('');
    this.saveMessage.set('');

    this.settingsApi
      .saveSettings(this.requestFromForm())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false))
      )
      .subscribe({
        next: (settings) => {
          this.applySettings(settings);
          this.uiConfig.reload();
          this.saveMessage.set('Settings saved.');
        },
        error: (error) => {
          this.errorMessage.set(toErrorMessage(error, 'Nie udało się zapisać ustawień workspace.'));
        }
      });
  }

  toggleTokenVisibility(): void {
    this.showToken.update((visible) => !visible);
  }

  toggleConfigDriftViewerGitLabTokenVisibility(): void {
    this.showConfigDriftViewerGitLabToken.update((visible) => !visible);
  }

  toggleCopilotGithubTokenVisibility(): void {
    this.showCopilotGithubToken.update((visible) => !visible);
  }

  toggleJiraTokenVisibility(): void {
    this.showJiraToken.update((visible) => !visible);
  }

  toggleConfluenceTokenVisibility(): void {
    this.showConfluenceToken.update((visible) => !visible);
  }

  toggleElasticsearchAuthorizationHeaderVisibility(): void {
    this.showElasticsearchAuthorizationHeader.update((visible) => !visible);
  }

  toggleDynatraceApiTokenVisibility(): void {
    this.showDynatraceApiToken.update((visible) => !visible);
  }

  sourceLabel(field: WorkspaceSettingsField, currentValue: string): SourceState {
    return this.usesCustomValue(field, currentValue) ? 'CUSTOM' : 'DEFAULT';
  }

  usesCustomValue(field: WorkspaceSettingsField, currentValue: string): boolean {
    const normalizedCurrentValue = normalizeValue(currentValue);
    if (!normalizedCurrentValue) {
      return false;
    }
    return normalizedCurrentValue !== normalizeValue(field.applicationValue);
  }

  defaultTooltip(field: WorkspaceSettingsField): string | null {
    const defaultValue = normalizeValue(field.applicationValue);
    if (!defaultValue) {
      return null;
    }
    return field.secret ? 'Default: configured' : `Default: ${defaultValue}`;
  }

  resetFieldToDefault(
    event: Event,
    control: FormControl<string>,
    field: WorkspaceSettingsField
  ): void {
    event.preventDefault();
    event.stopPropagation();
    control.setValue(field.applicationValue);
    control.markAsDirty();
  }

  private applySettings(settings: WorkspaceSettingsResponse): void {
    this.settings.set(settings);
    this.form.setValue({
      appUi: {
        title: settings.values.appUi.title.value
      },
      copilot: {
        localGithubToken: settings.values.copilot.localGithubToken.value
      },
      jira: {
        baseUrl: settings.values.jira.baseUrl.value,
        token: settings.values.jira.token.value
      },
      confluence: {
        baseUrl: settings.values.confluence.baseUrl.value,
        token: settings.values.confluence.token.value
      },
      gitLab: {
        baseUrl: settings.values.gitLab.baseUrl.value,
        group: settings.values.gitLab.group.value,
        token: settings.values.gitLab.token.value
      },
      configDriftViewerGitLab: {
        baseUrl: settings.values.configDriftViewerGitLab.baseUrl.value,
        token: settings.values.configDriftViewerGitLab.token.value
      },
      elasticsearch: {
        baseUrl: settings.values.elasticsearch.baseUrl.value,
        kibanaSpaceId: settings.values.elasticsearch.kibanaSpaceId.value,
        indexPattern: settings.values.elasticsearch.indexPattern.value,
        authorizationHeader: settings.values.elasticsearch.authorizationHeader.value
      },
      dynatrace: {
        baseUrl: settings.values.dynatrace.baseUrl.value,
        apiToken: settings.values.dynatrace.apiToken.value
      }
    });
  }

  private requestFromForm(): WorkspaceSettingsUpdateRequest {
    return {
      appUi: {
        title: this.form.controls.appUi.controls.title.value.trim()
      },
      copilot: {
        localGithubToken: this.form.controls.copilot.controls.localGithubToken.value.trim()
      },
      jira: {
        baseUrl: this.form.controls.jira.controls.baseUrl.value.trim(),
        token: this.form.controls.jira.controls.token.value.trim()
      },
      confluence: {
        baseUrl: this.form.controls.confluence.controls.baseUrl.value.trim(),
        token: this.form.controls.confluence.controls.token.value.trim()
      },
      gitLab: {
        baseUrl: this.form.controls.gitLab.controls.baseUrl.value.trim(),
        group: this.form.controls.gitLab.controls.group.value.trim(),
        token: this.form.controls.gitLab.controls.token.value.trim()
      },
      configDriftViewerGitLab: {
        baseUrl: this.form.controls.configDriftViewerGitLab.controls.baseUrl.value.trim(),
        token: this.form.controls.configDriftViewerGitLab.controls.token.value.trim()
      },
      elasticsearch: {
        baseUrl: this.form.controls.elasticsearch.controls.baseUrl.value.trim(),
        kibanaSpaceId: this.form.controls.elasticsearch.controls.kibanaSpaceId.value.trim(),
        indexPattern: this.form.controls.elasticsearch.controls.indexPattern.value.trim(),
        authorizationHeader:
          this.form.controls.elasticsearch.controls.authorizationHeader.value.trim()
      },
      dynatrace: {
        baseUrl: this.form.controls.dynatrace.controls.baseUrl.value.trim(),
        apiToken: this.form.controls.dynatrace.controls.apiToken.value.trim()
      }
    };
  }

  private fields(settings: WorkspaceSettingsResponse): WorkspaceSettingsField[] {
    return [
      settings.values.appUi.title,
      settings.values.copilot.localGithubToken,
      settings.values.jira.baseUrl,
      settings.values.jira.token,
      settings.values.confluence.baseUrl,
      settings.values.confluence.token,
      settings.values.gitLab.baseUrl,
      settings.values.gitLab.group,
      settings.values.gitLab.token,
      settings.values.configDriftViewerGitLab.baseUrl,
      settings.values.configDriftViewerGitLab.token,
      settings.values.elasticsearch.baseUrl,
      settings.values.elasticsearch.kibanaSpaceId,
      settings.values.elasticsearch.indexPattern,
      settings.values.elasticsearch.authorizationHeader,
      settings.values.dynatrace.baseUrl,
      settings.values.dynatrace.apiToken
    ];
  }
}

function toErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    const payload = error.error;
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
      const message = (payload as Record<string, unknown>)['message'];
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    if (error.status > 0) {
      return `Backend zwrócił HTTP ${error.status}.`;
    }
  }

  return fallback;
}

function normalizeValue(value: string | null | undefined): string {
  return value?.trim() ?? '';
}
