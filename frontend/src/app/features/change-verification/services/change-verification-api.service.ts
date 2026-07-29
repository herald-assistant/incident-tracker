import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ChangeVerificationJobStartRequest,
  ChangeVerificationJobStateSnapshot
} from '../models/change-verification.models';

@Injectable({
  providedIn: 'root'
})
export class ChangeVerificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/change-verification';

  startJob(
    request: ChangeVerificationJobStartRequest
  ): Observable<ChangeVerificationJobStateSnapshot> {
    return this.http.post<ChangeVerificationJobStateSnapshot>(`${this.baseUrl}/jobs`, request);
  }

  getJob(jobId: string): Observable<ChangeVerificationJobStateSnapshot> {
    return this.http.get<ChangeVerificationJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}`
    );
  }
}
