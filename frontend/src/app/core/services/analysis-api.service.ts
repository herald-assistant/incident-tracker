import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AnalysisJobResponse } from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AnalysisApiService {
  private readonly http = inject(HttpClient);

  startAnalysis(correlationId: string): Observable<AnalysisJobResponse> {
    return this.http.post<AnalysisJobResponse>('/analysis/jobs', { correlationId });
  }

  getAnalysis(analysisId: string): Observable<AnalysisJobResponse> {
    return this.http.get<AnalysisJobResponse>(`/analysis/jobs/${encodeURIComponent(analysisId)}`);
  }
}
