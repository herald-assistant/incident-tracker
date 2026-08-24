import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DeliveryScopeComplexityJobStartRequest,
  DeliveryScopeComplexityJobStateSnapshot
} from '../models/delivery-scope-complexity.models';

@Injectable({ providedIn: 'root' })
export class DeliveryScopeComplexityApiService {
  private readonly http = inject(HttpClient);
  private readonly featureUrl = '/api/delivery-scope-complexity';
  private readonly jobsUrl = `${this.featureUrl}/jobs`;

  startJob(
    request: DeliveryScopeComplexityJobStartRequest
  ): Observable<DeliveryScopeComplexityJobStateSnapshot> {
    return this.http.post<DeliveryScopeComplexityJobStateSnapshot>(this.jobsUrl, request);
  }

  getJob(jobId: string): Observable<DeliveryScopeComplexityJobStateSnapshot> {
    return this.http.get<DeliveryScopeComplexityJobStateSnapshot>(
      `${this.jobsUrl}/${encodeURIComponent(jobId)}`
    );
  }

  importRun(document: unknown): Observable<DeliveryScopeComplexityJobStateSnapshot> {
    return this.http.post<DeliveryScopeComplexityJobStateSnapshot>(
      `${this.featureUrl}/imports`,
      document
    );
  }
}
