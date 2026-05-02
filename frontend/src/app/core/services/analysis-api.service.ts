import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  AnalysisChatMessageRequest,
  AnalysisAiModelOptionsResponse,
  AnalysisJobStateSnapshot,
  AnalysisStartRequest
} from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AnalysisApiService {
  private readonly http = inject(HttpClient);

  startAnalysis(request: AnalysisStartRequest): Observable<AnalysisJobStateSnapshot> {
    return this.http.post<AnalysisJobStateSnapshot>('/analysis/jobs', request);
  }

  getAiModelOptions(): Observable<AnalysisAiModelOptionsResponse> {
    return this.http.get<AnalysisAiModelOptionsResponse>('/analysis/ai/options');
  }

  getAnalysis(analysisId: string): Observable<AnalysisJobStateSnapshot> {
    return this.http.get<AnalysisJobStateSnapshot>(`/analysis/jobs/${encodeURIComponent(analysisId)}`);
  }

  sendChatMessage(
    analysisId: string,
    request: AnalysisChatMessageRequest
  ): Observable<AnalysisJobStateSnapshot> {
    return this.http.post<AnalysisJobStateSnapshot>(
      `/analysis/jobs/${encodeURIComponent(analysisId)}/chat/messages`,
      request
    );
  }
}
