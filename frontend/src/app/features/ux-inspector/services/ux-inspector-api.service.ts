import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  UxInspectorExportEnvelope,
  UxInspectorInputOptionsResponse,
  UxInspectorJobStartRequest,
  UxInspectorJobStateSnapshot,
  UxInspectorViewCatalogResponse
} from '../models/ux-inspector.models';

@Injectable({ providedIn: 'root' })
export class UxInspectorApiService {
  private readonly http = inject(HttpClient);

  getInputOptions(): Observable<UxInspectorInputOptionsResponse> {
    return this.http.get<UxInspectorInputOptionsResponse>('/api/ux-inspector/input-options');
  }

  getViews(
    systemId: string,
    branch: string,
    refreshCache = false
  ): Observable<UxInspectorViewCatalogResponse> {
    let params = new HttpParams().set('systemId', systemId).set('branch', branch);
    if (refreshCache) {
      params = params.set('refresh', 'true');
    }
    return this.http.get<UxInspectorViewCatalogResponse>('/api/ux-inspector/views', { params });
  }

  startJob(request: UxInspectorJobStartRequest): Observable<UxInspectorJobStateSnapshot> {
    return this.http.post<UxInspectorJobStateSnapshot>('/api/ux-inspector/jobs', request);
  }

  getJob(jobId: string): Observable<UxInspectorJobStateSnapshot> {
    return this.http.get<UxInspectorJobStateSnapshot>(
      `/api/ux-inspector/jobs/${encodeURIComponent(jobId)}`
    );
  }

  sendChatMessage(jobId: string, message: string): Observable<UxInspectorJobStateSnapshot> {
    return this.http.post<UxInspectorJobStateSnapshot>(
      `/api/ux-inspector/jobs/${encodeURIComponent(jobId)}/chat/messages`,
      { message }
    );
  }

  exportJob(jobId: string): Observable<UxInspectorExportEnvelope> {
    return this.http.get<UxInspectorExportEnvelope>(
      `/api/ux-inspector/jobs/${encodeURIComponent(jobId)}/export`
    );
  }

  importAnalysis(document: unknown): Observable<UxInspectorJobStateSnapshot> {
    return this.http.post<UxInspectorJobStateSnapshot>('/api/ux-inspector/imports', document);
  }
}
