import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

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
} from '../collection/employee-collection.component';

interface CollectionConfig {
  heading: string;
  service: CollectionService;
  columns: CollectionColumn[];
  fields: CollectionField[];
}

/**
 * Renders every employee-owned collection (addresses, contacts, bank accounts, contracts, permits,
 * certifications, licences, education, experience, languages, assets) as an editable
 * add/edit/delete manager, stacked. Each section drives a generic EmployeeCollectionComponent from
 * its column/field config and its existing REST-backed service.
 */
@Component({
  selector: 'hum-employee-records',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmployeeCollectionComponent],
  templateUrl: './employee-records.component.html',
})
export class EmployeeRecordsComponent {
  readonly employeeId = input.required<string>();
  readonly canManage = input(true);

  protected readonly configs: CollectionConfig[] = [
    {
      heading: 'Addresses',
      service: inject(AddressService),
      columns: [
        { key: 'type', label: 'Type' },
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
        { key: 'countryId', label: 'Country id' },
        { key: 'primary', label: 'Primary address', type: 'checkbox' },
      ],
    },
    {
      heading: 'Emergency contacts',
      service: inject(EmergencyContactService),
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
    {
      heading: 'Bank accounts',
      service: inject(EmployeeBankAccountService),
      columns: [
        { key: 'bankName', label: 'Bank' },
        { key: 'iban', label: 'IBAN' },
        { key: 'primary', label: 'Primary' },
      ],
      fields: [
        { key: 'bankName', label: 'Bank name' },
        { key: 'iban', label: 'IBAN' },
        { key: 'swift', label: 'SWIFT' },
        { key: 'accountHolder', label: 'Account holder' },
        { key: 'currency', label: 'Currency (e.g. USD)' },
        { key: 'primary', label: 'Primary account', type: 'checkbox' },
      ],
    },
    {
      heading: 'Employment contracts',
      service: inject(EmploymentContractService),
      columns: [
        { key: 'contractNumber', label: 'Number' },
        { key: 'contractType', label: 'Type' },
        { key: 'status', label: 'Status' },
      ],
      fields: [
        { key: 'contractNumber', label: 'Contract number' },
        { key: 'startDate', label: 'Start date', type: 'date' },
        { key: 'endDate', label: 'End date', type: 'date' },
        { key: 'contractType', label: 'Contract type' },
        { key: 'positionId', label: 'Position id' },
        { key: 'departmentId', label: 'Department id' },
        { key: 'workingHours', label: 'Working hours', type: 'number' },
        { key: 'signedDate', label: 'Signed date', type: 'date' },
        { key: 'status', label: 'Status' },
      ],
    },
    {
      heading: 'Work permits',
      service: inject(WorkPermitService),
      columns: [
        { key: 'visaType', label: 'Visa type' },
        { key: 'permitNumber', label: 'Permit no.' },
        { key: 'expiryDate', label: 'Expires' },
      ],
      fields: [
        { key: 'visaType', label: 'Visa type' },
        { key: 'permitNumber', label: 'Permit number' },
        { key: 'issueDate', label: 'Issue date', type: 'date' },
        { key: 'expiryDate', label: 'Expiry date', type: 'date' },
        { key: 'sponsor', label: 'Sponsor' },
        { key: 'documentFileId', label: 'Document file id' },
      ],
    },
    {
      heading: 'Certifications',
      service: inject(EmployeeCertificationService),
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'issuer', label: 'Issuer' },
        { key: 'expiryDate', label: 'Expires' },
        { key: 'verified', label: 'Verified' },
      ],
      fields: [
        { key: 'name', label: 'Name', required: true },
        { key: 'issuer', label: 'Issuer' },
        { key: 'issueDate', label: 'Issue date', type: 'date' },
        { key: 'expiryDate', label: 'Expiry date', type: 'date' },
        { key: 'verified', label: 'Verified', type: 'checkbox' },
        { key: 'documentFileId', label: 'Document file id' },
      ],
    },
    {
      heading: 'Licenses',
      service: inject(EmployeeLicenseService),
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'issuer', label: 'Issuer' },
        { key: 'expiryDate', label: 'Expires' },
        { key: 'verified', label: 'Verified' },
      ],
      fields: [
        { key: 'name', label: 'Name', required: true },
        { key: 'issuer', label: 'Issuer' },
        { key: 'issueDate', label: 'Issue date', type: 'date' },
        { key: 'expiryDate', label: 'Expiry date', type: 'date' },
        { key: 'verified', label: 'Verified', type: 'checkbox' },
        { key: 'documentFileId', label: 'Document file id' },
      ],
    },
    {
      heading: 'Education',
      service: inject(EmployeeEducationService),
      columns: [
        { key: 'institution', label: 'Institution' },
        { key: 'degree', label: 'Degree' },
        { key: 'graduationDate', label: 'Graduated' },
      ],
      fields: [
        { key: 'institution', label: 'Institution' },
        { key: 'degree', label: 'Degree' },
        { key: 'fieldOfStudy', label: 'Field of study' },
        { key: 'graduationDate', label: 'Graduation date', type: 'date' },
        { key: 'documentFileId', label: 'Document file id' },
      ],
    },
    {
      heading: 'Experience',
      service: inject(EmployeeExperienceService),
      columns: [
        { key: 'company', label: 'Company' },
        { key: 'position', label: 'Position' },
        { key: 'startDate', label: 'From' },
      ],
      fields: [
        { key: 'company', label: 'Company' },
        { key: 'position', label: 'Position' },
        { key: 'startDate', label: 'Start date', type: 'date' },
        { key: 'endDate', label: 'End date', type: 'date' },
      ],
    },
    {
      heading: 'Languages',
      service: inject(EmployeeLanguageService),
      columns: [
        { key: 'language', label: 'Language' },
        { key: 'speaking', label: 'Speaking' },
      ],
      fields: [
        { key: 'language', label: 'Language' },
        { key: 'reading', label: 'Reading' },
        { key: 'writing', label: 'Writing' },
        { key: 'speaking', label: 'Speaking' },
      ],
    },
    {
      heading: 'Assets',
      service: inject(EmployeeAssetService),
      columns: [
        { key: 'type', label: 'Type' },
        { key: 'identifier', label: 'Identifier' },
        { key: 'assignedDate', label: 'Assigned' },
      ],
      fields: [
        { key: 'type', label: 'Type (e.g. LAPTOP)' },
        { key: 'identifier', label: 'Identifier' },
        { key: 'assignedDate', label: 'Assigned date', type: 'date' },
        { key: 'returnedDate', label: 'Returned date', type: 'date' },
      ],
    },
  ];
}
