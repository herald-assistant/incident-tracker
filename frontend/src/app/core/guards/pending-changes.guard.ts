import { CanDeactivateFn } from '@angular/router';

interface PendingChangesAware {
  canDeactivate(): boolean;
}

export const pendingChangesGuard: CanDeactivateFn<PendingChangesAware> = component => component.canDeactivate();
