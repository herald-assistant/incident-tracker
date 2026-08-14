import { CanDeactivateFn } from '@angular/router';

export interface PendingAiSkillChangesAware {
  canDeactivate(): boolean;
}

export const pendingAiSkillChangesGuard: CanDeactivateFn<PendingAiSkillChangesAware> = (
  component
) => component.canDeactivate();
