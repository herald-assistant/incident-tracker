import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from '@angular/router';
import { filter } from 'rxjs';

import { AppBrandComponent } from '../app-brand/app-brand';

type NavItem = {
  label: string;
  route?: string;
  icon: string;
  exact?: boolean;
  disabled?: boolean;
};

type NavGroup = {
  label: string;
  items: NavItem[];
};

type RouteContext = {
  section: string;
  title: string;
  capabilityInfo: RouteCapabilityInfo | null;
};

type RouteCapabilityInfo = {
  description: string;
  badges: string[];
  meta: RouteCapabilityMetaItem[];
};

type RouteCapabilityMetaItem = {
  label: string;
  value: string;
};

const DEFAULT_CONTEXT: RouteContext = {
  section: 'Analysis Features',
  title: 'Incident Analysis',
  capabilityInfo: null
};
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'team-delivery-workspace.sidebar.collapsed';
const SIDEBAR_EXPAND_CONTENT_DELAY_MS = 170;

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Analysis Features',
    items: [
      { label: 'Incident Analysis', route: '/', icon: 'troubleshoot', exact: true },
      { label: 'Flow Explorer', icon: 'account_tree', disabled: true },
      { label: 'Functional Logic', icon: 'schema', disabled: true },
      { label: 'Data Diagnostics', icon: 'database_search', disabled: true }
    ]
  },
  {
    label: 'Tool Workbench',
    items: [
      { label: 'Elastic Logs', route: '/elastic', icon: 'manage_search' },
      { label: 'GitLab Source', route: '/gitlab', icon: 'source' },
      { label: 'Database Tools', route: '/database', icon: 'database' },
      { label: 'Operational Context', route: '/operational-context', icon: 'hub' }
    ]
  },
  {
    label: 'Platform',
    items: [
      { label: 'AI Models', icon: 'model_training', disabled: true },
      { label: 'GitHub Auth', icon: 'account_circle', disabled: true },
      { label: 'Settings', icon: 'settings', disabled: true }
    ]
  }
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppBrandComponent],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss'
})
export class AppShellComponent {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly routeContext = signal<RouteContext>(DEFAULT_CONTEXT);
  private sidebarExpandTimer: ReturnType<typeof setTimeout> | null = null;

  readonly navGroups = NAV_GROUPS;
  readonly sidebarCollapsed = signal(readSidebarCollapsedPreference());
  readonly sidebarExpanding = signal(false);
  readonly sidebarRailMode = computed(() => this.sidebarCollapsed() || this.sidebarExpanding());
  readonly section = computed(() => this.routeContext().section);
  readonly title = computed(() => this.routeContext().title);
  readonly capabilityInfo = computed(() => this.routeContext().capabilityInfo);

  constructor() {
    this.updateRouteContext();
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => this.updateRouteContext());
  }

  protected toggleSidebar(): void {
    const collapsed = !this.sidebarCollapsed();
    this.clearSidebarExpandTimer();

    if (collapsed) {
      this.sidebarExpanding.set(false);
      this.sidebarCollapsed.set(true);
      writeSidebarCollapsedPreference(true);
      return;
    }

    this.sidebarExpanding.set(true);
    this.sidebarCollapsed.set(false);
    writeSidebarCollapsedPreference(false);
    this.sidebarExpandTimer = setTimeout(() => {
      this.sidebarExpanding.set(false);
      this.sidebarExpandTimer = null;
    }, SIDEBAR_EXPAND_CONTENT_DELAY_MS);
  }

  private updateRouteContext(): void {
    let route: ActivatedRoute | null = this.activatedRoute;
    let section = DEFAULT_CONTEXT.section;
    let title = DEFAULT_CONTEXT.title;
    let capabilityInfo: RouteCapabilityInfo | null = DEFAULT_CONTEXT.capabilityInfo;

    while (route) {
      const data = route.snapshot?.data ?? {};
      const routeSection = data['section'];
      const routeTitle = data['title'];
      const routeCapabilityInfo = normalizeCapabilityInfo(data['capabilityInfo']);

      if (typeof routeSection === 'string' && routeSection.trim()) {
        section = routeSection;
      }
      if (typeof routeTitle === 'string' && routeTitle.trim()) {
        title = routeTitle;
      }
      if (routeCapabilityInfo) {
        capabilityInfo = routeCapabilityInfo;
      }

      route = route.firstChild;
    }

    this.routeContext.set({ section, title, capabilityInfo });
  }

  private clearSidebarExpandTimer(): void {
    if (this.sidebarExpandTimer === null) {
      return;
    }

    clearTimeout(this.sidebarExpandTimer);
    this.sidebarExpandTimer = null;
  }
}

function normalizeCapabilityInfo(value: unknown): RouteCapabilityInfo | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const candidate = value as {
    description?: unknown;
    badges?: unknown;
    meta?: unknown;
  };
  const description = typeof candidate.description === 'string' ? candidate.description.trim() : '';
  const badges = Array.isArray(candidate.badges)
    ? candidate.badges.filter(
        (badge): badge is string => typeof badge === 'string' && badge.trim().length > 0
      )
    : [];
  const meta = Array.isArray(candidate.meta)
    ? candidate.meta
        .map((item): RouteCapabilityMetaItem | null => {
          if (!item || typeof item !== 'object') {
            return null;
          }

          const metaCandidate = item as { label?: unknown; value?: unknown };
          const label = typeof metaCandidate.label === 'string' ? metaCandidate.label.trim() : '';
          const metaValue = typeof metaCandidate.value === 'string' ? metaCandidate.value.trim() : '';

          return label && metaValue ? { label, value: metaValue } : null;
        })
        .filter((item): item is RouteCapabilityMetaItem => item !== null)
    : [];

  if (!description && badges.length === 0 && meta.length === 0) {
    return null;
  }

  return { description, badges, meta };
}

function readSidebarCollapsedPreference(): boolean {
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

function writeSidebarCollapsedPreference(collapsed: boolean): void {
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(collapsed));
  } catch {
    // Layout preference persistence is optional.
  }
}
