import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  OpenQuestionDto,
  OperationalContextAiApiPreviewEndpointKey,
  OperationalContextBlastRadiusReadModel,
  OperationalContextBoundedContextRowDto,
  OperationalContextCodeSearchReadModel,
  OperationalContextCodeSearchScopeRowDto,
  OperationalContextEntityDetailDto,
  OperationalContextEntityRelationsReadModelDto,
  OperationalContextFlowReadModel,
  OperationalContextGlossaryRowDto,
  OperationalContextHandoffRuleRowDto,
  OperationalContextImplementationReadModel,
  OperationalContextProfiledReadModelDto,
  OperationalContextIntegrationRowDto,
  OperationalContextProcessRowDto,
  OperationalContextReadModelProfile,
  OperationalContextRepositoryRowDto,
  OperationalContextSearchResultDto,
  OperationalContextSummaryDto,
  OperationalContextSystemRowDto,
  OperationalContextTeamRowDto,
  ValidationFindingDto
} from '../models/operational-context.models';

export interface OperationalContextAiApiPreviewRequest {
  key: OperationalContextAiApiPreviewEndpointKey;
  label: string;
  url: string;
  request: Observable<OperationalContextProfiledReadModelDto>;
}

@Injectable({
  providedIn: 'root'
})
export class OperationalContextApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/operational-context';

  getSummary(): Observable<OperationalContextSummaryDto> {
    return this.http.get<OperationalContextSummaryDto>(`${this.baseUrl}/summary`);
  }

  getSystems(): Observable<OperationalContextSystemRowDto[]> {
    return this.http.get<OperationalContextSystemRowDto[]>(`${this.baseUrl}/systems`);
  }

  getRepositories(): Observable<OperationalContextRepositoryRowDto[]> {
    return this.http.get<OperationalContextRepositoryRowDto[]>(`${this.baseUrl}/repositories`);
  }

  getCodeSearchScopes(): Observable<OperationalContextCodeSearchScopeRowDto[]> {
    return this.http.get<OperationalContextCodeSearchScopeRowDto[]>(
      `${this.baseUrl}/code-search-scopes`
    );
  }

  getProcesses(): Observable<OperationalContextProcessRowDto[]> {
    return this.http.get<OperationalContextProcessRowDto[]>(`${this.baseUrl}/processes`);
  }

  getIntegrations(): Observable<OperationalContextIntegrationRowDto[]> {
    return this.http.get<OperationalContextIntegrationRowDto[]>(`${this.baseUrl}/integrations`);
  }

  getBoundedContexts(): Observable<OperationalContextBoundedContextRowDto[]> {
    return this.http.get<OperationalContextBoundedContextRowDto[]>(
      `${this.baseUrl}/bounded-contexts`
    );
  }

  getTeams(): Observable<OperationalContextTeamRowDto[]> {
    return this.http.get<OperationalContextTeamRowDto[]>(`${this.baseUrl}/teams`);
  }

  getGlossary(): Observable<OperationalContextGlossaryRowDto[]> {
    return this.http.get<OperationalContextGlossaryRowDto[]>(`${this.baseUrl}/glossary`);
  }

  getHandoffRules(): Observable<OperationalContextHandoffRuleRowDto[]> {
    return this.http.get<OperationalContextHandoffRuleRowDto[]>(
      `${this.baseUrl}/handoff-rules`
    );
  }

  getOpenQuestions(): Observable<OpenQuestionDto[]> {
    return this.http.get<OpenQuestionDto[]>(`${this.baseUrl}/open-questions`);
  }

  getValidation(): Observable<ValidationFindingDto[]> {
    return this.http.get<ValidationFindingDto[]>(`${this.baseUrl}/validation`);
  }

  search(query: string): Observable<OperationalContextSearchResultDto[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<OperationalContextSearchResultDto[]>(`${this.baseUrl}/search`, {
      params
    });
  }

  getProfiledSearch(
    query: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(`${this.baseUrl}/search`, {
      params: this.profiledSearchParams(query, profile)
    });
  }

  profiledSearchUrl(query: string, profile: OperationalContextReadModelProfile): string {
    return `${this.baseUrl}/search?${this.profiledSearchParams(query, profile).toString()}`;
  }

  getEntity(type: string, id: string): Observable<OperationalContextEntityDetailDto> {
    const params = new HttpParams().set('id', id);
    return this.http.get<OperationalContextEntityDetailDto>(
      `${this.baseUrl}/entities/${encodeURIComponent(type)}`,
      { params }
    );
  }

  getProfiledEntity(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(
      `${this.baseUrl}/entities/${encodeURIComponent(type)}`,
      { params: this.profiledEntityIdParams(id, profile) }
    );
  }

  getEntityRelationsReadModel(
    type: string,
    id: string
  ): Observable<OperationalContextEntityRelationsReadModelDto> {
    return this.http.get<OperationalContextEntityRelationsReadModelDto>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/relations`,
      { params: this.entityIdParams(id) }
    );
  }

  getProfiledEntityRelationsReadModel(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/relations`,
      { params: this.profiledEntityIdParams(id, profile) }
    );
  }

  getCodeSearchReadModel(
    type: string,
    id: string
  ): Observable<OperationalContextCodeSearchReadModel> {
    return this.http.get<OperationalContextCodeSearchReadModel>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/code-search`,
      { params: this.entityIdParams(id) }
    );
  }

  getProfiledCodeSearchReadModel(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/code-search`,
      { params: this.profiledEntityIdParams(id, profile) }
    );
  }

  getImplementationReadModel(
    type: string,
    id: string
  ): Observable<OperationalContextImplementationReadModel> {
    return this.http.get<OperationalContextImplementationReadModel>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/implementations`,
      { params: this.entityIdParams(id) }
    );
  }

  getProfiledImplementationReadModel(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/implementations`,
      { params: this.profiledEntityIdParams(id, profile) }
    );
  }

  getFlowReadModel(type: string, id: string): Observable<OperationalContextFlowReadModel> {
    return this.http.get<OperationalContextFlowReadModel>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/flow`,
      { params: this.entityIdParams(id) }
    );
  }

  getProfiledFlowReadModel(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/flow`,
      { params: this.profiledEntityIdParams(id, profile) }
    );
  }

  getBlastRadiusReadModel(
    type: string,
    id: string
  ): Observable<OperationalContextBlastRadiusReadModel> {
    return this.http.get<OperationalContextBlastRadiusReadModel>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/blast-radius`,
      { params: this.entityIdParams(id) }
    );
  }

  getProfiledBlastRadiusReadModel(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    return this.http.get<OperationalContextProfiledReadModelDto>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/blast-radius`,
      { params: this.profiledEntityIdParams(id, profile) }
    );
  }

  getAiApiPreviewRequests(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile,
    includeReadModels: boolean
  ): OperationalContextAiApiPreviewRequest[] {
    const requests: OperationalContextAiApiPreviewRequest[] = [
      {
        key: 'entity',
        label: 'Entity detail',
        url: this.entityProfileUrl(type, id, profile),
        request: this.getProfiledEntity(type, id, profile)
      }
    ];

    if (!includeReadModels) {
      return requests;
    }

    return [
      ...requests,
      this.readModelPreviewRequest('relations', 'Relations', type, id, profile),
      this.readModelPreviewRequest('code-search', 'Code search', type, id, profile),
      this.readModelPreviewRequest('implementations', 'Implementations', type, id, profile),
      this.readModelPreviewRequest('flow', 'Flow', type, id, profile),
      this.readModelPreviewRequest('blast-radius', 'Blast radius', type, id, profile)
    ];
  }

  private entityIdParams(id: string): HttpParams {
    return new HttpParams().set('id', id);
  }

  private profiledSearchParams(
    query: string,
    profile: OperationalContextReadModelProfile
  ): HttpParams {
    return new HttpParams().set('q', query).set('profile', profile);
  }

  private profiledEntityIdParams(
    id: string,
    profile: OperationalContextReadModelProfile
  ): HttpParams {
    return this.entityIdParams(id).set('profile', profile);
  }

  private entityProfileUrl(
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): string {
    return `${this.baseUrl}/entities/${encodeURIComponent(type)}?${this.profiledEntityIdParams(id, profile).toString()}`;
  }

  private readModelProfileUrl(
    type: string,
    id: string,
    endpoint: OperationalContextAiApiPreviewEndpointKey,
    profile: OperationalContextReadModelProfile
  ): string {
    return `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/${endpoint}?${this.profiledEntityIdParams(id, profile).toString()}`;
  }

  private readModelPreviewRequest(
    key: Exclude<OperationalContextAiApiPreviewEndpointKey, 'entity' | 'search'>,
    label: string,
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): OperationalContextAiApiPreviewRequest {
    return {
      key,
      label,
      url: this.readModelProfileUrl(type, id, key, profile),
      request: this.profiledReadModelRequest(key, type, id, profile)
    };
  }

  private profiledReadModelRequest(
    key: Exclude<OperationalContextAiApiPreviewEndpointKey, 'entity' | 'search'>,
    type: string,
    id: string,
    profile: OperationalContextReadModelProfile
  ): Observable<OperationalContextProfiledReadModelDto> {
    switch (key) {
      case 'relations':
        return this.getProfiledEntityRelationsReadModel(type, id, profile);
      case 'code-search':
        return this.getProfiledCodeSearchReadModel(type, id, profile);
      case 'implementations':
        return this.getProfiledImplementationReadModel(type, id, profile);
      case 'flow':
        return this.getProfiledFlowReadModel(type, id, profile);
      case 'blast-radius':
        return this.getProfiledBlastRadiusReadModel(type, id, profile);
    }
  }
}
