import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  AnalysisJobResponse,
  AnalysisStartRequest
} from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AnalysisApiService {
  private readonly http = inject(HttpClient);

  startAnalysis(request: AnalysisStartRequest): Observable<AnalysisJobResponse> {
    return this.http.post<AnalysisJobResponse>('/analysis/jobs', request);
  }

  getAiModelOptions(): Observable<AnalysisAiModelOptionsResponse> {
    return this.http.get<AnalysisAiModelOptionsResponse>('/analysis/ai/options');
  }

  getAnalysis(analysisId: string): Observable<AnalysisJobResponse> {
    return this.http.get<AnalysisJobResponse>(`/analysis/jobs/${encodeURIComponent(analysisId)}`);
  }
}
