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
};

const DEFAULT_CONTEXT: RouteContext = {
  section: 'Analysis Features',
  title: 'Incident Analysis'
};
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'team-delivery-workspace.sidebar.collapsed';

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

  readonly navGroups = NAV_GROUPS;
  readonly sidebarCollapsed = signal(readSidebarCollapsedPreference());
  readonly section = computed(() => this.routeContext().section);
  readonly title = computed(() => this.routeContext().title);

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
    this.sidebarCollapsed.set(collapsed);
    writeSidebarCollapsedPreference(collapsed);
  }

  private updateRouteContext(): void {
    let route: ActivatedRoute | null = this.activatedRoute;
    let section = DEFAULT_CONTEXT.section;
    let title = DEFAULT_CONTEXT.title;

    while (route) {
      const data = route.snapshot?.data ?? {};
      const routeSection = data['section'];
      const routeTitle = data['title'];

      if (typeof routeSection === 'string' && routeSection.trim()) {
        section = routeSection;
      }
      if (typeof routeTitle === 'string' && routeTitle.trim()) {
        title = routeTitle;
      }

      route = route.firstChild;
    }

    this.routeContext.set({ section, title });
  }
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
