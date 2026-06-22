import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { Page } from 'app/core/api';

import { CreatePayRuleRequest, FormulaMetadata, FormulaValidationResult, PayComponent, PayRuleSummary } from '../models/pay-rule.model';

/**
 * Pay rules + the formula-engine contract — `/api/payroll/pay-rules` and
 * `/api/payroll/pay-components` (gated `MANAGE_PAY_COMPONENTS`). Backs the
 * HR/admin formula editor.
 */
@Injectable({ providedIn: 'root' })
export class PayRuleService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);
  private readonly rulesUrl = this.config.getEndpointFor('api/payroll/pay-rules');
  private readonly componentsUrl = this.config.getEndpointFor('api/payroll/pay-components');

  /** `GET /api/payroll/pay-rules/formula-metadata` — functions, variables, constants, limits. */
  formulaMetadata(): Observable<FormulaMetadata> {
    return this.http.get<FormulaMetadata>(`${this.rulesUrl}/formula-metadata`);
  }

  /** `GET /api/payroll/pay-components` — components a rule can attach to (first page, large size). */
  listComponents(): Observable<PayComponent[]> {
    return this.http
      .get<Page<PayComponent>>(this.componentsUrl, { params: { page: '0', size: '200', sort: 'code,asc' } })
      .pipe(map(p => p.content));
  }

  /** `GET /api/payroll/pay-components/{id}/active-rules` — rules currently on a component. */
  activeRules(componentId: string): Observable<PayRuleSummary[]> {
    return this.http.get<PayRuleSummary[]>(`${this.componentsUrl}/${componentId}/active-rules`);
  }

  /** `POST /api/payroll/pay-rules/validate-formula` — parse + sample-evaluate a formula. */
  validateFormula(formula: string): Observable<FormulaValidationResult> {
    return this.http.post<FormulaValidationResult>(`${this.rulesUrl}/validate-formula`, { formula });
  }

  /** `POST /api/payroll/pay-rules` — create a rule on a component. */
  createRule(body: CreatePayRuleRequest): Observable<PayComponent> {
    return this.http.post<PayComponent>(this.rulesUrl, body);
  }

  /** `POST /api/payroll/pay-rules/{ruleId}/active?active=` — activate/deactivate. */
  setActive(ruleId: string, active: boolean): Observable<PayComponent> {
    return this.http.post<PayComponent>(`${this.rulesUrl}/${ruleId}/active`, null, { params: { active } });
  }
}
