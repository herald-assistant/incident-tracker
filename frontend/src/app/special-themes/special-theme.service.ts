import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';

export interface SpecialThemeOverviewBanner {
  readonly heading: string;
  readonly message: string;
  readonly image: string;
}

interface SpecialTheme {
  readonly id: string;
  readonly from: string;
  readonly through: string;
  readonly overviewBanner?: SpecialThemeOverviewBanner;
}

const THEMES: readonly SpecialTheme[] = [
  {
    id: 'christmas',
    from: '2026-12-15',
    through: '2027-01-06',
    overviewBanner: {
      heading: 'Wesołych Świąt!',
      message: 'Całemu zespołowi życzymy spokojnych Świąt, dobrego odpoczynku i pomyślności w nowym roku! '
        + 'Dziękujemy za współpracę i za to, co udało nam się razem osiągnąć w tym roku.',
      image: 'assets/themes/christmas/workspace-overview-banner.jpg'
    }
  }
];

const DISABLED_KEY = 'team-delivery-workspace.special-themes.disabled';

@Injectable({ providedIn: 'root' })
export class SpecialThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly browser = this.document.defaultView;
  private readonly disabled = signal(this.readDisabled());
  private readonly scheduledTheme = signal<SpecialTheme | undefined>(undefined);

  readonly enabled = computed(() => !this.disabled());
  readonly activeTheme = computed(() => this.enabled() ? this.scheduledTheme() : undefined);

  constructor() {
    const browser = this.browser;
    if (!browser) {
      return;
    }

    let midnightTimer: number | undefined;
    const refreshSchedule = () => {
      browser.clearTimeout(midnightTimer);
      const now = new Date();
      const day = [
        now.getFullYear(),
        String(now.getMonth() + 1).padStart(2, '0'),
        String(now.getDate()).padStart(2, '0')
      ].join('-');
      this.scheduledTheme.set(THEMES.find(theme => theme.from <= day && day <= theme.through));

      const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
      midnightTimer = browser.setTimeout(refreshSchedule, midnight.getTime() - now.getTime());
    };

    const syncPreference = (event: StorageEvent) => {
      if (event.key === DISABLED_KEY || event.key === null) {
        this.disabled.set(this.readDisabled());
      }
    };

    refreshSchedule();
    browser.addEventListener('focus', refreshSchedule);
    browser.addEventListener('storage', syncPreference);
    const root = this.document.documentElement;

    effect(() => {
      const theme = this.activeTheme();
      if (theme) {
        root.setAttribute('data-special-theme', theme.id);
      } else {
        root.removeAttribute('data-special-theme');
      }
    });

    inject(DestroyRef).onDestroy(() => {
      browser.clearTimeout(midnightTimer);
      browser.removeEventListener('focus', refreshSchedule);
      browser.removeEventListener('storage', syncPreference);
      root.removeAttribute('data-special-theme');
    });
  }

  setEnabled(enabled: boolean): void {
    this.disabled.set(!enabled);
    try {
      const storage = this.browser?.localStorage;
      if (enabled) {
        storage?.removeItem(DISABLED_KEY);
      } else {
        storage?.setItem(DISABLED_KEY, 'true');
      }
    } catch {
      // The preference still applies in this tab when browser storage is unavailable.
    }
  }

  private readDisabled(): boolean {
    try {
      return this.browser?.localStorage.getItem(DISABLED_KEY) === 'true';
    } catch {
      return false;
    }
  }
}
