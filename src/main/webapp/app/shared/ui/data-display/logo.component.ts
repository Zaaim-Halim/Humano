import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Logo — the app icon (the "two people" mark from `favicon.svg`) followed by the "humano" wordmark and
 * its brand dot. The one place the logo is drawn, so the site header and footer, the sign-in screens
 * and the app sidebar always match. `size="sm"` is for tight spots (the product mock on the landing
 * page); `[mark]="false"` leaves the icon out. Wrap it in a link where it should lead home; the pre-boot
 * screen in `content/css/loading.css` draws its own copy, as it renders before Angular.
 */
@Component({
  selector: 'hum-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'hum-logo',
    '[class.hum-logo--sm]': 'size() === "sm"',
  },
  template: `
    @if (mark()) {
      <svg class="hum-logo__mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
        <rect width="32" height="32" rx="7" fill="#4338ca" />
        <path
          fill="#ffffff"
          d="M7 6.2A1.2 1.2 0 0 1 8.2 5H10.3A1.2 1.2 0 0 1 11.5 6.2V10.133A1 1 0 0 0 12.85 11.069A9 9 0 0 1 25 19.5V25.8A1.2 1.2 0 0 1 23.8 27H21.7A1.2 1.2 0 0 1 20.5 25.8V19.5A4.5 4.5 0 0 0 11.5 19.5V25.8A1.2 1.2 0 0 1 10.3 27H8.2A1.2 1.2 0 0 1 7 25.8Z"
        />
        <circle cx="22.75" cy="6.9" r="2.75" fill="#a5b4fc" />
      </svg>
    }
    <span class="hum-logo__word">{{ name() }}<span class="hum-logo__dot" aria-hidden="true"></span></span>
  `,
})
export class LogoComponent {
  readonly name = input('humano');
  readonly size = input<'md' | 'sm'>('md');
  readonly mark = input(true);
}
