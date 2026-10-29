import { CompanyProfile } from 'app/core/company/company.service';
import { countryName } from 'app/shared/util/countries';

import { PayrollTemplate } from '../models/payroll-template.model';

/** One step of the dashboard's "Get started" checklist; its texts are `humano.dashboard.setup.<id>.*`. */
export interface SetupStep {
  id: 'company' | 'people' | 'template' | 'payroll' | 'plan';
  icon: string;
  link: string;
  done: boolean;
  /** Values for the step's texts. */
  params?: Record<string, string>;
}

/** What the workspace data says about each step. */
export interface SetupState {
  profile: CompanyProfile | null;
  employees: number;
  /** Payroll runs so far; null without access to payroll. */
  runs: number | null;
  /** Every payroll template with this workspace's adoption; null while loading or without access. */
  templates: PayrollTemplate[] | null;
  planChosen: boolean;
}

/**
 * The "Get started" steps, each done when the workspace data says so; nothing is tracked separately.
 * <p>
 * A payroll template chosen at sign-up is added switched off, so it gets its own step, leading to that
 * template, before the first payroll run. Until a template is on, the payroll step stays open: a run
 * without it calculates no statutory taxes. Adopting a template also sets the default currency, so in
 * that workspace only the logo shows the admin completed the company profile.
 */
export function setupSteps(state: SetupState): SetupStep[] {
  const adopted = (state.templates ?? []).filter(t => t.adoption);
  const enabled = adopted.find(t => t.adoption?.enabled);
  const off = adopted.find(t => !t.adoption?.enabled);
  const profile = state.profile;
  const steps: SetupStep[] = [
    {
      id: 'company',
      icon: 'building-2',
      link: '/settings/company',
      done: !!profile && (!!profile.logo || (!!profile.defaultCurrencyId && adopted.length === 0)),
    },
    { id: 'people', icon: 'user-plus', link: '/employees/new', done: state.employees > 0 },
  ];
  if (adopted.length) {
    const shown = enabled ?? off!;
    steps.push({
      id: 'template',
      icon: 'landmark',
      link: enabled ? '/payroll/templates' : `/payroll/templates/${off!.id}`,
      done: !!enabled,
      params: { country: countryName(shown.country) },
    });
  }
  if (state.runs !== null) {
    steps.push({ id: 'payroll', icon: 'wallet', link: '/payroll/runs', done: state.runs > 0 && (!adopted.length || !!enabled) });
  }
  steps.push({ id: 'plan', icon: 'credit-card', link: '/settings/billing', done: state.planChosen });
  return steps;
}
