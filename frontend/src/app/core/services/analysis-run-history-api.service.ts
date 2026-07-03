import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AnalysisChatMessageRequest,
  LocalAnalysisRunDetailResponse,
  LocalAnalysisRunListResponse,
  RenameLocalAnalysisRunRequest
} from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AnalysisRunHistoryApiService {
  private readonly baseUrl = '/api/analysis/runs';
  private readonly http = inject(HttpClient);

  listRuns(): Observable<LocalAnalysisRunListResponse> {
    return this.http.get<LocalAnalysisRunListResponse>(this.baseUrl);
  }

  getRun(analysisId: string): Observable<LocalAnalysisRunDetailResponse> {
    return this.http.get<LocalAnalysisRunDetailResponse>(
      `${this.baseUrl}/${encodeURIComponent(analysisId)}`
    );
  }

  exportRun(analysisId: string): Observable<unknown> {
    return this.http.get<unknown>(`${this.baseUrl}/${encodeURIComponent(analysisId)}/export`);
  }

  renameRun(
    analysisId: string,
    request: RenameLocalAnalysisRunRequest
  ): Observable<LocalAnalysisRunDetailResponse> {
    return this.http.patch<LocalAnalysisRunDetailResponse>(
      `${this.baseUrl}/${encodeURIComponent(analysisId)}/name`,
      request
    );
  }

  sendChatMessage(
    analysisId: string,
    request: AnalysisChatMessageRequest
  ): Observable<LocalAnalysisRunDetailResponse> {
    return this.http.post<LocalAnalysisRunDetailResponse>(
      `${this.baseUrl}/${encodeURIComponent(analysisId)}/chat/messages`,
      request
    );
  }

  deleteRun(analysisId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(analysisId)}`);
  }
}
