import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { DepartmentService } from 'app/features/admin/services/department.service';
import { PositionService } from 'app/features/admin/services/position.service';
import { SelectOption } from 'app/shared/ui';

import { AddressService } from '../services/address.service';
import { EmergencyContactService } from '../services/emergency-contact.service';
import { EmployeeAssetService } from '../services/employee-asset.service';
import { EmployeeBankAccountService } from '../services/employee-bank-account.service';
import { EmployeeCertificationService } from '../services/employee-certification.service';
import { EmployeeEducationService } from '../services/employee-education.service';
import { EmployeeExperienceService } from '../services/employee-experience.service';
import { EmployeeLanguageService } from '../services/employee-language.service';
import { EmployeeLicenseService } from '../services/employee-license.service';
import { EmploymentContractService } from '../services/employment-contract.service';
import { WorkPermitService } from '../services/work-permit.service';
import {
  CollectionColumn,
  CollectionField,
  CollectionService,
  EmployeeCollectionComponent,
  RecordView,
} from '../collection/employee-collection.component';

/* eslint-disable @typescript-eslint/no-explicit-any */
interface Section {
  id: string;
  icon: string;
  service: CollectionService;
  columns: CollectionColumn[];
  fields: CollectionField[];
  present: (row: any) => RecordView;
}
/* eslint-enable @typescript-eslint/no-explicit-any */

interface Group {
  id: string;
  sections: Section[];
}

const F = 'humano.records.field.';
const DAY = 86_400_000;

/** Icon per equipment type. */
const ASSET_ICONS: Record<string, string> = {
  laptop: 'laptop',
  phone: 'smartphone',
  'access card': 'id-card',
  monitor: 'monitor',
  tablet: 'tablet',
  vehicle: 'car',
};

/**
 * The employee's records — every employee-owned collection, grouped into Personal & contact,
 * Employment, and Skills & qualifications, with a jump bar showing each section's count.
 *
 * Each section is the shared {@link EmployeeCollectionComponent} (card list + add/edit drawer +
 * delete) driven by its REST service, form fields (fixed choices as dropdowns, positions and
 * departments by name) and a presenter that turns a record into a card: icon, title, badges
 * (primary, verified, expired / expiring within 60 days, returned) and dates.
 */
@Component({
  selector: 'hum-employee-records',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, EmployeeCollectionComponent],
  templateUrl: './employee-records.component.html',
})
export class EmployeeRecordsComponent {
  readonly employeeId = input.required<string>();
  readonly canManage = input(true);

  private readonly translate = inject(TranslateService);
  private readonly addresses = inject(AddressService);
  private readonly contacts = inject(EmergencyContactService);
  private readonly bank = inject(EmployeeBankAccountService);
  private readonly contracts = inject(EmploymentContractService);
  private readonly permits = inject(WorkPermitService);
  private readonly assets = inject(EmployeeAssetService);
  private readonly education = inject(EmployeeEducationService);
  private readonly experience = inject(EmployeeExperienceService);
  private readonly certifications = inject(EmployeeCertificationService);
  private readonly licenses = inject(EmployeeLicenseService);
  private readonly languages = inject(EmployeeLanguageService);

  /** Position / department names, for contract cards and the contract form. */
  private readonly positionNames = signal(new Map<string, string>());
  private readonly departmentNames = signal(new Map<string, string>());

  /** Record count per section id, reported by each section after loading. */
  protected readonly counts = signal<Partial<Record<string, number>>>({});

  protected readonly groups = computed<Group[]>(() => {
    const positions = this.toOptions(this.positionNames());
    const departments = this.toOptions(this.departmentNames());
    return [
      {
        id: 'personal',
        sections: [
          {
            id: 'addresses',
            icon: 'map-pin',
            service: this.addresses,
            columns: [{ key: 'street', label: F + 'street' }],
            fields: [
              { key: 'type', label: F + 'type', type: 'select', options: this.choices(['Home', 'Work', 'Mailing', 'Temporary']) },
              { key: 'street', label: F + 'street' },
              { key: 'building', label: F + 'building' },
              { key: 'apartment', label: F + 'apartment' },
              { key: 'city', label: F + 'city' },
              { key: 'state', label: F + 'state' },
              { key: 'postalCode', label: F + 'postalCode' },
              { key: 'primary', label: F + 'primaryAddress', type: 'checkbox' },
            ],
            present: a => ({
              icon: a.type === 'Work' ? 'building-2' : 'house',
              title: [a.street, a.building, a.apartment].filter(Boolean).join(', ') || a.city || '—',
              subtitle: [a.city, [a.state, a.postalCode].filter(Boolean).join(' ')].filter(Boolean).join(', '),
              facts: [{ icon: 'tag', text: a.type }],
              badges: a.primary ? [{ label: this.t('humano.records.primary'), tone: 'brand' }] : [],
            }),
          },
          {
            id: 'contacts',
            icon: 'heart-handshake',
            service: this.contacts,
            columns: [{ key: 'name', label: F + 'name' }],
            fields: [
              { key: 'name', label: F + 'name', required: true },
              { key: 'relationship', label: F + 'relationship' },
              { key: 'phone', label: F + 'phone', type: 'tel' },
              { key: 'email', label: F + 'email', type: 'email' },
            ],
            present: c => ({
              title: c.name,
              subtitle: c.relationship,
              facts: [
                { icon: 'phone', text: c.phone },
                { icon: 'mail', text: c.email },
              ],
            }),
          },
          {
            id: 'bank',
            icon: 'landmark',
            service: this.bank,
            columns: [{ key: 'bankName', label: F + 'bankName' }],
            fields: [
              { key: 'bankName', label: F + 'bankName', required: true },
              { key: 'accountHolder', label: F + 'accountHolder' },
              { key: 'iban', label: F + 'iban' },
              { key: 'swift', label: F + 'swift' },
              {
                key: 'currency',
                label: F + 'currency',
                type: 'select',
                options: this.choices(['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'CHF', 'JPY', 'MAD']),
              },
              { key: 'primary', label: F + 'primaryAccount', type: 'checkbox' },
            ],
            present: b => ({
              title: b.bankName ?? '—',
              subtitle: b.accountHolder,
              facts: [
                { icon: 'hash', text: b.iban ? `•••• ${String(b.iban).slice(-4)}` : null },
                { icon: 'globe', text: b.swift },
                { icon: 'coins', text: b.currency },
              ],
              badges: b.primary ? [{ label: this.t('humano.records.primary'), tone: 'brand' }] : [],
            }),
          },
        ],
      },
      {
        id: 'employment',
        sections: [
          {
            id: 'contracts',
            icon: 'file-signature',
            service: this.contracts,
            columns: [{ key: 'contractNumber', label: F + 'contractNumber' }],
            fields: [
              { key: 'contractNumber', label: F + 'contractNumber' },
              {
                key: 'contractType',
                label: F + 'contractType',
                type: 'select',
                options: this.choices(['Permanent', 'Fixed-term', 'Part-time', 'Contractor', 'Internship']),
              },
              {
                key: 'status',
                label: F + 'status',
                type: 'select',
                options: this.choices(['Draft', 'Active', 'Suspended', 'Expired', 'Terminated']),
              },
              { key: 'positionId', label: F + 'position', type: 'select', options: positions },
              { key: 'departmentId', label: F + 'department', type: 'select', options: departments },
              { key: 'startDate', label: F + 'startDate', type: 'date' },
              { key: 'endDate', label: F + 'endDate', type: 'date' },
              { key: 'workingHours', label: F + 'workingHours', type: 'number' },
              { key: 'signedDate', label: F + 'signedDate', type: 'date' },
            ],
            present: c => {
              const ended = !!c.endDate && this.days(c.endDate) < 0;
              return {
                title: [c.contractType, c.positionId ? this.positionNames().get(c.positionId) : null].filter(Boolean).join(' · ') || '—',
                subtitle: c.contractNumber,
                facts: [
                  { icon: 'layers', text: c.departmentId ? this.departmentNames().get(c.departmentId) : null },
                  { icon: 'clock', text: c.workingHours ? this.t('humano.records.hoursWeek', { hours: Number(c.workingHours) }) : null },
                  { icon: 'pen-line', text: c.signedDate ? this.t('humano.records.signed', { date: this.date(c.signedDate) }) : null },
                ],
                aside: this.range(c.startDate, c.endDate),
                badges: c.status ? [{ label: c.status, tone: c.status === 'Active' && !ended ? 'success' : 'neutral' }] : [],
                muted: ended,
              };
            },
          },
          {
            id: 'permits',
            icon: 'stamp',
            service: this.permits,
            columns: [{ key: 'visaType', label: F + 'visaType' }],
            fields: [
              { key: 'visaType', label: F + 'visaType', required: true },
              { key: 'permitNumber', label: F + 'permitNumber' },
              { key: 'issueDate', label: F + 'issueDate', type: 'date' },
              { key: 'expiryDate', label: F + 'expiryDate', type: 'date' },
              { key: 'sponsor', label: F + 'sponsor' },
            ],
            present: p => ({
              title: p.visaType ?? '—',
              subtitle: p.permitNumber,
              facts: [
                { icon: 'building', text: p.sponsor ? this.t('humano.records.sponsor', { name: p.sponsor }) : null },
                { icon: 'calendar', text: p.issueDate ? this.t('humano.records.issued', { date: this.date(p.issueDate) }) : null },
              ],
              aside: p.expiryDate ? this.t('humano.records.expires', { date: this.date(p.expiryDate) }) : null,
              badges: this.expiryBadges(p.expiryDate),
            }),
          },
          {
            id: 'assets',
            icon: 'laptop',
            service: this.assets,
            columns: [{ key: 'type', label: F + 'assetType' }],
            fields: [
              {
                key: 'type',
                label: F + 'assetType',
                type: 'select',
                options: this.choices(['Laptop', 'Phone', 'Access card', 'Monitor', 'Tablet', 'Vehicle', 'Other']),
                required: true,
              },
              { key: 'identifier', label: F + 'identifier' },
              { key: 'assignedDate', label: F + 'assignedDate', type: 'date' },
              { key: 'returnedDate', label: F + 'returnedDate', type: 'date' },
            ],
            present: a => ({
              icon: ASSET_ICONS[String(a.type ?? '').toLowerCase()] ?? 'package',
              title: a.type ?? '—',
              subtitle: a.identifier,
              aside: a.returnedDate
                ? this.t('humano.records.returnedOn', { date: this.date(a.returnedDate) })
                : a.assignedDate
                  ? this.t('humano.records.assigned', { date: this.date(a.assignedDate) })
                  : null,
              badges: a.returnedDate ? [{ label: this.t('humano.records.returned'), tone: 'neutral' }] : [],
              muted: !!a.returnedDate,
            }),
          },
        ],
      },
      {
        id: 'qualifications',
        sections: [
          {
            id: 'education',
            icon: 'graduation-cap',
            service: this.education,
            columns: [{ key: 'institution', label: F + 'institution' }],
            fields: [
              { key: 'institution', label: F + 'institution', required: true },
              { key: 'degree', label: F + 'degree' },
              { key: 'fieldOfStudy', label: F + 'fieldOfStudy' },
              { key: 'graduationDate', label: F + 'graduationDate', type: 'date' },
            ],
            present: e => ({
              title: [e.degree, e.fieldOfStudy].filter(Boolean).join(' in ') || e.institution,
              subtitle: e.degree || e.fieldOfStudy ? e.institution : null,
              aside: e.graduationDate ? this.t('humano.records.graduated', { date: new Date(e.graduationDate).getFullYear() }) : null,
            }),
          },
          {
            id: 'experience',
            icon: 'briefcase',
            service: this.experience,
            columns: [{ key: 'company', label: F + 'company' }],
            fields: [
              { key: 'position', label: F + 'jobTitle', required: true },
              { key: 'company', label: F + 'company', required: true },
              { key: 'startDate', label: F + 'startDate', type: 'date' },
              { key: 'endDate', label: F + 'endDate', type: 'date' },
            ],
            present: x => ({
              title: x.position ?? '—',
              subtitle: x.company,
              facts: [{ icon: 'hourglass', text: this.duration(x.startDate, x.endDate) }],
              aside: this.range(x.startDate, x.endDate, true),
            }),
          },
          {
            id: 'certifications',
            icon: 'award',
            service: this.certifications,
            columns: [{ key: 'name', label: F + 'name' }],
            fields: [
              { key: 'name', label: F + 'name', required: true },
              { key: 'issuer', label: F + 'issuer' },
              { key: 'issueDate', label: F + 'issueDate', type: 'date' },
              { key: 'expiryDate', label: F + 'expiryDate', type: 'date' },
              { key: 'verified', label: F + 'verifiedCheck', type: 'checkbox' },
            ],
            present: c => this.credential(c),
          },
          {
            id: 'licenses',
            icon: 'id-card',
            service: this.licenses,
            columns: [{ key: 'name', label: F + 'name' }],
            fields: [
              { key: 'name', label: F + 'name', required: true },
              { key: 'issuer', label: F + 'issuer' },
              { key: 'issueDate', label: F + 'issueDate', type: 'date' },
              { key: 'expiryDate', label: F + 'expiryDate', type: 'date' },
              { key: 'verified', label: F + 'verifiedCheck', type: 'checkbox' },
            ],
            present: c => this.credential(c),
          },
          {
            id: 'languages',
            icon: 'languages',
            service: this.languages,
            columns: [{ key: 'language', label: F + 'language' }],
            fields: [
              { key: 'language', label: F + 'language', required: true },
              { key: 'speaking', label: F + 'speaking', type: 'select', options: this.levels() },
              { key: 'reading', label: F + 'reading', type: 'select', options: this.levels() },
              { key: 'writing', label: F + 'writing', type: 'select', options: this.levels() },
            ],
            present: l => ({
              title: l.language ?? '—',
              facts: [
                { icon: 'message-circle', text: l.speaking ? `${this.t('humano.records.skillSpeaking')}: ${l.speaking}` : null },
                { icon: 'book-open', text: l.reading ? `${this.t('humano.records.skillReading')}: ${l.reading}` : null },
                { icon: 'pen-line', text: l.writing ? `${this.t('humano.records.skillWriting')}: ${l.writing}` : null },
              ],
              badges: l.speaking ? [{ label: l.speaking, tone: l.speaking === 'Native' ? 'brand' : 'neutral' }] : [],
            }),
          },
        ],
      },
    ];
  });

  constructor() {
    const page = { page: 0, size: 500, sort: ['name,asc'] };
    inject(PositionService)
      .query(page)
      .subscribe({ next: res => this.positionNames.set(new Map(res.content.map(p => [p.id, p.name]))) });
    inject(DepartmentService)
      .query(page)
      .subscribe({ next: res => this.departmentNames.set(new Map(res.content.map(d => [d.id, d.name]))) });
  }

  protected setCount(id: string, n: number): void {
    this.counts.update(c => ({ ...c, [id]: n }));
  }

  protected jump(id: string): void {
    document.getElementById(`records-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Certification / licence card: verified state and expiry. */
  private credential(c: { name?: string; issuer?: string; issueDate?: string; expiryDate?: string; verified?: boolean }): RecordView {
    return {
      title: c.name ?? '—',
      subtitle: c.issuer,
      facts: [{ icon: 'calendar', text: c.issueDate ? this.t('humano.records.issued', { date: this.date(c.issueDate) }) : null }],
      aside: c.expiryDate ? this.t('humano.records.expires', { date: this.date(c.expiryDate) }) : null,
      badges: [
        c.verified
          ? { label: this.t('humano.records.verified'), tone: 'success' }
          : { label: this.t('humano.records.unverified'), tone: 'neutral' },
        ...this.expiryBadges(c.expiryDate),
      ],
      muted: !!c.expiryDate && this.days(c.expiryDate) < 0,
    };
  }

  private expiryBadges(expiry: string | null | undefined): NonNullable<RecordView['badges']> {
    if (!expiry) return [];
    const left = this.days(expiry);
    if (left < 0) return [{ label: this.t('humano.records.expired'), tone: 'danger' }];
    if (left <= 60) return [{ label: this.t('humano.records.expiresSoon'), tone: 'warning' }];
    return [];
  }

  /** Days from today until `iso` (negative when in the past). */
  private days(iso: string): number {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((new Date(`${iso}T00:00:00`).getTime() - today.getTime()) / DAY);
  }

  private date(iso: string, monthOnly = false): string {
    return new Date(`${iso}T00:00:00`).toLocaleDateString(
      undefined,
      monthOnly ? { month: 'short', year: 'numeric' } : { dateStyle: 'medium' },
    );
  }

  private range(from: string | null | undefined, to: string | null | undefined, monthOnly = false): string | null {
    if (!from) return null;
    return `${this.date(from, monthOnly)} – ${to ? this.date(to, monthOnly) : this.t('humano.records.present')}`;
  }

  /** "2 yrs 3 mo" between two dates (or until today). */
  private duration(from: string | null | undefined, to: string | null | undefined): string | null {
    if (!from) return null;
    const a = new Date(`${from}T00:00:00`);
    const b = to ? new Date(`${to}T00:00:00`) : new Date();
    const months = Math.max(0, (b.getFullYear() - a.getFullYear()) * 12 + b.getMonth() - a.getMonth());
    const y = Math.floor(months / 12);
    const m = months % 12;
    return [y ? `${y} yr${y > 1 ? 's' : ''}` : '', m ? `${m} mo` : ''].filter(Boolean).join(' ') || '< 1 mo';
  }

  private choices(values: string[]): SelectOption[] {
    return [{ value: '', label: this.t('humano.records.option.select') }, ...values.map(v => ({ value: v, label: v }))];
  }

  private levels(): SelectOption[] {
    return this.choices(['Basic', 'Intermediate', 'Professional', 'Fluent', 'Native']);
  }

  private toOptions(names: Map<string, string>): SelectOption[] {
    return [
      { value: '', label: this.t('humano.records.option.none') },
      ...[...names.entries()].sort((a, b) => a[1].localeCompare(b[1])).map(([value, label]) => ({ value, label })),
    ];
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
