import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

export interface Person {
  name: string;
  src?: string | null;
}

const AVATAR_HUES: [string, string][] = [
  ['var(--brand-muted)', 'var(--brand-text)'],
  ['var(--success-bg)', 'var(--success-fg)'],
  ['var(--warning-bg)', 'var(--warning-fg)'],
  ['var(--info-bg)', 'var(--info-fg)'],
  ['var(--violet-100)', 'var(--violet-700)'],
  ['var(--neutral-bg)', 'var(--neutral-fg)'],
];

export function avatarInitials(name: string): string {
  return [...name.split(/\s+/)]
    .map(w => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

export function avatarHue(name: string): [string, string] {
  const sum = Math.abs([...name].reduce((a, c) => a + c.charCodeAt(0), 0));
  return AVATAR_HUES[sum % AVATAR_HUES.length];
}

/**
 * Avatar — person/tenant identity. Shows `src` image, otherwise deterministic
 * colour initials derived from `name`. Mirrors `_ds_bundle.js` → Avatar.jsx.
 */
@Component({
  selector: 'hum-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'hum-avatar',
    '[class]': 'sizeClass()',
    '[style.background]': 'src() ? null : hue()[0]',
    '[style.color]': 'src() ? null : hue()[1]',
    '[attr.title]': 'name()',
  },
  template: `
    @if (src(); as image) {
      <img [src]="image" [alt]="name()" />
    } @else {
      {{ initials() }}
    }
  `,
})
export class AvatarComponent {
  readonly name = input('');
  readonly src = input<string | null>(null);
  readonly size = input<AvatarSize>('md');
  readonly square = input(false, { transform: booleanAttribute });

  protected readonly initials = computed(() => avatarInitials(this.name()));
  protected readonly hue = computed(() => avatarHue(this.name()));
  protected readonly sizeClass = computed(() =>
    [this.size() !== 'md' ? `hum-avatar--${this.size()}` : '', this.square() ? 'hum-avatar--square' : ''].filter(Boolean).join(' '),
  );
}

/**
 * AvatarGroup — overlapping stack with a "+N" overflow chip.
 */
@Component({
  selector: 'hum-avatar-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AvatarComponent],
  host: { class: 'hum-avatar-group' },
  template: `
    @for (p of visible(); track p.name) {
      <hum-avatar [name]="p.name" [src]="p.src ?? null" [size]="size()" />
    }
    @if (extra() > 0) {
      <span
        class="hum-avatar"
        [class]="size() !== 'md' ? 'hum-avatar--' + size() : ''"
        style="background: var(--bg-muted); color: var(--text-muted)"
      >
        +{{ extra() }}
      </span>
    }
  `,
})
export class AvatarGroupComponent {
  readonly people = input<Person[]>([]);
  readonly max = input(4);
  readonly size = input<AvatarSize>('sm');

  protected readonly visible = computed(() => this.people().slice(0, this.max()));
  protected readonly extra = computed(() => this.people().length - this.visible().length);
}
