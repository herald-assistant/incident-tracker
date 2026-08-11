import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  OperationalContextDeleteImpact,
  OperationalContextEditableEntity,
  OperationalContextEntityWriteRequest,
  OperationalContextMaintenanceCapabilities,
  OperationalContextMutationResult,
  OperationalContextWritableType
} from '../models/operational-context-maintenance.models';

@Injectable({ providedIn: 'root' })
export class OperationalContextMaintenanceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/operational-context/catalog';

  getCapabilities(): Observable<OperationalContextMaintenanceCapabilities> {
    return this.http.get<OperationalContextMaintenanceCapabilities>(`${this.baseUrl}/capabilities`);
  }

  getEntity(
    type: OperationalContextWritableType,
    id: string
  ): Observable<OperationalContextEditableEntity> {
    return this.http.get<OperationalContextEditableEntity>(
      `${this.baseUrl}/entities/${encodeURIComponent(type)}/${encodeURIComponent(id)}`
    );
  }

  create(request: OperationalContextEntityWriteRequest): Observable<OperationalContextMutationResult> {
    return this.http.post<OperationalContextMutationResult>(
      `${this.baseUrl}/entities/${encodeURIComponent(request.type)}`,
      request
    );
  }

  update(request: OperationalContextEntityWriteRequest): Observable<OperationalContextMutationResult> {
    return this.http.put<OperationalContextMutationResult>(
      `${this.baseUrl}/entities/${encodeURIComponent(request.type)}/${encodeURIComponent(request.id)}`,
      request
    );
  }

  getDeleteImpact(
    type: OperationalContextWritableType,
    id: string
  ): Observable<OperationalContextDeleteImpact> {
    return this.http.get<OperationalContextDeleteImpact>(
      `${this.baseUrl}/entities/${encodeURIComponent(type)}/${encodeURIComponent(id)}/delete-impact`
    );
  }

  delete(type: OperationalContextWritableType, id: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/entities/${encodeURIComponent(type)}/${encodeURIComponent(id)}`
    );
  }
}
