import { Injectable } from '@angular/core';
import { Observable, concat, defer, exhaustMap, takeWhile, timer } from 'rxjs';

export interface AnalysisJobPollingOptions<T> {
  load: () => Observable<T>;
  isTerminal: (snapshot: T) => boolean;
  initialDelayMs?: number;
  intervalMs?: number;
}

@Injectable({
  providedIn: 'root'
})
export class AnalysisJobPollingService {
  poll<T>(options: AnalysisJobPollingOptions<T>): Observable<T> {
    const initialDelayMs = options.initialDelayMs ?? 0;
    const intervalMs = options.intervalMs ?? 1500;
    const initialLoad = initialDelayMs > 0
      ? timer(initialDelayMs).pipe(exhaustMap(() => options.load()))
      : defer(options.load);
    const subsequentLoads = timer(intervalMs, intervalMs).pipe(
      exhaustMap(() => options.load())
    );

    return concat(initialLoad, subsequentLoads).pipe(
      takeWhile((snapshot) => !options.isTerminal(snapshot), true)
    );
  }
}
