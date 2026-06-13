/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
const angularLanguages = {
  en: async (): Promise<void> => import('@angular/common/locales/en'),
};

const languagesData = {
  en: async (): Promise<any> => import('i18n/en.json').catch(),
};

export const loadLocale = (locale: keyof typeof angularLanguages): Promise<any> => {
  angularLanguages[locale]();
  return languagesData[locale]();
};
