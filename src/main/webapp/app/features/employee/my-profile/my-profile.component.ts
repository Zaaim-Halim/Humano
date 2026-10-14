import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { ButtonComponent, EmptyStateComponent, SkeletonRowComponent, TabItem, TabsComponent } from 'app/shared/ui';

import {
  CollectionColumn,
  CollectionField,
  CollectionService,
  EmployeeCollectionComponent,
  RecordView,
} from '../collection/employee-collection.component';
import { EmployeeLeaveComponent } from '../insights/employee-leave.component';
import { EmployeePayComponent } from '../insights/employee-pay.component';
import { EmployeePerformanceComponent } from '../insights/employee-performance.component';
import { EmployeeHeroComponent } from '../profile/employee-hero.component';
import { EmployeeOverviewComponent } from '../profile/employee-overview.component';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { MeAddressService, MeEmergencyContactService } from '../services/me-collection.service';

type MyProfileTab = 'overview' | 'pay' | 'leave' | 'performance' | 'details';

/** A self-maintained collection rendered under the read-only cards. */
interface EditableCollection {
  headingKey: string;
  itemKey: string;
  icon: string;
  present: (row: any) => RecordView; // eslint-disable-line @typescript-eslint/no-explicit-any
  service: CollectionService;
  columns: CollectionColumn[];
  fields: CollectionField[];
}

/**
 * My profile — the employee's own record, laid out like the HR employee page.
 *
 * <p>The profile comes from {@link CurrentEmployeeService} (`GET /api/me/employee`, always the
 * caller's own record). Tabs:
 * - Overview: the shared header (with photo upload) and overview cards; government ids masked
 *   until revealed. Read-only — those fields are HR-written.
 * - Pay, Time off, Performance: the shared insight components reading self-scoped data
 *   (`/api/me/compensation`, `/api/me/payslips`, `/api/me/performance-reviews`, own leave).
 * - My details: addresses and emergency contacts, editable through `/api/me/**`.
 */
@Component({
  selector: 'hum-my-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    TabsComponent,
    EmployeeCollectionComponent,
    EmployeeHeroComponent,
    EmployeeOverviewComponent,
    EmployeePayComponent,
    EmployeeLeaveComponent,
    EmployeePerformanceComponent,
  ],
  templateUrl: './my-profile.component.html',
})
export default class MyProfileComponent {
  private readonly translate = inject(TranslateService);
  private readonly currentEmployee = inject(CurrentEmployeeService);

  protected readonly profile = this.currentEmployee.currentEmployee;
  protected readonly resolved = this.currentEmployee.resolved;

  protected readonly tab = signal<MyProfileTab>('overview');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'overview', label: this.t('humano.employee360.tabOverview'), icon: 'layout-grid' },
    { id: 'pay', label: this.t('humano.employee360.tabCompensation'), icon: 'wallet' },
    { id: 'leave', label: this.t('humano.employee360.tabLeaves'), icon: 'palmtree' },
    { id: 'performance', label: this.t('humano.employee360.tabPerformance'), icon: 'star' },
    { id: 'details', label: this.t('humano.myProfile.detailsTab'), icon: 'folder' },
  ]);

  protected onImageChanged(imageUrl: string | null): void {
    const p = this.profile();
    if (p) this.currentEmployee.currentEmployee.set({ ...p, imageUrl });
  }

  /**
   * The two collections an employee maintains themselves, via `/api/me/**`. Kept to
   * addresses and emergency contacts deliberately — everything else on the record is
   * HR-written, and its endpoints would 403 for a plain employee.
   */
  protected readonly editable: EditableCollection[] = [
    {
      headingKey: 'humano.myProfile.addressesTitle',
      itemKey: 'humano.records.section.addresses.item',
      icon: 'map-pin',
      service: inject(MeAddressService),
      columns: [{ key: 'street', label: 'humano.records.field.street' }],
      fields: [
        {
          key: 'type',
          label: 'humano.records.field.type',
          type: 'select',
          options: ['', 'Home', 'Work', 'Mailing', 'Temporary'].map(v => ({ value: v, label: v || '—' })),
        },
        { key: 'street', label: 'humano.records.field.street' },
        { key: 'building', label: 'humano.records.field.building' },
        { key: 'apartment', label: 'humano.records.field.apartment' },
        { key: 'city', label: 'humano.records.field.city' },
        { key: 'state', label: 'humano.records.field.state' },
        { key: 'postalCode', label: 'humano.records.field.postalCode' },
        { key: 'primary', label: 'humano.records.field.primaryAddress', type: 'checkbox' },
      ],
      present: (a: Record<string, string | boolean | null>) => ({
        icon: a['type'] === 'Work' ? 'building-2' : 'house',
        title: [a['street'], a['building'], a['apartment']].filter(Boolean).join(', ') || String(a['city'] ?? '—'),
        subtitle: [a['city'], [a['state'], a['postalCode']].filter(Boolean).join(' ')].filter(Boolean).join(', '),
        facts: [{ icon: 'tag', text: a['type'] as string | null }],
        badges: a['primary'] ? [{ label: String(this.translate.instant('humano.records.primary')), tone: 'brand' as const }] : [],
      }),
    },
    {
      headingKey: 'humano.myProfile.emergencyContactsTitle',
      itemKey: 'humano.records.section.contacts.item',
      icon: 'heart-handshake',
      service: inject(MeEmergencyContactService),
      columns: [{ key: 'name', label: 'humano.records.field.name' }],
      fields: [
        { key: 'name', label: 'humano.records.field.name', required: true },
        { key: 'relationship', label: 'humano.records.field.relationship' },
        { key: 'phone', label: 'humano.records.field.phone', type: 'tel' },
        { key: 'email', label: 'humano.records.field.email', type: 'email' },
      ],
      present: (c: Record<string, string | null>) => ({
        title: c['name'] ?? '—',
        subtitle: c['relationship'],
        facts: [
          { icon: 'phone', text: c['phone'] },
          { icon: 'mail', text: c['email'] },
        ],
      }),
    },
  ];

  constructor() {
    this.currentEmployee.resolve();
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }

  protected retry(): void {
    this.currentEmployee.resolve(true);
  }
}
