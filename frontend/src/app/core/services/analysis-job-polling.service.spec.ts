import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { AnalysisJobPollingService } from './analysis-job-polling.service';

describe('AnalysisJobPollingService', () => {
  let service: AnalysisJobPollingService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(AnalysisJobPollingService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should avoid overlapping requests and stop after the terminal snapshot', () => {
    const requests: Subject<{ status: string }>[] = [];
    const snapshots: string[] = [];
    let completed = false;

    service
      .poll({
        load: () => {
          const request = new Subject<{ status: string }>();
          requests.push(request);
          return request;
        },
        isTerminal: (snapshot) => snapshot.status === 'COMPLETED',
        intervalMs: 100
      })
      .subscribe({
        next: (snapshot) => snapshots.push(snapshot.status),
        complete: () => (completed = true)
      });

    vi.advanceTimersByTime(250);
    expect(requests.length).toBe(1);

    requests[0].next({ status: 'RUNNING' });
    requests[0].complete();
    vi.advanceTimersByTime(100);
    expect(requests.length).toBe(2);

    requests[1].next({ status: 'COMPLETED' });
    requests[1].complete();
    expect(snapshots).toEqual(['RUNNING', 'COMPLETED']);
    expect(completed).toBe(true);

    vi.advanceTimersByTime(500);
    expect(requests.length).toBe(2);
  });
});
