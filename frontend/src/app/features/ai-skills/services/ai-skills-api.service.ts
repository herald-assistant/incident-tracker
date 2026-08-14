import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AiSkillCatalogResponse,
  AiSkillDetailResponse,
  AiSkillUpdateRequest
} from '../models/ai-skills.models';

@Injectable({
  providedIn: 'root'
})
export class AiSkillsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/ai/skills';

  getCatalog(): Observable<AiSkillCatalogResponse> {
    return this.http.get<AiSkillCatalogResponse>(this.baseUrl);
  }

  getSkill(skillName: string): Observable<AiSkillDetailResponse> {
    return this.http.get<AiSkillDetailResponse>(
      `${this.baseUrl}/${encodeURIComponent(skillName)}`
    );
  }

  updateSkill(skillName: string, request: AiSkillUpdateRequest): Observable<AiSkillDetailResponse> {
    return this.http.put<AiSkillDetailResponse>(
      `${this.baseUrl}/${encodeURIComponent(skillName)}`,
      request
    );
  }

  restoreDefault(skillName: string): Observable<AiSkillDetailResponse> {
    return this.http.post<AiSkillDetailResponse>(
      `${this.baseUrl}/${encodeURIComponent(skillName)}/restore-default`,
      null
    );
  }
}
