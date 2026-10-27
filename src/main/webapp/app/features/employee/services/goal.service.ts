import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Goal, GoalCheckIn, GoalCheckInRequest, GoalTeamMember, SaveGoalRequest } from '../models/goal.model';

/**
 * Goals — `/api/hr/goals`. Rights follow the reporting line: the employee proposes and checks in,
 * their manager (or HR) agrees, sets, completes and cancels. Each goal carries what the signed-in
 * user may do with it (`can`).
 */
@Injectable({ providedIn: 'root' })
export class GoalService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/goals');

  mine(): Observable<Goal[]> {
    return this.http.get<Goal[]>(`${this.resourceUrl}/mine`);
  }

  team(): Observable<Goal[]> {
    return this.http.get<Goal[]>(`${this.resourceUrl}/team`);
  }

  /** The signed-in manager's direct reports; empty for everyone else. */
  teamMembers(): Observable<GoalTeamMember[]> {
    return this.http.get<GoalTeamMember[]>(`${this.resourceUrl}/team-members`);
  }

  ofEmployee(employeeId: string): Observable<Goal[]> {
    return this.http.get<Goal[]>(`${this.resourceUrl}/employees/${employeeId}`);
  }

  create(body: SaveGoalRequest): Observable<Goal> {
    return this.http.post<Goal>(this.resourceUrl, body);
  }

  update(id: string, body: SaveGoalRequest): Observable<Goal> {
    return this.http.put<Goal>(`${this.resourceUrl}/${id}`, body);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${id}`);
  }

  agree(id: string): Observable<Goal> {
    return this.http.post<Goal>(`${this.resourceUrl}/${id}/agree`, {});
  }

  complete(id: string): Observable<Goal> {
    return this.http.post<Goal>(`${this.resourceUrl}/${id}/complete`, {});
  }

  cancel(id: string, reason: string): Observable<Goal> {
    return this.http.post<Goal>(`${this.resourceUrl}/${id}/cancel`, { reason });
  }

  checkIns(id: string): Observable<GoalCheckIn[]> {
    return this.http.get<GoalCheckIn[]>(`${this.resourceUrl}/${id}/check-ins`);
  }

  checkIn(id: string, body: GoalCheckInRequest): Observable<Goal> {
    return this.http.post<Goal>(`${this.resourceUrl}/${id}/check-ins`, body);
  }
}
