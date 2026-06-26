import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  LocalAnalysisRunDetailResponse,
  LocalAnalysisRunListResponse,
  RenameLocalAnalysisRunRequest
} from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AnalysisRunHistoryApiService {
  private readonly http = inject(HttpClient);

  listRuns(): Observable<LocalAnalysisRunListResponse> {
    return this.http.get<LocalAnalysisRunListResponse>('/analysis/runs');
  }

  getRun(analysisId: string): Observable<LocalAnalysisRunDetailResponse> {
    return this.http.get<LocalAnalysisRunDetailResponse>(
      `/analysis/runs/${encodeURIComponent(analysisId)}`
    );
  }

  renameRun(
    analysisId: string,
    request: RenameLocalAnalysisRunRequest
  ): Observable<LocalAnalysisRunDetailResponse> {
    return this.http.patch<LocalAnalysisRunDetailResponse>(
      `/analysis/runs/${encodeURIComponent(analysisId)}/name`,
      request
    );
  }

  deleteRun(analysisId: string): Observable<void> {
    return this.http.delete<void>(`/analysis/runs/${encodeURIComponent(analysisId)}`);
  }
}
