import { ChangeDetectionStrategy, Component, ElementRef, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { ButtonComponent } from 'app/shared/ui';

import { LEGAL } from './legal-info';
import { LegalNavComponent } from './legal-nav.component';

/**
 * A privacy notice template for employers (the controllers of their staff's HR data) to adapt and give
 * their employees: what is kept in Humano and why. Not one of Humano's own legal documents — a starting
 * point, matching the data categories of the Data Processing Addendum (Annex 1).
 */
@Component({
  selector: 'hum-employee-notice',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide hum-legal' },
  imports: [RouterLink, LucideAngularModule, ButtonComponent, LegalNavComponent],
  templateUrl: './employee-notice.component.html',
})
export default class EmployeeNoticeComponent {
  protected readonly legal = LEGAL;
  protected readonly copied = signal(false);
  private readonly notice = viewChild.required<ElementRef<HTMLElement>>('notice');

  /** Copies the notice as plain text, ready to paste into the employer's own document. */
  protected copy(): void {
    const text = this.notice().nativeElement.innerText;
    void navigator.clipboard.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    });
  }
}
