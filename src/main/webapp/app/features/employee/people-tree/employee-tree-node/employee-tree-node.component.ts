import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AvatarComponent, BadgeComponent } from 'app/shared/ui';

import { EmployeeTreeNode } from '../../models/hierarchy.model';

/**
 * Recursive node of the People Tree — one employee card with an expand/collapse
 * control that reveals its direct reports (which are themselves
 * `hum-employee-tree-node`s). Status resolves through STATUS_MAP via `hum-badge`.
 */
@Component({
  selector: 'hum-employee-tree-node',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, AvatarComponent, BadgeComponent, EmployeeTreeNodeComponent],
  templateUrl: './employee-tree-node.component.html',
  styleUrls: ['./employee-tree-node.component.scss'],
})
export class EmployeeTreeNodeComponent {
  readonly node = input.required<EmployeeTreeNode>();

  protected readonly expanded = signal(true);
  protected readonly childCount = computed(() => this.node().children?.length ?? 0);
  protected readonly hasChildren = computed(() => this.childCount() > 0);

  protected toggle(): void {
    this.expanded.update(v => !v);
  }
}
