import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DeliveryEffectivenessAssessmentJobStartRequest,
  DeliveryEffectivenessAssessmentJobStateSnapshot
} from '../models/delivery-effectiveness-assessment.models';

@Injectable({ providedIn: 'root' })
export class DeliveryEffectivenessAssessmentApiService {
  private readonly http = inject(HttpClient);
  private readonly featureUrl = '/api/delivery-effectiveness-assessment';
  private readonly jobsUrl = `${this.featureUrl}/jobs`;

  startJob(
    request: DeliveryEffectivenessAssessmentJobStartRequest
  ): Observable<DeliveryEffectivenessAssessmentJobStateSnapshot> {
    return this.http.post<DeliveryEffectivenessAssessmentJobStateSnapshot>(this.jobsUrl, request);
  }

  getJob(jobId: string): Observable<DeliveryEffectivenessAssessmentJobStateSnapshot> {
    return this.http.get<DeliveryEffectivenessAssessmentJobStateSnapshot>(
      `${this.jobsUrl}/${encodeURIComponent(jobId)}`
    );
  }

  importRun(document: unknown): Observable<DeliveryEffectivenessAssessmentJobStateSnapshot> {
    return this.http.post<DeliveryEffectivenessAssessmentJobStateSnapshot>(
      `${this.featureUrl}/imports`,
      document
    );
  }
}
