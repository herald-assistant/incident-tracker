import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  UiExplorerInputOptionsResponse,
  UiExplorerExportEnvelope,
  UiExplorerJobStartRequest,
  UiExplorerJobStateSnapshot,
  UiExplorerScreenCatalogResponse
} from '../models/ui-explorer.models';

@Injectable({
  providedIn: 'root'
})
export class UiExplorerApiService {
  private readonly http = inject(HttpClient);

  getInputOptions(): Observable<UiExplorerInputOptionsResponse> {
    return this.http.get<UiExplorerInputOptionsResponse>('/api/ui-explorer/input-options');
  }

  getScreens(systemId: string, branch: string): Observable<UiExplorerScreenCatalogResponse> {
    const params = new HttpParams().set('systemId', systemId).set('branch', branch);
    return this.http.get<UiExplorerScreenCatalogResponse>('/api/ui-explorer/screens', { params });
  }

  startJob(request: UiExplorerJobStartRequest): Observable<UiExplorerJobStateSnapshot> {
    return this.http.post<UiExplorerJobStateSnapshot>('/api/ui-explorer/jobs', request);
  }

  getJob(jobId: string): Observable<UiExplorerJobStateSnapshot> {
    return this.http.get<UiExplorerJobStateSnapshot>(
      `/api/ui-explorer/jobs/${encodeURIComponent(jobId)}`
    );
  }

  exportJob(jobId: string): Observable<UiExplorerExportEnvelope> {
    return this.http.get<UiExplorerExportEnvelope>(
      `/api/ui-explorer/jobs/${encodeURIComponent(jobId)}/export`
    );
  }

  importAnalysis(document: unknown): Observable<UiExplorerJobStateSnapshot> {
    return this.http.post<UiExplorerJobStateSnapshot>('/api/ui-explorer/imports', document);
  }
}
