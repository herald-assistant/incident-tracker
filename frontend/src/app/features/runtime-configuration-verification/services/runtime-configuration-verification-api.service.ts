import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  RuntimeConfigurationDeepPreflight,
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationVerificationJobStartRequest,
  RuntimeConfigurationVerificationJobStateSnapshot,
  RuntimeConfigurationWorkbenchAiInputResponse,
  RuntimeConfigurationWorkbenchAnonymizationPage,
  RuntimeConfigurationWorkbenchArtifactResponse,
  RuntimeConfigurationWorkbenchConfigurationDiffResponse,
  RuntimeConfigurationWorkbenchDeepResponse,
  RuntimeConfigurationWorkbenchMappingPage,
  RuntimeConfigurationWorkbenchPreviewRequest,
  RuntimeConfigurationWorkbenchPreviewResponse,
  RuntimeConfigurationWorkbenchSourceResponse
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

  getWorkbenchSource(previewId: string): Observable<RuntimeConfigurationWorkbenchSourceResponse> {
    return this.http.get<RuntimeConfigurationWorkbenchSourceResponse>(
      `${this.previewUrl(previewId)}/source`
    );
  }

  getWorkbenchConfigurationDiff(
    previewId: string
  ): Observable<RuntimeConfigurationWorkbenchConfigurationDiffResponse> {
    return this.http.get<RuntimeConfigurationWorkbenchConfigurationDiffResponse>(
      `${this.previewUrl(previewId)}/configuration-diff`
    );
  }

  getWorkbenchMapping(
    previewId: string,
    offset: number,
    limit: number,
    changedOnly: boolean
  ): Observable<RuntimeConfigurationWorkbenchMappingPage> {
    const params = new HttpParams()
      .set('offset', offset)
      .set('limit', limit)
      .set('changedOnly', changedOnly);
    return this.http.get<RuntimeConfigurationWorkbenchMappingPage>(
      `${this.previewUrl(previewId)}/mapping`,
      { params }
    );
  }

  getWorkbenchAnonymization(
    previewId: string,
    offset: number,
    limit: number
  ): Observable<RuntimeConfigurationWorkbenchAnonymizationPage> {
    const params = new HttpParams().set('offset', offset).set('limit', limit);
    return this.http.get<RuntimeConfigurationWorkbenchAnonymizationPage>(
      `${this.previewUrl(previewId)}/anonymization`,
      { params }
    );
  }

  getWorkbenchDeep(previewId: string): Observable<RuntimeConfigurationWorkbenchDeepResponse> {
    return this.http.get<RuntimeConfigurationWorkbenchDeepResponse>(
      `${this.previewUrl(previewId)}/deep`
    );
  }

  getWorkbenchAiInput(
    previewId: string
  ): Observable<RuntimeConfigurationWorkbenchAiInputResponse> {
    return this.http.get<RuntimeConfigurationWorkbenchAiInputResponse>(
      `${this.previewUrl(previewId)}/ai-input`
    );
  }

  getWorkbenchArtifact(
    previewId: string,
    name: string
  ): Observable<RuntimeConfigurationWorkbenchArtifactResponse> {
    return this.http.get<RuntimeConfigurationWorkbenchArtifactResponse>(
      `${this.previewUrl(previewId)}/artifact`,
      { params: new HttpParams().set('name', name) }
    );
  }

  private previewUrl(previewId: string): string {
    return `${this.baseUrl}/workbench/preview/${encodeURIComponent(previewId)}`;
  }
}
