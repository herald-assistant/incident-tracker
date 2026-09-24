import { ActivatedRoute, Router } from '@angular/router';
import { vi } from 'vitest';

import { rememberLocalRunId } from './local-run-route.utils';

describe('rememberLocalRunId', () => {
  it('replaces the current URL while preserving unrelated query parameters', () => {
    const route = {} as ActivatedRoute;
    const navigate = vi.fn(() => Promise.resolve(true));

    rememberLocalRunId({ navigate } as unknown as Router, route, 'crm-run-1');

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: route,
      queryParams: { localRunId: 'crm-run-1' },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  });
});
