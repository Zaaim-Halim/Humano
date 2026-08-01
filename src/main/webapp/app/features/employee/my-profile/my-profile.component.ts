import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { AccountService } from 'app/core/auth/account.service';
import {
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
} from 'app/shared/ui';

import {
  CollectionColumn,
  CollectionField,
  CollectionService,
  EmployeeCollectionComponent,
} from '../collection/employee-collection.component';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { MeAddressService, MeEmergencyContactService } from '../services/me-collection.service';

/** One read-only label/value pair; `value` is already display-ready. */
interface Field {
  labelKey: string;
  value: string | null;
}

/** A self-maintained collection rendered under the read-only cards. */
interface EditableCollection {
  headingKey: string;
  service: CollectionService;
  columns: CollectionColumn[];
  fields: CollectionField[];
}

/**
 * My profile — the employee's own record.
 *
 * <p>Reads the profile {@link CurrentEmployeeService} already holds from
 * `GET /api/me/employee` (security-context scoped, so it is always the caller's own
 * record), plus name/email from `GET /api/account` since those live on the user
 * rather than the employee row.
 *
 * <p>The employment and identification cards are read-only — those fields are written
 * through the HR-gated `/api/hr/**` endpoints, so the page names who to ask instead of
 * offering inputs that would 403. Addresses and emergency contacts *are* editable here,
 * through the self-scoped `/api/me/**` endpoints that derive the owner from the security
 * context (see `MeProfileResource`).
 *
 * <p>Government identification is masked until explicitly revealed — it is the
 * caller's own data, but it should not sit on screen by default.
 */
@Component({
  selector: 'hum-my-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    EmployeeCollectionComponent,
  ],
  templateUrl: './my-profile.component.html',
})
export default class MyProfileComponent {
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly accountService = inject(AccountService);

  protected readonly account = this.accountService.trackCurrentAccount();
  protected readonly profile = this.currentEmployee.currentEmployee;
  protected readonly resolved = this.currentEmployee.resolved;

  protected readonly revealIds = signal(false);

  /** "First Last", falling back to the login — the employee row carries no name. */
  protected readonly fullName = computed(() => {
    const a = this.account();
    const name = `${a?.firstName ?? ''} ${a?.lastName ?? ''}`.trim();
    return name || (a?.login ?? '');
  });

  protected readonly employment = computed<Field[]>(() => {
    const p = this.profile();
    if (!p) return [];
    const d = p.personalDetails;
    return [
      { labelKey: 'humano.myProfile.employeeNumber', value: d.employeeNumber ?? null },
      { labelKey: 'humano.myProfile.position', value: p.positionName },
      { labelKey: 'humano.myProfile.department', value: p.departmentName },
      { labelKey: 'humano.myProfile.unit', value: p.unitName },
      { labelKey: 'humano.myProfile.manager', value: p.managerInfo },
      { labelKey: 'humano.myProfile.employmentType', value: p.employmentType?.name ?? null },
      { labelKey: 'humano.myProfile.grade', value: p.grade?.name ?? null },
      { labelKey: 'humano.myProfile.level', value: p.level?.name ?? null },
      { labelKey: 'humano.myProfile.category', value: p.category?.name ?? null },
      { labelKey: 'humano.myProfile.startDate', value: p.startDate },
      { labelKey: 'humano.myProfile.endDate', value: p.endDate },
      { labelKey: 'humano.myProfile.probationEnd', value: d.probationEndDate ?? null },
      { labelKey: 'humano.myProfile.confirmationDate', value: d.confirmationDate ?? null },
      { labelKey: 'humano.myProfile.workLocation', value: d.workLocation ?? null },
      { labelKey: 'humano.myProfile.fte', value: d.fte === null || d.fte === undefined ? null : String(d.fte) },
    ];
  });

  protected readonly personal = computed<Field[]>(() => {
    const p = this.profile();
    if (!p) return [];
    const d = p.personalDetails;
    return [
      { labelKey: 'humano.myProfile.email', value: this.account()?.email ?? null },
      { labelKey: 'humano.myProfile.phone', value: p.phone },
      { labelKey: 'humano.myProfile.workPhone', value: d.workPhone ?? null },
      { labelKey: 'humano.myProfile.birthDate', value: d.birthDate ?? null },
      { labelKey: 'humano.myProfile.placeOfBirth', value: d.placeOfBirth ?? null },
      { labelKey: 'humano.myProfile.gender', value: d.gender ?? null },
      { labelKey: 'humano.myProfile.nationality', value: p.nationality?.name ?? null },
      { labelKey: 'humano.myProfile.maritalStatus', value: p.maritalStatus?.name ?? null },
      { labelKey: 'humano.myProfile.country', value: p.countryName },
    ];
  });

  /** Government ids, masked to their last four characters unless revealed. */
  protected readonly governmentIds = computed<Field[]>(() => {
    const g = this.profile()?.governmentIds;
    if (!g) return [];
    const show = this.revealIds();
    const mask = (v: string | null | undefined): string | null => {
      if (!v) return null;
      return show ? v : `•••• ${v.slice(-4)}`;
    };
    return [
      { labelKey: 'humano.myProfile.nationalId', value: mask(g.nationalId) },
      { labelKey: 'humano.myProfile.passportNumber', value: mask(g.passportNumber) },
      { labelKey: 'humano.myProfile.taxNumber', value: mask(g.taxNumber) },
      { labelKey: 'humano.myProfile.socialSecurityNumber', value: mask(g.socialSecurityNumber) },
    ];
  });

  /** True when the profile carries no government id at all — then the card is pointless. */
  protected readonly hasGovernmentIds = computed(() => {
    const g = this.profile()?.governmentIds;
    return !!g && !!(g.nationalId ?? g.passportNumber ?? g.taxNumber ?? g.socialSecurityNumber);
  });

  /**
   * The two collections an employee maintains themselves, via `/api/me/**`. Kept to
   * addresses and emergency contacts deliberately — everything else on the record is
   * HR-written, and its endpoints would 403 for a plain employee.
   */
  protected readonly editable: EditableCollection[] = [
    {
      headingKey: 'humano.myProfile.addressesTitle',
      service: inject(MeAddressService),
      columns: [
        { key: 'type', label: 'Type' },
        { key: 'street', label: 'Street' },
        { key: 'city', label: 'City' },
        { key: 'postalCode', label: 'Postal code' },
        { key: 'primary', label: 'Primary' },
      ],
      fields: [
        { key: 'type', label: 'Type (e.g. HOME)' },
        { key: 'street', label: 'Street' },
        { key: 'building', label: 'Building' },
        { key: 'apartment', label: 'Apartment' },
        { key: 'city', label: 'City' },
        { key: 'state', label: 'State' },
        { key: 'postalCode', label: 'Postal code' },
        { key: 'primary', label: 'Primary address', type: 'checkbox' },
      ],
    },
    {
      headingKey: 'humano.myProfile.emergencyContactsTitle',
      service: inject(MeEmergencyContactService),
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'relationship', label: 'Relationship' },
        { key: 'phone', label: 'Phone' },
      ],
      fields: [
        { key: 'name', label: 'Name', required: true },
        { key: 'relationship', label: 'Relationship' },
        { key: 'phone', label: 'Phone' },
        { key: 'email', label: 'Email' },
      ],
    },
  ];

  constructor() {
    this.currentEmployee.resolve();
  }

  protected retry(): void {
    this.currentEmployee.resolve(true);
  }
}
