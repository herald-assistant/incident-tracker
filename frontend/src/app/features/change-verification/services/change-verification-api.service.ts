import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ChangeVerificationJobStartRequest,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationExecution,
  ChangeVerificationSmokeExecutionRequest,
  ChangeVerificationSmokePack
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

  updateSmokePack(
    jobId: string,
    smokePack: ChangeVerificationSmokePack
  ): Observable<ChangeVerificationSmokePack> {
    return this.http.put<ChangeVerificationSmokePack>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}/smoke-pack`,
      smokePack
    );
  }

  getPostmanCollection(jobId: string): Observable<unknown> {
    return this.http.get<unknown>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}/postman/collection`
    );
  }

  executeSmokePack(
    jobId: string,
    request: ChangeVerificationSmokeExecutionRequest
  ): Observable<ChangeVerificationExecution> {
    return this.http.post<ChangeVerificationExecution>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}/smoke-executions`,
      request
    );
  }
}
