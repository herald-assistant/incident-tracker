import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterOutlet
} from '@angular/router';
import { filter } from 'rxjs';

import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { AppBrandComponent } from '../app-brand/app-brand';

type NavItem = {
  label: string;
  route?: string;
  icon: string;
  exact?: boolean;
  reloadOnActiveClick?: boolean;
  disabled?: boolean;
};

type NavGroup = {
  id: string;
  label: string;
  items: NavItem[];
};

type RouteContext = {
  section: string;
  title: string;
  breadcrumbs: RouteBreadcrumbItem[];
  capabilityInfo: RouteCapabilityInfo | null;
};

type RouteBreadcrumbItem = {
  label: string;
  route: string | null;
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
  section: 'Platform',
  title: 'Team Delivery Workspace',
  breadcrumbs: [],
  capabilityInfo: null
};
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'team-delivery-workspace.sidebar.collapsed';
const SIDEBAR_EXPAND_CONTENT_DELAY_MS = 170;

const NAV_GROUPS: NavGroup[] = [
  {
    id: 'analysis-features',
    label: 'Analysis Features',
    items: [
      {
        label: 'Incident Analysis',
        route: '/incident-analysis',
        icon: 'troubleshoot',
        reloadOnActiveClick: true
      },
      {
        label: 'Flow Explorer',
        route: '/flow-explorer',
        icon: 'account_tree',
        reloadOnActiveClick: true
      },
      {
        label: 'UI Explorer',
        route: '/ui-explorer',
        icon: 'screen_search_desktop',
        reloadOnActiveClick: true
      },
      {
        label: 'UX Inspector',
        route: '/ux-inspector',
        icon: 'ads_click',
        reloadOnActiveClick: true
      },
      {
        label: 'Change Verification',
        route: '/change-verification',
        icon: 'fact_check',
        reloadOnActiveClick: true
      },
      {
        label: 'Release summary',
        route: '/release-summary',
        icon: 'deployed_code',
        reloadOnActiveClick: true
      },
      {
        label: 'Config Drift Viewer',
        route: '/config-drift-viewer',
        icon: 'build_circle',
        reloadOnActiveClick: true
      },
      {
        label: 'Behavior Change Assessment',
        route: '/delivery-complexity-assessment',
        icon: 'domain_add',
        reloadOnActiveClick: true
      },
      {
        label: 'Technical Change Assessment',
        route: '/delivery-scope-complexity',
        icon: 'engineering',
        reloadOnActiveClick: true
      },
      {
        label: 'Delivery Trends',
        route: '/delivery-complexity-trends',
        icon: 'finance',
        reloadOnActiveClick: true
      },
      { label: 'Data Diagnostics', icon: 'database_search', disabled: true }
    ]
  },
  {
    id: 'platform',
    label: 'Platform',
    items: [
      { label: 'Workspace Overview', route: '/', icon: 'space_dashboard', exact: true },
      { label: 'Analysis History', route: '/analysis-history', icon: 'history' },
      { label: 'Workspace Settings', route: '/workspace-settings', icon: 'tune' },
      { label: 'AI Skills', route: '/ai-skills', icon: 'auto_stories' },
      { label: 'AI Models', route: '/ai-models', icon: 'model_training' },
      { label: 'Personalization', icon: 'palette', disabled: true },
      { label: 'Authentication', icon: 'admin_panel_settings', disabled: true }
    ]
  },
  {
    id: 'tool-workbench',
    label: 'Tool Workbench',
    items: [
      { label: 'Elastic Logs', route: '/elastic', icon: 'manage_search' },
      { label: 'GitLab Source', route: '/gitlab', icon: 'source' },
      { label: 'Jira Source', route: '/jira', icon: 'assignment' },
      { label: 'Confluence Source', route: '/confluence', icon: 'article' },
      {
        label: 'Config Drift Viewer',
        route: '/config-drift-viewer-tools',
        icon: 'security'
      },
      { label: 'Database Tools', route: '/database', icon: 'database' },
      { label: 'Operational Context', route: '/operational-context', icon: 'hub' }
    ]
  }
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, AppBrandComponent, MatTooltipModule],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss'
})
export class AppShellComponent {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly uiConfig = inject(AppUiConfigService);
  private readonly routeContext = signal<RouteContext>(DEFAULT_CONTEXT);
  private readonly currentUrl = signal('');
  private sidebarExpandTimer: ReturnType<typeof setTimeout> | null = null;

  get navGroups(): NavGroup[] {
    return NAV_GROUPS.map(group => ({...group, items: group.items.filter(item => item.route !== '/release-summary' || this.uiConfig.config().releaseSummaryEnabled)}));
  }
  protected readonly collapsedNavGroups = signal(
    Object.fromEntries(
      NAV_GROUPS.map(({ id }) => [id, readCollapsedPreference(navGroupStorageKey(id))])
    )
  );
  readonly sidebarCollapsed = signal(readCollapsedPreference(SIDEBAR_COLLAPSED_STORAGE_KEY));
  readonly sidebarExpanding = signal(false);
  readonly sidebarRailMode = computed(() => this.sidebarCollapsed() || this.sidebarExpanding());
  readonly section = computed(() => this.routeContext().section);
  readonly title = computed(() => this.routeContext().title);
  readonly breadcrumbs = computed((): RouteBreadcrumbItem[] => {
    const workspaceTitle = this.uiConfig.config().title;
    const routeBreadcrumbs = this.routeContext().breadcrumbs;

    if (routeBreadcrumbs.length === 0) {
      return [];
    }

    if (routeBreadcrumbs.length === 1 && routeBreadcrumbs[0].label === workspaceTitle) {
      return [];
    }

    return [{ label: workspaceTitle, route: '/' }, ...routeBreadcrumbs];
  });
  readonly capabilityInfo = computed(() => this.routeContext().capabilityInfo);

  constructor() {
    this.uiConfig.load();
    this.currentUrl.set(this.router.url);
    this.updateRouteContext();
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe((event) => {
        this.currentUrl.set(event.urlAfterRedirects);
        this.updateRouteContext();
      });
  }

  protected toggleSidebar(): void {
    const collapsed = !this.sidebarCollapsed();
    this.clearSidebarExpandTimer();

    if (collapsed) {
      this.sidebarExpanding.set(false);
      this.sidebarCollapsed.set(true);
      writeCollapsedPreference(SIDEBAR_COLLAPSED_STORAGE_KEY, true);
      return;
    }

    this.sidebarExpanding.set(true);
    this.sidebarCollapsed.set(false);
    writeCollapsedPreference(SIDEBAR_COLLAPSED_STORAGE_KEY, false);
    this.sidebarExpandTimer = setTimeout(() => {
      this.sidebarExpanding.set(false);
      this.sidebarExpandTimer = null;
    }, SIDEBAR_EXPAND_CONTENT_DELAY_MS);
  }

  protected toggleNavGroup(id: string): void {
    const collapsed = !this.collapsedNavGroups()[id];
    this.collapsedNavGroups.update(groups => ({ ...groups, [id]: collapsed }));
    writeCollapsedPreference(navGroupStorageKey(id), collapsed);
  }

  protected navigateFromSidebar(event: MouseEvent, item: NavItem): void {
    if (!item.route || shouldUseNativeLinkNavigation(event)) {
      return;
    }

    event.preventDefault();

    if (item.reloadOnActiveClick && this.isRouteActive(item.route, true)) {
      this.reloadActiveRoute(item.route);
      return;
    }

    void this.router.navigateByUrl(item.route);
  }

  protected isNavItemActive(item: NavItem): boolean {
    if (!item.route) {
      return false;
    }

    return this.isRouteActive(item.route, Boolean(item.exact));
  }

  private updateRouteContext(): void {
    let route: ActivatedRoute | null = this.activatedRoute;
    let section = DEFAULT_CONTEXT.section;
    let title = DEFAULT_CONTEXT.title;
    let capabilityInfo: RouteCapabilityInfo | null = DEFAULT_CONTEXT.capabilityInfo;
    const breadcrumbs: RouteBreadcrumbItem[] = [];
    const pathSegments: string[] = [];

    while (route) {
      const snapshot = route.snapshot;
      const data = route.snapshot?.data ?? {};
      const routeSection = data['section'];
      const routeTitle = data['title'];
      const routeCapabilityInfo = normalizeCapabilityInfo(data['capabilityInfo']);
      const currentSegments = snapshot?.url?.map((segment) => segment.path).filter(Boolean) ?? [];

      pathSegments.push(...currentSegments);

      if (typeof routeSection === 'string' && routeSection.trim()) {
        section = routeSection;
      }
      if (typeof routeTitle === 'string' && routeTitle.trim()) {
        title = routeTitle.trim();
        breadcrumbs.push({
          label: title,
          route: pathSegments.length > 0 ? `/${pathSegments.join('/')}` : '/'
        });
      }
      if (routeCapabilityInfo) {
        capabilityInfo = routeCapabilityInfo;
      }

      route = route.firstChild;
    }

    const currentBreadcrumb = breadcrumbs.at(-1);
    if (currentBreadcrumb) {
      currentBreadcrumb.route = null;
    }

    this.routeContext.set({ section, title, breadcrumbs, capabilityInfo });
  }

  private clearSidebarExpandTimer(): void {
    if (this.sidebarExpandTimer === null) {
      return;
    }

    clearTimeout(this.sidebarExpandTimer);
    this.sidebarExpandTimer = null;
  }

  private isRouteActive(route: string, exact: boolean): boolean {
    this.currentUrl();
    return this.router.isActive(route, {
      paths: exact ? 'exact' : 'subset',
      queryParams: 'ignored',
      fragment: 'ignored',
      matrixParams: 'ignored'
    });
  }

  private reloadActiveRoute(route: string): void {
    void this.router
      .navigateByUrl('/', { skipLocationChange: true })
      .then(() => this.router.navigateByUrl(route))
      .catch(() => undefined);
  }
}

function shouldUseNativeLinkNavigation(event: MouseEvent): boolean {
  return event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey;
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

function navGroupStorageKey(id: string): string {
  return `team-delivery-workspace.sidebar.group.${id}.collapsed`;
}

function readCollapsedPreference(key: string): boolean {
  try {
    return window.localStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
}

function writeCollapsedPreference(key: string, collapsed: boolean): void {
  try {
    window.localStorage.setItem(key, String(collapsed));
  } catch {
    // Layout preference persistence is optional.
  }
}
