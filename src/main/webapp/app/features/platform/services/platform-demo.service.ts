import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';

import {
  ConvertDemoRequest,
  CreateDemoWorkspaceRequest,
  DemoCredentials,
  DemoRequest,
  DemoRequestStatus,
  DemoWorkspace,
  DemoWorkspaceCreated,
} from '../models/demo.model';

/** Owner console — demo requests (`/api/platform/demo-requests`) and demo workspaces (`/api/platform/demos`). */
@Injectable({ providedIn: 'root' })
export class PlatformDemoService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);

  requests(): Observable<Page<DemoRequest>> {
    return this.http.get<Page<DemoRequest>>(this.url('api/platform/demo-requests'), { params: { size: 500 } });
  }

  updateRequest(id: string, body: { status?: DemoRequestStatus; notes?: string }): Observable<DemoRequest> {
    return this.http.patch<DemoRequest>(this.url(`api/platform/demo-requests/${encodeURIComponent(id)}`), body);
  }

  deleteRequest(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`api/platform/demo-requests/${encodeURIComponent(id)}`));
  }

  demos(): Observable<DemoWorkspace[]> {
    return this.http.get<DemoWorkspace[]>(this.url('api/platform/demos'));
  }

  create(body: CreateDemoWorkspaceRequest): Observable<DemoWorkspaceCreated> {
    return this.http.post<DemoWorkspaceCreated>(this.url('api/platform/demos'), body);
  }

  retry(id: string): Observable<DemoWorkspaceCreated> {
    return this.http.post<DemoWorkspaceCreated>(this.url(`api/platform/demos/${encodeURIComponent(id)}/retry`), {});
  }

  resetPassword(id: string): Observable<DemoCredentials> {
    return this.http.post<DemoCredentials>(this.url(`api/platform/demos/${encodeURIComponent(id)}/reset-password`), {});
  }

  extend(id: string, days: number): Observable<DemoWorkspace> {
    return this.http.post<DemoWorkspace>(this.url(`api/platform/demos/${encodeURIComponent(id)}/extend`), { days });
  }

  expire(id: string): Observable<DemoWorkspace> {
    return this.http.post<DemoWorkspace>(this.url(`api/platform/demos/${encodeURIComponent(id)}/expire`), {});
  }

  convert(id: string, body: ConvertDemoRequest): Observable<void> {
    return this.http.post<void>(this.url(`api/platform/demos/${encodeURIComponent(id)}/convert`), body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`api/platform/demos/${encodeURIComponent(id)}`));
  }

  private url(path: string): string {
    return this.config.getEndpointFor(path);
  }
}
