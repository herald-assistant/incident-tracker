import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  FlowExplorerChatMessageRequest,
  FlowExplorerConfig,
  FlowExplorerEndpointInventoryQuery,
  FlowExplorerEndpointInventoryResponse,
  FlowExplorerJobStartRequest,
  FlowExplorerJobStateSnapshot,
  FlowExplorerSystemOption
} from '../models/flow-explorer.models';

@Injectable({
  providedIn: 'root'
})
export class FlowExplorerApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/flow-explorer';

  getConfig(): Observable<FlowExplorerConfig> {
    return this.http.get<FlowExplorerConfig>(`${this.baseUrl}/config`);
  }

  getSystems(): Observable<FlowExplorerSystemOption[]> {
    return this.http.get<FlowExplorerSystemOption[]>(`${this.baseUrl}/systems`);
  }

  getEndpointInventory(
    systemId: string,
    query: FlowExplorerEndpointInventoryQuery = {}
  ): Observable<FlowExplorerEndpointInventoryResponse> {
    let params = new HttpParams();

    for (const [key, value] of Object.entries(query)) {
      if (typeof value === 'string' && value.trim()) {
        params = params.set(key, value.trim());
      }
    }

    return this.http.get<FlowExplorerEndpointInventoryResponse>(
      `${this.baseUrl}/systems/${encodeURIComponent(systemId)}/endpoints`,
      { params }
    );
  }

  startJob(request: FlowExplorerJobStartRequest): Observable<FlowExplorerJobStateSnapshot> {
    return this.http.post<FlowExplorerJobStateSnapshot>(`${this.baseUrl}/jobs`, request);
  }

  getJob(jobId: string): Observable<FlowExplorerJobStateSnapshot> {
    return this.http.get<FlowExplorerJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}`
    );
  }

  sendChatMessage(
    jobId: string,
    request: FlowExplorerChatMessageRequest
  ): Observable<FlowExplorerJobStateSnapshot> {
    return this.http.post<FlowExplorerJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(jobId)}/chat/messages`,
      request
    );
  }
}
