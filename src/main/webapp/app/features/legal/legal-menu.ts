import { MenuItem } from 'app/shared/ui';

const PAGES: Record<string, string> = {
  'legal-terms': '/legal/terms',
  'legal-privacy': '/legal/privacy',
  'legal-dpa': '/legal/data-protection',
};

/** The legal pages in an account menu (app and platform console); `t` translates a key. */
export function legalMenuItems(t: (key: string) => string): MenuItem[] {
  return [
    { heading: t('humano.shell.legal') },
    { id: 'legal-terms', label: t('humano.shell.terms'), icon: 'file-text' },
    { id: 'legal-privacy', label: t('humano.shell.privacy'), icon: 'shield' },
    { id: 'legal-dpa', label: t('humano.shell.dataProtection'), icon: 'scale' },
  ];
}

/** Opens the legal page a menu item stands for in a new tab, so the work in progress stays; false for other items. */
export function openLegalPage(id: string | undefined): boolean {
  const path = id ? PAGES[id] : undefined;
  if (!path) return false;
  window.open(path, '_blank', 'noopener');
  return true;
}
