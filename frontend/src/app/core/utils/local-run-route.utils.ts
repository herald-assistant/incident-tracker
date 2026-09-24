import { ActivatedRoute, Router } from '@angular/router';

export function rememberLocalRunId(router: Router, route: ActivatedRoute, runId: string): void {
  if (!runId.trim()) return;

  try {
    void router.navigate([], {
      relativeTo: route,
      queryParams: { localRunId: runId },
      queryParamsHandling: 'merge',
      replaceUrl: true
    }).catch(() => undefined);
  } catch {
    // URL persistence must not interrupt an already started run.
  }
}
