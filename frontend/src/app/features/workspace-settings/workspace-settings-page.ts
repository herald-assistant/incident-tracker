import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  WorkspaceSettingsField,
  WorkspaceSettingsResponse,
  WorkspaceSettingsUpdateRequest
} from '../../core/models/workspace-settings.models';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { WorkspaceSettingsApiService } from '../../core/services/workspace-settings-api.service';

@Component({
  selector: 'app-workspace-settings-page',
  imports: [ReactiveFormsModule],
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

  readonly form = new FormGroup({
    appUi: new FormGroup({
      title: new FormControl('', { nonNullable: true })
    }),
    gitLab: new FormGroup({
      baseUrl: new FormControl('', { nonNullable: true }),
      group: new FormControl('', { nonNullable: true }),
      token: new FormControl('', { nonNullable: true })
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

  resetToApplicationProperties(): void {
    const settings = this.settings();
    if (!settings) {
      return;
    }

    this.form.setValue({
      appUi: {
        title: settings.values.appUi.title.applicationValue
      },
      gitLab: {
        baseUrl: settings.values.gitLab.baseUrl.applicationValue,
        group: settings.values.gitLab.group.applicationValue,
        token: settings.values.gitLab.token.applicationValue
      }
    });
  }

  toggleTokenVisibility(): void {
    this.showToken.update((visible) => !visible);
  }

  sourceLabel(field: WorkspaceSettingsField): string {
    return field.source === 'WORKSPACE_SETTINGS' ? 'settings.json' : 'application.properties';
  }

  sourceClass(field: WorkspaceSettingsField): string {
    return field.source === 'WORKSPACE_SETTINGS'
      ? 'workspace-settings-source workspace-settings-source--workspace'
      : 'workspace-settings-source';
  }

  displayValue(value: string | null | undefined): string {
    return value && value.trim() ? value : 'not set';
  }

  secretDisplayValue(field: WorkspaceSettingsField): string {
    const value = field.applicationValue || '';
    if (!value) {
      return 'not set';
    }
    return 'configured';
  }

  private applySettings(settings: WorkspaceSettingsResponse): void {
    this.settings.set(settings);
    this.form.setValue({
      appUi: {
        title: settings.values.appUi.title.value
      },
      gitLab: {
        baseUrl: settings.values.gitLab.baseUrl.value,
        group: settings.values.gitLab.group.value,
        token: settings.values.gitLab.token.value
      }
    });
  }

  private requestFromForm(): WorkspaceSettingsUpdateRequest {
    return {
      appUi: {
        title: this.form.controls.appUi.controls.title.value.trim()
      },
      gitLab: {
        baseUrl: this.form.controls.gitLab.controls.baseUrl.value.trim(),
        group: this.form.controls.gitLab.controls.group.value.trim(),
        token: this.form.controls.gitLab.controls.token.value.trim()
      }
    };
  }

  private fields(settings: WorkspaceSettingsResponse): WorkspaceSettingsField[] {
    return [
      settings.values.appUi.title,
      settings.values.gitLab.baseUrl,
      settings.values.gitLab.group,
      settings.values.gitLab.token
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
