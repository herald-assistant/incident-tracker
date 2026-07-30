import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AnalysisAiModelOptionsResponse } from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class AiOptionsApiService {
  private readonly http = inject(HttpClient);

  getOptions(): Observable<AnalysisAiModelOptionsResponse> {
    return this.http.get<AnalysisAiModelOptionsResponse>('/api/analysis/ai/options');
  }
}
