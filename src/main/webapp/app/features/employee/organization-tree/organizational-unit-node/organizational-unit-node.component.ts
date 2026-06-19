import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { BadgeComponent } from 'app/shared/ui';
import { BadgeTone } from 'app/config/status-map';

import { OrganizationalUnitTreeNode } from '../../models/hierarchy.model';

/** OrganizationalUnitType → badge tone. Unmapped types fall back to neutral. */
const TYPE_TONE: Record<string, BadgeTone> = {
  DIRECTORATE: 'brand',
  DIVISION: 'brand',
  BOARD: 'brand',
  DEPARTMENT: 'info',
  SECTOR: 'info',
  SECTION: 'info',
  BRANCH: 'warning',
  OFFICE: 'warning',
  TEAM: 'success',
  SQUAD: 'success',
  CELL: 'success',
  GROUP: 'success',
};

/**
 * Recursive node of the Organization Tree — one unit card with an
 * expand/collapse control that reveals its sub-units (which are themselves
 * `hum-organizational-unit-node`s). Type colour comes from {@link TYPE_TONE}.
 */
@Component({
  selector: 'hum-organizational-unit-node',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, BadgeComponent, OrganizationalUnitNodeComponent],
  templateUrl: './organizational-unit-node.component.html',
  styleUrls: ['./organizational-unit-node.component.scss'],
})
export class OrganizationalUnitNodeComponent {
  readonly node = input.required<OrganizationalUnitTreeNode>();

  protected readonly expanded = signal(true);
  protected readonly childCount = computed(() => this.node().children?.length ?? 0);
  protected readonly hasChildren = computed(() => this.childCount() > 0);

  protected readonly hasHeadcount = computed(() => {
    const h = this.node().headcount;
    return h !== null && h !== undefined;
  });

  protected readonly typeTone = computed<BadgeTone>(() => TYPE_TONE[this.node().type] ?? 'neutral');
  protected readonly typeLabel = computed(() => {
    const type = this.node().type;
    return type.charAt(0) + type.slice(1).toLowerCase();
  });

  protected toggle(): void {
    this.expanded.update(v => !v);
  }
}
