import { loader } from 'fumadocs-core/source';
import { createMDXSource } from 'fumadocs-mdx';
import { docs, meta } from '@/.source';
import { i18n } from './i18n';

export const source = loader({
  baseUrl: '/docs',
  i18n,
  source: createMDXSource(docs, meta),
});
