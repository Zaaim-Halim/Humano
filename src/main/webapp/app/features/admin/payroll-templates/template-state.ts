import { BadgeTone } from 'app/config/status-map';

import { PayrollTemplate } from '../models/payroll-template.model';

/** Where a template stands for this workspace, as one badge: in use, adopted but off, a draft, or just available. */
export function templateState(t: PayrollTemplate): { key: string; tone: BadgeTone } {
  if (t.adoption?.enabled) return { key: 'humano.payrollTemplates.state.on', tone: 'success' };
  if (t.adoption) return { key: 'humano.payrollTemplates.state.off', tone: 'warning' };
  if (!t.adoptable) return { key: 'humano.payrollTemplates.state.draft', tone: 'neutral' };
  return { key: 'humano.payrollTemplates.state.available', tone: 'info' };
}
