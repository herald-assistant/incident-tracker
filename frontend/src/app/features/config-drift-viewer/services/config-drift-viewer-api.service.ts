import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ConfigDriftViewerDeepPreflight,
  ConfigDriftViewerInputOptions,
  ConfigDriftViewerJobStartRequest,
  ConfigDriftViewerJobStateSnapshot,
  ConfigDriftViewerWorkbenchAiInputResponse,
  ConfigDriftViewerWorkbenchAnonymizationPage,
  ConfigDriftViewerWorkbenchArtifactResponse,
  ConfigDriftViewerWorkbenchConfigurationDiffResponse,
  ConfigDriftViewerWorkbenchDeepResponse,
  ConfigDriftViewerWorkbenchMappingPage,
  ConfigDriftViewerWorkbenchPreviewRequest,
  ConfigDriftViewerWorkbenchPreviewResponse,
  ConfigDriftViewerWorkbenchSourceResponse
} from '../models/config-drift-viewer.models';

@Injectable({
  providedIn: 'root'
})
export class ConfigDriftViewerApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/config-drift-viewer/v1';

  getInputOptions(): Observable<ConfigDriftViewerInputOptions> {
    return this.http.get<ConfigDriftViewerInputOptions>(
      `${this.baseUrl}/input-options`
    );
  }

  getDeepPreflight(
    repositoryId: string,
    systemId: string,
    codeRef?: string
  ): Observable<ConfigDriftViewerDeepPreflight> {
    let params = new HttpParams()
      .set('repositoryId', repositoryId)
      .set('systemId', systemId);
    if (codeRef?.trim()) {
      params = params.set('codeRef', codeRef.trim());
    }
    return this.http.get<ConfigDriftViewerDeepPreflight>(
      `${this.baseUrl}/deep-preflight`,
      { params }
    );
  }

  startJob(
    request: ConfigDriftViewerJobStartRequest
  ): Observable<ConfigDriftViewerJobStateSnapshot> {
    return this.http.post<ConfigDriftViewerJobStateSnapshot>(
      `${this.baseUrl}/jobs`,
      request
    );
  }

  getJob(jobId: string): Observable<ConfigDriftViewerJobStateSnapshot> {
    return this.http.get<ConfigDriftViewerJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}`
    );
  }

  importResult(document: unknown): Observable<ConfigDriftViewerJobStateSnapshot> {
    return this.http.post<ConfigDriftViewerJobStateSnapshot>(
      `${this.baseUrl}/imports`,
      document
    );
  }

  preview(
    request: ConfigDriftViewerWorkbenchPreviewRequest
  ): Observable<ConfigDriftViewerWorkbenchPreviewResponse> {
    return this.http.post<ConfigDriftViewerWorkbenchPreviewResponse>(
      `${this.baseUrl}/workbench/preview`,
      request
    );
  }

  getWorkbenchSource(previewId: string): Observable<ConfigDriftViewerWorkbenchSourceResponse> {
    return this.http.get<ConfigDriftViewerWorkbenchSourceResponse>(
      `${this.previewUrl(previewId)}/source`
    );
  }

  getWorkbenchConfigurationDiff(
    previewId: string
  ): Observable<ConfigDriftViewerWorkbenchConfigurationDiffResponse> {
    return this.http.get<ConfigDriftViewerWorkbenchConfigurationDiffResponse>(
      `${this.previewUrl(previewId)}/configuration-diff`
    );
  }

  getWorkbenchMapping(
    previewId: string,
    offset: number,
    limit: number,
    changedOnly: boolean
  ): Observable<ConfigDriftViewerWorkbenchMappingPage> {
    const params = new HttpParams()
      .set('offset', offset)
      .set('limit', limit)
      .set('changedOnly', changedOnly);
    return this.http.get<ConfigDriftViewerWorkbenchMappingPage>(
      `${this.previewUrl(previewId)}/mapping`,
      { params }
    );
  }

  getWorkbenchAnonymization(
    previewId: string,
    offset: number,
    limit: number
  ): Observable<ConfigDriftViewerWorkbenchAnonymizationPage> {
    const params = new HttpParams().set('offset', offset).set('limit', limit);
    return this.http.get<ConfigDriftViewerWorkbenchAnonymizationPage>(
      `${this.previewUrl(previewId)}/anonymization`,
      { params }
    );
  }

  getWorkbenchDeep(previewId: string): Observable<ConfigDriftViewerWorkbenchDeepResponse> {
    return this.http.get<ConfigDriftViewerWorkbenchDeepResponse>(
      `${this.previewUrl(previewId)}/deep`
    );
  }

  getWorkbenchAiInput(
    previewId: string
  ): Observable<ConfigDriftViewerWorkbenchAiInputResponse> {
    return this.http.get<ConfigDriftViewerWorkbenchAiInputResponse>(
      `${this.previewUrl(previewId)}/ai-input`
    );
  }

  getWorkbenchArtifact(
    previewId: string,
    name: string
  ): Observable<ConfigDriftViewerWorkbenchArtifactResponse> {
    return this.http.get<ConfigDriftViewerWorkbenchArtifactResponse>(
      `${this.previewUrl(previewId)}/artifact`,
      { params: new HttpParams().set('name', name) }
    );
  }

  private previewUrl(previewId: string): string {
    return `${this.baseUrl}/workbench/preview/${encodeURIComponent(previewId)}`;
  }
}
