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
  private readonly baseUrl = '/api/delivery-effectiveness-assessment/jobs';

  startJob(
    request: DeliveryEffectivenessAssessmentJobStartRequest
  ): Observable<DeliveryEffectivenessAssessmentJobStateSnapshot> {
    return this.http.post<DeliveryEffectivenessAssessmentJobStateSnapshot>(this.baseUrl, request);
  }

  getJob(jobId: string): Observable<DeliveryEffectivenessAssessmentJobStateSnapshot> {
    return this.http.get<DeliveryEffectivenessAssessmentJobStateSnapshot>(
      `${this.baseUrl}/${encodeURIComponent(jobId)}`
    );
  }
}
