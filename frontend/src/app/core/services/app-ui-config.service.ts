import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { AppUiConfig } from '../models/ui-config.models';

export const DEFAULT_APP_TITLE = 'Team Delivery Workspace';

const FALLBACK_UI_CONFIG: AppUiConfig = {
  title: DEFAULT_APP_TITLE,
  subtitle: null,
  defaultTitle: DEFAULT_APP_TITLE
};

@Injectable({
  providedIn: 'root'
})
export class AppUiConfigService {
  private readonly http = inject(HttpClient, { optional: true });
  private loaded = false;

  readonly config = signal<AppUiConfig>(FALLBACK_UI_CONFIG);

  load(): void {
    if (this.loaded) {
      return;
    }

    this.loaded = true;
    this.fetchConfig();
  }

  reload(): void {
    this.loaded = true;
    this.fetchConfig();
  }

  private fetchConfig(): void {
    if (!this.http) {
      return;
    }

    this.http.get<AppUiConfig>('/api/ui/config').subscribe({
      next: (config) => this.config.set(normalizeUiConfig(config)),
      error: () => this.config.set(FALLBACK_UI_CONFIG)
    });
  }
}

function normalizeUiConfig(config: Partial<AppUiConfig> | null | undefined): AppUiConfig {
  const defaultTitle = textOrDefault(config?.defaultTitle, DEFAULT_APP_TITLE);
  const title = textOrDefault(config?.title, defaultTitle);
  const subtitle = textOrNull(config?.subtitle);

  return {
    title,
    subtitle: subtitle === title ? null : subtitle,
    defaultTitle
  };
}

function textOrDefault(value: string | null | undefined, fallback: string): string {
  const normalized = value?.trim();
  return normalized ? normalized : fallback;
}

function textOrNull(value: string | null | undefined): string | null {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}
