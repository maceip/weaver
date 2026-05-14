import type { I18nConfig } from 'fumadocs-core/i18n';

export const i18n: I18nConfig = {
  defaultLanguage: 'en',
  languages: ['en', 'ko'],
  hideLocale: 'default-locale',
};

export const languageNames: Record<string, string> = {
  en: 'English',
  ko: '한국어',
};
