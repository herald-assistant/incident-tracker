import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  RuntimeConfigurationDeepPreflight,
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationVerificationJobStartRequest,
  RuntimeConfigurationVerificationJobStateSnapshot,
  RuntimeConfigurationWorkbenchPreviewRequest,
  RuntimeConfigurationWorkbenchPreviewResponse
} from '../models/runtime-configuration-verification.models';

@Injectable({
  providedIn: 'root'
})
export class RuntimeConfigurationVerificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/runtime-configuration-verification';

  getInputOptions(): Observable<RuntimeConfigurationVerificationInputOptions> {
    return this.http.get<RuntimeConfigurationVerificationInputOptions>(
      `${this.baseUrl}/input-options`
    );
  }

  getDeepPreflight(
    repositoryId: string,
    systemId: string,
    codeRef?: string
  ): Observable<RuntimeConfigurationDeepPreflight> {
    let params = new HttpParams()
      .set('repositoryId', repositoryId)
      .set('systemId', systemId);
    if (codeRef?.trim()) {
      params = params.set('codeRef', codeRef.trim());
    }
    return this.http.get<RuntimeConfigurationDeepPreflight>(
      `${this.baseUrl}/deep-preflight`,
      { params }
    );
  }

  startJob(
    request: RuntimeConfigurationVerificationJobStartRequest
  ): Observable<RuntimeConfigurationVerificationJobStateSnapshot> {
    return this.http.post<RuntimeConfigurationVerificationJobStateSnapshot>(
      `${this.baseUrl}/jobs`,
      request
    );
  }

  getJob(jobId: string): Observable<RuntimeConfigurationVerificationJobStateSnapshot> {
    return this.http.get<RuntimeConfigurationVerificationJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}`
    );
  }

  importResult(document: unknown): Observable<RuntimeConfigurationVerificationJobStateSnapshot> {
    return this.http.post<RuntimeConfigurationVerificationJobStateSnapshot>(
      `${this.baseUrl}/imports`,
      document
    );
  }

  preview(
    request: RuntimeConfigurationWorkbenchPreviewRequest
  ): Observable<RuntimeConfigurationWorkbenchPreviewResponse> {
    return this.http.post<RuntimeConfigurationWorkbenchPreviewResponse>(
      `${this.baseUrl}/workbench/preview`,
      request
    );
  }
}
