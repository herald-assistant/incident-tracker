import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  OpenQuestionDto,
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
  OperationalContextIntegrationRowDto,
  OperationalContextProcessRowDto,
  OperationalContextRepositoryRowDto,
  OperationalContextSearchResultDto,
  OperationalContextSummaryDto,
  OperationalContextSystemRowDto,
  OperationalContextTeamRowDto,
  ValidationFindingDto
} from '../models/operational-context.models';

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

  getEntity(type: string, id: string): Observable<OperationalContextEntityDetailDto> {
    const params = new HttpParams().set('id', id);
    return this.http.get<OperationalContextEntityDetailDto>(
      `${this.baseUrl}/entities/${encodeURIComponent(type)}`,
      { params }
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

  getCodeSearchReadModel(
    type: string,
    id: string
  ): Observable<OperationalContextCodeSearchReadModel> {
    return this.http.get<OperationalContextCodeSearchReadModel>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/code-search`,
      { params: this.entityIdParams(id) }
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

  getFlowReadModel(type: string, id: string): Observable<OperationalContextFlowReadModel> {
    return this.http.get<OperationalContextFlowReadModel>(
      `${this.baseUrl}/read-model/entities/${encodeURIComponent(type)}/flow`,
      { params: this.entityIdParams(id) }
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

  private entityIdParams(id: string): HttpParams {
    return new HttpParams().set('id', id);
  }
}
