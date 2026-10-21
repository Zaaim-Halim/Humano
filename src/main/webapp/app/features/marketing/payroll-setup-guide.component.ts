import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import {
  FUNCTION_CATEGORY_LABEL,
  FUNCTION_CATEGORY_ORDER,
  FUNCTION_DOCS,
  RECIPES,
  VARIABLE_DOCS,
  VARIABLE_GROUP_ORDER,
} from 'app/features/admin/pay-rules/formula-catalog';

/**
 * Public guide (no sign-in) for workspaces that set payroll up themselves — no statutory template for
 * their country, or they chose none at sign-up. It walks through the pay-rules screen and renders the
 * same formula reference the in-app editor shows (`formula-catalog.ts`), so there is one source for
 * what the functions and variables mean.
 */
@Component({
  selector: 'hum-payroll-setup-guide',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-guide' },
  imports: [RouterLink, TranslatePipe, LucideAngularModule],
  templateUrl: './payroll-setup-guide.component.html',
})
export default class PayrollSetupGuideComponent {
  protected readonly categories = FUNCTION_CATEGORY_ORDER.map(category => ({
    label: FUNCTION_CATEGORY_LABEL[category],
    functions: Object.entries(FUNCTION_DOCS)
      .filter(([, doc]) => doc.category === category)
      .map(([name, doc]) => ({ signature: `#${name}(${doc.params.join(', ')})`, ...doc })),
  }));

  protected readonly variableGroups = VARIABLE_GROUP_ORDER.map(group => ({
    label: group.label,
    variables: Object.entries(VARIABLE_DOCS)
      .filter(([, doc]) => doc.group === group.id)
      .map(([name, doc]) => ({ name: `#${name}`, description: doc.description })),
  })).filter(g => g.variables.length > 0);

  protected readonly recipes = RECIPES;
}
