import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  AnalysisChatMessageRequest,
  AnalysisAiModelOptionsResponse,
  AnalysisJobInputOptionsResponse,
  AnalysisJobStateSnapshot,
  AnalysisStartRequest
} from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AnalysisApiService {
  private readonly baseUrl = '/api/analysis';
  private readonly http = inject(HttpClient);

  startAnalysis(request: AnalysisStartRequest): Observable<AnalysisJobStateSnapshot> {
    return this.http.post<AnalysisJobStateSnapshot>(`${this.baseUrl}/jobs`, this.toStartFormData(request));
  }

  getInputOptions(): Observable<AnalysisJobInputOptionsResponse> {
    return this.http.get<AnalysisJobInputOptionsResponse>(`${this.baseUrl}/jobs/input-options`);
  }

  getAiModelOptions(): Observable<AnalysisAiModelOptionsResponse> {
    return this.http.get<AnalysisAiModelOptionsResponse>(`${this.baseUrl}/ai/options`);
  }

  getAnalysis(analysisId: string): Observable<AnalysisJobStateSnapshot> {
    return this.http.get<AnalysisJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(analysisId)}`
    );
  }

  sendChatMessage(
    analysisId: string,
    request: AnalysisChatMessageRequest
  ): Observable<AnalysisJobStateSnapshot> {
    return this.http.post<AnalysisJobStateSnapshot>(
      `${this.baseUrl}/jobs/${encodeURIComponent(analysisId)}/chat/messages`,
      request
    );
  }

  private toStartFormData(request: AnalysisStartRequest): FormData {
    const formData = new FormData();
    formData.append('source', request.source || 'ELASTICSEARCH');
    if (request.correlationId) {
      formData.append('correlationId', request.correlationId);
    }
    if (request.logFile) {
      formData.append('logFile', request.logFile, request.logFile.name);
    }
    if (request.model) {
      formData.append('model', request.model);
    }
    if (request.reasoningEffort) {
      formData.append('reasoningEffort', request.reasoningEffort);
    }
    return formData;
  }
}
