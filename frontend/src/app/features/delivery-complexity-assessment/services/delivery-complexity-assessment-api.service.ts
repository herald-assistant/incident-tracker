import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DeliveryComplexityAssessmentJobStartRequest,
  DeliveryComplexityAssessmentJobStateSnapshot
} from '../models/delivery-complexity-assessment.models';

@Injectable({ providedIn: 'root' })
export class DeliveryComplexityAssessmentApiService {
  private readonly http = inject(HttpClient);
  private readonly featureUrl = '/api/delivery-complexity-assessment';
  private readonly jobsUrl = `${this.featureUrl}/jobs`;

  startJob(
    request: DeliveryComplexityAssessmentJobStartRequest
  ): Observable<DeliveryComplexityAssessmentJobStateSnapshot> {
    return this.http.post<DeliveryComplexityAssessmentJobStateSnapshot>(this.jobsUrl, request);
  }

  getJob(jobId: string): Observable<DeliveryComplexityAssessmentJobStateSnapshot> {
    return this.http.get<DeliveryComplexityAssessmentJobStateSnapshot>(
      `${this.jobsUrl}/${encodeURIComponent(jobId)}`
    );
  }

  importRun(document: unknown): Observable<DeliveryComplexityAssessmentJobStateSnapshot> {
    return this.http.post<DeliveryComplexityAssessmentJobStateSnapshot>(
      `${this.featureUrl}/imports`,
      document
    );
  }
}
